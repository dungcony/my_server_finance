package com.datn.financeapp.module.goal.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalFilterReq;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalUpdateReq;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalListRes;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import com.datn.financeapp.module.goal.entity.GoalContribution;
import com.datn.financeapp.module.goal.entity.SavingGoal;
import com.datn.financeapp.module.goal.enums.SavingGoalStatus;
import com.datn.financeapp.module.goal.event.GoalCompletedEvent;
import com.datn.financeapp.module.goal.helper.ContributionGoalSpecs;
import com.datn.financeapp.module.goal.helper.SavingGoalHelper;
import com.datn.financeapp.module.goal.mapper.ContributionGoalMapper;
import com.datn.financeapp.module.goal.repository.ContributionGoalRepository;
import com.datn.financeapp.module.goal.service.ContributionBehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cài đặt nghiệp vụ nạp tiền, rút tiền và điều chỉnh đợt đóng góp tiết kiệm.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContributionBehaviorImpl implements ContributionBehaviorService {

    private final ContributionGoalRepository contributionGoalRepository;
    private final ContributionGoalMapper contributionGoalMapper;
    private final SavingGoalHelper savingGoalHelper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public ContributionGoalRes addContribution(UUID goalId, ContributionGoalCreateReq req, boolean isAdmin) {
        SavingGoal goal = savingGoalHelper.findSavingGoal(goalId, isAdmin);

        if (goal.getStatus() != SavingGoalStatus.IN_PROGRESS)
            throw new BusinessException(ErrorCode.CONFLICT, "Không thể đóng góp vào mục tiêu đã hoàn thành hoặc huỷ");

        GoalContribution gc = contributionGoalMapper.toGoalContribution(goalId, req);

        // Chặn nếu là lệnh rút tiền mà số tiền rút vượt quá số tiền đã tích lũy
        if (gc.getAmount() < 0 && goal.getSavedAmount() + gc.getAmount() < 0) {
            throw new BusinessException(
                    ErrorCode.GOAL_WITHDRAW_EXCEEDS_SAVED,
                    "Số tiền rút không được vượt quá số tiền đã tích lũy hiện tại."
            );
        }

        gc = contributionGoalRepository.save(gc);

        long newSaveAmount = goal.getSavedAmount() + gc.getAmount();
        if (newSaveAmount >= goal.getTargetAmount()) {
            applicationEventPublisher.publishEvent(new GoalCompletedEvent(
                    goal.getUserId(),
                    goal.getId(),
                    goal.getName(),
                    goal.getTargetAmount()
            ));
        }

        return contributionGoalMapper.toContributionRes(gc);
    }

    @Override
    public ContributionGoalListRes gets(UUID goalId, ContributionGoalFilterReq filter, Pageable pageable, boolean isAdmin) {

        SavingGoal goal = savingGoalHelper.findSavingGoal(goalId, isAdmin);


        Page<GoalContribution> page = contributionGoalRepository.findAll(ContributionGoalSpecs.filter(goalId, filter), pageable);

        return contributionGoalMapper.toContributionListRes(
                page.getContent(),
                PageMeta.from(page)
        );

    }


    @Override
    @Transactional
    public ContributionGoalRes updateContribution(UUID goalId, UUID contributionId, ContributionGoalUpdateReq req, boolean isAdmin) {
        SavingGoal goal = savingGoalHelper.findSavingGoal(goalId, isAdmin);

        if (goal.getStatus() != SavingGoalStatus.IN_PROGRESS)
            throw new BusinessException(ErrorCode.CONFLICT, "Không thể chỉnh sửa đóng góp của mục tiêu đã hủy hoặc đã hoàn thành");

        GoalContribution contribution = contributionGoalRepository.findByIdAndGoalIdAndIsDeletedFalse(contributionId, goal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GOAL_CONTRIBUTION_NOT_FOUND, "Không tìm thấy lượt đóng góp."));

        if (req.amount() != null) updateAmount(goal, contribution, req.amount());
        if (req.contributedDate() != null) contribution.setContributedDate(req.contributedDate());
        if (req.note() != null) contribution.setNote(req.note());

        contribution = contributionGoalRepository.save(contribution);
        return contributionGoalMapper.toContributionRes(contribution);
    }

    @Transactional
    @Override
    public void deleteContribution(UUID goalId, UUID contributionId, boolean isAdmin) {
        SavingGoal goal = savingGoalHelper.findSavingGoal(goalId, isAdmin);

        if (goal.getStatus() != SavingGoalStatus.IN_PROGRESS)
            throw new BusinessException(ErrorCode.CONFLICT, "Không thể chỉnh sửa đóng góp của mục tiêu đã hủy hoặc đã hoàn thành");

        GoalContribution contribution = contributionGoalRepository.findByIdAndGoalIdAndIsDeletedFalse(contributionId, goal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GOAL_CONTRIBUTION_NOT_FOUND, "Không tìm thấy lượt đóng góp."));

        if (goal.getSavedAmount() - contribution.getAmount() < 0) {
            throw new BusinessException(
                    ErrorCode.GOAL_CONTRIBUTION_NEGATIVE_BALANCE,
                    "Không thể xóa khoản đóng góp vì số tiền tích lũy còn lại sẽ bị âm."
            );
        }

        contribution.setIsDeleted(true);
        contributionGoalRepository.save(contribution);
    }


    //----------------------------------- PRIVATE ---------------------------//

    /**
     * Hủy bỏ một lượt đóng góp (xóa mềm khi ghi nhận nhầm).
     */
    private void updateAmount(SavingGoal goal, GoalContribution contribution, long newAmount) {

        // Giữ đúng dấu âm nếu đây là khoản rút tiền
        boolean isWithdrawal = contribution.getAmount() < 0;
        long effectiveNewAmount = isWithdrawal ? -newAmount : newAmount;
        long newSavedAmount = goal.getSavedAmount() - contribution.getAmount() + effectiveNewAmount;

        if (newSavedAmount < 0) {
            throw new BusinessException(
                    ErrorCode.GOAL_CONTRIBUTION_NEGATIVE_BALANCE,
                    "Không thể cập nhật khoản đóng góp vì số tiền tích lũy còn lại sẽ bị âm."
            );
        }

        // Chỉ bắn sự kiện khi mục tiêu trước đó chưa hoàn thành và vừa mới cán đích
        if (goal.getStatus() != SavingGoalStatus.COMPLETED && newSavedAmount >= goal.getTargetAmount()) {
            applicationEventPublisher.publishEvent(new GoalCompletedEvent(
                    goal.getUserId(),
                    goal.getId(),
                    goal.getName(),
                    goal.getTargetAmount()
            ));
        }

        contribution.setAmount(effectiveNewAmount);

    }
}
