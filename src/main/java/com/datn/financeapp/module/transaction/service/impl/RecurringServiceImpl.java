package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.module.transaction.helper.RecurringPeriodWriter;
import com.datn.financeapp.module.transaction.service.RecurringService;

import com.datn.financeapp.module.category.dto.response.CategoryResponse;
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
import com.datn.financeapp.module.transaction.enums.FreequenType;
import com.datn.financeapp.module.transaction.enums.TransactionType;
import com.datn.financeapp.module.transaction.mapper.RecurringMapper;
import com.datn.financeapp.module.transaction.repository.RecurringTransactionRepository;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.transaction.service.TxnService;
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
 * {@link TxnService} và {@link RecurringPeriodWriter}.
 */
@Service
@RequiredArgsConstructor
public class RecurringServiceImpl implements RecurringService {

    // khoản định kỳ chỉ sinh được thu hoặc chi, không sinh cho vay
    private static final Set<TransactionType> VALID_TYPES = Set.of(TransactionType.EXPENSE, TransactionType.INCOME);

    private final RecurringTransactionRepository recurringRepository;
    private final TxnService txnService;
    private final TransactionRepository transactionRepository;
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
        return recurringRepository.findAllForUser(userId, isEnabled, type == null ? null : type.toUpperCase()).stream()
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
                txnService.getByRecurring(userId, id).stream()
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
        TransactionType type = parseType(req.type());
        FreequenType frequency = parseFrequency(req.frequency());

        int interval = req.interval() == null ? 1 : req.interval();
        if (interval < 1) {
            throw new BusinessException(ErrorCode.RECURRING_INTERVAL_INVALID);
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessException(ErrorCode.RECURRING_END_DATE_INVALID);
        }

        requireWalletAccess(req.walletId(), userId);
        requireCategory(req.categoryId());

        RecurringTransaction rec = RecurringTransaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .walletId(req.walletId())
                .categoryId(req.categoryId())
                .type(type)
                .amount(req.amount())
                .name(req.displayName())
                .note(req.note())
                .frequency(frequency)
                .interval(interval)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .nextRunDate(req.startDate())
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
            rec.setName(req.displayName());
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
            requireCategory(req.categoryId());
            rec.setCategoryId(req.categoryId());
        }
        if (req.frequency() != null) {
            rec.setFrequency(parseFrequency(req.frequency()));
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
                transactionId, rec.getType().name().toLowerCase(), rec.getAmount(), LocalDate.now(), rec.getWalletId(), balance);
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
     * Danh mục hiện không phân biệt thu/chi nên chỉ cần danh mục tồn tại và chưa bị xoá
     * ({@link CategoryService#findActiveById} ném {@code CATEGORY_NOT_FOUND} nếu không có).
     */
    private CategoryResponse requireCategory(UUID categoryId) {
        return categoryService.findActiveById(categoryId);
    }

    private TransactionType parseType(String type) {
        // lỗi chung cho cả giá trị lạ lẫn LOAN: khoản định kỳ chỉ sinh được thu hoặc chi
        try {
            TransactionType parsed = TransactionType.valueOf(type.toUpperCase());
            if (VALID_TYPES.contains(parsed)) {
                return parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // rơi xuống ném lỗi nghiệp vụ bên dưới
        }
        throw new BusinessException(ErrorCode.RECURRING_TYPE_INVALID);
    }

    private FreequenType parseFrequency(String frequency) {
        try {
            return FreequenType.valueOf(frequency.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.RECURRING_FREQUENCY_INVALID);
        }
    }

    private RecurringListItemResponse buildListItem(RecurringTransaction rec) {
        WalletRefResponse wallet = walletService.findRefById(rec.getWalletId());
        CategoryResponse category = categoryService.findActiveById(rec.getCategoryId());

        long runCount = transactionRepository.countByRecurringIdAndIsDeletedFalse(rec.getId());

        return recurringMapper.toListItemResponse(rec, wallet, category, runCount);
    }
}
