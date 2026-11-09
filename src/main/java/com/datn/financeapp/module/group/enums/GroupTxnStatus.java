package com.datn.financeapp.module.group.enums;

public enum GroupTxnStatus {
    PENDING,    // chờ thủ quỹ/chủ nhóm duyệt
    CONFIRMED,  // đã xác nhận
    REJECTED;   // bị từ chối

    /**
     * @return {@code true} nếu giao dịch còn chờ thủ quỹ/chủ nhóm duyệt, chưa tính vào quỹ
     */
    public boolean isWaiting() {
        return this == PENDING;
    }
}
