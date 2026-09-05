package com.datn.financeapp.wallet.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** Body của PATCH /wallets/reorder (api/02-VI.md mục 7). */
public record ReorderWalletsRequest(@NotEmpty List<UUID> sortOrder) {
}
