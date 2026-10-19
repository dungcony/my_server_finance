package com.datn.financeapp.unitTest.report;

import com.datn.financeapp.module.budget.dto.response.BudgetAlertRes;
import com.datn.financeapp.module.budget.enums.BudgetStatus;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.service.BudgetOverviewService;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.report.dto.response.CategoryBreakdownResponse;
import com.datn.financeapp.module.report.dto.response.CategoryGroupBreakdownResponse;
import com.datn.financeapp.module.report.dto.response.DailyTrendResponse;
import com.datn.financeapp.module.report.dto.response.MonthlyTrendResponse;
import com.datn.financeapp.module.report.dto.response.ReportHomeResponse;
import com.datn.financeapp.module.report.dto.response.ReportSummaryResponse;
import com.datn.financeapp.module.report.repository.ReportRepository;
import com.datn.financeapp.module.report.service.impl.ReportQueryServiceImpl;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private BudgetCategoryHelper budgetCategoryHelper;

    @Mock
    private WalletService walletService;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private BudgetOverviewService budgetOverviewService;

    @Mock
    private UserTimezoneHelper timezoneHelper;

    @Mock
    private IconService iconService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReportQueryServiceImpl reportQueryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

    @BeforeEach
    void setUp() {
        when(timezoneHelper.getCurrentZone()).thenReturn(zone);
        when(timezoneHelper.toStartInstant(any(LocalDate.class), any(ZoneId.class)))
                .thenAnswer(inv -> ((LocalDate) inv.getArgument(0)).atStartOfDay((ZoneId) inv.getArgument(1)).toInstant());
        when(timezoneHelper.toEndInstant(any(LocalDate.class), any(ZoneId.class)))
                .thenAnswer(inv -> ((LocalDate) inv.getArgument(0)).plusDays(1).atStartOfDay((ZoneId) inv.getArgument(1)).toInstant().minusNanos(1));
    }

    @Test
    @DisplayName("summary: Tính toán tổng thu chi, số dư đầu kỳ, cuối kỳ và so sánh kỳ trước")
    void summary_Success() {
        ReportRepository.SummaryProjection current = mock(ReportRepository.SummaryProjection.class);
        when(current.getTotalIncome()).thenReturn(10_000_000L);
        when(current.getTotalExpense()).thenReturn(4_000_000L);
        when(current.getTransactionCount()).thenReturn(25L);

        ReportRepository.SummaryProjection prev = mock(ReportRepository.SummaryProjection.class);
        when(prev.getTotalExpense()).thenReturn(3_000_000L);

        when(reportRepository.summary(eq(userId), any(Instant.class), any(Instant.class), eq(walletId)))
                .thenReturn(current)
                .thenReturn(prev);

        Wallet wallet = Wallet.builder().id(walletId).balance(15_000_000L).build();
        when(walletRepository.findByIdAndUserIdAndIsDeleted(walletId, userId, false)).thenReturn(Optional.of(wallet));

        ReportSummaryResponse response = reportQueryService.summary(userId, "month", null, null, walletId);

        assertThat(response).isNotNull();
        assertThat(response.totalIncome()).isEqualTo(10_000_000L);
        assertThat(response.totalExpense()).isEqualTo(4_000_000L);
        assertThat(response.netIncome()).isEqualTo(6_000_000L);
        assertThat(response.transactionCount()).isEqualTo(25L);
        assertThat(response.closingBalance()).isEqualTo(15_000_000L);
        // openingBalance = closingBalance - totalIncome + totalExpense = 15m - 10m + 4m = 9m
        assertThat(response.openingBalance()).isEqualTo(9_000_000L);
        assertThat(response.vsPreviousPeriod().difference()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("byCategoryGroup: Gom nhóm theo nhóm danh mục lớn và tính tỷ trọng")
    void byCategoryGroup_Success() {
        ReportRepository.CategoryGroupAmountProjection groupProj = mock(ReportRepository.CategoryGroupAmountProjection.class);
        UUID groupId = UUID.randomUUID();
        when(groupProj.getCategoryGroupId()).thenReturn(groupId);
        when(groupProj.getCode()).thenReturn("an_uong");
        when(groupProj.getName()).thenReturn("Ăn uống");
        when(groupProj.getColor()).thenReturn("#3d6b7d");
        when(groupProj.getAmount()).thenReturn(500_000L);
        when(groupProj.getTransactionCount()).thenReturn(5L);

        when(reportRepository.byCategoryGroup(eq(userId), any(Instant.class), any(Instant.class), eq("EXPENSE"), eq(walletId)))
                .thenReturn(List.of(groupProj));

        ReportRepository.CategoryAmountProjection catProj = mock(ReportRepository.CategoryAmountProjection.class);
        UUID catId = UUID.randomUUID();
        when(catProj.getCategoryId()).thenReturn(catId);
        when(catProj.getName()).thenReturn("Ăn trưa");
        when(catProj.getAmount()).thenReturn(500_000L);

        when(reportRepository.byCategoryInGroup(eq(userId), any(Instant.class), any(Instant.class), eq("EXPENSE"), eq("an_uong"), eq(walletId)))
                .thenReturn(List.of(catProj));

        CategoryGroupBreakdownResponse res = reportQueryService.byCategoryGroup(
                userId, "month", null, null, "expense", walletId);

        assertThat(res.total()).isEqualTo(500_000L);
        assertThat(res.groups()).hasSize(1);
        assertThat(res.groups().get(0).name()).isEqualTo("Ăn uống");
        assertThat(res.groups().get(0).amount()).isEqualTo(500_000L);
        assertThat(res.groups().get(0).category()).hasSize(1);
    }

    @Test
    @DisplayName("byCategory: Cơ cấu danh mục theo cấp cha (parent level) kèm chi tiết con")
    void byCategory_ParentLevel_Success() {
        ReportRepository.CategoryParentAmountProjection parentProj = mock(ReportRepository.CategoryParentAmountProjection.class);
        UUID catId = UUID.randomUUID();
        when(parentProj.getCategoryId()).thenReturn(catId);
        when(parentProj.getCode()).thenReturn("an_uong");
        when(parentProj.getName()).thenReturn("Ăn uống");
        when(parentProj.getIconCode()).thenReturn("an_uong");
        when(parentProj.getIconPath()).thenReturn("svg_path");
        when(parentProj.getColor()).thenReturn("#3d6b7d");
        when(parentProj.getAmount()).thenReturn(1_000_000L);
        when(parentProj.getTransactionCount()).thenReturn(10L);
        when(parentProj.getHasChildren()).thenReturn(true);

        when(reportRepository.byCategoryParentLevel(eq(userId), any(Instant.class), any(Instant.class), eq("EXPENSE"), eq(10), eq(walletId)))
                .thenReturn(List.of(parentProj));

        ReportRepository.CategoryAmountProjection childProj = mock(ReportRepository.CategoryAmountProjection.class);
        when(childProj.getCategoryId()).thenReturn(UUID.randomUUID());
        when(childProj.getName()).thenReturn("Ăn sáng");
        when(childProj.getAmount()).thenReturn(400_000L);
        when(childProj.getTransactionCount()).thenReturn(4L);

        when(reportRepository.childrenDetail(eq(userId), any(Instant.class), any(Instant.class), eq("EXPENSE"), eq("an_uong"), eq(walletId)))
                .thenReturn(List.of(childProj));

        CategoryBreakdownResponse res = reportQueryService.byCategory(
                userId, "month", null, null, "expense", "parent", 10, walletId);

        assertThat(res.total()).isEqualTo(1_000_000L);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).name()).isEqualTo("Ăn uống");
        assertThat(res.items().get(0).childrenDetail()).hasSize(1);
        assertThat(res.items().get(0).childrenDetail().get(0).name()).isEqualTo("Ăn sáng");
    }

    @Test
    @DisplayName("monthlyTrend: Xu hướng theo các tháng gần nhất")
    void monthlyTrend_Success() {
        ReportRepository.MonthlyAmountProjection m1 = mock(ReportRepository.MonthlyAmountProjection.class);
        when(m1.getMonth()).thenReturn(LocalDate.now().minusMonths(1).withDayOfMonth(1));
        when(m1.getTotalIncome()).thenReturn(8_000_000L);
        when(m1.getTotalExpense()).thenReturn(3_000_000L);

        when(reportRepository.monthlyTrend(eq(userId), any(Instant.class), any(Instant.class), anyString()))
                .thenReturn(List.of(m1));

        MonthlyTrendResponse res = reportQueryService.monthlyTrend(userId, 6);

        assertThat(res.months()).hasSize(6);
        assertThat(res.averageExpense()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("home: Báo cáo tổng quan trang chủ tích hợp đầy đủ số dư, top ví, chi tiêu, gần đây, cảnh báo")
    void home_Success() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(userId))).thenReturn(20_000_000L);

        Wallet wallet1 = Wallet.builder().id(UUID.randomUUID()).name("Tiền mặt").balance(10_000_000L).type(WalletType.CASH).build();
        when(walletRepository.findAllByUserIdAndIsDeleted(eq(userId), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wallet1)));

        ReportRepository.SummaryProjection summary = mock(ReportRepository.SummaryProjection.class);
        when(summary.getTotalIncome()).thenReturn(15_000_000L);
        when(summary.getTotalExpense()).thenReturn(5_000_000L);
        when(reportRepository.summary(eq(userId), any(Instant.class), any(Instant.class), eq(walletId)))
                .thenReturn(summary);

        when(reportRepository.dailyExpense(eq(userId), any(Instant.class), any(Instant.class), anyString(), eq(walletId)))
                .thenReturn(List.of());

        when(reportRepository.byCategoryParentLevel(eq(userId), any(Instant.class), any(Instant.class), anyString(), anyInt(), eq(walletId)))
                .thenReturn(List.of());

        Transaction txn = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(wallet1.getId())
                .categoryCode("an_sang")
                .type(TxnType.EXPENSE)
                .amount(50_000L)
                .occurredAt(Instant.now())
                .name("Bánh mì")
                .build();
        when(reportRepository.eligibleTransactions(eq(userId), any(Instant.class), any(Instant.class), eq(walletId)))
                .thenReturn(List.of(txn));

        CateSumaryRes catRes = new CateSumaryRes(UUID.randomUUID(), "Ăn sáng", "an_sang", "banh_mi", "#3d6b7d");
        when(budgetCategoryHelper.findSummary("an_sang")).thenReturn(Optional.of(catRes));

        WalletSumaryRes wRes = new WalletSumaryRes(wallet1.getId(), "Tiền mặt", "default_wallet", "#3d6b7d");
        when(walletService.findSumaryByIdAndUser(wallet1.getId(), userId, true)).thenReturn(Optional.of(wRes));

        BudgetAlertRes alert = new BudgetAlertRes(
                UUID.randomUUID(), "Ăn uống", "critical", BigDecimal.valueOf(1.2), BudgetStatus.OVER_LIMIT, "Cảnh báo", "Vượt ngân sách");
        when(budgetOverviewService.alerts(userId)).thenReturn(List.of(alert));

        ReportHomeResponse res = reportQueryService.home(userId, "month", walletId);

        assertThat(res).isNotNull();
        assertThat(res.balance().personalTotal()).isEqualTo(20_000_000L);
        assertThat(res.topWallets()).hasSize(1);
        assertThat(res.periodSummary().totalIncome()).isEqualTo(15_000_000L);
        assertThat(res.recentTransactions()).hasSize(1);
        assertThat(res.recentTransactions().get(0).displayName()).isEqualTo("Bánh mì");
        assertThat(res.budgetsNeedingAttention()).hasSize(1);
        assertThat(res.budgetsNeedingAttention().get(0).status()).isEqualTo("over_limit");
    }
}
