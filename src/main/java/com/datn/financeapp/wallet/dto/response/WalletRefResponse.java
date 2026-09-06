package com.datn.financeapp.wallet.dto.response;

import java.util.UUID;

/**
 * Tham chiếu tối thiểu tới một ví — chỉ định danh và tên hiển thị.
 *
 * <p>Dùng khi module khác cần "nhắc tên ví" trong phản hồi của mình (sổ nợ, mục tiêu, khoản định
 * kỳ, báo cáo) mà không cần số dư hay cấu hình. Trả DTO này thay vì entity {@code Wallet} để
 * module ngoài không phụ thuộc vào cấu trúc bảng {@code wallets} (quy tắc 11 CLAUDE.md).
 */
public record WalletRefResponse(UUID id, String name) {}
