package com.datn.financeapp.module.group.dto.request.group;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GroupTransferOwnershipReq(
        @NotNull(message = "Chủ nhóm mới không được để trống.") @JsonAlias({"new_owner_user_id", "newOwnerUserId"}) UUID newOwnerId) {
}
