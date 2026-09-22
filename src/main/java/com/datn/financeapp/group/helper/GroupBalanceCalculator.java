package com.datn.financeapp.group.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.entity.GroupTransactionParticipant;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Pure Engine tính toán bảng cân đối tài chính cho nhóm (Group Balance Calculator).
 * <p>
 * Tuân thủ toàn diện đặc tả kỹ thuật tại {@code docs/GroupBalanceCalculator_Design.md}:
 * <ul>
 *     <li>Thuần logic in-memory, hoàn toàn không phụ thuộc Database hay I/O.</li>
 *     <li>Bộ tích lũy 5 biến số độc lập: rawContribution, paidOutOfPocket, refunded, withdrawn, share.</li>
 *     <li>Triệt tiêu lỗi N+1 query qua danh sách thời gian gia nhập/rời nhóm {@link GroupMemberPeriod}.</li>
 *     <li>Phân bổ chi phí công bằng & ổn định (Fair & Deterministic) với thuật toán luân phiên phần dư.</li>
 *     <li>Xử lý chia share hỗn hợp và kiểm tra nghiêm ngặt tính bảo toàn tài chính (Fail-fast).</li>
 * </ul>
 * </p>
 */
@Component
public class GroupBalanceCalculator {

    /**
     * Tính toán bảng cân đối cho nhóm từ dữ liệu giao dịch, participants và timeline thành viên.
     * Thuần logic in-memory, triệt tiêu lỗi N+1 Query.
     *
     * @param txns                Danh sách giao dịch cần tính toán
     * @param participantsByTxnId Map danh sách participant theo từng transactionId (đã batch load)
     * @param memberPeriods       Danh sách timeline thành viên của nhóm để tự lọc in-memory
     * @return Bảng cân đối tài chính tổng hợp
     */
    public MemberBalances calculateBalances(
            List<GroupTransaction> txns,
            Map<UUID, List<GroupTransactionParticipant>> participantsByTxnId,
            List<GroupMemberPeriod> memberPeriods
    ) {
        return doCalculateBalances(txns, participantsByTxnId, memberPeriods, null);
    }

    /**
     * Overload hỗ trợ resolver hàm khi cần kiểm thử linh hoạt hoặc tương thích ngược.
     *
     * @param txns                   Danh sách giao dịch
     * @param participantsByTxnId    Map participants theo transactionId
     * @param fallbackMemberResolver Hàm cung cấp danh sách thành viên có mặt tại thời điểm giao dịch
     * @return Bảng cân đối tài chính tổng hợp
     */
    public MemberBalances calculateBalances(
            List<GroupTransaction> txns,
            Map<UUID, List<GroupTransactionParticipant>> participantsByTxnId,
            Function<Instant, List<UUID>> fallbackMemberResolver
    ) {
        return doCalculateBalances(txns, participantsByTxnId, null, fallbackMemberResolver);
    }

    private MemberBalances doCalculateBalances(
            List<GroupTransaction> txns,
            Map<UUID, List<GroupTransactionParticipant>> participantsByTxnId,
            List<GroupMemberPeriod> memberPeriods,
            Function<Instant, List<UUID>> fallbackMemberResolver
    ) {
        Map<UUID, MemberBalanceAccumulator> memberMap = new HashMap<>();

        if (txns == null || txns.isEmpty()) {
            return new MemberBalances(memberMap);
        }

        // 1. Điều kiện lọc: chưa bị xóa mềm và (status == null || status == CONFIRMED)
        // Sắp xếp theo occurredAt ASC, createdAt ASC (Mục 2)
        Comparator<GroupTransaction> comparator = Comparator
                .comparing(GroupTransaction::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GroupTransaction::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

        List<GroupTransaction> sortedTxns = txns.stream()
                .filter(t -> t.getDeletedAt() == null && (t.getStatus() == null || t.getStatus() == GroupTransactionStatus.CONFIRMED))
                .sorted(comparator)
                .toList();

        // 2. Duyệt tuần tự qua danh sách giao dịch
        for (GroupTransaction txn : sortedTxns) {
            GroupTransactionType type = txn.getType();
            if (type == null) {
                throw new IllegalStateException("Loại giao dịch không được để trống (null). Transaction ID: " + txn.getId());
            }

            long amount = txn.getAmount() != null ? txn.getAmount() : 0L;
            if (amount <= 0) {
                throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Số tiền giao dịch phải lớn hơn 0. Transaction ID: " + txn.getId());
            }

            UUID uid = txn.getUserId();

            switch (type) {
                case CONTRIBUTION -> {
                    if (uid != null) {
                        memberMap.computeIfAbsent(uid, k -> new MemberBalanceAccumulator()).addContribution(amount);
                    }
                }
                case EXPENSE -> {
                    if (txn.getMoneySource() == MoneySource.PERSONAL && uid != null) {
                        memberMap.computeIfAbsent(uid, k -> new MemberBalanceAccumulator()).addPaidOutOfPocket(amount);
                    }
                    distributeShare(txn, amount, 1L, memberMap, participantsByTxnId, memberPeriods, fallbackMemberResolver);
                }
                case REFUND -> {
                    if (uid != null) {
                        memberMap.computeIfAbsent(uid, k -> new MemberBalanceAccumulator()).addRefund(amount);
                    }
                }
                case WITHDRAWAL -> {
                    if (uid != null) {
                        memberMap.computeIfAbsent(uid, k -> new MemberBalanceAccumulator()).addWithdrawal(amount);
                    }
                }
                case ADJUSTMENT_DOWN -> {
                    // Quỹ hao hụt -> tăng share phải chịu (factor = +1)
                    distributeShare(txn, amount, 1L, memberMap, participantsByTxnId, memberPeriods, fallbackMemberResolver);
                }
                case ADJUSTMENT_UP -> {
                    // Quỹ dôi ra -> giảm share phải chịu (factor = -1)
                    distributeShare(txn, amount, -1L, memberMap, participantsByTxnId, memberPeriods, fallbackMemberResolver);
                }
                default -> throw new IllegalStateException("Loại giao dịch chưa được hỗ trợ: " + type);
            }
        }

        return new MemberBalances(memberMap);
    }

    /**
     * Phân bổ share cho các thành viên chịu chi phí theo các quy tắc tại Mục 6 và Mục 8.
     */
    private void distributeShare(
            GroupTransaction txn,
            long amount,
            long factor,
            Map<UUID, MemberBalanceAccumulator> memberMap,
            Map<UUID, List<GroupTransactionParticipant>> participantsByTxnId,
            List<GroupMemberPeriod> memberPeriods,
            Function<Instant, List<UUID>> fallbackMemberResolver
    ) {
        List<GroupTransactionParticipant> raw = (participantsByTxnId != null && txn.getId() != null)
                ? participantsByTxnId.getOrDefault(txn.getId(), List.of())
                : List.of();

        if (!raw.isEmpty()) {
            // Validate: Không trùng lặp userId và shareAmount phải > 0 (Mục 2 & Mục 8)
            Set<UUID> seenUserIds = new HashSet<>();
            for (GroupTransactionParticipant p : raw) {
                if (p.getUserId() == null || !seenUserIds.add(p.getUserId())) {
                    throw new BusinessException(
                            ErrorCode.INVALID_PARTICIPANT_DATA,
                            "Danh sách người tham gia có thành viên bị trùng lặp hoặc null: " + p.getUserId()
                    );
                }
                if (p.getShareAmount() != null && p.getShareAmount() <= 0) {
                    throw new BusinessException(
                            ErrorCode.INVALID_PARTICIPANT_DATA,
                            "Số tiền share của thành viên phải lớn hơn 0. User ID: " + p.getUserId()
                    );
                }
            }

            List<GroupTransactionParticipant> specified = new ArrayList<>();
            List<GroupTransactionParticipant> unspecified = new ArrayList<>();

            for (GroupTransactionParticipant p : raw) {
                if (p.getShareAmount() != null) {
                    specified.add(p);
                } else {
                    unspecified.add(p);
                }
            }

            if (unspecified.isEmpty()) {
                // Trường hợp 1: Tất cả đều có shareAmount -> Validate tổng = amount
                long sum = 0L;
                for (GroupTransactionParticipant p : specified) {
                    sum += p.getShareAmount();
                }
                if (sum != amount) {
                    throw new BusinessException(ErrorCode.PARTICIPANTS_SUM_MISMATCH);
                }
                for (GroupTransactionParticipant p : specified) {
                    memberMap.computeIfAbsent(p.getUserId(), k -> new MemberBalanceAccumulator())
                            .addShare(p.getShareAmount() * factor);
                }
            } else if (specified.isEmpty()) {
                // Trường hợp 2: Tất cả đều null -> Chia đều amount cho tất cả participants
                List<UUID> userIds = unspecified.stream()
                        .map(GroupTransactionParticipant::getUserId)
                        .toList();
                distributeEvenly(txn, userIds, amount, factor, memberMap);
            } else {
                // Trường hợp 3: Hỗn hợp (Mục 6)
                long sumSpecified = 0L;
                for (GroupTransactionParticipant p : specified) {
                    sumSpecified += p.getShareAmount();
                }
                if (sumSpecified >= amount) {
                    throw new BusinessException(
                            ErrorCode.PARTICIPANTS_SUM_MISMATCH,
                            "Tổng số tiền các thành viên chỉ định phải nhỏ hơn số tiền giao dịch."
                    );
                }
                // Người có shareAmount: giữ nguyên giá trị đã chỉ định
                for (GroupTransactionParticipant p : specified) {
                    memberMap.computeIfAbsent(p.getUserId(), k -> new MemberBalanceAccumulator())
                            .addShare(p.getShareAmount() * factor);
                }
                // Người có shareAmount = null: chia đều số tiền còn lại (amount - sumSpecified)
                long remAmount = amount - sumSpecified;
                List<UUID> nullUserIds = unspecified.stream()
                        .map(GroupTransactionParticipant::getUserId)
                        .toList();
                distributeEvenly(txn, nullUserIds, remAmount, factor, memberMap);
            }
        } else {
            // Không có participant nào -> Danh sách thành viên có mặt tại occurredAt (Mục 2 & Mục 6)
            List<UUID> memberUserIds = resolveCandidateMembers(txn, memberPeriods, fallbackMemberResolver);
            if (memberUserIds.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.PARTICIPANT_NOT_MEMBER,
                        "Không tìm thấy thành viên nào có mặt tại thời điểm giao dịch để phân bổ chi phí."
                );
            }
            distributeEvenly(txn, memberUserIds, amount, factor, memberMap);
        }
    }

    private List<UUID> resolveCandidateMembers(
            GroupTransaction txn,
            List<GroupMemberPeriod> memberPeriods,
            Function<Instant, List<UUID>> fallbackMemberResolver
    ) {
        if (memberPeriods != null) {
            Instant occurredAt = txn.getOccurredAt();
            return memberPeriods.stream()
                    .filter(period -> period.isActiveAt(occurredAt))
                    .map(GroupMemberPeriod::userId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparing(UUID::toString))
                    .toList();
        }

        if (fallbackMemberResolver != null) {
            List<UUID> ids = fallbackMemberResolver.apply(txn.getOccurredAt());
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparing(UUID::toString))
                    .toList();
        }

        return List.of();
    }

    /**
     * Chia đều số tiền cho danh sách userId và phân bổ phần dư theo thuật toán luân phiên Fair & Deterministic.
     *
     * @param txn       Giao dịch đang xử lý (dùng ID để xác định startIndex luân phiên)
     * @param userIds   Danh sách userId cần chia đều
     * @param amount    Số tiền cần chia
     * @param factor    +1 (tăng share) hoặc -1 (giảm share)
     * @param memberMap Map accumulator
     */
    private void distributeEvenly(
            GroupTransaction txn,
            List<UUID> userIds,
            long amount,
            long factor,
            Map<UUID, MemberBalanceAccumulator> memberMap
    ) {
        if (userIds == null || userIds.isEmpty() || amount == 0L) {
            return;
        }

        List<UUID> sortedUserIds = userIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        int n = sortedUserIds.size();
        long base = amount / n;
        long remainder = amount % n;

        // Dùng Math.floorMod để tránh lỗi tràn số âm khi hashCode == Integer.MIN_VALUE
        int hashCode = (txn != null && txn.getId() != null) ? txn.getId().hashCode() : 0;
        int startIndex = Math.floorMod(hashCode, n);

        long[] portions = new long[n];
        Arrays.fill(portions, base);
        for (int i = 0; i < remainder; i++) {
            int idx = (startIndex + i) % n;
            portions[idx] = base + 1;
        }

        for (int i = 0; i < n; i++) {
            UUID uid = sortedUserIds.get(i);
            memberMap.computeIfAbsent(uid, k -> new MemberBalanceAccumulator())
                    .addShare(portions[i] * factor);
        }
    }
}
