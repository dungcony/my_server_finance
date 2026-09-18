package com.datn.financeapp.group.dto.group;

import com.datn.financeapp.group.dto.wallet.GroupWalletRes;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.GroupStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupDetailRes(
        UUID id,
        String name,
        String description,
        GroupStatus status,
        Long target,
        Boolean isSettlementEnabled,
        Boolean isSplitEqually,
        Boolean isJoinWithoutConfirm,
        Instant createdAt,
        GroupRole myRole,
        GroupWalletRes fund,
        List<GroupWalletRes> wallets,
        List<GroupMemberRes> members) {
}
