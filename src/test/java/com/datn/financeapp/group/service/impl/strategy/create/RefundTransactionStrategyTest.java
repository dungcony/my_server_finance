package com.datn.financeapp.group.service.impl.strategy.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.MemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lớp kiểm thử cho {@link RefundTransactionStrategy}.
 * Đảm bảo logic kiểm tra hạn mức hoàn trả (không vượt số còn lại của người nhận) hoạt động đúng.
 */
@ExtendWith(MockitoExtension.class)
class RefundTransactionStrategyTest {

    @Mock
    private GroupTransactionRepository transactionRepository;

    @Mock
    private MemberRepository memberRepository;

    private RefundTransactionStrategy strategy;

    private UUID groupId;
    private UUID operatorId;
    private UUID transactorId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        transactorId = UUID.randomUUID();
        strategy = new RefundTransactionStrategy(transactionRepository, memberRepository);
    }

    @Test
    @DisplayName("Chỉ hỗ trợ loại giao dịch REFUND")
    void testSupports() {
        assertThat(strategy.supports(TransactionType.REFUND)).isTrue();
        assertThat(strategy.supports(TransactionType.EXPENSE)).isFalse();
        assertThat(strategy.supports(TransactionType.CONTRIBUTION)).isFalse();
    }

    @Test
    @DisplayName("Chỉ Owner hoặc Treasurer mới có thể tạo trực tiếp thành CONFIRMED")
    void testDetermineStatus() {
        MemberAuthInfo ownerInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, MemberRole.OWNER, operatorId);
        assertThat(strategy.determineStatus(ownerInfo)).isEqualTo(TransactionStatus.CONFIRMED);

        MemberAuthInfo memberInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, MemberRole.MEMBER, UUID.randomUUID());
        assertThatThrownBy(() -> strategy.determineStatus(memberInfo))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED.getCode());
    }

    @Test
    @DisplayName("Xây dựng giao dịch REFUND thành công khi trong số còn lại của người nhận")
    void testBuild_Success() {
        stubOneContribution(2000000L);

        GTransaction txn = strategy.build(operatorId, groupId, refundReq(500000L), TransactionStatus.CONFIRMED, true);

        assertThat(txn.getAmount()).isEqualTo(500000L);
        assertThat(txn.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(txn.getMoneySource()).isEqualTo(MoneySource.FUND);
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Ném lỗi khi REFUND vượt quá số còn lại của người nhận")
    void testBuild_ThrowsException_WhenAmountExceeds() {
        stubOneContribution(2000000L);

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, refundReq(2500000L),
                TransactionStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CANNOT_REFUND_EXCEED_BALANCE.getCode());
    }

    @Test
    @DisplayName("Nhóm tắt tính thừa thiếu vẫn chặn REFUND vượt số còn lại của người nhận")
    void testBuild_RefundExceedsBalance_SettlementDisabled_Throws() {
        stubOneContribution(2000000L);

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, refundReq(2500000L),
                TransactionStatus.CONFIRMED, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CANNOT_REFUND_EXCEED_BALANCE.getCode());
    }

    @Test
    @DisplayName("Nhóm tắt tính thừa thiếu: REFUND trong số còn lại của người nhận vẫn được tạo")
    void testBuild_RefundWithinBalance_SettlementDisabled_Success() {
        stubOneContribution(2000000L);

        GTransaction txn = strategy.build(operatorId, groupId, refundReq(500000L),
                TransactionStatus.CONFIRMED, false);

        assertThat(txn.getAmount()).isEqualTo(500000L);
        assertThat(txn.getType()).isEqualTo(TransactionType.REFUND);
    }

    @Test
    @DisplayName("Nguồn tiền không phải FUND thì bị từ chối")
    void testBuild_ThrowsException_WhenMoneySourceNotFund() {
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(TransactionType.REFUND, MoneySource.PERSONAL,
                100000L, Instant.now(), null, null, transactorId, "Sai nguồn", List.of());

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, req, TransactionStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.MONEY_SOURCE_INVALID.getCode());
    }

    // dựng lịch sử một khoản góp của người nhận để có số còn lại trong quỹ
    private void stubOneContribution(long amount) {
        GTransaction contribution = GTransaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.CONTRIBUTION)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(transactorId)
                .amount(amount)
                .status(TransactionStatus.CONFIRMED)
                .participants(List.of())
                .build();
        when(transactionRepository.findByGroupIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(groupId))
                .thenReturn(List.of(contribution));
        Member member = Member.builder().userId(transactorId).status(MemberStatus.ACTIVE).build();
        when(memberRepository.findByGroupIdAndStatusNotOrderByJoinedAtDesc(groupId, MemberStatus.PENDING))
                .thenReturn(List.of(member));
    }

    private GroupTransactionCreateReq refundReq(long amount) {
        return new GroupTransactionCreateReq(TransactionType.REFUND, MoneySource.FUND, amount, Instant.now(), null,
                null, transactorId, "Hoàn tiền", List.of());
    }
}
