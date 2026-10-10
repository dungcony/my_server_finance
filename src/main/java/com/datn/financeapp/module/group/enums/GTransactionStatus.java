package com.datn.financeapp.module.group.enums;

public enum GTransactionStatus {
    PENDING,              // chờ thủ quỹ/chủ nhóm duyệt
    AWAITING_TRANSACTOR,  // chờ người chi trả/nhận tiền (transactor) đồng ý, do thủ quỹ/chủ nhóm ghi hộ
    CONFIRMED,            // đã xác nhận
    REJECTED;             // bị từ chối

    /**
     * @return {@code true} nếu giao dịch còn chờ người xử lý (thủ quỹ/chủ nhóm hoặc người chi trả), chưa tính vào quỹ
     */
    public boolean isWaiting() {
        return this == PENDING || this == AWAITING_TRANSACTOR;
    }
}
