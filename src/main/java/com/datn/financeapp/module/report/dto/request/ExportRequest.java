package com.datn.financeapp.module.report.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request DTO yêu cầu xuất báo cáo dữ liệu.
 *
 * @param format   định dạng xuất (ví dụ: csv)
 * @param period   chu kỳ xuất báo cáo (month, week, year)
 * @param fromDate ngày bắt đầu (nếu tùy chỉnh)
 * @param toDate   ngày kết thúc (nếu tùy chỉnh)
 * @param include  các thông tin bổ sung cần bao gồm
 */
public record ExportRequest(
        @NotBlank String format, String period, LocalDate fromDate, LocalDate toDate, String include) {
}
