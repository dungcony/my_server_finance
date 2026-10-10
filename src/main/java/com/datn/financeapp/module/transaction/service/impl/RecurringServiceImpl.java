package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.module.transaction.service.RecurringService;

import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.transaction.dto.request.CreateRecurringRequest;
import com.datn.financeapp.module.transaction.dto.request.PauseRecurringRequest;
import com.datn.financeapp.module.transaction.dto.response.RecurringDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.RecurringListItemResponse;
import com.datn.financeapp.module.transaction.dto.response.RunNowResponse;
import com.datn.financeapp.module.transaction.dto.request.UpdateRecurringRequest;
import com.datn.financeapp.module.transaction.entity.RecurringTransaction;
import com.datn.financeapp.module.transaction.mapper.RecurringMapper;
import com.datn.financeapp.module.transaction.repository.RecurringTransactionRepository;
import com.datn.financeapp.module.transaction.service.TransactionService;
import com.datn.financeapp.module.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.module.wallet.service.WalletService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý các yêu cầu nghiệp vụ liên quan đến giao dịch định kỳ.
 * Triển khai giao diện {@link RecurringService}, tương tác với {@link RecurringTransactionRepository},
 * {@link TransactionService} và {@link RecurringPeriodWriter}.
 */
@Service
@RequiredArgsConstructor
public class RecurringServiceImpl implements RecurringService {

    private static final Set<String> VALID_FREQUENCIES = Set.of("day", "week", "month", "year");
    private static final Set<String> VALID_TYPES = Set.of("expense", "income");

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionService transactionService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final RecurringPeriodWriter periodWriter;
    private final RecurringMapper recurringMapper;

    // ---------------------------------------------------------------------
    // Đọc
    // ---------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<RecurringListItemResponse> list(UUID userId, Boolean isEnabled, String type) {
        return recurringRepository.findAllForUser(userId, isEnabled, type).stream()
                .map(this::buildListItem)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public RecurringDetailResponse detail(UUID userId, UUID id) {
        RecurringTransaction rec = requireOwned(userId, id);

        List<RecurringDetailResponse.GeneratedTransaction> history =
                transactionService.findGeneratedByRecurringId(id).stream()
                        .map(recurringMapper::toGeneratedTransaction)
                        .toList();

        return recurringMapper.toDetailResponse(buildListItem(rec), history);
    }

    // ---------------------------------------------------------------------
    // Ghi
    // ---------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecurringListItemResponse create(UUID userId, CreateRecurringRequest req) {
        validateType(req.type());
        validateFrequency(req.frequency());

        int interval = req.interval() == null ? 1 : req.interval();
        if (interval < 1) {
            throw new BusinessException(ErrorCode.RECURRING_INTERVAL_INVALID);
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessException(ErrorCode.RECURRING_END_DATE_INVALID);
        }

        requireWalletAccess(req.walletId(), userId);
        requireCategoryOfType(req.categoryId(), userId, req.type());

        RecurringTransaction rec = RecurringTransaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .walletId(req.walletId())
                .categoryId(req.categoryId())
                .type(req.type())
                .amount(req.amount())
                .displayName(req.displayName())
                .note(req.note())
                .frequency(req.frequency())
                .interval(interval)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .nextRunDate(req.startDate())
                .lastRunDate(null)
                .isEnabled(true)
                .createdAt(Instant.now())
                .build();
        recurringRepository.saveAndFlush(rec);

        return buildListItem(rec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecurringListItemResponse update(UUID userId, UUID id, UpdateRecurringRequest req) {
        RecurringTransaction rec = requireOwned(userId, id);

        if (req.displayName() != null) {
            rec.setDisplayName(req.displayName());
        }
        if (req.amount() != null) {
            rec.setAmount(req.amount());
        }
        if (req.note() != null) {
            rec.setNote(req.note());
        }
        if (req.walletId() != null) {
            requireWalletAccess(req.walletId(), userId);
            rec.setWalletId(req.walletId());
        }
        if (req.categoryId() != null) {
            requireCategoryOfType(req.categoryId(), userId, rec.getType());
            rec.setCategoryId(req.categoryId());
        }
        if (req.frequency() != null) {
            validateFrequency(req.frequency());
            rec.setFrequency(req.frequency());
        }
        if (req.interval() != null) {
            rec.setInterval(req.interval());
        }
        if (req.endDate() != null) {
            if (req.endDate().isBefore(rec.getStartDate())) {
                throw new BusinessException(ErrorCode.RECURRING_END_DATE_INVALID);
            }
            rec.setEndDate(req.endDate());
        }

        recurringRepository.saveAndFlush(rec);
        return buildListItem(rec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        RecurringTransaction rec = requireOwned(userId, id);
        recurringRepository.delete(rec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecurringListItemResponse pause(UUID userId, UUID id, PauseRecurringRequest req) {
        RecurringTransaction rec = requireOwned(userId, id);
        rec.setIsEnabled(req.isEnabled());
        recurringRepository.saveAndFlush(rec);
        return buildListItem(rec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RunNowResponse runNow(UUID userId, UUID id) {
        RecurringTransaction rec = requireOwned(userId, id);

        UUID transactionId = periodWriter.runSinglePeriodNow(id);

        Long balance = walletService.findRawBalance(rec.getWalletId());
        return recurringMapper.toRunNowResponse(
                transactionId, rec.getType(), rec.getAmount(), LocalDate.now(), rec.getWalletId(), balance);
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    /**
     * Điều kiện quyền nằm ngay trong SQL của repository (CLAUDE.md §7). Không có quyền -> 404 chứ
     * không phải 403, để không lộ việc bản ghi có tồn tại hay không (T-04-13).
     */
    private RecurringTransaction requireOwned(UUID userId, UUID id) {
        return recurringRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khoản định kỳ."));
    }

    private WalletRefResponse requireWalletAccess(UUID walletId, UUID userId) {
        WalletRefResponse wallet = walletService.findRefForUser(userId, walletId);
        if (wallet == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví.");
        }
        return wallet;
    }

    /**
     * Chặn danh mục lệch loại NGAY từ lúc đặt lịch. Nếu để lọt, trigger
     * {@code trg_transactions_validate} sẽ chặn ở tầng CSDL nhưng phải đợi tới lúc tác vụ nền
     * chạy — khi đó lỗi lặp lại mỗi ngày trong log mà người dùng không hề hay biết.
     */
    private CategoryRefResponse requireCategoryOfType(UUID categoryId, UUID userId, String type) {
        CategoryRefResponse category = categoryService.findRefVisibleToUser(categoryId, userId);
        if (category == null) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_NOT_ALLOWED);
        }
        if (!category.type().equals(type)) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH);
        }
        return category;
    }

    private void validateType(String type) {
        if (!VALID_TYPES.contains(type)) {
            // ck_rec_type chỉ cho expense/income — khoản định kỳ không sinh chuyển tiền được.
            throw new BusinessException(ErrorCode.RECURRING_TYPE_INVALID);
        }
    }

    private void validateFrequency(String frequency) {
        if (!VALID_FREQUENCIES.contains(frequency)) {
            throw new BusinessException(ErrorCode.RECURRING_FREQUENCY_INVALID);
        }
    }

    private RecurringListItemResponse buildListItem(RecurringTransaction rec) {
        WalletRefResponse wallet = walletService.findRefById(rec.getWalletId());
        CategoryRefResponse category = categoryService.findRefById(rec.getCategoryId());

        long runCount = transactionService.countByRecurringId(rec.getId());

        return recurringMapper.toListItemResponse(rec, wallet, category, runCount);
    }
}
