package com.datn.financeapp.recurring.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của {@code PATCH /recurring/{id}} (api/09 mục A). Mọi trường đều tuỳ chọn — null nghĩa là
 * "giữ nguyên", không phải "xoá về null".
 *
 * <p>Ngoại lệ có chủ đích: {@code endDate} không phân biệt được "giữ nguyên" với "bỏ ngày kết
 * thúc" nếu chỉ nhìn null. Đặc tả không có nhu cầu gỡ {@code end_date} nên ở đây null = giữ
 * nguyên, muốn kéo dài vô hạn thì đặt ngày rất xa.
 */
public record UpdateRecurringRequest(
        @Size(min = 1, max = 100) String displayName,
        @Positive Long amount,
        UUID walletId,
        UUID categoryId,
        String frequency,
        @Positive Integer interval,
        LocalDate endDate,
        String note) {}
