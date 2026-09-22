package com.datn.financeapp.group.dto.request.group;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record GroupCreateReq(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @PositiveOrZero Long target,
        @NotNull @JsonAlias({"is_split_equally", "isSplitEqually"}) Boolean isSettlementEnabled,
        @NotNull Boolean isJoinWithoutConfirm,
        List<UUID> members
        ) {
}
