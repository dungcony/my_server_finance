package com.datn.financeapp.auth.events.listener;

import com.datn.financeapp.auth.service.TokenService;
import com.datn.financeapp.user.event.publiser.UserDeletedEvent;
import com.datn.financeapp.user.event.publiser.UserLockedEvent;
import com.datn.financeapp.user.event.publiser.UserPasswordChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Lắng nghe sự kiện từ module User để thu hồi toàn bộ token phiên (refresh token)
 * khi người dùng đổi mật khẩu hoặc xoá tài khoản.
 */
@Component
@RequiredArgsConstructor
public class AuthEventListener {

    private final TokenService tokenService;

    @EventListener
    public void onPasswordChanged(UserPasswordChangedEvent event) {
        tokenService.revokeAllByUserId(event.userId());
    }

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        tokenService.revokeAllByUserId(event.userId());
    }

    @EventListener
    public void onUserLocked(UserLockedEvent event) {
        tokenService.revokeAllByUserId(event.userId());
    }
}
