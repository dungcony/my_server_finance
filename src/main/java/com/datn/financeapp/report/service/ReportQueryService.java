package com.datn.financeapp.report.service;

import com.datn.financeapp.budget.service.BudgetService;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.entity.Icon;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.category.repository.IconRepository;
import com.datn.financeapp.report.dto.CategoryBreakdownResponse;
import com.datn.financeapp.report.dto.CategoryGroupBreakdownResponse;
import com.datn.financeapp.report.dto.DailyTrendResponse;
import com.datn.financeapp.report.dto.MonthlyTrendResponse;
import com.datn.financeapp.report.dto.ReportHomeResponse;
import com.datn.financeapp.report.dto.ReportSummaryResponse;
import com.datn.financeapp.report.repository.ReportRepository;
import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6 điểm cuối báo cáo (REPORT-01..04, api/06-BAO-CAO.md) — TÍNH TẠI CHỖ mỗi lần gọi, KHÔNG cache
 * (D-54, vì mọi truy vấn đọc trực tiếp {@code transactions} nên luôn chính xác, không cần đồng
 * bộ). {@code @Transactional(readOnly = true)} đặt CẤP CLASS vì mọi method đều chỉ đọc; không có
 * method con nào cần transaction MỚI nên không có nguy cơ self-invocation mất proxy.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReportRepository reportRepository;
    private final CategoryRepository categoryRepository;
    private final IconRepository iconRepository;
    private final WalletRepository walletRepository;
    private final BudgetService budgetService;
    private final JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------ 2. summary

    public ReportSummaryResponse summary(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, UUID walletId) {
        DateRange range = resolveRange(period, fromDate, toDate);

        ReportRepository.SummaryProjection current =
                reportRepository.summary(userId, range.from(), range.to(), walletId);
        long totalIncome = orZero(current.getTotalIncome());
        long totalExpense = orZero(current.getTotalExpense());
        long transactionCount = orZero(current.getTransactionCount());
        long netIncome = totalIncome - totalExpense;

        Long closingBalance = currentBalanceForWallets(userId, walletId);
        long openingBalance = closingBalance - totalIncome + totalExpense;

        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        long avgDailyExpense = days > 0 ? Math.round((double) totalExpense / days) : 0;

        DateRange previous = previousRange(range);
        ReportRepository.SummaryProjection prev =
                reportRepository.summary(userId, previous.from(), previous.to(), walletId);
        long previousExpense = orZero(prev.getTotalExpense());
        Long difference = previousExpense == 0 && totalExpense == 0 ? 0L : totalExpense - previousExpense;
        Double changeRatio = previousExpense == 0 ? null : (double) difference / previousExpense;
        String label = buildComparisonLabel(changeRatio);

        return new ReportSummaryResponse(
                new ReportSummaryResponse.PeriodInfo(range.label(), range.from(), range.to()),
                openingBalance,
                closingBalance,
                totalIncome,
                totalExpense,
                netIncome,
                transactionCount,
                avgDailyExpense,
                new ReportSummaryResponse.VsPreviousPeriod(previousExpense, difference, changeRatio, label));
    }

    private String buildComparisonLabel(Double changeRatio) {
        if (changeRatio == null) {
            return "Chưa có dữ liệu kỳ trước để so sánh.";
        }
        double percent = Math.abs(changeRatio) * 100;
        String direction = changeRatio < 0 ? "Giảm" : "Tăng";
        return String.format("%s %.1f%% so với kỳ trước", direction, percent);
    }

    private Long currentBalanceForWallets(UUID userId, UUID walletId) {
        if (walletId != null) {
            return walletRepository.findCurrentBalanceNative(walletId).orElse(0L);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(current_balance), 0) FROM wallets "
                        + "WHERE user_id = ? AND NOT is_deleted",
                Long.class,
                userId);
        return total == null ? 0L : total;
    }

    // ------------------------------------------------------- 3. by-category-group

    public CategoryGroupBreakdownResponse byCategoryGroup(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, String type) {
        DateRange range = resolveRange(period, fromDate, toDate);
        String txnType = type == null ? "expense" : type;

        List<ReportRepository.CategoryGroupAmountProjection> rows =
                reportRepository.byCategoryGroup(userId, range.from(), range.to(), txnType);
        long total = rows.stream().mapToLong(r -> orZero(r.getAmount())).sum();

        List<CategoryGroupBreakdownResponse.GroupItem> groups = new ArrayList<>();
        for (ReportRepository.CategoryGroupAmountProjection row : rows) {
            long amount = orZero(row.getAmount());
            double ratio = total == 0 ? 0 : (double) amount / total;
            List<ReportRepository.CategoryAmountProjection> catRows = reportRepository.byCategoryInGroup(
                    userId, range.from(), range.to(), txnType, row.getCategoryGroupId());
            List<CategoryGroupBreakdownResponse.CategoryItem> categories = catRows.stream()
                    .map(c -> new CategoryGroupBreakdownResponse.CategoryItem(
                            c.getCategoryId(),
                            c.getName(),
                            orZero(c.getAmount()),
                            amount == 0 ? 0 : (double) orZero(c.getAmount()) / amount))
                    .toList();
            groups.add(new CategoryGroupBreakdownResponse.GroupItem(
                    row.getCategoryGroupId(),
                    row.getName(),
                    row.getColor(),
                    amount,
                    ratio,
                    Math.round(ratio * 100) + "%",
                    orZero(row.getTransactionCount()),
                    categories));
        }
        return new CategoryGroupBreakdownResponse(total, groups);
    }

    // ------------------------------------------------------------ 4. by-category

    public CategoryBreakdownResponse byCategory(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, String type, String level, int limit) {
        DateRange range = resolveRange(period, fromDate, toDate);
        String txnType = type == null ? "expense" : type;
        boolean parentLevel = level == null || "parent".equals(level);

        List<ReportRepository.CategoryParentAmountProjection> rows = parentLevel
                ? reportRepository.byCategoryParentLevel(userId, range.from(), range.to(), txnType, limit)
                : reportRepository.byCategoryChildLevel(userId, range.from(), range.to(), txnType, limit);

        long total = rows.stream().mapToLong(r -> orZero(r.getAmount())).sum();
        long transactionTotal = rows.stream().mapToLong(r -> orZero(r.getTransactionCount())).sum();
        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        long avgPerDay = days > 0 ? Math.round((double) total / days) : 0;

        Map<UUID, Icon> iconCache = new HashMap<>();
        List<CategoryBreakdownResponse.Item> items = new ArrayList<>();
        for (ReportRepository.CategoryParentAmountProjection row : rows) {
            long amount = orZero(row.getAmount());
            long count = orZero(row.getTransactionCount());
            Icon icon = row.getIconId() == null
                    ? null
                    : iconCache.computeIfAbsent(row.getIconId(), id -> iconRepository.findById(id).orElse(null));

            List<CategoryBreakdownResponse.ChildDetail> childrenDetail = List.of();
            if (Boolean.TRUE.equals(row.getHasChildren()) && parentLevel) {
                List<UUID> tree = categoryRepository.findCategoryTree(row.getCategoryId());
                childrenDetail = reportRepository
                        .childrenDetail(
                                userId,
                                range.from(),
                                range.to(),
                                txnType,
                                row.getCategoryId(),
                                tree.toArray(new UUID[0]))
                        .stream()
                        .map(c -> new CategoryBreakdownResponse.ChildDetail(
                                c.getCategoryId(),
                                c.getCategoryId() == null
                                        ? "Không phân loại " + txnType + " tiết"
                                        : c.getName(),
                                orZero(c.getAmount()),
                                0L))
                        .toList();
            }

            items.add(new CategoryBreakdownResponse.Item(
                    row.getCategoryId(),
                    row.getName(),
                    icon == null ? null : new CategoryBreakdownResponse.IconRef(icon.getCode(), icon.getPathData()),
                    row.getColor(),
                    amount,
                    total == 0 ? 0 : (double) amount / total,
                    count,
                    count == 0 ? 0 : Math.round((double) amount / count),
                    Boolean.TRUE.equals(row.getHasChildren()),
                    childrenDetail));
        }
        return new CategoryBreakdownResponse(total, avgPerDay, items);
    }

    // -------------------------------------------------------- 5. daily-trend

    public DailyTrendResponse dailyTrend(UUID userId, YearMonth month) {
        YearMonth target = month == null ? YearMonth.now() : month;
        LocalDate start = target.atDay(1);
        LocalDate monthEnd = target.atEndOfMonth();
        LocalDate today = LocalDate.now();
        LocalDate effectiveEnd = monthEnd.isAfter(today) ? today : monthEnd;
        boolean isCurrentOrFutureMonth = !effectiveEnd.isBefore(start);

        List<ReportRepository.DailyAmountProjection> rows = isCurrentOrFutureMonth
                ? reportRepository.dailyExpense(userId, start, effectiveEnd)
                : reportRepository.dailyExpense(userId, start, monthEnd);

        List<DailyTrendResponse.CurrentPoint> currentLine = new ArrayList<>();
        long cumulative = 0;
        for (ReportRepository.DailyAmountProjection row : rows) {
            long spentThatDay = orZero(row.getAmount());
            cumulative += spentThatDay;
            currentLine.add(new DailyTrendResponse.CurrentPoint(
                    row.getDate().getDayOfMonth(), spentThatDay, cumulative));
        }
        long currentTotal = cumulative;

        // Trung bình 3 tháng trước — tổng chi từng tháng chia đều ra days_in_month để có 1 đường
        // mốc so sánh liền mạch hết tháng (api/06 mục 5: avg_3_months_line chạy đủ 31 ngày).
        List<Long> pastMonthTotals = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            YearMonth past = target.minusMonths(i);
            ReportRepository.SummaryProjection s =
                    reportRepository.summary(userId, past.atDay(1), past.atEndOfMonth(), null);
            pastMonthTotals.add(orZero(s.getTotalExpense()));
        }
        double avgTotal = pastMonthTotals.stream().mapToLong(Long::longValue).average().orElse(0);
        int daysInMonth = target.lengthOfMonth();
        List<DailyTrendResponse.AveragePoint> avgLine = new ArrayList<>();
        double perDay = daysInMonth == 0 ? 0 : avgTotal / daysInMonth;
        for (int d = 1; d <= daysInMonth; d++) {
            avgLine.add(new DailyTrendResponse.AveragePoint(d, Math.round(perDay * d)));
        }
        long avgSamePoint = Math.round(perDay * effectiveEnd.getDayOfMonth());

        double ratioVsAverage = avgSamePoint == 0 ? 0 : (double) currentTotal / avgSamePoint;
        String assessLabel = ratioVsAverage > 1.1 ? "Cao hơn thường lệ"
                : ratioVsAverage < 0.9 ? "Thấp hơn thường lệ" : "Bình thường";
        String content = String.format(
                "Đến ngày %d, bạn đã chi %s%.0f%% so với trung bình 3 tháng trước cùng thời điểm.",
                effectiveEnd.getDayOfMonth(),
                ratioVsAverage >= 1 ? "nhiều hơn " : "ít hơn ",
                Math.abs(ratioVsAverage - 1) * 100);

        return new DailyTrendResponse(
                target.format(MONTH_FORMAT),
                daysInMonth,
                currentLine,
                avgLine,
                currentTotal,
                avgSamePoint,
                new DailyTrendResponse.Assessment(assessLabel, ratioVsAverage, content));
    }

    // ------------------------------------------------------ 6. monthly-trend

    public MonthlyTrendResponse monthlyTrend(UUID userId, int monthsCount) {
        int count = Math.min(Math.max(monthsCount, 1), 24);
        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(count - 1);

        List<ReportRepository.MonthlyAmountProjection> rows =
                reportRepository.monthlyTrend(userId, start.atDay(1), end.atEndOfMonth());
        Map<YearMonth, ReportRepository.MonthlyAmountProjection> byMonth = new HashMap<>();
        for (ReportRepository.MonthlyAmountProjection row : rows) {
            byMonth.put(YearMonth.from(row.getMonth()), row);
        }

        List<MonthlyTrendResponse.MonthItem> months = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            YearMonth ym = start.plusMonths(i);
            ReportRepository.MonthlyAmountProjection row = byMonth.get(ym);
            long income = row == null ? 0 : orZero(row.getTotalIncome());
            long expense = row == null ? 0 : orZero(row.getTotalExpense());
            months.add(new MonthlyTrendResponse.MonthItem(
                    ym.format(MONTH_FORMAT), "T" + ym.getMonthValue(), income, expense, income - expense));
        }

        double averageExpense =
                months.stream().mapToLong(MonthlyTrendResponse.MonthItem::totalExpense).average().orElse(0);
        MonthlyTrendResponse.MonthItem highest = months.stream()
                .max(Comparator.comparingLong(MonthlyTrendResponse.MonthItem::totalExpense))
                .orElse(null);
        MonthlyTrendResponse.MonthItem lowest = months.stream()
                .min(Comparator.comparingLong(MonthlyTrendResponse.MonthItem::totalExpense))
                .orElse(null);

        return new MonthlyTrendResponse(
                months,
                Math.round(averageExpense),
                highest == null ? null : new MonthlyTrendResponse.MonthAmount(highest.month(), highest.totalExpense()),
                lowest == null ? null : new MonthlyTrendResponse.MonthAmount(lowest.month(), lowest.totalExpense()));
    }

    // ---------------------------------------------------------------- 1. home

    /**
     * Gộp gọi các method khác trong CÙNG service (đều là method Java thuần trả DTO, không mang
     * annotation {@code @Transactional} riêng — chỉ class-level {@code readOnly=true} là đủ,
     * không có nguy cơ self-invocation vì không method con nào cần transaction MỚI).
     */
    public ReportHomeResponse home(UUID userId, String period) {
        DateRange range = resolveRange(period, null, null);

        Long personalTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(current_balance), 0) FROM wallets "
                        + "WHERE user_id = ? AND include_in_total AND NOT is_deleted",
                Long.class,
                userId);
        Long sharedTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(current_balance), 0) FROM wallets "
                        + "WHERE group_id IN (SELECT group_id FROM group_members WHERE user_id = ? AND is_active) "
                        + "AND include_in_total AND NOT is_deleted",
                Long.class,
                userId);

        List<Wallet> wallets = walletRepository.findAllForUser(userId, null, null, true);
        List<ReportHomeResponse.WalletItem> topWallets = wallets.stream()
                .limit(3)
                .map(w -> new ReportHomeResponse.WalletItem(
                        w.getId(), w.getName(), w.getCurrentBalance(), w.getType()))
                .toList();

        ReportRepository.SummaryProjection summaryRow =
                reportRepository.summary(userId, range.from(), range.to(), null);
        long totalIncome = orZero(summaryRow.getTotalIncome());
        long totalExpense = orZero(summaryRow.getTotalExpense());

        DailyTrendResponse trend = dailyTrend(userId, YearMonth.from(range.to()));
        long maxValue = trend.currentLine().stream()
                .mapToLong(DailyTrendResponse.CurrentPoint::cumulative)
                .max()
                .orElse(0);
        DailyTrendResponse.CurrentPoint highest = trend.currentLine().stream()
                .max(Comparator.comparingLong(DailyTrendResponse.CurrentPoint::spentThatDay))
                .orElse(null);
        ReportHomeResponse.DailyTrend.Note note = highest == null
                ? new ReportHomeResponse.DailyTrend.Note(null, 0L, trend.avg3MonthsSamePoint())
                : new ReportHomeResponse.DailyTrend.Note(
                        String.format("%02d/%02d", highest.date(), range.to().getMonthValue()),
                        highest.spentThatDay(),
                        trend.avg3MonthsSamePoint());

        CategoryBreakdownResponse topSpendingBreakdown =
                byCategory(userId, period, null, null, "expense", "parent", 5);
        List<ReportHomeResponse.TopSpendingItem> topSpending = topSpendingBreakdown.items().stream()
                .map(item -> new ReportHomeResponse.TopSpendingItem(
                        item.categoryId(),
                        item.name(),
                        item.amount(),
                        item.ratio(),
                        item.icon() == null
                                ? null
                                : new ReportHomeResponse.IconRef(item.icon().code(), item.icon().pathData()),
                        item.color()))
                .toList();

        List<Transaction> recent = reportRepository.eligibleTransactions(userId, range.from(), range.to()).stream()
                .limit(10)
                .toList();
        List<ReportHomeResponse.RecentTransactionItem> recentTransactions = new ArrayList<>();
        Map<UUID, Wallet> walletCache = new HashMap<>();
        Map<UUID, Category> categoryCache = new HashMap<>();
        Map<UUID, Icon> iconCache = new HashMap<>();
        for (Transaction t : recent) {
            Wallet wallet = walletCache.computeIfAbsent(
                    t.getWalletId(), id -> walletRepository.findById(id).orElse(null));
            ReportHomeResponse.CategoryRef categoryRef = null;
            if (t.getCategoryId() != null) {
                Category category =
                        categoryCache.computeIfAbsent(t.getCategoryId(), id -> categoryRepository
                                .findById(id)
                                .orElse(null));
                if (category != null) {
                    Icon icon = iconCache.computeIfAbsent(
                            category.getIconId(), id -> iconRepository.findById(id).orElse(null));
                    categoryRef = new ReportHomeResponse.CategoryRef(
                            category.getName(),
                            icon == null ? null : new ReportHomeResponse.IconRef(icon.getCode(), icon.getPathData()),
                            category.getColor());
                }
            }
            recentTransactions.add(new ReportHomeResponse.RecentTransactionItem(
                    t.getId(),
                    t.getType(),
                    t.getAmount(),
                    t.getDate(),
                    t.getDisplayName(),
                    categoryRef,
                    wallet == null ? null : new ReportHomeResponse.WalletRef(wallet.getName())));
        }

        // Hình dạng riêng theo api/06 mục 1 — KHÔNG dùng lại budgetService.alerts() (hợp đồng
        // của api/05 mục 7, tên khoá khác hẳn). Xem BudgetService.attentionItems().
        List<ReportHomeResponse.BudgetAttentionItem> budgetsNeedingAttention =
                budgetService.attentionItems(userId);

        return new ReportHomeResponse(
                new ReportHomeResponse.Balance(
                        personalTotal == null ? 0 : personalTotal, sharedTotal == null ? 0 : sharedTotal),
                topWallets,
                new ReportHomeResponse.PeriodSummary(
                        range.label(), totalIncome, totalExpense, totalIncome - totalExpense),
                new ReportHomeResponse.DailyTrend(trend.currentLine(), trend.avg3MonthsLine(), maxValue, note),
                topSpending,
                recentTransactions,
                budgetsNeedingAttention);
    }

    // ------------------------------------------------------------- helpers

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * Suy ra khoảng ngày từ {@code period}/{@code from_date}/{@code to_date} — theo api/00 mục
     * 7.2, {@code period} được ưu tiên khi cả hai cùng gửi.
     */
    private DateRange resolveRange(String period, LocalDate fromDate, LocalDate toDate) {
        if (period != null) {
            LocalDate today = LocalDate.now();
            return switch (period) {
                case "week" -> {
                    LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - 1);
                    yield new DateRange(start, start.plusDays(6), "Tuần này");
                }
                case "quarter" -> {
                    int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                    LocalDate start = LocalDate.of(today.getYear(), quarterMonth, 1);
                    yield new DateRange(start, start.plusMonths(3).minusDays(1), "Quý này");
                }
                case "year" -> {
                    LocalDate start = LocalDate.of(today.getYear(), 1, 1);
                    yield new DateRange(start, LocalDate.of(today.getYear(), 12, 31), "Năm nay");
                }
                default -> {
                    YearMonth ym = YearMonth.from(today);
                    yield new DateRange(
                            ym.atDay(1), ym.atEndOfMonth(), "Tháng " + ym.getMonthValue() + ", " + ym.getYear());
                }
            };
        }
        if (fromDate != null && toDate != null) {
            return new DateRange(fromDate, toDate, fromDate + " - " + toDate);
        }
        YearMonth ym = YearMonth.now();
        return new DateRange(ym.atDay(1), ym.atEndOfMonth(), "Tháng " + ym.getMonthValue() + ", " + ym.getYear());
    }

    private DateRange previousRange(DateRange range) {
        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        LocalDate prevTo = range.from().minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        return new DateRange(prevFrom, prevTo, "Kỳ trước");
    }

    private record DateRange(LocalDate from, LocalDate to, String label) {}
}
