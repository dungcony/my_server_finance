package com.datn.financeapp.module.group.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.events.GroupTxnPendingEvent;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.GroupTxnHelper;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import com.datn.financeapp.module.group.validator.GroupTransactionParticipantValidator;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GroupTxnMapperCreateImplTest {

    @Mock
    private GroupTxnRepository groupTxnRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private MemberBalanceService memberBalanceService;
    @Mock
    private GroupTxnCache groupTxnCache;
    @Mock
    private GroupTxnHelper groupTxnHelper;
    @Mock
    private GroupPermissionValidator permissionValidator;
    @Mock
    private GroupTransactionParticipantValidator transactionValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GroupTxnCreateImpl service;

    private UUID groupId;
    private UUID ownerId;
    private UUID treasurerId;
    private UUID memberId;
    private UUID otherMemberId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        treasurerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        otherMemberId = UUID.randomUUID();
    }

    // ---------------------------------- REFUND ----------------------------------//

    @Test
    @DisplayName("Thủ quỹ hoàn tiền cho nhiều người: CONFIRMED, không có người chi, phát event kèm phần của từng người")
    void create_refundByTreasurer_confirmedWithShares() {
        stubAuth(treasurerId, MemberRole.MEMBER, treasurerId);
        when(memberBalanceService.getBalancesForUpdate(eq(groupId), anyList()))
                .thenReturn(balancesWithContribution(500_000L, memberId, otherMemberId));
        GroupTxnCreateReq req = refundReq(null, List.of(
                new GroupTxnParticipantReq(memberId, 300_000L),
                new GroupTxnParticipantReq(otherMemberId, 200_000L)));

        service.create(treasurerId, groupId, req);

        GroupTxn saved = savedTransaction();
        assertThat(saved.getStatus()).isEqualTo(GroupTxnStatus.CONFIRMED);
        assertThat(saved.getTransactorId()).isNull();
        assertThat(saved.getParticipants()).hasSize(2);

        GroupTxnConfirmedEvent event = publishedEvent(GroupTxnConfirmedEvent.class);
        assertThat(event.type()).isEqualTo(GroupTxnType.REFUND);
        assertThat(event.participantShares()).containsEntry(memberId, 300_000L).containsEntry(otherMemberId, 200_000L);
        verify(memberBalanceService).applyDelta(eq(groupId), any(), any());
    }

    @Test
    @DisplayName("Chủ nhóm không phải thủ quỹ tạo hoàn tiền: FORBIDDEN, không lưu")
    void create_refundByOwnerNotTreasurer_forbidden() {
        stubAuth(ownerId, MemberRole.OWNER, treasurerId);

        assertThatThrownBy(() -> service.create(ownerId, groupId, refundReq(null, List.of(
                new GroupTxnParticipantReq(memberId, 100_000L)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        verify(groupTxnRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hoàn tiền mà có transactorId: ném GROUP_TXN_TRANSACTOR_REQUIRED_NULL")
    void create_refundWithTransactor_rejected() {
        stubAuth(treasurerId, MemberRole.MEMBER, treasurerId);

        assertThatThrownBy(() -> service.create(treasurerId, groupId, refundReq(treasurerId, List.of(
                new GroupTxnParticipantReq(memberId, 100_000L)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL.getCode()));
        verify(groupTxnRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hoàn cho một người vượt số dư của họ: GROUP_TXN_REFUND_EXCEEDS_BALANCE, không lưu")
    void create_refundExceedsBalanceOfOneReceiver_rejected() {
        stubAuth(treasurerId, MemberRole.MEMBER, treasurerId);
        when(memberBalanceService.getBalancesForUpdate(eq(groupId), anyList()))
                .thenReturn(balancesWithContribution(250_000L, memberId, otherMemberId));

        assertThatThrownBy(() -> service.create(treasurerId, groupId, refundReq(null, List.of(
                new GroupTxnParticipantReq(memberId, 200_000L),
                new GroupTxnParticipantReq(otherMemberId, 300_000L)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE.getCode()));
        verify(groupTxnRepository, never()).save(any());
    }

    // ---------------------------------- quyền của member ----------------------------------//

    @Test
    @DisplayName("Member thường không được tạo hoàn tiền")
    void create_refundByMember_forbidden() {
        stubAuth(memberId, MemberRole.MEMBER, treasurerId);

        assertThatThrownBy(() -> service.create(memberId, groupId, refundReq(null, List.of(
                new GroupTxnParticipantReq(memberId, 100_000L)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_CREATE_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("Member thường không được tạo kiểm kê")
    void create_adjustmentByMember_forbidden() {
        stubAuth(memberId, MemberRole.MEMBER, treasurerId);
        GroupTxnCreateReq req = new GroupTxnCreateReq(GroupTxnType.ADJUSTMENT_UP,
                MoneySource.FUND, 100_000L, Instant.now(), null, null, null,
                List.of(new GroupTxnParticipantReq(memberId, 100_000L)));

        assertThatThrownBy(() -> service.create(memberId, groupId, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_CREATE_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("Member tạo chi tiêu bằng quỹ không có người chi: PENDING và phát event báo người duyệt")
    void create_fundExpenseByMember_pendingAndNotifiesReviewers() {
        stubAuth(memberId, MemberRole.MEMBER, treasurerId);
        UUID categoryId = UUID.randomUUID();
        when(categoryService.findIdByCode("an_uong")).thenReturn(categoryId);
        GroupTxnCreateReq req = new GroupTxnCreateReq(GroupTxnType.EXPENSE,
                MoneySource.FUND, 100_000L, Instant.now(), "an_uong", null, null,
                List.of(new GroupTxnParticipantReq(memberId, 100_000L)));

        service.create(memberId, groupId, req);

        GroupTxn saved = savedTransaction();
        assertThat(saved.getStatus()).isEqualTo(GroupTxnStatus.PENDING);
        assertThat(saved.getTransactorId()).isNull();
        assertThat(saved.getCategoryId()).isEqualTo(categoryId);

        GroupTxnPendingEvent event = publishedEvent(GroupTxnPendingEvent.class);
        assertThat(event.createdBy()).isEqualTo(memberId);
        verify(memberBalanceService, never()).applyDelta(any(), any(), any());
    }

    @Test
    @DisplayName("Member tạo chi tiêu bằng tiền cá nhân cho người khác: GROUP_TXN_CREATE_FORBIDDEN")
    void create_personalExpenseForSomeoneElseByMember_forbidden() {
        stubAuth(memberId, MemberRole.MEMBER, treasurerId);
        GroupTxnCreateReq req = new GroupTxnCreateReq(GroupTxnType.EXPENSE,
                MoneySource.PERSONAL, 100_000L, Instant.now(), "an_uong", otherMemberId, null,
                List.of(new GroupTxnParticipantReq(otherMemberId, 100_000L)));

        assertThatThrownBy(() -> service.create(memberId, groupId, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_CREATE_FORBIDDEN.getCode()));
    }

    // ---------------------------------- tạo hộ và kiểm kê ----------------------------------//

    @Test
    @DisplayName("Chủ nhóm ghi hộ nộp quỹ cho member: CONFIRMED ngay, phát event cho chính member đó")
    void create_contributionOnBehalfByOwner_confirmedWithoutConsent() {
        stubAuth(ownerId, MemberRole.OWNER, treasurerId);
        GroupTxnCreateReq req = new GroupTxnCreateReq(GroupTxnType.CONTRIBUTION,
                MoneySource.PERSONAL, 300_000L, Instant.now(), null, memberId, null,
                List.of(new GroupTxnParticipantReq(memberId, 300_000L)));

        service.create(ownerId, groupId, req);

        assertThat(savedTransaction().getStatus()).isEqualTo(GroupTxnStatus.CONFIRMED);
        GroupTxnConfirmedEvent event = publishedEvent(GroupTxnConfirmedEvent.class);
        assertThat(event.transactorId()).isEqualTo(memberId);
        assertThat(event.type()).isEqualTo(GroupTxnType.CONTRIBUTION);
    }

    @Test
    @DisplayName("Kiểm kê do chủ nhóm tạo: CONFIRMED ngay")
    void create_adjustmentByOwner_confirmed() {
        GroupTxnCreateReq req = new GroupTxnCreateReq(GroupTxnType.ADJUSTMENT_DOWN,
                MoneySource.FUND, 100_000L, Instant.now(), null, null, "kiểm kê",
                List.of(new GroupTxnParticipantReq(memberId, 100_000L)));

        stubAuth(ownerId, MemberRole.OWNER, treasurerId);
        service.create(ownerId, groupId, req);
        assertThat(savedTransaction().getStatus()).isEqualTo(GroupTxnStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Kiểm kê do thủ quỹ tạo: PENDING và phát event báo người duyệt")
    void create_adjustmentByTreasurer_pending() {
        stubAuth(treasurerId, MemberRole.MEMBER, treasurerId);
        GroupTxnCreateReq req = new GroupTxnCreateReq(GroupTxnType.ADJUSTMENT_UP,
                MoneySource.FUND, 100_000L, Instant.now(), null, null, "kiểm kê",
                List.of(new GroupTxnParticipantReq(memberId, 100_000L)));

        service.create(treasurerId, groupId, req);

        assertThat(savedTransaction().getStatus()).isEqualTo(GroupTxnStatus.PENDING);
        publishedEvent(GroupTxnPendingEvent.class);
    }

    // ---------------------------------- helper ----------------------------------//

    private void stubAuth(UUID operatorId, MemberRole role, UUID keeperId) {
        when(permissionValidator.getAuthInfo(groupId, operatorId)).thenReturn(
                new MemberAuthInfo(groupId, operatorId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, role, keeperId));
    }

    private GroupTxnCreateReq refundReq(UUID transactorId, List<GroupTxnParticipantReq> participants) {
        long total = participants.stream().mapToLong(GroupTxnParticipantReq::shareAmount).sum();
        return new GroupTxnCreateReq(GroupTxnType.REFUND, MoneySource.FUND, total, Instant.now(),
                null, transactorId, null, participants);
    }

    private MemberBalances balancesWithContribution(long contribution, UUID... userIds) {
        Map<UUID, MemberBalanceAccumulator> map = new HashMap<>();
        for (UUID userId : userIds) {
            MemberBalanceAccumulator acc = new MemberBalanceAccumulator();
            acc.addContribution(contribution);
            map.put(userId, acc);
        }
        return new MemberBalances(map);
    }

    private GroupTxn savedTransaction() {
        ArgumentCaptor<GroupTxn> captor = ArgumentCaptor.forClass(GroupTxn.class);
        verify(groupTxnRepository).save(captor.capture());
        return captor.getValue();
    }

    private <T> T publishedEvent(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có event " + type.getSimpleName()));
    }
}
