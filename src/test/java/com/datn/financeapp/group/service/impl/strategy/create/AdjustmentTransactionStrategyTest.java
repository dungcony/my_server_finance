package com.datn.financeapp.group.service.impl.strategy.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.service.MemberService;

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
 * Lớp kiểm thử cho {@link AdjustmentTransactionStrategy}.
 * Đảm bảo giao dịch điều chỉnh quỹ chia đều chênh lệch cho thành viên hợp lệ.
 */
@ExtendWith(MockitoExtension.class)
class AdjustmentTransactionStrategyTest {

    @Mock
    private MemberService memberService;

    @Mock
    private TransactionHelper transactionHelper;

    private AdjustmentTransactionStrategy strategy;

    private UUID groupId;
    private UUID operatorId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        strategy = new AdjustmentTransactionStrategy(memberService, transactionHelper);
    }

    @Test
    @DisplayName("Hỗ trợ đúng loại giao dịch ADJUSTMENT_UP và ADJUSTMENT_DOWN")
    void testSupports() {
        assertThat(strategy.supports(GTransactionType.ADJUSTMENT_UP)).isTrue();
        assertThat(strategy.supports(GTransactionType.ADJUSTMENT_DOWN)).isTrue();
        assertThat(strategy.supports(GTransactionType.EXPENSE)).isFalse();
    }

    @Test
    @DisplayName("Chỉ Owner hoặc Treasurer mới có thể tạo trực tiếp thành CONFIRMED")
    void testDetermineStatus() {
        MemberAuthInfo ownerInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, com.datn.financeapp.group.enums.MemberRole.OWNER, operatorId);
        assertThat(strategy.determineStatus(ownerInfo)).isEqualTo(GTransactionStatus.CONFIRMED);

        MemberAuthInfo memberInfo = new MemberAuthInfo(groupId, operatorId, null, true, null, com.datn.financeapp.group.enums.MemberRole.MEMBER, UUID.randomUUID());
        assertThat(strategy.determineStatus(memberInfo)).isEqualTo(GTransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Xây dựng giao dịch ADJUSTMENT thành công và chia đều số tiền")
    void testBuild_Success() {
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GTransactionType.ADJUSTMENT_UP,
                MoneySource.FUND,
                600000L,
                Instant.now(),
                null,
                null,
                operatorId,
                "Chênh lệch kiểm kê",
                List.of()
        );

        List<UUID> memberIds = List.of(operatorId, UUID.randomUUID());
        when(memberService.findIdAllMember(groupId)).thenReturn(memberIds);


        GTransaction txn = strategy.build(operatorId, groupId, req, GTransactionStatus.CONFIRMED, true);

        assertThat(txn.getAmount()).isEqualTo(600000L);
        assertThat(txn.getType()).isEqualTo(GTransactionType.ADJUSTMENT_UP);
        assertThat(txn.getMoneySource()).isEqualTo(MoneySource.FUND);
        assertThat(txn.getParticipants()).hasSize(2);
    }
}
