package com.datn.financeapp.group.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
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
import org.springframework.context.ApplicationEventPublisher;

/**
 * Kiểm thử {@link GTransactionServiceImpl#delete}: chủ nhóm xoá mềm giao dịch nhóm.
 */
@ExtendWith(MockitoExtension.class)
class GTransactionServiceImplTest {

    @Mock
    private GroupTransactionRepository transactionRepository;
    @Mock
    private MemberService memberService;
    @Mock
    private TransactionHelper transactionHelper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private GroupPermissionValidator permissionValidator;
    @Mock
    private GroupTransactionPaticipantValidator transactionValidator;

    private GTransactionServiceImpl service;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        service = new GTransactionServiceImpl(transactionRepository, memberService, transactionHelper,
                eventPublisher, permissionValidator, transactionValidator, List.of(), List.of());
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        when(permissionValidator.verifyActiveMemberInGroupActive(groupId, ownerId)).thenReturn(
                new MemberAuthInfo(groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE,
                        MemberRole.OWNER, ownerId));
    }

    @Test
    @DisplayName("Chủ nhóm xoá khoản góp thì đánh dấu xoá mềm và lưu lại")
    void delete_Contribution_SoftDeletes() {
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, 1_000_000L);
        stubFind(contribution);

        service.delete(ownerId, groupId, contribution.getId());

        assertThat(contribution.getDeletedAt()).isNotNull();
        verify(transactionRepository).save(contribution);
    }

    private void stubFind(GTransaction txn) {
        when(transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(txn.getId(), groupId))
                .thenReturn(java.util.Optional.of(txn));
    }

    private GTransaction txn(TransactionType type, MoneySource source, long amount) {
        Instant now = Instant.now();
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .type(type)
                .moneySource(source)
                .transactorId(memberId)
                .createdBy(memberId)
                .status(TransactionStatus.CONFIRMED)
                .amount(amount)
                .participants(new ArrayList<>())
                .occurredAt(now)
                .createdAt(now)
                .build();
    }
}
