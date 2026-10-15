package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.request.TxnFilterRequest;
import com.datn.financeapp.module.transaction.dto.request.TxnUpdateRequest;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.TxnListRes;
import com.datn.financeapp.module.transaction.dto.response.TxnSummaryRes;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.transaction.helper.TxnSpecifial;
import com.datn.financeapp.module.transaction.mapper.TxnMapper;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Triển khai dịch vụ quản lý giao dịch tài chính cá nhân {@link TxnBehavierService}.
 * <p>
 * Cung cấp các thao tác nghiệp vụ:
 * <ul>
 *   <li>{@link #create(UUID, TxnCreateReq)}: Tạo mới giao dịch và xác thực danh mục lá, quyền sở hữu ví.</li>
 *   <li>{@link #gets(TxnFilterRequest, Pageable)}: Truy vấn danh sách tóm tắt giao dịch phân trang theo bộ lọc.</li>
 *   <li>{@link #detail(UUID, UUID)}: Truy vấn chi tiết một giao dịch theo ID và người dùng.</li>
 *   <li>{@link #update(UUID, TxnUpdateRequest)}: Cập nhật thông tin chi tiết của giao dịch hiện có.</li>
 *   <li>{@link #softDelete(UUID, UUID)}: Xóa mềm giao dịch theo cơ chế idempotent và chặn khi gắn liền với khoản nợ.</li>
 *   <li>{@link #bulkDelete(UUID, List)}: Xóa mềm nhiều giao dịch cùng lúc.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TxnBehavierImpl implements TxnBehavierService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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
        if (req.amount() == null || req.amount() <= 0) {
            throw new BusinessException(ErrorCode.TRANSACTION_AMOUNT_INVALID);
        }

        if (req.categoryId() == null) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_REQUIRED);
        }

        // kiểm tra danh mục là lá
        var cate = categoryService.findCateIfIsleaf(req.categoryId());
        var wallet = walletService.findIfMyWallet(userId, req.walletId());

        // xây dựng entity và lưu giao dịch
        Transaction txn = txnMapper.toEntity(req);
        if (txn.getId() == null)
            txn.setId(UUID.randomUUID());

        txn.setUserId(userId);
        txn.setName(req.displayName());
        txn.setIsDeleted(false);
        txnRepository.save(txn);

        return txnMapper.toResponse(txn, cate, wallet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public TxnListRes gets(TxnFilterRequest filter, Pageable page) {
        UUID userId = SecurityContextUtil.currentUserId();

        Specification<Transaction> spec = TxnSpecifial.filter(userId, filter);

        Page<Transaction> pages = txnRepository.findAll(spec, page);

        List<TxnSummaryRes> txns = pages.getContent().stream()
                .map(txnMapper::toSummaryResponse)
                .toList();

        return new TxnListRes(
                txns,
                PageMeta.from(pages)
        );
    }

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy giao dịch
     */
    @Override
    @Transactional(readOnly = true)
    public TxnDetailResponse detail(UUID id, UUID userId) {
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        Transaction txn = txnRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        // kiểm tra danh mục là lá
        var cate = categoryService.findCateIfIsleaf(txn.getCategoryId());
        var wallet = walletService.findIfMyWallet(userId, txn.getWalletId());

        return txnMapper.toResponse(txn, cate, wallet);
    }

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy giao dịch hoặc ví không thuộc quyền sở hữu của người dùng,
     *                           hoặc dữ liệu vi phạm số tiền, ngày tương lai, danh mục lá
     */
    @Override
    @Transactional
    public TxnDetailResponse update(UUID transactionId, TxnUpdateRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        WalletRes wallet;
        CategoryRes category;

        Transaction txn = txnRepository.findByIdAndUserIdAndIsDeletedFalse(transactionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        if (req.walletId() != null) {
            txn.setWalletId(req.walletId());
            wallet = walletService.findIfMyWallet(userId, req.walletId());
        } else wallet = walletService.findIfMyWallet(userId, txn.getWalletId());

        if (req.type() == TxnType.EXPENSE || req.type() == TxnType.INCOME) {
            if (txn.getCategoryId() == null || req.categoryId() != null)
                throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_REQUIRED);
        }
        if (req.categoryId() != null) {
            txn.setCategoryId(req.categoryId());
            category = categoryService.findCateIfIsleaf(req.categoryId());
        } else {
            category = categoryService.findCateIfIsleaf(txn.getCategoryId());
        }

        // cập nhật thời gian phát sinh nếu có chỉ định ngày
        if (req.date() != null) {
            if (req.date().isAfter(LocalDate.now())) {
                throw new BusinessException(ErrorCode.TRANSACTION_DATE_IN_FUTURE);
            }
            txn.setOccurredAt(req.date().atStartOfDay(VIETNAM_ZONE).toInstant());
        }

        if (req.type() != null) txn.setType(req.type());
        if (req.amount() != null) txn.setAmount(req.amount());
        if (req.displayName() != null) txn.setName(req.displayName());
        if (req.note() != null) txn.setNote(req.note());

        txnRepository.save(txn);
        return txnMapper.toResponse(txn, category, wallet);
    }

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy giao dịch hoặc giao dịch đang liên kết với khoản nợ
     */
    @Override
    @Transactional
    public void softDelete(UUID userId, UUID transactionId) {
        if (userId == null) {
            userId = SecurityContextUtil.currentUserId();
        }

        Transaction txn = txnRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        // xử lý idempotent: nếu đã xoá trước đó thì dừng sớm
        if (Boolean.TRUE.equals(txn.getIsDeleted())) {
            return;
        }

        // chặn xoá giao dịch đang liên kết với khoản trả nợ trong sổ nợ
        if (txnRepository.existsDebtPaymentLink(transactionId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_LINKED_TO_DEBT);
        }

        txn.setIsDeleted(true);
        txnRepository.save(txn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void bulkDelete(UUID userId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // lặp xoá từng giao dịch để đảm bảo kiểm tra idempotent và ràng buộc nợ
        for (UUID id : ids) {
            softDelete(userId, id);
        }
    }

}

