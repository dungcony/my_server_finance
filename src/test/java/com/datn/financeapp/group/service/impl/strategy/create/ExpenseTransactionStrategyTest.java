package com.datn.financeapp.group.service.impl.strategy.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lớp kiểm thử cho {@link ExpenseTransaction}.
 * Đảm bảo các logic nghiệp vụ tạo giao dịch chi tiêu được thực thi đúng.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseTransactionStrategyTest {

    @Mock
    private GroupTransactionPaticipantValidator validator;

    @Mock
    private TransactionHelper helper;

    @Mock
    private MemberService memberService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ExpenseTransaction strategy;

    private UUID groupId;
    private UUID operatorId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        strategy = new ExpenseTransaction(validator, helper, memberService, eventPublisher);
    }

    @Test
    @DisplayName("Hỗ trợ đúng loại giao dịch EXPENSE")
    void testSupports_ReturnsTrueForExpense() {
        assertThat(strategy.supports(GTransactionType.EXPENSE)).isTrue();
        assertThat(strategy.supports(GTransactionType.CONTRIBUTION)).isFalse();
    }

    @Test
    @DisplayName("Owner tạo giao dịch -> Trạng thái CONFIRMED")
    void testDetermineStatus_Owner_ReturnsConfirmed() {
        MemberAuthInfo authInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, com.datn.financeapp.group.enums.MemberRole.OWNER, operatorId);
        assertThat(strategy.determineStatus(authInfo)).isEqualTo(GTransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Member thường tạo giao dịch -> Trạng thái PENDING")
    void testDetermineStatus_Member_ReturnsPending() {
        MemberAuthInfo authInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, com.datn.financeapp.group.enums.MemberRole.MEMBER, UUID.randomUUID());
        assertThat(strategy.determineStatus(authInfo)).isEqualTo(GTransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Xây dựng giao dịch chi tiêu thành công với việc chia tiền hợp lệ")
    void testBuild_Success() {
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GTransactionType.EXPENSE,
                MoneySource.FUND,
                1000000L,
                Instant.now(),
                null,
                categoryId,
                operatorId,
                "Ăn trưa",
                List.of(new GroupTransactionParticipantReq(operatorId, 1000000L))
        );

        when(helper.resolveParticipants(eq(1000000L), eq(req.participants()), any()))
                .thenReturn(List.of(TransactionParticipant.builder().userId(operatorId).shareAmount(1000000L).build()));

        GTransaction txn = strategy.build(operatorId, groupId, req, GTransactionStatus.CONFIRMED, true);

        assertThat(txn.getAmount()).isEqualTo(1000000L);
        assertThat(txn.getType()).isEqualTo(GTransactionType.EXPENSE);
        assertThat(txn.getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
        assertThat(txn.getParticipants()).hasSize(1);

        verify(validator).validTransactorAndParticipants(any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("Ném lỗi khi tổng chia tiền không khớp số tiền chi")
    void testBuild_ThrowsException_WhenShareMismatch() {
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GTransactionType.EXPENSE,
                MoneySource.FUND,
                1000000L,
                Instant.now(),
                null,
                categoryId,
                operatorId,
                "Ăn trưa",
                List.of(new GroupTransactionParticipantReq(operatorId, 500000L))
        );

        when(helper.resolveParticipants(eq(1000000L), eq(req.participants()), any()))
                .thenThrow(new BusinessException(ErrorCode.PARTICIPANTS_SUM_MISMATCH));

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, req, GTransactionStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PARTICIPANTS_SUM_MISMATCH.getCode());
    }
}
