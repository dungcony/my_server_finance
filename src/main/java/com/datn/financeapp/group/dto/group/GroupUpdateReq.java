package com.datn.financeapp.group.dto.group;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record GroupUpdateReq(
        @Size(max = 100) String name,
        @Size(max = 255) String description,
        @PositiveOrZero Long target,
        @JsonAlias({"is_split_equally", "isSplitEqually"}) Boolean isSettlementEnabled,
        Boolean isJoinWithoutConfirm) {
}
