package com.datn.financeapp.group.helper;

import lombok.Getter;

/**
 * Đối tượng đóng gói và tích lũy 5 chỉ số tài chính cốt lõi cho từng thành viên trong nhóm.
 */
@Getter
public class MemberBalanceAccumulator {

    private long rawContribution = 0L;
    private long paidOutOfPocket = 0L;
    private long refunded = 0L;
    private long withdrawn = 0L;
    private long share = 0L;

    public void addContribution(long amount) {
        this.rawContribution += amount;
    }

    public void addPaidOutOfPocket(long amount) {
        this.paidOutOfPocket += amount;
    }

    public void addRefund(long amount) {
        this.refunded += amount;
    }

    public void addWithdrawal(long amount) {
        this.withdrawn += amount;
    }

    public void addShare(long amount) {
        this.share += amount;
    }

    /**
     * Số tiền đóng góp còn lại trong quỹ (= tổng CONTRIBUTION - tổng WITHDRAWAL)
     */
    public long getRemainingContribution() {
        return rawContribution - withdrawn;
    }

    /**
     * Số dư ròng bất biến: Net = rawContribution + paidOutOfPocket - refunded - withdrawn - share
     * <ul>
     *     <li>Net &gt; 0: Nhóm đang nợ thành viên này (thành viên được nhận lại tiền)</li>
     *     <li>Net &lt; 0: Thành viên này đang nợ nhóm (cần đóng bù thêm)</li>
     *     <li>Net = 0: Huề (đã tất toán xong)</li>
     * </ul>
     */
    public long getNetBalance() {
        return rawContribution + paidOutOfPocket - refunded - withdrawn - share;
    }
}
