package com.datn.financeapp.goal.mapper;

import com.datn.financeapp.category.dto.response.IconRefResponse;
import com.datn.financeapp.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.goal.dto.response.GoalDetailResponse;
import com.datn.financeapp.goal.dto.response.GoalListItemResponse;
import com.datn.financeapp.goal.entity.GoalContribution;
import com.datn.financeapp.goal.entity.SavingsGoal;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Mapper(componentModel = "spring")
public interface GoalMapper {

    default GoalListItemResponse toListItem(
            SavingsGoal goal,
            WalletRefResponse wallet,
            long savedAmount,
            String status,
            IconRefResponse iconRef,
            int contributionCount) {
        if (goal == null) {
            return null;
        }
        long missingAmount = Math.max(0, goal.getTargetAmount() - savedAmount);
        BigDecimal progress = progressRatio(savedAmount, goal.getTargetAmount());
        String progressLabel = progress.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%";

        LocalDate today = LocalDate.now();
        Integer daysRemaining =
                goal.getTargetDate() == null ? null : (int) ChronoUnit.DAYS.between(today, goal.getTargetDate());

        GoalListItemResponse.IconSummary icon =
                iconRef == null ? null : new GoalListItemResponse.IconSummary(iconRef.code(), iconRef.pathData());

        return new GoalListItemResponse(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                savedAmount,
                missingAmount,
                progress,
                progressLabel,
                goal.getTargetDate(),
                daysRemaining,
                status,
                icon,
                wallet == null ? null : new GoalListItemResponse.WalletSummary(wallet.id(), wallet.name()),
                buildSuggestion(goal, missingAmount, today),
                contributionCount);
    }

    default CreateGoalResponse toCreateGoalResponse(GoalListItemResponse item) {
        return new CreateGoalResponse(item);
    }

    default CreateContributionResponse toContributionResponse(
            GoalContribution contribution,
            long refreshedSavedAmount,
            BigDecimal progress,
            String refreshedStatus,
            CreateContributionResponse.TransactionSummary transactionSummary,
            CreateContributionResponse.NewBalance newBalance) {
        return new CreateContributionResponse(
                new CreateContributionResponse.Contribution(
                        contribution.getId(),
                        contribution.getAmount(),
                        contribution.getContributedDate(),
                        contribution.getTransactionId()),
                new CreateContributionResponse.GoalProgress(
                        refreshedSavedAmount,
                        progress,
                        refreshedStatus),
                transactionSummary,
                newBalance);
    }

    default GoalDetailResponse.ContributionHistoryItem toHistoryItem(GoalContribution c) {
        if (c == null) {
            return null;
        }
        return new GoalDetailResponse.ContributionHistoryItem(
                c.getId(), c.getAmount(), c.getContributedDate(), c.getTransactionId());
    }

    default GoalDetailResponse toDetailResponse(
            GoalListItemResponse item,
            List<GoalDetailResponse.ContributionHistoryItem> contributions) {
        return new GoalDetailResponse(item, contributions);
    }

    default BigDecimal progressRatio(long savedAmount, long targetAmount) {
        return BigDecimal.valueOf(savedAmount).divide(BigDecimal.valueOf(targetAmount), 4, RoundingMode.HALF_UP);
    }

    default GoalListItemResponse.Suggestion buildSuggestion(SavingsGoal goal, long missingAmount, LocalDate today) {
        if (goal.getTargetDate() == null || missingAmount <= 0) {
            return null;
        }
        long monthsRemaining = Math.max(1, ChronoUnit.MONTHS.between(today, goal.getTargetDate()));
        long monthlyRequired = (long) Math.ceil((double) missingAmount / monthsRemaining);
        String content = String.format("Cần nạp %,d đ mỗi tháng để đạt mục tiêu đúng hạn.", monthlyRequired);
        return new GoalListItemResponse.Suggestion(monthlyRequired, content, null);
    }
}
