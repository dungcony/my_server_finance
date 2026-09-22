package com.datn.financeapp.group.helper;

import java.util.Map;
import java.util.UUID;

/**
 * Kết quả tổng hợp balance của tất cả thành viên trong nhóm.
 * Bọc Map các đối tượng {@link MemberBalanceAccumulator}, cung cấp các phương thức truy xuất số liệu an toàn.
 */
public record MemberBalances(Map<UUID, MemberBalanceAccumulator> members) {

    private static final MemberBalanceAccumulator EMPTY = new MemberBalanceAccumulator();

    public MemberBalanceAccumulator get(UUID userId) {
        return members.getOrDefault(userId, EMPTY);
    }

    public long getRemainingContribution(UUID userId) {
        return get(userId).getRemainingContribution();
    }

    public long getPaidOutOfPocket(UUID userId) {
        return get(userId).getPaidOutOfPocket();
    }

    public long getRefunded(UUID userId) {
        return get(userId).getRefunded();
    }

    public long getWithdrawn(UUID userId) {
        return get(userId).getWithdrawn();
    }

    public long getShare(UUID userId) {
        return get(userId).getShare();
    }

    public long getNetBalance(UUID userId) {
        return get(userId).getNetBalance();
    }

    /**
     * Tổng Net Balance của tất cả thành viên.
     * Phục vụ kiểm tra phương trình bảo toàn quỹ: sum(NetBalance) == Fund Balance.
     */
    public long totalNet() {
        return members.values().stream()
                .mapToLong(MemberBalanceAccumulator::getNetBalance)
                .sum();
    }
}
