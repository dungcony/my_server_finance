package com.datn.financeapp.auth.dto.response;

import java.util.UUID;

public record RegisterRes(
        UUID id,
        String email
) {
}
