package com.datn.financeapp.group.dto.wallet;

import com.datn.financeapp.group.enums.GroupWalletStatus;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GroupWalletUpdateReq(
        @Size(max = 50) String name,
        UUID heldByUserId,
        GroupWalletStatus status) {
}
