package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.exception.PasswordAlreadySetException;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.helper.PasswordGenerator;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserMapper userMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordGenerator passwordGenerator;

    @InjectMocks
    private AccountServiceImpl accountService;

    private final UUID userId = UUID.randomUUID();

    private User userWithPassword(String passwordHash) {
        return User.builder()
                .id(userId)
                .email("google.user@example.com")
                .password(passwordHash)
                .build();
    }

    @Test
    @DisplayName("generatePassword: Tài khoản chưa có mật khẩu -> lưu bản băm và gửi mật khẩu thô về email")
    void generatePassword_noPassword_savesHashAndEmailsRawPassword() {
        User user = userWithPassword(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordGenerator.generate()).thenReturn("Abcd2345Efgh");
        when(passwordEncoder.encode("Abcd2345Efgh")).thenReturn("hashed-value");

        accountService.generatePassword(userId);

        assertThat(user.getPassword()).isEqualTo("hashed-value");
        verify(userRepository).save(user);
        verify(emailService).sendGeneratedPassword("google.user@example.com", "Abcd2345Efgh");
        // không thu hồi refresh token — người dùng chưa có mật khẩu cũ nào bị lộ
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("generatePassword: Tài khoản đã có mật khẩu -> PASSWORD_ALREADY_SET, không đổi gì, không gửi mail")
    void generatePassword_hasPassword_throwsAndChangesNothing() {
        User user = userWithPassword("existing-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountService.generatePassword(userId))
                .isInstanceOf(PasswordAlreadySetException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PASSWORD_ALREADY_SET"));

        assertThat(user.getPassword()).isEqualTo("existing-hash");
        verify(userRepository, never()).save(user);
        verifyNoInteractions(emailService, passwordGenerator);
    }

    @Test
    @DisplayName("generatePassword: Không tìm thấy user -> UserNotFoundException")
    void generatePassword_userNotFound_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.generatePassword(userId))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(emailService, passwordGenerator);
    }
}
