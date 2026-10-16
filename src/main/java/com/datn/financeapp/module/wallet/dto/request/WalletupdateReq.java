package com.datn.financeapp.module.wallet.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WalletupdateReq(

        @Size(min = 1, max = 50, message = "Tên ví phải từ 1 đến 50 ký tự.")
        String name,
        UUID iconId,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.")
        String color

) {
}
