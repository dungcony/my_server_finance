package com.datn.financeapp.group.dto.request.group;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GroupTransferOwnershipReq(
        @NotNull @JsonAlias({"new_owner_user_id", "newOwnerUserId"}) UUID newOwnerId) {
}
