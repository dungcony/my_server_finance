package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.enums.MemberStatus;

import java.util.UUID;

public record GroupidAndMemberStatus(
        UUID groupId,
        MemberStatus memberStatus
) {

}
