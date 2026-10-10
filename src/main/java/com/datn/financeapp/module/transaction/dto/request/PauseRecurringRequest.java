package com.datn.financeapp.module.transaction.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu tạm dừng hoặc tiếp tục chạy giao dịch định kỳ.
 *
 * @param isEnabled Trạng thái bật/tắt ({@code true} để bật, {@code false} để tạm dừng)
 */
public record PauseRecurringRequest(@NotNull(message = "Vui lòng chọn bật hoặc tắt.") Boolean isEnabled) {
}
