package com.datn.financeapp.group.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Kiểm thử logic tính toán BalanceCalculator theo đặc tả docs/BalanceCalculator_Design.md")
class BalanceCalculatorTest {

    private BalanceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new BalanceCalculator();
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
            GTransaction tx1 = GTransaction.builder()
                    .id(tx1Id)
                    .transactorId(userA)
                    .type(GTransactionType.CONTRIBUTION)
                    .amount(600000L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(now.minusSeconds(30))
                    .createdAt(now.minusSeconds(30))
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            // Giao dịch 2: B tự bỏ tiền túi chi tiêu 300,000đ, chia đều cho cả 3 người A, B, C (mỗi người 100k)
            UUID tx2Id = UUID.randomUUID();
            GTransaction tx2 = GTransaction.builder()
                    .id(tx2Id)
                    .transactorId(userB)
                    .type(GTransactionType.EXPENSE)
                    .amount(300000L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(now.minusSeconds(20))
                    .createdAt(now.minusSeconds(20))
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            TransactionParticipant p2A = TransactionParticipant.builder().userId(userA).shareAmount(100000L).build();
            TransactionParticipant p2B = TransactionParticipant.builder().userId(userB).shareAmount(100000L).build();
            TransactionParticipant p2C = TransactionParticipant.builder().userId(userC).shareAmount(100000L).build();

            // Giao dịch 3: Quỹ nhóm chi hoàn trả cho B 100,000đ
            UUID tx3Id = UUID.randomUUID();
            GTransaction tx3 = GTransaction.builder()
                    .id(tx3Id)
                    .transactorId(userB)
                    .type(GTransactionType.REFUND)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now.minusSeconds(10))
                    .createdAt(now.minusSeconds(10))
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            tx2.setParticipants(List.of(p2A, p2B, p2C));

            MemberBalances result = BalanceCalculator.calculateBalances(
                    List.of(tx1, tx2, tx3), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(Instant.now().minusSeconds(100)).build()), null
            );

            // Kiểm tra chỉ số A
            assertThat(result.get(userA).getRawContribution()).isEqualTo(600000L);
            assertThat(result.getPaidOutOfPocket(userA)).isEqualTo(0L);
            assertThat(result.getRefunded(userA)).isEqualTo(0L);
            assertThat(result.getShare(userA)).isEqualTo(100000L);
            assertThat(result.getNetBalance(userA)).isEqualTo(500000L);

            // Kiểm tra chỉ số B
            assertThat(result.get(userB).getRawContribution()).isEqualTo(0L);
            assertThat(result.getPaidOutOfPocket(userB)).isEqualTo(300000L);
            assertThat(result.getRefunded(userB)).isEqualTo(100000L);
            assertThat(result.getShare(userB)).isEqualTo(100000L);
            assertThat(result.getNetBalance(userB)).isEqualTo(100000L);

            // Kiểm tra chỉ số C
            assertThat(result.get(userC).getRawContribution()).isEqualTo(0L);
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
            GTransaction tx = GTransaction.builder()
                    .id(txId)
                    .transactorId(userA)
                    .type(GTransactionType.EXPENSE)
                    .amount(500000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now)
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            // A: shareAmount = 200,000; B: null; C: null
            TransactionParticipant pA = TransactionParticipant.builder().userId(userA).shareAmount(200000L).build();
            TransactionParticipant pB = TransactionParticipant.builder().userId(userB).shareAmount(null).build();
            TransactionParticipant pC = TransactionParticipant.builder().userId(userC).shareAmount(null).build();

            tx.setParticipants(List.of(pA, pB, pC));

            MemberBalances result = BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(Instant.now().minusSeconds(100)).build()), null);

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
            GTransaction tx = GTransaction.builder()
                    .id(txId)
                    .transactorId(userA)
                    .type(GTransactionType.EXPENSE)
                    .amount(100L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now)
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            // 3 người chia 100đ -> base = 33, remainder = 1
            TransactionParticipant pA = TransactionParticipant.builder().userId(userA).shareAmount(null).build();
            TransactionParticipant pB = TransactionParticipant.builder().userId(userB).shareAmount(null).build();
            TransactionParticipant pC = TransactionParticipant.builder().userId(userC).shareAmount(null).build();

            tx.setParticipants(List.of(pA, pB, pC));

            MemberBalances result = BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(Instant.now().minusSeconds(100)).build()), null);

            // Tổng số tiền share phải bằng chính xác 100đ
            long totalShare = result.getShare(userA) + result.getShare(userB) + result.getShare(userC);
            assertThat(totalShare).isEqualTo(100L);

            // 1 người nhận 34đ, 2 người nhận 33đ
            List<Long> shares = List.of(result.getShare(userA), result.getShare(userB), result.getShare(userC));
            assertThat(shares).containsExactlyInAnyOrder(34L, 33L, 33L);

            // Kiểm tra tính xác định (Deterministic): Chạy lại lần 2 phải cho kết quả giống hệt
            MemberBalances result2 = BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userC).joinedAt(Instant.now().minusSeconds(100)).build()), null);
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
            GTransaction txDown = GTransaction.builder()
                    .id(txDownId)
                    .transactorId(userA)
                    .type(GTransactionType.ADJUSTMENT_DOWN)
                    .amount(200000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now.minusSeconds(10))
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            TransactionParticipant pDownA = TransactionParticipant.builder().userId(userA).shareAmount(100000L).build();
            TransactionParticipant pDownB = TransactionParticipant.builder().userId(userB).shareAmount(100000L).build();

            // 2. Điều chỉnh tăng 60k (quỹ dôi ra) -> mỗi người được giảm 30k share (factor = -1)
            UUID txUpId = UUID.randomUUID();
            GTransaction txUp = GTransaction.builder()
                    .id(txUpId)
                    .transactorId(userA)
                    .type(GTransactionType.ADJUSTMENT_UP)
                    .amount(60000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now)
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            TransactionParticipant pUpA = TransactionParticipant.builder().userId(userA).shareAmount(30000L).build();
            TransactionParticipant pUpB = TransactionParticipant.builder().userId(userB).shareAmount(30000L).build();

            txDown.setParticipants(List.of(pDownA, pDownB));
            txUp.setParticipants(List.of(pUpA, pUpB));

            MemberBalances result = BalanceCalculator.calculateBalances(List.of(txDown, txUp), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(userB).joinedAt(Instant.now().minusSeconds(100)).build()), null);

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

            List<Member> members = List.of(
                    Member.builder().userId(uActive).joinedAt(t0).build(),
                    Member.builder().userId(uLeftLater).joinedAt(t0).leftAt(tLeft).build(),
                    Member.builder().userId(uLeftEarly).joinedAt(t0).leftAt(t0.plus(1, ChronoUnit.DAYS)).build(),
                    Member.builder().userId(uJoinedLate).joinedAt(tJoinedLate).build()
            );

            // Giao dịch 200k không có participants lúc tOccurred
            UUID txId = UUID.randomUUID();
            GTransaction tx = GTransaction.builder()
                    .id(txId)
                    .transactorId(uActive)
                    .type(GTransactionType.EXPENSE)
                    .amount(200000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(tOccurred)
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            MemberBalances result = BalanceCalculator.calculateBalances(List.of(tx), members, null);

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
            List<Member> members = List.of(Member.builder().userId(user).joinedAt(tJoined).build());

            GTransaction tx = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(user)
                    .type(GTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(tOccurredBeforeJoin)
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), members, null))
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

            GTransaction tx = GTransaction.builder()
                    .id(txId)
                    .transactorId(userA)
                    .type(GTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            // Tổng share là 40k + 40k = 80k != 100k
            TransactionParticipant pA = TransactionParticipant.builder().userId(userA).shareAmount(40000L).build();
            TransactionParticipant pB = TransactionParticipant.builder().userId(userB).shareAmount(40000L).build();

            tx.setParticipants(List.of(pA, pB));

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
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

            GTransaction tx = GTransaction.builder()
                    .id(txId)
                    .transactorId(userA)
                    .type(GTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            // A gán 100k (= amount), B gán null -> tổng đã gán >= amount -> không hợp lý
            TransactionParticipant pA = TransactionParticipant.builder().userId(userA).shareAmount(100000L).build();
            TransactionParticipant pB = TransactionParticipant.builder().userId(userB).shareAmount(null).build();

            tx.setParticipants(List.of(pA, pB));

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
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

            GTransaction tx = GTransaction.builder()
                    .id(txId)
                    .transactorId(userA)
                    .type(GTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            TransactionParticipant pA = TransactionParticipant.builder().userId(userA).shareAmount(0L).build();

            tx.setParticipants(List.of(pA));

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
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

            GTransaction tx = GTransaction.builder()
                    .id(txId)
                    .transactorId(userA)
                    .type(GTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            TransactionParticipant p1 = TransactionParticipant.builder().userId(userA).shareAmount(50000L).build();
            TransactionParticipant p2 = TransactionParticipant.builder().userId(userA).shareAmount(50000L).build();

            tx.setParticipants(List.of(p1, p2));

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
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
            GTransaction tx = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(userA)
                    .type(GTransactionType.CONTRIBUTION)
                    .amount(0L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT.getCode());
                    });
        }
    }

    @Nested
    @DisplayName("6. Xác thực bảo toàn tài chính & kiểm soát nguồn tiền (Validation & Security Tests)")
    class ValidationAndSecurityTests {

        @Test
        @DisplayName("CONTRIBUTION có transactorId == null -> ném PAYER_NOT_MEMBER")
        void testContribution_ThrowsWhenTransactorNull() {
            GTransaction tx = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(null)
                    .type(GTransactionType.CONTRIBUTION)
                    .amount(50000L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.PAYER_NOT_MEMBER.getCode());
                    });
        }

        @Test
        @DisplayName("CONTRIBUTION với moneySource == null -> Không ném lỗi, rawContribution cộng bình thường (Option 1)")
        void testContribution_MoneySourceNull_AccumulatesNormally() {
            UUID userA = UUID.randomUUID();
            GTransaction tx = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(userA)
                    .type(GTransactionType.CONTRIBUTION)
                    .amount(250000L)
                    .moneySource(null)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            MemberBalances result = BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null);
            assertThat(result.get(userA).getRawContribution()).isEqualTo(250000L);
            assertThat(result.getNetBalance(userA)).isEqualTo(250000L);
        }

        @Test
        @DisplayName("CONTRIBUTION có moneySource == FUND -> ném MONEY_SOURCE_INVALID (Option 1)")
        void testContribution_ThrowsWhenMoneySourceIsFund() {
            UUID userA = UUID.randomUUID();
            GTransaction tx = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(userA)
                    .type(GTransactionType.CONTRIBUTION)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.MONEY_SOURCE_INVALID.getCode());
                    });
        }

        @Test
        @DisplayName("EXPENSE nguồn tiền PERSONAL nhưng transactorId == null -> ném PAYER_NOT_MEMBER")
        void testExpensePersonal_ThrowsWhenTransactorNull() {
            GTransaction tx = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(null)
                    .type(GTransactionType.EXPENSE)
                    .amount(150000L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            UUID userA = UUID.randomUUID();
            TransactionParticipant p = TransactionParticipant.builder().userId(userA).shareAmount(150000L).build();
            tx.setParticipants(List.of(p));

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.PAYER_NOT_MEMBER.getCode());
                    });
        }

        @Test
        @DisplayName("EXPENSE nguồn tiền FUND cho phép transactorId == null vì chi từ quỹ chung")
        void testExpenseFund_AllowsTransactorNull() {
            UUID userA = UUID.randomUUID();
            GTransaction tx = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(null)
                    .type(GTransactionType.EXPENSE)
                    .amount(100000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            TransactionParticipant p = TransactionParticipant.builder().userId(userA).shareAmount(100000L).build();
            tx.setParticipants(List.of(p));

            MemberBalances result = BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(userA).joinedAt(Instant.now().minusSeconds(100)).build(), Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null);
            assertThat(result.getShare(userA)).isEqualTo(100000L);
            assertThat(result.getNetBalance(userA)).isEqualTo(-100000L);
        }

        @Test
        @DisplayName("REFUND có transactorId == null -> ném PAYER_NOT_MEMBER")
        void testRefund_ThrowsWhenTransactorNull() {
            GTransaction tx = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(null)
                    .type(GTransactionType.REFUND)
                    .amount(80000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(Instant.now())
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            assertThatThrownBy(() -> BalanceCalculator.calculateBalances(List.of(tx), List.of(Member.builder().userId(UUID.randomUUID()).joinedAt(Instant.now().minusSeconds(100)).build()), null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(ErrorCode.PAYER_NOT_MEMBER.getCode());
                    });
        }

        @Test
        @DisplayName("REFUND trả lại tiền đã góp làm số dư ròng của người nhận giảm đúng bằng số tiền trả")
        void testRefund_ReturningContribution_ReducesNetBalance() {
            UUID userA = UUID.randomUUID();
            Instant now = Instant.now();
            GTransaction contribution = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(userA)
                    .type(GTransactionType.CONTRIBUTION)
                    .amount(1_000_000L)
                    .moneySource(MoneySource.PERSONAL)
                    .occurredAt(now.minusSeconds(20))
                    .createdAt(now.minusSeconds(20))
                    .status(GTransactionStatus.CONFIRMED)
                    .build();
            GTransaction refund = GTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactorId(userA)
                    .type(GTransactionType.REFUND)
                    .amount(400_000L)
                    .moneySource(MoneySource.FUND)
                    .occurredAt(now.minusSeconds(10))
                    .createdAt(now.minusSeconds(10))
                    .status(GTransactionStatus.CONFIRMED)
                    .build();

            MemberBalances result = BalanceCalculator.calculateBalances(List.of(contribution, refund),
                    List.of(Member.builder().userId(userA).joinedAt(now.minusSeconds(100)).build()), null);

            assertThat(result.get(userA).getRawContribution()).isEqualTo(1_000_000L);
            assertThat(result.getRefunded(userA)).isEqualTo(400_000L);
            assertThat(result.getNetBalance(userA)).isEqualTo(600_000L);
        }

        @Test
        @DisplayName("MemberBalances.get(nonExistentUserId) trả về instance độc lập, không làm biến đổi state toàn cục")
        void testMemberBalances_GetNonExistentUser_ReturnsIsolatedInstance() {
            MemberBalances balances = BalanceCalculator.calculateBalances(List.of(), List.of(), null);

            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();

            // Thao tác gọi addShare trên user chưa từng có giao dịch
            MemberBalanceAccumulator acc1 = balances.get(user1);
            assertThat(acc1.getShare()).isEqualTo(0L);
            acc1.addShare(999999L);
            assertThat(acc1.getShare()).isEqualTo(999999L);

            // Kiểm tra user khác chưa có giao dịch không bị ảnh hưởng (share vẫn = 0)
            MemberBalanceAccumulator acc2 = balances.get(user2);
            assertThat(acc2.getShare()).isEqualTo(0L);

            // Lấy lại user1 từ balances vẫn trả về accumulator mới sạch (chưa bị persist ngầm)
            assertThat(balances.get(user1).getShare()).isEqualTo(0L);
        }
    }
}
