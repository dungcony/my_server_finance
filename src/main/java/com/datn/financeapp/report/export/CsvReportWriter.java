package com.datn.financeapp.report.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Ghi CSV bằng JDK thuần (không thêm thư viện mới) — UTF-8 kèm BOM để Excel mở đúng tiếng Việt
 * có dấu. Escape RFC 4180 thủ công: field chứa dấu phẩy/ngoặc kép/xuống dòng bọc trong
 * {@code "..."}, escape {@code "} thành {@code ""}.
 */
public final class CsvReportWriter {

    private static final String[] HEADER = {"date", "type", "amount", "category_name", "wallet_name", "note"};

    private CsvReportWriter() {}

    /**
     * Tên tệp sinh từ {@code jobId} (UUID), KHÔNG dùng bất kỳ input người dùng nào — chặn path
     * traversal (T-04-17).
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

    /** Một dòng dữ liệu xuất — cột theo đúng thứ tự {@link #HEADER}. */
    public record TransactionExportRow(
            String date, String type, long amount, String categoryName, String walletName, String note) {}
}
