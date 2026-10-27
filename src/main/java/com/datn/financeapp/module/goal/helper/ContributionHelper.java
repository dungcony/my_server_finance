package com.datn.financeapp.module.goal.helper;

import com.datn.financeapp.module.goal.entity.GoalContribution;
import com.datn.financeapp.module.goal.repository.ContributionGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContributionHelper {

    private final ContributionGoalRepository contributionGoalRepository;

    @Transactional
    public void initContribution(UUID savingGoalId, Long amount) {
        if (amount == null || amount <= 0)
            return;

        GoalContribution initContrib = GoalContribution.builder()
                .id(UUID.randomUUID())
                .goalId(savingGoalId)
                .amount(amount)
                .contributedDate(LocalDate.now())
                .note("Khoản tích lũy ban đầu")
                .build();

        contributionGoalRepository.save(initContrib);
    }

    @Transactional
    public void removeForSavingGoal(UUID savingGoalId) {
        contributionGoalRepository.removeForSavingGoal(savingGoalId);
    }
}
