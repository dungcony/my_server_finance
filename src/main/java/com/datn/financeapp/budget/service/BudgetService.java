package com.datn.financeapp.budget.service;

import com.datn.financeapp.budget.dto.BudgetAlertResponse;
import com.datn.financeapp.budget.dto.BudgetListItemResponse;
import com.datn.financeapp.budget.dto.BudgetSuggestionResponse;
import com.datn.financeapp.budget.dto.BudgetSummaryResponse;
import com.datn.financeapp.budget.dto.CreateBudgetRequest;
import com.datn.financeapp.budget.dto.UpdateBudgetRequest;
import com.datn.financeapp.budget.entity.Budget;
import com.datn.financeapp.budget.repository.BudgetProgressRepository;
import com.datn.financeapp.budget.repository.BudgetProgressRepository.BudgetProgressProjection;
import com.datn.financeapp.budget.repository.BudgetRepository;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.entity.Icon;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.category.repository.IconRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.notification.repository.NotificationRepository;
import com.datn.financeapp.report.dto.ReportHomeResponse;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic ngân sách (BUDGET-01..06, api/05-NGAN-SACH.md).
 *
 * <p><b>Nguyên tắc số một của module này:</b> KHÔNG tính {@code spent_amount} ở tầng Java. Mọi
 * con số tiến độ đọc từ {@code v_budget_progress} qua {@link BudgetProgressRepository} — view đã
 * gói sẵn cộng gộp danh mục con, loại {@code transfer} và điều kiện phạm vi quyền. Viết lại một
 * trong ba quy tắc đó ở đây là cách chắc chắn nhất để chúng lệch nhau về sau.
 *
 * <p>Ngoại lệ DUY NHẤT có tính tổng là {@code suggestion()} (BUDGET-05) — nó cần số chi của các
 * kỳ ngân sách ĐÃ KẾT THÚC, tức dữ liệu nằm ngoài phạm vi view (view chỉ tính kỳ của chính bản
 * ghi ngân sách hiện tại). Phép tính đó vẫn đi qua {@code BudgetRepository.sumExpenseInPeriod} —
 * SQL có {@code fn_category_tree}, không tự viết điều kiện lọc cây mới ở Java.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_NEAR_LIMIT = "near_limit";
    private static final String STATUS_OVER_LIMIT = "over_limit";

    /** Ba màu trạng thái lấy từ api/05 mục 2.2 — trùng bảng màu design/README.md. */
    private static final Map<String, String> STATUS_COLORS = Map.of(
            STATUS_NORMAL, "#4e9e76",
            STATUS_NEAR_LIMIT, "#d6a95e",
            STATUS_OVER_LIMIT, "#e3796c");

    private final BudgetRepository budgetRepository;
    private final BudgetProgressRepository budgetProgressRepository;
    private final CategoryRepository categoryRepository;
    private final IconRepository iconRepository;
    private final WalletRepository walletRepository;
    private final NotificationRepository notificationRepository;
    private final BudgetRenewalWorker budgetRenewalWorker;

    // ---------------------------------------------------------------------
    // Đọc
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BudgetListItemResponse> list(UUID userId, Boolean isActive, String periodType) {
        return toResponses(userId, budgetProgressRepository.findAllForUser(userId, isActive, periodType));
    }

    @Transactional(readOnly = true)
    public BudgetListItemResponse detail(UUID userId, UUID budgetId) {
        BudgetProgressProjection row =
                budgetProgressRepository.findByIdForUser(budgetId, userId).orElseThrow(this::notFound);
        return toResponses(userId, List.of(row)).get(0);
    }

    /**
     * api/05 mục 3. Tổng hợp trên các ngân sách ĐANG HIỆU LỰC. {@code period} lấy từ ngân sách mới
     * nhất làm đại diện — người dùng thường đặt cùng một loại kỳ cho tất cả.
     */
    @Transactional(readOnly = true)
    public BudgetSummaryResponse summary(UUID userId) {
        List<BudgetProgressProjection> rows = budgetProgressRepository.findAllForUser(userId, true, null);

        long totalLimit = rows.stream().mapToLong(BudgetProgressProjection::getLimitAmount).sum();
        long totalSpent = rows.stream().mapToLong(row -> nullToZero(row.getSpentAmount())).sum();

        BigDecimal ratio = totalLimit == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalSpent).divide(BigDecimal.valueOf(totalLimit), 4, RoundingMode.HALF_UP);

        int overLimitCount = (int) rows.stream()
                .filter(row -> STATUS_OVER_LIMIT.equals(row.getStatus()))
                .count();
        int nearLimitCount = (int) rows.stream()
                .filter(row -> STATUS_NEAR_LIMIT.equals(row.getStatus()))
                .count();

        return new BudgetSummaryResponse(
                rows.isEmpty() ? currentMonthPeriod() : toPeriod(rows.get(0)),
                totalLimit,
                totalSpent,
                totalLimit - totalSpent,
                ratio,
                statusOfRatio(ratio),
                rows.size(),
                overLimitCount,
                nearLimitCount);
    }

    /**
     * BUDGET-06, api/05 mục 7 — cảnh báo tính TẠI CHỖ, là nguồn sự thật cho trạng thái hiện tại.
     * Khác hẳn bảng {@code notifications} (lịch sử tại thời điểm vượt ngưỡng, không tự sửa lại khi
     * người dùng xoá giao dịch sau đó). Hai nguồn cố ý khác nhau, đừng cố đồng bộ (D-41).
     */
    @Transactional(readOnly = true)
    public List<BudgetAlertResponse> alerts(UUID userId) {
        List<BudgetProgressProjection> rows = budgetProgressRepository.findAllForUser(userId, true, null).stream()
                .filter(row -> !STATUS_NORMAL.equals(row.getStatus()))
                .toList();

        Map<UUID, Category> categories = loadCategories(userId, rows);

        List<BudgetAlertResponse> alerts = new ArrayList<>(rows.size());
        for (BudgetProgressProjection row : rows) {
            Category category = categories.get(row.getCategoryId());
            BigDecimal ratio = nullToZero(row.getRatio());

            alerts.add(new BudgetAlertResponse(
                    row.getId(),
                    category != null ? category.getName() : "Danh mục đã xoá",
                    STATUS_OVER_LIMIT.equals(row.getStatus()) ? "critical" : "alert",
                    ratio,
                    percentLabel(ratio) + "%",
                    buildAlertTitle(row),
                    buildAlertContent(row),
                    // api/05 minh hoạ gợi ý kèm hành động "move_limit" (chuyển hạn mức giữa hai
                    // ngân sách), nhưng KHÔNG có endpoint nào cho hành động đó trong bất kỳ đặc
                    // tả nào — không bịa ra ở đây, trả null cho tới khi hợp đồng được bổ sung.
                    null));
        }
        return alerts;
    }

    /**
     * Khối "ngân sách cần chú ý" của {@code GET /reports/home} (api/06 mục 1).
     *
     * <p>Cùng nguồn dữ liệu với {@link #alerts(UUID)} nhưng KHÁC hình dạng response — api/06 quy
     * định {@code id}/{@code category}/{@code ratio}/{@code status}, còn api/05 mục 7 quy định
     * {@code budget_id}/{@code severity} kèm câu chữ soạn sẵn. Trước đây {@code /reports/home}
     * dùng lại thẳng {@link BudgetAlertResponse}, khiến response lệch api/06: client đọc khoá
     * {@code id} nhận về null và sập màn Tổng quan ngay khi có ngân sách đầu tiên vượt hạn mức
     * (FIX-06, đợt test 02/09/2026). Tách method riêng để hai hợp đồng không kéo nhau nữa.
     *
     * <p>{@code status} giữ NGUYÊN giá trị gốc của {@code v_budget_progress}, không quy đổi sang
     * {@code alert}/{@code critical}.
     */
    @Transactional(readOnly = true)
    public List<ReportHomeResponse.BudgetAttentionItem> attentionItems(UUID userId) {
        List<BudgetProgressProjection> rows = budgetProgressRepository.findAllForUser(userId, true, null).stream()
                .filter(row -> !STATUS_NORMAL.equals(row.getStatus()))
                .toList();

        Map<UUID, Category> categories = loadCategories(userId, rows);

        List<ReportHomeResponse.BudgetAttentionItem> items = new ArrayList<>(rows.size());
        for (BudgetProgressProjection row : rows) {
            Category category = categories.get(row.getCategoryId());
            items.add(new ReportHomeResponse.BudgetAttentionItem(
                    row.getId(),
                    category != null ? category.getName() : "Danh mục đã xoá",
                    nullToZero(row.getRatio()),
                    row.getStatus()));
        }
        return items;
    }

    /**
     * BUDGET-05, api/05 mục 6 — gợi ý hạn mức = trung bình chi của 3 kỳ ngân sách gần nhất ĐÃ KẾT
     * THÚC × 1.05, làm tròn LÊN hàng trăm nghìn.
     *
     * <p>Nhân 1.05 để chừa khoảng dư: đặt hạn mức đúng bằng mức trung bình thì kỳ nào cũng gần
     * vượt, cảnh báo kêu liên tục và người dùng sẽ học cách bỏ qua chúng.
     */
    @Transactional(readOnly = true)
    public BudgetSuggestionResponse suggestion(UUID userId, UUID categoryId) {
        Category category = categoryRepository
                .findByIdAndVisibleToUser(categoryId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục."));

        BudgetSuggestionResponse.CategorySummary categorySummary =
                new BudgetSuggestionResponse.CategorySummary(category.getId(), category.getName());

        List<Long> spentPerPeriod = budgetRepository.findLastThreeEndedPeriods(userId, categoryId).stream()
                .map(budget -> budgetRepository.sumExpenseInPeriod(
                        userId,
                        budget.getCategoryId(),
                        budget.getWalletId(),
                        budget.getStartDate(),
                        budget.getEndDate()))
                .filter(spent -> spent > 0)
                .toList();

        // api/05 mục 6: dưới 1 kỳ có dữ liệu thì trả null + giải thích, để app ẩn hẳn khối gợi ý
        // thay vì hiện một con số bịa từ mẫu quá nhỏ.
        if (spentPerPeriod.isEmpty()) {
            return new BudgetSuggestionResponse(
                    categorySummary,
                    null,
                    new BudgetSuggestionResponse.Basis(null, null, null, 0),
                    "Chưa đủ lịch sử chi tiêu cho " + category.getName()
                            + " để gợi ý hạn mức. Hãy ghi chi tiêu thêm một thời gian rồi quay lại.");
        }

        long average = Math.round(
                spentPerPeriod.stream().mapToLong(Long::longValue).average().orElse(0));
        long max = spentPerPeriod.stream().mapToLong(Long::longValue).max().orElse(0);
        long min = spentPerPeriod.stream().mapToLong(Long::longValue).min().orElse(0);

        return new BudgetSuggestionResponse(
                categorySummary,
                roundUpToHundredThousand(Math.round(average * 1.05)),
                new BudgetSuggestionResponse.Basis(average, max, min, spentPerPeriod.size()),
                "Trung bình " + spentPerPeriod.size() + " kỳ gần nhất bạn chi " + formatAmount(average) + " đ cho "
                        + category.getName() + ". Mức đề xuất cộng thêm 5% để có khoảng dư.");
    }

    // ---------------------------------------------------------------------
    // Ghi
    // ---------------------------------------------------------------------

    /**
     * api/05 mục 4. Chặn ngân sách trùng KHÔNG bằng cách SELECT kiểm tra trước mà để ràng buộc
     * {@code ex_bud_no_overlap} (EXCLUDE gist, V3) tự chặn rồi bắt {@link
     * DataIntegrityViolationException}: kiểm tra trước có khe hở race condition giữa hai request
     * song song, ràng buộc CSDL thì không.
     *
     * <p>Phải {@code saveAndFlush} chứ không {@code save}: {@code save} chỉ đưa entity vào
     * persistence context, INSERT thật chạy lúc flush cuối transaction — khi đó exception ném ra
     * NGOÀI phạm vi khối try này và trả về 500 thay vì 409.
     */
    @Transactional
    public BudgetListItemResponse create(UUID userId, CreateBudgetRequest req) {
        Category category = categoryRepository
                .findByIdAndVisibleToUser(req.categoryId(), userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục."));

        // Trigger trg_budgets_validate cũng chặn, nhưng kiểm tra ở đây để trả đúng mã nghiệp vụ
        // CATEGORY_NOT_EXPENSE thay vì lỗi ràng buộc thô — hai tầng phòng thủ.
        if (!"expense".equals(category.getType())) {
            throw new BusinessException(
                    "CATEGORY_NOT_EXPENSE", HttpStatus.BAD_REQUEST.value(), "Chỉ đặt ngân sách cho danh mục chi.");
        }

        if (req.walletId() != null
                && walletRepository.findByIdForUser(req.walletId(), userId).isEmpty()) {
            throw new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví.");
        }

        LocalDate startDate = req.startDate() != null ? req.startDate() : startOfCurrentPeriod(req.periodType());

        Budget budget = Budget.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .groupId(null)
                .categoryId(req.categoryId())
                .walletId(req.walletId())
                .limitAmount(req.limitAmount())
                .periodType(req.periodType())
                .startDate(startDate)
                .endDate(endOfPeriod(req.periodType(), startDate))
                .autoRenew(req.autoRenew() == null || req.autoRenew())
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        try {
            budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    "BUDGET_ALREADY_EXISTS",
                    HttpStatus.CONFLICT.value(),
                    "Đã có ngân sách cho danh mục này trong kỳ.");
        }

        return detail(userId, budget.getId());
    }

    /**
     * api/05 mục 5. {@code category_id} và {@code period_type} KHÔNG sửa được — đổi hai trường đó
     * thực chất là một ngân sách khác, cho sửa sẽ làm mọi con số của kỳ đang chạy vô nghĩa. Từ
     * chối tường minh bằng {@code CATEGORY_NOT_EDITABLE} rõ ràng hơn im lặng bỏ qua trường client
     * gửi lên (T-04-06).
     */
    @Transactional
    public BudgetListItemResponse update(UUID userId, UUID budgetId, UpdateBudgetRequest req) {
        Budget budget = budgetRepository.findByIdForUser(budgetId, userId).orElseThrow(this::notFound);

        boolean changesCategory = req.categoryId() != null && !req.categoryId().equals(budget.getCategoryId());
        boolean changesPeriod = req.periodType() != null && !req.periodType().equals(budget.getPeriodType());
        if (changesCategory || changesPeriod) {
            throw new BusinessException(
                    "CATEGORY_NOT_EDITABLE",
                    HttpStatus.BAD_REQUEST.value(),
                    "Không đổi danh mục hoặc kỳ của ngân sách — xoá và tạo lại.");
        }

        if (req.limitAmount() != null) {
            budget.setLimitAmount(req.limitAmount());
        }
        if (req.autoRenew() != null) {
            budget.setAutoRenew(req.autoRenew());
        }
        if (req.isActive() != null) {
            budget.setIsActive(req.isActive());
        }
        if (req.walletId() != null) {
            if (walletRepository.findByIdForUser(req.walletId(), userId).isEmpty()) {
                throw new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví.");
            }
            budget.setWalletId(req.walletId());
        }

        try {
            budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException e) {
            // Bật lại is_active của một ngân sách có kỳ chồng lấn cũng đụng ex_bud_no_overlap.
            throw new BusinessException(
                    "BUDGET_ALREADY_EXISTS",
                    HttpStatus.CONFLICT.value(),
                    "Đã có ngân sách cho danh mục này trong kỳ.");
        }

        return detail(userId, budget.getId());
    }

    /**
     * api/05 mục 5 "Xoá là xoá mềm". Bảng {@code budgets} KHÔNG có cột {@code is_deleted} (đối
     * chiếu schema V3 thật — CORE-11), nên xoá mềm ở đây nghĩa là tắt {@code is_active}: bản ghi
     * còn nguyên trong CSDL (giữ lịch sử, và BUDGET-05 vẫn dùng được kỳ cũ để gợi ý), chỉ biến
     * mất khỏi danh sách đang hiệu lực và thôi chiếm chỗ trong {@code ex_bud_no_overlap}.
     */
    @Transactional
    public void delete(UUID userId, UUID budgetId) {
        Budget budget = budgetRepository.findByIdForUser(budgetId, userId).orElseThrow(this::notFound);
        budget.setIsActive(false);
        budgetRepository.save(budget);
    }

    // ---------------------------------------------------------------------
    // Tác vụ nền JOB-02 (D-57, api/05 mục 8)
    // ---------------------------------------------------------------------

    /**
     * Quét mọi ngân sách {@code auto_renew=true} đã hết kỳ và tự tạo kỳ mới. KHÔNG {@code
     * @Transactional} ở method top-level: mỗi ngân sách xử lý ĐỘC LẬP trong transaction riêng của
     * {@link BudgetRenewalWorker#renewOneBudget} (cùng tinh thần D-51/D-52) — một ngân sách lỗi
     * không được cuốn theo những ngân sách đã lặp thành công trước đó trong cùng lần chạy job.
     */
    public void renewExpiredBudgets() {
        List<Budget> toRenew = budgetRepository.findAutoRenewExpired(LocalDate.now());
        for (Budget old : toRenew) {
            try {
                budgetRenewalWorker.renewOneBudget(old.getId());
            } catch (Exception e) {
                log.error("Lỗi khi tự động lặp kỳ ngân sách {}", old.getId(), e);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Ánh xạ và tính toán phụ trợ
    // ---------------------------------------------------------------------

    private List<BudgetListItemResponse> toResponses(UUID userId, List<BudgetProgressProjection> rows) {
        Map<UUID, Category> categories = loadCategories(userId, rows);
        Map<UUID, Icon> icons = loadIcons(categories.values());
        Map<UUID, Wallet> wallets = loadWallets(userId, rows);

        List<BudgetListItemResponse> result = new ArrayList<>(rows.size());
        for (BudgetProgressProjection row : rows) {
            Category category = categories.get(row.getCategoryId());
            Icon icon = category != null ? icons.get(category.getIconId()) : null;
            Wallet wallet = row.getWalletId() != null ? wallets.get(row.getWalletId()) : null;
            BigDecimal ratio = nullToZero(row.getRatio());

            BudgetListItemResponse.CategorySummary categorySummary = category == null
                    ? null
                    : new BudgetListItemResponse.CategorySummary(
                            category.getId(),
                            category.getName(),
                            icon == null ? null : new BudgetListItemResponse.IconSummary(icon.getCode(), icon.getPathData()),
                            category.getColor());

            result.add(new BudgetListItemResponse(
                    row.getId(),
                    categorySummary,
                    wallet == null ? null : new BudgetListItemResponse.WalletSummary(wallet.getId(), wallet.getName()),
                    row.getLimitAmount(),
                    nullToZero(row.getSpentAmount()),
                    row.getRemaining(),
                    ratio,
                    percentLabel(ratio),
                    row.getStatus(),
                    STATUS_COLORS.get(row.getStatus()),
                    row.getPeriodType(),
                    row.getStartDate(),
                    row.getEndDate(),
                    row.getDaysRemaining(),
                    row.getAutoRenew(),
                    // Ngân sách chung của nhóm thuộc Phase 5; Phase 4 chỉ có ngân sách cá nhân.
                    false,
                    new BudgetListItemResponse.Alert(
                            buildAlertTitle(row), buildAlertContent(row), projectedDepletionDate(row))));
        }
        return result;
    }

    private Map<UUID, Category> loadCategories(UUID userId, List<BudgetProgressProjection> rows) {
        Map<UUID, Category> byId = new HashMap<>();
        for (BudgetProgressProjection row : rows) {
            if (row.getCategoryId() == null || byId.containsKey(row.getCategoryId())) {
                continue;
            }
            categoryRepository
                    .findByIdAndVisibleToUser(row.getCategoryId(), userId)
                    .ifPresent(category -> byId.put(category.getId(), category));
        }
        return byId;
    }

    private Map<UUID, Icon> loadIcons(Collection<Category> categories) {
        List<UUID> iconIds = categories.stream()
                .map(Category::getIconId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Icon> byId = new HashMap<>();
        iconRepository.findAllById(iconIds).forEach(icon -> byId.put(icon.getId(), icon));
        return byId;
    }

    private Map<UUID, Wallet> loadWallets(UUID userId, List<BudgetProgressProjection> rows) {
        Map<UUID, Wallet> byId = new HashMap<>();
        for (BudgetProgressProjection row : rows) {
            if (row.getWalletId() == null || byId.containsKey(row.getWalletId())) {
                continue;
            }
            walletRepository.findByIdForUser(row.getWalletId(), userId).ifPresent(wallet -> byId.put(wallet.getId(), wallet));
        }
        return byId;
    }

    private String buildAlertTitle(BudgetProgressProjection row) {
        return switch (row.getStatus()) {
            case STATUS_OVER_LIMIT -> "Đã vượt ngân sách";
            case STATUS_NEAR_LIMIT -> "Sắp hết ngân sách";
            default -> "Ngân sách trong tầm kiểm soát";
        };
    }

    private String buildAlertContent(BudgetProgressProjection row) {
        long remaining = nullToZero(row.getRemaining());
        int daysRemaining = row.getDaysRemaining() == null ? 0 : row.getDaysRemaining();

        if (STATUS_OVER_LIMIT.equals(row.getStatus())) {
            return "Vượt " + formatAmount(Math.abs(remaining)) + " đ khi kỳ còn " + daysRemaining + " ngày.";
        }

        LocalDate depletion = projectedDepletionDate(row);
        if (depletion != null) {
            long daysEarlier = ChronoUnit.DAYS.between(depletion, row.getEndDate());
            return "Với nhịp chi hiện tại, ngân sách sẽ hết vào ngày "
                    + String.format("%02d/%02d", depletion.getDayOfMonth(), depletion.getMonthValue())
                    + " — sớm hơn " + daysEarlier + " ngày so với cuối kỳ.";
        }
        return "Còn " + formatAmount(remaining) + " đ cho " + daysRemaining + " ngày còn lại của kỳ.";
    }

    /**
     * api/05 mục 2.3 — dự báo theo nhịp chi, để cảnh báo TRƯỚC khi vượt thay vì đợi vượt rồi mới
     * báo. Trả {@code null} khi:
     *
     * <ul>
     *   <li>chưa chi đồng nào, hoặc đã vượt hạn mức ({@code remaining <= 0}) — không còn gì để dự
     *       báo hết;
     *   <li>kỳ mới bắt đầu hôm nay ({@code daysElapsed <= 0}) — chưa có nhịp chi nào để ngoại suy,
     *       và phép chia sẽ chia cho 0;
     *   <li>ngày dự báo rơi SAU {@code end_date} — theo nhịp này tiêu vẫn không hết, không cần làm
     *       người dùng lo.
     * </ul>
     */
    private LocalDate projectedDepletionDate(BudgetProgressProjection row) {
        long spent = nullToZero(row.getSpentAmount());
        long remaining = nullToZero(row.getRemaining());
        if (spent <= 0 || remaining <= 0) {
            return null;
        }

        LocalDate today = LocalDate.now();
        long daysElapsed = ChronoUnit.DAYS.between(row.getStartDate(), today);
        if (daysElapsed <= 0) {
            return null;
        }

        double dailySpending = (double) spent / daysElapsed;
        LocalDate depletion = today.plusDays((long) Math.floor(remaining / dailySpending));

        return depletion.isBefore(row.getEndDate()) ? depletion : null;
    }

    /**
     * Kỳ mặc định khi người dùng CHƯA có ngân sách nào — tháng hiện tại.
     *
     * <p>api/05-NGAN-SACH.md mục 3 luôn trả {@code period}; trả {@code null} làm app sập ngay khi
     * parse (`period` không nullable ở model Flutter), tức màn Ngân sách trắng với mọi tài khoản
     * mới đăng ký. Kỳ là thuộc tính của MỐC THỜI GIAN đang xem, không phải của tập ngân sách.
     */
    private BudgetSummaryResponse.Period currentMonthPeriod() {
        LocalDate start = startOfCurrentPeriod("month");
        LocalDate end = endOfPeriod("month", start);
        return new BudgetSummaryResponse.Period(
                "month",
                periodLabel("month"),
                start,
                end,
                (int) ChronoUnit.DAYS.between(LocalDate.now(), end));
    }

    private BudgetSummaryResponse.Period toPeriod(BudgetProgressProjection row) {
        return new BudgetSummaryResponse.Period(
                row.getPeriodType(),
                periodLabel(row.getPeriodType()),
                row.getStartDate(),
                row.getEndDate(),
                row.getDaysRemaining());
    }

    private String periodLabel(String periodType) {
        return switch (periodType) {
            case "week" -> "Tuần này";
            case "month" -> "Tháng này";
            case "quarter" -> "Quý này";
            case "year" -> "Năm nay";
            default -> periodType;
        };
    }

    /** Ngưỡng 0.8/1.0 giống hệt view {@code v_budget_progress} — dùng cho con số TỔNG HỢP. */
    private String statusOfRatio(BigDecimal ratio) {
        if (ratio.compareTo(BigDecimal.ONE) >= 0) {
            return STATUS_OVER_LIMIT;
        }
        if (ratio.compareTo(new BigDecimal("0.8")) >= 0) {
            return STATUS_NEAR_LIMIT;
        }
        return STATUS_NORMAL;
    }

    /** Đầu kỳ hiện tại theo {@code period_type} (api/05 mục 4 — mặc định khi client không gửi). */
    private LocalDate startOfCurrentPeriod(String periodType) {
        LocalDate today = LocalDate.now();
        return switch (periodType) {
            case "week" -> today.with(DayOfWeek.MONDAY);
            case "month" -> today.withDayOfMonth(1);
            case "quarter" -> today.withDayOfMonth(1).withMonth((today.getMonthValue() - 1) / 3 * 3 + 1);
            case "year" -> today.withDayOfYear(1);
            default -> throw new BusinessException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST.value(), "Loại kỳ ngân sách không hợp lệ.");
        };
    }

    /**
     * Ngày cuối kỳ tính từ {@code start_date}. Dùng {@code plusMonths().minusDays(1)} thay vì "ngày
     * cuối tháng" cứng để kỳ bắt đầu giữa tháng (client tự chọn {@code start_date}) vẫn ra một kỳ
     * dài đúng một tháng, không bị cụt. Với {@code start_date} là mặc định (ngày 1) thì hai cách
     * cho kết quả giống hệt nhau.
     */
    static LocalDate endOfPeriod(String periodType, LocalDate startDate) {
        return switch (periodType) {
            case "week" -> startDate.plusDays(6);
            case "month" -> startDate.plusMonths(1).minusDays(1);
            case "quarter" -> startDate.plusMonths(3).minusDays(1);
            case "year" -> startDate.plusYears(1).minusDays(1);
            default -> throw new BusinessException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST.value(), "Loại kỳ ngân sách không hợp lệ.");
        };
    }

    /** Làm tròn LÊN hàng trăm nghìn (api/05 mục 6): 3.980.000 × 1.05 = 4.179.000 -> 4.200.000. */
    private long roundUpToHundredThousand(long amount) {
        return (long) (Math.ceil(amount / 100_000.0) * 100_000);
    }

    /**
     * Định dạng số tiền VND kiểu Việt Nam: dấu chấm phân cách hàng nghìn.
     *
     * <p>{@code public} vì {@code TransactionService} (package khác) cũng dựng câu cảnh báo ngân
     * sách cho {@code affected_budgets} — dùng chung một chỗ định dạng để hai đường không trôi
     * dạt về câu chữ.
     */
    public static String formatAmount(long amount) {
        return String.format("%,d", amount).replace(",", ".");
    }

    private int percentLabel(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Không có quyền cũng trả 404 (không phải 403) để không lộ việc bản ghi có tồn tại hay không. */
    private BusinessException notFound() {
        return new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ngân sách.");
    }
}
