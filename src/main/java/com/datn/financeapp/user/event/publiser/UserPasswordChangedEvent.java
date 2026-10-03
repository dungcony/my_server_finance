package com.datn.financeapp.user.event.publiser;

import java.util.UUID;

// Sự kiện phát ra khi người dùng đổi mật khẩu thành công.
public record UserPasswordChangedEvent(UUID userId) {
}
