package com.datn.financeapp.goal.dto.response;

// Body phản hồi 201 của {@code POST /goals} (api/09 mục B2).
public record CreateGoalResponse(GoalListItemResponse goal) {}
