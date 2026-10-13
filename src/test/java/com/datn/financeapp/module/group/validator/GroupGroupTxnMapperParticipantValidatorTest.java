package com.datn.financeapp.module.group.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.service.MemberService;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupGroupTxnMapperParticipantValidatorTest {

    @Mock
    private MemberService memberService;

    private GroupTransactionParticipantValidator validator;

    private UUID groupId;
    private UUID transactorId;
    private UUID participantId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        validator = new GroupTransactionParticipantValidator(memberService);
        groupId = UUID.randomUUID();
        transactorId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        otherId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Ném lỗi khi tổng shareAmount nhỏ hơn tổng tiền giao dịch")
    void validParticipants_SumLessThanAmount_ThrowsException() {
        List<GroupTxnParticipantReq> participants = List.of(
                new GroupTxnParticipantReq(participantId, 50_000L)
        );

        assertThatThrownBy(() -> validator.validParticipants(groupId, transactorId, participants, 100_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.GROUP_TXN_PARTICIPANTS_SUM_MISMATCH.getCode());
        verify(memberService, never()).allMemberInGroup(any(), any());
    }

    @Test
    @DisplayName("Ném lỗi khi tổng shareAmount vượt tổng tiền giao dịch")
    void validParticipants_SumGreaterThanAmount_ThrowsException() {
        List<GroupTxnParticipantReq> participants = List.of(
                new GroupTxnParticipantReq(participantId, 70_000L),
                new GroupTxnParticipantReq(otherId, 60_000L)
        );

        assertThatThrownBy(() -> validator.validParticipants(groupId, transactorId, participants, 100_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.GROUP_TXN_PARTICIPANTS_SUM_MISMATCH.getCode());
    }

    @Test
    @DisplayName("Ném lỗi khi một người xuất hiện hai lần trong danh sách tham gia")
    void validParticipants_DuplicatedParticipant_ThrowsException() {
        List<GroupTxnParticipantReq> participants = List.of(
                new GroupTxnParticipantReq(participantId, 40_000L),
                new GroupTxnParticipantReq(participantId, 60_000L)
        );

        assertThatThrownBy(() -> validator.validParticipants(groupId, transactorId, participants, 100_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.GROUP_TXN_PARTICIPANT_DUPLICATED.getCode());
    }

    @Test
    @DisplayName("Hợp lệ khi tổng shareAmount khớp; người chi ngoài danh sách tham gia vẫn được kiểm tư cách thành viên")
    void validParticipants_TransactorOutsideParticipants_TransactorAlsoChecked() {
        List<GroupTxnParticipantReq> participants = List.of(
                new GroupTxnParticipantReq(participantId, 40_000L),
                new GroupTxnParticipantReq(otherId, 60_000L)
        );

        when(memberService.allMemberInGroup(eq(groupId), any())).thenReturn(true);

        assertThatCode(() -> validator.validParticipants(groupId, transactorId, participants, 100_000L))
                .doesNotThrowAnyException();
        verify(memberService).allMemberInGroup(groupId, List.of(participantId, otherId, transactorId));
    }

    @Test
    @DisplayName("Người chi đã nằm trong danh sách tham gia thì không bị thêm lần hai")
    void validParticipants_TransactorInsideParticipants_NotAddedTwice() {
        List<GroupTxnParticipantReq> participants = List.of(
                new GroupTxnParticipantReq(transactorId, 40_000L),
                new GroupTxnParticipantReq(otherId, 60_000L)
        );

        when(memberService.allMemberInGroup(eq(groupId), any())).thenReturn(true);

        assertThatCode(() -> validator.validParticipants(groupId, transactorId, participants, 100_000L))
                .doesNotThrowAnyException();
        verify(memberService).allMemberInGroup(groupId, List.of(transactorId, otherId));
    }

    @Test
    @DisplayName("Giao dịch dùng tiền quỹ không có người chi (null): chỉ kiểm các người tham gia")
    void validParticipants_NullTransactor_OnlyParticipantsChecked() {
        List<GroupTxnParticipantReq> participants = List.of(
                new GroupTxnParticipantReq(participantId, 40_000L),
                new GroupTxnParticipantReq(otherId, 60_000L)
        );

        when(memberService.allMemberInGroup(eq(groupId), any())).thenReturn(true);

        assertThatCode(() -> validator.validParticipants(groupId, null, participants, 100_000L))
                .doesNotThrowAnyException();
        verify(memberService).allMemberInGroup(groupId, List.of(participantId, otherId));
    }

    @Test
    @DisplayName("Ném lỗi khi có người không thuộc nhóm")
    void validParticipants_MemberNotInGroup_ThrowsException() {
        List<GroupTxnParticipantReq> participants = List.of(
                new GroupTxnParticipantReq(participantId, 100_000L)
        );

        when(memberService.allMemberInGroup(eq(groupId), any())).thenReturn(false);

        assertThatThrownBy(() -> validator.validParticipants(groupId, transactorId, participants, 100_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP.getCode());
    }
}
