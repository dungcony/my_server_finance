package com.datn.financeapp.group.enums;

public enum TransactionType {
    EXPENSE,          // nhóm tiêu tiền
    CONTRIBUTION,     // thành viên góp tiền vào quỹ
    REFUND,           // quỹ trả tiền cho thành viên (hoàn tiền túi hoặc trả lại tiền đã góp)
    ADJUSTMENT_UP,    // kiểm kê: tiền thật nhiều hơn sổ
    ADJUSTMENT_DOWN   // kiểm kê: tiền thật ít hơn sổ
}
