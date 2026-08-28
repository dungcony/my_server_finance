package com.datn.financeapp.report.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** {@code POST /reports/export} (api/06-BAO-CAO.md mục 7, D-59). */
public record ExportRequest(
        @NotBlank String format, String period, LocalDate fromDate, LocalDate toDate, String include) {}
