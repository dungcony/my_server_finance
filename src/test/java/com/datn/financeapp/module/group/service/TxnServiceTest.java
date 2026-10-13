package com.datn.financeapp.module.group.service;

import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.entity.Fund;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.Group;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.GroupTxnHelper;
import com.datn.financeapp.module.group.mapper.GroupTxnMapper;
import com.datn.financeapp.module.group.repository.GroupRepository;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.impl.GroupTxnGetImpl;
import com.datn.financeapp.module.group.validator.GroupTransactionParticipantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.datn.financeapp.module.category.service.CategoryService;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lớp kiểm thử cho {@link GroupTxnGetImpl}: duyệt và xóa giao dịch nhóm với validator thật.
 * Việc tạo giao dịch nằm ở {@code GTransactionCreateImpl}, test tương ứng là {@code GTransactionCreateImplTest}.
 */
@ExtendWith(MockitoExtension.class)
class TxnServiceTest {

    @Mock
    private GroupTxnRepository transactionRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CategoryService categoryService;

    @Mock
    private MemberBalanceService memberBalanceService;

    @Mock
    private GroupTxnCache groupTxnCache;

    private GroupTxnGetImpl transactionService;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberId;
    private UUID fundId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        fundId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        GroupPermissionValidator permissionValidator = new GroupPermissionValidator(groupRepository, null, null);
        GroupTransactionParticipantValidator transactionValidator = new GroupTransactionParticipantValidator(memberService);
        GroupTxnMapper groupTxnMapper = Mappers.getMapper(GroupTxnMapper.class);
        GroupTxnHelper groupTxnHelper = new GroupTxnHelper(groupTxnMapper, categoryService);

        transactionService = new GroupTxnGetImpl(
                transactionRepository,
                memberBalanceService,
                groupTxnHelper,
                groupTxnCache,
                eventPublisher,
                permissionValidator,
                transactionValidator,
                List.of()
        );
    }

    @Test
    @DisplayName("Thủ quỹ xác nhận khoản chi PENDING -> chuyển CONFIRMED và trừ tiền quỹ")
    void testConfirm_PendingTransaction_AdjustsBalance() {
        Fund fund = Fund.builder().id(fundId).groupId(groupId).keepperId(ownerId).currentBalance(5000000L).build();
        MemberAuthInfo authInfo = new MemberAuthInfo(
                groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, ownerId
        );
        UUID txnId = UUID.randomUUID();

        GroupTxn txn = GroupTxn.builder()
                .id(txnId)
                .groupId(groupId)
                .type(GroupTxnType.EXPENSE)
                .moneySource(MoneySource.FUND)
                .amount(500000L)
                .status(GroupTxnStatus.PENDING)
                .build();

        when(groupRepository.findAuthInfo(groupId, ownerId)).thenReturn(Optional.of(authInfo));
        when(transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(txnId, groupId)).thenReturn(Optional.of(txn));

        GroupTxnDetailRes res = transactionService.confirm(ownerId, groupId, txnId);

        assertThat(res.status()).isEqualTo(GroupTxnStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Chủ nhóm xóa khoản chi CONFIRMED -> Hoàn tác số dư quỹ")
    void testDelete_ConfirmedTransaction_RollsBackBalance() {
        Group group = Group.builder().id(groupId).status(GroupStatus.ACTIVE).build();
        Fund fund = Fund.builder().id(fundId).groupId(groupId).keepperId(ownerId).currentBalance(5000000L).build();
        UUID txnId = UUID.randomUUID();

        GroupTxn txn = GroupTxn.builder()
                .id(txnId)
                .groupId(groupId)
                .type(GroupTxnType.EXPENSE)
                .moneySource(MoneySource.FUND)
                .amount(800000L)
                .status(GroupTxnStatus.CONFIRMED)
                .build();

        MemberAuthInfo authInfo = new MemberAuthInfo(
                groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, ownerId
        );
        when(groupRepository.findAuthInfo(groupId, ownerId)).thenReturn(Optional.of(authInfo));
        when(transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(txnId, groupId)).thenReturn(Optional.of(txn));

        transactionService.delete(ownerId, groupId, txnId);

        assertThat(txn.getDeletedAt()).isNotNull();
    }
}
