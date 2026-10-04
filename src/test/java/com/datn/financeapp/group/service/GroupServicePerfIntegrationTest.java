package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.group.dto.request.fund.FundReconcileReq;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.performance.ServicePerfSupport;
import com.datn.financeapp.performance.ServicePerfSupport.Profile;
import com.datn.financeapp.performance.SqlCountingConfig;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Đo hiệu năng (số câu SQL + thời gian) của từng hàm trong module {@code group}: {@link GroupService},
 * {@link MemberService}, {@link MemberBehavierService}, {@link GTransactionService},
 * {@link GTransactionReviewService}, {@link FundService}, {@link ReportService}. Khung đo và cách đọc báo cáo
 * xem {@link com.datn.financeapp.performance.ServicePerfSupport}.
 *
 * <p>Hồ sơ dữ liệu của nhóm chính mà các hàm đọc nhắm vào:
 * <ul>
 *   <li><b>Tốt nhất</b>: nhóm {@value #FEW_MEMBERS} thành viên, chưa có giao dịch.</li>
 *   <li><b>Tệ nhất</b>: nhóm {@value #MANY_MEMBERS} thành viên đang hoạt động cộng {@value #PENDING_MEMBERS}
 *       người chờ duyệt, {@value #CONFIRMED_TRANSACTIONS} giao dịch đã duyệt (mỗi giao dịch chi chia cho cả nhóm)
 *       và {@value #PENDING_TRANSACTIONS} giao dịch chờ duyệt; chủ nhóm còn thuộc thêm {@value #EXTRA_GROUPS}
 *       nhóm khác để hàm liệt kê nhóm phải duyệt nhiều nhóm.</li>
 * </ul>
 * Hàm ghi và hàm phá huỷ (xoá nhóm, rời nhóm, duyệt giao dịch...) chạy trên nhóm hoặc giao dịch dựng riêng cho
 * từng lần gọi, nên không làm bẩn nhóm chính; kích thước nhóm dựng riêng cũng đổi theo hồ sơ.
 *
 * <p>{@link FundService#create} chỉ được {@code GroupService.create} gọi nội bộ và không gọi lại được trên nhóm
 * đã có quỹ, nên chi phí của nó nằm trong số đo của {@code GroupService.create}.
 *
 * <p>Không phải test hành vi, chỉ ghi báo cáo vào log và {@code target/perf-group.txt}. Chạy riêng:
 * {@code mvn test -Dtest=GroupServicePerfIntegrationTest}
 */
@Slf4j
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, SqlCountingConfig.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class GroupServicePerfIntegrationTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int FEW_MEMBERS = 2;
    static final int MANY_MEMBERS = 30;
    static final int PENDING_MEMBERS = 5;
    static final int CONFIRMED_TRANSACTIONS = 300;
    static final int PENDING_TRANSACTIONS = 30;
    static final int EXTRA_GROUPS = 19;
    // số thành viên / giao dịch dựng riêng cho các hàm thao tác hàng loạt
    private static final int BATCH_FEW = 1;
    private static final int BATCH_MANY = 10;
    // tổng người dùng dựng sẵn: đủ cho nhóm lớn nhất, người chờ duyệt, người vào nhóm và người nhận chuyển quyền
    private static final int USER_POOL_SIZE = MANY_MEMBERS + PENDING_MEMBERS + 15;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ncm91cC1wZXJmLXRlc3QtMDcwNzA3MDc=");
    }

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberBehavierService memberBehavierService;

    @Autowired
    private GTransactionService gTransactionService;

    @Autowired
    private GTransactionReviewService reviewService;

    @Autowired
    private FundService fundService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ServicePerfSupport perf = new ServicePerfSupport("group");
    private final AtomicInteger counter = new AtomicInteger();

    private UUID ownerId;
    private List<UUID> pool;
    private UUID categoryId;
    private UUID mainGroupId;

    @Test
    @DisplayName("Đo SQL và thời gian từng hàm của module group ở hồ sơ tốt nhất và tệ nhất")
    void measureGroupModuleFunctions() throws Exception {
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow();
        ownerId = createUser(userRole);
        pool = new ArrayList<>();
        for (int i = 0; i < USER_POOL_SIZE; i++)
            pool.add(createUser(userRole));
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = 'expense' LIMIT 1", UUID.class);

        // pool.get(0) là thành viên thường của nhóm chính, cũng là người tạo giao dịch chờ duyệt
        mainGroupId = newGroup(FEW_MEMBERS - 1, true);

        measureAll(Profile.TOT_NHAT, BATCH_FEW);

        growMainGroupToWorstCase();
        measureAll(Profile.TE_NHAT, BATCH_MANY);

        perf.writeReport(
                FEW_MEMBERS + " thành viên, 0 giao dịch",
                MANY_MEMBERS + " thành viên + " + PENDING_MEMBERS + " chờ duyệt, "
                        + CONFIRMED_TRANSACTIONS + " giao dịch đã duyệt + " + PENDING_TRANSACTIONS
                        + " chờ duyệt, " + (EXTRA_GROUPS + 1) + " nhóm của chủ");
        assertThat(perf.measuredFunctionCount()).isPositive();
    }

    // batch là số thành viên / giao dịch cho hàm thao tác hàng loạt: ít ở hồ sơ tốt nhất, nhiều ở hồ sơ tệ nhất
    private void measureAll(Profile profile, int batch) {
        UUID group = mainGroupId;
        measureGroupService(profile, group, batch);
        measureMemberService(profile, group, batch);
        measureMemberBehavierService(profile, group, batch);
        measureTransactionService(profile, group);
        measureReviewService(profile, batch);
        measureFundService(profile, group, batch);
        measureReportService(profile, group);
    }

    private void measureGroupService(Profile profile, UUID group, int batch) {
        AtomicReference<GroupDetailRes> fresh = new AtomicReference<>();

        perf.measure(profile, "GroupService.findNotDeletedById", () -> groupService.findNotDeletedById(group));
        perf.measure(profile, "GroupService.create",
                () -> groupService.create(ownerId, groupRequest(memberIds(0, batch), true)));
        perf.measure(profile, "GroupService.list", () -> groupService.list(ownerId));
        perf.measure(profile, "GroupService.detail", () -> groupService.detail(ownerId, group));
        perf.measure(profile, "GroupService.update",
                () -> groupService.update(ownerId, group,
                        new GroupUpdateReq("Nhóm đo hiệu năng", "Mô tả mới", null, null, null)));
        perf.measure(profile, "GroupService.pendingCount", () -> groupService.pendingCount(ownerId, group));
        perf.measure(profile, "GroupService.archive",
                () -> fresh.set(newGroupDetail(batch, true)),
                () -> groupService.archive(ownerId, fresh.get().id()));
        perf.measure(profile, "GroupService.unarchive",
                () -> {
                    fresh.set(newGroupDetail(batch, true));
                    groupService.archive(ownerId, fresh.get().id());
                },
                () -> groupService.unarchive(ownerId, fresh.get().id()));
        perf.measure(profile, "GroupService.delete",
                () -> fresh.set(newGroupDetail(batch, true)),
                () -> groupService.delete(ownerId, fresh.get().id()));
        perf.measure(profile, "GroupService.joinByCode",
                () -> fresh.set(newGroupDetail(batch, true)),
                () -> groupService.joinByCode(joiner(), new GroupJoinReq(fresh.get().inviteCode())));
    }

    private void measureMemberService(Profile profile, UUID group, int batch) {
        AtomicReference<UUID> fresh = new AtomicReference<>();
        List<UUID> activeIds = memberService.findIdAllMember(group);

        perf.measure(profile, "MemberService.create",
                () -> fresh.set(newGroup(batch, true)),
                () -> memberService.create(new MemberCreateReq(fresh.get(), joiner(), MemberRole.MEMBER, MemberStatus.ACTIVE)));
        perf.measure(profile, "MemberService.creates",
                () -> fresh.set(newGroup(batch, true)),
                () -> memberService.creates(joinerRequests(fresh.get(), batch)));
        perf.measure(profile, "MemberService.countActiveMembers", () -> memberService.countActiveMembers(group));
        perf.measure(profile, "MemberService.countPendingMembers", () -> memberService.countPendingMembers(group));
        perf.measure(profile, "MemberService.getMember", () -> memberService.getMember(group, pool.get(0), null));
        perf.measure(profile, "MemberService.getActivateMembers", () -> memberService.getActivateMembers(group));
        perf.measure(profile, "MemberService.getMembersWithStatusIn",
                () -> memberService.getMembersWithStatusIn(
                        group, List.of(MemberStatus.ACTIVE, MemberStatus.LEFT, MemberStatus.REMOVED)));
        perf.measure(profile, "MemberService.findIdAllMember", () -> memberService.findIdAllMember(group));
        perf.measure(profile, "MemberService.allMemberInGroup", () -> memberService.allMemberInGroup(group, activeIds));
        // người nằm ngoài pool thành viên và pool người chờ duyệt, nên chắc chắn không thuộc nhóm chính
        perf.measure(profile, "MemberService.assertNotInGroup",
                () -> memberService.assertNotInGroup(group, pool.get(MANY_MEMBERS + PENDING_MEMBERS)));
    }

    private void measureMemberBehavierService(Profile profile, UUID group, int batch) {
        AtomicReference<GroupDetailRes> fresh = new AtomicReference<>();

        perf.measure(profile, "MemberBehavierService.listMembers (mọi trạng thái)",
                () -> memberBehavierService.listMembers(ownerId, group, null));
        perf.measure(profile, "MemberBehavierService.listMembers (người chờ duyệt)",
                () -> memberBehavierService.listMembers(ownerId, group, MemberStatus.PENDING));
        perf.measure(profile, "MemberBehavierService.ownerAddMembers",
                () -> fresh.set(newGroupDetail(1, true)),
                () -> memberBehavierService.ownerAddMembers(
                        ownerId, fresh.get().id(), new MemberAddReq(memberIds(1, batch))));
        perf.measure(profile, "MemberBehavierService.ownerAddMember",
                () -> fresh.set(newGroupDetail(1, true)),
                () -> memberBehavierService.ownerAddMember(pool.get(1), fresh.get().id()));
        perf.measure(profile, "MemberBehavierService.leave",
                () -> fresh.set(newGroupDetail(batch, true)),
                () -> memberBehavierService.leave(pool.get(0), fresh.get().id()));
        perf.measure(profile, "MemberBehavierService.removeMember",
                () -> fresh.set(newGroupDetail(batch, true)),
                () -> memberBehavierService.removeMember(ownerId, fresh.get().id(), pool.get(0)));
        perf.measure(profile, "MemberBehavierService.transferOwnership",
                () -> fresh.set(newGroupDetail(batch, true)),
                () -> memberBehavierService.transferOwnership(ownerId, fresh.get().id(), pool.get(0)));
        perf.measure(profile, "MemberBehavierService.approve",
                () -> fresh.set(groupWithPendingMembers(batch)),
                () -> memberBehavierService.approve(ownerId, fresh.get().id(), joinerAt(0)));
        perf.measure(profile, "MemberBehavierService.approveAll",
                () -> fresh.set(groupWithPendingMembers(batch)),
                () -> memberBehavierService.approveAll(ownerId, fresh.get().id()));
        perf.measure(profile, "MemberBehavierService.reject",
                () -> fresh.set(groupWithPendingMembers(batch)),
                () -> memberBehavierService.reject(ownerId, fresh.get().id(), joinerAt(0)));
        perf.measure(profile, "MemberBehavierService.rejectAll",
                () -> fresh.set(groupWithPendingMembers(batch)),
                () -> memberBehavierService.rejectAll(ownerId, fresh.get().id()));
    }

    private void measureTransactionService(Profile profile, UUID group) {
        AtomicReference<UUID> txn = new AtomicReference<>();
        GroupTransactionFilterReq defaultPage = filter(1, 20);
        GroupTransactionFilterReq largestPage = filter(1, 100);

        perf.measure(profile, "GTransactionService.create (chi tiêu, chia đều cho cả nhóm)",
                () -> gTransactionService.create(ownerId, group, expenseRequest(ownerId)));
        perf.measure(profile, "GTransactionService.create (nộp quỹ)",
                () -> gTransactionService.create(ownerId, group, contributionRequest(ownerId)));
        perf.measure(profile, "GTransactionService.list (trang mặc định, 20 bản ghi)",
                () -> gTransactionService.list(ownerId, group, defaultPage));
        perf.measure(profile, "GTransactionService.list (trang lớn nhất, 100 bản ghi)",
                () -> gTransactionService.list(ownerId, group, largestPage));
        perf.measure(profile, "GTransactionService.myList",
                () -> gTransactionService.myList(ownerId, group, largestPage));
        perf.measure(profile, "GTransactionService.listPending",
                () -> gTransactionService.listPending(ownerId, group, 1, 100));
        perf.measure(profile, "GTransactionService.detail",
                () -> txn.set(createExpense(group)),
                () -> gTransactionService.detail(ownerId, group, txn.get()));
        perf.measure(profile, "GTransactionService.update",
                () -> txn.set(createExpense(group)),
                () -> gTransactionService.update(ownerId, group, txn.get(), updateRequest()));
        perf.measure(profile, "GTransactionService.delete",
                () -> txn.set(createExpense(group)),
                () -> gTransactionService.delete(ownerId, group, txn.get()));
        perf.measure(profile, "GTransactionService.countPendingForGroup",
                () -> gTransactionService.countPendingForGroup(group));
        perf.measure(profile, "GTransactionService.sumConfirmedAmount (toàn thời gian)",
                () -> gTransactionService.sumConfirmedAmount(group, GTransactionType.EXPENSE));
        perf.measure(profile, "GTransactionService.sumConfirmedAmount (theo kỳ)",
                () -> gTransactionService.sumConfirmedAmount(
                        group, GTransactionType.EXPENSE, Instant.now().minusSeconds(30L * 24 * 3600), Instant.now()));
    }

    // giao dịch chờ duyệt do thành viên thường (pool.get(0)) tạo trong nhóm chính
    private void measureReviewService(Profile profile, int batch) {
        UUID group = mainGroupId;
        AtomicReference<UUID> txn = new AtomicReference<>();
        AtomicReference<List<UUID>> txns = new AtomicReference<>();

        perf.measure(profile, "GTransactionReviewService.confirm",
                () -> txn.set(createPendingExpense(group)),
                () -> reviewService.confirm(ownerId, group, txn.get()));
        perf.measure(profile, "GTransactionReviewService.reject",
                () -> txn.set(createPendingExpense(group)),
                () -> reviewService.reject(ownerId, group, txn.get()));
        perf.measure(profile, "GTransactionReviewService.bulkConfirm",
                () -> txns.set(createPendingExpenses(group, batch)),
                () -> reviewService.bulkConfirm(ownerId, group, new GroupTransactionBulkReviewReq(txns.get())));
        perf.measure(profile, "GTransactionReviewService.bulkReject",
                () -> txns.set(createPendingExpenses(group, batch)),
                () -> reviewService.bulkReject(ownerId, group, new GroupTransactionBulkReviewReq(txns.get())));
    }

    private void measureFundService(Profile profile, UUID group, int batch) {
        AtomicReference<GroupDetailRes> fresh = new AtomicReference<>();
        UUID fundId = groupService.findNotDeletedById(group).fund().id();

        perf.skip("FundService.create", "chỉ GroupService.create gọi nội bộ, đã nằm trong số đo của hàm đó");
        perf.measure(profile, "FundService.updateFundKeepper",
                () -> fresh.set(newGroupDetail(batch, true)),
                () -> fundService.updateFundKeepper(ownerId, fresh.get().id(), new FundKepperUpdateReq(pool.get(0))));
        perf.measure(profile, "FundService.reconcileFund",
                () -> fresh.set(newGroupDetail(batch, true)),
                () -> fundService.reconcileFund(ownerId, fresh.get().id(),
                        new FundReconcileReq(100_000L, LocalDate.now(VIETNAM_ZONE), "Kiểm kê đo hiệu năng", List.of(pool.get(0)))));
        perf.measure(profile, "FundService.adjustBalance", () -> fundService.adjustBalance(fundId, 1L));
    }

    private void measureReportService(Profile profile, UUID group) {
        String thisMonth = YearMonth.now(VIETNAM_ZONE).toString();

        perf.measure(profile, "ReportService.getSummary (toàn thời gian)", () -> reportService.getSummary(ownerId, group, null));
        perf.measure(profile, "ReportService.getSummary (theo tháng)", () -> reportService.getSummary(ownerId, group, thisMonth));
        perf.measure(profile, "ReportService.getBalances", () -> reportService.getBalances(ownerId, group));
    }

    // đưa nhóm chính từ hồ sơ tốt nhất lên tệ nhất, làm tăng dần nên không phải dựng lại từ đầu
    private void growMainGroupToWorstCase() {
        memberBehavierService.ownerAddMembers(
                ownerId, mainGroupId, new MemberAddReq(memberIds(FEW_MEMBERS - 1, MANY_MEMBERS - FEW_MEMBERS)));

        // đổi sang chế độ phải duyệt để người vào bằng mã mời rơi vào trạng thái chờ duyệt, xong trả lại chế độ cũ
        groupService.update(ownerId, mainGroupId, new GroupUpdateReq(null, null, null, null, false));
        GroupDetailRes detail = groupService.detail(ownerId, mainGroupId);
        for (int i = 0; i < PENDING_MEMBERS; i++)
            groupService.joinByCode(pool.get(MANY_MEMBERS + i), new GroupJoinReq(detail.inviteCode()));
        groupService.update(ownerId, mainGroupId, new GroupUpdateReq(null, null, null, null, true));

        int created = 0;
        for (int i = 0; i < CONFIRMED_TRANSACTIONS; i++)
            created += trySeed(() -> gTransactionService.create(ownerId, mainGroupId,
                    expenseRequest(ownerId)));
        int createdPending = 0;
        for (int i = 0; i < PENDING_TRANSACTIONS; i++)
            createdPending += trySeed(() -> gTransactionService.create(pool.get(0), mainGroupId,
                    expenseRequest(pool.get(0))));
        for (int i = 0; i < EXTRA_GROUPS; i++)
            newGroup(1, true);

        log.warn("[PERF] dựng hồ sơ tệ nhất: {}/{} giao dịch đã duyệt, {}/{} giao dịch chờ duyệt",
                created, CONFIRMED_TRANSACTIONS, createdPending, PENDING_TRANSACTIONS);
    }

    // dữ liệu mẫu sai thì chỉ ghi log và đếm, không làm hỏng cả test
    private int trySeed(Runnable seed) {
        try {
            seed.run();
            return 1;
        } catch (RuntimeException e) {
            log.warn("[PERF] dựng dữ liệu mẫu thất bại: {}", e.toString());
            return 0;
        }
    }

    private GroupDetailRes newGroupDetail(int memberCount, boolean joinWithoutConfirm) {
        return groupService.create(ownerId, groupRequest(memberIds(0, memberCount), joinWithoutConfirm));
    }

    private UUID newGroup(int memberCount, boolean joinWithoutConfirm) {
        return newGroupDetail(memberCount, joinWithoutConfirm).id();
    }

    // nhóm cần duyệt thành viên, đã có pendingCount người xin vào ở trạng thái chờ
    private GroupDetailRes groupWithPendingMembers(int pendingCount) {
        GroupDetailRes group = newGroupDetail(1, false);
        for (int i = 0; i < pendingCount; i++)
            groupService.joinByCode(joinerAt(i), new GroupJoinReq(group.inviteCode()));
        return group;
    }

    private GroupCreateReq groupRequest(List<UUID> members, boolean joinWithoutConfirm) {
        return new GroupCreateReq(
                "Nhóm đo " + counter.incrementAndGet(), "Dữ liệu mẫu đo hiệu năng", 10_000_000L,
                true, joinWithoutConfirm, members);
    }

    // người dùng pool[from, from + count)
    private List<UUID> memberIds(int from, int count) {
        return new ArrayList<>(pool.subList(from, from + count));
    }

    // người vào nhóm dựng riêng luôn lấy từ cuối pool, không trùng với thành viên các nhóm đo
    private UUID joiner() {
        return joinerAt(0);
    }

    private UUID joinerAt(int index) {
        return pool.get(MANY_MEMBERS + index);
    }

    private List<MemberCreateReq> joinerRequests(UUID groupId, int count) {
        List<MemberCreateReq> requests = new ArrayList<>();
        for (int i = 0; i < count; i++)
            requests.add(new MemberCreateReq(groupId, joinerAt(i), MemberRole.MEMBER, MemberStatus.ACTIVE));
        return requests;
    }

    private GroupTransactionFilterReq filter(int page, int size) {
        return new GroupTransactionFilterReq(null, null, null, null, null, null, null, null, page, size);
    }

    private GroupTransactionCreateReq expenseRequest(UUID transactor) {
        return new GroupTransactionCreateReq(
                GTransactionType.EXPENSE, MoneySource.PERSONAL, 10_000L + counter.incrementAndGet(),
                null, LocalDate.now(VIETNAM_ZONE), categoryId, transactor, "Chi đo hiệu năng", null);
    }

    private GroupTransactionCreateReq contributionRequest(UUID transactor) {
        return new GroupTransactionCreateReq(
                GTransactionType.CONTRIBUTION, MoneySource.PERSONAL, 50_000L,
                null, LocalDate.now(VIETNAM_ZONE), null, transactor, "Nộp quỹ đo hiệu năng", null);
    }

    // không đổi số tiền: đổi tổng tiền thì nghiệp vụ bắt buộc gửi kèm lại danh sách người chia tiền
    private GroupTransactionUpdateReq updateRequest() {
        return new GroupTransactionUpdateReq(
                null, null, LocalDate.now(VIETNAM_ZONE), null, null, null, "Sửa đo hiệu năng", null);
    }

    // giao dịch đã duyệt của chủ nhóm, chia đều cho toàn bộ thành viên của nhóm
    private UUID createExpense(UUID group) {
        return gTransactionService.create(ownerId, group, expenseRequest(ownerId)).id();
    }

    // thành viên thường tạo giao dịch nên rơi vào trạng thái chờ duyệt
    private UUID createPendingExpense(UUID group) {
        return gTransactionService.create(pool.get(0), group, expenseRequest(pool.get(0))).id();
    }

    private List<UUID> createPendingExpenses(UUID group, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++)
            ids.add(createPendingExpense(group));
        return ids;
    }

    private UUID createUser(Role userRole) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("perf.group." + counter.incrementAndGet() + "." + UUID.randomUUID() + "@example.com")
                .firstName("Đo")
                .lastName("Nhóm " + counter.get())
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build());
        UserRole link = new UserRole(user.getId(), userRole.getId());
        link.setUser(user);
        link.setRole(userRole);
        userRoleRepository.save(link);
        return user.getId();
    }
}
