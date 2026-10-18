package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.request.TxnFilterRequest;
import com.datn.financeapp.module.transaction.dto.request.TxnUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.TxnListRes;
import com.datn.financeapp.module.transaction.dto.response.TxnSummaryRes;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.events.TxnCreateEvent;
import com.datn.financeapp.module.transaction.events.TxnDeleteEvent;
import com.datn.financeapp.module.transaction.events.TxnUpdateEvent;
import com.datn.financeapp.module.transaction.helper.TxnSpecifial;
import com.datn.financeapp.module.transaction.mapper.TxnMapper;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Triển khai dịch vụ quản lý giao dịch tài chính cá nhân {@link TxnBehavierService}.
 */
@Service
@RequiredArgsConstructor
public class TxnBehavierImpl implements TxnBehavierService {

    private final TransactionRepository txnRepository;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TxnMapper txnMapper;

    private final ApplicationEventPublisher publisher;

    @Override
    @Transactional
    public TxnDetailResponse create(UUID userId, TxnCreateReq req) {

        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        // kiểm tra danh mục là lá
        var cate = categoryService.findIfIsleaf(req.categoryId());

        // kiểm tra ví là của user
        var wallet = walletService.findSumaryByIdAndUser(req.walletId(), userId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Ví phải là của user thì mới có thể tạo giao dịch"));

        // xây dựng entity và lưu giao dịch
        Transaction txn = txnMapper.toEntity(req);

        txn.setId(UUID.randomUUID());
        txn.setUserId(userId);
        txnRepository.save(txn);

        // sự kiện cập nhật số dư ví sẽ hoàn thiện ở module wallet sau
        publisher.publishEvent(new TxnCreateEvent());

        return txnMapper.toResponse(txn, cate, wallet);
    }


    @Override
    @Transactional(readOnly = true)
    public TxnListRes gets(TxnFilterRequest filter, Pageable page, boolean isAdmin) {

        if (!isAdmin)
            filter = filter.withUser(SecurityContextUtil.currentUserId());

        Specification<Transaction> spec = TxnSpecifial.filter(filter);
        Page<Transaction> pages = txnRepository.findAll(spec, page);

        List<TxnSummaryRes> txns = pages.getContent().stream()
                .map(txnMapper::toSummaryResponse)
                .toList();

        return new TxnListRes(
                txns,
                PageMeta.from(pages)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TxnDetailResponse get(UUID id, boolean isAdmin) {

        Transaction txn = txnRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        if (!isAdmin) {
            // không cho xem giao dịch ví của người khác
            if (!txn.getUserId().equals(SecurityContextUtil.currentUserId()))
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch");

            if (txn.getIsDeleted() == true)
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch");
        }

        // xem nên k cần cate là lá
        var cate = txn.getCategoryId() == null ? null
                : categoryService.findByIdAndIsDeletedAndIsLeaf(txn.getCategoryId(), true, false).orElse(null);

        var wallet = walletService.findSumaryByIdAndUser(txn.getWalletId(), txn.getUserId(), true)
                .orElse(null);


        return txnMapper.toResponse(txn, cate, wallet);
    }


    @Override
    @Transactional
    public void update(UUID transactionId, TxnUpdateReq req, boolean isAdmin) {

        Transaction txn = txnRepository.findByIdAndIsDeletedFalse(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        if (!isAdmin && !txn.getUserId().equals(SecurityContextUtil.currentUserId()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch.");

        // Không cần thiết categoryId với mọi loại giao dịch
        if (req.occuredAt() != null) txn.setOccurredAt(req.occuredAt());
        if (req.type() != null) txn.setType(req.type());
        if (req.amount() != null) txn.setAmount(req.amount());
        if (req.displayName() != null) txn.setName(req.displayName());
        if (req.note() != null) txn.setNote(req.note());

        if (req.categoryId() != null) {
            var tmp = categoryService.findByIdAndIsDeletedAndIsLeaf(req.categoryId(), false, true);
            if (tmp.isEmpty())
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "danh mục truyền lên cần phải tồn tại và không được phép tồn tại danh mục con");
            txn.setCategoryId(req.categoryId());
        }

        if (req.walletId() != null) {
            var tmp = walletService.findSumaryByIdAndUser(req.walletId(), txn.getUserId(), false);
            if (tmp.isEmpty())
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ví truyền lên cần phải tồn tại và là của bạn");

            txn.setWalletId(req.walletId());
        }

        txnRepository.save(txn);

        // sự kiện cập nhật số dư ví sẽ hoàn thiện ở module wallet sau
        publisher.publishEvent(new TxnUpdateEvent());
    }

    @Override
    @Transactional
    public void softDelete(UUID transactionId, boolean isAdmin) {

        Transaction txn = txnRepository.findByIdAndIsDeletedFalse(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        if (!isAdmin && !txn.getUserId().equals(SecurityContextUtil.currentUserId()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch.");

        txn.setIsDeleted(true);
        txnRepository.save(txn);

        // sự kiện cập nhật số dư ví sẽ hoàn thiện ở module wallet sau
        publisher.publishEvent(new TxnDeleteEvent());
    }


    @Override
    @Transactional
    public void bulkDelete(UUID userId, List<UUID> ids) {
    }

}

