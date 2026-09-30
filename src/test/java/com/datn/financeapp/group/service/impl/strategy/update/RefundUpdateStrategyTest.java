package com.datn.financeapp.group.service.impl.strategy.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử {@link RefundUpdateStrategy}: sửa REFUND phải kiểm lại hạn mức trên số dư hiện tại,
 * bỏ chính khoản đang sửa ra khỏi phép tính, và chỉ kiểm khi số tiền tăng hoặc đổi người nhận.
 */
@ExtendWith(MockitoExtension.class)
class RefundUpdateStrategyTest {

    @Mock
    private GroupTransactionPaticipantValidator transactionValidator;
    @Mock
    private GroupTransactionRepository transactionRepository;
    @Mock
    private MemberRepository memberRepository;

    private RefundUpdateStrategy strategy;

    private UUID groupId;
    private UUID treasurerId;
    private UUID userA;
    private UUID userB;
    private Instant joinedAt;

    @BeforeEach
    void setUp() {
        strategy = new RefundUpdateStrategy(transactionValidator, transactionRepository, memberRepository);
        groupId = UUID.randomUUID();
        treasurerId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        joinedAt = Instant.now().minusSeconds(3600);
        lenient().when(memberRepository.findByGroupIdAndStatusNotOrderByJoinedAtDesc(groupId, MemberStatus.PENDING))
                .thenReturn(List.of(member(userA), member(userB), member(treasurerId)));
    }

    @Test
    @DisplayName("REFUND 200k khi A còn 200k: sửa lên 300k vẫn hợp lệ vì khoản này đã được trừ sẵn trong số dư")
    void update_RefundRaisedWithinBalanceExcludingItself_Succeeds() {
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contribution, refund);

        assertThatCode(() -> strategy.update(refund, treasurerId, groupId, updateReq(300_000L, userA),
                treasurerAuth())).doesNotThrowAnyException();

        assertThat(refund.getAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("REFUND 200k khi A còn 200k: sửa lên 500k vượt phần của A (400k nếu chưa có khoản này) thì bị từ chối")
    void update_RefundRaisedBeyondBalanceExcludingItself_ShouldBeRejected() {
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contribution, refund);

        assertThatThrownBy(() -> strategy.update(refund, treasurerId, groupId, updateReq(500_000L, userA),
                treasurerAuth()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CANNOT_REFUND_EXCEED_BALANCE.getCode()));

        assertThat(refund.getAmount()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("Nhóm tắt tính thừa thiếu vẫn chặn sửa REFUND vượt số còn lại của người nhận")
    void update_RefundRaisedBeyondBalance_SettlementDisabled_ShouldBeRejected() {
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contribution, refund);
        MemberAuthInfo settlementDisabled = new MemberAuthInfo(groupId, treasurerId, GroupStatus.ACTIVE, false,
                MemberStatus.ACTIVE, MemberRole.MEMBER, treasurerId);

        assertThatThrownBy(() -> strategy.update(refund, treasurerId, groupId, updateReq(500_000L, userA),
                settlementDisabled))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CANNOT_REFUND_EXCEED_BALANCE.getCode()));
    }

    @Test
    @DisplayName("Đổi người nhận REFUND sang B chỉ còn 100k thì bị từ chối")
    void update_RefundMovedToUserWithLowBalance_ShouldBeRejected() {
        GTransaction contributionA = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GTransaction contributionB = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userB, 100_000L);
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contributionA, contributionB, refund);

        assertThatThrownBy(() -> strategy.update(refund, treasurerId, groupId, updateReq(200_000L, userB),
                treasurerAuth()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Số dư của A đã tụt âm vì chi tiêu: chỉ sửa ghi chú (giữ nguyên số tiền) vẫn được, không kiểm lại hạn mức")
    void update_RefundSameAmountAfterBalanceDropped_Succeeds() {
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 200_000L);
        GTransaction expense = txn(TransactionType.EXPENSE, MoneySource.FUND, userA, 500_000L);
        expense.getParticipants().add(TransactionParticipant.builder().userId(userA).build());
        stubAllTxns(contribution, refund, expense);

        assertThatCode(() -> strategy.update(refund, treasurerId, groupId, updateReq(200_000L, userA),
                treasurerAuth())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Giảm số tiền REFUND luôn được phép, kể cả khi số dư của A đã âm")
    void update_RefundLowered_AlwaysSucceeds() {
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 200_000L);
        GTransaction expense = txn(TransactionType.EXPENSE, MoneySource.FUND, userA, 500_000L);
        expense.getParticipants().add(TransactionParticipant.builder().userId(userA).build());
        stubAllTxns(contribution, refund, expense);

        assertThatCode(() -> strategy.update(refund, treasurerId, groupId, updateReq(100_000L, userA),
                treasurerAuth())).doesNotThrowAnyException();

        assertThat(refund.getAmount()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("REFUND trả lại 400k tiền góp 1tr: sửa lên 900k được, lên 1,2tr vượt số còn lại thì bị từ chối")
    void update_RefundReturningContribution_RespectsBalanceExcludingItself() {
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 1_000_000L);
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 400_000L);
        stubAllTxns(contribution, refund);

        assertThatCode(() -> strategy.update(refund, treasurerId, groupId, updateReq(900_000L, userA),
                treasurerAuth())).doesNotThrowAnyException();

        // đặt lại số tiền cũ để thử ca vượt hạn mức
        refund.setAmount(400_000L);
        assertThatThrownBy(() -> strategy.update(refund, treasurerId, groupId, updateReq(1_200_000L, userA),
                treasurerAuth()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CANNOT_REFUND_EXCEED_BALANCE.getCode()));
    }

    @Test
    @DisplayName("Thủ quỹ sửa REFUND vẫn giữ trạng thái CONFIRMED và nguồn tiền FUND")
    void update_Refund_StaysConfirmedFromFund() {
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 400_000L);
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 200_000L);
        stubAllTxns(contribution, refund);

        strategy.update(refund, treasurerId, groupId, updateReq(200_000L, userA), treasurerAuth());

        assertThat(refund.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(refund.getMoneySource()).isEqualTo(MoneySource.FUND);
    }

    // dùng lenient vì ca giảm / giữ nguyên số tiền không cần đọc lịch sử
    private void stubAllTxns(GTransaction... txns) {
        lenient().when(transactionRepository
                .findByGroupIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(groupId))
                .thenReturn(List.of(txns));
    }

    private Member member(UUID userId) {
        return Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userId)
                .role(MemberRole.MEMBER).status(MemberStatus.ACTIVE).joinedAt(joinedAt).build();
    }

    private GTransaction txn(TransactionType type, MoneySource source, UUID transactorId, long amount) {
        Instant now = Instant.now().minusSeconds(60);
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .type(type)
                .moneySource(source)
                .transactorId(transactorId)
                .createdBy(treasurerId)
                .status(TransactionStatus.CONFIRMED)
                .amount(amount)
                .participants(new ArrayList<>())
                .occurredAt(now)
                .createdAt(now)
                .build();
    }

    private GroupTransactionUpdateReq updateReq(long amount, UUID userId) {
        return new GroupTransactionUpdateReq(amount, null, null, null, null, userId, null, null);
    }

    private MemberAuthInfo treasurerAuth() {
        return new MemberAuthInfo(groupId, treasurerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE,
                MemberRole.MEMBER, treasurerId);
    }
}
