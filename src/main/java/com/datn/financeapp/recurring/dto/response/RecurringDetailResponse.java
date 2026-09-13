package com.datn.financeapp.recurring.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phản hồi của {@code GET /recurring/{id}} — "chi tiết kèm lịch sử đã sinh" (api/09 mục A bảng
 * điểm cuối).
 *
 * <p>Lịch sử lấy từ {@code transactions} theo {@code recurring_id}, sắp xếp mới nhất trước, và bỏ
 * bản ghi đã xoá mềm.
 */
public record RecurringDetailResponse(RecurringListItemResponse recurring, List<GeneratedTransaction> history) {

    public record GeneratedTransaction(UUID id, LocalDate date, Long amount, String type, String source) {}
}
