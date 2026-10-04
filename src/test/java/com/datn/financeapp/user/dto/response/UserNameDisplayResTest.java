package com.datn.financeapp.user.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.user.enums.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserNameDisplayResTest {

    private static final String MASKED_NAME = "Người sử dụng app";

    private UserNameDisplayRes of(String firstName, String lastName, UserStatus status, boolean deleted) {
        return new UserNameDisplayRes(UUID.randomUUID(), firstName, lastName, status, deleted);
    }

    @Test
    @DisplayName("fullName: ghép họ và tên bằng một dấu cách")
    void fullName_JoinsFirstAndLastName() {
        assertThat(of("An", "Nguyễn", UserStatus.ACTIVE, false).fullName()).isEqualTo("An Nguyễn");
    }

    @Test
    @DisplayName("fullName: thiếu họ hoặc tên thì chỉ hiện phần còn lại, không in chữ null")
    void fullName_MissingPart_ShowsRemainingPart() {
        assertThat(of("An", null, UserStatus.ACTIVE, false).fullName()).isEqualTo("An");
        assertThat(of(null, "Nguyễn", UserStatus.ACTIVE, false).fullName()).isEqualTo("Nguyễn");
        assertThat(of(null, null, UserStatus.ACTIVE, false).fullName()).isEmpty();
    }

    @Test
    @DisplayName("fullName: tài khoản đã xoá bị che tên")
    void fullName_DeletedAccount_IsMasked() {
        assertThat(of("An", "Nguyễn", UserStatus.ACTIVE, true).fullName()).isEqualTo(MASKED_NAME);
    }

    @Test
    @DisplayName("fullName: tài khoản bị khoá bị che tên")
    void fullName_BlockedAccount_IsMasked() {
        assertThat(of("An", "Nguyễn", UserStatus.BLOCKED, false).fullName()).isEqualTo(MASKED_NAME);
    }
}
