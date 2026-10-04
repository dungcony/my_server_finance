package com.datn.financeapp.group.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.response.fund.FundRes;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceItemRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.response.report.GroupSummaryReportRes;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.*;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.service.impl.GReportServiceImpl;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Lớp kiểm thử cho {@link GReportServiceImpl}.
 * Phục vụ việc kiểm tra logic tổng hợp báo cáo, tính toán số dư quỹ và cân đối thành viên.
 */
@ExtendWith(MockitoExtension.class)
class GroupReportServiceTest {

    // báo cáo cân đối lấy mọi thành viên trừ người đang chờ duyệt
    private static final List<MemberStatus> NON_PENDING_STATUSES =
            List.of(MemberStatus.ACTIVE, MemberStatus.LEFT, MemberStatus.REMOVED);

    @Mock
    private GroupService groupService;

    @Mock
    private GTransactionService gTransactionService;

    @Mock
    private MemberService memberService;

    @Mock
    private UserService userService;

    @Mock
    private GroupRepository groupRepository;

    private GReportServiceImpl reportService;

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

        reportService = new GReportServiceImpl(
                groupService,
                gTransactionService,
                memberService,
                userService,
                permissionValidator
        );
    }

    @Test
    @DisplayName("Kiểm tra công thức bất biến: Tổng phần mọi người = Tổng số dư quỹ")
    void testGetBalances_InvariantHolds() {
        // thiết lập dữ liệu ban đầu
        FundRes fund = new FundRes(
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

        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, Instant.now(), null);
        MemberRes memB = member(userB, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now(), null);
        MemberRes memC = member(userC, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now(), null);

        stubMembers(memA, memB, memC);
        stubNames(userNamed(userA, "Nguyen", "A"), userNamed(userB, "Tran", "B"), userNamed(userC, "Le", "C"));

        Instant now = Instant.now();

        // giao dịch đóng góp của thành viên A
        GTransaction tx1 = GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userA)
                .createdBy(userA)
                .type(GTransactionType.CONTRIBUTION)
                .status(GTransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // giao dịch đóng góp của thành viên B
        GTransaction tx2 = GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userB)
                .createdBy(userB)
                .type(GTransactionType.CONTRIBUTION)
                .status(GTransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // giao dịch đóng góp của thành viên C
        GTransaction tx3 = GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userC)
                .createdBy(userC)
                .type(GTransactionType.CONTRIBUTION)
                .status(GTransactionStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .createdAt(now)
                .build();

        // giao dịch B chi tiền túi 600k xem phim cho B và C mỗi người 300k
        UUID tx4Id = UUID.randomUUID();
        TransactionParticipant pB = TransactionParticipant.builder().userId(userB).shareAmount(300000L).build();
        TransactionParticipant pC = TransactionParticipant.builder().userId(userC).shareAmount(300000L).build();

        GTransaction tx4 = GTransaction.builder()
                .id(tx4Id)
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userB)
                .createdBy(userB)
                .type(GTransactionType.EXPENSE)
                .status(GTransactionStatus.CONFIRMED)
                .amount(600000L)
                .participants(List.of(pB, pC))
                .occurredAt(now)
                .createdAt(now)
                .build();

        stubTransactions(tx1, tx2, tx3, tx4);

        // thực hiện lấy báo cáo số dư
        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        // kiểm tra kết quả tính toán
        assertThat(report).isNotNull();
        assertThat(report.fundBalance()).isEqualTo(3000000L);

        GroupBalanceItemRes itemA = report.balances().stream().filter(b -> b.userId().equals(userA)).findFirst().orElseThrow();
        GroupBalanceItemRes itemB = report.balances().stream().filter(b -> b.userId().equals(userB)).findFirst().orElseThrow();
        GroupBalanceItemRes itemC = report.balances().stream().filter(b -> b.userId().equals(userC)).findFirst().orElseThrow();

        // thành viên A nộp 1tr chi túi 0 chịu 0 số dư ròng 1tr
        assertThat(itemA.totalPaidOutOfPocket()).isEqualTo(0L);
        assertThat(itemA.totalShareAmount()).isEqualTo(0L);
        assertThat(itemA.netBalance()).isEqualTo(1000000L);

        // thành viên B nộp 1tr chi túi 600k chịu 300k số dư ròng 1.3tr
        assertThat(itemB.totalPaidOutOfPocket()).isEqualTo(600000L);
        assertThat(itemB.totalShareAmount()).isEqualTo(300000L);
        assertThat(itemB.netBalance()).isEqualTo(1300000L);

        // thành viên C nộp 1tr chi túi 0 chịu 300k số dư ròng 700k
        assertThat(itemC.totalPaidOutOfPocket()).isEqualTo(0L);
        assertThat(itemC.totalShareAmount()).isEqualTo(300000L);
        assertThat(itemC.netBalance()).isEqualTo(700000L);

        // tổng số dư ròng bằng số dư quỹ thực tế
        long sumNet = itemA.netBalance() + itemB.netBalance() + itemC.netBalance();
        assertThat(sumNet).isEqualTo(report.fundBalance());
    }

    @Test
    @DisplayName("Nhóm có mục tiêu: người góp đủ nhưng bị chia chi tiêu nhiều thì cần nộp thêm đúng bằng số dư âm")
    void testGetBalances_TargetGroup_NeededFollowsNegativeNet() {
        stubGroupWithThreeMembers(3000000L, 0L);
        stubThreeMembersWithNames();

        // A góp 1tr rồi một mình chịu khoản chi 1.2tr từ quỹ
        GTransaction contribution = txn(GTransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 1000000L, List.of());
        GTransaction expense = txn(GTransactionType.EXPENSE, MoneySource.FUND, userA, 1200000L,
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
        stubThreeMembersWithNames();

        // B trả hộ 300k chia đều cho cả nhóm, mỗi người chịu 100k
        GTransaction expense = txn(GTransactionType.EXPENSE, MoneySource.PERSONAL, userB, 300000L, List.of());
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
        stubThreeMembersWithNames();

        GTransaction contribution = txn(GTransactionType.CONTRIBUTION, MoneySource.PERSONAL, userA, 1000000L, List.of());
        GTransaction refund = txn(GTransactionType.REFUND, MoneySource.FUND, userA, 400000L, List.of());
        stubTransactions(contribution, refund);

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        GroupBalanceItemRes itemA = itemOf(report, userA);
        assertThat(itemA.totalRefunded()).isEqualTo(400000L);
        assertThat(itemA.netBalance()).isEqualTo(600000L);
    }

    @Test
    @DisplayName("Báo cáo tổng quan toàn thời gian khi không truyền month")
    void testGetSummary_AllTime_WhenMonthIsNull() {
        stubGroupWithThreeMembers(5000000L, 1000000L);

        when(gTransactionService.sumConfirmedAmount(groupId, GTransactionType.EXPENSE))
                .thenReturn(2000000L);
        when(gTransactionService.sumConfirmedAmount(groupId, GTransactionType.CONTRIBUTION))
                .thenReturn(3000000L);

        // gọi với month là null
        GroupSummaryReportRes reportNullMonth = reportService.getSummary(userA, groupId, null);
        assertThat(reportNullMonth.period()).isNull();
        assertThat(reportNullMonth.totalExpense()).isEqualTo(2000000L);
        assertThat(reportNullMonth.totalContribution()).isEqualTo(3000000L);

        // gọi với month là chuỗi rỗng
        GroupSummaryReportRes reportBlankMonth = reportService.getSummary(userA, groupId, "   ");
        assertThat(reportBlankMonth.period()).isNull();
        assertThat(reportBlankMonth.totalExpense()).isEqualTo(2000000L);
        assertThat(reportBlankMonth.totalContribution()).isEqualTo(3000000L);
    }

    @Test
    @DisplayName("Báo cáo tổng quan theo tháng hợp lệ")
    void testGetSummary_ByMonth() {
        stubGroupWithThreeMembers(5000000L, 1000000L);

        when(gTransactionService.sumConfirmedAmount(
                eq(groupId), eq(GTransactionType.EXPENSE), any(Instant.class), any(Instant.class)))
                .thenReturn(800000L);
        when(gTransactionService.sumConfirmedAmount(
                eq(groupId), eq(GTransactionType.CONTRIBUTION), any(Instant.class), any(Instant.class)))
                .thenReturn(1500000L);

        GroupSummaryReportRes report = reportService.getSummary(userA, groupId, "2026-09");

        assertThat(report.period()).isEqualTo("2026-09");
        assertThat(report.totalExpense()).isEqualTo(800000L);
        assertThat(report.totalContribution()).isEqualTo(1500000L);
    }

    @Test
    @DisplayName("Báo cáo tổng quan ném ngoại lệ khi tháng sai định dạng")
    void testGetSummary_InvalidMonthFormat_ThrowsException() {
        stubGroupWithThreeMembers(5000000L, 1000000L);

        assertThatThrownBy(() -> reportService.getSummary(userA, groupId, "2026-13"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());

        assertThatThrownBy(() -> reportService.getSummary(userA, groupId, "invalid-date"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("Thành viên đã rời nhóm (LEFT) còn số dư ròng khác 0 thì vẫn xuất hiện trong báo cáo")
    void testGetBalances_LeftMemberWithNonZeroBalance_IncludedInReport() {
        UUID userD = UUID.randomUUID();
        Instant now = Instant.now();

        FundRes fund = new FundRes(UUID.randomUUID(), groupId, userA, 1000000L, now);
        GroupDetailRes group = new GroupDetailRes(groupId, "Nhóm thử", null, GroupStatus.ACTIVE, "CODE", null,
                true, true, now, MemberRole.OWNER, fund, List.of());
        when(groupService.findNotDeletedById(groupId)).thenReturn(group);
        when(groupRepository.findAuthInfo(groupId, userA)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, userA, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, userA)));

        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, now.minusSeconds(3600), null);
        MemberRes memD = member(userD, MemberRole.MEMBER, MemberStatus.LEFT, now.minusSeconds(3600), now.minusSeconds(600));

        stubMembers(memA, memD);
        stubNames(userNamed(userA, "Nguyen", "A"), userNamed(userD, "Nguoi Roi Nhom", "D"));

        // D chi tiền túi 500k cho riêng D chịu 200k, A chịu 300k => D còn net 300k > 0
        TransactionParticipant pA = TransactionParticipant.builder().userId(userA).shareAmount(300000L).build();
        TransactionParticipant pD = TransactionParticipant.builder().userId(userD).shareAmount(200000L).build();
        GTransaction expense = txn(GTransactionType.EXPENSE, MoneySource.PERSONAL, userD, 500000L, List.of(pA, pD));
        stubTransactions(expense);

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        // kiểm tra D vẫn có mặt trong danh sách báo cáo
        assertThat(report.balances()).hasSize(2);
        GroupBalanceItemRes itemD = itemOf(report, userD);
        assertThat(itemD.status()).isEqualTo(MemberStatus.LEFT.name());
        assertThat(itemD.netBalance()).isEqualTo(300000L);
    }

    @Test
    @DisplayName("Thành viên đã rời nhóm (LEFT) có số dư ròng bằng 0 thì không xuất hiện trong báo cáo")
    void testGetBalances_LeftMemberWithZeroBalance_ExcludedFromReport() {
        UUID userD = UUID.randomUUID();
        Instant now = Instant.now();

        FundRes fund = new FundRes(UUID.randomUUID(), groupId, userA, 0L, now);
        GroupDetailRes group = new GroupDetailRes(groupId, "Nhóm thử", null, GroupStatus.ACTIVE, "CODE", null,
                true, true, now, MemberRole.OWNER, fund, List.of());
        when(groupService.findNotDeletedById(groupId)).thenReturn(group);
        when(groupRepository.findAuthInfo(groupId, userA)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, userA, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, userA)));

        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, now.minusSeconds(3600), null);
        MemberRes memD = member(userD, MemberRole.MEMBER, MemberStatus.LEFT, now.minusSeconds(3600), now.minusSeconds(600));

        stubMembers(memA, memD);
        stubNames(userNamed(userA, "Nguyen", "A"));

        // D không phát sinh bất kỳ giao dịch nào => netBalance = 0
        stubTransactions();

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        // chỉ có thành viên ACTIVE là A hiển thị
        assertThat(report.balances()).hasSize(1);
        assertThat(report.balances().get(0).userId()).isEqualTo(userA);
    }

    @Test
    @DisplayName("Giao dịch lịch sử không có participant chia chi phí cho thành viên LEFT nếu xảy ra trước khi rời nhóm")
    void testGetBalances_HistoricalTransaction_SplitsToLeftMember() {
        UUID userD = UUID.randomUUID();
        Instant now = Instant.now();

        FundRes fund = new FundRes(UUID.randomUUID(), groupId, userA, 0L, now);
        GroupDetailRes group = new GroupDetailRes(groupId, "Nhóm thử", null, GroupStatus.ACTIVE, "CODE", null,
                true, true, now, MemberRole.OWNER, fund, List.of());
        when(groupService.findNotDeletedById(groupId)).thenReturn(group);
        when(groupRepository.findAuthInfo(groupId, userA)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, userA, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, userA)));

        Instant t0Joined = now.minusSeconds(3000);
        Instant t1TxnBeforeLeave = now.minusSeconds(2000);
        Instant t2Left = now.minusSeconds(1000);
        Instant t3TxnAfterLeave = now.minusSeconds(100);

        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, t0Joined, null);
        MemberRes memD = member(userD, MemberRole.MEMBER, MemberStatus.LEFT, t0Joined, t2Left);

        stubMembers(memA, memD);
        stubNames(userNamed(userA, "Nguyen", "A"), userNamed(userD, "Nguoi Roi Nhom", "D"));

        // giao dịch diễn ra lúc t1 trước khi D rời nhóm, A chi tiền túi 400k không participant chia đều cho A và D mỗi người 200k
        GTransaction txn1 = txn(GTransactionType.EXPENSE, MoneySource.PERSONAL, userA, 400000L, List.of(), t1TxnBeforeLeave);

        // giao dịch diễn ra lúc t3 sau khi D đã rời nhóm, A chi tiền túi 300k không participant chỉ một mình A gánh 300k
        GTransaction txn2 = txn(GTransactionType.EXPENSE, MoneySource.PERSONAL, userA, 300000L, List.of(), t3TxnAfterLeave);

        stubTransactions(txn1, txn2);

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        // kiểm tra thành viên D chịu đúng phần chia của giao dịch 1 (200k) và không chịu giao dịch 2
        GroupBalanceItemRes itemD = itemOf(report, userD);
        assertThat(itemD.totalShareAmount()).isEqualTo(200000L);
        assertThat(itemD.netBalance()).isEqualTo(-200000L);
        assertThat(itemD.neededContribution()).isEqualTo(200000L);

        // thành viên A chi tiền túi 700k, chịu 200k (từ txn1) + 300k (từ txn2) = 500k => netBalance = 200k
        GroupBalanceItemRes itemA = itemOf(report, userA);
        assertThat(itemA.totalPaidOutOfPocket()).isEqualTo(700000L);
        assertThat(itemA.totalShareAmount()).isEqualTo(500000L);
        assertThat(itemA.netBalance()).isEqualTo(200000L);
    }

    @Test
    @DisplayName("Người đã rời nhóm còn số dư xếp sau thành viên đang hoạt động, người vào nhóm gần nhất lên trước")
    void testGetBalances_DepartedMembers_SortedByJoinedAtDescending() {
        UUID userD = UUID.randomUUID();
        UUID userE = UUID.randomUUID();
        Instant now = Instant.now();

        stubGroupWithThreeMembers(null, 300000L);

        // MemberService không đảm bảo thứ tự: D vào nhóm sớm hơn E nhưng lại được trả về trước
        stubMembers(
                member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, now.minusSeconds(5000), null),
                member(userD, MemberRole.MEMBER, MemberStatus.LEFT, now.minusSeconds(4000), now.minusSeconds(1000)),
                member(userE, MemberRole.MEMBER, MemberStatus.REMOVED, now.minusSeconds(2000), now.minusSeconds(500)));
        stubNames(userNamed(userA, "Nguyen", "A"), userNamed(userD, "Pham", "D"), userNamed(userE, "Hoang", "E"));

        // D và E đều đã góp tiền nên còn số dư khác 0 và phải hiện trong báo cáo
        stubTransactions(
                txn(GTransactionType.CONTRIBUTION, MoneySource.PERSONAL, userD, 100000L, List.of(), now.minusSeconds(3500)),
                txn(GTransactionType.CONTRIBUTION, MoneySource.PERSONAL, userE, 200000L, List.of(), now.minusSeconds(1500)));

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        // A đang hoạt động đứng đầu, sau đó E (vào nhóm gần nhất) rồi tới D
        assertThat(report.balances())
                .extracting(GroupBalanceItemRes::userId)
                .containsExactly(userA, userE, userD);
    }

    @Test
    @DisplayName("Tên hiển thị lấy từ kết quả tra tên của UserService, thành viên không có trong kết quả tra tên thì dùng tên dự phòng theo id")
    void testGetBalances_DisplayName_FromUserResOrFallbackToId() {
        stubGroupWithThreeMembers(null, 0L);

        Instant joinedAt = Instant.now().minusSeconds(3600);
        stubMembers(
                member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, joinedAt, null),
                member(userB, MemberRole.MEMBER, MemberStatus.ACTIVE, joinedAt, null));

        // chỉ A có trong kết quả tra tên, B thì không
        stubNames(userNamed(userA, "Nguyen", "A"));
        stubTransactions();

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        assertThat(itemOf(report, userA).fullName()).isEqualTo("Nguyen A");
        assertThat(itemOf(report, userB).fullName()).isEqualTo("Thành viên " + userB.toString().substring(0, 8));
    }

    @Test
    @DisplayName("Nhóm đã lưu trữ vẫn xem được báo cáo tổng quan (nhóm lưu trữ chỉ đọc, rule.md quy tắc 26)")
    void testGetSummary_ArchivedGroup_StillReadable() {
        stubArchivedGroup(5000000L, 1000000L);

        when(gTransactionService.sumConfirmedAmount(groupId, GTransactionType.EXPENSE)).thenReturn(2000000L);
        when(gTransactionService.sumConfirmedAmount(groupId, GTransactionType.CONTRIBUTION)).thenReturn(3000000L);

        GroupSummaryReportRes report = reportService.getSummary(userA, groupId, null);

        assertThat(report.totalExpense()).isEqualTo(2000000L);
        assertThat(report.totalContribution()).isEqualTo(3000000L);
    }

    @Test
    @DisplayName("Nhóm đã lưu trữ vẫn xem được báo cáo cân đối thành viên")
    void testGetBalances_ArchivedGroup_StillReadable() {
        stubArchivedGroup(null, 0L);
        stubThreeMembersWithNames();
        stubTransactions();

        GroupBalanceReportRes report = reportService.getBalances(userA, groupId);

        assertThat(report.balances()).hasSize(3);
    }

    // dựng nhóm đã lưu trữ cùng quyền truy cập của chủ nhóm A
    private void stubArchivedGroup(Long target, long fundBalance) {
        FundRes fund = new FundRes(UUID.randomUUID(), groupId, userA, fundBalance, Instant.now());
        GroupDetailRes group = new GroupDetailRes(groupId, "Nhóm thử", null, GroupStatus.ARCHIVED, "CODE", target,
                true, true, Instant.now(), MemberRole.OWNER, fund, List.of());
        when(groupService.findNotDeletedById(groupId)).thenReturn(group);
        when(groupRepository.findAuthInfo(groupId, userA)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, userA, GroupStatus.ARCHIVED, true, MemberStatus.ACTIVE, MemberRole.OWNER, userA)));
    }

    // dựng nhóm bật tính thừa thiếu của chủ nhóm A cùng quyền truy cập
    private void stubGroupWithThreeMembers(Long target, long fundBalance) {
        FundRes fund = new FundRes(UUID.randomUUID(), groupId, userA, fundBalance, Instant.now());
        GroupDetailRes group = new GroupDetailRes(groupId, "Nhóm thử", null, GroupStatus.ACTIVE, "CODE", target,
                true, true, Instant.now(), MemberRole.OWNER, fund, List.of());
        when(groupService.findNotDeletedById(groupId)).thenReturn(group);
        when(groupRepository.findAuthInfo(groupId, userA)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, userA, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, userA)));
    }

    // dựng danh sách ba thành viên và tên hiển thị, chỉ báo cáo cân đối mới dùng
    private void stubThreeMembersWithNames() {
        Instant joinedAt = Instant.now().minusSeconds(3600);
        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, joinedAt, null);
        MemberRes memB = member(userB, MemberRole.MEMBER, MemberStatus.ACTIVE, joinedAt, null);
        MemberRes memC = member(userC, MemberRole.MEMBER, MemberStatus.ACTIVE, joinedAt, null);

        stubMembers(memA, memB, memC);
        stubNames(userNamed(userA, "Nguyen", "A"), userNamed(userB, "Tran", "B"), userNamed(userC, "Le", "C"));
    }

    // dựng thành viên dạng DTO như MemberService trả về
    private MemberRes member(UUID userId, MemberRole role, MemberStatus status, Instant joinedAt, Instant leftAt) {
        return new MemberRes(UUID.randomUUID(), userId, role, status, joinedAt, leftAt, null, false);
    }

    private void stubMembers(MemberRes... members) {
        when(memberService.getMembersWithStatusIn(groupId, NON_PENDING_STATUSES)).thenReturn(List.of(members));
    }

    // dựng bản ghi người dùng chỉ cần phần tên để UserService.getNames trả về
    private UserNameDisplayRes userNamed(UUID id, String firstName, String lastName) {
        return new UserNameDisplayRes(id, firstName, lastName, UserStatus.ACTIVE, false);
    }

    private void stubNames(UserNameDisplayRes... users) {
        Map<UUID, UserNameDisplayRes> byId = new HashMap<>();
        for (UserNameDisplayRes user : users) {
            byId.put(user.id(), user);
        }
        when(userService.getNames(any())).thenReturn(byId);
    }

    private void stubTransactions(GTransaction... txns) {
        when(gTransactionService.streamConfirmedTransactions(groupId))
            .thenAnswer(inv -> java.util.List.of(txns).stream());
    }

    private GTransaction txn(GTransactionType type, MoneySource source, UUID transactorId, long amount,
                             List<TransactionParticipant> participants) {
        return txn(type, source, transactorId, amount, participants, Instant.now());
    }

    private GTransaction txn(GTransactionType type, MoneySource source, UUID transactorId, long amount,
                             List<TransactionParticipant> participants, Instant occurredAt) {
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(source)
                .transactorId(transactorId)
                .createdBy(transactorId)
                .type(type)
                .status(GTransactionStatus.CONFIRMED)
                .amount(amount)
                .participants(participants)
                .occurredAt(occurredAt)
                .createdAt(occurredAt)
                .build();
    }

    private GroupBalanceItemRes itemOf(GroupBalanceReportRes report, UUID userId) {
        return report.balances().stream().filter(b -> b.userId().equals(userId)).findFirst().orElseThrow();
    }
}
