package com.datn.financeapp.module.group.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnParticipantRes;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.GroupTxnParticipant;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.service.MemberService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper hỗ trợ xử lý nghiệp vụ giao dịch nhóm (Group Transaction).
 * <p>
 * Đóng gói các logic tính toán thuần (Pure Math / Logic) bao gồm:
 * <ul>
 *     <li>Ánh xạ Entity sang DTO phản hồi chi tiết {@link GroupTxnDetailRes}</li>
 *     <li>Tính toán mức biến động (delta) số dư quỹ nhóm theo loại giao dịch và nguồn tiền</li>
 *     <li>Phân giải danh sách người tham gia chia tiền (Participants) và kiểm tra tính hợp lệ</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class GroupTxnHelper {

    private final MemberService memberService;

    /**
     * kiểm tra người tạo, chi, và người đc chỉ định tham gia là ở trong nhóm
     * chỉ bằng đúng 1 câu query IN duy nhất tới Database (WHERE user_id IN (:candidateIds)).
     *
     * @param groupId      ID nhóm
     * @param participants Danh sách người chia tiền
     * @param transactorId Người chi tiền
     * @param amount       Số tiền giao dịch
     */
    public void validParticipants(
            UUID groupId,
            UUID transactorId,
            @NonNull List<GroupTxnParticipantReq> participants,
            long amount
    ) {

        List<UUID> candidateIds = participants.stream()
                .map(GroupTxnParticipantReq::userId)
                .collect(Collectors.toCollection(ArrayList::new));

        // kiểm tra có bị trùng lặp id khi gửi lên k
        checkDuplicated(candidateIds);
        validParticipantShares(participants, amount);

        if (transactorId != null && !candidateIds.contains(transactorId))
            candidateIds.add(transactorId);

        // kiểm tra tư cách thành viên cho toàn bộ ID cần xét
        if (!memberService.allMemberInGroup(groupId, candidateIds))
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP);
    }

    /**
     * Tính toán mức biến động (delta) của số dư quỹ nhóm sinh ra bởi giao dịch.
     *
     * @param type       Loại giao dịch ({@link GroupTxnType})
     * @param source     Nguồn tiền sử dụng ({@link MoneySource}: {@code FUND} hoặc {@code PERSONAL})
     * @param amount     Số tiền giao dịch (phải &gt; 0)
     * @param isReversal Cờ đánh dấu có phải giao dịch đảo ngược (reversal/rollback) hay không
     * @return Mức biến động số dư quỹ (giá trị dương: quỹ tăng, âm: quỹ giảm, 0: quỹ không đổi)
     */
    public static long calculateDelta(GroupTxnType type, MoneySource source, long amount, boolean isReversal) {
        long factor = isReversal ? -1L : 1L;
        return switch (type) {
            case EXPENSE -> (source == MoneySource.FUND) ? -amount * factor : 0L;
            case CONTRIBUTION -> (source == MoneySource.PERSONAL) ? amount * factor : 0L;
            case REFUND, ADJUSTMENT_DOWN -> -amount * factor;
            case ADJUSTMENT_UP -> amount * factor;
        };
    }

    public static long calculateDelta(GroupTxn txn) {
        return calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), false);
    }


    /**
     * Chia đều tổng số tiền cho danh sách thành viên được chỉ định.
     *
     * @param totalAmount Tổng số tiền cần chia
     * @param userIds     Danh sách UUID của các thành viên tham gia chia tiền
     * @return Danh sách {@link GroupTxnParticipantRes} chứa ID và số tiền chia tương ứng
     */
    public static List<GroupTxnParticipantRes> splitEvenly(long totalAmount, List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        int n = userIds.size();
        long base = totalAmount / n;
        long rem = totalAmount % n;

        List<UUID> sorted = userIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        List<GroupTxnParticipantRes> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long share = base + (i < rem ? 1 : 0);
            result.add(new GroupTxnParticipantRes(sorted.get(i), share));
        }
        return result;
    }

    public static List<GroupTxnParticipant> getParticipants(@NotNull(message = "participants không được null") List<GroupTxnParticipantReq> participants) {
        return participants.stream()
                .map(p -> GroupTxnParticipant.builder()
                        .userId(p.userId())
                        .shareAmount(p.shareAmount())
                        .build())
                .toList();
    }


    public static @NonNull GroupTxnStatus getStatus(UUID operatorId, GroupTxnCreateReq req, MemberAuthInfo authInfo) {
        if (authInfo.isMember()) {
            // member không được kiểm kê hay hoàn tiền
            if (req.type() == GroupTxnType.ADJUSTMENT_UP
                    || req.type() == GroupTxnType.ADJUSTMENT_DOWN
                    || req.type() == GroupTxnType.REFUND)
                throw new BusinessException(ErrorCode.GROUP_TXN_CREATE_FORBIDDEN);


            // dùng quỹ thì k cần có người chi trả
            if (req.moneySource() == MoneySource.FUND && req.transactorId() != null)
                throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL);

            if (req.moneySource() == MoneySource.PERSONAL && req.transactorId() == null)
                throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED);

            if (req.moneySource() == MoneySource.PERSONAL && !operatorId.equals(req.transactorId()))
                throw new BusinessException(ErrorCode.GROUP_TXN_CREATE_FORBIDDEN);


            return GroupTxnStatus.PENDING;
        }

        return switch (req.type()) {
            // kiểm kê: trưởng nhóm duyệt ngay, thủ quỹ chờ duyệt
            case ADJUSTMENT_UP, ADJUSTMENT_DOWN ->
                    authInfo.isOwner() ? GroupTxnStatus.CONFIRMED : GroupTxnStatus.PENDING;
            // tạo hộ hay tự tạo đều duyệt ngay vì app chỉ ghi chép; riêng hoàn tiền thì create đã kiểm người tạo là thủ quỹ
            case EXPENSE, CONTRIBUTION, REFUND -> GroupTxnStatus.CONFIRMED;
        };
    }


    //-----------------------------PRIVATE----------------------------------//
    // kiểm tra tồn tại 2 id giống nhau
    private static void checkDuplicated(List<UUID> ids) {
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (!seen.add(id)) {
                throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANT_DUPLICATED);
            }
        }
    }

    /**
     * Xác thực tính hợp lệ về số tiền của danh sách người tham gia.
     * Quy tắc:
     * - Mọi người tham gia bắt buộc phải có số tiền chia cụ thể lớn hơn 0.
     * - Tổng số tiền chia của tất cả người tham gia phải bằng tổng tiền giao dịch.
     *
     * @param participants danh sách người tham gia chia tiền
     * @param amount       tổng số tiền của hóa đơn (giao dịch)
     * @throws BusinessException nếu số tiền bị null, nhỏ hơn hoặc bằng 0, hoặc tổng không khớp
     */
    private static void validParticipantShares(
            @NonNull List<GroupTxnParticipantReq> participants, long amount
    ) {
        long remaining = amount;
        for (var p : participants) {
            // share lớn hơn phần còn lại thì tổng chắc chắn vượt, không cần cộng
            if (p.shareAmount() > remaining)
                throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_SUM_MISMATCH);
            remaining -= p.shareAmount();
        }
        if (remaining != 0)
            throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_SUM_MISMATCH);
    }
}
