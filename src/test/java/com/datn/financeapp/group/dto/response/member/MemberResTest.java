package com.datn.financeapp.group.dto.response.member;

import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử {@link MemberRes#withDisplay}: chỉ gắn thêm tên hiển thị và cờ thủ quỹ, mọi trường còn lại phải giữ nguyên.
 */
class MemberResTest {

    private final UUID id = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant joinedAt = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    @DisplayName("Thành viên đã rời nhóm: gắn tên hiển thị xong vẫn giữ nguyên leftAt, joinedAt và trạng thái")
    void withDisplay_LeftMember_KeepsLeftAtAndOtherFields() {
        Instant leftAt = Instant.parse("2026-09-10T00:00:00Z");
        MemberRes left = new MemberRes(id, userId, MemberRole.MEMBER, MemberStatus.LEFT, joinedAt, leftAt, null, false);

        MemberRes shown = left.withDisplay("Nguyen A", true);

        assertThat(shown.leftAt()).isEqualTo(leftAt);
        assertThat(shown.joinedAt()).isEqualTo(joinedAt);
        assertThat(shown.status()).isEqualTo(MemberStatus.LEFT);
        assertThat(shown.id()).isEqualTo(id);
        assertThat(shown.userId()).isEqualTo(userId);
        assertThat(shown.role()).isEqualTo(MemberRole.MEMBER);
        assertThat(shown.displayName()).isEqualTo("Nguyen A");
        assertThat(shown.isTreasurer()).isTrue();
    }

    @Test
    @DisplayName("Thành viên đang ở nhóm: leftAt vẫn là null sau khi gắn tên hiển thị")
    void withDisplay_ActiveMember_LeftAtStaysNull() {
        MemberRes active = new MemberRes(id, userId, MemberRole.OWNER, MemberStatus.ACTIVE, joinedAt);

        MemberRes shown = active.withDisplay("Tran B", false);

        assertThat(shown.leftAt()).isNull();
        assertThat(shown.joinedAt()).isEqualTo(joinedAt);
        assertThat(shown.displayName()).isEqualTo("Tran B");
        assertThat(shown.isTreasurer()).isFalse();
    }
}
