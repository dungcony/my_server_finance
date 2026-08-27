package com.datn.financeapp.recurring.service;

import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.entity.Icon;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.category.repository.IconRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.recurring.dto.CreateRecurringRequest;
import com.datn.financeapp.recurring.dto.PauseRecurringRequest;
import com.datn.financeapp.recurring.dto.RecurringDetailResponse;
import com.datn.financeapp.recurring.dto.RecurringListItemResponse;
import com.datn.financeapp.recurring.dto.RunNowResponse;
import com.datn.financeapp.recurring.dto.UpdateRecurringRequest;
import com.datn.financeapp.recurring.entity.RecurringTransaction;
import com.datn.financeapp.recurring.repository.RecurringTransactionRepository;
import com.datn.financeapp.transaction.repository.TransactionRepository;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
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
public class RecurringService {

    private static final Set<String> VALID_FREQUENCIES = Set.of("day", "week", "month", "year");
    private static final Set<String> VALID_TYPES = Set.of("expense", "income");

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final IconRepository iconRepository;
    private final RecurringPeriodWriter periodWriter;

    // ---------------------------------------------------------------------
    // Đọc
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RecurringListItemResponse> list(UUID userId, Boolean isEnabled, String type) {
        return recurringRepository.findAllForUser(userId, isEnabled, type).stream()
                .map(this::buildListItem)
                .toList();
    }

    /** "Chi tiết kèm lịch sử đã sinh" — lịch sử là các giao dịch mang {@code recurring_id} này. */
    @Transactional(readOnly = true)
    public RecurringDetailResponse detail(UUID userId, UUID id) {
        RecurringTransaction rec = requireOwned(userId, id);

        List<RecurringDetailResponse.GeneratedTransaction> history =
                transactionRepository.findAllByRecurringIdAndIsDeletedFalseOrderByDateDesc(id).stream()
                        .map(t -> new RecurringDetailResponse.GeneratedTransaction(
                                t.getId(), t.getDate(), t.getAmount(), t.getType(), t.getSource()))
                        .toList();

        return new RecurringDetailResponse(buildListItem(rec), history);
    }

    // ---------------------------------------------------------------------
    // Ghi
    // ---------------------------------------------------------------------

    /** RECUR-01, api/09 mục A2. */
    @Transactional
    public RecurringListItemResponse create(UUID userId, CreateRecurringRequest req) {
        validateType(req.type());
        validateFrequency(req.frequency());

        int interval = req.interval() == null ? 1 : req.interval();
        if (interval < 1) {
            throw new BusinessException(
                    "INVALID_INTERVAL", HttpStatus.BAD_REQUEST.value(), "Khoảng lặp phải lớn hơn hoặc bằng 1.");
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessException(
                    "INVALID_END_DATE", HttpStatus.BAD_REQUEST.value(), "Ngày kết thúc phải sau ngày bắt đầu.");
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
                throw new BusinessException(
                        "INVALID_END_DATE", HttpStatus.BAD_REQUEST.value(), "Ngày kết thúc phải sau ngày bắt đầu.");
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

    /** RECUR-02, api/09 mục A3 — tạm dừng KHÔNG xoá lịch, bật lại là chạy tiếp từ {@code next_run_date}. */
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

        Long balance = walletRepository.findCurrentBalanceNative(rec.getWalletId()).orElse(null);
        return new RunNowResponse(
                new RunNowResponse.TransactionSummary(transactionId, rec.getType(), rec.getAmount(), LocalDate.now()),
                new RunNowResponse.NewBalance(rec.getWalletId(), balance));
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
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy khoản định kỳ."));
    }

    private Wallet requireWalletAccess(UUID walletId, UUID userId) {
        return walletRepository
                .findByIdForUser(walletId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));
    }

    /**
     * Chặn danh mục lệch loại NGAY từ lúc đặt lịch. Nếu để lọt, trigger
     * {@code trg_transactions_validate} sẽ chặn ở tầng CSDL nhưng phải đợi tới lúc tác vụ nền
     * chạy — khi đó lỗi lặp lại mỗi ngày trong log mà người dùng không hề hay biết.
     */
    private Category requireCategoryOfType(UUID categoryId, UUID userId, String type) {
        Category category = categoryRepository
                .findByIdAndVisibleToUser(categoryId, userId)
                .orElseThrow(() -> new BusinessException(
                        "CATEGORY_NOT_ALLOWED", HttpStatus.BAD_REQUEST.value(), "Danh mục không hợp lệ."));
        if (!category.getType().equals(type)) {
            throw new BusinessException(
                    "CATEGORY_TYPE_MISMATCH",
                    HttpStatus.BAD_REQUEST.value(),
                    "Danh mục thu gán cho khoản chi hoặc ngược lại.");
        }
        return category;
    }

    private void validateType(String type) {
        if (!VALID_TYPES.contains(type)) {
            // ck_rec_type chỉ cho expense/income — khoản định kỳ không sinh chuyển tiền được.
            throw new BusinessException(
                    "INVALID_TYPE", HttpStatus.BAD_REQUEST.value(), "Loại khoản định kỳ phải là expense hoặc income.");
        }
    }

    private void validateFrequency(String frequency) {
        if (!VALID_FREQUENCIES.contains(frequency)) {
            throw new BusinessException(
                    "INVALID_FREQUENCY",
                    HttpStatus.BAD_REQUEST.value(),
                    "Tần suất phải là day, week, month hoặc year.");
        }
    }

    private RecurringListItemResponse buildListItem(RecurringTransaction rec) {
        Wallet wallet = walletRepository.findById(rec.getWalletId()).orElse(null);
        Category category = categoryRepository.findById(rec.getCategoryId()).orElse(null);
        Icon icon = category == null || category.getIconId() == null
                ? null
                : iconRepository.findById(category.getIconId()).orElse(null);

        long runCount = transactionRepository.countByRecurringIdAndIsDeletedFalse(rec.getId());

        return new RecurringListItemResponse(
                rec.getId(),
                rec.getDisplayName(),
                rec.getType(),
                rec.getAmount(),
                wallet == null ? null : new RecurringListItemResponse.WalletRef(wallet.getId(), wallet.getName()),
                category == null
                        ? null
                        : new RecurringListItemResponse.CategoryRef(
                                category.getId(),
                                category.getName(),
                                icon == null
                                        ? null
                                        : new RecurringListItemResponse.IconRef(icon.getCode(), icon.getPathData()),
                                category.getColor()),
                rec.getFrequency(),
                rec.getInterval(),
                buildScheduleLabel(rec),
                rec.getStartDate(),
                rec.getEndDate(),
                rec.getNextRunDate(),
                rec.getLastRunDate(),
                ChronoUnit.DAYS.between(LocalDate.now(), rec.getNextRunDate()),
                rec.getIsEnabled(),
                runCount,
                rec.getNote());
    }

    /**
     * Câu mô tả lịch bằng tiếng Việt, tính SẴN ở server theo api/09 mục A1 ("để ứng dụng hiển thị
     * thẳng, không phải tự ghép từ {@code frequency} và {@code interval}").
     *
     * <p>Ngày trong nhãn lấy từ {@code start_date} — ngày GỐC, không phải {@code next_run_date} vốn
     * có thể đang bị làm tròn xuống ngày cuối tháng ngắn. Lấy nhầm sẽ hiện "Hằng tháng vào ngày 28"
     * cho một lịch thật ra chạy ngày 31.
     */
    private String buildScheduleLabel(RecurringTransaction rec) {
        int interval = rec.getInterval();
        return switch (rec.getFrequency()) {
            case "day" -> interval == 1 ? "Hằng ngày" : "Mỗi " + interval + " ngày";
            case "week" -> interval == 1 ? "Hằng tuần" : "Mỗi " + interval + " tuần";
            case "month" -> (interval == 1 ? "Hằng tháng" : "Mỗi " + interval + " tháng")
                    + " vào ngày " + rec.getStartDate().getDayOfMonth();
            case "year" -> (interval == 1 ? "Hằng năm" : "Mỗi " + interval + " năm")
                    + " vào ngày " + rec.getStartDate().getDayOfMonth()
                    + " tháng " + rec.getStartDate().getMonthValue();
            default -> rec.getFrequency();
        };
    }
}
