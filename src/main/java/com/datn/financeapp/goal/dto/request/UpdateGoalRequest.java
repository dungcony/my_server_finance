package com.datn.financeapp.goal.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của {@code PATCH /goals/{id}} (api/09 mục B5) — chỉ 5 trường này sửa được.
 *
 * <p>{@code status} sửa được là ngoại lệ CÓ CHỦ Ý và DUY NHẤT: api/09 mục B5 cho phép đặt
 * {@code cancelled} để dừng theo dõi mà vẫn giữ lịch sử. Trigger tôn trọng giá trị này (nhánh
 * {@code WHEN status = 'cancelled' THEN 'cancelled'}) nên không bị ghi đè về sau. Service CHỈ
 * chấp nhận giá trị {@code cancelled}/{@code in_progress}, không cho tay ghi {@code completed} —
 * trạng thái đó phải do trigger tự suy ra từ {@code saved_amount}.
 */
public record UpdateGoalRequest(
        @Size(min = 1, max = 100) String name,
        @Positive Long targetAmount,
        LocalDate targetDate,
        UUID iconId,
        String status) {}
