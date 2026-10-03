package com.datn.financeapp.user.event.Listener;

import com.datn.financeapp.auth.events.publisher.VerifyEmailEvent;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.repository.UserRepository;
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
