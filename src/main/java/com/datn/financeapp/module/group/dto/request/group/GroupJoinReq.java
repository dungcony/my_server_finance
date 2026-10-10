package com.datn.financeapp.module.group.dto.request.group;

import jakarta.validation.constraints.NotBlank;

public record GroupJoinReq(
        @NotBlank(message = "Mã mời không được để trống.") String inviteCode) {
}
