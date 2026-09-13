package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.auth.events.LoginSuccessEvent;
import com.datn.financeapp.auth.events.VerifyEmailEvent;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountEventListener {

    private final UserRepository userRepository;

    @EventListener
    public void confimEmail(VerifyEmailEvent event) {
        User user = userRepository.findByEmail(event.email())
                .orElseThrow(UserNotFoundException::new);
        user.setConfirm(true);
        userRepository.save(user);
    }

    @EventListener
    public void loginSuccess(LoginSuccessEvent event) {
        userRepository.findById(event.uid()).ifPresent(user -> {
            user.setLastLoginAt(event.now());
            userRepository.save(user);
        });
    }
}
