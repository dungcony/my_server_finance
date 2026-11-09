package com.datn.financeapp.module.wallet.dto.request;

import com.datn.financeapp.module.wallet.enums.WalletType;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record WalletCreateReq(
        @NotBlank(message = "Tên ví không được để trống.")
        @Size(min = 1, max = 50, message = "Tên ví phải từ 1 đến 50 ký tự.")
        String name,

        @NotNull(message = "Vui lòng chọn loại ví.")
        WalletType type,

        @PositiveOrZero(message = "số dư không được để âm")
        @NotNull(message = "Số dư ban đầu không được để trống")
        Long balance,

        UUID userId,
        @NotBlank(message = "Hãy chọn icon")
        String iconCode,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.")
        String color) {
}
