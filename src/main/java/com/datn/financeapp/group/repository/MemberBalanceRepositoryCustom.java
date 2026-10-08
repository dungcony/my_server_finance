package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.helper.MemberBalanceAccumulator;

import java.util.Map;
import java.util.UUID;

public interface MemberBalanceRepositoryCustom {
    void batchAddDelta(UUID groupId, Map<UUID, MemberBalanceAccumulator> deltas);
}
