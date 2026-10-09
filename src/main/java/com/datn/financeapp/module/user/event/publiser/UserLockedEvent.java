package com.datn.financeapp.module.user.event.publiser;

import java.util.UUID;

public record UserLockedEvent(
        UUID userId
) {
}
