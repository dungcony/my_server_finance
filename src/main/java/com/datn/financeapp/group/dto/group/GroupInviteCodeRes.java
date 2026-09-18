package com.datn.financeapp.group.dto.group;

import java.time.Instant;

public record GroupInviteCodeRes(
        String inviteCode,
        Instant expiresAt) {
}
