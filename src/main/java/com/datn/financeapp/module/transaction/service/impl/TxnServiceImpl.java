package com.datn.financeapp.module.transaction.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.module.budget.service.BudgetSpendService;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.TxnSummaryResponse;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TransactionSource;
import com.datn.financeapp.module.transaction.helper.TransactionWriteCommand;
import com.datn.financeapp.module.transaction.helper.TransactionWriter;
import com.datn.financeapp.module.transaction.mapper.TxnMapper;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.transaction.service.TxnService;
import com.datn.financeapp.module.transaction.validator.TransactionValidator;
import com.datn.financeapp.module.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý các yêu cầu nghiệp vụ liên quan đến giao dịch tài chính đơn lẻ.
 * Cung cấp các thao tác như {@link #create(UUID, TxnCreateReq)} tạo mới giao dịch,
 * {@link #update(UUID, UUID, UpdateTransactionRequest)} cập nhật giao dịch,
 * {@link #delete(UUID, UUID)} xoá mềm giao dịch,
 * {@link #getByRecurring(UUID, UUID)} lấy giao dịch theo khoản định kỳ,
 * {@link #list(UUID, TransactionFilterRequest, PageRequestParams)} phân trang danh sách và
 * {@link #listByDate(UUID, TransactionFilterRequest)} gom nhóm theo ngày.
 */
@Service
@RequiredArgsConstructor
public class TxnServiceImpl implements TxnService {

    private final TransactionRepository transactionRepository;
    private final BudgetSpendService budgetSpendService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionWriter transactionWriter;
    private final TransactionValidator transactionValidator;
    private final JdbcTemplate jdbcTemplate;
    private final TxnMapper txnMapper;

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy ví hoặc ví không thuộc quyền sở hữu của người dùng,
     *                           hoặc dữ liệu giao dịch vi phạm quy tắc số tiền, ngày tương lai, danh mục lá
     */
    @Override
    @Transactional
    public TxnDetailResponse create(UUID userId, TxnCreateReq req) {
        // chuyển đổi thời điểm sang ngày theo múi giờ Việt Nam
        LocalDate date = req.occurredAt() != null
                ? req.occurredAt().atZone(VIETNAM_ZONE).toLocalDate()
                : LocalDate.now(VIETNAM_ZONE);

        // xác thực quyền sở hữu ví để ngăn chặn IDOR
        WalletRefResponse wallet = walletService.findRefForUser(userId, req.walletId());
        if (wallet == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví.");
        }

        // thẩm định tính hợp lệ của giao dịch theo từng loại
        transactionValidator.type(req.type(), req.amount(), req.categoryId(), date);

        // mặc định nguồn thủ công nếu không chỉ định cụ thể
        TransactionSource source = req.source() != null ? req.source() : TransactionSource.MANUAL;

        // xây dựng lệnh ghi và thực thi đồng bộ số dư ví cùng ngân sách
        TransactionWriteCommand cmd = new TransactionWriteCommand(
                null,
                userId,
                req.walletId(),
                req.categoryId(),
                req.type(),
                req.amount(),
                date,
                req.note(),
                req.displayName(),
                source,
                null,
                req.receiptUrl()
        );
        TransactionWriter.WriteResult writeResult = transactionWriter.write(cmd);

        // truy vấn lại bản ghi giao dịch vừa được lưu
        Transaction txn = transactionRepository.findByIdAndUserIdAndIsDeletedFalse(writeResult.transactionId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        return txnMapper.toResponse(txn);
    }

    @Override
    public TxnDetailResponse update(UUID userId, UUID transactionId, UpdateTransactionRequest req) {
        return null;
    }

    @Override
    public TxnDetailResponse delete(UUID userId, UUID transactionId) {
        return null;
    }

    @Override
    public long countByRecurring(UUID userId) {
        return 0;
    }

    @Override
    public List<TxnDetailResponse> getByRecurring(UUID userId, UUID recurringId) {
        return List.of();
    }

    @Override
    public List<TxnSummaryResponse> list(UUID userId, TransactionFilterRequest filters, PageRequestParams page) {
        return List.of();
    }

    @Override
    public List<TxnSummaryResponse> listByDate(UUID userId, TransactionFilterRequest filters) {
        return List.of();
    }
}
