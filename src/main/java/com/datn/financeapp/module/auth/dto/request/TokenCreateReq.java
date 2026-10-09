package com.datn.financeapp.module.auth.dto.request;

import java.util.List;
import java.util.UUID;

public record TokenCreateReq(
        UUID userId,
        String email,
        String plan,
        List<String> authorities,
        int roleLevel
) {
}
