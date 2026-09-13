package com.datn.financeapp.user.event;

import java.util.UUID;

// Sự kiện phát ra khi người dùng yêu cầu xóa tài khoản thành công.
public record UserDeletedEvent(UUID userId) {
}
