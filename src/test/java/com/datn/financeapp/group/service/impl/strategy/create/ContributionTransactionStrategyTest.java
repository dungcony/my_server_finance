package com.datn.financeapp.group.service.impl.strategy.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lớp kiểm thử cho {@link ContributionTransactionStrategy}.
 * Đảm bảo các logic tạo giao dịch đóng góp quỹ được thực thi đúng.
 */
class ContributionTransactionStrategyTest {

    private ContributionTransactionStrategy strategy;

    private UUID groupId;
    private UUID operatorId;
    private UUID transactorId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        transactorId = UUID.randomUUID();
        strategy = new ContributionTransactionStrategy();
    }

    @Test
    @DisplayName("Hỗ trợ đúng loại giao dịch CONTRIBUTION")
    void testSupports_ReturnsTrueForContribution() {
        assertThat(strategy.supports(TransactionType.CONTRIBUTION)).isTrue();
        assertThat(strategy.supports(TransactionType.EXPENSE)).isFalse();
    }

    @Test
    @DisplayName("Owner tạo giao dịch -> Trạng thái CONFIRMED")
    void testDetermineStatus_Owner_ReturnsConfirmed() {
        MemberAuthInfo authInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, com.datn.financeapp.group.enums.MemberRole.OWNER, operatorId);
        assertThat(strategy.determineStatus(authInfo)).isEqualTo(TransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Member thường tạo giao dịch -> Trạng thái PENDING")
    void testDetermineStatus_Member_ReturnsPending() {
        MemberAuthInfo authInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, com.datn.financeapp.group.enums.MemberRole.MEMBER, UUID.randomUUID());
        assertThat(strategy.determineStatus(authInfo)).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Xây dựng giao dịch đóng góp quỹ thành công")
    void testBuild_Success() {
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                TransactionType.CONTRIBUTION,
                MoneySource.PERSONAL,
                1000000L,
                Instant.now(),
                null,
                null,
                transactorId,
                "Đóng quỹ",
                List.of()
        );

        GTransaction txn = strategy.build(operatorId, groupId, req, TransactionStatus.CONFIRMED, true);

        assertThat(txn.getAmount()).isEqualTo(1000000L);
        assertThat(txn.getType()).isEqualTo(TransactionType.CONTRIBUTION);
        assertThat(txn.getMoneySource()).isEqualTo(MoneySource.PERSONAL);
        assertThat(txn.getTransactorId()).isEqualTo(transactorId);
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(txn.getParticipants()).isEmpty();
    }

    @Test
    @DisplayName("Ném lỗi khi nguồn tiền không phải là PERSONAL")
    void testBuild_ThrowsException_WhenMoneySourceNotPersonal() {
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                TransactionType.CONTRIBUTION,
                MoneySource.FUND,
                1000000L,
                Instant.now(),
                null,
                null,
                transactorId,
                "Đóng quỹ",
                List.of()
        );

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, req, TransactionStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.MONEY_SOURCE_INVALID.getCode());
    }

    @Test
    @DisplayName("Ném lỗi khi có người tham gia (participants) được truyền vào")
    void testBuild_ThrowsException_WhenParticipantsNotEmpty() {
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                TransactionType.CONTRIBUTION,
                MoneySource.PERSONAL,
                1000000L,
                Instant.now(),
                null,
                null,
                transactorId,
                "Đóng quỹ",
                List.of(new GroupTransactionParticipantReq(UUID.randomUUID(), 100000L))
        );

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, req, TransactionStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PARTICIPANTS_NOT_ALLOWED.getCode());
    }
}
