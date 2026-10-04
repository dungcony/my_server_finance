package com.datn.financeapp.group.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.service.MemberService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Validator chuyên trách kiểm tra các ràng buộc nghiệp vụ (Domain Constraints) của giao dịch nhóm.
 * <p>
 * Đảm bảo các quy tắc kiểm tra logic phức tạp được đóng gói độc lập, tái sử dụng giữa Create và Update,
 * tuân thủ nguyên tắc Single Responsibility Principle (SRP).
 * </p>
 */
@Component
@RequiredArgsConstructor
public class GroupTransactionPaticipantValidator {

    private final MemberService memberService;

    /**
     * Kiểm tra thời điểm xảy ra giao dịch (không được là thời gian trong tương lai).
     *
     * @param occurredAt Thời điểm phát sinh giao dịch
     * @throws BusinessException nếu thời điểm lớn hơn hiện tại ({@link ErrorCode#GROUP_TXN_DATE_IN_FUTURE})
     */
    public void timeNotFuture(Instant occurredAt) {
        if (occurredAt != null && occurredAt.isAfter(Instant.now()))
            throw new BusinessException(ErrorCode.GROUP_TXN_DATE_IN_FUTURE);
    }

    /**
     * kiểm tra người tạo, chi, và người đc chỉ định tham gia là ở trong nhóm
     * chỉ bằng đúng 1 câu query IN duy nhất tới Database (WHERE user_id IN (:candidateIds)).
     *
     * @param groupId      ID nhóm
     * @param transactorId ID người đứng tên giao dịch
     * @param participants Danh sách người chia tiền
     * @param amount       Số tiền giao dịch
     */
    public void validTransactorAndParticipants(
            UUID groupId,
            UUID transactorId,
            List<GroupTransactionParticipantReq> participants,
            long amount
    ) {
        // nếu không truyền người chia tiền thì chỉ kiểm tra người chi tiền có trong nhóm không
        if (participants == null || participants.isEmpty()) {
            if (!memberService.allMemberInGroup(groupId, List.of(transactorId))) {
                throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP);
            }
            return;
        }

        // kiểm tra có bị trùng lặp id khi gửi lên k
        List<UUID> candidateIds = new ArrayList<>(participants.stream()
                .map(GroupTransactionParticipantReq::userId)
                .toList());

        checkExists(candidateIds);

        if (!candidateIds.contains(transactorId))
            candidateIds.add(transactorId);

        // Kiểm tra format chia tiền và tổng số tiền
        validParticipantShares(participants, amount);

        // kiểm tra tư cách thành viên cho toàn bộ ID cần xét
        if (!memberService.allMemberInGroup(groupId, candidateIds))
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP);
    }

    //-----------------------------PRIVATE----------------------------------//
    // kiểm tra tồn tại 2 id giống nhau
    private static void checkExists(List<UUID> ids) {

        // Dùng Set để lấy danh sách ID không trùng lặp
        Set<UUID> uniqueParticipantIds = new HashSet<>(ids);
        // So sánh size của Set với size của List ban đầu để phát hiện trùng lặp
        if (uniqueParticipantIds.size() != ids.size()) {
            throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANT_DUPLICATED);
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
    private void validParticipantShares(List<GroupTransactionParticipantReq> participants, long amount) {
        if (participants.isEmpty()) {
            return;
        }

        long sum = 0;
        for (var p : participants) {
            if (p.shareAmount() == null || p.shareAmount() <= 0) {
                throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_AMOUNT_INVALID,
                        "Số tiền chia của thành viên phải lớn hơn 0");
            }
            sum += p.shareAmount();
        }

        if (sum != amount) {
            throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_SUM_MISMATCH);
        }
    }
}
