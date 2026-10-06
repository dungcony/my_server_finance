package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.entity.MemberBalance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li>{@link #from(List)}: Chuyển các dòng bảng tổng hợp sang bộ tích luỹ. Input: danh sách dòng. Output: MemberBalances.</li>
 *   <li>{@link #minus(MemberBalances)}: Chênh lệch từng thành viên giữa hai lần tổng hợp. Input: số dư cần trừ. Output: MemberBalances.</li>
 *   <li>{@link #plus(MemberBalances)}: Cộng dồn hai lần tổng hợp. Input: số dư cần cộng. Output: MemberBalances.</li>
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

    /**
     * Chuyển các dòng của bảng tổng hợp sang bộ tích luỹ. Sao chép số liệu sang đối tượng mới,
     * nên sửa kết quả trả về không chạm tới entity đang được Hibernate quản lý.
     *
     * @param rows Các dòng {@code group_member_balances} của nhóm
     * @return Số dư theo từng thành viên
     */
    public static MemberBalances from(List<MemberBalance> rows) {
        Map<UUID, MemberBalanceAccumulator> map = new HashMap<>();
        for (MemberBalance row : rows) {
            MemberBalanceAccumulator acc = new MemberBalanceAccumulator();
            acc.addPaidOutOfPocket(row.getPaidOutOfPocket());
            acc.addContribution(row.getContribution());
            acc.addRefund(row.getRefund());
            acc.addShare(row.getShare());
            map.put(row.getUserId(), acc);
        }
        return new MemberBalances(map);
    }

    /**
     * Tính chênh lệch từng thành viên giữa hai lần tổng hợp ({@code this - other}).
     *
     * @param other Số dư cần trừ đi
     * @return Chênh lệch theo từng thành viên có mặt ở một trong hai bên
     */
    public MemberBalances minus(MemberBalances other) {
        return combine(other, -1L);
    }

    /**
     * Cộng dồn số dư của hai lần tổng hợp ({@code this + other}), dùng khi gộp ảnh hưởng của nhiều giao dịch.
     *
     * @param other Số dư cần cộng thêm
     * @return Tổng theo từng thành viên có mặt ở một trong hai bên
     */
    public MemberBalances plus(MemberBalances other) {
        return combine(other, 1L);
    }

    private MemberBalances combine(MemberBalances other, long sign) {
        Map<UUID, MemberBalanceAccumulator> map = new HashMap<>();
        Set<UUID> userIds = new HashSet<>(members.keySet());
        userIds.addAll(other.members().keySet());
        for (UUID userId : userIds) {
            MemberBalanceAccumulator mine = get(userId);
            MemberBalanceAccumulator theirs = other.get(userId);
            MemberBalanceAccumulator result = new MemberBalanceAccumulator();
            result.addPaidOutOfPocket(mine.getPaidOutOfPocket() + sign * theirs.getPaidOutOfPocket());
            result.addContribution(mine.getRawContribution() + sign * theirs.getRawContribution());
            result.addRefund(mine.getRefunded() + sign * theirs.getRefunded());
            result.addShare(mine.getShare() + sign * theirs.getShare());
            map.put(userId, result);
        }
        return new MemberBalances(map);
    }
}
