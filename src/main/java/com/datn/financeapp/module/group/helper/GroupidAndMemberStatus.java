package com.datn.financeapp.module.group.helper;

import com.datn.financeapp.module.group.enums.MemberStatus;

import java.util.UUID;

public record GroupidAndMemberStatus(
        UUID groupId,
        MemberStatus memberStatus
) {

}
