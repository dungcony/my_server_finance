package com.datn.financeapp.module.group.events;

import java.util.UUID;

public record MemberLeaveEvent(
        UUID groupId,
        UUID memberId,
        UUID ownerId
) {
}
