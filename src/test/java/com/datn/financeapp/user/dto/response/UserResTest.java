package com.datn.financeapp.user.dto.response;

import com.datn.financeapp.user.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử {@link UserRes#fullName()}: ghép tên hiển thị, không in chữ "null" khi thiếu một phần.
 */
class UserResTest {

    @Test
    @DisplayName("Có đủ firstName và lastName thì ghép cách nhau một dấu cách")
    void fullName_BothParts_JoinedBySpace() {
        UserRes user = userWithName("Nguyen", "An");

        assertThat(user.fullName()).isEqualTo("Nguyen An");
    }

    @Test
    @DisplayName("Tài khoản đã xóa hoặc khóa chỉ có tên thay thế, không có lastName thì không in chữ null")
    void fullName_MissingLastName_ReturnsFirstNameOnly() {
        UserRes user = new UserRes("Người dùng App", UserStatus.BLOCKED, true);

        assertThat(user.fullName()).isEqualTo("Người dùng App");
    }

    @Test
    @DisplayName("lastName chỉ gồm khoảng trắng thì bỏ qua, không để thừa dấu cách ở cuối")
    void fullName_BlankLastName_IgnoredWithoutTrailingSpace() {
        UserRes user = userWithName("Nguyen", "   ");

        assertThat(user.fullName()).isEqualTo("Nguyen");
    }

    @Test
    @DisplayName("Thiếu cả firstName lẫn lastName thì trả chuỗi rỗng")
    void fullName_BothMissing_ReturnsEmpty() {
        UserRes user = userWithName(null, null);

        assertThat(user.fullName()).isEmpty();
    }

    private UserRes userWithName(String firstName, String lastName) {
        return new UserRes(null, null, firstName, lastName, null, UserStatus.ACTIVE, null, null, null, false);
    }
}
