package com.datn.financeapp.transaction.service.impl;

import com.datn.financeapp.transaction.service.TransactionService;
import com.datn.financeapp.transaction.service.TransactionWriteCommand;
import com.datn.financeapp.transaction.service.TransactionWriter;

import com.datn.financeapp.budget.service.BudgetService;
import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.category.dto.response.IconRefResponse;
import com.datn.financeapp.category.service.CategoryService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.transaction.dto.response.AffectedBudgetResponse;
import com.datn.financeapp.transaction.dto.request.CreateTransactionRequest;
import com.datn.financeapp.transaction.dto.response.CreateTransactionResponse;
import com.datn.financeapp.transaction.dto.response.DeleteTransactionResponse;
import com.datn.financeapp.transaction.dto.response.GeneratedTransactionResponse;
import com.datn.financeapp.transaction.dto.request.DuplicateTransactionRequest;
import com.datn.financeapp.transaction.dto.response.TransactionByDateResponse;
import com.datn.financeapp.transaction.dto.response.TransactionDetailResponse;
import com.datn.financeapp.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.transaction.dto.response.TransactionListItemResponse;
import com.datn.financeapp.transaction.dto.response.TransactionListResponse;
import com.datn.financeapp.transaction.dto.response.TransactionRefResponse;
import com.datn.financeapp.transaction.dto.response.TransactionResponse;
import com.datn.financeapp.transaction.dto.response.TransactionSummaryResponse;
import com.datn.financeapp.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.transaction.mapper.TransactionMapper;
import com.datn.financeapp.transaction.repository.TransactionRepository;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.wallet.service.WalletService;
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
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionWriter transactionWriter;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionMapper transactionMapper;

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
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }

        if ("transfer".equals(req.type())) {
            walletService.lockWalletsInOrder(Set.of(req.walletId(), req.destinationWalletId()), userId);
        } else {
            walletService.lockWalletsInOrder(Set.of(req.walletId()), userId);
        }

        if (req.categoryId() != null) {
            CategoryRefResponse category =
                    categoryService.findRefVisibleToUser(req.categoryId(), userId);
            if (category == null) {
                throw new BusinessException(ErrorCode.CATEGORY_NOT_ALLOWED);
            }
            if (!category.type().equals(req.type())) {
                throw new BusinessException(ErrorCode.CATEGORY_TYPE_MISMATCH);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        validateShape(req.type(), req.categoryId(), req.destinationWalletId(), req.walletId());

        if (req.amount() == null || req.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
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
        walletService.lockWalletsInOrder(walletIds, userId);

        if (req.categoryId() != null) {
            CategoryRefResponse category =
                    categoryService.findRefVisibleToUser(req.categoryId(), userId);
            if (category == null) {
                throw new BusinessException(ErrorCode.CATEGORY_NOT_ALLOWED);
            }
            if (!category.type().equals(req.type())) {
                throw new BusinessException(ErrorCode.CATEGORY_TYPE_MISMATCH);
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

        long walletNewBalance = walletService.requireRawBalance(req.walletId());

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
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));
            // Đã xoá mềm từ trước — idempotent, không làm gì thêm, không ném lỗi.
            long balance = walletService.requireRawBalance(existing.getWalletId());
            return new DeleteTransactionResponse(
                    new DeleteTransactionResponse.NewBalance(existing.getWalletId(), balance));
        }

        if (transactionRepository.existsDebtPaymentLink(transactionId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_LINKED_TO_DEBT);
        }

        Set<UUID> walletIds = new HashSet<>();
        walletIds.add(txn.getWalletId());
        if (txn.getDestinationWalletId() != null) {
            walletIds.add(txn.getDestinationWalletId());
        }
        walletService.lockWalletsInOrder(walletIds, userId);

        applyEffect(txn.getType(), txn.getWalletId(), txn.getDestinationWalletId(), txn.getAmount(), true);

        txn.setIsDeleted(true);
        txn.setUpdatedAt(Instant.now());
        transactionRepository.save(txn);

        long walletNewBalance = walletService.requireRawBalance(txn.getWalletId());
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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

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
    /**
     * Số giao dịch còn sống do một khoản định kỳ sinh ra. Dành cho {@code recurring/} hiển thị
     * "đã chạy bao nhiêu lần".
     */
    @Transactional(readOnly = true)
    public long countByRecurringId(UUID recurringId) {
        return transactionRepository.countByRecurringIdAndIsDeletedFalse(recurringId);
    }

    // Số giao dịch còn sống của một người dùng. Dành cho hồ sơ tài khoản (getMe).
    @Transactional(readOnly = true)
    public long countActiveByUserId(UUID userId) {
        return transactionRepository.countByUserIdAndIsDeletedFalse(userId);
    }

    /**
     * Lịch sử giao dịch do một khoản định kỳ sinh ra, mới nhất trước. Dành cho {@code recurring/}
     * dựng phần lịch sử ở màn chi tiết — cần cả {@code type} và {@code source} nên trả
     * {@link GeneratedTransactionResponse} chứ không phải {@link TransactionRefResponse}.
     */
    @Transactional(readOnly = true)
    public List<GeneratedTransactionResponse> findGeneratedByRecurringId(UUID recurringId) {
        return transactionRepository
                .findAllByRecurringIdAndIsDeletedFalseOrderByDateDesc(recurringId)
                .stream()
                .map(transactionMapper::toGeneratedResponse)
                .toList();
    }

    /**
     * Tham chiếu tối thiểu tới một giao dịch, hoặc {@code null} nếu không tìm thấy /
     * {@code transactionId} rỗng.
     *
     * <p>Dành cho module khác cần nhắc tới giao dịch trong phản hồi của mình. KHÔNG kiểm quyền ở
     * đây: bên gọi đã xác thực quyền trên bản ghi cha của mình (khoản nợ, khoản định kỳ) và giao
     * dịch được trỏ tới là do chính nghiệp vụ đó sinh ra.
     */
    @Transactional(readOnly = true)
    public TransactionRefResponse findRefById(UUID transactionId) {
        if (transactionId == null) {
            return null;
        }
        return transactionRepository
                .findById(transactionId)
                .map(transactionMapper::toRef)
                .orElse(null);
    }

    /**
     * Ánh xạ kết quả của {@code BudgetService} sang DTO của module này. Logic dựng câu cảnh báo
     * nằm bên {@code budget/} vì nó dùng cách định dạng tiền của chính module đó.
     */
    private List<AffectedBudgetResponse> computeAffectedBudgets(
            UUID userId, String type, UUID categoryId, LocalDate date) {
        return budgetService.findImpactedBudgets(userId, type, categoryId, date).stream()
                .map(b -> new AffectedBudgetResponse(
                        b.id(), b.categoryName(), b.limitAmount(), b.spentAmount(),
                        b.ratio(), b.status(), b.alert()))
                .toList();
    }

    /**
     * GET /transactions (TXN-01, api/04-GIAO-DICH.md mục 1). Điểm bắt buộc TXN-08: nếu {@code
     * filters.categoryId() != null}, cộng gộp danh mục con bằng {@code
     * categoryService.findCategoryTree(...)} rồi truyền mảng UUID vào query — KHÔNG tự viết
     * lại điều kiện lọc {@code category_id = :categoryId} đơn thuần ở đây hay bất kỳ nơi khác
     * ({@link #listByDate} tái sử dụng đúng phương thức private này).
     */
    @Transactional(readOnly = true)
    public TransactionListResponse list(UUID userId, TransactionFilterRequest filters, PageRequestParams page) {
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
        TransactionSummaryResponse summary =
                TransactionSummaryResponse.of(summaryRow.getTotalIncome(), summaryRow.getTotalExpense());

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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

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

        return transactionMapper.toDetailResponse(base, aiDraft);
    }

    /**
     * GET /transactions/by-date (TXN-02, api/04-GIAO-DICH.md mục 2) — dùng lại đúng query {@link
     * TransactionRepository#search}/{@link TransactionRepository#summary} của {@link #list},
     * không phân trang, nhóm theo {@code date} ở tầng Java (danh sách đã đủ nhỏ sau khi lọc theo
     * kỳ). {@code day_label}/"hôm nay" tính theo GIỜ VIỆT NAM ({@link #VIETNAM_ZONE}), KHÔNG
     * dùng {@code LocalDate.now()} trần trụi (múi giờ JVM đã ép UTC — xem pom.xml).
     */
    @Transactional(readOnly = true)
    public TransactionByDateResponse listByDate(UUID userId, TransactionFilterRequest filters) {
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
        TransactionSummaryResponse summary =
                TransactionSummaryResponse.of(summaryRow.getTotalIncome(), summaryRow.getTotalExpense());

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

    // TXN-08: cộng gộp danh mục con vào cha — điểm gọi DUY NHẤT của {@code findCategoryTree}.
    private UUID[] resolveCategoryTree(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryService.findCategoryTree(categoryId).toArray(new UUID[0]);
    }

    /**
     * {@code period} (week/month/quarter/year) ưu tiên hơn {@code fromDate}/{@code toDate} client
     * gửi trực tiếp (api/00-QUY-UOC-CHUNG.md mục 7.2). Tính theo {@code LocalDate.now()} (không
     * cần múi giờ Việt Nam ở đây — biên kỳ báo cáo không phải nhãn hiển thị "hôm nay/hôm qua").
     */
    private LocalDate[] resolveDateRange(TransactionFilterRequest filters) {
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
    public TransactionListItemResponse buildListItemResponse(Transaction txn) {
        return toListItemResponse(txn);
    }

    /**
     * Đọc lại bản ghi vừa ghi qua {@link TransactionWriter#write} — dùng cho
     * {@code TransactionBulkService} sau khi mỗi dòng commit ở transaction riêng.
     */
    public Transaction findPersistedOrThrow(UUID transactionId) {
        return transactionRepository.findById(transactionId).orElseThrow();
    }

    private TransactionListItemResponse toListItemResponse(Transaction txn) {
        WalletRefResponse wallet = walletService.findRefById(txn.getWalletId());
        WalletRefResponse destinationWallet = walletService.findRefById(txn.getDestinationWalletId());

        TransactionListItemResponse.CategoryRef categoryRef = null;
        if (txn.getCategoryId() != null) {
            CategoryRefResponse category = categoryService.findRefById(txn.getCategoryId());
            if (category != null) {
                CategoryRefResponse parent = null;
                if (category.parentCategoryId() != null) {
                    parent = categoryService.findRefById(category.parentCategoryId());
                }
                categoryRef = transactionMapper.toListItemCategoryRef(category, parent);
            }
        }

        return transactionMapper.toListItemResponse(txn, wallet, destinationWallet, categoryRef);
    }

    /**
     * Validate ràng buộc theo {@code type} (đối chiếu {@code ck_txn_shape} — db/migration/
     * V2__giao_dich.sql): category bắt buộc/rỗng, destination bắt buộc/rỗng theo {@code type}.
     *
     * <p>package-private: also called by TransactionBulkService (plan 03-04) to validate each
     * bulk row without duplicating logic.
     */
    public void validateShape(String type, UUID categoryId, UUID destinationWalletId, UUID walletId) {
        if ("transfer".equals(type)) {
            if (destinationWalletId == null) {
                throw new BusinessException(ErrorCode.DESTINATION_WALLET_REQUIRED);
            }
            if (categoryId != null) {
                throw new BusinessException(ErrorCode.CATEGORY_NOT_ALLOWED, "Giao dịch chuyển không được có danh mục.");
            }
            if (walletId != null && walletId.equals(destinationWalletId)) {
                throw new BusinessException(ErrorCode.SAME_SOURCE_AND_DESTINATION);
            }
        } else {
            if (categoryId == null) {
                throw new BusinessException(ErrorCode.CATEGORY_REQUIRED);
            }
            if (destinationWalletId != null) {
                throw new BusinessException(ErrorCode.DESTINATION_WALLET_NOT_ALLOWED);
            }
        }
    }

    /**
     * Áp dụng (hoặc hoàn tác, nếu {@code reverse=true}) ảnh hưởng của một giao dịch lên (các) ví
     * liên quan — dùng chung cho bước 1 (hoàn tác CŨ) và bước 3 (áp dụng MỚI) của
     * {@link #update}, cũng như bước hoàn tác của {@link #delete}.
     */
    private void applyEffect(
            String type, UUID walletId, UUID destinationWalletId, long amount, boolean reverse) {
        switch (type) {
            case "expense" -> walletService.adjustBalance(walletId, reverse ? amount : -amount);
            case "income" -> walletService.adjustBalance(walletId, reverse ? -amount : amount);
            case "transfer" -> {
                walletService.adjustBalance(walletId, reverse ? amount : -amount);
                walletService.adjustBalance(destinationWalletId, reverse ? -amount : amount);
            }
            default -> throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + type);
        }
    }

    private TransactionResponse toResponse(Transaction txn) {
        WalletRefResponse wallet = walletService.findRefById(txn.getWalletId());
        WalletRefResponse destinationWallet = walletService.findRefById(txn.getDestinationWalletId());
        CategoryRefResponse category = categoryService.findRefById(txn.getCategoryId());

        return transactionMapper.toResponse(txn, wallet, destinationWallet, category);
    }
}
