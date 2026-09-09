package com.datn.financeapp.goal.service;

import com.datn.financeapp.goal.dto.request.CreateContributionRequest;
import com.datn.financeapp.goal.dto.request.CreateGoalRequest;
import com.datn.financeapp.goal.dto.request.UpdateGoalRequest;
import com.datn.financeapp.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.goal.dto.response.GoalDetailResponse;
import com.datn.financeapp.goal.dto.response.GoalListItemResponse;
import java.util.List;
import java.util.UUID;

// Public API của module Goal (Quản lý mục tiêu tiết kiệm và đóng góp).
public interface GoalService {

    CreateGoalResponse create(UUID userId, CreateGoalRequest req);

    CreateContributionResponse addContribution(UUID userId, UUID goalId, CreateContributionRequest req);

    void cancelContribution(UUID userId, UUID goalId, UUID contributionId);

    GoalListItemResponse update(UUID userId, UUID goalId, UpdateGoalRequest req);

    void delete(UUID userId, UUID goalId, boolean revertTransactions);

    List<GoalListItemResponse> list(UUID userId, String status);

    GoalDetailResponse detail(UUID userId, UUID goalId);
}
