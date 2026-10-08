package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.entity.MemberBalance;
import com.datn.financeapp.group.repository.MemberBalanceRepository;
import com.datn.financeapp.performance.SqlCountingConfig;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
 * Đo hiệu năng các hàm của module {@code group} khi số giao dịch lên tới {@value #MILLION} bản ghi, theo hai
 * kịch bản. Dữ liệu được chèn thẳng bằng SQL ({@code generate_series}) thay vì gọi service, vì gọi service cho
 * một triệu giao dịch mất nhiều giờ.
 *
 * <ul>
 *   <li><b>Kịch bản A — một nhóm khổng lồ</b>: một nhóm {@value #A_MEMBERS} thành viên, số giao dịch đã duyệt
 *       tăng dần 1.000, 10.000, 100.000, 1.000.000 (mỗi giao dịch chia cho {@value #A_PARTICIPANTS} người). Đây là
 *       chặn trên: những hàm nạp toàn bộ giao dịch của nhóm ({@code findConfirmedTransactions},
 *       {@code getBalances}) có thể hết bộ nhớ ở mức cuối, và test ghi lại điều đó thay vì bỏ qua.</li>
 *   <li><b>Kịch bản B — nhiều nhóm</b>: mỗi nhóm {@value #B_TXNS_PER_GROUP} giao dịch, số nhóm tăng dần
 *       50, 500, 5.000 (tổng 10.000, 100.000, 1.000.000 giao dịch). Hàm luôn được đo trên cùng một nhóm kích thước
 *       không đổi, nên thời gian đứng yên nghĩa là chỉ mục theo nhóm hoạt động tốt.</li>
 * </ul>
 *
 * <p>Chỉ chạy khi có cờ {@code -Dperf.scale=true} để không lẫn vào lượt test thường. Nên cấp thêm bộ nhớ cho JVM
 * test để phân biệt "hết bộ nhớ thật" với "heap mặc định nhỏ":
 * {@code mvn test -Dtest=GroupScalePerfIntegrationTest -Dperf.scale=true -DargLine="-Xmx6g"}. Mỗi kịch bản chạy
 * từ vài phút đến chục phút. Ở mức từ 100.000 trở lên mỗi hàm chỉ đo một lần (không khởi động nguội) để khỏi nhân
 * đôi thời gian. Báo cáo ghi vào log (WARN) và {@code target/perf-group-scale-a.txt}, {@code ...-b.txt}.
 */
@Slf4j
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "perf.scale", matches = "true")
@org.springframework.context.annotation.Import({TestRedisConfig.class, SqlCountingConfig.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class GroupScalePerfIntegrationTest {

    static final long MILLION = 1_000_000L;
    static final int A_MEMBERS = 30;
    static final int A_PARTICIPANTS = 5;
    static final int A_PENDING = 100;
    static final int B_GROUPS = 5_000;
    static final int B_TXNS_PER_GROUP = 200;
    static final int B_MEMBERS_PER_GROUP = 6;
    static final int B_PARTICIPANTS = 3;
    private static final long[] A_SCALES = {1_000, 10_000, 100_000, MILLION};
    private static final int[] B_GROUP_SCALES = {50, 500, B_GROUPS};
    private static final int CHUNK = 100_000;
    private static final int B_GROUPS_PER_CHUNK = CHUNK / B_TXNS_PER_GROUP;
    private static final long WARM_UP_UP_TO = 10_000;
    private static final int POOL_SIZE = 30;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ncm91cC1zY2FsZS10ZXN0LTA4MDgwODA4");
    }

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private GTransactionService gTransactionService;

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

    @Autowired
    private MemberBalanceRepository memberBalanceRepository;

    @FunctionalInterface
    private interface Step {
        void run() throws Exception;
    }

    private record Cell(long sql, long ms, String error) {
    }

    private final Map<String, Map<Long, Cell>> results = new LinkedHashMap<>();
    private final Set<Long> scalesSeen = new LinkedHashSet<>();

    private List<UUID> pool;
    private UUID categoryId;
    private String poolLiteral;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS perf_groups");
        jdbcTemplate.execute("TRUNCATE group_transaction_participants, group_transactions, group_members,"
                + " group_funds, groups CASCADE");
        results.clear();
        scalesSeen.clear();
        createUserPool();
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = 'expense' LIMIT 1", UUID.class);
    }

    @Test
    @DisplayName("Kịch bản A: một nhóm có tới 1.000.000 giao dịch")
    void oneHugeGroup() throws Exception {
        UUID owner = pool.get(0);
        UUID group = insertGroupWithMembers(owner, pool.subList(0, A_MEMBERS));

        long seeded = 0;
        for (long scale : A_SCALES) {
            log.warn("[PERF-SCALE] Đang chèn tới {} giao dịch...", scale);
            seeded = insertTransactionsForGroup(group, owner, seeded + 1, scale);
            insertPendingTransactions(group, owner);
            analyze();
            measureGroup(scale, group, owner);
        }

        writeReport("a", "Kịch bản A — MỘT NHÓM, " + A_MEMBERS + " thành viên, mỗi giao dịch chia cho "
                + A_PARTICIPANTS + " người, " + A_PENDING + " giao dịch chờ duyệt");
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("Kịch bản B: 1.000.000 giao dịch rải trên 5.000 nhóm")
    void manyGroups() throws Exception {
        jdbcTemplate.execute("CREATE TABLE perf_groups AS SELECT g AS n, gen_random_uuid() AS id"
                + " FROM generate_series(1, " + B_GROUPS + ") AS g");
        jdbcTemplate.execute("ALTER TABLE perf_groups ADD PRIMARY KEY (n)");

        int created = 0;
        for (int groupScale : B_GROUP_SCALES) {
            log.warn("[PERF-SCALE] Đang chèn tới {} nhóm...", groupScale);
            created = insertGroupRange(created + 1, groupScale);
            analyze();
            // nhóm số 1 luôn giữ nguyên kích thước, chủ nhóm tương ứng ở vị trí n % POOL_SIZE trong pool
            UUID group = jdbcTemplate.queryForObject("SELECT id FROM perf_groups WHERE n = 1", UUID.class);
            measureGroup((long) groupScale * B_TXNS_PER_GROUP, group, pool.get(1 % POOL_SIZE));
        }

        writeReport("b", "Kịch bản B — NHIỀU NHÓM, mỗi nhóm " + B_TXNS_PER_GROUP + " giao dịch và "
                + B_MEMBERS_PER_GROUP + " thành viên; số liệu ở cột là TỔNG giao dịch toàn hệ thống");
        assertThat(results).isNotEmpty();
    }

    // các hàm nhẹ đo trước, hàm nạp toàn bộ giao dịch đo sau cùng vì có thể hết bộ nhớ
    private void measureGroup(long scale, UUID group, UUID operator) {
        scalesSeen.add(scale);
        String thisMonth = YearMonth.now(VIETNAM_ZONE).toString();
        LocalDate monthStart = YearMonth.now(VIETNAM_ZONE).atDay(1);
        LocalDate monthEnd = YearMonth.now(VIETNAM_ZONE).atEndOfMonth();

        measure(scale, "GTransactionService.countPendingForGroup", () -> gTransactionService.countPendingForGroup(group));
        measure(scale, "GTransactionService.sumConfirmedAmount (toàn thời gian)",
                () -> gTransactionService.sumConfirmedAmount(group, GTransactionType.EXPENSE));
        measure(scale, "GTransactionService.list (trang 1, 20 bản ghi)",
                () -> gTransactionService.list(operator, group, filter(1, 20, null, null)));
        measure(scale, "GTransactionService.list (trang 1, 100 bản ghi)",
                () -> gTransactionService.list(operator, group, filter(1, 100, null, null)));
        measure(scale, "GTransactionService.list (trang sâu: trang 1000 x 100)",
                () -> gTransactionService.list(operator, group, filter(1000, 100, null, null)));
        measure(scale, "GTransactionService.list (lọc theo tháng này, 100 bản ghi)",
                () -> gTransactionService.list(operator, group, filter(1, 100, monthStart, monthEnd)));
        measure(scale, "GTransactionService.listPending", () -> gTransactionService.listPending(operator, group, 1, 100));
        measure(scale, "ReportService.getSummary (toàn thời gian)", () -> reportService.getSummary(operator, group, null));
        measure(scale, "ReportService.getSummary (theo tháng)", () -> reportService.getSummary(operator, group, thisMonth));
        measure(scale, "ReportService.getBalances", () -> reportService.getBalances(operator, group));

        // người nhận chọn ngoài phần tính giờ; hoàn 1đ cho người có số dư ròng lớn nhất để chắc chắn qua hạn mức
        UUID recipient = richestMember(group);
        measure(scale, "GTransactionService.create (REFUND 1đ)", () -> gTransactionService.create(operator, group,
                new GroupTransactionCreateReq(GTransactionType.REFUND, MoneySource.FUND, 1L, Instant.now(),
                        null, null, recipient, "Đo hoàn tiền", List.of())));
    }

    private UUID richestMember(UUID group) {
        return memberBalanceRepository.findByGroupId(group).stream()
                .max(Comparator.comparingLong(b ->
                        b.getContribution() + b.getPaidOutOfPocket() - b.getRefund() - b.getShare()))
                .map(MemberBalance::getUserId)
                .orElseGet(() -> pool.get(0));
    }

    // khởi động nguội chỉ ở mức nhỏ; Throwable gồm cả OutOfMemoryError để một hàm hết bộ nhớ không làm mất cả báo cáo
    private void measure(long scale, String label, Step action) {
        Cell cell;
        try {
            if (scale <= WARM_UP_UP_TO)
                action.run();
            long sqlBefore = SqlCountingConfig.sqlCount();
            long start = System.nanoTime();
            action.run();
            cell = new Cell(
                    SqlCountingConfig.sqlCount() - sqlBefore, (System.nanoTime() - start) / 1_000_000, null);
        } catch (Throwable t) {
            cell = new Cell(-1, -1, describe(t));
        }
        results.computeIfAbsent(label, key -> new LinkedHashMap<>()).put(scale, cell);
        log.warn("[PERF-SCALE] {} giao dịch | {} | {}", scale, label,
                cell.error() == null ? cell.sql() + " SQL / " + cell.ms() + "ms" : "LỖI: " + cell.error());
    }

    private GroupTransactionFilterReq filter(int page, int size, LocalDate from, LocalDate to) {
        return new GroupTransactionFilterReq(null, null, null, null, null, null, from, to, page, size);
    }

    // ---------------------------------------- dữ liệu ----------------------------------------

    private void createUserPool() {
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow();
        pool = new ArrayList<>();
        for (int i = 0; i < POOL_SIZE; i++) {
            User user = userRepository.save(User.builder()
                    .id(UUID.randomUUID())
                    .email("perf.scale." + i + "." + UUID.randomUUID() + "@example.com")
                    .firstName("Đo")
                    .lastName("Quy mô " + i)
                    .status(UserStatus.ACTIVE)
                    .createdAt(Instant.now())
                    .build());
            UserRole link = new UserRole(user.getId(), userRole.getId());
            link.setUser(user);
            link.setRole(userRole);
            userRoleRepository.save(link);
            pool.add(user.getId());
        }
        poolLiteral = uuidArray(pool);
    }

    private UUID insertGroupWithMembers(UUID owner, List<UUID> members) {
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO groups (id, name, description, invite_code, status, is_settlement_enabled,"
                        + " is_join_without_confirm, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'ACTIVE', true, true, now(), now())",
                groupId, "Nhóm khổng lồ", "Đo quy mô", "A" + groupId.toString().substring(0, 8));
        jdbcTemplate.update("INSERT INTO group_funds (id, group_id, keepper_id, current_balance, created_at)"
                + " VALUES (?, ?, ?, 0, now())", UUID.randomUUID(), groupId, owner);
        for (UUID member : members)
            jdbcTemplate.update("INSERT INTO group_members (id, group_id, user_id, role, status, joined_at)"
                            + " VALUES (?, ?, ?, ?, 'ACTIVE', now() - interval '60 days')",
                    UUID.randomUUID(), groupId, member, member.equals(owner) ? "OWNER" : "MEMBER");
        return groupId;
    }

    // chèn giao dịch đã duyệt số thứ tự (from..to) cho nhóm A theo từng cụm để mỗi câu SQL không quá lớn
    private long insertTransactionsForGroup(UUID group, UUID owner, long from, long to) {
        String members = uuidArray(pool.subList(0, A_MEMBERS));
        for (long start = from; start <= to; start += CHUNK) {
            long end = Math.min(start + CHUNK - 1, to);
            jdbcTemplate.update(("""
                    WITH inserted AS (
                      INSERT INTO group_transactions
                        (id, group_id, money_source, transactor_id, created_by, category_id, type, status,
                         reviewed_by, reviewed_at, amount, occurred_at, note, created_at, updated_at, version)
                      SELECT gen_random_uuid(), '{group}'::uuid, 'PERSONAL',
                             {members}[1 + (g % {memberCount})], {members}[1 + (g % {memberCount})],
                             '{category}'::uuid, 'EXPENSE', 'CONFIRMED',
                             '{owner}'::uuid, now(), 10000 + (g % 90000),
                             now() - ((g % 700) || ' days')::interval - ((g % 1440) || ' minutes')::interval,
                             'Giao dịch đo quy mô', now(), now(), 0
                      FROM generate_series({start}::bigint, {end}::bigint) AS g
                      RETURNING id, amount
                    )
                    INSERT INTO group_transaction_participants (group_transaction_id, user_id, share_amount)
                    SELECT i.id, p.user_id,
                           (i.amount / {participants})
                               + CASE WHEN p.ord = {participants} THEN i.amount % {participants} ELSE 0 END
                    FROM inserted i
                    CROSS JOIN unnest({members}[1:{participants}]) WITH ORDINALITY AS p(user_id, ord)
                    """)
                    .replace("{group}", group.toString())
                    .replace("{members}", members)
                    .replace("{memberCount}", String.valueOf(A_MEMBERS))
                    .replace("{category}", categoryId.toString())
                    .replace("{owner}", owner.toString())
                    .replace("{participants}", String.valueOf(A_PARTICIPANTS))
                    .replace("{start}", String.valueOf(start))
                    .replace("{end}", String.valueOf(end)));
        }
        return to;
    }

    // giao dịch chờ duyệt không có người chia tiền, đủ để đo listPending và đếm theo trạng thái
    private void insertPendingTransactions(UUID group, UUID owner) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM group_transactions WHERE group_id = ? AND status = 'PENDING'",
                Integer.class, group);
        if (existing != null && existing > 0)
            return;
        jdbcTemplate.update("""
                INSERT INTO group_transactions
                  (id, group_id, money_source, transactor_id, created_by, category_id, type, status,
                   amount, occurred_at, note, created_at, updated_at, version)
                SELECT gen_random_uuid(), ?, 'PERSONAL', ?, ?, ?, 'EXPENSE', 'PENDING',
                       15000 + g, now() - (g || ' hours')::interval, 'Chờ duyệt đo quy mô', now(), now(), 0
                FROM generate_series(1, ?) AS g
                """, group, pool.get(1), pool.get(1), categoryId, A_PENDING);
    }

    // chèn nhóm số from..to của kịch bản B cùng quỹ, thành viên và giao dịch; trả về số nhóm đã chèn tới đó
    private int insertGroupRange(int from, int to) {
        for (int start = from; start <= to; start += B_GROUPS_PER_CHUNK) {
            int end = Math.min(start + B_GROUPS_PER_CHUNK - 1, to);
            insertGroupsAndMembers(start, end);
            insertGroupTransactions(start, end);
        }
        return to;
    }

    private void insertGroupsAndMembers(int fromGroup, int toGroup) {
        jdbcTemplate.update(sql("""
                INSERT INTO groups (id, name, description, invite_code, status, is_settlement_enabled,
                                    is_join_without_confirm, created_at, updated_at)
                SELECT pg.id, 'Nhóm quy mô ' || pg.n, 'Đo quy mô', 'B' || upper(substr(md5(pg.id::text), 1, 12)),
                       'ACTIVE', true, true, now(), now()
                FROM perf_groups pg WHERE pg.n BETWEEN {from} AND {to}
                """, fromGroup, toGroup));
        jdbcTemplate.update(sql("""
                INSERT INTO group_funds (id, group_id, keepper_id, current_balance, created_at)
                SELECT gen_random_uuid(), pg.id, {pool}[1 + pg.n % {poolSize}], 0, now()
                FROM perf_groups pg WHERE pg.n BETWEEN {from} AND {to}
                """, fromGroup, toGroup));
        jdbcTemplate.update(sql("""
                INSERT INTO group_members (id, group_id, user_id, role, status, joined_at)
                SELECT gen_random_uuid(), pg.id, {pool}[1 + (pg.n + k) % {poolSize}],
                       CASE WHEN k = 0 THEN 'OWNER' ELSE 'MEMBER' END, 'ACTIVE', now() - interval '60 days'
                FROM perf_groups pg CROSS JOIN generate_series(0, {memberMax}) AS k
                WHERE pg.n BETWEEN {from} AND {to}
                """, fromGroup, toGroup));
    }

    private void insertGroupTransactions(int fromGroup, int toGroup) {
        jdbcTemplate.update(sql("""
                WITH inserted AS (
                  INSERT INTO group_transactions
                    (id, group_id, money_source, transactor_id, created_by, category_id, type, status,
                     reviewed_by, reviewed_at, amount, occurred_at, note, created_at, updated_at, version)
                  SELECT gen_random_uuid(), pg.id, 'PERSONAL',
                         {pool}[1 + (pg.n + t % {memberCount}) % {poolSize}],
                         {pool}[1 + (pg.n + t % {memberCount}) % {poolSize}],
                         '{category}'::uuid, 'EXPENSE', 'CONFIRMED',
                         {pool}[1 + pg.n % {poolSize}], now(), 10000 + (t * 37 % 90000),
                         now() - ((t % 700) || ' days')::interval - ((t % 1440) || ' minutes')::interval,
                         'Giao dịch đo quy mô', now(), now(), 0
                  FROM perf_groups pg CROSS JOIN generate_series(1, {txns}) AS t
                  WHERE pg.n BETWEEN {from} AND {to}
                  RETURNING id, group_id, amount
                )
                INSERT INTO group_transaction_participants (group_transaction_id, user_id, share_amount)
                SELECT i.id, {pool}[1 + (pg.n + k) % {poolSize}],
                       (i.amount / {participants})
                           + CASE WHEN k = {participantMax} THEN i.amount % {participants} ELSE 0 END
                FROM inserted i
                JOIN perf_groups pg ON pg.id = i.group_id
                CROSS JOIN generate_series(0, {participantMax}) AS k
                """, fromGroup, toGroup));
    }

    // thay chỗ giữ chỗ {..} bằng giá trị thật; toàn bộ là số hoặc UUID tự sinh nên không có rủi ro chèn SQL
    private String sql(String template, int fromGroup, int toGroup) {
        return template
                .replace("{pool}", poolLiteral)
                .replace("{poolSize}", String.valueOf(POOL_SIZE))
                .replace("{memberCount}", String.valueOf(B_MEMBERS_PER_GROUP))
                .replace("{memberMax}", String.valueOf(B_MEMBERS_PER_GROUP - 1))
                .replace("{participantMax}", String.valueOf(B_PARTICIPANTS - 1))
                .replace("{participants}", String.valueOf(B_PARTICIPANTS))
                .replace("{txns}", String.valueOf(B_TXNS_PER_GROUP))
                .replace("{category}", categoryId.toString())
                .replace("{from}", String.valueOf(fromGroup))
                .replace("{to}", String.valueOf(toGroup));
    }

    private String uuidArray(List<UUID> ids) {
        return "(ARRAY[" + ids.stream().map(id -> "'" + id + "'").collect(Collectors.joining(",")) + "]::uuid[])";
    }

    // giao dịch được chèn thẳng bằng SQL nên phải tính lại bảng tổng hợp số dư, rồi cập nhật thống kê
    // để bộ lập kế hoạch truy vấn thấy đúng lượng dữ liệu vừa chèn
    private void analyze() {
        jdbcTemplate.execute("SELECT fn_rebuild_group_member_balances()");
        for (String table : List.of("groups", "group_members", "group_funds", "group_transactions",
                "group_transaction_participants", "group_member_balances"))
            jdbcTemplate.execute("ANALYZE " + table);
    }

    // ---------------------------------------- báo cáo ----------------------------------------

    private void writeReport(String suffix, String title) throws IOException {
        StringBuilder out = new StringBuilder("[PERF-SCALE] ").append(title).append('\n');
        out.append(String.format("%-62s", "hàm (ô = số câu SQL / mili giây)"));
        for (long scale : scalesSeen)
            out.append(String.format(" %16s", format(scale) + " giao dịch"));
        out.append('\n');

        Map<String, String> errors = new LinkedHashMap<>();
        results.forEach((label, byScale) -> {
            out.append(String.format("%-62s", label));
            for (long scale : scalesSeen) {
                Cell cell = byScale.get(scale);
                out.append(String.format(" %16s", cellText(cell)));
                if (cell != null && cell.error() != null)
                    errors.putIfAbsent(label + " @ " + format(scale), cell.error());
            }
            out.append('\n');
        });
        if (!errors.isEmpty()) {
            out.append("\nLỗi:\n");
            errors.forEach((where, error) -> out.append("  ").append(where).append(" -> ").append(error).append('\n'));
        }

        log.warn("\n{}", out);
        Path file = Path.of("target", "perf-group-scale-" + suffix + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, out.toString());
    }

    private String cellText(Cell cell) {
        if (cell == null)
            return "-";
        return cell.error() != null ? "LỖI" : cell.sql() + " / " + cell.ms() + "ms";
    }

    private String format(long scale) {
        return String.format("%,d", scale).replace(',', '.');
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root)
            root = root.getCause();
        String message = root.getMessage() == null ? "" : ": " + root.getMessage().replaceAll("\\s+", " ");
        String text = root.getClass().getSimpleName() + message;
        return text.length() > 110 ? text.substring(0, 110) + "..." : text;
    }
}
