package com.datn.financeapp.module.report.repository;

/**
 * Lớp chứa các hằng số đoạn truy vấn SQL dùng chung cho module báo cáo.
 * Tham chiếu các đoạn điều kiện chính như {@link #REPORT_ELIGIBLE} và {@link #WALLET_FILTER}.
 */
public final class ReportQueryFragments {

    private ReportQueryFragments() {
    }

    public static final String REPORT_ELIGIBLE =
            "t.type <> 'transfer' AND t.counts_in_report = TRUE AND NOT t.is_deleted";

    /**
     * Lọc theo một ví, bỏ qua khi {@code :walletId} là NULL ("Ví tổng").
     *
     * <p>Xét cả {@code destination_wallet_id} để tiền chuyển ĐẾN ví đang xem cũng thuộc về nó —
     * dù {@link #REPORT_ELIGIBLE} đã loại {@code transfer} khỏi báo cáo nên nhánh đó hiện không
     * bao giờ khớp. Giữ lại cho khớp {@code TransactionRepository#search}: hai nơi lọc ví theo
     * hai luật khác nhau là kiểu lệch âm thầm rất khó truy.
     */
    public static final String WALLET_FILTER =
            "(CAST(:walletId AS uuid) IS NULL OR t.wallet_id = CAST(:walletId AS uuid)"
                    + " OR t.destination_wallet_id = CAST(:walletId AS uuid))";
}
