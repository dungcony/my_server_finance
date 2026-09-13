package com.datn.financeapp.wallet.dto.response;

import java.util.UUID;

/**
 * Ví kèm số dư THÔ — đọc thẳng cột {@code current_balance}, tức đã gồm cả giao dịch tương lai.
 *
 * <p>⚠️ <b>Khác nghĩa với {@code currentBalance} của {@link WalletResponse}</b>, vốn là "tiền thật
 * đến hết hôm nay" (đã trừ ngược giao dịch tương lai — D-37/TXN-09). Hai giá trị chỉ bằng nhau khi
 * ví không có giao dịch tương lai nào.
 *
 * <p>Dành riêng cho màn Báo cáo, nơi cần đúng giá trị thô. Đừng dùng DTO này cho API ví.
 */
public record WalletRawBalanceResponse(UUID id, String name, String type, Long currentBalance) {}
