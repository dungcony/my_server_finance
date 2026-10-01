package com.datn.financeapp.group.dto.response.group;

// số việc đang chờ người gọi xử lý, dùng để hiện badge trên giao diện
public record GroupPendingCountRes(
        long pendingTransactions,
        long pendingMembers) {
}
