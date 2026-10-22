package com.datn.financeapp.module.user.event.Listener;

import com.datn.financeapp.module.auth.events.publisher.VerifyEmailEvent;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.exception.UserNotFoundException;
import com.datn.financeapp.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerifyEmailListener {

    private final UserRepository userRepository;

    @EventListener
    public void confimEmail(VerifyEmailEvent event) {
        User user = userRepository.findByEmail(event.email())
                .orElseThrow(UserNotFoundException::new);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

}
