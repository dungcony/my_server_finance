package com.datn.financeapp.transaction.dto;

/**
 * Tổng thu/chi trên TOÀN BỘ kết quả lọc (không chỉ trang hiện tại) — api/04-GIAO-DICH.md mục 1.
 * LOẠI TRỪ {@code type = 'transfer'} (CLAUDE.md §2 bất biến): transfer không phải thu, không
 * phải chi.
 */
public record TransactionSummaryDto(Long totalIncome, Long totalExpense, Long difference) {

    public static TransactionSummaryDto of(Long totalIncome, Long totalExpense) {
        long income = totalIncome == null ? 0L : totalIncome;
        long expense = totalExpense == null ? 0L : totalExpense;
        return new TransactionSummaryDto(income, expense, income - expense);
    }
}
