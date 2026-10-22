package com.datn.financeapp.module.group.events;

import java.util.UUID;

public record FundBalanceChangedEvent(UUID groupId, Long delta) {
}
