package com.datn.financeapp.goal.dto;

/** Body phản hồi 201 của {@code POST /goals} (api/09 mục B2). */
public record CreateGoalResponse(GoalListItemResponse goal) {}
