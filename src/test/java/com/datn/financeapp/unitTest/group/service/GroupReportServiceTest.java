package com.datn.financeapp.unitTest.group.service;

import com.datn.financeapp.module.group.service.*;


import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.response.fund.FundRes;
import com.datn.financeapp.module.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.module.group.dto.response.member.MemberRes;
import com.datn.financeapp.module.group.dto.response.report.GroupBalanceItemRes;
import com.datn.financeapp.module.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.module.group.dto.response.report.GroupSummaryReportRes;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.GroupTxnParticipant;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.helper.BalanceCalculator;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.MemberAuthCache;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import java.util.Collections;
import com.datn.financeapp.module.group.service.impl.GReportServiceImpl;
import com.datn.financeapp.module.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupReportServiceTest {

    // báo cáo cân đối lấy mọi thành viên trừ người đang chờ duyệt
    private static final List<MemberStatus> NON_PENDING_STATUSES =
            List.of(MemberStatus.ACTIVE, MemberStatus.LEFT, MemberStatus.REMOVED);

    @Mock
    private GroupService groupService;

    @Mock
    private GroupTxnGetService groupTxnGetService;

    @Mock
    private MemberBalanceService memberBalanceService;

    @Mock
    private MemberService memberService;

    @Mock
    private UserService userService;

    @Mock
    private MemberAuthCache memberAuthCache;

    private GReportServiceImpl reportService;

    private UUID groupId;
    private UUID userA;
    private UUID userB;
    private UUID userC;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        userC = UUID.randomUUID();

        reportService = new GReportServiceImpl(
                groupService,
                groupTxnGetService,
                memberBalanceService,
                memberService,
                userService,
                memberAuthCache
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userA.toString(), null, Collections.emptyList())
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
        when(memberAuthCache.get(groupId, userA)).thenReturn(Optional.of(authInfo));

        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, Instant.now(), null);
        MemberRes memB = member(userB, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now(), null);
        MemberRes memC = member(userC, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now(), null);

        stubMembers(memA, memB, memC);
        stubNames(userNamed(userA, "Nguyen", "A"), userNamed(userB, "Tran", "B"), userNamed(userC, "Le", "C"));

        Instant now = Instant.now();

        // giao dịch đóng góp của thành viên A
        GroupTxn tx1 = GroupTxn.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userA)
                .createdBy(userA)
                .type(GroupTxnType.CONTRIBUTION)
                .status(GroupTxnStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .build();

        // giao dịch đóng góp của thành viên B
        GroupTxn tx2 = GroupTxn.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userB)
                .createdBy(userB)
                .type(GroupTxnType.CONTRIBUTION)
                .status(GroupTxnStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .build();

        // giao dịch đóng góp của thành viên C
        GroupTxn tx3 = GroupTxn.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userC)
                .createdBy(userC)
                .type(GroupTxnType.CONTRIBUTION)
                .status(GroupTxnStatus.CONFIRMED)
                .amount(1000000L)
                .occurredAt(now)
                .build();

        // giao dịch B chi tiền túi 600k xem phim cho B và C mỗi người 300k
        UUID tx4Id = UUID.randomUUID();
        GroupTxnParticipant pB = GroupTxnParticipant.builder().userId(userB).shareAmount(300000L).build();
        GroupTxnParticipant pC = GroupTxnParticipant.builder().userId(userC).shareAmount(300000L).build();

        GroupTxn tx4 = GroupTxn.builder()
                .id(tx4Id)
                .groupId(groupId)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(userB)
                .createdBy(userB)
                .type(GroupTxnType.EXPENSE)
                .status(GroupTxnStatus.CONFIRMED)
                .amount(600000L)
                .participants(List.of(pB, pC))
                .occurredAt(now)
                .build();

        stubTransactions(tx1, tx2, tx3, tx4);

        // thực hiện lấy báo cáo số dư
        GroupBalanceReportRes report = reportService.getBalances(groupId);

        // kiểm tra kết quả tính toán
        assertThat(report).isNotNull();
        assertThat(report.fundBalance()).isEqualTo(3000000L);

        GroupBalanceItemRes itemA = report.balances().stream().filter(b -> b.userId().equals(userA)).findFirst().orElseThrow();
        GroupBalanceItemRes itemB = report.balances().stream().filter(b -> b.userId().equals(userB)).findFirst().orElseThrow();
        GroupBalanceItemRes itemC = report.balances().stream().filter(b -> b.userId().equals(userC)).findFirst().orElseThrow();

        // thành viên A nộp 1tr chi túi 0 chịu 0 số dư ròng 1tr
        assertThat(itemA.totalShareAmount()).isEqualTo(0L);
        assertThat(itemA.netBalance()).isEqualTo(1000000L);

        // thành viên B nộp 1tr chi túi 600k chịu 300k số dư ròng 1.3tr
        assertThat(itemB.totalShareAmount()).isEqualTo(300000L);
        assertThat(itemB.netBalance()).isEqualTo(1300000L);

        // thành viên C nộp 1tr chi túi 0 chịu 300k số dư ròng 700k
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
        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 1000000L, List.of());
        GroupTxn expense = txn(GroupTxnType.EXPENSE, MoneySource.FUND, userA, 1200000L,
                List.of(GroupTxnParticipant.builder().userId(userA).shareAmount(1200000L).build()));
        stubTransactions(contribution, expense);

        GroupBalanceReportRes report = reportService.getBalances(groupId);

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
        List<GroupTxnParticipant> participants = List.of(
                GroupTxnParticipant.builder().userId(userA).shareAmount(100000L).build(),
                GroupTxnParticipant.builder().userId(userB).shareAmount(100000L).build(),
                GroupTxnParticipant.builder().userId(userC).shareAmount(100000L).build()
        );
        GroupTxn expense = txn(GroupTxnType.EXPENSE, MoneySource.PERSONAL, userB, 300000L, participants);
        stubTransactions(expense);

        GroupBalanceReportRes report = reportService.getBalances(groupId);

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

        GroupTxn contribution = txn(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, userA, 1000000L, List.of());
        GroupTxn refund = txn(GroupTxnType.REFUND, MoneySource.FUND, userA, 400000L,
                List.of(GroupTxnParticipant.builder().userId(userA).shareAmount(400000L).build()));
        stubTransactions(contribution, refund);

        GroupBalanceReportRes report = reportService.getBalances(groupId);

        GroupBalanceItemRes itemA = itemOf(report, userA);
        assertThat(itemA.totalRefunded()).isEqualTo(400000L);
        assertThat(itemA.netBalance()).isEqualTo(600000L);
    }

    @Test
    @DisplayName("Báo cáo tổng quan toàn thời gian khi không truyền month")
    void testGetSummary_AllTime_WhenMonthIsNull() {
        stubGroupWithThreeMembers(5000000L, 1000000L);

        when(groupTxnGetService.sumConfirmedAmount(groupId, GroupTxnType.EXPENSE))
                .thenReturn(2000000L);
        when(groupTxnGetService.sumConfirmedAmount(groupId, GroupTxnType.CONTRIBUTION))
                .thenReturn(3000000L);

        // gọi với month là null
        GroupSummaryReportRes reportNullMonth = reportService.getSummary(groupId, null);
        assertThat(reportNullMonth.period()).isNull();
        assertThat(reportNullMonth.totalExpense()).isEqualTo(2000000L);
        assertThat(reportNullMonth.totalContribution()).isEqualTo(3000000L);

        // gọi với month là rỗng
        GroupSummaryReportRes reportBlankMonth = reportService.getSummary(groupId, "   ");
        assertThat(reportBlankMonth.period()).isNull();
        assertThat(reportBlankMonth.totalExpense()).isEqualTo(2000000L);
        assertThat(reportBlankMonth.totalContribution()).isEqualTo(3000000L);
    }

    @Test
    @DisplayName("Lọc báo cáo tổng quan theo tháng hợp lệ yyyy-MM")
    void testGetSummary_FilterByMonth_ValidFormat() {
        stubGroupWithThreeMembers(5000000L, 1000000L);

        when(groupTxnGetService.sumConfirmedAmount(eq(groupId), eq(GroupTxnType.EXPENSE), any(Instant.class), any(Instant.class)))
                .thenReturn(500000L);
        when(groupTxnGetService.sumConfirmedAmount(eq(groupId), eq(GroupTxnType.CONTRIBUTION), any(Instant.class), any(Instant.class)))
                .thenReturn(1500000L);

        GroupSummaryReportRes report = reportService.getSummary(groupId, "2026-03");

        assertThat(report.period()).isEqualTo("2026-03");
        assertThat(report.totalExpense()).isEqualTo(500000L);
        assertThat(report.totalContribution()).isEqualTo(1500000L);
    }

    @Test
    @DisplayName("Truyền month sai định dạng thì ném lỗi VALIDATION_ERROR")
    void testGetSummary_InvalidMonthFormat_ThrowsException() {
        stubGroupWithThreeMembers(5000000L, 1000000L);

        assertThatThrownBy(() -> reportService.getSummary(groupId, "2026/03"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());

        assertThatThrownBy(() -> reportService.getSummary(groupId, "abc"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("Người ngoài nhóm không được xem báo cáo tổng quan (ném FORBIDDEN)")
    void testGetSummary_NonMember_Forbidden() {
        when(memberAuthCache.get(groupId, userA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getSummary(groupId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("Thành viên đang PENDING không được xem báo cáo tổng quan (ném FORBIDDEN)")
    void testGetSummary_PendingMember_Forbidden() {
        when(memberAuthCache.get(groupId, userA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getSummary(groupId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("Người ngoài nhóm không được xem bảng cân đối (ném FORBIDDEN)")
    void testGetBalances_NonMember_Forbidden() {
        when(memberAuthCache.get(groupId, userA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getBalances(groupId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("Giao dịch trong quá khứ chỉ tính cho người là thành viên tại thời điểm giao dịch")
    void testGetBalances_HistoricalTransaction_SplitsToLeftMember() {
        UUID userD = UUID.randomUUID();
        Instant now = Instant.now();

        stubGroupWithThreeMembers(null, 500000L);

        Instant t0Joined = now.minusSeconds(10000);
        Instant t1TxnBeforeLeave = now.minusSeconds(5000);
        Instant t2Left = now.minusSeconds(1000);
        Instant t3TxnAfterLeave = now.minusSeconds(100);

        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, t0Joined, null);
        MemberRes memD = member(userD, MemberRole.MEMBER, MemberStatus.LEFT, t0Joined, t2Left);

        stubMembers(memA, memD);
        stubNames(userNamed(userA, "Nguyen", "A"), userNamed(userD, "Nguoi Roi Nhom", "D"));

        // giao dịch diễn ra lúc t1 trước khi D rời nhóm, A chi tiền túi 400k chia đều cho A và D mỗi người 200k
        List<GroupTxnParticipant> txn1Participants = List.of(
                GroupTxnParticipant.builder().userId(userA).shareAmount(200000L).build(),
                GroupTxnParticipant.builder().userId(userD).shareAmount(200000L).build()
        );
        GroupTxn txn1 = txn(GroupTxnType.EXPENSE, MoneySource.PERSONAL, userA, 400000L, txn1Participants, t1TxnBeforeLeave);

        // giao dịch diễn ra lúc t3 sau khi D đã rời nhóm, A chi tiền túi 300k chỉ một mình A gánh 300k
        List<GroupTxnParticipant> txn2Participants = List.of(
                GroupTxnParticipant.builder().userId(userA).shareAmount(300000L).build()
        );
        GroupTxn txn2 = txn(GroupTxnType.EXPENSE, MoneySource.PERSONAL, userA, 300000L, txn2Participants, t3TxnAfterLeave);

        stubTransactions(txn1, txn2);

        GroupBalanceReportRes report = reportService.getBalances(groupId);

        // kiểm tra thành viên D chịu đúng phần chia của giao dịch 1 (200k) và không chịu giao dịch 2
        GroupBalanceItemRes itemD = itemOf(report, userD);
        assertThat(itemD.totalShareAmount()).isEqualTo(200000L);
        assertThat(itemD.netBalance()).isEqualTo(-200000L);
        assertThat(itemD.neededContribution()).isEqualTo(200000L);

        // thành viên A chi tiền túi 700k, chịu 200k (từ txn1) + 300k (từ txn2) = 500k => netBalance = 200k
        GroupBalanceItemRes itemA = itemOf(report, userA);
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
        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, now.minusSeconds(10000), null);
        MemberRes memD = member(userD, MemberRole.MEMBER, MemberStatus.LEFT, now.minusSeconds(8000), now.minusSeconds(1000));
        MemberRes memE = member(userE, MemberRole.MEMBER, MemberStatus.REMOVED, now.minusSeconds(2000), now.minusSeconds(500));

        stubMembers(memA, memD, memE);
        stubNames(
                userNamed(userA, "Nguyen", "A"),
                userNamed(userD, "Tran", "D"),
                userNamed(userE, "Le", "E")
        );

        // dựng số dư: D còn 100k, E còn 200k (cả hai đều khác 0 để cùng lọt vào danh sách)
        com.datn.financeapp.module.group.helper.MemberBalances mb = new com.datn.financeapp.module.group.helper.MemberBalances();
        mb.addContribution(userA, 1000000L);
        mb.addShare(userA, 1000000L);
        mb.addContribution(userD, 100000L);
        mb.addContribution(userE, 200000L);
        when(memberBalanceService.getBalances(groupId)).thenReturn(mb);

        GroupBalanceReportRes report = reportService.getBalances(groupId);

        // danh sách trả về phải có 3 người: ACTIVE (A) đứng đầu, sau đó đến E (vào gần hơn: -2000s), cuối cùng là D (-8000s)
        List<UUID> returnedOrder = report.balances().stream().map(GroupBalanceItemRes::userId).toList();
        assertThat(returnedOrder).containsExactly(userA, userE, userD);
    }

    @Test
    @DisplayName("Người đã rời nhóm và hết sạch số dư (net = 0) bị loại khỏi báo cáo cân đối")
    void testGetBalances_DepartedMemberWithZeroNet_ExcludedFromReport() {
        UUID userD = UUID.randomUUID();
        Instant now = Instant.now();

        stubGroupWithThreeMembers(null, 0L);

        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, now.minusSeconds(10000), null);
        MemberRes memD = member(userD, MemberRole.MEMBER, MemberStatus.LEFT, now.minusSeconds(5000), now.minusSeconds(1000));

        stubMembers(memA, memD);
        stubNames(userNamed(userA, "Nguyen", "A"), userNamed(userD, "Tran", "D"));

        // D không còn số dư (tất cả chỉ số = 0)
        com.datn.financeapp.module.group.helper.MemberBalances mb = new com.datn.financeapp.module.group.helper.MemberBalances();
        when(memberBalanceService.getBalances(groupId)).thenReturn(mb);

        GroupBalanceReportRes report = reportService.getBalances(groupId);

        // chỉ có thành viên ACTIVE (A) xuất hiện trong báo cáo, D bị lọc bỏ
        List<UUID> userIdsInReport = report.balances().stream().map(GroupBalanceItemRes::userId).toList();
        assertThat(userIdsInReport).containsExactly(userA);
    }

    @Test
    @DisplayName("Thành viên không tìm thấy tên trong UserService thì fallback tên mặc định 'Thành viên ' + 8 ký tự id")
    void testGetBalances_UserNotFound_FallbackToDefaultName() {
        stubGroupWithThreeMembers(null, 0L);

        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, Instant.now(), null);
        MemberRes memB = member(userB, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now(), null);
        stubMembers(memA, memB);

        // chỉ A có trong kết quả tra tên, B thì không
        stubNames(userNamed(userA, "Nguyen", "A"));
        stubTransactions();

        GroupBalanceReportRes report = reportService.getBalances(groupId);

        assertThat(itemOf(report, userA).fullName()).isEqualTo("Nguyen A");
        assertThat(itemOf(report, userB).fullName()).isEqualTo("Thành viên " + userB.toString().substring(0, 8));
    }

    @Test
    @DisplayName("Nhóm đã lưu trữ không xem được báo cáo tổng quan, trả về FORBIDDEN")
    void testGetSummary_ArchivedGroup_StillReadable() {
        stubArchivedGroup(5000000L, 1000000L);

        assertThatThrownBy(() -> reportService.getSummary(groupId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("Nhóm đã lưu trữ không xem được báo cáo cân đối, trả về FORBIDDEN")
    void testGetBalances_ArchivedGroup_StillReadable() {
        stubArchivedGroup(null, 0L);

        assertThatThrownBy(() -> reportService.getBalances(groupId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    // dựng nhóm đã lưu trữ cùng quyền truy cập của chủ nhóm A
    private void stubArchivedGroup(Long target, long fundBalance) {
        FundRes fund = new FundRes(UUID.randomUUID(), groupId, userA, fundBalance, Instant.now());
        GroupDetailRes group = new GroupDetailRes(groupId, "Nhóm thử", null, GroupStatus.ARCHIVED, "CODE", target,
                true, true, Instant.now(), MemberRole.OWNER, fund, List.of());
        when(groupService.findNotDeletedById(groupId)).thenReturn(group);
        when(memberAuthCache.get(groupId, userA)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, userA, GroupStatus.ARCHIVED, true, MemberStatus.ACTIVE, MemberRole.OWNER, userA)));
    }

    // dựng nhóm bật tính thừa thiếu của chủ nhóm A cùng quyền truy cập
    private void stubGroupWithThreeMembers(Long target, long fundBalance) {
        FundRes fund = new FundRes(UUID.randomUUID(), groupId, userA, fundBalance, Instant.now());
        GroupDetailRes group = new GroupDetailRes(groupId, "Nhóm thử", null, GroupStatus.ACTIVE, "CODE", target,
                true, true, Instant.now(), MemberRole.OWNER, fund, List.of());
        when(groupService.findNotDeletedById(groupId)).thenReturn(group);
        when(memberAuthCache.get(groupId, userA)).thenReturn(Optional.of(
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

    private void stubTransactions(GroupTxn... txns) {
        List<MemberRes> members;
        try {
            members = memberService.getMembersWithStatusIn(groupId, NON_PENDING_STATUSES);
        } catch (Exception e) {
            members = List.of();
        }
        if (members == null || members.isEmpty()) {
            members = List.of(
                    member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, Instant.now().minusSeconds(86400), null),
                    member(userB, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now().minusSeconds(86400), null),
                    member(userC, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now().minusSeconds(86400), null)
            );
        }
        com.datn.financeapp.module.group.helper.MemberBalances mb = new com.datn.financeapp.module.group.helper.MemberBalances();
        for (GroupTxn txn : txns) {
            mb = mb.plus(com.datn.financeapp.module.group.helper.BalanceCalculator.effectOf(txn));
        }
        when(memberBalanceService.getBalances(groupId)).thenReturn(mb);
    }

    private GroupTxn txn(GroupTxnType type, MoneySource source, UUID transactorId, long amount,
                         List<GroupTxnParticipant> participants) {
        return txn(type, source, transactorId, amount, participants, Instant.now());
    }

    private GroupTxn txn(GroupTxnType type, MoneySource source, UUID transactorId, long amount,
                         List<GroupTxnParticipant> participants, Instant occurredAt) {
        return GroupTxn.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(source)
                .transactorId(transactorId)
                .createdBy(transactorId)
                .type(type)
                .status(GroupTxnStatus.CONFIRMED)
                .amount(amount)
                .participants(participants)
                .occurredAt(occurredAt)
                .build();
    }

    private GroupBalanceItemRes itemOf(GroupBalanceReportRes report, UUID userId) {
        return report.balances().stream().filter(b -> b.userId().equals(userId)).findFirst().orElseThrow();
    }
}
