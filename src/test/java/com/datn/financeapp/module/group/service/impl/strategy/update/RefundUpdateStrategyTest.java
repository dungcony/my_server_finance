package com.datn.financeapp.module.group.service.impl.strategy.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnUpdateReq;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.GroupTxnParticipant;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.helper.BalanceCalculator;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import com.datn.financeapp.module.group.validator.GroupTransactionParticipantValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử {@link RefundUpdate}: sửa REFUND phải kiểm lại hạn mức trên số dư hiện tại,
 * bỏ chính khoản đang sửa ra khỏi phép tính, và chỉ kiểm khi số tiền tăng hoặc đổi người nhận.
 */
@ExtendWith(MockitoExtension.class)
class RefundUpdateStrategyTest {

    @Mock
    private GroupTransactionParticipantValidator transactionValidator;
    @Mock
    private MemberBalanceService memberBalanceService;

    private RefundUpdate strategy;

    private UUID groupId;
    private UUID treasurerId;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        strategy = new RefundUpdate(transactionValidator, memberBalanceService);
        groupId = UUID.randomUUID();
        treasurerId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
    }

    @Test
    @DisplayName("REFUND 200k khi A còn 200k: sửa lên 300k vẫn hợp lệ vì khoản này đã được trừ sẵn trong số dư")
    void update_RefundRaisedWithinBalanceExcludingItself_Succeeds() {
        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contribution, refund);

        assertThatCode(() -> strategy.update(refund, treasurerId, groupId, updateReq(300_000L, userA),
                treasurerAuth())).doesNotThrowAnyException();

        assertThat(refund.getAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("REFUND 200k khi A còn 200k: sửa lên 500k vượt phần của A (400k nếu chưa có khoản này) thì bị từ chối")
    void update_RefundRaisedBeyondBalanceExcludingItself_ShouldBeRejected() {
        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contribution, refund);

        assertThatThrownBy(() -> strategy.update(refund, treasurerId, groupId, updateReq(500_000L, userA),
                treasurerAuth()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE.getCode()));

        assertThat(refund.getAmount()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("Nhóm tắt tính thừa thiếu vẫn chặn sửa REFUND vượt số còn lại của người nhận")
    void update_RefundRaisedBeyondBalance_SettlementDisabled_ShouldBeRejected() {
        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contribution, refund);
        MemberAuthInfo settlementDisabled = new MemberAuthInfo(groupId, treasurerId, GroupStatus.ACTIVE, false,
                MemberStatus.ACTIVE, MemberRole.MEMBER, treasurerId);

        assertThatThrownBy(() -> strategy.update(refund, treasurerId, groupId, updateReq(500_000L, userA),
                settlementDisabled))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE.getCode()));
    }

    @Test
    @DisplayName("Đổi người nhận REFUND sang B chỉ còn 100k thì bị từ chối")
    void update_RefundMovedToUserWithLowBalance_ShouldBeRejected() {
        GroupTxn contributionA = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GroupTxn contributionB = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userB, 100_000L);
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contributionA, contributionB, refund);

        assertThatThrownBy(() -> strategy.update(refund, treasurerId, groupId, updateReq(200_000L, userB),
                treasurerAuth()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Số dư của A đã tụt âm vì chi tiêu: chỉ sửa ghi chú (giữ nguyên số tiền) vẫn được, không kiểm lại hạn mức")
    void update_RefundSameAmountAfterBalanceDropped_Succeeds() {
        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 200_000L);
        GroupTxn expense = txn(GroupTxnType.EXPENSE, MoneySource.FUND, userA, 500_000L);
        expense.getParticipants().add(GroupTxnParticipant.builder().userId(userA).build());
        stubAllTxns(contribution, refund, expense);

        assertThatCode(() -> strategy.update(refund, treasurerId, groupId, updateReq(200_000L, userA),
                treasurerAuth())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Giảm số tiền REFUND luôn được phép, kể cả khi số dư của A đã âm")
    void update_RefundLowered_AlwaysSucceeds() {
        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 200_000L);
        GroupTxn expense = txn(GroupTxnType.EXPENSE, MoneySource.FUND, userA, 500_000L);
        expense.getParticipants().add(GroupTxnParticipant.builder().userId(userA).build());
        stubAllTxns(contribution, refund, expense);

        assertThatCode(() -> strategy.update(refund, treasurerId, groupId, updateReq(100_000L, userA),
                treasurerAuth())).doesNotThrowAnyException();

        assertThat(refund.getAmount()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("REFUND trả lại 400k tiền góp 1tr: sửa lên 900k được, lên 1,2tr vượt số còn lại thì bị từ chối")
    void update_RefundReturningContribution_RespectsBalanceExcludingItself() {
        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 1_000_000L);
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 400_000L);
        stubAllTxns(contribution, refund);

        assertThatCode(() -> strategy.update(refund, treasurerId, groupId, updateReq(900_000L, userA),
                treasurerAuth())).doesNotThrowAnyException();

        // đặt lại số tiền cũ để thử ca vượt hạn mức
        refund.setAmount(400_000L);
        assertThatThrownBy(() -> strategy.update(refund, treasurerId, groupId, updateReq(1_200_000L, userA),
                treasurerAuth()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE.getCode()));
    }

    @Test
    @DisplayName("Thủ quỹ sửa REFUND vẫn giữ trạng thái CONFIRMED và nguồn tiền FUND")
    void update_Refund_StaysConfirmedFromFund() {
        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contribution, refund);

        strategy.update(refund, treasurerId, groupId, updateReq(200_000L, userA), treasurerAuth());

        assertThat(refund.getStatus()).isEqualTo(GroupTxnStatus.CONFIRMED);
        assertThat(refund.getMoneySource()).isEqualTo(MoneySource.FUND);
    }

    // bảng tổng hợp giả lập bằng tổng ảnh hưởng của chính các giao dịch trong lịch sử; tính lúc được gọi để
    // phản ánh đúng số tiền hiện tại của giao dịch, và dùng lenient vì ca giảm / giữ nguyên số tiền không đọc số dư
    private void stubAllTxns(GroupTxn... txns) {
        lenient().when(memberBalanceService.getBalancesForUpdate(eq(groupId), any()))
                .thenAnswer(invocation -> {
                    MemberBalances total = new MemberBalances(Map.of());
                    for (GroupTxn t : txns)
                        total = total.plus(BalanceCalculator.effectOf(t));
                    return total;
                });
    }

    private GroupTxn txn(GroupTxnType type, MoneySource source, UUID transactorId, long amount) {
        Instant now = Instant.now().minusSeconds(60);
        return GroupTxn.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .type(type)
                .moneySource(source)
                .transactorId(transactorId)
                .createdBy(treasurerId)
                .status(GroupTxnStatus.CONFIRMED)
                .amount(amount)
                .participants(new ArrayList<>())
                .occurredAt(now)
                .createdAt(now)
                .build();
    }

    private GroupTxnUpdateReq updateReq(long amount, UUID userId) {
        return new GroupTxnUpdateReq(amount, null, null, null, null, userId, null, null);
    }

    private MemberAuthInfo treasurerAuth() {
        return new MemberAuthInfo(groupId, treasurerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE,
                MemberRole.MEMBER, treasurerId);
    }
}
