package com.datn.financeapp.recurring.service.impl;

import com.datn.financeapp.recurring.service.RecurringService;

import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.category.service.CategoryService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.recurring.dto.request.CreateRecurringRequest;
import com.datn.financeapp.recurring.dto.request.PauseRecurringRequest;
import com.datn.financeapp.recurring.dto.response.RecurringDetailResponse;
import com.datn.financeapp.recurring.dto.response.RecurringListItemResponse;
import com.datn.financeapp.recurring.dto.response.RunNowResponse;
import com.datn.financeapp.recurring.dto.request.UpdateRecurringRequest;
import com.datn.financeapp.recurring.entity.RecurringTransaction;
import com.datn.financeapp.recurring.mapper.RecurringMapper;
import com.datn.financeapp.recurring.repository.RecurringTransactionRepository;
import com.datn.financeapp.transaction.dto.response.GeneratedTransactionResponse;
import com.datn.financeapp.transaction.service.TransactionService;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.wallet.service.WalletService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic phần request-response của khoản định kỳ (RECUR-01..03, api/09 Phần A).
 *
 * <p><b>Ranh giới trách nhiệm:</b> lớp này KHÔNG tự ghi giao dịch hay đụng số dư ví. Mọi việc sinh
 * giao dịch đi qua {@link RecurringPeriodWriter} → {@code TransactionWriter} (D-31), nơi đã cài
 * đúng luồng cập nhật số dư của CLAUDE.md quy tắc 4-5.
 *
 * <p><b>Tạo lịch KHÔNG sinh giao dịch ngay</b> (api/09 mục A2 "Việc máy chủ phải làm" #4) — chỉ
 * đặt {@code next_run_date = start_date} rồi chờ tác vụ nền. Sinh ngay sẽ trừ oan ví một kỳ mà
 * người dùng chưa hề tiêu.
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

    @Transactional(readOnly = true)
    public List<RecurringListItemResponse> list(UUID userId, Boolean isEnabled, String type) {
        return recurringRepository.findAllForUser(userId, isEnabled, type).stream()
                .map(this::buildListItem)
                .toList();
    }

    // "Chi tiết kèm lịch sử đã sinh" — lịch sử là các giao dịch mang {@code recurring_id} này.
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

    // RECUR-01, api/09 mục A2.
    @Transactional
    public RecurringListItemResponse create(UUID userId, CreateRecurringRequest req) {
        validateType(req.type());
        validateFrequency(req.frequency());

        int interval = req.interval() == null ? 1 : req.interval();
        if (interval < 1) {
            throw new BusinessException(ErrorCode.INVALID_INTERVAL);
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_END_DATE);
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
                // api/09 mục A2 #2-3: lịch bắt đầu đúng ngày người dùng chọn, bật sẵn, chưa chạy
                // lần nào. TUYỆT ĐỐI không gọi TransactionWriter ở đây.
                .nextRunDate(req.startDate())
                .lastRunDate(null)
                .isEnabled(true)
                .createdAt(Instant.now())
                .build();
        recurringRepository.saveAndFlush(rec);

        return buildListItem(rec);
    }

    /**
     * Sửa lịch. {@code start_date} KHÔNG cho sửa: nó là mốc gốc quyết định "ngày trong tháng" của
     * mọi kỳ tương lai (xem {@code RecurringDateCalculator}), đổi nó sẽ làm các kỳ đã sinh và các
     * kỳ sắp tới không còn cùng một quy luật. Muốn đổi ngày thì xoá và tạo lịch mới.
     */
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
                throw new BusinessException(ErrorCode.INVALID_END_DATE);
            }
            rec.setEndDate(req.endDate());
        }

        recurringRepository.saveAndFlush(rec);
        return buildListItem(rec);
    }

    /**
     * <b>Xoá CỨNG, có chủ đích.</b> {@code recurring_transactions} không có cột {@code is_deleted}
     * (schema V2) nên không xoá mềm được; còn đặt {@code is_enabled = false} là ngữ nghĩa TẠM DỪNG,
     * đã có endpoint {@code pause} riêng — dùng nó thay cho DELETE sẽ khiến lịch đã xoá vẫn hiện
     * trong danh sách khi lọc {@code is_enabled=false}.
     *
     * <p>An toàn vì {@code fk_txn_recurring ON DELETE SET NULL}: các giao dịch đã sinh vẫn nằm
     * nguyên trong {@code transactions}, chỉ mất đường liên kết ngược về lịch. Tiền đã tiêu thật
     * thì không được biến mất khỏi sổ.
     */
    @Transactional
    public void delete(UUID userId, UUID id) {
        RecurringTransaction rec = requireOwned(userId, id);
        recurringRepository.delete(rec);
    }

    // RECUR-02, api/09 mục A3 — tạm dừng KHÔNG xoá lịch, bật lại là chạy tiếp từ {@code next_run_date}.
    @Transactional
    public RecurringListItemResponse pause(UUID userId, UUID id, PauseRecurringRequest req) {
        RecurringTransaction rec = requireOwned(userId, id);
        rec.setIsEnabled(req.isEnabled());
        recurringRepository.saveAndFlush(rec);
        return buildListItem(rec);
    }

    /**
     * RECUR-02, api/09 mục A3 — ghi ngay không đợi tới hạn.
     *
     * <p>Kiểm tra quyền ở ĐÂY (qua {@link #requireOwned}), rồi mới uỷ quyền cho
     * {@link RecurringPeriodWriter} vốn là bean hạ tầng không biết gì về người dùng đang đăng nhập.
     */
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
            throw new BusinessException(ErrorCode.CATEGORY_NOT_ALLOWED);
        }
        if (!category.type().equals(type)) {
            throw new BusinessException(ErrorCode.CATEGORY_TYPE_MISMATCH);
        }
        return category;
    }

    private void validateType(String type) {
        if (!VALID_TYPES.contains(type)) {
            // ck_rec_type chỉ cho expense/income — khoản định kỳ không sinh chuyển tiền được.
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
    }

    private void validateFrequency(String frequency) {
        if (!VALID_FREQUENCIES.contains(frequency)) {
            throw new BusinessException(ErrorCode.INVALID_FREQUENCY);
        }
    }

    private RecurringListItemResponse buildListItem(RecurringTransaction rec) {
        WalletRefResponse wallet = walletService.findRefById(rec.getWalletId());
        CategoryRefResponse category = categoryService.findRefById(rec.getCategoryId());

        long runCount = transactionService.countByRecurringId(rec.getId());

        return recurringMapper.toListItemResponse(rec, wallet, category, runCount);
    }
}
