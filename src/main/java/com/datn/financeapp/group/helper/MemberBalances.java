package com.datn.financeapp.group.helper;

import java.util.Map;
import java.util.UUID;

/**
 * Kết quả tổng hợp balance của tất cả thành viên trong nhóm.
 * Bọc Map các đối tượng {@link MemberBalanceAccumulator}, cung cấp các phương thức truy xuất số liệu an toàn.
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #get(UUID)}: Lấy bộ tích lũy. Input: userId. Output: accumulator.</li>
 *   <li>Các hàm {@code get...} (PaidOutOfPocket, Refunded, Share, NetBalance): Lấy chỉ số tương ứng. Input: userId. Output: số tiền (long).</li>
 *   <li>{@link #totalNet()}: Tổng số dư ròng cả nhóm. Input: không. Output: tổng số tiền (long).</li>
 * </ul>
 * </p>
 *
 * @param members Map lưu trữ bộ tích lũy số dư theo ID của từng thành viên

 */
public record MemberBalances(Map<UUID, MemberBalanceAccumulator> members) {

    /**
     * Lấy bộ tích lũy số dư của thành viên.
     *
     * @param userId ID của thành viên
     * @return Đối tượng {@link MemberBalanceAccumulator} tương ứng, không bao giờ null
     */
    public MemberBalanceAccumulator get(UUID userId) {
        MemberBalanceAccumulator acc = members.get(userId);
        return acc != null ? acc : new MemberBalanceAccumulator();
    }

    /**
     * Lấy tổng số tiền thành viên đã tự bỏ tiền túi chi trả cho nhóm.
     *
     * @param userId ID của thành viên
     * @return Tổng số tiền đã chi trả tiền túi
     */
    public long getPaidOutOfPocket(UUID userId) {
        return get(userId).getPaidOutOfPocket();
    }

    /**
     * Lấy tổng số tiền thành viên đã được quỹ nhóm hoàn trả.
     *
     * @param userId ID của thành viên
     * @return Tổng số tiền đã được hoàn trả
     */
    public long getRefunded(UUID userId) {
        return get(userId).getRefunded();
    }

    /**
     * Lấy tổng số tiền thành viên phải gánh chịu trong các chi phí chung của nhóm.
     *
     * @param userId ID của thành viên
     * @return Tổng số tiền share phải chịu
     */
    public long getShare(UUID userId) {
        return get(userId).getShare();
    }

    /**
     * Lấy số dư ròng (Net Balance) của thành viên.
     *
     * @param userId ID của thành viên
     * @return Số dư ròng của thành viên
     */
    public long getNetBalance(UUID userId) {
        return get(userId).getNetBalance();
    }

    /**
     * Tính tổng số dư ròng của tất cả thành viên.
     *
     * @return Tổng số dư ròng của toàn bộ thành viên
     */
    public long totalNet() {
        return members.values().stream()
                .mapToLong(MemberBalanceAccumulator::getNetBalance)
                .sum();
    }
}
