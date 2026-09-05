package com.datn.financeapp.recurring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của {@code POST /recurring} (api/09 mục A2).
 *
 * <p>{@code interval} để null nghĩa là 1 (mặc định theo đặc tả), service tự điền — không đặt giá
 * trị mặc định ở đây để phân biệt được "không gửi" với "gửi 1".
 *
 * <p>KHÔNG có trường {@code next_run_date}: nó do server suy ra bằng {@code start_date}, người
 * dùng không được tự đặt.
 */
public record CreateRecurringRequest(
        @NotBlank @Size(min = 1, max = 100) String displayName,
        @NotBlank String type,
        @NotNull @Positive Long amount,
        @NotNull UUID walletId,
        @NotNull UUID categoryId,
        @NotBlank String frequency,
        Integer interval,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String note) {}
