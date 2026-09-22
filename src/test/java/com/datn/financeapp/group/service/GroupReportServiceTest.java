package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.datn.financeapp.group.dto.response.report.GroupBalanceItemRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.entity.GroupTransactionParticipant;
import com.datn.financeapp.group.entity.GroupWallet;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.GroupWalletStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.GroupTransactionParticipantRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.helper.GroupBalanceCalculator;
import com.datn.financeapp.group.service.GroupBalanceService;
import com.datn.financeapp.group.service.GroupReportService;
import com.datn.financeapp.group.service.GroupService;
import com.datn.financeapp.group.service.impl.GroupBalanceServiceImpl;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.group.service.impl.GroupReportServiceImpl;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupReportServiceTest {

    @Mock
    private GroupService groupService;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupTransactionRepository groupTransactionRepository;

    @Mock
    private GroupTransactionParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    private GroupBalanceCalculator balanceCalculator;
    private GroupBalanceService balanceService;
    private GroupReportServiceImpl reportService;

    private UUID groupId;
    private UUID userA;
    private UUID userB;
    private UUID userC;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        userC = UUID.randomUUID();

        balanceCalculator = new GroupBalanceCalculator();
        balanceService = new GroupBalanceServiceImpl(
                groupTransactionRepository,
                participantRepository,
                groupMemberRepository,
                balanceCalculator
        );

        GroupPermissionValidator permissionValidator = new GroupPermissionValidator(groupMemberRepository, groupRepository);

        reportService = new GroupReportServiceImpl(
                groupService,
                groupMemberRepository,
                groupTransactionRepository,
                userRepository,
                balanceService,
                permissionValidator
        );
    }

    @Test
    @DisplayName("Kiểm tra công thức bất biến: Tổng phần mọi người = Tổng số dư quỹ")
    void testGetBalances_InvariantHolds() {
        // Given
        GroupWalletRes wallet = new GroupWalletRes(
                UUID.randomUUID(),
                groupId,
                userA,
                3000000L,
                GroupWalletStatus.ACTIVE,
                Instant.now()
        );

        GroupDetailRes group = new GroupDetailRes(
                groupId,
                "Du lịch Đà Nẵng",
                null,
                GroupStatus.ACTIVE,
                15000000L,
                true,
                true,
                Instant.now(),
                GroupRole.OWNER,
                wallet,
                List.of()
        );

        when(groupService.findById(groupId)).thenReturn(group);
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userA, MemberStatus.ACTIVE))
                .thenReturn(true);

        GroupMember memA = GroupMember.builder().id(UUID.randomUUID()).groupId(groupId).userId(userA).role(GroupRole.OWNER).status(MemberStatus.ACTIVE).joinedAt(Instant.now()).build();
        GroupMember memB = GroupMember.builder().id(UUID.randomUUID()).groupId(groupId).userId(userB).role(GroupRole.MEMBER).status(MemberStatus.ACTIVE).joinedAt(Instant.now()).build();
        GroupMember memC = GroupMember.builder().id(UUID.randomUUID()).groupId(groupId).userId(userC).role(GroupRole.MEMBER).status(MemberStatus.ACTIVE).joinedAt(Instant.now()).build();

        when(groupMemberRepository.findByGroupIdOrderByJoinedAtDesc(groupId)).thenReturn(List.of(memA, memB, memC));

        User uA = User.builder().id(userA).firstName("A").lastName("Nguyen").build();
        User uB = User.builder().id(userB).firstName("B").lastName("Tran").build();
        User uC = User.builder().id(userC).firstName("C").lastName("Le").build();

        when(userRepository.findAllById(any())).thenReturn(List.of(uA, uB, uC));

        Instant now = Instant.now();

        // Giao dịch 1: A nộp 1tr
        GroupTransaction tx1 = GroupTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .userId(userA)
                .type(GroupTransactionType.CONTRIBUTION)
                .status(GroupTransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // Giao dịch 2: B nộp 1tr
        GroupTransaction tx2 = GroupTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .userId(userB)
                .type(GroupTransactionType.CONTRIBUTION)
                .status(GroupTransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // Giao dịch 3: C nộp 1tr
        GroupTransaction tx3 = GroupTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .userId(userC)
                .type(GroupTransactionType.CONTRIBUTION)
                .status(GroupTransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // Giao dịch 4: B chi tiền túi 600k xem phim cho B và C (mỗi người 300k, moneySource = PERSONAL)
        UUID tx4Id = UUID.randomUUID();
        GroupTransaction tx4 = GroupTransaction.builder()
                .id(tx4Id)
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .userId(userB)
                .type(GroupTransactionType.EXPENSE)
                .status(GroupTransactionStatus.CONFIRMED)
                .amount(600000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        when(groupTransactionRepository.findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
                groupId, GroupTransactionStatus.CONFIRMED))
                .thenReturn(List.of(tx1, tx2, tx3, tx4));

        GroupTransactionParticipant pB = GroupTransactionParticipant.builder().groupTransactionId(tx4Id).userId(userB).shareAmount(300000L).build();
        GroupTransactionParticipant pC = GroupTransactionParticipant.builder().groupTransactionId(tx4Id).userId(userC).shareAmount(300000L).build();
        when(participantRepository.findByGroupTransactionIdIn(any())).thenReturn(List.of(pB, pC));

        // When
        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.fundBalance()).isEqualTo(3000000L);

        GroupBalanceItemRes itemA = report.balances().stream().filter(b -> b.userId().equals(userA)).findFirst().orElseThrow();
        GroupBalanceItemRes itemB = report.balances().stream().filter(b -> b.userId().equals(userB)).findFirst().orElseThrow();
        GroupBalanceItemRes itemC = report.balances().stream().filter(b -> b.userId().equals(userC)).findFirst().orElseThrow();

        // A: nạp 1tr, chi túi 0, chịu 0 => net 1tr
        assertThat(itemA.totalContributed()).isEqualTo(1000000L);
        assertThat(itemA.totalPaidOutOfPocket()).isEqualTo(0L);
        assertThat(itemA.totalShareAmount()).isEqualTo(0L);
        assertThat(itemA.netBalance()).isEqualTo(1000000L);

        // B: nạp 1tr, chi túi 600k, chịu 300k => net 1.3tr
        assertThat(itemB.totalContributed()).isEqualTo(1000000L);
        assertThat(itemB.totalPaidOutOfPocket()).isEqualTo(600000L);
        assertThat(itemB.totalShareAmount()).isEqualTo(300000L);
        assertThat(itemB.netBalance()).isEqualTo(1300000L);

        // C: nạp 1tr, chi túi 0, chịu 300k => net 700k
        assertThat(itemC.totalContributed()).isEqualTo(1000000L);
        assertThat(itemC.totalPaidOutOfPocket()).isEqualTo(0L);
        assertThat(itemC.totalShareAmount()).isEqualTo(300000L);
        assertThat(itemC.netBalance()).isEqualTo(700000L);

        // Bất biến trung tâm: Tổng netBalance = Số dư quỹ = 3tr
        long sumNet = itemA.netBalance() + itemB.netBalance() + itemC.netBalance();
        assertThat(sumNet).isEqualTo(report.fundBalance());
    }
}
