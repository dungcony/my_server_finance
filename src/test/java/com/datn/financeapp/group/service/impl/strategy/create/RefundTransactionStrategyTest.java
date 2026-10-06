package com.datn.financeapp.group.service.impl.strategy.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.*;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.MemberBalanceAccumulator;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.service.MemberBalanceService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lớp kiểm thử cho {@link RefundTransaction}.
 * Đảm bảo logic kiểm tra hạn mức hoàn trả (không vượt số còn lại của người nhận) hoạt động đúng.
 */
@ExtendWith(MockitoExtension.class)
class RefundTransactionStrategyTest {

    @Mock
    private MemberBalanceService memberBalanceService;

    private RefundTransaction strategy;

    private UUID groupId;
    private UUID operatorId;
    private UUID transactorId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        transactorId = UUID.randomUUID();
        strategy = new RefundTransaction(memberBalanceService);
    }

    @Test
    @DisplayName("Chỉ hỗ trợ loại giao dịch REFUND")
    void testSupports() {
        assertThat(strategy.supports(GTransactionType.REFUND)).isTrue();
        assertThat(strategy.supports(GTransactionType.EXPENSE)).isFalse();
        assertThat(strategy.supports(GTransactionType.CONTRIBUTION)).isFalse();
    }

    @Test
    @DisplayName("Chỉ Owner hoặc Treasurer mới có thể tạo trực tiếp thành CONFIRMED")
    void testDetermineStatus() {
        MemberAuthInfo ownerInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, MemberRole.OWNER, operatorId);
        assertThat(strategy.determineStatus(ownerInfo)).isEqualTo(GTransactionStatus.CONFIRMED);

        MemberAuthInfo memberInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, MemberRole.MEMBER, UUID.randomUUID());
        assertThatThrownBy(() -> strategy.determineStatus(memberInfo))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_TXN_TYPE_NOT_ALLOWED.getCode());
    }

    @Test
    @DisplayName("Xây dựng giao dịch REFUND thành công khi trong số còn lại của người nhận")
    void testBuild_Success() {
        stubOneContribution(2000000L);

        GTransaction txn = strategy.build(operatorId, groupId, refundReq(500000L), GTransactionStatus.CONFIRMED, true);

        assertThat(txn.getAmount()).isEqualTo(500000L);
        assertThat(txn.getType()).isEqualTo(GTransactionType.REFUND);
        assertThat(txn.getMoneySource()).isEqualTo(MoneySource.FUND);
        assertThat(txn.getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Ném lỗi khi REFUND vượt quá số còn lại của người nhận")
    void testBuild_ThrowsException_WhenAmountExceeds() {
        stubOneContribution(2000000L);

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, refundReq(2500000L),
                GTransactionStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE.getCode());
    }

    @Test
    @DisplayName("Nhóm tắt tính thừa thiếu vẫn chặn REFUND vượt số còn lại của người nhận")
    void testBuild_RefundExceedsBalance_SettlementDisabled_Throws() {
        stubOneContribution(2000000L);

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, refundReq(2500000L),
                GTransactionStatus.CONFIRMED, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE.getCode());
    }

    @Test
    @DisplayName("Nhóm tắt tính thừa thiếu: REFUND trong số còn lại của người nhận vẫn được tạo")
    void testBuild_RefundWithinBalance_SettlementDisabled_Success() {
        stubOneContribution(2000000L);

        GTransaction txn = strategy.build(operatorId, groupId, refundReq(500000L),
                GTransactionStatus.CONFIRMED, false);

        assertThat(txn.getAmount()).isEqualTo(500000L);
        assertThat(txn.getType()).isEqualTo(GTransactionType.REFUND);
    }

    @Test
    @DisplayName("Nguồn tiền không phải FUND thì bị từ chối")
    void testBuild_ThrowsException_WhenMoneySourceNotFund() {
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(GTransactionType.REFUND, MoneySource.PERSONAL,
                100000L, Instant.now(), null, null, transactorId, "Sai nguồn", List.of());

        assertThatThrownBy(() -> strategy.build(operatorId, groupId, req, GTransactionStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID.getCode());
    }

    // số dư của người nhận chỉ gồm một khoản góp, tương đương lịch sử một khoản CONTRIBUTION đã duyệt
    private void stubOneContribution(long amount) {
        MemberBalanceAccumulator acc = new MemberBalanceAccumulator();
        acc.addContribution(amount);
        when(memberBalanceService.getBalancesForUpdate(groupId, List.of(transactorId)))
                .thenReturn(new MemberBalances(Map.of(transactorId, acc)));
    }

    private GroupTransactionCreateReq refundReq(long amount) {
        return new GroupTransactionCreateReq(GTransactionType.REFUND, MoneySource.FUND, amount, Instant.now(), null,
                null, transactorId, "Hoàn tiền", List.of());
    }
}
