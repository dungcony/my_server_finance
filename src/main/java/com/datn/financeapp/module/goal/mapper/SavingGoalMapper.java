package com.datn.financeapp.module.goal.mapper;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalListRes;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalRes;
import com.datn.financeapp.module.goal.entity.GoalContribution;
import com.datn.financeapp.module.goal.entity.SavingGoal;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GoalMapper {

    SavingGoalRes toSavingGoalRes(SavingGoal goal);

    SavingGoalListRes.SavingGoalSummayRes toSavingGoalSummaryRes(SavingGoal goal);

    SavingGoal toEntity();

    ContributionGoalRes toContributionRes(GoalContribution contribution);

    ContributionGoalListRes.ContributionGoalSummayRes toContributionSummaryRes(GoalContribution contribution);

    default SavingGoalListRes toListRes(Page<SavingGoal> page) {
        if (page == null) {
            return null;
        }
        List<SavingGoalListRes.SavingGoalSummayRes> items = page.getContent().stream()
                .map(this::toSavingGoalSummaryRes)
                .toList();
        PageMeta meta = new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return new SavingGoalListRes(items, meta);
    }

    default ContributionGoalListRes toContributionListRes(Page<GoalContribution> page) {
        if (page == null) {
            return null;
        }
        List<ContributionGoalListRes.ContributionGoalSummayRes> items = page.getContent().stream()
                .map(this::toContributionSummaryRes)
                .toList();
        PageMeta meta = new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return new ContributionGoalListRes(items, meta);
    }
}
