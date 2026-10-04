package com.datn.financeapp.user.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceImplValidUserTest {

    private static final String EMAIL = "kiem.tra@example.com";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private void givenUser(UserStatus status, boolean deleted) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .status(status)
                .isDeleted(deleted)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("validUser: tài khoản đang hoạt động -> không ném lỗi")
    void validUser_ActiveAccount_Passes() {
        givenUser(UserStatus.ACTIVE, false);

        assertThatCode(() -> userService.validUser(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validUser: tài khoản bị khoá -> AUTH_ACCOUNT_BLOCKED, không phải 'chưa xác thực'")
    void validUser_BlockedAccount_ThrowsBlocked() {
        givenUser(UserStatus.BLOCKED, false);

        assertThatThrownBy(() -> userService.validUser(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_ACCOUNT_BLOCKED");
    }

    @Test
    @DisplayName("validUser: tài khoản chưa xác thực email -> AUTH_ACCOUNT_NOT_VERIFIED")
    void validUser_PendingVerifyAccount_ThrowsNotVerified() {
        givenUser(UserStatus.PENDING_VERIFY, false);

        assertThatThrownBy(() -> userService.validUser(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_ACCOUNT_NOT_VERIFIED");
    }

    @Test
    @DisplayName("validUser: tài khoản đã xoá coi như không tồn tại -> NOT_FOUND")
    void validUser_DeletedAccount_ThrowsNotFound() {
        givenUser(UserStatus.ACTIVE, true);

        assertThatThrownBy(() -> userService.validUser(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("validUser: email chưa đăng ký -> NOT_FOUND")
    void validUser_UnknownEmail_ThrowsNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.validUser(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }
}
