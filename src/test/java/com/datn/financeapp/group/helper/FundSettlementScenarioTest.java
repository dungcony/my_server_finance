package com.datn.financeapp.group.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lớp kiểm thử kịch bản quyết toán quỹ theo tài liệu docs/kich_ban_quyet_toan_quy.md.
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #testCompleteFundSettlementScenario()}: Kiểm thử toàn bộ quy trình quyết toán quỹ nhóm từ lập nhóm, chi tiêu, C rời nhóm, D gia nhập, phân bổ thất thoát đến quyết toán cuối cùng.</li>
 * </ul>
 * </p>
 */
@DisplayName("Kiểm thử kịch bản quyết toán quỹ nhóm theo tài liệu docs/kich_ban_quyet_toan_quy.md")
class FundSettlementScenarioTest {

    private UUID groupId;
    private UUID userA;
    private UUID userB;
    private UUID userC;
    private UUID userD;

    private Instant tGroupCreated;
    private Instant tDay1;
    private Instant tDay2;
    private Instant tDay3Leaves;
    private Instant tDay4Joins;
    private Instant tDay5DeficitFound;
    private Instant tDay6Expense;
    private Instant tDay7Settlement;

    private List<MemberRes> members;
    private List<GTransaction> transactions;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        userC = UUID.randomUUID();
        userD = UUID.randomUUID();

        Instant baseTime = Instant.parse("2026-06-01T00:00:00Z");
        tGroupCreated = baseTime;
        tDay1 = baseTime.plus(1, ChronoUnit.DAYS);
        tDay2 = baseTime.plus(2, ChronoUnit.DAYS);
        tDay3Leaves = baseTime.plus(3, ChronoUnit.DAYS);
        tDay4Joins = baseTime.plus(4, ChronoUnit.DAYS);
        tDay5DeficitFound = baseTime.plus(5, ChronoUnit.DAYS);
        tDay6Expense = baseTime.plus(6, ChronoUnit.DAYS);
        tDay7Settlement = baseTime.plus(7, ChronoUnit.DAYS);

        members = new ArrayList<>();
        transactions = new ArrayList<>();
    }

    // dựng thành viên dạng DTO như MemberService trả về
    private MemberRes member(UUID userId, MemberRole role, MemberStatus status, Instant joinedAt, Instant leftAt) {
        return new MemberRes(UUID.randomUUID(), userId, role, status, joinedAt, leftAt, null, false);
    }

    @Test
    @DisplayName("Kịch bản trọn vẹn: Lập nhóm -> Chi tiêu -> C rời nhóm -> D vào nhóm -> Thất thoát quỹ -> Chi tiếp -> Quyết toán cuối cùng")
    void testCompleteFundSettlementScenario() {
        // tạo danh sách thành viên ban đầu A, B, C
        MemberRes memA = member(userA, MemberRole.OWNER, MemberStatus.ACTIVE, tGroupCreated, null);
        MemberRes memB = member(userB, MemberRole.MEMBER, MemberStatus.ACTIVE, tGroupCreated, null);
        MemberRes memC = member(userC, MemberRole.MEMBER, MemberStatus.ACTIVE, tGroupCreated, null);

        members.add(memA);
        members.add(memB);
        members.add(memC);

        // nộp quỹ ban đầu mỗi người 3.000.000 VNĐ
        GTransaction contribA = buildTransaction(userA, GTransactionType.CONTRIBUTION, 3000000L, MoneySource.PERSONAL, tGroupCreated.plusSeconds(60), null);
        GTransaction contribB = buildTransaction(userB, GTransactionType.CONTRIBUTION, 3000000L, MoneySource.PERSONAL, tGroupCreated.plusSeconds(120), null);
        GTransaction contribC = buildTransaction(userC, GTransactionType.CONTRIBUTION, 3000000L, MoneySource.PERSONAL, tGroupCreated.plusSeconds(180), null);

        transactions.addAll(List.of(contribA, contribB, contribC));

        // kiểm tra số dư quỹ ban đầu là 9.000.000 VNĐ
        MemberBalances balancesPhase1 = BalanceCalculator.calculateBalances(transactions, members, null);
        assertThat(balancesPhase1.get(userA).getRawContribution()).isEqualTo(3000000L);
        assertThat(balancesPhase1.get(userB).getRawContribution()).isEqualTo(3000000L);
        assertThat(balancesPhase1.get(userC).getRawContribution()).isEqualTo(3000000L);
        assertThat(balancesPhase1.totalNet()).isEqualTo(9000000L);

        // thanh toán vé máy bay và phòng: 3.153.000 VNĐ từ quỹ chung, người tham gia A, B, C
        GTransaction txFlightHotel = buildTransaction(userB, GTransactionType.EXPENSE, 3153000L, MoneySource.FUND, tGroupCreated.plus(12, ChronoUnit.HOURS),
                List.of(
                        TransactionParticipant.builder().userId(userA).shareAmount(1051000L).build(),
                        TransactionParticipant.builder().userId(userB).shareAmount(1051000L).build(),
                        TransactionParticipant.builder().userId(userC).shareAmount(1051000L).build()
                ));
        transactions.add(txFlightHotel);

        // chi tiêu chơi chung 1.206.000 VNĐ từ quỹ chung, người tham gia A, B, C
        GTransaction txDay1 = buildTransaction(userB, GTransactionType.EXPENSE, 1206000L, MoneySource.FUND, tDay1,
                List.of(
                        TransactionParticipant.builder().userId(userA).shareAmount(402000L).build(),
                        TransactionParticipant.builder().userId(userB).shareAmount(402000L).build(),
                        TransactionParticipant.builder().userId(userC).shareAmount(402000L).build()
                ));
        transactions.add(txDay1);

        // B và C đi chơi riêng 756.000 VNĐ từ quỹ chung, người tham gia B, C
        GTransaction txDay2 = buildTransaction(userB, GTransactionType.EXPENSE, 756000L, MoneySource.FUND, tDay2,
                List.of(
                        TransactionParticipant.builder().userId(userB).shareAmount(378000L).build(),
                        TransactionParticipant.builder().userId(userC).shareAmount(378000L).build()
                ));
        transactions.add(txDay2);

        // kiểm tra số dư và chi phí lũy kế trước khi C rời nhóm
        MemberBalances balancesPhase2 = BalanceCalculator.calculateBalances(transactions, members, null);
        assertThat(balancesPhase2.getShare(userA)).isEqualTo(1453000L);
        assertThat(balancesPhase2.getShare(userB)).isEqualTo(1831000L);
        assertThat(balancesPhase2.getShare(userC)).isEqualTo(1831000L);
        // quỹ còn lại = 9.000.000 - 3.153.000 - 1.206.000 - 756.000 = 3.885.000 VNĐ
        assertThat(balancesPhase2.totalNet()).isEqualTo(3885000L);

        // C yêu cầu rời nhóm và được hoàn lại số tiền chênh lệch
        long cContribution = balancesPhase2.get(userC).getRawContribution();
        long cUsed = balancesPhase2.getShare(userC);
        long cRefundAmount = cContribution - cUsed;
        assertThat(cRefundAmount).isEqualTo(1169000L);

        // tạo giao dịch hoàn tiền cho C từ quỹ
        GTransaction txRefundC = buildTransaction(userC, GTransactionType.REFUND, cRefundAmount, MoneySource.FUND, tDay3Leaves, null);
        transactions.add(txRefundC);

        // cập nhật trạng thái C rời nhóm
        // MemberRes là record bất biến nên thay bản ghi của C bằng bản đã rời nhóm
        members.replaceAll(m -> m.userId().equals(userC)
                ? member(userC, MemberRole.MEMBER, MemberStatus.LEFT, tGroupCreated, tDay3Leaves)
                : m);

        // kiểm tra số dư sau khi hoàn tiền cho C: Net Balance của C bằng đúng 0 VNĐ
        MemberBalances balancesPhase3 = BalanceCalculator.calculateBalances(transactions, members, null);
        assertThat(balancesPhase3.getNetBalance(userC)).isZero();
        // quỹ còn lại sau hoàn tiền = 3.885.000 - 1.169.000 = 2.716.000 VNĐ
        assertThat(balancesPhase3.totalNet()).isEqualTo(2716000L);

        // D tham gia nhóm và nộp vào quỹ 3.000.000 VNĐ
        MemberRes memD = member(userD, MemberRole.MEMBER, MemberStatus.ACTIVE, tDay4Joins, null);
        members.add(memD);

        GTransaction contribD = buildTransaction(userD, GTransactionType.CONTRIBUTION, 3000000L, MoneySource.PERSONAL, tDay4Joins.plusSeconds(300), null);
        transactions.add(contribD);

        // quỹ sau khi D đóng = 2.716.000 + 3.000.000 = 5.716.000 VNĐ
        MemberBalances balancesPhase4 = BalanceCalculator.calculateBalances(transactions, members, null);
        assertThat(balancesPhase4.totalNet()).isEqualTo(5716000L);

        // phát hiện thất thoát quỹ 1.000.000 VNĐ, xảy ra trước khi D tham gia nên chỉ A và B chịu
        GTransaction txDeficit = buildTransaction(userB, GTransactionType.ADJUSTMENT_DOWN, 1000000L, MoneySource.FUND, tDay5DeficitFound,
                List.of(
                        TransactionParticipant.builder().userId(userA).shareAmount(500000L).build(),
                        TransactionParticipant.builder().userId(userB).shareAmount(500000L).build()
                ));
        transactions.add(txDeficit);

        // quỹ thực tế sau thất thoát = 5.716.000 - 1.000.000 = 4.716.000 VNĐ
        MemberBalances balancesPhase5 = BalanceCalculator.calculateBalances(transactions, members, null);
        assertThat(balancesPhase5.totalNet()).isEqualTo(4716000L);

        // A, B, D đi chơi chung phát sinh chi phí 2.000.000 VNĐ từ quỹ
        GTransaction txDay6 = buildTransaction(userB, GTransactionType.EXPENSE, 2000000L, MoneySource.FUND, tDay6Expense,
                List.of(
                        TransactionParticipant.builder().userId(userA).shareAmount(666667L).build(),
                        TransactionParticipant.builder().userId(userB).shareAmount(666667L).build(),
                        TransactionParticipant.builder().userId(userD).shareAmount(666666L).build()
                ));
        transactions.add(txDay6);

        // quỹ còn lại trước quyết toán = 4.716.000 - 2.000.000 = 2.716.000 VNĐ
        MemberBalances finalBalances = BalanceCalculator.calculateBalances(transactions, members, null);
        assertThat(finalBalances.totalNet()).isEqualTo(2716000L);

        // kiểm tra tổng chi phí của từng người
        // tổng share của A bao gồm chi phí thực tế 2.119.667 VNĐ và thất thoát 500.000 VNĐ
        assertThat(finalBalances.getShare(userA)).isEqualTo(2619667L);
        // tổng share của B bao gồm chi phí thực tế 2.497.667 VNĐ và thất thoát 500.000 VNĐ
        assertThat(finalBalances.getShare(userB)).isEqualTo(2997667L);
        // C giữ nguyên chi phí khi rời nhóm
        assertThat(finalBalances.getShare(userC)).isEqualTo(1831000L);
        // D chịu chi phí phát sinh sau khi tham gia
        assertThat(finalBalances.getShare(userD)).isEqualTo(666666L);

        // kiểm tra số tiền từng người được nhận lại (Net Balance) tại thời điểm quyết toán
        long refundA = finalBalances.getNetBalance(userA);
        long refundB = finalBalances.getNetBalance(userB);
        long refundC = finalBalances.getNetBalance(userC);
        long refundD = finalBalances.getNetBalance(userD);

        assertThat(refundA).isEqualTo(380333L);
        assertThat(refundB).isEqualTo(2333L);
        assertThat(refundC).isZero();
        assertThat(refundD).isEqualTo(2333334L);

        // kiểm tra tổng tiền hoàn trả bằng đúng số dư quỹ còn lại
        long totalRefund = refundA + refundB + refundC + refundD;
        assertThat(totalRefund).isEqualTo(2716000L);
        assertThat(totalRefund).isEqualTo(finalBalances.totalNet());

        // mô phỏng hoàn tiền đợt quyết toán cuối cùng cho A, B, D
        GTransaction finalRefundA = buildTransaction(userA, GTransactionType.REFUND, refundA, MoneySource.FUND, tDay7Settlement, null);
        GTransaction finalRefundB = buildTransaction(userB, GTransactionType.REFUND, refundB, MoneySource.FUND, tDay7Settlement.plusSeconds(10), null);
        GTransaction finalRefundD = buildTransaction(userD, GTransactionType.REFUND, refundD, MoneySource.FUND, tDay7Settlement.plusSeconds(20), null);

        transactions.addAll(List.of(finalRefundA, finalRefundB, finalRefundD));

        // kiểm tra số dư quỹ sau quyết toán và số dư ròng của tất cả thành viên đều về 0 VNĐ
        MemberBalances postSettlementBalances = BalanceCalculator.calculateBalances(transactions, members, null);
        assertThat(postSettlementBalances.getNetBalance(userA)).isZero();
        assertThat(postSettlementBalances.getNetBalance(userB)).isZero();
        assertThat(postSettlementBalances.getNetBalance(userC)).isZero();
        assertThat(postSettlementBalances.getNetBalance(userD)).isZero();
        assertThat(postSettlementBalances.totalNet()).isZero();
    }

    private GTransaction buildTransaction(
            UUID transactorId,
            GTransactionType type,
            Long amount,
            MoneySource moneySource,
            Instant occurredAt,
            List<TransactionParticipant> participants
    ) {
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .transactorId(transactorId)
                .createdBy(transactorId)
                .type(type)
                .amount(amount)
                .moneySource(moneySource)
                .occurredAt(occurredAt)
                .createdAt(occurredAt)
                .status(GTransactionStatus.CONFIRMED)
                .participants(participants != null ? participants : List.of())
                .build();
    }
}
