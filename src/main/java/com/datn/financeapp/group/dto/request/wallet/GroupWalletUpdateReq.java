package com.datn.financeapp.group.dto.request.wallet;

import com.datn.financeapp.group.enums.GroupWalletStatus;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GroupWalletUpdateReq(
        UUID heldByUserId) {
}
