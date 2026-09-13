package com.datn.financeapp.debt.service;

import com.datn.financeapp.debt.dto.request.CreateDebtRequest;
import com.datn.financeapp.debt.dto.request.CreatePaymentRequest;
import com.datn.financeapp.debt.dto.request.UpdateDebtRequest;
import com.datn.financeapp.debt.dto.request.WriteOffRequest;
import com.datn.financeapp.debt.dto.response.CreateDebtResponse;
import com.datn.financeapp.debt.dto.response.CreatePaymentResponse;
import com.datn.financeapp.debt.dto.response.DebtDetailResponse;
import com.datn.financeapp.debt.dto.response.DebtListItemResponse;
import com.datn.financeapp.debt.dto.response.DebtSummaryResponse;
import java.util.List;
import java.util.UUID;

// Public API của module Debt (Quản lý sổ nợ, các đợt thanh toán và nhắc nợ).
public interface DebtService {

    CreateDebtResponse create(UUID userId, CreateDebtRequest req);

    CreatePaymentResponse addPayment(UUID userId, UUID debtId, CreatePaymentRequest req);

    void cancelPayment(UUID userId, UUID debtId, UUID paymentId);

    DebtListItemResponse writeOff(UUID userId, UUID debtId, WriteOffRequest req);

    DebtListItemResponse update(UUID userId, UUID debtId, UpdateDebtRequest req);

    void delete(UUID userId, UUID debtId);

    List<DebtListItemResponse> list(UUID userId, String type, String status, Boolean isOverdue);

    DebtDetailResponse detail(UUID userId, UUID debtId);

    DebtSummaryResponse summary(UUID userId);

    void sendDueReminders();
}
