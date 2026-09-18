package com.datn.financeapp.group.dto.group;

import jakarta.validation.constraints.NotBlank;

public record GroupJoinReq(
        @NotBlank String inviteCode) {
}
