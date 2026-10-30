package com.datn.financeapp.module.report.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Class tiện ích ghi báo cáo dữ liệu định dạng CSV.
 * Cung cấp phương thức ghi tệp chính {@link #write(Path, UUID, List)} hỗ trợ định dạng UTF-8 BOM và chuẩn hóa chuẩn RFC 4180.
 */
public final class CsvReportWriter {

    private static final String[] HEADER = {"date", "type", "amount", "category_name", "wallet_name", "note"};

    private CsvReportWriter() {
    }

    /**
     * Ghi danh sách các dòng dữ liệu xuất ra tệp CSV.
     *
     * @param targetDir thư mục chứa tệp xuất
     * @param jobId     ID tiến trình xuất dữ liệu
     * @param rows      danh sách dòng dữ liệu giao dịch xuất
     * @return {@link Path} đường dẫn tệp CSV đã tạo
     * @throws IOException nếu xảy ra lỗi ghi tệp
     */
    public static Path write(Path targetDir, UUID jobId, List<TransactionExportRow> rows) throws IOException {
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(jobId + ".csv");

        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write('﻿');
            writer.write(String.join(",", HEADER));
            writer.write("\r\n");
            for (TransactionExportRow row : rows) {
                writer.write(escape(row.date()));
                writer.write(',');
                writer.write(escape(row.type()));
                writer.write(',');
                writer.write(escape(String.valueOf(row.amount())));
                writer.write(',');
                writer.write(escape(row.categoryName()));
                writer.write(',');
                writer.write(escape(row.walletName()));
                writer.write(',');
                writer.write(escape(row.note()));
                writer.write("\r\n");
            }
        }
        return target;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * Record mô tả dữ liệu xuất của một dòng giao dịch.
     *
     * @param date         ngày thực hiện giao dịch dạng chuỗi
     * @param type         loại giao dịch
     * @param amount       số tiền
     * @param categoryName tên danh mục
     * @param walletName   tên ví
     * @param note         ghi chú giao dịch
     */
    public record TransactionExportRow(
            String date, String type, long amount, String categoryName, String walletName, String note) {
    }
}
