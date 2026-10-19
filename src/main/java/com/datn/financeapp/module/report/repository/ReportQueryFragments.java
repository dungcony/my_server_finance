package com.datn.financeapp.module.report.repository;

/**
 * Lớp chứa các hằng số đoạn truy vấn SQL dùng chung cho module báo cáo.
 * Tham chiếu các đoạn điều kiện chính như {@link #REPORT_ELIGIBLE} và {@link #WALLET_FILTER}.
 */
public final class ReportQueryFragments {

    private ReportQueryFragments() {
    }

    public static final String REPORT_ELIGIBLE =
            " NOT t.is_deleted AND t.type IN ('EXPENSE', 'INCOME') ";

    /**
     * Lọc theo một ví, bỏ qua khi {@code :walletId} là NULL ("Ví tổng").
     */
    public static final String WALLET_FILTER =
            " (CAST(:walletId AS uuid) IS NULL OR t.wallet_id = CAST(:walletId AS uuid)) ";
}
