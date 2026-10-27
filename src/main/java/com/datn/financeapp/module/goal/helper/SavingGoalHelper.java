package com.datn.financeapp.module.goal.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.goal.entity.SavingGoal;
import com.datn.financeapp.module.goal.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SavingGoalHelper {

    private final SavingsGoalRepository savingsGoalRepository;

    public SavingGoal findSavingGoal(UUID goalId, boolean isAdmin) {
        if (isAdmin) {
            return savingsGoalRepository.findByIdAndIsDeletedFalse(goalId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));
        }
        return savingsGoalRepository.findByIdAndUserIdAndIsDeletedFalse(goalId, SecurityContextUtil.currentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));
    }
}
