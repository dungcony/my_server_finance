package com.datn.financeapp.goal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của {@code POST /goals/{id}/contributions} (api/09 mục B3).
 *
 * <p>{@code createTransaction} mặc định {@code true} theo đặc tả. Service đọc TƯỜNG MINH trường
 * này (D-49) chứ KHÔNG suy đoán chế độ từ việc mục tiêu có {@code wallet_id} hay không — hai
 * chuyện độc lập: mục tiêu có ví vẫn được nạp kiểu "chỉ ghi nhận tiến độ".
 */
public record CreateContributionRequest(
        @NotNull @Positive Long amount,
        LocalDate contributedDate,
        UUID sourceWalletId,
        Boolean createTransaction,
        String note) {}
