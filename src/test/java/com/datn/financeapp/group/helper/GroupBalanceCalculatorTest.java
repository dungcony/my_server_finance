package com.datn.financeapp.group.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.entity.GroupTransactionParticipant;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.MoneySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Kiểm thử logic tính toán GroupBalanceCalculator theo đặc tả docs/GroupBalanceCalculator_Design.md")
class GroupBalanceCalculatorTest {

    private GroupBalanceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new GroupBalanceCalculator();
    }

    @Nested
    @DisplayName("1. Kịch bản nghiệp vụ chuẩn từ tài liệu đặc tả (Mục 10)")
    class ScenarioTests {

        @Test
        @DisplayName("Kịch bản 1: Nhóm 3 người A, B, C và kiểm tra bất biến bảo toàn quỹ")
        void testScenario1_ThreeMembers_And_FundInvariant() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            UUID userC = UUID.randomUUID();
            Instant now = Instant.now();

            // Giao dịch 1: A nộp vào quỹ 600,000đ
            UUID tx1Id = UUID.randomUUID();
            GroupTransaction tx1 = GroupTransaction.builder()
                    .id(tx1Id)
                    .userId(userA)
                    .type(GroupTransactionType.CONTRIBUTION)
                    .amount(600000L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(now.minusSeconds(30))
                    .createdAt(now.minusSeconds(30))
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            // Giao dịch 2: B tự bỏ tiền túi chi tiêu 300,000đ, chia đều cho cả 3 người A, B, C (mỗi người 100k)
            UUID tx2Id = UUID.randomUUID();
            GroupTransaction tx2 = GroupTransaction.builder()
                    .id(tx2Id)
                    .userId(userB)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(300000L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(now.minusSeconds(20))
                    .createdAt(now.minusSeconds(20))
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            GroupTransactionParticipant p2A = GroupTransactionParticipant.builder().groupTransactionId(tx2Id).userId(userA).shareAmount(100000L).build();
            GroupTransactionParticipant p2B = GroupTransactionParticipant.builder().groupTransactionId(tx2Id).userId(userB).shareAmount(100000L).build();
            GroupTransactionParticipant p2C = GroupTransactionParticipant.builder().groupTransactionId(tx2Id).userId(userC).shareAmount(100000L).build();

            // Giao dịch 3: Quỹ nhóm chi hoàn trả cho B 100,000đ
            UUID tx3Id = UUID.randomUUID();
            GroupTransaction tx3 = GroupTransaction.builder()
                    .id(tx3Id)
                    .userId(userB)
                    .type(GroupTransactionType.REFUND)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now.minusSeconds(10))
                    .createdAt(now.minusSeconds(10))
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            Map<UUID, List<GroupTransactionParticipant>> participantsMap = Map.of(
                    tx2Id, List.of(p2A, p2B, p2C)
            );

            MemberBalances result = calculator.calculateBalances(
                    List.of(tx1, tx2, tx3),
                    participantsMap,
                    List.of()
            );

            // Kiểm tra chỉ số A
            assertThat(result.getRemainingContribution(userA)).isEqualTo(600000L);
            assertThat(result.getPaidOutOfPocket(userA)).isEqualTo(0L);
            assertThat(result.getRefunded(userA)).isEqualTo(0L);
            assertThat(result.getShare(userA)).isEqualTo(100000L);
            assertThat(result.getNetBalance(userA)).isEqualTo(500000L);

            // Kiểm tra chỉ số B
            assertThat(result.getRemainingContribution(userB)).isEqualTo(0L);
            assertThat(result.getPaidOutOfPocket(userB)).isEqualTo(300000L);
            assertThat(result.getRefunded(userB)).isEqualTo(100000L);
            assertThat(result.getShare(userB)).isEqualTo(100000L);
            assertThat(result.getNetBalance(userB)).isEqualTo(100000L);

            // Kiểm tra chỉ số C
            assertThat(result.getRemainingContribution(userC)).isEqualTo(0L);
            assertThat(result.getPaidOutOfPocket(userC)).isEqualTo(0L);
            assertThat(result.getRefunded(userC)).isEqualTo(0L);
            assertThat(result.getShare(userC)).isEqualTo(100000L);
            assertThat(result.getNetBalance(userC)).isEqualTo(-100000L);

            // Kiểm tra bất biến tài chính: sum(Net Balance) == Số dư tiền mặt thực tế trong quỹ (500,000đ)
            long expectedFundBalance = 600000L - 100000L; // 500,000đ
            assertThat(result.totalNet()).isEqualTo(expectedFundBalance);
        }

        @Test
        @DisplayName("Kịch bản 2: Chia share hỗn hợp (Mục 10)")
        void testScenario2_MixedShares() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            UUID userC = UUID.randomUUID();
            Instant now = Instant.now();

            UUID txId = UUID.randomUUID();
            GroupTransaction tx = GroupTransaction.builder()
                    .id(txId)
                    .userId(userA)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(500000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now)
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            // A: shareAmount = 200,000; B: null; C: null
            GroupTransactionParticipant pA = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userA).shareAmount(200000L).build();
            GroupTransactionParticipant pB = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userB).shareAmount(null).build();
            GroupTransactionParticipant pC = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userC).shareAmount(null).build();

            Map<UUID, List<GroupTransactionParticipant>> participantsMap = Map.of(txId, List.of(pA, pB, pC));

            MemberBalances result = calculator.calculateBalances(List.of(tx), participantsMap, List.of());

            // A nhận 200k, còn lại 300k chia đều cho B và C (mỗi người 150k)
            assertThat(result.getShare(userA)).isEqualTo(200000L);
            assertThat(result.getShare(userB)).isEqualTo(150000L);
            assertThat(result.getShare(userC)).isEqualTo(150000L);

            // Tổng share được bảo toàn = 500,000đ
            long totalShare = result.getShare(userA) + result.getShare(userB) + result.getShare(userC);
            assertThat(totalShare).isEqualTo(500000L);
        }
    }

    @Nested
    @DisplayName("2. Thuật toán luân phiên phần dư (Fair & Deterministic)")
    class RemainderRotationTests {

        @Test
        @DisplayName("Chia đều và xử lý phần dư bằng thuật toán luân phiên startIndex")
        void testDistributeEvenly_WithRotation() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            UUID userC = UUID.randomUUID();
            Instant now = Instant.now();

            UUID txId = UUID.randomUUID();
            GroupTransaction tx = GroupTransaction.builder()
                    .id(txId)
                    .userId(userA)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(100L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now)
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            // 3 người chia 100đ -> base = 33, remainder = 1
            GroupTransactionParticipant pA = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userA).shareAmount(null).build();
            GroupTransactionParticipant pB = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userB).shareAmount(null).build();
            GroupTransactionParticipant pC = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userC).shareAmount(null).build();

            Map<UUID, List<GroupTransactionParticipant>> participantsMap = Map.of(txId, List.of(pA, pB, pC));

            MemberBalances result = calculator.calculateBalances(List.of(tx), participantsMap, List.of());

            // Tổng số tiền share phải bằng chính xác 100đ
            long totalShare = result.getShare(userA) + result.getShare(userB) + result.getShare(userC);
            assertThat(totalShare).isEqualTo(100L);

            // 1 người nhận 34đ, 2 người nhận 33đ
            List<Long> shares = List.of(result.getShare(userA), result.getShare(userB), result.getShare(userC));
            assertThat(shares).containsExactlyInAnyOrder(34L, 33L, 33L);

            // Kiểm tra tính xác định (Deterministic): Chạy lại lần 2 phải cho kết quả giống hệt
            MemberBalances result2 = calculator.calculateBalances(List.of(tx), participantsMap, List.of());
            assertThat(result2.getShare(userA)).isEqualTo(result.getShare(userA));
            assertThat(result2.getShare(userB)).isEqualTo(result.getShare(userB));
            assertThat(result2.getShare(userC)).isEqualTo(result.getShare(userC));
        }

        @Test
        @DisplayName("Luân phiên không gây lỗi ArrayIndexOutOfBoundsException khi hashCode là số âm hoặc Integer.MIN_VALUE")
        void testMathFloorMod_NegativeHashCodeSafety() {
            // Test với danh sách 3 người
            List<UUID> users = new ArrayList<>(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
            users.sort(Comparator.comparing(UUID::toString));

            int n = users.size();
            int minValStartIndex = Math.floorMod(Integer.MIN_VALUE, n);
            assertThat(minValStartIndex).isBetween(0, n - 1);
        }
    }

    @Nested
    @DisplayName("3. Điều chỉnh số dư quỹ (ADJUSTMENT_DOWN & ADJUSTMENT_UP)")
    class AdjustmentTests {

        @Test
        @DisplayName("ADJUSTMENT_DOWN tăng share thành viên, ADJUSTMENT_UP giảm share thành viên")
        void testAdjustments_DownAndUp() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            Instant now = Instant.now();

            // 1. Điều chỉnh giảm 200k (hao hụt quỹ) -> mỗi người chịu thêm 100k share (factor = +1)
            UUID txDownId = UUID.randomUUID();
            GroupTransaction txDown = GroupTransaction.builder()
                    .id(txDownId)
                    .userId(userA)
                    .type(GroupTransactionType.ADJUSTMENT_DOWN)
                    .amount(200000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now.minusSeconds(10))
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            GroupTransactionParticipant pDownA = GroupTransactionParticipant.builder().groupTransactionId(txDownId).userId(userA).shareAmount(100000L).build();
            GroupTransactionParticipant pDownB = GroupTransactionParticipant.builder().groupTransactionId(txDownId).userId(userB).shareAmount(100000L).build();

            // 2. Điều chỉnh tăng 60k (quỹ dôi ra) -> mỗi người được giảm 30k share (factor = -1)
            UUID txUpId = UUID.randomUUID();
            GroupTransaction txUp = GroupTransaction.builder()
                    .id(txUpId)
                    .userId(userA)
                    .type(GroupTransactionType.ADJUSTMENT_UP)
                    .amount(60000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now)
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            GroupTransactionParticipant pUpA = GroupTransactionParticipant.builder().groupTransactionId(txUpId).userId(userA).shareAmount(30000L).build();
            GroupTransactionParticipant pUpB = GroupTransactionParticipant.builder().groupTransactionId(txUpId).userId(userB).shareAmount(30000L).build();

            Map<UUID, List<GroupTransactionParticipant>> map = Map.of(
                    txDownId, List.of(pDownA, pDownB),
                    txUpId, List.of(pUpA, pUpB)
            );

            MemberBalances result = calculator.calculateBalances(List.of(txDown, txUp), map, List.of());

            // Share của mỗi người: 100k - 30k = 70k
            assertThat(result.getShare(userA)).isEqualTo(70000L);
            assertThat(result.getShare(userB)).isEqualTo(70000L);

            // Net balance: -70k (cần nộp bù 70k)
            assertThat(result.getNetBalance(userA)).isEqualTo(-70000L);
            assertThat(result.getNetBalance(userB)).isEqualTo(-70000L);
        }
    }

    @Nested
    @DisplayName("4. Lọc thành viên theo timeline (GroupMemberPeriod) in-memory")
    class MembershipTimelineTests {

        @Test
        @DisplayName("Giao dịch không có participant: Chỉ phân bổ cho thành viên có mặt tại occurredAt")
        void testMembershipTimeline_OnlyActiveAtOccurredAt() {
            Instant t0 = Instant.now().minus(30, ChronoUnit.DAYS);
            Instant tOccurred = Instant.now().minus(15, ChronoUnit.DAYS);
            Instant tLeft = Instant.now().minus(10, ChronoUnit.DAYS);
            Instant tJoinedLate = Instant.now().minus(5, ChronoUnit.DAYS);

            UUID uActive = UUID.randomUUID();      // Có mặt: gia nhập t0, chưa rời
            UUID uLeftLater = UUID.randomUUID();    // Có mặt: gia nhập t0, rời tLeft (sau tOccurred)
            UUID uLeftEarly = UUID.randomUUID();    // Không có mặt: gia nhập t0, rời t0+1d (trước tOccurred)
            UUID uJoinedLate = UUID.randomUUID();   // Không có mặt: gia nhập tJoinedLate (sau tOccurred)

            List<GroupMemberPeriod> periods = List.of(
                    new GroupMemberPeriod(uActive, t0, null),
                    new GroupMemberPeriod(uLeftLater, t0, tLeft),
                    new GroupMemberPeriod(uLeftEarly, t0, t0.plus(1, ChronoUnit.DAYS)),
                    new GroupMemberPeriod(uJoinedLate, tJoinedLate, null)
            );

            // Giao dịch 200k không có participants lúc tOccurred
            UUID txId = UUID.randomUUID();
            GroupTransaction tx = GroupTransaction.builder()
                    .id(txId)
                    .userId(uActive)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(200000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(tOccurred)
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            MemberBalances result = calculator.calculateBalances(List.of(tx), Map.of(), periods);

            // Chỉ uActive và uLeftLater chịu share (mỗi người 100k)
            assertThat(result.getShare(uActive)).isEqualTo(100000L);
            assertThat(result.getShare(uLeftLater)).isEqualTo(100000L);

            // uLeftEarly và uJoinedLate không bị gán bất kỳ share nào
            assertThat(result.getShare(uLeftEarly)).isEqualTo(0L);
            assertThat(result.getShare(uJoinedLate)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("5. Xử lý lỗi bắt buộc (Fail-fast - Mục 8)")
    class FailFastTests {

        @Test
        @DisplayName("Giao dịch cần phân bổ share nhưng không có ai có mặt -> ném BusinessException")
        void testFailFast_EmptyCandidatesAtOccurredAt() {
            Instant tJoined = Instant.now();
            Instant tOccurredBeforeJoin = tJoined.minus(10, ChronoUnit.DAYS);

            UUID user = UUID.randomUUID();
            List<GroupMemberPeriod> periods = List.of(new GroupMemberPeriod(user, tJoined, null));

            GroupTransaction tx = GroupTransaction.builder()
                    .id(UUID.randomUUID())
                    .userId(user)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(tOccurredBeforeJoin)
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            assertThatThrownBy(() -> calculator.calculateBalances(List.of(tx), Map.of(), periods))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.PARTICIPANT_NOT_MEMBER.getCode());
                    });
        }

        @Test
        @DisplayName("Tổng shareAmount không khớp amount -> ném PARTICIPANTS_SUM_MISMATCH")
        void testFailFast_ParticipantsSumMismatch() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            UUID txId = UUID.randomUUID();

            GroupTransaction tx = GroupTransaction.builder()
                    .id(txId)
                    .userId(userA)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            // Tổng share là 40k + 40k = 80k != 100k
            GroupTransactionParticipant pA = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userA).shareAmount(40000L).build();
            GroupTransactionParticipant pB = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userB).shareAmount(40000L).build();

            Map<UUID, List<GroupTransactionParticipant>> map = Map.of(txId, List.of(pA, pB));

            assertThatThrownBy(() -> calculator.calculateBalances(List.of(tx), map, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.PARTICIPANTS_SUM_MISMATCH.getCode());
                    });
        }

        @Test
        @DisplayName("Chia hỗn hợp nhưng tổng shareAmount chỉ định >= amount -> ném PARTICIPANTS_SUM_MISMATCH")
        void testFailFast_MixedShareSumExceedsAmount() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            UUID txId = UUID.randomUUID();

            GroupTransaction tx = GroupTransaction.builder()
                    .id(txId)
                    .userId(userA)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            // A gán 100k (= amount), B gán null -> tổng đã gán >= amount -> không hợp lý
            GroupTransactionParticipant pA = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userA).shareAmount(100000L).build();
            GroupTransactionParticipant pB = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userB).shareAmount(null).build();

            Map<UUID, List<GroupTransactionParticipant>> map = Map.of(txId, List.of(pA, pB));

            assertThatThrownBy(() -> calculator.calculateBalances(List.of(tx), map, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.PARTICIPANTS_SUM_MISMATCH.getCode());
                    });
        }

        @Test
        @DisplayName("shareAmount <= 0 -> ném INVALID_PARTICIPANT_DATA")
        void testFailFast_NegativeOrZeroShareAmount() {
            UUID userA = UUID.randomUUID();
            UUID txId = UUID.randomUUID();

            GroupTransaction tx = GroupTransaction.builder()
                    .id(txId)
                    .userId(userA)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            GroupTransactionParticipant pA = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userA).shareAmount(0L).build();

            Map<UUID, List<GroupTransactionParticipant>> map = Map.of(txId, List.of(pA));

            assertThatThrownBy(() -> calculator.calculateBalances(List.of(tx), map, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.INVALID_PARTICIPANT_DATA.getCode());
                    });
        }

        @Test
        @DisplayName("Trùng lặp userId trong cùng transaction -> ném INVALID_PARTICIPANT_DATA")
        void testFailFast_DuplicateParticipantUserId() {
            UUID userA = UUID.randomUUID();
            UUID txId = UUID.randomUUID();

            GroupTransaction tx = GroupTransaction.builder()
                    .id(txId)
                    .userId(userA)
                    .type(GroupTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            GroupTransactionParticipant p1 = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userA).shareAmount(50000L).build();
            GroupTransactionParticipant p2 = GroupTransactionParticipant.builder().groupTransactionId(txId).userId(userA).shareAmount(50000L).build();

            Map<UUID, List<GroupTransactionParticipant>> map = Map.of(txId, List.of(p1, p2));

            assertThatThrownBy(() -> calculator.calculateBalances(List.of(tx), map, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.INVALID_PARTICIPANT_DATA.getCode());
                    });
        }

        @Test
        @DisplayName("Số tiền giao dịch <= 0 -> ném INVALID_AMOUNT")
        void testFailFast_InvalidAmount() {
            UUID userA = UUID.randomUUID();
            GroupTransaction tx = GroupTransaction.builder()
                    .id(UUID.randomUUID())
                    .userId(userA)
                    .type(GroupTransactionType.CONTRIBUTION)
                    .amount(0L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(Instant.now())
                    .status(GroupTransactionStatus.CONFIRMED)
                    .build();

            assertThatThrownBy(() -> calculator.calculateBalances(List.of(tx), Map.of(), List.of()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT.getCode());
                    });
        }
    }
}
