package com.datn.financeapp.group.events;

import java.util.UUID;

public record FundBalanceChangedEvent(UUID groupId, Long delta) {
}
