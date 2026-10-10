package com.datn.financeapp.module.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Body của POST /wallets (api/02-VI.md mục 4).
public record CreateWalletRequest(
        @NotBlank(message = "Tên ví không được để trống.") @Size(min = 1, max = 50, message = "Tên ví phải từ 1 đến 50 ký tự.") String name,
        @NotBlank(message = "Vui lòng chọn loại ví.")
        @Pattern(regexp = "^(cash|bank|e_wallet|credit_card)$",
                message = "Loại ví không hợp lệ.") String type,
        @NotNull(message = "Số dư ban đầu không được để trống.") Long initialBalance,
        Boolean includeInTotal,
        String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.") String color,
        UUID groupId) {
}
