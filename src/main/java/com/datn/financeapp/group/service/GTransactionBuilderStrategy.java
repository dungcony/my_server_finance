package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.MemberAuthInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Interface chiến lược xây dựng đối tượng giao dịch tài chính nhóm.
 * <p>
 * Định nghĩa các phương thức:
 * <ul>
 * <li>{@link #supports(TransactionType)}: Kiểm tra loại giao dịch hỗ trợ.</li>
 * <li>{@link #build(UUID, UUID, GroupTransactionCreateReq, TransactionStatus, boolean)}:
 * Xây dựng thực thể giao dịch theo nghiệp vụ riêng.</li>
 * <li>{@link #baseBuilder(UUID, UUID, GroupTransactionCreateReq, MoneySource, TransactionStatus)}:
 * Khởi tạo builder dùng chung cho các trường cơ bản.</li>
 * </ul>
 * </p>
 */
public interface GTransactionBuilderStrategy {

    // kiểm tra strategy có phụ trách loại giao dịch truyền vào hay không
    boolean supports(TransactionType type);

    // mỗi strategy tự quyết định trạng thái giao dịch dựa vào quyền người thực hiện
    TransactionStatus determineStatus(MemberAuthInfo authInfo);

    // xây dựng entity giao dịch tương ứng theo nghiệp vụ riêng
    GTransaction build(UUID operatorId, UUID groupId, GroupTransactionCreateReq req,
                       TransactionStatus status, boolean isSettlementEnabled);

    // hàm dùng chung khởi tạo base builder cho các strategy tránh lặp code
    default GTransaction.GTransactionBuilder baseBuilder(UUID operatorId, UUID groupId,
                                                         GroupTransactionCreateReq req, MoneySource moneySource, TransactionStatus status) {
        Instant now = Instant.now();
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .transactorId(req.transactorId())
                .type(req.type())
                .amount(req.amount())
                .occurredAt(req.resolveOccurredAt())
                .note(req.note())
                .moneySource(moneySource)
                .status(status)
                .createdBy(operatorId)
                .createdAt(now)
                .updatedAt(now)
                .reviewedBy(status == TransactionStatus.CONFIRMED ? operatorId : null)
                .reviewedAt(status == TransactionStatus.CONFIRMED ? now : null);
    }
}
