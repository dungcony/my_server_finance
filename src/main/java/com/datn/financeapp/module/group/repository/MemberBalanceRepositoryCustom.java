package com.datn.financeapp.module.group.repository;

import com.datn.financeapp.module.group.helper.MemberBalanceAccumulator;

import java.util.Map;
import java.util.UUID;

public interface MemberBalanceRepositoryCustom {
    void batchAddDelta(UUID groupId, Map<UUID, MemberBalanceAccumulator> deltas);
}
