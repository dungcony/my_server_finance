package com.datn.financeapp.recurring.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code POST /recurring/{id}/pause} (api/09 mục A3). Một endpoint làm cả hai chiều: tạm
 * dừng ({@code false}) và bật lại ({@code true}).
 *
 * <p>Tạm dừng KHÔNG xoá lịch — {@code next_run_date} giữ nguyên, bật lại là chạy tiếp từ đó.
 */
public record PauseRecurringRequest(@NotNull(message = "Vui lòng chọn bật hoặc tắt.") Boolean isEnabled) {}
