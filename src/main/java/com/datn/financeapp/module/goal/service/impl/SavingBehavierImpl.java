package com.datn.financeapp.module.goal.service.impl;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.goal.dto.request.SavingGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalFilterReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalUpdateRequest;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalRes;
import com.datn.financeapp.module.goal.entity.SavingGoal;
import com.datn.financeapp.module.goal.enums.SavingGoalStatus;
import com.datn.financeapp.module.goal.helper.ContributionHelper;
import com.datn.financeapp.module.goal.helper.GoalSpecs;
import com.datn.financeapp.module.goal.helper.SavingGoalHelper;
import com.datn.financeapp.module.goal.mapper.SavingGoalMapper;
import com.datn.financeapp.module.goal.repository.SavingsGoalRepository;
import com.datn.financeapp.module.goal.service.SavingBehavierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cài đặt nghiệp vụ quản lý vòng đời mục tiêu tiết kiệm.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavingBehavierImpl implements SavingBehavierService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final ContributionHelper contributionHelper;
    private final SavingGoalHelper savingGoalHelper;
    private final SavingGoalMapper savingGoalMapper;


    @Transactional
    @Override
    public SavingGoalRes create(UUID userId, SavingGoalCreateReq req) {

        // amount và status đã có mặc định
        SavingGoal goal = savingGoalMapper.toSavingGoal(req);
        goal.setId(UUID.randomUUID());
        goal.setUserId(userId);

        goal = savingsGoalRepository.save(goal);
        contributionHelper.initContribution(goal.getId(), goal.getSavedAmount());

        return savingGoalMapper.toSavingGoalRes(goal);
    }

    @Override
    @Transactional(readOnly = true)
    public SavingGoalRes detail(UUID goalId, boolean isAdmin) {

        SavingGoal goal = savingGoalHelper.findSavingGoal(goalId, isAdmin);

        return savingGoalMapper.toSavingGoalRes(goal);
    }

    @Override
    @Transactional(readOnly = true)
    public SavingGoalListRes gets(UUID userId, SavingGoalFilterReq filter, Pageable pageable) {
        Page<SavingGoal> page = savingsGoalRepository.findAll(GoalSpecs.filter(userId, filter), pageable);
        return savingGoalMapper.toListRes(page.getContent(), PageMeta.from(page));
    }


    @Override
    @Transactional
    public SavingGoalRes update(UUID goalId, SavingGoalUpdateRequest req, boolean isAdmin) {

        SavingGoal goal = savingGoalHelper.findSavingGoal(goalId, isAdmin);

        if (req.name() != null && !req.name().isBlank()) goal.setName(req.name());
        if (req.targetAmount() != null) goal.setTargetAmount(req.targetAmount());
        if (req.targetDate() != null) goal.setTargetDate(req.targetDate());
        if (req.iconCode() != null) goal.setIconCode(req.iconCode());
        if (req.note() != null) goal.setNote(req.note());

        if (req.status() != null && (req.status() == SavingGoalStatus.IN_PROGRESS || req.status() == SavingGoalStatus.CANCELLED))
            goal.setStatus(req.status());

        goal = savingsGoalRepository.save(goal);
        return savingGoalMapper.toSavingGoalRes(goal);
    }

    @Override
    @Transactional
    public void delete(UUID goalId, boolean isAdmin) {
        SavingGoal goal = savingGoalHelper.findSavingGoal(goalId, isAdmin);

        goal.setIsDeleted(true);
        savingsGoalRepository.save(goal);

        contributionHelper.removeForSavingGoal(goalId);
    }


}
