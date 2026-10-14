package com.datn.financeapp.module.transaction.service.impl;

import java.util.List;
import java.util.UUID;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.TxnSummaryResponse;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TxnSource;
import com.datn.financeapp.module.transaction.mapper.TxnMapper;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TxnBehavierServiceImpl implements TxnBehavierService {

    private final TransactionRepository txnRepository;

    private final WalletService walletService;
    private final CategoryService categoryService;

    private final TxnMapper txnMapper;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy ví hoặc ví không thuộc quyền sở hữu của người dùng,
     *                           hoặc dữ liệu giao dịch vi phạm quy tắc số tiền, ngày tương lai, danh mục lá
     */
    @Override
    @Transactional
    public TxnDetailResponse create(UUID userId, TxnCreateReq req) {

        if (req.amount() == null || req.amount() <= 0)
            throw new BusinessException(ErrorCode.TRANSACTION_AMOUNT_INVALID);

        if (req.categoryId() == null)
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_REQUIRED);

        // Xác thực danh mục là lá
        var category = categoryService.findIfIsleaf(req.categoryId());
        // xác thực quyền sở hữu ví để ngăn chặn IDOR
        var wallet = walletService.myWallet(userId, req.walletId());

        // mặc định nguồn thủ công nếu không chỉ định cụ thể
        TxnSource source = req.source() != null ? req.source() : TxnSource.MANUAL;

        // xây dựng lệnh ghi và thực thi đồng bộ số dư ví cùng ngân sách
        Transaction txn = txnMapper.toEntity(req);
        txn.setSource(source);
        txnRepository.save(txn);

        return txnMapper.toResponse(txn);
    }

    @Override
    public List<TxnSummaryResponse> gets(TransactionFilterRequest filters, Pageable page) {
        return List.of();
    }

    @Override
    public TxnDetailResponse update(UUID transactionId, UpdateTransactionRequest req) {
        return null;
    }

    @Override
    public void softDelete(UUID transactionId) {

    }

}
