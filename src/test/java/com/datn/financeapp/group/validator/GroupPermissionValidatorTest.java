package com.datn.financeapp.group.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử {@link GroupPermissionValidator}: xác thực tư cách thành viên và trạng thái nhóm.
 * Trọng tâm là cờ {@code allowArchived}: hàm ghi dùng bản mặc định phải bị chặn khi nhóm lưu trữ,
 * hàm đọc truyền {@code true} thì vẫn đọc được (rule.md quy tắc 26).
 */
@ExtendWith(MockitoExtension.class)
class GroupPermissionValidatorTest {

    @Mock
    private GroupRepository groupRepository;

    private GroupPermissionValidator validator;

    private UUID groupId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        validator = new GroupPermissionValidator(groupRepository);
        groupId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Nhóm đang hoạt động thì trả thông tin quyền ở cả bản mặc định lẫn bản cho phép nhóm lưu trữ")
    void getAuthInfo_ActiveGroup_ReturnsInfo() {
        MemberAuthInfo info = info(GroupStatus.ACTIVE, MemberRole.MEMBER);
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.of(info));

        assertThat(validator.getAuthInfo(groupId, userId)).isSameAs(info);
        assertThat(validator.getAuthInfo(groupId, userId, false)).isSameAs(info);
        assertThat(validator.getAuthInfo(groupId, userId, true)).isSameAs(info);
    }

    @Test
    @DisplayName("Nhóm lưu trữ: bản mặc định (dùng cho thao tác ghi) bị chặn GROUP_ARCHIVED")
    void getAuthInfo_ArchivedGroup_DefaultBlocks() {
        when(groupRepository.findAuthInfo(groupId, userId))
                .thenReturn(Optional.of(info(GroupStatus.ARCHIVED, MemberRole.OWNER)));

        assertThatThrownBy(() -> validator.getAuthInfo(groupId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_ARCHIVED.getCode());
    }

    @Test
    @DisplayName("Nhóm lưu trữ: allowArchived = false cũng bị chặn GROUP_ARCHIVED")
    void getAuthInfo_ArchivedGroup_AllowArchivedFalse_Blocks() {
        when(groupRepository.findAuthInfo(groupId, userId))
                .thenReturn(Optional.of(info(GroupStatus.ARCHIVED, MemberRole.OWNER)));

        assertThatThrownBy(() -> validator.getAuthInfo(groupId, userId, false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_ARCHIVED.getCode());
    }

    @Test
    @DisplayName("Nhóm lưu trữ: allowArchived = true (thao tác đọc) vẫn trả thông tin quyền")
    void getAuthInfo_ArchivedGroup_AllowArchivedTrue_ReturnsInfo() {
        MemberAuthInfo info = info(GroupStatus.ARCHIVED, MemberRole.MEMBER);
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.of(info));

        assertThat(validator.getAuthInfo(groupId, userId, true)).isSameAs(info);
    }

    @Test
    @DisplayName("Nhóm đã xoá luôn báo GROUP_NOT_FOUND, kể cả khi cho phép nhóm lưu trữ")
    void getAuthInfo_DeletedGroup_NotFoundForBothFlags() {
        when(groupRepository.findAuthInfo(groupId, userId))
                .thenReturn(Optional.of(info(GroupStatus.DELETED, MemberRole.OWNER)));

        assertThatThrownBy(() -> validator.getAuthInfo(groupId, userId, false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND.getCode());
        assertThatThrownBy(() -> validator.getAuthInfo(groupId, userId, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Không phải thành viên ACTIVE (không có bản ghi) thì báo GROUP_NOT_FOUND ở cả hai cờ")
    void getAuthInfo_NoMembership_NotFoundForBothFlags() {
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.getAuthInfo(groupId, userId, false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND.getCode());
        assertThatThrownBy(() -> validator.getAuthInfo(groupId, userId, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Thiếu groupId hoặc operatorId thì báo VALIDATION_ERROR")
    void getAuthInfo_NullArguments_ValidationError() {
        assertThatThrownBy(() -> validator.getAuthInfo((UUID) null, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThatThrownBy(() -> validator.getAuthInfo(groupId, null, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("verifyOwner: chủ nhóm trên nhóm lưu trữ qua khi allowArchived = true, bị chặn ở bản mặc định")
    void verifyOwner_OwnerOnArchivedGroup_OnlyPassesWhenAllowed() {
        when(groupRepository.findAuthInfo(groupId, userId))
                .thenReturn(Optional.of(info(GroupStatus.ARCHIVED, MemberRole.OWNER)));

        assertThatCode(() -> validator.verifyOwner(groupId, userId, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.verifyOwner(groupId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_ARCHIVED.getCode());
    }

    @Test
    @DisplayName("verifyOwner: thành viên thường bị chặn GROUP_OWNER_REQUIRED dù cho phép nhóm lưu trữ")
    void verifyOwner_NormalMember_ForbiddenOwnerRequired() {
        when(groupRepository.findAuthInfo(groupId, userId))
                .thenReturn(Optional.of(info(GroupStatus.ACTIVE, MemberRole.MEMBER)));

        assertThatThrownBy(() -> validator.verifyOwner(groupId, userId, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED.getCode());
    }

    @Test
    @DisplayName("verifyMember: thành viên ACTIVE xem được nhóm lưu trữ khi allowArchived = true, bị chặn khi false")
    void verifyMember_ArchivedGroup_PassesOnlyWhenAllowed() {
        when(groupRepository.findAuthInfo(groupId, userId))
                .thenReturn(Optional.of(info(GroupStatus.ARCHIVED, MemberRole.MEMBER)));

        assertThatCode(() -> validator.verifyMember(groupId, userId, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.verifyMember(groupId, userId, false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_ARCHIVED.getCode());
    }

    @Test
    @DisplayName("verifyMember: người không phải thành viên ACTIVE (không có bản ghi) thì bị từ chối")
    void verifyMember_NotActiveMember_Rejected() {
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.verifyMember(groupId, userId, true))
                .isInstanceOf(BusinessException.class);
    }

    // thành viên ACTIVE của nhóm với trạng thái nhóm và vai trò cho trước
    private MemberAuthInfo info(GroupStatus groupStatus, MemberRole role) {
        return new MemberAuthInfo(groupId, userId, groupStatus, true, MemberStatus.ACTIVE, role, null);
    }
}
