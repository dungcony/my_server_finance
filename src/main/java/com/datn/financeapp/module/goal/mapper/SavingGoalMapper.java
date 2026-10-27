package com.datn.financeapp.module.goal.mapper;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.goal.dto.request.SavingGoalCreateReq;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalRes;
import com.datn.financeapp.module.goal.entity.SavingGoal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SavingGoalMapper {

    SavingGoalRes toSavingGoalRes(SavingGoal goal);

    SavingGoalListRes.SavingGoalSummayRes toSavingGoalSummaryRes(SavingGoal goal);

    default SavingGoalListRes toListRes(List<SavingGoal> savingGoals, PageMeta meta) {
        List<SavingGoalListRes.SavingGoalSummayRes> items = savingGoals.stream()
                .map(this::toSavingGoalSummaryRes)
                .toList();

        return new SavingGoalListRes(items, meta);
    }

    @Mapping(target = "status", constant = "IN_PROGRESS")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "savedAmount", source = "initialAmount")
    @Mapping(target = "new", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    SavingGoal toSavingGoal(SavingGoalCreateReq req);

}
