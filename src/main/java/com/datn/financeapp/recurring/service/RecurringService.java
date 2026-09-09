package com.datn.financeapp.recurring.service;

import com.datn.financeapp.recurring.dto.request.CreateRecurringRequest;
import com.datn.financeapp.recurring.dto.request.PauseRecurringRequest;
import com.datn.financeapp.recurring.dto.request.UpdateRecurringRequest;
import com.datn.financeapp.recurring.dto.response.RecurringDetailResponse;
import com.datn.financeapp.recurring.dto.response.RecurringListItemResponse;
import com.datn.financeapp.recurring.dto.response.RunNowResponse;
import java.util.List;
import java.util.UUID;

// Public API của module Recurring (Quản lý các giao dịch định kỳ).
public interface RecurringService {

    List<RecurringListItemResponse> list(UUID userId, Boolean isEnabled, String type);

    RecurringDetailResponse detail(UUID userId, UUID id);

    RecurringListItemResponse create(UUID userId, CreateRecurringRequest req);

    RecurringListItemResponse update(UUID userId, UUID id, UpdateRecurringRequest req);

    void delete(UUID userId, UUID id);

    RecurringListItemResponse pause(UUID userId, UUID id, PauseRecurringRequest req);

    RunNowResponse runNow(UUID userId, UUID id);
}
