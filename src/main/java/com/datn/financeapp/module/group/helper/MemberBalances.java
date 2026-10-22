package com.datn.financeapp.module.group.helper;

import com.datn.financeapp.module.group.entity.MemberBalance;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Số dư của nhiều thành viên trong nhóm, tính trong bộ nhớ và không gắn với DB.
 * Dùng cho cả số dư tích luỹ đọc từ bảng {@code group_member_balances} lẫn ảnh hưởng của một giao dịch lên số dư.
 */
public final class MemberBalances {
    /**
     * lưu số dư của thành viên theo map để dễ truy vấn
     */
    private final Map<UUID, Balance> members = new HashMap<>();

    // ------------------------ WRITE METHOD -------------------//

    /**
     * Cộng tiền thành viên đã góp/ứng cho nhóm (nộp quỹ hoặc dùng tiền túi chi hộ).
     *
     * @param userId ID của thành viên
     * @param amount Số tiền cần cộng
     */
    public void addContribution(UUID userId, long amount) {
        add(userId, new Balance(amount, 0, 0));
    }

    /**
     * Cộng tiền thành viên đã được quỹ nhóm trả lại.
     *
     * @param userId ID của thành viên
     * @param amount Số tiền cần cộng
     */
    public void addRefund(UUID userId, long amount) {
        add(userId, new Balance(0, amount, 0));
    }

    /**
     * Cộng phần thành viên phải gánh chịu trong các khoản chi tiêu chung.
     *
     * @param userId ID của thành viên
     * @param amount Số tiền cần cộng (dương làm tăng phần phải chịu, âm làm giảm)
     */
    public void addShare(UUID userId, long amount) {
        add(userId, new Balance(0, 0, amount));
    }

    //-------------------------- READ METHOD ----------------------//

    /**
     * Lấy số dư của thành viên.
     *
     * @param userId ID của thành viên
     * @return {@link Balance} của thành viên, bằng 0 ở cả ba chỉ số nếu chưa có, không bao giờ null
     */
    public Balance get(UUID userId) {
        return members.getOrDefault(userId, new Balance(0, 0, 0));
    }

    /**
     * Xem toàn bộ số dư theo từng thành viên.
     *
     * @return Map chỉ đọc (userId sang {@link Balance}), sửa sẽ ném {@link UnsupportedOperationException}
     */
    public Map<UUID, Balance> members() {
        // bản chỉ đọc, không cho phép thao tác sửa đổi
        return Collections.unmodifiableMap(members);
    }

    /**
     * Tính tổng số dư ròng của tất cả thành viên.
     *
     * @return Tổng {@link Balance#net()} của toàn bộ thành viên
     */
    public long totalNet() {
        return members.values().stream().mapToLong(Balance::net).sum();
    }

    //------------------------- Khởi tạo ---------------------------//

    /**
     * Chuyển các dòng của bảng tổng hợp sang số dư trong bộ nhớ. Sao chép số liệu sang đối tượng mới,
     * nên không chạm tới entity đang được Hibernate quản lý.
     *
     * @param rows Các dòng {@code group_member_balances} của nhóm
     * @return Số dư theo từng thành viên
     */
    public static MemberBalances from(List<MemberBalance> rows) {
        MemberBalances result = new MemberBalances();
        for (MemberBalance r : rows) {
            result.members.put(r.getUserId(),
                    new Balance(r.getContribution(), r.getRefund(), r.getShare()));
        }
        return result;
    }

    /**
     * Cộng dồn số dư của hai lần tổng hợp ({@code this + other}), dùng khi gộp ảnh hưởng của nhiều giao dịch.
     *
     * @param other Số dư cần cộng thêm
     * @return Đối tượng mới là tổng theo từng thành viên có mặt ở một trong hai bên, hai đối tượng gốc không đổi
     */
    public MemberBalances plus(MemberBalances other) {
        MemberBalances result = copy();
        other.members.forEach(result::add);
        return result;
    }

    /**
     * Tính chênh lệch từng thành viên giữa hai lần tổng hợp ({@code this - other}).
     *
     * @param other Số dư cần trừ đi
     * @return Đối tượng mới là chênh lệch theo từng thành viên có mặt ở một trong hai bên, hai đối tượng gốc không đổi
     */
    public MemberBalances minus(MemberBalances other) {
        MemberBalances result = copy();
        other.members.forEach((userId, b) -> result.add(userId, b.negate()));
        return result;
    }

    //-----------------------------PRIVATE METHOD-----------------------------//

    /**
     * Cộng một khoản vào số dư của thành viên, chưa có thì tạo mới.
     */
    private void add(UUID userId, Balance delta) {
        members.merge(userId, delta, Balance::plus);
    }

    /**
     * Tạo bản sao độc lập (Balance bất biến nên chỉ cần sao chép Map).
     */
    private MemberBalances copy() {
        MemberBalances c = new MemberBalances();
        c.members.putAll(members);
        return c;
    }

    //------------------------------------------------------INNER CLASS----------------------------------------------------//

    /**
     * Số dư 3 chỉ số của một thành viên, bất biến.
     *
     * @param contribution Tiền đã góp quỹ (dùng tiền túi để chi hộ cũng tính là góp quỹ)
     * @param refund       Tiền đã được quỹ trả lại
     * @param share        Tiền phải gánh chịu trong các khoản chi tiêu
     */
    public record Balance(
            long contribution,
            long refund,
            long share
    ) {
        /**
         * Tính số dư ròng của thành viên, tức số tiền người đó còn trong quỹ.
         *
         * @return {@code contribution - refund - share}; dương là nhóm nợ thành viên, âm là thành viên nợ nhóm
         */
        public long net() {
            return contribution - refund - share;
        }

        /**
         * Kiểm tra cả ba chỉ số đều bằng 0, dùng để bỏ qua thành viên không có chênh lệch khi ghi bảng tổng hợp.
         *
         * @return {@code true} nếu không có chỉ số nào khác 0
         */
        public boolean isZero() {
            return contribution == 0 && refund == 0 && share == 0;
        }

        /**
         * Cộng từng chỉ số với số dư khác.
         *
         * @param o Số dư cần cộng
         * @return Số dư mới là tổng hai bên
         */
        Balance plus(Balance o) {
            return new Balance(
                    contribution + o.contribution,
                    refund + o.refund,
                    share + o.share
            );
        }

        /**
         * Đảo dấu cả ba chỉ số, dùng để thực hiện phép trừ bằng phép cộng.
         *
         * @return Số dư mới có các chỉ số đối dấu
         */
        Balance negate() {
            return new Balance(-contribution, -refund, -share);
        }
    }

}
