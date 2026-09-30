package com.datn.financeapp.group.events;

import java.util.UUID;

public record MemberLeaveEvent(
        UUID groupId,
        UUID memberId,
        UUID ownerId
        ) {
}
