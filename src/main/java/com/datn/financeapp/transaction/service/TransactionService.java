package com.datn.financeapp.transaction.service;

import com.datn.financeapp.budget.repository.BudgetProgressRepository;
import com.datn.financeapp.budget.service.BudgetService;
import com.datn.financeapp.budget.repository.BudgetProgressRepository.BudgetProgressProjection;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.entity.Icon;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.category.repository.IconRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.transaction.dto.AffectedBudgetResponse;
import com.datn.financeapp.transaction.dto.CreateTransactionRequest;
import com.datn.financeapp.transaction.dto.CreateTransactionResponse;
import com.datn.financeapp.transaction.dto.DeleteTransactionResponse;
import com.datn.financeapp.transaction.dto.DuplicateTransactionRequest;
import com.datn.financeapp.transaction.dto.TransactionByDateResponse;
import com.datn.financeapp.transaction.dto.TransactionDetailResponse;
import com.datn.financeapp.transaction.dto.TransactionFilterParams;
import com.datn.financeapp.transaction.dto.TransactionListItemResponse;
import com.datn.financeapp.transaction.dto.TransactionListResponse;
import com.datn.financeapp.transaction.dto.TransactionResponse;
import com.datn.financeapp.transaction.dto.TransactionSummaryDto;
import com.datn.financeapp.transaction.dto.UpdateTransactionRequest;
import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.transaction.repository.TransactionRepository;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD giao dịch đơn lẻ (TXN-03, TXN-05, TXN-06, TXN-07) — module lõi rủi ro cao nhất của dự án.
 * {@code create()}/{@code duplicate()} ghi bản ghi mới qua {@link TransactionWriter} (D-31, điểm
 * ghi duy nhất). {@code update()}/{@code delete()} sửa TẠI CHỖ nên gọi trực tiếp
 * {@link TransactionRepository#save} + {@link WalletRepository#adjustBalance} trong CÙNG một
 * {@code @Transactional} — {@code TransactionWriter.write()} luôn INSERT bản ghi mới, không phù
 * hợp cho UPDATE tại chỗ.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final IconRepository iconRepository;
    private final TransactionWriter transactionWriter;
    private final JdbcTemplate jdbcTemplate;
    private final BudgetProgressRepository budgetProgressRepository;

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * api/04-GIAO-DICH.md mục 4. Validate ràng buộc theo {@code type} Ở TẦNG SERVICE TRƯỚC KHI
     * chạm CSDL — không dựa vào {@code ck_txn_shape}/trigger để báo lỗi nghiệp vụ (constraint DB
     * là lưới an toàn cuối). KHÔNG chặn ngày tương lai (D-36) — không có bất kỳ
     * {@code if (date.isAfter(...))} nào ở đây.
     */
    @Transactional
    public CreateTransactionResponse create(UUID userId, CreateTransactionRequest req) {
        validateShape(req.type(), req.categoryId(), req.destinationWalletId(), req.walletId());

        if (req.amount() == null || req.amount() <= 0) {
            throw new BusinessException(
                    "INVALID_AMOUNT", HttpStatus.BAD_REQUEST.value(), "Số tiền phải lớn hơn 0.");
        }

        if ("transfer".equals(req.type())) {
            lockWalletsInOrder(Set.of(req.walletId(), req.destinationWalletId()), userId);
        } else {
            lockWalletsInOrder(Set.of(req.walletId()), userId);
        }

        Category category = null;
        if (req.categoryId() != null) {
            category = categoryRepository
                    .findByIdAndVisibleToUser(req.categoryId(), userId)
                    .orElseThrow(() -> new BusinessException(
                            "CATEGORY_NOT_ALLOWED", HttpStatus.BAD_REQUEST.value(), "Danh mục không hợp lệ."));
            if (!category.getType().equals(req.type())) {
                throw new BusinessException(
                        "CATEGORY_TYPE_MISMATCH",
                        HttpStatus.BAD_REQUEST.value(),
                        "Danh mục thu gán cho khoản chi hoặc ngược lại.");
            }
        }

        LocalDate date = req.date() != null ? req.date() : LocalDate.now();
        TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                UUID.randomUUID(),
                userId,
                req.walletId(),
                req.destinationWalletId(),
                req.categoryId(),
                req.type(),
                req.amount(),
                date,
                req.note(),
                req.displayName(),
                req.source() != null ? req.source() : "manual",
                req.countsInReport() == null || req.countsInReport(),
                null,
                req.draftId(),
                req.receiptUrl()));

        Transaction saved = transactionRepository.findById(result.transactionId()).orElseThrow();
        return new CreateTransactionResponse(
                toResponse(saved),
                new CreateTransactionResponse.NewBalance(req.walletId(), result.walletNewBalance()),
                computeAffectedBudgets(userId, req.type(), req.categoryId(), date));
    }

    /**
     * api/04-GIAO-DICH.md mục 8 — trình tự 3 bước bất di bất dịch (CLAUDE.md §4), toàn bộ trong
     * MỘT {@code @Transactional}: hoàn tác ảnh hưởng CŨ → ghi giá trị MỚI → áp dụng ảnh hưởng
     * MỚI. Khoá tối đa 4 ví (D-33) theo {@code UUID.compareTo()} tăng dần, loại trùng trước khi
     * khoá.
     */
    @Transactional
    public CreateTransactionResponse update(UUID userId, UUID transactionId, UpdateTransactionRequest req) {
        Transaction old = transactionRepository
                .findByIdAndUserIdAndIsDeletedFalse(transactionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy giao dịch."));

        validateShape(req.type(), req.categoryId(), req.destinationWalletId(), req.walletId());

        if (req.amount() == null || req.amount() <= 0) {
            throw new BusinessException(
                    "INVALID_AMOUNT", HttpStatus.BAD_REQUEST.value(), "Số tiền phải lớn hơn 0.");
        }

        Set<UUID> walletIds = new HashSet<>();
        walletIds.add(old.getWalletId());
        if (old.getDestinationWalletId() != null) {
            walletIds.add(old.getDestinationWalletId());
        }
        walletIds.add(req.walletId());
        if (req.destinationWalletId() != null) {
            walletIds.add(req.destinationWalletId());
        }
        lockWalletsInOrder(walletIds, userId);

        if (req.categoryId() != null) {
            Category category = categoryRepository
                    .findByIdAndVisibleToUser(req.categoryId(), userId)
                    .orElseThrow(() -> new BusinessException(
                            "CATEGORY_NOT_ALLOWED", HttpStatus.BAD_REQUEST.value(), "Danh mục không hợp lệ."));
            if (!category.getType().equals(req.type())) {
                throw new BusinessException(
                        "CATEGORY_TYPE_MISMATCH",
                        HttpStatus.BAD_REQUEST.value(),
                        "Danh mục thu gán cho khoản chi hoặc ngược lại.");
            }
        }

        // Bước 1 — HOÀN TÁC ảnh hưởng CŨ lên (các) ví CŨ.
        applyEffect(old.getType(), old.getWalletId(), old.getDestinationWalletId(), old.getAmount(), true);

        // Bước 2 — GHI giá trị MỚI vào bản ghi (cùng id, không tạo entity mới).
        old.setType(req.type());
        old.setAmount(req.amount());
        old.setWalletId(req.walletId());
        old.setDestinationWalletId(req.destinationWalletId());
        old.setCategoryId(req.categoryId());
        old.setDate(req.date() != null ? req.date() : LocalDate.now());
        old.setNote(req.note());
        old.setDisplayName(req.displayName());
        if (req.receiptUrl() != null) {
            old.setReceiptUrl(req.receiptUrl());
        }
        if (req.countsInReport() != null) {
            old.setCountsInReport(req.countsInReport());
        }
        old.setUpdatedAt(Instant.now());
        transactionRepository.save(old);

        // Bước 3 — ÁP DỤNG ảnh hưởng MỚI lên (các) ví MỚI.
        applyEffect(req.type(), req.walletId(), req.destinationWalletId(), req.amount(), false);

        long walletNewBalance = walletRepository.findCurrentBalanceNative(req.walletId()).orElseThrow();

        Transaction reloaded = transactionRepository.findById(transactionId).orElseThrow();
        return new CreateTransactionResponse(
                toResponse(reloaded),
                new CreateTransactionResponse.NewBalance(req.walletId(), walletNewBalance),
                computeAffectedBudgets(userId, req.type(), req.categoryId(), old.getDate()));
    }

    /**
     * api/04-GIAO-DICH.md mục 9 (D-32). Chặn TRƯỚC khi hoàn tác số dư — không có đường nào bỏ
     * qua kiểm tra {@code existsDebtPaymentLink}. TUYỆT ĐỐI KHÔNG ghi
     * {@code debts.paid_amount}/{@code debts.status} — cột do trigger V4 sở hữu.
     */
    @Transactional
    public DeleteTransactionResponse delete(UUID userId, UUID transactionId) {
        Transaction txn = transactionRepository
                .findByIdAndUserIdAndIsDeletedFalse(transactionId, userId)
                .orElse(null);

        if (txn == null) {
            // Phân biệt "đã xoá idempotent" (200, CORE-06) với "không tồn tại/của người khác"
            // (404) — copy khuôn WalletService.delete().
            Transaction existing = transactionRepository
                    .findByIdAndUserId(transactionId, userId)
                    .orElseThrow(() -> new BusinessException(
                            "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy giao dịch."));
            // Đã xoá mềm từ trước — idempotent, không làm gì thêm, không ném lỗi.
            long balance = walletRepository.findCurrentBalanceNative(existing.getWalletId()).orElseThrow();
            return new DeleteTransactionResponse(
                    new DeleteTransactionResponse.NewBalance(existing.getWalletId(), balance));
        }

        if (transactionRepository.existsDebtPaymentLink(transactionId)) {
            throw new BusinessException(
                    "TRANSACTION_LINKED_TO_DEBT",
                    HttpStatus.CONFLICT.value(),
                    "Giao dịch này là một lần trả nợ — huỷ ở sổ nợ, không xoá trực tiếp.");
        }

        Set<UUID> walletIds = new HashSet<>();
        walletIds.add(txn.getWalletId());
        if (txn.getDestinationWalletId() != null) {
            walletIds.add(txn.getDestinationWalletId());
        }
        lockWalletsInOrder(walletIds, userId);

        applyEffect(txn.getType(), txn.getWalletId(), txn.getDestinationWalletId(), txn.getAmount(), true);

        txn.setIsDeleted(true);
        txn.setUpdatedAt(Instant.now());
        transactionRepository.save(txn);

        long walletNewBalance = walletRepository.findCurrentBalanceNative(txn.getWalletId()).orElseThrow();
        return new DeleteTransactionResponse(
                new DeleteTransactionResponse.NewBalance(txn.getWalletId(), walletNewBalance));
    }

    /**
     * api/04-GIAO-DICH.md mục 10 (TXN-07). Ngày mặc định HÔM NAY (không phải ngày gốc),
     * {@code source} LUÔN {@code "manual"} (không kế thừa nguồn gốc).
     */
    @Transactional
    public CreateTransactionResponse duplicate(UUID userId, UUID transactionId, DuplicateTransactionRequest req) {
        Transaction original = transactionRepository
                .findByIdAndUserIdAndIsDeletedFalse(transactionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy giao dịch."));

        LocalDate date = (req != null && req.date() != null) ? req.date() : LocalDate.now();
        Long amount = (req != null && req.amount() != null) ? req.amount() : original.getAmount();

        TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                UUID.randomUUID(),
                userId,
                original.getWalletId(),
                original.getDestinationWalletId(),
                original.getCategoryId(),
                original.getType(),
                amount,
                date,
                original.getNote(),
                original.getDisplayName(),
                "manual",
                original.getCountsInReport(),
                null,
                null,
                original.getReceiptUrl()));

        Transaction saved = transactionRepository.findById(result.transactionId()).orElseThrow();
        return new CreateTransactionResponse(
                toResponse(saved),
                new CreateTransactionResponse.NewBalance(original.getWalletId(), result.walletNewBalance()),
                computeAffectedBudgets(userId, original.getType(), original.getCategoryId(), date));
    }

    /**
     * Tính affected_budgets cho response POST/PUT/duplicate — gọi ĐỒNG BỘ trong cùng
     * @Transactional (khác BudgetAlertListener chạy AFTER_COMMIT bất đồng bộ), vì response
     * phải trả ngay trong request này (api/04-GIAO-DICH.md dòng 264: "không phải gọi thêm lần
     * nữa"). Chỉ tính khi type=expense và categoryId != null — cùng điều kiện BudgetAlertListener
     * áp dụng.
     */
    private List<AffectedBudgetResponse> computeAffectedBudgets(
            UUID userId, String type, UUID categoryId, LocalDate date) {
        if (!"expense".equals(type) || categoryId == null) {
            return List.of();
        }
        List<BudgetProgressProjection> affected =
                budgetProgressRepository.findActiveByUserAndCategoryInTree(userId, categoryId, date);
        List<AffectedBudgetResponse> result = new ArrayList<>();
        for (BudgetProgressProjection budget : affected) {
            if ("normal".equals(budget.getStatus())) {
                continue;
            }
            Category root = categoryRepository.findById(budget.getCategoryId()).orElse(null);
            String categoryName = root != null ? root.getName() : "";
            long remaining = budget.getRemaining() == null ? 0L : budget.getRemaining();
            int daysRemaining = budget.getDaysRemaining() == null ? 0 : budget.getDaysRemaining();
            // Dùng lại BudgetService.formatAmount: api/04 mục 6 ghi "Còn 502.000 đ", có dấu
            // chấm phân cách hàng nghìn. Nối thẳng số vào chuỗi cho ra "505000 đ" — lệch hợp
            // đồng và lệch luôn với câu cảnh báo của chính module ngân sách.
            String alert = "over_limit".equals(budget.getStatus())
                    ? "Vượt " + BudgetService.formatAmount(Math.abs(remaining))
                            + " đ khi kỳ còn " + daysRemaining + " ngày."
                    : "Còn " + BudgetService.formatAmount(remaining)
                            + " đ cho " + daysRemaining + " ngày còn lại của kỳ.";
            result.add(new AffectedBudgetResponse(
                    budget.getId(), categoryName, budget.getLimitAmount(), budget.getSpentAmount(),
                    budget.getRatio(), budget.getStatus(), alert));
        }
        return result;
    }

    /**
     * GET /transactions (TXN-01, api/04-GIAO-DICH.md mục 1). Điểm bắt buộc TXN-08: nếu {@code
     * filters.categoryId() != null}, cộng gộp danh mục con bằng {@code
     * categoryRepository.findCategoryTree(...)} rồi truyền mảng UUID vào query — KHÔNG tự viết
     * lại điều kiện lọc {@code category_id = :categoryId} đơn thuần ở đây hay bất kỳ nơi khác
     * ({@link #listByDate} tái sử dụng đúng phương thức private này).
     */
    @Transactional(readOnly = true)
    public TransactionListResponse list(UUID userId, TransactionFilterParams filters, PageRequestParams page) {
        LocalDate[] resolvedRange = resolveDateRange(filters);
        LocalDate fromDate = resolvedRange[0];
        LocalDate toDate = resolvedRange[1];
        UUID[] categoryTree = resolveCategoryTree(filters.categoryId());
        boolean includeTransfers = filters.includeTransfersOrDefault();

        List<Transaction> rows = transactionRepository.search(
                userId,
                fromDate,
                toDate,
                filters.type(),
                filters.walletId(),
                categoryTree,
                filters.source(),
                filters.countsInReport(),
                filters.search(),
                filters.minAmount(),
                filters.maxAmount(),
                includeTransfers,
                page.sortBy(),
                page.sortOrder(),
                page.pageSize(),
                (page.page() - 1) * page.pageSize());

        long totalItems = transactionRepository.countSearch(
                userId,
                fromDate,
                toDate,
                filters.type(),
                filters.walletId(),
                categoryTree,
                filters.source(),
                filters.countsInReport(),
                filters.search(),
                filters.minAmount(),
                filters.maxAmount(),
                includeTransfers);

        TransactionRepository.SummaryProjection summaryRow = transactionRepository.summary(
                userId,
                fromDate,
                toDate,
                filters.type(),
                filters.walletId(),
                categoryTree,
                filters.source(),
                filters.countsInReport(),
                filters.search(),
                filters.minAmount(),
                filters.maxAmount());

        List<TransactionListItemResponse> items = rows.stream().map(this::toListItemResponse).toList();
        int totalPages = (int) Math.ceil((double) totalItems / page.pageSize());
        PageMeta pageMeta = new PageMeta(page.page(), page.pageSize(), totalItems, totalPages);
        TransactionSummaryDto summary =
                TransactionSummaryDto.of(summaryRow.getTotalIncome(), summaryRow.getTotalExpense());

        return TransactionListResponse.of(items, pageMeta, summary);
    }

    /**
     * GET /transactions/{id} (api/04-GIAO-DICH.md mục 3) — 404 nếu không thấy (không phải 403,
     * T-03-09). {@code relatedDebt}/{@code recurring} LUÔN {@code null} ở Phase 3 (thuộc Phase
     * 4) — không bịa dữ liệu.
     */
    @Transactional(readOnly = true)
    public TransactionDetailResponse detail(UUID userId, UUID transactionId) {
        Transaction txn = transactionRepository
                .findByIdAndUserIdAndIsDeletedFalse(transactionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy giao dịch."));

        TransactionListItemResponse base = toListItemResponse(txn);

        TransactionDetailResponse.AiDraftRef aiDraft = null;
        if (txn.getDraftId() != null) {
            List<TransactionDetailResponse.AiDraftRef> rows = jdbcTemplate.query(
                    "SELECT id, method, raw_input, confidence FROM ai_drafts WHERE id = ?",
                    (rs, rowNum) -> new TransactionDetailResponse.AiDraftRef(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("method"),
                            rs.getString("raw_input"),
                            rs.getObject("confidence") != null ? rs.getDouble("confidence") : null),
                    txn.getDraftId());
            aiDraft = rows.isEmpty() ? null : rows.get(0);
        }

        return new TransactionDetailResponse(
                base.id(),
                base.type(),
                base.amount(),
                base.date(),
                base.displayName(),
                base.note(),
                base.source(),
                base.wallet(),
                base.destinationWallet(),
                base.category(),
                base.receiptUrl(),
                base.recurringId(),
                base.draftId(),
                base.createdAt(),
                base.updatedAt(),
                null,
                null,
                aiDraft);
    }

    /**
     * GET /transactions/by-date (TXN-02, api/04-GIAO-DICH.md mục 2) — dùng lại đúng query {@link
     * TransactionRepository#search}/{@link TransactionRepository#summary} của {@link #list},
     * không phân trang, nhóm theo {@code date} ở tầng Java (danh sách đã đủ nhỏ sau khi lọc theo
     * kỳ). {@code day_label}/"hôm nay" tính theo GIỜ VIỆT NAM ({@link #VIETNAM_ZONE}), KHÔNG
     * dùng {@code LocalDate.now()} trần trụi (múi giờ JVM đã ép UTC — xem pom.xml).
     */
    @Transactional(readOnly = true)
    public TransactionByDateResponse listByDate(UUID userId, TransactionFilterParams filters) {
        LocalDate[] resolvedRange = resolveDateRange(filters);
        LocalDate fromDate = resolvedRange[0];
        LocalDate toDate = resolvedRange[1];
        UUID[] categoryTree = resolveCategoryTree(filters.categoryId());
        boolean includeTransfers = filters.includeTransfersOrDefault();

        List<Transaction> rows = transactionRepository.search(
                userId,
                fromDate,
                toDate,
                filters.type(),
                filters.walletId(),
                categoryTree,
                filters.source(),
                filters.countsInReport(),
                filters.search(),
                filters.minAmount(),
                filters.maxAmount(),
                includeTransfers,
                // Màn Sổ luôn xếp ngày mới nhất lên trên, không nhận sort_by của client — nó
                // gom theo ngày nên thứ tự khác sẽ làm vỡ cách gom. Trong cùng một ngày,
                // TransactionRepository#search có tiêu chí phụ created_at DESC nên khoản vừa
                // ghi nằm trên cùng (FIX-05).
                "date",
                "desc",
                Integer.MAX_VALUE,
                0);

        TransactionRepository.SummaryProjection summaryRow = transactionRepository.summary(
                userId,
                fromDate,
                toDate,
                filters.type(),
                filters.walletId(),
                categoryTree,
                filters.source(),
                filters.countsInReport(),
                filters.search(),
                filters.minAmount(),
                filters.maxAmount());
        TransactionSummaryDto summary =
                TransactionSummaryDto.of(summaryRow.getTotalIncome(), summaryRow.getTotalExpense());

        Map<LocalDate, List<Transaction>> grouped = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .forEach(t -> grouped.computeIfAbsent(t.getDate(), d -> new ArrayList<>()).add(t));

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDate yesterday = today.minusDays(1);

        List<TransactionByDateResponse.DayGroupDto> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Transaction>> entry : grouped.entrySet()) {
            LocalDate date = entry.getKey();
            List<Transaction> dayTransactions = entry.getValue();

            long dayTotal = dayTransactions.stream()
                    .filter(t -> !"transfer".equals(t.getType()))
                    .mapToLong(t -> "income".equals(t.getType()) ? t.getAmount() : -t.getAmount())
                    .sum();

            days.add(new TransactionByDateResponse.DayGroupDto(
                    date,
                    String.format("%02d", date.getDayOfMonth()),
                    dayLabel(date, today, yesterday),
                    monthYearLabel(date),
                    dayTotal,
                    dayTransactions.stream().map(this::toListItemResponse).toList()));
        }

        String periodLabel = buildPeriodLabel(fromDate, toDate);
        return new TransactionByDateResponse(
                new TransactionByDateResponse.PeriodSummaryDto(
                        periodLabel, summary.totalIncome(), summary.totalExpense(), summary.difference()),
                days);
    }

    /** TXN-08: cộng gộp danh mục con vào cha — điểm gọi DUY NHẤT của {@code findCategoryTree}. */
    private UUID[] resolveCategoryTree(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findCategoryTree(categoryId).toArray(new UUID[0]);
    }

    /**
     * {@code period} (week/month/quarter/year) ưu tiên hơn {@code fromDate}/{@code toDate} client
     * gửi trực tiếp (api/00-QUY-UOC-CHUNG.md mục 7.2). Tính theo {@code LocalDate.now()} (không
     * cần múi giờ Việt Nam ở đây — biên kỳ báo cáo không phải nhãn hiển thị "hôm nay/hôm qua").
     */
    private LocalDate[] resolveDateRange(TransactionFilterParams filters) {
        if (filters.period() == null) {
            return new LocalDate[] {filters.fromDate(), filters.toDate()};
        }
        LocalDate now = LocalDate.now(VIETNAM_ZONE);
        return switch (filters.period()) {
            case "week" -> new LocalDate[] {
                now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            };
            case "month" -> new LocalDate[] {
                now.with(TemporalAdjusters.firstDayOfMonth()), now.with(TemporalAdjusters.lastDayOfMonth())
            };
            case "quarter" -> {
                int quarterStartMonth = ((now.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate quarterStart = LocalDate.of(now.getYear(), quarterStartMonth, 1);
                yield new LocalDate[] {quarterStart, quarterStart.plusMonths(3).minusDays(1)};
            }
            case "year" -> new LocalDate[] {
                now.with(TemporalAdjusters.firstDayOfYear()), now.with(TemporalAdjusters.lastDayOfYear())
            };
            default -> new LocalDate[] {filters.fromDate(), filters.toDate()};
        };
    }

    private String dayLabel(LocalDate date, LocalDate today, LocalDate yesterday) {
        if (date.equals(today)) {
            return "Hôm nay";
        }
        if (date.equals(yesterday)) {
            return "Hôm qua";
        }
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "Thứ hai";
            case TUESDAY -> "Thứ ba";
            case WEDNESDAY -> "Thứ tư";
            case THURSDAY -> "Thứ năm";
            case FRIDAY -> "Thứ sáu";
            case SATURDAY -> "Thứ bảy";
            case SUNDAY -> "Chủ nhật";
        };
    }

    private String monthYearLabel(LocalDate date) {
        return "tháng " + date.getMonthValue() + " " + date.getYear();
    }

    private String buildPeriodLabel(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (fromDate != null) {
            sb.append(String.format("%02d/%02d", fromDate.getDayOfMonth(), fromDate.getMonthValue()));
        }
        sb.append(" – ");
        if (toDate != null) {
            sb.append(String.format("%02d/%02d", toDate.getDayOfMonth(), toDate.getMonthValue()));
        }
        return sb.toString();
    }

    /**
     * Bọc lại {@link #toListItemResponse} package-private cho {@code TransactionBulkService}
     * (plan 03-04) — dùng để build phần tử {@code transaction[]} của phản hồi bulk mà không
     * copy-paste logic build wallet/category ref.
     */
    TransactionListItemResponse buildListItemResponse(Transaction txn) {
        return toListItemResponse(txn);
    }

    /**
     * Đọc lại bản ghi vừa ghi qua {@link TransactionWriter#write} — dùng cho
     * {@code TransactionBulkService} sau khi mỗi dòng commit ở transaction riêng.
     */
    Transaction findPersistedOrThrow(UUID transactionId) {
        return transactionRepository.findById(transactionId).orElseThrow();
    }

    private TransactionListItemResponse toListItemResponse(Transaction txn) {
        Wallet wallet = walletRepository.findById(txn.getWalletId()).orElse(null);
        Wallet destinationWallet = txn.getDestinationWalletId() != null
                ? walletRepository.findById(txn.getDestinationWalletId()).orElse(null)
                : null;

        TransactionListItemResponse.CategoryRef categoryRef = null;
        if (txn.getCategoryId() != null) {
            Category category = categoryRepository.findById(txn.getCategoryId()).orElse(null);
            if (category != null) {
                Icon icon = iconRepository.findById(category.getIconId()).orElse(null);
                TransactionListItemResponse.IconRef iconRef =
                        icon != null ? new TransactionListItemResponse.IconRef(icon.getCode(), icon.getPathData()) : null;
                TransactionListItemResponse.ParentRef parentRef = null;
                if (category.getParentCategoryId() != null) {
                    Category parent =
                            categoryRepository.findById(category.getParentCategoryId()).orElse(null);
                    if (parent != null) {
                        parentRef = new TransactionListItemResponse.ParentRef(parent.getId(), parent.getName());
                    }
                }
                categoryRef = new TransactionListItemResponse.CategoryRef(
                        category.getId(), category.getName(), category.getType(), iconRef, category.getColor(), parentRef);
            }
        }

        return new TransactionListItemResponse(
                txn.getId(),
                txn.getType(),
                txn.getAmount(),
                txn.getDate(),
                txn.getDisplayName(),
                txn.getNote(),
                txn.getSource(),
                wallet != null
                        ? new TransactionListItemResponse.WalletRef(wallet.getId(), wallet.getName(), wallet.getType())
                        : null,
                destinationWallet != null
                        ? new TransactionListItemResponse.WalletRef(
                                destinationWallet.getId(), destinationWallet.getName(), destinationWallet.getType())
                        : null,
                categoryRef,
                txn.getReceiptUrl(),
                txn.getRecurringId(),
                txn.getDraftId(),
                txn.getCreatedAt(),
                txn.getUpdatedAt());
    }

    /**
     * Validate ràng buộc theo {@code type} (đối chiếu {@code ck_txn_shape} — db/migration/
     * V2__giao_dich.sql): category bắt buộc/rỗng, destination bắt buộc/rỗng theo {@code type}.
     *
     * <p>package-private: also called by TransactionBulkService (plan 03-04) to validate each
     * bulk row without duplicating logic.
     */
    void validateShape(String type, UUID categoryId, UUID destinationWalletId, UUID walletId) {
        if ("transfer".equals(type)) {
            if (destinationWalletId == null) {
                throw new BusinessException(
                        "DESTINATION_WALLET_REQUIRED",
                        HttpStatus.BAD_REQUEST.value(),
                        "Giao dịch chuyển phải có ví đích.");
            }
            if (categoryId != null) {
                throw new BusinessException(
                        "CATEGORY_NOT_ALLOWED",
                        HttpStatus.BAD_REQUEST.value(),
                        "Giao dịch chuyển không được có danh mục.");
            }
            if (walletId != null && walletId.equals(destinationWalletId)) {
                throw new BusinessException(
                        "SAME_SOURCE_AND_DESTINATION", HttpStatus.BAD_REQUEST.value(), "Hai ví phải khác nhau.");
            }
        } else {
            if (categoryId == null) {
                throw new BusinessException(
                        "CATEGORY_REQUIRED", HttpStatus.BAD_REQUEST.value(), "Chi/thu phải có danh mục.");
            }
            if (destinationWalletId != null) {
                throw new BusinessException(
                        "DESTINATION_WALLET_NOT_ALLOWED",
                        HttpStatus.BAD_REQUEST.value(),
                        "Giao dịch chi/thu không được có ví đích.");
            }
        }
    }

    /**
     * Khoá tất cả ví liên quan theo thứ tự {@code UUID.compareTo()} tăng dần (D-33, chống
     * deadlock — mở rộng nguyên thuật toán từ {@code WalletTransferService} 2 ví lên tối đa 4
     * ví), kiểm tra quyền sở hữu ngay sau khi khoá từng ví.
     */
    private Map<UUID, Wallet> lockWalletsInOrder(Set<UUID> walletIds, UUID userId) {
        List<UUID> sortedIds = new ArrayList<>(walletIds);
        sortedIds.sort(UUID::compareTo);

        Map<UUID, Wallet> lockedWallets = new HashMap<>();
        for (UUID walletId : sortedIds) {
            Wallet wallet = walletRepository
                    .findByIdForUpdate(walletId)
                    .orElseThrow(() -> new BusinessException(
                            "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));
            if (!userId.equals(wallet.getUserId())) {
                throw new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví.");
            }
            lockedWallets.put(walletId, wallet);
        }
        return lockedWallets;
    }

    /**
     * Áp dụng (hoặc hoàn tác, nếu {@code reverse=true}) ảnh hưởng của một giao dịch lên (các) ví
     * liên quan — dùng chung cho bước 1 (hoàn tác CŨ) và bước 3 (áp dụng MỚI) của
     * {@link #update}, cũng như bước hoàn tác của {@link #delete}.
     */
    private void applyEffect(
            String type, UUID walletId, UUID destinationWalletId, long amount, boolean reverse) {
        switch (type) {
            case "expense" -> walletRepository.adjustBalance(walletId, reverse ? amount : -amount);
            case "income" -> walletRepository.adjustBalance(walletId, reverse ? -amount : amount);
            case "transfer" -> {
                walletRepository.adjustBalance(walletId, reverse ? amount : -amount);
                walletRepository.adjustBalance(destinationWalletId, reverse ? -amount : amount);
            }
            default -> throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + type);
        }
    }

    private TransactionResponse toResponse(Transaction txn) {
        Wallet wallet = walletRepository.findById(txn.getWalletId()).orElse(null);
        Wallet destinationWallet = txn.getDestinationWalletId() != null
                ? walletRepository.findById(txn.getDestinationWalletId()).orElse(null)
                : null;
        Category category = txn.getCategoryId() != null
                ? categoryRepository.findById(txn.getCategoryId()).orElse(null)
                : null;

        return new TransactionResponse(
                txn.getId(),
                txn.getType(),
                txn.getAmount(),
                txn.getDate(),
                txn.getDisplayName(),
                txn.getNote(),
                txn.getSource(),
                wallet != null ? new TransactionResponse.WalletRef(wallet.getId(), wallet.getName(), wallet.getType()) : null,
                destinationWallet != null
                        ? new TransactionResponse.WalletRef(
                                destinationWallet.getId(), destinationWallet.getName(), destinationWallet.getType())
                        : null,
                category != null
                        ? new TransactionResponse.CategoryRef(
                                category.getId(), category.getName(), category.getType(), category.getParentCategoryId())
                        : null,
                txn.getReceiptUrl(),
                txn.getRecurringId(),
                txn.getDraftId(),
                txn.getCreatedAt(),
                txn.getUpdatedAt());
    }
}
