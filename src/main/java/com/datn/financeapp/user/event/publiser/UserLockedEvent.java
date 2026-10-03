package com.datn.financeapp.user.event.publiser;

import java.util.UUID;

public record UserLockedEvent(
        UUID userId
) {
}
