package com.datn.financeapp.module.goal.mapper;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalCreateReq;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalListRes;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import com.datn.financeapp.module.goal.entity.GoalContribution;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ContributionGoalMapper {

    ContributionGoalRes toContributionRes(GoalContribution contribution);

    default ContributionGoalListRes toContributionListRes(List<GoalContribution> goalContributions, PageMeta meta) {
        List<ContributionGoalRes> items = goalContributions.stream()
                .map(this::toContributionRes)
                .toList();
        return new ContributionGoalListRes(items, meta);
    }

    default GoalContribution toGoalContribution(UUID savingGoal, ContributionGoalCreateReq req) {

        return GoalContribution.builder()
                .id(UUID.randomUUID())
                .amount(req.amount())
                .contributedDate(req.contributedDate())
                .note(req.note())
                .goalId(savingGoal)
                .build();
    }
}
