package com.datn.financeapp.group.enums;

public enum GroupTransactionType {
    EXPENSE,          // nhóm tiêu tiền
    CONTRIBUTION,     // thành viên góp tiền vào quỹ
    REFUND,           // quỹ hoàn tiền túi cho người đã trả hộ
    WITHDRAWAL,       // thành viên rút lại tiền đã góp
    ADJUSTMENT_UP,    // kiểm kê: tiền thật nhiều hơn sổ
    ADJUSTMENT_DOWN   // kiểm kê: tiền thật ít hơn sổ
}
