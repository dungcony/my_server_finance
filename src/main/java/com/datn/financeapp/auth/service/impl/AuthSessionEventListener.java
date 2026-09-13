package com.datn.financeapp.auth.service.impl;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.event.UserDeletedEvent;
import com.datn.financeapp.user.event.UserPasswordChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Lắng nghe sự kiện từ module User để thu hồi toàn bộ token phiên (refresh token)
 * khi người dùng đổi mật khẩu hoặc xoá tài khoản.
 */
@Component
@RequiredArgsConstructor
public class AuthSessionEventListener {

    private final RefreshTokenRepository refreshTokenRepository;

    @EventListener
    public void onPasswordChanged(UserPasswordChangedEvent event) {
        refreshTokenRepository.revokeAllActiveForUser(event.userId());
    }

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        refreshTokenRepository.revokeAllActiveForUser(event.userId());
    }
}
