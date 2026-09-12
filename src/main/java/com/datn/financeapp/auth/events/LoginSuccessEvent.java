package com.datn.financeapp.auth.events;

import java.time.Instant;
import java.util.UUID;

public record LoginSuccessEvent(
        UUID uid,
        Instant now
) {
}
