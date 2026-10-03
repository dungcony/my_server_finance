package com.datn.financeapp.group.service;

import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.entity.Fund;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.*;
import com.datn.financeapp.group.helper.GTransactionBuilder;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.mapper.GTransactionMapper;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.service.impl.GTransactionServiceImpl;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Lớp kiểm thử cho {@link GTransactionServiceImpl}.
 * Chịu trách nhiệm kiểm tra việc định tuyến (routing) tạo, sửa, xóa giao dịch.
 * Do kiến trúc đã áp dụng Strategy Pattern, việc kiểm tra chi tiết logic tạo/sửa
 * của từng loại giao dịch sẽ nằm ở các class Test của từng Strategy tương ứng.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private GroupTransactionRepository transactionRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GTransactionBuilder expenseStrategy;

    @Mock
    private GTransactionBuilder refundStrategy;

    private GTransactionServiceImpl transactionService;

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

        GroupPermissionValidator permissionValidator = new GroupPermissionValidator(groupRepository);
        GroupTransactionPaticipantValidator transactionValidator = new GroupTransactionPaticipantValidator(memberService);
        GTransactionMapper gTransactionMapper = Mappers.getMapper(GTransactionMapper.class);
        TransactionHelper transactionHelper = new TransactionHelper(gTransactionMapper);

        transactionService = new GTransactionServiceImpl(
                transactionRepository,
                memberService,
                transactionHelper,
                eventPublisher,
                permissionValidator,
                transactionValidator,
                List.of(expenseStrategy, refundStrategy),
                List.of()
        );
    }

    @Test
    @DisplayName("Chủ nhóm ghi khoản chi quỹ -> tự động CONFIRMED và trừ tiền quỹ")
    void testCreate_ExpenseByOwner_DirectConfirmed() {
        CategoryRefResponse cat = new CategoryRefResponse(categoryId, "Ăn uống", "expense", "#FF0000", null, null);
        MemberAuthInfo authInfo = new MemberAuthInfo(
                groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, ownerId
        );
        Member ownerMember = Member.builder().groupId(groupId).userId(ownerId).status(MemberStatus.ACTIVE).role(MemberRole.OWNER).build();

        when(groupRepository.findAuthInfo(groupId, ownerId)).thenReturn(Optional.of(authInfo));
        when(memberService.allMemberInGroup(eq(groupId), any())).thenReturn(true);
        when(expenseStrategy.supports(GTransactionType.EXPENSE)).thenReturn(true);
        when(expenseStrategy.determineStatus(any())).thenReturn(GTransactionStatus.CONFIRMED);

        GTransaction mockTxn = GTransaction.builder().id(UUID.randomUUID()).amount(1000000L).type(GTransactionType.EXPENSE).moneySource(MoneySource.FUND).status(GTransactionStatus.CONFIRMED).build();
        when(expenseStrategy.build(eq(ownerId), eq(groupId), any(), eq(GTransactionStatus.CONFIRMED), eq(true))).thenReturn(mockTxn);

        when(transactionRepository.save(any(GTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GTransactionType.EXPENSE,
                MoneySource.FUND,
                1000000L,
                Instant.now(),
                null,
                categoryId,
                ownerId,
                "Ăn trưa",
                List.of()
        );

        GroupTransactionDetailRes res = transactionService.create(ownerId, groupId, req);

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(GTransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Thành viên thường ghi khoản chi -> PENDING và quỹ không đổi")
    void testCreate_ExpenseByMember_Pending() {
        CategoryRefResponse cat = new CategoryRefResponse(categoryId, "Ăn uống", "expense", "#FF0000", null, null);
        MemberAuthInfo authInfo = new MemberAuthInfo(
                groupId, memberId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.MEMBER, ownerId
        );
        Member member = Member.builder().groupId(groupId).userId(memberId).status(MemberStatus.ACTIVE).role(MemberRole.MEMBER).build();

        when(groupRepository.findAuthInfo(groupId, memberId)).thenReturn(Optional.of(authInfo));
        when(memberService.allMemberInGroup(eq(groupId), any())).thenReturn(true);
        when(expenseStrategy.supports(GTransactionType.EXPENSE)).thenReturn(true);
        when(expenseStrategy.determineStatus(any())).thenReturn(GTransactionStatus.PENDING);

        GTransaction mockTxn = GTransaction.builder().id(UUID.randomUUID()).amount(500000L).type(GTransactionType.EXPENSE).moneySource(MoneySource.FUND).status(GTransactionStatus.PENDING).build();
        when(expenseStrategy.build(eq(memberId), eq(groupId), any(), eq(GTransactionStatus.PENDING), eq(true))).thenReturn(mockTxn);

        when(transactionRepository.save(any(GTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GTransactionType.EXPENSE,
                MoneySource.FUND,
                500000L,
                Instant.now(),
                null,
                categoryId,
                memberId,
                "Mua nước",
                List.of()
        );

        GroupTransactionDetailRes res = transactionService.create(memberId, groupId, req);

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(GTransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Thủ quỹ xác nhận khoản chi PENDING -> chuyển CONFIRMED và trừ tiền quỹ")
    void testConfirm_PendingTransaction_AdjustsBalance() {
        Fund fund = Fund.builder().id(fundId).groupId(groupId).keepperId(ownerId).currentBalance(5000000L).build();
        MemberAuthInfo authInfo = new MemberAuthInfo(
                groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, ownerId
        );
        UUID txnId = UUID.randomUUID();

        GTransaction txn = GTransaction.builder()
                .id(txnId)
                .groupId(groupId)
                .type(GTransactionType.EXPENSE)
                .moneySource(MoneySource.FUND)
                .amount(500000L)
                .status(GTransactionStatus.PENDING)
                .build();

        when(groupRepository.findAuthInfo(groupId, ownerId)).thenReturn(Optional.of(authInfo));
        when(transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(txnId, groupId)).thenReturn(Optional.of(txn));

        GroupTransactionDetailRes res = transactionService.confirm(ownerId, groupId, txnId);

        assertThat(res.status()).isEqualTo(GTransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Hoàn tiền vượt quá số còn lại của người nhận -> Báo lỗi GROUP_TXN_REFUND_EXCEEDS_BALANCE")
    void testCreateRefund_ExceedsBalance_ThrowsException() {
        MemberAuthInfo authInfo = new MemberAuthInfo(
                groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, ownerId
        );

        when(groupRepository.findAuthInfo(groupId, ownerId)).thenReturn(Optional.of(authInfo));
        when(memberService.allMemberInGroup(eq(groupId), any())).thenReturn(true);
        when(refundStrategy.supports(GTransactionType.REFUND)).thenReturn(true);
        when(refundStrategy.determineStatus(any())).thenReturn(GTransactionStatus.CONFIRMED);
        when(refundStrategy.build(eq(ownerId), eq(groupId), any(), eq(GTransactionStatus.CONFIRMED), eq(true)))
                .thenThrow(new BusinessException(ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE));

        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GTransactionType.REFUND,
                MoneySource.FUND,
                500000L,
                Instant.now(),
                null,
                null,
                memberId,
                "Trả lại tiền",
                List.of()
        );

        assertThatThrownBy(() -> transactionService.create(ownerId, groupId, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE.getCode());
    }

    @Test
    @DisplayName("Chủ nhóm xóa khoản chi CONFIRMED -> Hoàn tác số dư quỹ")
    void testDelete_ConfirmedTransaction_RollsBackBalance() {
        Group group = Group.builder().id(groupId).status(GroupStatus.ACTIVE).build();
        Fund fund = Fund.builder().id(fundId).groupId(groupId).keepperId(ownerId).currentBalance(5000000L).build();
        UUID txnId = UUID.randomUUID();

        GTransaction txn = GTransaction.builder()
                .id(txnId)
                .groupId(groupId)
                .type(GTransactionType.EXPENSE)
                .moneySource(MoneySource.FUND)
                .amount(800000L)
                .status(GTransactionStatus.CONFIRMED)
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
