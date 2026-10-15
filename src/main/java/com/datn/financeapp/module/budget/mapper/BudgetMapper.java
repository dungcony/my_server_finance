package com.datn.financeapp.module.budget.mapper;

import com.datn.financeapp.module.budget.dto.response.BudgetRes;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.helper.BudgetProgress;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Ghép {@link Budget}, tiến độ đã tính sẵn ({@link BudgetProgress}) và danh mục thành {@link BudgetRes}.
 * Không chứa phép tính nào: ngưỡng và tỉ lệ nằm hết ở {@link BudgetProgress}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BudgetMapper {

    @Mapping(target = "id", source = "budget.id")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "limitAmount", source = "budget.limitAmount")
    @Mapping(target = "spentAmount", source = "budget.spent")
    @Mapping(target = "remaining", source = "progress.remaining")
    @Mapping(target = "ratio", source = "progress.ratio")
    @Mapping(target = "status", source = "progress.status")
    @Mapping(target = "periodType", source = "budget.periodType")
    @Mapping(target = "startDate", source = "budget.startDate")
    @Mapping(target = "endDate", source = "budget.endDate")
    @Mapping(target = "daysRemaining", source = "progress.daysRemaining")
    @Mapping(target = "autoRenew", source = "budget.autoRenew")
    @Mapping(target = "isActive", source = "budget.isActive")
    BudgetRes toRes(Budget budget, BudgetProgress progress, CategoryRes category);
}
