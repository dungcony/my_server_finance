package com.datn.financeapp.group.helper;

import lombok.Getter;

/**
 * Đối tượng đóng gói và tích lũy 4 chỉ số tài chính cốt lõi cho từng thành viên trong nhóm:
 * <ul>
 *     <li>{@code rawContribution}: Tổng số tiền đã đóng góp vào quỹ chung</li>
 *     <li>{@code paidOutOfPocket}: Tổng số tiền tự bỏ tiền túi chi trả cho nhóm</li>
 *     <li>{@code refunded}: Tổng số tiền được quỹ nhóm trả lại (hoàn tiền túi hoặc trả lại tiền đã góp)</li>
 *     <li>{@code share}: Tổng số tiền phải gánh/chịu trong các khoản chi tiêu</li>
 * </ul>
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>Các hàm {@code add...} (Contribution, PaidOutOfPocket, Refund, Share): Cộng dồn chỉ số. Input: số tiền. Output: void.</li>
 *   <li>{@link #getNetBalance()}: Tính số dư ròng, tức số tiền người đó còn trong quỹ. Input: không. Output: số dư ròng.</li>
 *   <li>{@link #isZero()}: Kiểm tra cả 4 chỉ số đều bằng 0. Input: không. Output: boolean.</li>
 * </ul>
 * </p>
 */
@Getter
public class MemberBalanceAccumulator {

    /**
     * Tổng số tiền thành viên đã nộp/đóng góp vào quỹ chung của nhóm
     * (tích lũy từ các giao dịch {@code CONTRIBUTION} với nguồn tiền {@code PERSONAL} hoặc không xác định/null).
     */
    private long rawContribution = 0L;

    /**
     * Tổng số tiền thành viên đã tự bỏ tiền túi chi trả hộ cho nhóm
     * (tích lũy từ các giao dịch chi tiêu {@code EXPENSE} với nguồn tiền {@code PERSONAL}).
     */
    private long paidOutOfPocket = 0L;

    /**
     * Tổng số tiền thành viên đã được quỹ nhóm trả lại
     * (tích lũy từ các giao dịch {@code REFUND}, ví dụ: hoàn trả khoản chi hộ hoặc trả lại tiền đã góp).
     */
    private long refunded = 0L;

    /**
     * Tổng số tiền thành viên phải chịu/gánh trong các khoản chi tiêu chung của nhóm
     * (tính từ phân bổ chi phí theo danh sách người tham gia {@code participants} hoặc chia đều).
     */
    private long share = 0L;

    /**
     * Cộng dồn số tiền thành viên nộp/đóng góp vào quỹ.
     *
     * @param amount Số tiền đóng góp
     */
    public void addContribution(long amount) {
        this.rawContribution += amount;
    }

    /**
     * Cộng dồn số tiền thành viên tự bỏ tiền túi chi trả hộ cho nhóm.
     *
     * @param amount Số tiền chi trả hộ
     */
    public void addPaidOutOfPocket(long amount) {
        this.paidOutOfPocket += amount;
    }

    /**
     * Cộng dồn số tiền thành viên được quỹ trả lại.
     *
     * @param amount Số tiền trả lại
     */
    public void addRefund(long amount) {
        this.refunded += amount;
    }

    /**
     * Cộng dồn (hoặc điều chỉnh giảm) phần chi phí thành viên phải gánh chịu.
     *
     * @param amount Số tiền share (dương đối với chi phí tăng, âm đối với điều chỉnh giảm chi phí)
     */
    public void addShare(long amount) {
        this.share += amount;
    }

    /**
     * Tính số dư ròng (Net Balance) của thành viên, tức số tiền người đó còn trong quỹ.
     *
     * @return Số dư ròng (Net &gt; 0: Nhóm nợ thành viên, Net &lt; 0: Thành viên nợ nhóm)
     */
    public long getNetBalance() {
        return rawContribution + paidOutOfPocket - refunded - share;
    }

    /**
     * Kiểm tra cả 4 chỉ số đều bằng 0, dùng để bỏ qua thành viên không có chênh lệch khi ghi bảng tổng hợp.
     *
     * @return {@code true} nếu không có chỉ số nào khác 0
     */
    public boolean isZero() {
        return rawContribution == 0L && paidOutOfPocket == 0L && refunded == 0L && share == 0L;
    }
}
