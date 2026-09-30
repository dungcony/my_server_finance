package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceItemRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.*;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.fund.GroupFundRes;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.group.service.impl.ReportServiceImpl;
import com.datn.financeapp.user.service.ProfileService;
import com.datn.financeapp.group.helper.MemberAuthInfo;

import java.util.Optional;

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
 * Lớp kiểm thử cho {@link ReportServiceImpl}.
 * Phục vụ việc kiểm tra logic tổng hợp báo cáo, tính toán số dư quỹ và cân đối thành viên.
 */
@ExtendWith(MockitoExtension.class)
class GroupReportServiceTest {

    @Mock
    private GroupService groupService;

    @Mock
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private GroupTransactionRepository groupTransactionRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private GroupRepository groupRepository;

    private ReportServiceImpl reportService;

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


        GroupPermissionValidator permissionValidator = new GroupPermissionValidator(groupRepository);

        reportService = new ReportServiceImpl(
                groupService,
                memberService,
                groupTransactionRepository,
                profileService,
                permissionValidator
        );
    }

    @Test
    @DisplayName("Kiểm tra công thức bất biến: Tổng phần mọi người = Tổng số dư quỹ")
    void testGetBalances_InvariantHolds() {
        // Given
        GroupFundRes fund = new GroupFundRes(
                UUID.randomUUID(),
                groupId,
                userA,
                3000000L,
                Instant.now()
        );

        GroupDetailRes group = new GroupDetailRes(
                groupId,
                "Du lịch Đà Nẵng",
                null,
                GroupStatus.ACTIVE,
                "CODE",
                15000000L,
                true,
                true,
                Instant.now(),
                MemberRole.OWNER,
                fund,
                List.of()
        );

        when(groupService.findNotDeletedById(groupId)).thenReturn(group);


        MemberAuthInfo authInfo = new MemberAuthInfo(
                groupId, userA, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, userA
        );
        when(groupRepository.findAuthInfo(groupId, userA)).thenReturn(Optional.of(authInfo));

        Member memA = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userA).role(MemberRole.OWNER).status(MemberStatus.ACTIVE).joinedAt(Instant.now()).build();
        Member memB = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userB).role(MemberRole.MEMBER).status(MemberStatus.ACTIVE).joinedAt(Instant.now()).build();
        Member memC = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userC).role(MemberRole.MEMBER).status(MemberStatus.ACTIVE).joinedAt(Instant.now()).build();


        MemberRes mResA = new MemberRes(memA.getId(), userA, MemberRole.OWNER, MemberStatus.ACTIVE, memA.getJoinedAt());
        MemberRes mResB = new MemberRes(memB.getId(), userB, MemberRole.MEMBER, MemberStatus.ACTIVE, memB.getJoinedAt());
        MemberRes mResC = new MemberRes(memC.getId(), userC, MemberRole.MEMBER, MemberStatus.ACTIVE, memC.getJoinedAt());

        when(memberService.findIdAllMember(groupId)).thenReturn(List.of(userA, userB, userC));
        when(memberService.findMembers(eq(groupId), any(List.class))).thenReturn(List.of(memA, memB, memC));
        when(memberService.findMembers(groupId)).thenReturn(List.of(mResA, mResB, mResC));
        when(profileService.getDisplayNames(any())).thenReturn(Map.of(userA, "Nguyen A", userB, "Tran B", userC, "Le C"));

        Instant now = Instant.now();

        // Giao dịch 1: A nộp 1tr
        GTransaction tx1 = GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userA)
                .createdBy(userA)
                .type(TransactionType.CONTRIBUTION)
                .status(TransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // Giao dịch 2: B nộp 1tr
        GTransaction tx2 = GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userB)
                .createdBy(userB)
                .type(TransactionType.CONTRIBUTION)
                .status(TransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // Giao dịch 3: C nộp 1tr
        GTransaction tx3 = GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userC)
                .createdBy(userC)
                .type(TransactionType.CONTRIBUTION)
                .status(TransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // Giao dịch 4: B chi tiền túi 600k xem phim cho B và C (mỗi người 300k, moneySource = PERSONAL)
        UUID tx4Id = UUID.randomUUID();
        TransactionParticipant pB = TransactionParticipant.builder().userId(userB).shareAmount(300000L).build();
        TransactionParticipant pC = TransactionParticipant.builder().userId(userC).shareAmount(300000L).build();

        GTransaction tx4 = GTransaction.builder()
                .id(tx4Id)
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userB)
                .createdBy(userB)
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.CONFIRMED)
                .amount(600000L)
                .participants(List.of(pB, pC))
                .occurredAt(now)
                .createdAt(now)
                .build();

        when(groupTransactionRepository.findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
                groupId, TransactionStatus.CONFIRMED))
                .thenReturn(List.of(tx1, tx2, tx3, tx4));

        // When
        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.fundBalance()).isEqualTo(3000000L);

        GroupBalanceItemRes itemA = report.balances().stream().filter(b -> b.userId().equals(userA)).findFirst().orElseThrow();
        GroupBalanceItemRes itemB = report.balances().stream().filter(b -> b.userId().equals(userB)).findFirst().orElseThrow();
        GroupBalanceItemRes itemC = report.balances().stream().filter(b -> b.userId().equals(userC)).findFirst().orElseThrow();

        // A: nạp 1tr, chi túi 0, chịu 0 => net 1tr
        assertThat(itemA.totalPaidOutOfPocket()).isEqualTo(0L);
        assertThat(itemA.totalShareAmount()).isEqualTo(0L);
        assertThat(itemA.netBalance()).isEqualTo(1000000L);

        // B: nạp 1tr, chi túi 600k, chịu 300k => net 1.3tr
        assertThat(itemB.totalPaidOutOfPocket()).isEqualTo(600000L);
        assertThat(itemB.totalShareAmount()).isEqualTo(300000L);
        assertThat(itemB.netBalance()).isEqualTo(1300000L);

        // C: nạp 1tr, chi túi 0, chịu 300k => net 700k
        assertThat(itemC.totalPaidOutOfPocket()).isEqualTo(0L);
        assertThat(itemC.totalShareAmount()).isEqualTo(300000L);
        assertThat(itemC.netBalance()).isEqualTo(700000L);

        // Bất biến trung tâm: Tổng netBalance = Số dư quỹ = 3tr
        long sumNet = itemA.netBalance() + itemB.netBalance() + itemC.netBalance();
        assertThat(sumNet).isEqualTo(report.fundBalance());
    }

    @Test
    @DisplayName("Nhóm có mục tiêu: người góp đủ nhưng bị chia chi tiêu nhiều thì cần nộp thêm đúng bằng số dư âm")
    void testGetBalances_TargetGroup_NeededFollowsNegativeNet() {
        stubGroupWithThreeMembers(3000000L, 0L);

        // A góp 1tr rồi một mình chịu khoản chi 1,2tr từ quỹ
        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 1000000L, List.of());
        GTransaction expense = txn(TransactionType.EXPENSE, MoneySource.FUND, userA, 1200000L,
                List.of(TransactionParticipant.builder().userId(userA).build()));
        stubTransactions(contribution, expense);

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        GroupBalanceItemRes itemA = itemOf(report, userA);
        assertThat(itemA.netBalance()).isEqualTo(-200000L);
        assertThat(itemA.neededContribution()).isEqualTo(200000L);
    }

    @Test
    @DisplayName("Nhóm có mục tiêu: tiền túi trả hộ không làm giảm số cần nộp, chỉ số dư quyết định")
    void testGetBalances_TargetGroup_PaidOutOfPocketDoesNotReduceNeededDirectly() {
        stubGroupWithThreeMembers(3000000L, 0L);

        // B trả hộ 300k chia đều cho cả nhóm, mỗi người chịu 100k
        GTransaction expense = txn(TransactionType.EXPENSE, MoneySource.PERSONAL, userB, 300000L, List.of());
        stubTransactions(expense);

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        assertThat(itemOf(report, userB).netBalance()).isEqualTo(200000L);
        assertThat(itemOf(report, userB).neededContribution()).isEqualTo(0L);
        assertThat(itemOf(report, userA).neededContribution()).isEqualTo(100000L);
        assertThat(itemOf(report, userC).neededContribution()).isEqualTo(100000L);
        assertThat(report.totalNeededContribution()).isEqualTo(200000L);
    }

    @Test
    @DisplayName("Quỹ trả lại 400k tiền đã góp cho A thì số còn lại của A giảm xuống 600k")
    void testGetBalances_RefundReturningContribution_ReducesNetBalance() {
        stubGroupWithThreeMembers(null, 600000L);

        GTransaction contribution = txn(TransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 1000000L, List.of());
        GTransaction refund = txn(TransactionType.REFUND, MoneySource.FUND, userA, 400000L, List.of());
        stubTransactions(contribution, refund);

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        GroupBalanceItemRes itemA = itemOf(report, userA);
        assertThat(itemA.totalRefunded()).isEqualTo(400000L);
        assertThat(itemA.netBalance()).isEqualTo(600000L);
    }

    // dựng nhóm bật tính thừa thiếu có ba thành viên A (chủ nhóm), B, C
    private void stubGroupWithThreeMembers(Long target, long fundBalance) {
        GroupFundRes fund = new GroupFundRes(UUID.randomUUID(), groupId, userA, fundBalance, Instant.now());
        GroupDetailRes group = new GroupDetailRes(groupId, "Nhóm thử", null, GroupStatus.ACTIVE, "CODE", target,
                true, true, Instant.now(), MemberRole.OWNER, fund, List.of());
        when(groupService.findNotDeletedById(groupId)).thenReturn(group);
        when(groupRepository.findAuthInfo(groupId, userA)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, userA, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, userA)));

        Instant joinedAt = Instant.now().minusSeconds(3600);
        Member memA = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userA).role(MemberRole.OWNER)
                .status(MemberStatus.ACTIVE).joinedAt(joinedAt).build();
        Member memB = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userB).role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE).joinedAt(joinedAt).build();
        Member memC = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userC).role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE).joinedAt(joinedAt).build();

        when(memberService.findIdAllMember(groupId)).thenReturn(List.of(userA, userB, userC));
        when(memberService.findMembers(eq(groupId), any(List.class))).thenReturn(List.of(memA, memB, memC));
        when(memberService.findMembers(groupId)).thenReturn(List.of(
                new MemberRes(memA.getId(), userA, MemberRole.OWNER, MemberStatus.ACTIVE, joinedAt),
                new MemberRes(memB.getId(), userB, MemberRole.MEMBER, MemberStatus.ACTIVE, joinedAt),
                new MemberRes(memC.getId(), userC, MemberRole.MEMBER, MemberStatus.ACTIVE, joinedAt)));
        when(profileService.getDisplayNames(any()))
                .thenReturn(Map.of(userA, "Nguyen A", userB, "Tran B", userC, "Le C"));
    }

    private void stubTransactions(GTransaction... txns) {
        when(groupTransactionRepository.findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
                groupId, TransactionStatus.CONFIRMED)).thenReturn(List.of(txns));
    }

    private GTransaction txn(TransactionType type, MoneySource source, UUID transactorId, long amount,
                             List<TransactionParticipant> participants) {
        Instant now = Instant.now();
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(source)
                .transactorId(transactorId)
                .createdBy(transactorId)
                .type(type)
                .status(TransactionStatus.CONFIRMED)
                .amount(amount)
                .participants(participants)
                .occurredAt(now)
                .createdAt(now)
                .build();
    }

    private GroupBalanceItemRes itemOf(GroupBalanceReportRes report, UUID userId) {
        return report.balances().stream().filter(b -> b.userId().equals(userId)).findFirst().orElseThrow();
    }
}
