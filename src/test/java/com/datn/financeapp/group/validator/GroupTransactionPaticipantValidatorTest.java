package com.datn.financeapp.group.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.service.MemberService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupTransactionPaticipantValidatorTest {

    @Mock
    private MemberService memberService;

    private GroupTransactionPaticipantValidator validator;

    private UUID groupId;
    private UUID transactorId;
    private UUID participantId;

    @BeforeEach
    void setUp() {
        validator = new GroupTransactionPaticipantValidator(memberService);
        groupId = UUID.randomUUID();
        transactorId = UUID.randomUUID();
        participantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Ném lỗi khi danh sách người tham gia có shareAmount bị null")
    void validTransactorAndParticipants_NullShareAmount_ThrowsException() {
        List<GroupTransactionParticipantReq> participants = List.of(
                new GroupTransactionParticipantReq(participantId, null)
        );

        assertThatThrownBy(() -> validator.validTransactorAndParticipants(groupId, transactorId, participants, 100_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.GROUP_TXN_PARTICIPANTS_AMOUNT_INVALID.getCode());
    }

    @Test
    @DisplayName("Ném lỗi khi danh sách người tham gia có shareAmount <= 0")
    void validTransactorAndParticipants_ZeroOrNegativeShareAmount_ThrowsException() {
        List<GroupTransactionParticipantReq> participants = List.of(
                new GroupTransactionParticipantReq(participantId, 0L)
        );

        assertThatThrownBy(() -> validator.validTransactorAndParticipants(groupId, transactorId, participants, 100_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.GROUP_TXN_PARTICIPANTS_AMOUNT_INVALID.getCode());
    }

    @Test
    @DisplayName("Ném lỗi khi tổng shareAmount không khớp với tổng tiền giao dịch")
    void validTransactorAndParticipants_SumMismatch_ThrowsException() {
        List<GroupTransactionParticipantReq> participants = List.of(
                new GroupTransactionParticipantReq(participantId, 50_000L)
        );

        assertThatThrownBy(() -> validator.validTransactorAndParticipants(groupId, transactorId, participants, 100_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.GROUP_TXN_PARTICIPANTS_SUM_MISMATCH.getCode());
    }

    @Test
    @DisplayName("Hợp lệ khi mọi người tham gia đều có shareAmount hợp lệ và tổng khớp")
    void validTransactorAndParticipants_ValidShares_Success() {
        UUID otherId = UUID.randomUUID();
        List<GroupTransactionParticipantReq> participants = List.of(
                new GroupTransactionParticipantReq(participantId, 40_000L),
                new GroupTransactionParticipantReq(otherId, 60_000L)
        );

        when(memberService.allMemberInGroup(eq(groupId), any())).thenReturn(true);

        assertThatCode(() -> validator.validTransactorAndParticipants(groupId, transactorId, participants, 100_000L))
                .doesNotThrowAnyException();
    }
}
