package com.datn.financeapp.budget.mapper;

import com.datn.financeapp.budget.dto.response.BudgetAlertResponse;
import com.datn.financeapp.budget.dto.response.BudgetImpactResponse;
import com.datn.financeapp.budget.dto.response.BudgetListItemResponse;
import com.datn.financeapp.budget.dto.response.BudgetSummaryResponse;
import com.datn.financeapp.budget.repository.BudgetProgressRepository.BudgetProgressProjection;
import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface BudgetMapper {

    Map<String, String> STATUS_COLORS = Map.of(
            "normal", "#4e9e76",
            "near_limit", "#d6a95e",
            "over_limit", "#e3796c");

    default BudgetListItemResponse toListItemResponse(
            BudgetProgressProjection row,
            CategoryRefResponse category,
            WalletRefResponse wallet) {
        if (row == null) {
            return null;
        }
        BigDecimal ratio = nullToZero(row.getRatio());

        BudgetListItemResponse.CategorySummary categorySummary = category == null
                ? null
                : new BudgetListItemResponse.CategorySummary(
                        category.id(),
                        category.name(),
                        category.icon() == null
                                ? null
                                : new BudgetListItemResponse.IconSummary(
                                        category.icon().code(), category.icon().pathData()),
                        category.color());

        return new BudgetListItemResponse(
                row.getId(),
                categorySummary,
                wallet == null ? null : new BudgetListItemResponse.WalletSummary(wallet.id(), wallet.name()),
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
                false,
                new BudgetListItemResponse.Alert(
                        buildAlertTitle(row), buildAlertContent(row), projectedDepletionDate(row)));
    }

    default BudgetSummaryResponse toSummaryResponse(
            BudgetSummaryResponse.Period period,
            long totalLimit,
            long totalSpent,
            BigDecimal ratio,
            String status,
            int totalCount,
            int overLimitCount,
            int nearLimitCount) {
        return new BudgetSummaryResponse(
                period,
                totalLimit,
                totalSpent,
                totalLimit - totalSpent,
                ratio,
                status,
                totalCount,
                overLimitCount,
                nearLimitCount);
    }

    default BudgetAlertResponse toAlertResponse(
            BudgetProgressProjection row,
            CategoryRefResponse category) {
        BigDecimal ratio = nullToZero(row.getRatio());
        return new BudgetAlertResponse(
                row.getId(),
                category != null ? category.name() : "Danh mục đã xoá",
                "over_limit".equals(row.getStatus()) ? "critical" : "alert",
                ratio,
                percentLabel(ratio) + "%",
                buildAlertTitle(row),
                buildAlertContent(row),
                null);
    }

    default BudgetImpactResponse toImpactResponse(
            BudgetProgressProjection budget,
            String categoryName,
            String alert) {
        return new BudgetImpactResponse(
                budget.getId(),
                categoryName,
                budget.getLimitAmount(),
                budget.getSpentAmount(),
                budget.getRatio(),
                budget.getStatus(),
                alert);
    }

    default BudgetSummaryResponse.Period toPeriod(BudgetProgressProjection row) {
        return new BudgetSummaryResponse.Period(
                row.getPeriodType(),
                periodLabel(row.getPeriodType()),
                row.getStartDate(),
                row.getEndDate(),
                row.getDaysRemaining());
    }

    default BudgetSummaryResponse.Period currentMonthPeriod(LocalDate start, LocalDate end) {
        return new BudgetSummaryResponse.Period(
                "month",
                periodLabel("month"),
                start,
                end,
                (int) ChronoUnit.DAYS.between(LocalDate.now(), end));
    }

    default String periodLabel(String periodType) {
        return switch (periodType) {
            case "week" -> "Tuần này";
            case "month" -> "Tháng này";
            case "quarter" -> "Quý này";
            case "year" -> "Năm nay";
            default -> periodType;
        };
    }

    default String buildAlertTitle(BudgetProgressProjection row) {
        return switch (row.getStatus()) {
            case "over_limit" -> "Đã vượt ngân sách";
            case "near_limit" -> "Sắp hết ngân sách";
            default -> "Ngân sách trong tầm kiểm soát";
        };
    }

    default String buildAlertContent(BudgetProgressProjection row) {
        long remaining = nullToZero(row.getRemaining());
        int daysRemaining = row.getDaysRemaining() == null ? 0 : row.getDaysRemaining();

        if ("over_limit".equals(row.getStatus())) {
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

    default LocalDate projectedDepletionDate(BudgetProgressProjection row) {
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

    static String formatAmount(long amount) {
        return String.format("%,d", amount).replace(",", ".");
    }

    default int percentLabel(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    default long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    default BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
