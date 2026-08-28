package com.datn.financeapp.report.repository;

/**
 * Hằng số SQL dùng chung cho MỌI query của module báo cáo — ba nguyên tắc bắt buộc theo
 * api/06-BAO-CAO.md (đã sửa D-61): loại {@code type = 'transfer'}, loại giao dịch không tính vào
 * báo cáo ({@code counts_in_report = FALSE}, ví dụ điều chỉnh số dư), loại bản ghi đã xoá mềm.
 *
 * <p>KHÁC {@code TransactionRepository} ở {@code transaction/} — nơi {@code counts_in_report} là
 * tham số lọc người dùng TỰ CHỌN (sổ giao dịch cho xem cả điều chỉnh nếu muốn) — ở đây ba điều
 * kiện này HARD-CODE, không nhận tham số cho phép tắt, vì báo cáo không bao giờ được lẫn giao
 * dịch chuyển tiền hay điều chỉnh số dư.
 */
public final class ReportQueryFragments {

    private ReportQueryFragments() {}

    public static final String REPORT_ELIGIBLE =
            "t.type <> 'transfer' AND t.counts_in_report = TRUE AND NOT t.is_deleted";
}
