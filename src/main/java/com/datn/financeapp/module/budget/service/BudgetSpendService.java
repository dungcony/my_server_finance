package com.datn.financeapp.module.budget.service;

import com.datn.financeapp.module.budget.dto.response.BudgetAffectedRes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service quản lý việc cộng/trừ số tiền đã chi (spent) của các ngân sách bị ảnh hưởng
 * khi phát sinh, sửa đổi hoặc xóa giao dịch chi tiêu.
 */
public interface BudgetSpendService {

    List<BudgetAffectedRes> addExpense(UUID userId, String categoryCode, Instant occurredAt, long amount);

    List<BudgetAffectedRes> addExpense(UUID userId, String categoryCode, LocalDate date, long amount);

    void removeExpense(UUID userId, String categoryCode, Instant occurredAt, long amount);

    void removeExpense(UUID userId, String categoryCode, LocalDate date, long amount);
}
