package com.datn.financeapp.group.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.helper.MemberBalances;

import java.util.List;
import java.util.UUID;

public final class GroupTransactionTypeValidator {

    /**
     * Xác thực tính hợp lệ của giao dịch nộp quỹ (CONTRIBUTION).
     * <ul>
     *   <li>Nguồn tiền nộp quỹ bắt buộc phải từ tiền túi cá nhân ({@link MoneySource#PERSONAL}).</li>
     *   <li>Giao dịch nộp quỹ không được phép có danh sách người chia tiền (participants).</li>
     * </ul>
     *
     * @param moneySource  Nguồn tiền
     * @param participants Danh sách người chia tiền
     * @throws BusinessException nếu nguồn tiền không phải PERSONAL hoặc truyền kèm participants
     */
    public static void validContribution(MoneySource moneySource, List<GroupTransactionParticipantReq> participants) {
        if (moneySource != MoneySource.PERSONAL)
            throw new BusinessException(ErrorCode.MONEY_SOURCE_INVALID);

        if (participants != null && !participants.isEmpty())
            throw new BusinessException(ErrorCode.PARTICIPANTS_NOT_ALLOWED);
    }

    /**
     * Kiểm tra giới hạn hoàn trả (REFUND).
     * <p>
     * Số tiền quỹ trả cho một thành viên không được vượt quá Net Balance (số dư ròng,
     * tức số tiền người đó còn trong quỹ). Áp dụng cho cả nhóm bật và tắt quyết toán.
     * </p>
     *
     * @param transactorId ID người nhận hoàn trả
     * @param amount       Số tiền hoàn trả
     * @param mb           Kết quả balance đã tính sẵn từ BalanceCalculator
     */
    public static void validRefundLimit(UUID transactorId, long amount, MemberBalances mb) {
        long netBalance = mb.getNetBalance(transactorId);
        if (amount > netBalance) {
            throw new BusinessException(ErrorCode.CANNOT_REFUND_EXCEED_BALANCE);
        }
    }
}

