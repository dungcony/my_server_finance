package com.datn.financeapp.module.report.dto.request;

import java.util.UUID;

/**
 * DTO yêu cầu bộ lọc danh sách tiến trình xuất báo cáo dành cho Quản trị viên.
 *
 * @param userId ID người dùng cần lọc
 * @param status trạng thái tiến trình (processing, completed, failed)
 * @param format định dạng file (csv, pdf, excel)
 */
public record ExportJobFilterRequest(
        UUID userId,
        String status,
        String format) {
}
