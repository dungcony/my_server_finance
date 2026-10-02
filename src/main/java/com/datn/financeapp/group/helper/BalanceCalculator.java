package com.datn.financeapp.group.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.GTransactionType;
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
import java.util.stream.Collectors;


/**
 * Class chuyên dùng để tính toán thu chi và chia tiền cho các thành viên trong
 * nhóm.
 * Giúp trả lời các câu hỏi: Ai đã nộp bao nhiêu? Ai đã tiêu bao nhiêu? Nhóm còn
 * nợ ai và ai đang nợ nhóm?
 * <p>
 * Các hàm trong class:
 * <ul>
 * <li>{@link #calculateBalances}: Hàm chính để chốt sổ, tính ra kết quả thu chi
 * của từng người.</li>
 * <li>{@link #allocateShareAmongParticipants}: Hàm chia tiền (chia đều hoặc
 * chia theo con số cụ thể) cho những người tham gia giao dịch.</li>
 * <li>{@link #getActiveMemberIdsAt}: Lấy danh sách những người đang có mặt
 * trong nhóm tại thời điểm xảy ra giao dịch.</li>
 * <li>{@link #allocateEvenly}: Hàm chia đều một số tiền cho nhiều người (có xử
 * lý chia phần dư công bằng).</li>
 * </ul>
 * </p>
 */
public final class BalanceCalculator {

    /**
     * Hàm chính để chốt sổ, tính toán lại toàn bộ tiền bạc của các thành viên.
     * Dựa vào lịch sử giao dịch (nộp, rút, chi tiêu...), hàm sẽ tính ra số dư cuối
     * cùng của từng người.
     *
     * @param allTxns      Toàn bộ danh sách giao dịch lấy từ database
     * @param allMembers   Toàn bộ lịch sử thành viên lấy từ database
     * @param excludeTxnId ID của giao dịch muốn bỏ qua không tính (dùng khi đang
     *                     cập nhật hoặc xóa giao dịch)
     * @return Đối tượng chứa kết quả thống kê tiền bạc của từng người
     * (MemberBalances)
     */
    public static MemberBalances calculateBalances(
            List<GTransaction> allTxns,
            List<Member> allMembers,
            UUID excludeTxnId) {
        Map<UUID, MemberBalanceAccumulator> memberMap = new HashMap<>();

        if (allTxns == null || allTxns.isEmpty()) {
            return new MemberBalances(memberMap);
        }

        List<GTransaction> txns = (excludeTxnId == null)
                ? allTxns
                : allTxns.stream().filter(t -> !t.getId().equals(excludeTxnId)).toList();

        if (txns.isEmpty()) {
            return new MemberBalances(memberMap);
        }

        List<GroupMemberPeriod> memberPeriods = (allMembers == null)
                ? List.of()
                : allMembers.stream()
                .map(m -> new GroupMemberPeriod(m.getUserId(), m.getJoinedAt(), m.getLeftAt()))
                .toList();

        Map<UUID, List<TransactionParticipant>> participantsByTxnId = txns.stream()
                .collect(Collectors.toMap(GTransaction::getId, GTransaction::getParticipants));

        // 1. Điều kiện lọc: chưa bị xóa mềm và (status == null || status == CONFIRMED)
        // Sắp xếp theo occurredAt ASC, createdAt ASC (Mục 2)
        Comparator<GTransaction> comparator = Comparator
                .comparing(GTransaction::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GTransaction::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

        List<GTransaction> sortedTxns = txns.stream()
                .filter(t -> t.getDeletedAt() == null
                        && (t.getStatus() == null || t.getStatus() == GTransactionStatus.CONFIRMED))
                .sorted(comparator)
                .toList();

        // 2. Duyệt tuần tự qua danh sách giao dịch
        for (GTransaction txn : sortedTxns) {
            GTransactionType type = txn.getType();
            if (type == null) {
                throw new IllegalStateException(
                        "Loại giao dịch không được để trống (null). Transaction ID: " + txn.getId());
            }

            long amount = txn.getAmount() != null ? txn.getAmount() : 0L;
            if (amount <= 0) {
                throw new BusinessException(ErrorCode.INVALID_AMOUNT,
                        "Số tiền giao dịch phải lớn hơn 0. Transaction ID: " + txn.getId());
            }

            UUID uid = txn.getTransactorId();

            switch (type) {
                case CONTRIBUTION -> {
                    if (txn.getMoneySource() == MoneySource.FUND) {
                        throw new BusinessException(
                                ErrorCode.MONEY_SOURCE_INVALID,
                                "Nguồn tiền nộp quỹ không thể là tiền từ quỹ (FUND). Transaction ID: " + txn.getId());
                    }
                    if (uid == null) {
                        throw new BusinessException(
                                ErrorCode.PAYER_NOT_MEMBER,
                                "Người nộp quỹ không được để trống (null). Transaction ID: " + txn.getId());
                    }
                    memberMap.computeIfAbsent(uid, k -> new MemberBalanceAccumulator()).addContribution(amount);
                }
                case EXPENSE -> {
                    if (txn.getMoneySource() == MoneySource.PERSONAL) {
                        if (uid == null) {
                            throw new BusinessException(
                                    ErrorCode.PAYER_NOT_MEMBER,
                                    "Người chi tiền túi không được để trống (null). Transaction ID: " + txn.getId());
                        }
                        memberMap.computeIfAbsent(uid, k -> new MemberBalanceAccumulator()).addPaidOutOfPocket(amount);
                    }
                    allocateShareAmongParticipants(txn, amount, 1L, memberMap, participantsByTxnId, memberPeriods);
                }
                case REFUND -> {
                    if (uid == null) {
                        throw new BusinessException(
                                ErrorCode.PAYER_NOT_MEMBER,
                                "Người nhận hoàn tiền không được để trống (null). Transaction ID: " + txn.getId());
                    }
                    memberMap.computeIfAbsent(uid, k -> new MemberBalanceAccumulator()).addRefund(amount);
                }
                case ADJUSTMENT_DOWN ->
                    // Quỹ hao hụt -> tăng share phải chịu (factor = +1)
                        allocateShareAmongParticipants(txn, amount, 1L, memberMap, participantsByTxnId, memberPeriods);
                case ADJUSTMENT_UP ->
                    // Quỹ dôi ra -> giảm share phải chịu (factor = -1)
                        allocateShareAmongParticipants(txn, amount, -1L, memberMap, participantsByTxnId, memberPeriods);
                default -> throw new IllegalStateException("Loại giao dịch chưa được hỗ trợ: " + type);
            }
        }

        return new MemberBalances(memberMap);
    }

    // ---------------------------------------PRIVATE----------------------------------------------//

    /**
     * Phân bổ chi phí (share) cho các thành viên tham gia giao dịch.
     *
     * @param txn                 Giao dịch đang phân bổ chi phí
     * @param amount              Tổng số tiền cần phân bổ
     * @param factor              Hệ số phân bổ (+1: tăng phần share phải gánh, -1:
     *                            giảm phần share phải gánh)
     * @param memberMap           Map tích lũy số dư của từng thành viên
     * @param participantsByTxnId Map danh sách người tham gia theo từng
     *                            transactionId
     * @param memberPeriods       Danh sách chu kỳ thời gian tham gia nhóm
     */
    private static void allocateShareAmongParticipants(
            GTransaction txn,
            long amount,
            long factor,
            Map<UUID, MemberBalanceAccumulator> memberMap,
            Map<UUID, List<TransactionParticipant>> participantsByTxnId,
            List<GroupMemberPeriod> memberPeriods) {
        List<TransactionParticipant> raw = (participantsByTxnId != null && txn.getId() != null)
                ? participantsByTxnId.getOrDefault(txn.getId(), List.of())
                : List.of();

        if (!raw.isEmpty()) {
            // Validate: Không trùng lặp userId và shareAmount phải > 0 (Mục 2 & Mục 8)
            Set<UUID> seenUserIds = new HashSet<>();
            for (TransactionParticipant p : raw) {
                if (p.getUserId() == null || !seenUserIds.add(p.getUserId())) {
                    throw new BusinessException(
                            ErrorCode.INVALID_PARTICIPANT_DATA,
                            "Danh sách người tham gia có thành viên bị trùng lặp hoặc null: " + p.getUserId());
                }
                if (p.getShareAmount() != null && p.getShareAmount() <= 0) {
                    throw new BusinessException(
                            ErrorCode.INVALID_PARTICIPANT_DATA,
                            "Số tiền share của thành viên phải lớn hơn 0. User ID: " + p.getUserId());
                }
            }

            List<TransactionParticipant> specified = new ArrayList<>();
            List<TransactionParticipant> unspecified = new ArrayList<>();

            for (TransactionParticipant p : raw) {
                if (p.getShareAmount() != null) {
                    specified.add(p);
                } else {
                    unspecified.add(p);
                }
            }

            if (unspecified.isEmpty()) {
                // Trường hợp 1: Tất cả đều có shareAmount -> Validate tổng = amount
                long sum = 0L;
                for (TransactionParticipant p : specified) {
                    sum += p.getShareAmount();
                }
                if (sum != amount) {
                    throw new BusinessException(ErrorCode.PARTICIPANTS_SUM_MISMATCH);
                }
                for (TransactionParticipant p : specified) {
                    memberMap.computeIfAbsent(p.getUserId(), k -> new MemberBalanceAccumulator())
                            .addShare(p.getShareAmount() * factor);
                }
            } else if (specified.isEmpty()) {
                // Trường hợp 2: Tất cả đều null -> Chia đều amount cho tất cả participants
                List<UUID> userIds = unspecified.stream()
                        .map(TransactionParticipant::getUserId)
                        .toList();
                allocateEvenly(txn, userIds, amount, factor, memberMap);
            } else {
                // Trường hợp 3: Hỗn hợp (Mục 6)
                long sumSpecified = 0L;
                for (TransactionParticipant p : specified) {
                    sumSpecified += p.getShareAmount();
                }
                if (sumSpecified >= amount) {
                    throw new BusinessException(
                            ErrorCode.PARTICIPANTS_SUM_MISMATCH,
                            "Tổng số tiền các thành viên chỉ định phải nhỏ hơn số tiền giao dịch.");
                }
                // Người có shareAmount: giữ nguyên giá trị đã chỉ định
                for (TransactionParticipant p : specified) {
                    memberMap.computeIfAbsent(p.getUserId(), k -> new MemberBalanceAccumulator())
                            .addShare(p.getShareAmount() * factor);
                }
                // Người có shareAmount = null: chia đều số tiền còn lại (amount - sumSpecified)
                long remAmount = amount - sumSpecified;
                List<UUID> nullUserIds = unspecified.stream()
                        .map(TransactionParticipant::getUserId)
                        .toList();
                allocateEvenly(txn, nullUserIds, remAmount, factor, memberMap);
            }
        } else {
            // Không có participant nào -> Danh sách thành viên có mặt tại occurredAt (Mục 2
            // & Mục 6)
            List<UUID> memberUserIds = getActiveMemberIdsAt(txn, memberPeriods);
            if (memberUserIds.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.PARTICIPANT_NOT_MEMBER,
                        "Không tìm thấy thành viên nào có mặt tại thời điểm giao dịch để phân bổ chi phí.");
            }
            allocateEvenly(txn, memberUserIds, amount, factor, memberMap);
        }
    }

    /**
     * Lấy danh sách ID thành viên đang hoạt động tại thời điểm diễn ra giao dịch.
     *
     * @param txn           Giao dịch chứa thời điểm phát sinh (occurredAt)
     * @param memberPeriods Danh sách chu kỳ thời gian thành viên
     * @return Danh sách ID thành viên hợp lệ tại thời điểm giao dịch, đã được sắp
     * xếp tăng dần
     */
    private static List<UUID> getActiveMemberIdsAt(
            GTransaction txn,
            List<GroupMemberPeriod> memberPeriods) {
        if (memberPeriods != null) {
            Instant occurredAt = txn.getOccurredAt();
            return memberPeriods.stream()
                    .filter(period -> period.isActiveAt(occurredAt))
                    .map(GroupMemberPeriod::userId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        return List.of();
    }

    /**
     * Chia đều số tiền cho danh sách thành viên và phân bổ phần dư công bằng.
     *
     * @param txn       Giao dịch đang xử lý (dùng ID để xác định startIndex luân
     *                  phiên)
     * @param userIds   Danh sách userId cần chia đều
     * @param amount    Số tiền cần chia
     * @param factor    +1 (tăng share) hoặc -1 (giảm share)
     * @param memberMap Map accumulator
     */
    private static void allocateEvenly(
            GTransaction txn,
            List<UUID> userIds,
            long amount,
            long factor,
            Map<UUID, MemberBalanceAccumulator> memberMap) {
        if (userIds == null || userIds.isEmpty() || amount == 0L) {
            return;
        }

        List<UUID> sortedUserIds = userIds.stream()
                .sorted(Comparator.naturalOrder())
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
