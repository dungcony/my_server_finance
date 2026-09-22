package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.category.service.CategoryService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.service.GroupBalanceService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.request.transaction.GroupWithdrawalReq;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.entity.GroupWallet;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.GroupWalletStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.helper.GroupSplitHelper;
import com.datn.financeapp.group.mapper.GroupTransactionMapper;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.GroupTransactionParticipantRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.GroupWalletRepository;
import com.datn.financeapp.group.service.impl.GroupTransactionServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupTransactionServiceTest {

    @Mock
    private GroupTransactionRepository transactionRepository;

    @Mock
    private GroupTransactionParticipantRepository participantRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupWalletRepository groupWalletRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private GroupBalanceService balanceService;

    private GroupTransactionServiceImpl transactionService;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberId;
    private UUID walletId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        GroupPermissionValidator permissionValidator = new GroupPermissionValidator(groupMemberRepository, groupRepository);
        GroupTransactionMapper transactionMapper = Mappers.getMapper(GroupTransactionMapper.class);
        GroupSplitHelper splitHelper = new GroupSplitHelper();

        transactionService = new GroupTransactionServiceImpl(
                transactionRepository,
                participantRepository,
                groupMemberRepository,
                groupWalletRepository,
                categoryService,
                balanceService,
                permissionValidator,
                transactionMapper,
                splitHelper
        );
    }

    @Test
    @DisplayName("Chủ nhóm ghi khoản chi quỹ -> tự động CONFIRMED và trừ tiền quỹ")
    void testCreate_ExpenseByOwner_DirectConfirmed() {
        Group group = Group.builder().id(groupId).status(GroupStatus.ACTIVE).isSettlementEnabled(true).build();
        GroupWallet wallet = GroupWallet.builder().id(walletId).groupId(groupId).heldByUserId(ownerId).status(GroupWalletStatus.ACTIVE).currentBalance(5000000L).build();
        CategoryRefResponse cat = new CategoryRefResponse(categoryId, "Ăn uống", "expense", "#FF0000", null, null);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, ownerId, MemberStatus.ACTIVE)).thenReturn(true);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupWalletRepository.findFirstByGroupIdAndStatus(groupId, GroupWalletStatus.ACTIVE)).thenReturn(Optional.of(wallet));
        when(categoryService.validateSystemExpenseCategory(categoryId)).thenReturn(cat);
        when(groupMemberRepository.findMemberUserIdsAtOccurredAt(eq(groupId), any())).thenReturn(List.of(ownerId, memberId));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(groupId, ownerId, GroupRole.OWNER, MemberStatus.ACTIVE)).thenReturn(true);
        when(groupWalletRepository.findByGroupIdForUpdate(groupId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(GroupTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GroupTransactionType.EXPENSE,
                MoneySource.FUND,
                1000000L,
                Instant.now(),
                null,
                categoryId,
                null,
                ownerId,
                "Ăn trưa",
                null,
                List.of()
        );

        GroupTransactionDetailRes res = transactionService.create(ownerId, groupId, req);

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(GroupTransactionStatus.CONFIRMED);
        assertThat(res.reviewedBy()).isEqualTo(ownerId);
        verify(groupWalletRepository).adjustBalance(walletId, -1000000L);
    }

    @Test
    @DisplayName("Thành viên thường ghi khoản chi -> PENDING và quỹ không đổi")
    void testCreate_ExpenseByMember_Pending() {
        Group group = Group.builder().id(groupId).status(GroupStatus.ACTIVE).isSettlementEnabled(true).build();
        GroupWallet wallet = GroupWallet.builder().id(walletId).groupId(groupId).heldByUserId(ownerId).status(GroupWalletStatus.ACTIVE).currentBalance(5000000L).build();
        CategoryRefResponse cat = new CategoryRefResponse(categoryId, "Ăn uống", "expense", "#FF0000", null, null);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, memberId, MemberStatus.ACTIVE)).thenReturn(true);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupWalletRepository.findFirstByGroupIdAndStatus(groupId, GroupWalletStatus.ACTIVE)).thenReturn(Optional.of(wallet));
        when(categoryService.validateSystemExpenseCategory(categoryId)).thenReturn(cat);
        when(groupMemberRepository.findMemberUserIdsAtOccurredAt(eq(groupId), any())).thenReturn(List.of(ownerId, memberId));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(groupId, memberId, GroupRole.OWNER, MemberStatus.ACTIVE)).thenReturn(false);
        when(transactionRepository.save(any(GroupTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GroupTransactionType.EXPENSE,
                MoneySource.FUND,
                500000L,
                Instant.now(),
                null,
                categoryId,
                null,
                memberId,
                "Mua nước",
                null,
                List.of()
        );

        GroupTransactionDetailRes res = transactionService.create(memberId, groupId, req);

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(GroupTransactionStatus.PENDING);
        assertThat(res.reviewedBy()).isNull();
    }

    @Test
    @DisplayName("Thủ quỹ xác nhận khoản chi PENDING -> chuyển CONFIRMED và trừ tiền quỹ")
    void testConfirm_PendingTransaction_AdjustsBalance() {
        Group group = Group.builder().id(groupId).status(GroupStatus.ACTIVE).build();
        GroupWallet wallet = GroupWallet.builder().id(walletId).groupId(groupId).heldByUserId(ownerId).status(GroupWalletStatus.ACTIVE).currentBalance(5000000L).build();
        UUID txnId = UUID.randomUUID();

        GroupTransaction txn = GroupTransaction.builder()
                .id(txnId)
                .groupId(groupId)
                .type(GroupTransactionType.EXPENSE)
                .moneySource(MoneySource.FUND)
                .amount(500000L)
                .status(GroupTransactionStatus.PENDING)
                .build();

        when(groupWalletRepository.findFirstByGroupIdAndStatus(groupId, GroupWalletStatus.ACTIVE)).thenReturn(Optional.of(wallet));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(groupId, ownerId, GroupRole.OWNER, MemberStatus.ACTIVE)).thenReturn(true);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(txnId, groupId)).thenReturn(Optional.of(txn));
        when(groupWalletRepository.findByGroupIdForUpdate(groupId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(GroupTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupTransactionDetailRes res = transactionService.confirm(ownerId, groupId, txnId);

        assertThat(res.status()).isEqualTo(GroupTransactionStatus.CONFIRMED);
        assertThat(res.reviewedBy()).isEqualTo(ownerId);
        verify(groupWalletRepository).adjustBalance(walletId, -500000L);
    }

    @Test
    @DisplayName("Rút tiền vượt quá số tiền đã góp -> Báo lỗi AMOUNT_EXCEEDS_CONTRIBUTION")
    void testCreateWithdrawal_ExceedsContribution_ThrowsException() {
        Group group = Group.builder().id(groupId).status(GroupStatus.ACTIVE).isSettlementEnabled(true).build();
        GroupWallet wallet = GroupWallet.builder().id(walletId).groupId(groupId).heldByUserId(ownerId).status(GroupWalletStatus.ACTIVE).currentBalance(5000000L).build();

        when(groupWalletRepository.findFirstByGroupIdAndStatus(groupId, GroupWalletStatus.ACTIVE)).thenReturn(Optional.of(wallet));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(groupId, ownerId, GroupRole.OWNER, MemberStatus.ACTIVE)).thenReturn(true);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findMemberUserIdsAtOccurredAt(eq(groupId), any())).thenReturn(List.of(ownerId, memberId));
        when(balanceService.getRemainingContribution(groupId, memberId, null)).thenReturn(200000L);

        GroupWithdrawalReq req = new GroupWithdrawalReq(memberId, 500000L, "Rút tiền góp", Instant.now());

        assertThatThrownBy(() -> transactionService.createWithdrawal(ownerId, groupId, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AMOUNT_EXCEEDS_CONTRIBUTION.getCode());
    }

    @Test
    @DisplayName("Chủ nhóm xóa khoản chi CONFIRMED -> Hoàn tác số dư quỹ")
    void testDelete_ConfirmedTransaction_RollsBackBalance() {
        Group group = Group.builder().id(groupId).status(GroupStatus.ACTIVE).build();
        GroupWallet wallet = GroupWallet.builder().id(walletId).groupId(groupId).heldByUserId(ownerId).status(GroupWalletStatus.ACTIVE).currentBalance(5000000L).build();
        UUID txnId = UUID.randomUUID();

        GroupTransaction txn = GroupTransaction.builder()
                .id(txnId)
                .groupId(groupId)
                .type(GroupTransactionType.EXPENSE)
                .moneySource(MoneySource.FUND)
                .amount(800000L)
                .status(GroupTransactionStatus.CONFIRMED)
                .build();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, ownerId, MemberStatus.ACTIVE)).thenReturn(true);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupWalletRepository.findFirstByGroupIdAndStatus(groupId, GroupWalletStatus.ACTIVE)).thenReturn(Optional.of(wallet));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(groupId, ownerId, GroupRole.OWNER, MemberStatus.ACTIVE)).thenReturn(true);
        when(transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(txnId, groupId)).thenReturn(Optional.of(txn));
        when(groupWalletRepository.findByGroupIdForUpdate(groupId)).thenReturn(Optional.of(wallet));

        transactionService.delete(ownerId, groupId, txnId);

        assertThat(txn.getDeletedAt()).isNotNull();
        verify(groupWalletRepository).adjustBalance(walletId, 800000L);
    }
}
