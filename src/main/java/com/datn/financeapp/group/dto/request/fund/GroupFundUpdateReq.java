package com.datn.financeapp.group.dto.request.fund;

import java.util.UUID;

public record GroupFundUpdateReq(
        UUID heldByUserId) {
}
