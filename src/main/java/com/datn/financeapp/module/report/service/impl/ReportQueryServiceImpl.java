package com.datn.financeapp.module.report.service.impl;

import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.service.BudgetOverviewService;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.report.dto.response.CategoryBreakdownResponse;
import com.datn.financeapp.module.report.dto.response.CategoryGroupBreakdownResponse;
import com.datn.financeapp.module.report.dto.response.DailyTrendResponse;
import com.datn.financeapp.module.report.dto.response.MonthlyTrendResponse;
import com.datn.financeapp.module.report.dto.response.ReportHomeResponse;
import com.datn.financeapp.module.report.dto.response.ReportSummaryResponse;
import com.datn.financeapp.module.report.repository.ReportRepository;
import com.datn.financeapp.module.report.service.ReportQueryService;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý các truy vấn báo cáo thống kê thu chi, cơ cấu danh mục và xu hướng.
 * Triển khai giao diện {@link ReportQueryService}, tương tác trực tiếp với {@link ReportRepository} và JDBC.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryServiceImpl implements ReportQueryService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReportRepository reportRepository;
    private final BudgetCategoryHelper budgetCategoryHelper;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final BudgetOverviewService budgetOverviewService;
    private final UserTimezoneHelper timezoneHelper;
    private final IconService iconService;
    private final JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------ 2. summary

    /**
     * {@inheritDoc}
     */
    @Override
    public ReportSummaryResponse summary(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, UUID walletId) {
        DateRange range = resolveRange(period, fromDate, toDate);
        ZoneId zone = timezoneHelper.getCurrentZone();
        Instant startInstant = timezoneHelper.toStartInstant(range.from(), zone);
        Instant endInstant = timezoneHelper.toEndInstant(range.to(), zone);

        ReportRepository.SummaryProjection current =
                reportRepository.summary(userId, startInstant, endInstant, walletId);
        long totalIncome = orZero(current.getTotalIncome());
        long totalExpense = orZero(current.getTotalExpense());
        long transactionCount = orZero(current.getTransactionCount());
        long netIncome = totalIncome - totalExpense;

        Long closingBalance = currentBalanceForWallets(userId, walletId);
        long openingBalance = closingBalance - totalIncome + totalExpense;

        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        long avgDailyExpense = days > 0 ? Math.round((double) totalExpense / days) : 0;

        DateRange previous = previousRange(range);
        Instant prevStart = timezoneHelper.toStartInstant(previous.from(), zone);
        Instant prevEnd = timezoneHelper.toEndInstant(previous.to(), zone);

        ReportRepository.SummaryProjection prev =
                reportRepository.summary(userId, prevStart, prevEnd, walletId);
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
            return walletRepository.findByIdAndUserIdAndIsDeleted(walletId, userId, false)
                    .map(Wallet::getBalance)
                    .orElse(0L);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance), 0) FROM wallets WHERE user_id = ? AND NOT is_deleted",
                Long.class,
                userId);
        return total == null ? 0L : total;
    }

    // ------------------------------------------------------- 3. by-category-group

    /**
     * {@inheritDoc}
     */
    @Override
    public CategoryGroupBreakdownResponse byCategoryGroup(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, String type, UUID walletId) {
        DateRange range = resolveRange(period, fromDate, toDate);
        ZoneId zone = timezoneHelper.getCurrentZone();
        Instant startInstant = timezoneHelper.toStartInstant(range.from(), zone);
        Instant endInstant = timezoneHelper.toEndInstant(range.to(), zone);
        String txnType = (type == null || "expense".equalsIgnoreCase(type)) ? "EXPENSE" : "INCOME";

        List<ReportRepository.CategoryGroupAmountProjection> rows =
                reportRepository.byCategoryGroup(userId, startInstant, endInstant, txnType, walletId);
        long total = rows.stream().mapToLong(r -> orZero(r.getAmount())).sum();

        List<CategoryGroupBreakdownResponse.GroupItem> groups = new ArrayList<>();
        for (ReportRepository.CategoryGroupAmountProjection row : rows) {
            long amount = orZero(row.getAmount());
            double ratio = total == 0 ? 0 : (double) amount / total;
            List<ReportRepository.CategoryAmountProjection> catRows = reportRepository.byCategoryInGroup(
                    userId, startInstant, endInstant, txnType, row.getCode(), walletId);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public CategoryBreakdownResponse byCategory(
            UUID userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            String type,
            String level,
            int limit,
            UUID walletId) {
        DateRange range = resolveRange(period, fromDate, toDate);
        ZoneId zone = timezoneHelper.getCurrentZone();
        Instant startInstant = timezoneHelper.toStartInstant(range.from(), zone);
        Instant endInstant = timezoneHelper.toEndInstant(range.to(), zone);
        String txnType = (type == null || "expense".equalsIgnoreCase(type)) ? "EXPENSE" : "INCOME";
        boolean parentLevel = level == null || "parent".equalsIgnoreCase(level);

        List<ReportRepository.CategoryParentAmountProjection> rows = parentLevel
                ? reportRepository.byCategoryParentLevel(userId, startInstant, endInstant, txnType, limit, walletId)
                : reportRepository.byCategoryChildLevel(userId, startInstant, endInstant, txnType, limit, walletId);

        long total = rows.stream().mapToLong(r -> orZero(r.getAmount())).sum();
        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        long avgPerDay = days > 0 ? Math.round((double) total / days) : 0;

        List<CategoryBreakdownResponse.Item> items = new ArrayList<>();
        for (ReportRepository.CategoryParentAmountProjection row : rows) {
            long amount = orZero(row.getAmount());
            long count = orZero(row.getTransactionCount());
            CategoryBreakdownResponse.IconRef icon = row.getIconCode() != null
                    ? new CategoryBreakdownResponse.IconRef(row.getIconCode(), row.getIconPath())
                    : null;

            List<CategoryBreakdownResponse.ChildDetail> childrenDetail = List.of();
            if (Boolean.TRUE.equals(row.getHasChildren()) && parentLevel) {
                childrenDetail = reportRepository
                        .childrenDetail(userId, startInstant, endInstant, txnType, row.getCode(), walletId)
                        .stream()
                        .map(c -> new CategoryBreakdownResponse.ChildDetail(
                                c.getCategoryId(),
                                c.getName(),
                                orZero(c.getAmount()),
                                orZero(c.getTransactionCount())))
                        .toList();
            }

            items.add(new CategoryBreakdownResponse.Item(
                    row.getCategoryId(),
                    row.getName(),
                    icon,
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

    /**
     * {@inheritDoc}
     */
    @Override
    public DailyTrendResponse dailyTrend(UUID userId, YearMonth month, UUID walletId) {
        YearMonth target = month == null ? YearMonth.now() : month;
        LocalDate start = target.atDay(1);
        LocalDate monthEnd = target.atEndOfMonth();
        LocalDate today = LocalDate.now();
        LocalDate effectiveEnd = monthEnd.isAfter(today) ? today : monthEnd;
        boolean isCurrentOrFutureMonth = !effectiveEnd.isBefore(start);

        ZoneId zone = timezoneHelper.getCurrentZone();
        Instant startInstant = timezoneHelper.toStartInstant(start, zone);
        Instant endInstant = timezoneHelper.toEndInstant(isCurrentOrFutureMonth ? effectiveEnd : monthEnd, zone);
        String timeZoneStr = zone.getId();

        List<ReportRepository.DailyAmountProjection> rows =
                reportRepository.dailyExpense(userId, startInstant, endInstant, timeZoneStr, walletId);

        Map<Integer, Long> spentByDay = new HashMap<>();
        for (ReportRepository.DailyAmountProjection row : rows) {
            if (row.getDate() != null) {
                spentByDay.put(row.getDate().getDayOfMonth(), orZero(row.getAmount()));
            }
        }

        List<DailyTrendResponse.CurrentPoint> currentLine = new ArrayList<>();
        long cumulative = 0;
        int maxDay = isCurrentOrFutureMonth ? effectiveEnd.getDayOfMonth() : monthEnd.getDayOfMonth();
        for (int d = 1; d <= maxDay; d++) {
            long spentThatDay = spentByDay.getOrDefault(d, 0L);
            cumulative += spentThatDay;
            currentLine.add(new DailyTrendResponse.CurrentPoint(d, spentThatDay, cumulative));
        }
        long currentTotal = cumulative;

        List<Long> pastMonthTotals = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            YearMonth past = target.minusMonths(i);
            Instant pastStart = timezoneHelper.toStartInstant(past.atDay(1), zone);
            Instant pastEnd = timezoneHelper.toEndInstant(past.atEndOfMonth(), zone);
            ReportRepository.SummaryProjection s =
                    reportRepository.summary(userId, pastStart, pastEnd, walletId);
            pastMonthTotals.add(orZero(s.getTotalExpense()));
        }
        double avgTotal = pastMonthTotals.stream().mapToLong(val -> val != null ? val : 0L).average().orElse(0);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public MonthlyTrendResponse monthlyTrend(UUID userId, int monthsCount) {
        int count = Math.min(Math.max(monthsCount, 1), 24);
        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(count - 1);
        ZoneId zone = timezoneHelper.getCurrentZone();
        Instant startInstant = timezoneHelper.toStartInstant(start.atDay(1), zone);
        Instant endInstant = timezoneHelper.toEndInstant(end.atEndOfMonth(), zone);
        String timeZoneStr = zone.getId();

        List<ReportRepository.MonthlyAmountProjection> rows =
                reportRepository.monthlyTrend(userId, startInstant, endInstant, timeZoneStr);
        Map<YearMonth, ReportRepository.MonthlyAmountProjection> byMonth = new HashMap<>();
        for (ReportRepository.MonthlyAmountProjection row : rows) {
            if (row.getMonth() != null) {
                byMonth.put(YearMonth.from(row.getMonth()), row);
            }
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
                months.stream().mapToLong(item -> item.totalExpense()).average().orElse(0);
        MonthlyTrendResponse.MonthItem highest = months.stream()
                .max(Comparator.comparingLong(item -> item.totalExpense()))
                .orElse(null);
        MonthlyTrendResponse.MonthItem lowest = months.stream()
                .min(Comparator.comparingLong(item -> item.totalExpense()))
                .orElse(null);

        return new MonthlyTrendResponse(
                months,
                Math.round(averageExpense),
                highest == null ? null : new MonthlyTrendResponse.MonthAmount(highest.month(), highest.totalExpense()),
                lowest == null ? null : new MonthlyTrendResponse.MonthAmount(lowest.month(), lowest.totalExpense()));
    }

    // ---------------------------------------------------------------- 1. home

    /**
     * {@inheritDoc}
     */
    @Override
    public ReportHomeResponse home(UUID userId, String period, UUID walletId) {
        DateRange range = resolveRange(period, null, null);
        ZoneId zone = timezoneHelper.getCurrentZone();
        Instant startInstant = timezoneHelper.toStartInstant(range.from(), zone);
        Instant endInstant = timezoneHelper.toEndInstant(range.to(), zone);

        Long personalTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance), 0) FROM wallets WHERE user_id = ? AND NOT is_deleted",
                Long.class,
                userId);
        Long sharedTotal = 0L;

        List<Wallet> wallets = walletRepository.findAllByUserIdAndIsDeleted(
                userId, false, PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "balance"))).getContent();
        List<ReportHomeResponse.WalletItem> topWallets = wallets.stream()
                .map(w -> new ReportHomeResponse.WalletItem(
                        w.getId(), w.getName(), w.getBalance(), w.getType() != null ? w.getType().name() : ""))
                .toList();

        ReportRepository.SummaryProjection summaryRow =
                reportRepository.summary(userId, startInstant, endInstant, walletId);
        long totalIncome = orZero(summaryRow.getTotalIncome());
        long totalExpense = orZero(summaryRow.getTotalExpense());

        DailyTrendResponse trend = dailyTrend(userId, YearMonth.from(range.to()), walletId);
        long maxValue = trend.currentLine().stream()
                .mapToLong(p -> p.cumulative())
                .max()
                .orElse(0);
        DailyTrendResponse.CurrentPoint highest = trend.currentLine().stream()
                .max(Comparator.comparingLong(p -> p.spentThatDay()))
                .orElse(null);
        ReportHomeResponse.DailyTrend.Note note = highest == null
                ? new ReportHomeResponse.DailyTrend.Note(null, 0L, trend.avg3MonthsSamePoint())
                : new ReportHomeResponse.DailyTrend.Note(
                String.format("%02d/%02d", highest.date(), range.to().getMonthValue()),
                highest.spentThatDay(),
                trend.avg3MonthsSamePoint());

        CategoryBreakdownResponse topSpendingBreakdown =
                byCategory(userId, period, null, null, "expense", "parent", 5, walletId);
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

        List<Transaction> recent = reportRepository
                .eligibleTransactions(userId, startInstant, endInstant, walletId)
                .stream()
                .limit(10)
                .toList();
        List<ReportHomeResponse.RecentTransactionItem> recentTransactions = new ArrayList<>();
        Map<UUID, WalletSumaryRes> walletCache = new HashMap<>();
        Map<String, CateSumaryRes> categoryCache = new HashMap<>();

        for (Transaction t : recent) {
            WalletSumaryRes wallet = walletCache.computeIfAbsent(
                    t.getWalletId(),
                    wId -> walletService.findSumaryByIdAndUser(wId, userId, true).orElse(null));

            ReportHomeResponse.CategoryRef categoryRef = null;
            if (t.getCategoryCode() != null && !t.getCategoryCode().isBlank()) {
                CateSumaryRes category = categoryCache.computeIfAbsent(
                        t.getCategoryCode(),
                        code -> budgetCategoryHelper.findSummary(code).orElse(null));
                if (category != null) {
                    categoryRef = new ReportHomeResponse.CategoryRef(
                            category.name(),
                            new ReportHomeResponse.IconRef(category.code(), category.icon()),
                            category.color());
                }
            }

            LocalDate txDate = t.getOccurredAt() != null
                    ? timezoneHelper.toLocalDate(t.getOccurredAt(), zone)
                    : null;
            String displayName = t.getName() != null && !t.getName().isBlank()
                    ? t.getName()
                    : (categoryRef != null ? categoryRef.name() : "");

            recentTransactions.add(new ReportHomeResponse.RecentTransactionItem(
                    t.getId(),
                    t.getType() != null ? t.getType().name() : "",
                    t.getAmount(),
                    txDate,
                    displayName,
                    categoryRef,
                    wallet == null ? null : new ReportHomeResponse.WalletRef(wallet.name())));
        }

        List<ReportHomeResponse.BudgetAttentionItem> budgetsNeedingAttention =
                budgetOverviewService.alerts(userId).stream()
                        .map(alert -> new ReportHomeResponse.BudgetAttentionItem(
                                alert.budgetId(), alert.category(), alert.ratio(), alert.status().code()))
                        .toList();

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

    private DateRange resolveRange(String period, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            return new DateRange(
                    fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + " - "
                            + toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    fromDate,
                    toDate);
        }
        String p = period == null ? "month" : period.toLowerCase();
        LocalDate today = LocalDate.now();
        return switch (p) {
            case "week" -> {
                LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - 1);
                yield new DateRange("Tuần này", start, start.plusDays(6));
            }
            case "quarter" -> {
                int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(today.getYear(), quarterMonth, 1);
                LocalDate end = start.plusMonths(3).minusDays(1);
                yield new DateRange("Quý này", start, end);
            }
            case "year" -> new DateRange(
                    "Năm nay",
                    LocalDate.of(today.getYear(), 1, 1),
                    LocalDate.of(today.getYear(), 12, 31));
            default -> {
                YearMonth ym = YearMonth.from(today);
                yield new DateRange("Tháng này", ym.atDay(1), ym.atEndOfMonth());
            }
        };
    }

    private DateRange previousRange(DateRange current) {
        long days = ChronoUnit.DAYS.between(current.from(), current.to()) + 1;
        LocalDate prevTo = current.from().minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        return new DateRange("Kỳ trước", prevFrom, prevTo);
    }

    record DateRange(String label, LocalDate from, LocalDate to) {
    }
}
