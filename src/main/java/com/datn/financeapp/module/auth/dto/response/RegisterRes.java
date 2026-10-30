package com.datn.financeapp.module.auth.dto.response;

import java.util.UUID;

public record RegisterRes(
        UUID id,
        String email
) {
}
