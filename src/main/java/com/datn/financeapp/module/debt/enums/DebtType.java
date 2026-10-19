package com.datn.financeapp.module.debt.enums;

public enum DebtType {
    OUTSTANDING, // đang nợ, chưa thanh toán hết
    SETTLET, // đã trả xong
    WRITTEN_OFF // xoá nợ, không đòi được nữa
}
