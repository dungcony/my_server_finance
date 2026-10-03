package com.datn.financeapp.auth.dto.request;

import java.util.List;
import java.util.UUID;

public record TokenCreateReq(
        UUID userId,
        String plan,
        List<String> authorities,
        int roleLevel
) {
}
