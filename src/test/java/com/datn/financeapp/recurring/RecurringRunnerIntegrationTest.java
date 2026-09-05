package com.datn.financeapp.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.recurring.service.RecurringRunnerService;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test tích hợp tác vụ sinh giao dịch định kỳ (RECUR-03) — mục ưu tiên 3 và 4 của D-58.
 *
 * <p>Ba rủi ro được nhắm thẳng:
 *
 * <ul>
 *   <li><b>D-50</b> — vắng mặt nhiều tháng phải sinh ĐỦ từng kỳ, mỗi giao dịch mang ĐÚNG ngày đáng
 *       lẽ phải chạy, không dồn hết vào hôm nay. Dồn vào một ngày thì
 *       {@code uq_txn_recurring_date} sẽ nuốt mất các kỳ còn lại.
 *   <li><b>D-52</b> — một khoản hỏng (ví đã xoá) không được làm chết cả tác vụ, và KHÔNG được tự
 *       tắt {@code is_enabled} vì người dùng sẽ không bao giờ biết tiền nhà đã ngừng ghi.
 *   <li><b>Chống trùng</b> — chạy lại tác vụ phải là thao tác vô hại (idempotent).
 * </ul>
 *
 * <p>Thay vì đợi thời gian thật, test đặt thẳng {@code next_run_date} về quá khứ bằng
 * {@link JdbcTemplate} để mô phỏng "người dùng vắng mặt".
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecurringRunnerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1yZWN1cnJpbmctcnVubmVyLTMyYnk");
    }

    @TestConfiguration
    static class NoRateLimitConfig {
        @Bean
        @Primary
        RateLimitFilter rateLimitFilter() {
            return new RateLimitFilter(null) {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws ServletException, IOException {
                    chain.doFilter(req, res);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecurringRunnerService recurringRunnerService;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM recurring_transactions");
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "full_name", "Người Kiểm Thử");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("access_token");
    }

    private String createWallet(String token, String name, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "cash",
                "initial_balance", initialBalance);
        String response = mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("id");
    }

    private String createRootCategory(String token, String name, String type) throws Exception {
        String iconId = jdbcTemplate.queryForObject("SELECT id FROM icons WHERE code = ?", String.class, "khac");
        String groupId =
                jdbcTemplate.queryForObject("SELECT id FROM category_groups WHERE name = ?", String.class, "Khác");
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type,
                "icon_id", iconId,
                "color", "#3d6b7d",
                "category_group_id", groupId);
        String response = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("id");
    }

    private String createRecurring(
            String token,
            String walletId,
            String categoryId,
            String displayName,
            long amount,
            LocalDate startDate,
            String frequency) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("display_name", displayName);
        body.put("type", "expense");
        body.put("amount", amount);
        body.put("wallet_id", walletId);
        body.put("category_id", categoryId);
        body.put("frequency", frequency);
        body.put("interval", 1);
        body.put("start_date", startDate.toString());

        String response = mockMvc.perform(post("/recurring")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("id");
    }

    /** Mô phỏng "người dùng vắng mặt" — kéo lịch về quá khứ mà không phải đợi thời gian thật. */
    private void rewindSchedule(String recurringId, LocalDate startDate, LocalDate nextRunDate) {
        jdbcTemplate.update(
                "UPDATE recurring_transactions SET start_date = ?, next_run_date = ? WHERE id = ?",
                java.sql.Date.valueOf(startDate),
                java.sql.Date.valueOf(nextRunDate),
                UUID.fromString(recurringId));
    }

    private List<LocalDate> transactionDatesForRecurring(String recurringId) {
        return jdbcTemplate.queryForList(
                        "SELECT date FROM transactions WHERE recurring_id = ? AND NOT is_deleted ORDER BY date",
                        java.sql.Date.class,
                        UUID.fromString(recurringId))
                .stream()
                .map(java.sql.Date::toLocalDate)
                .toList();
    }

    private Map<String, Object> recurringRowFromDatabase(String recurringId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM recurring_transactions WHERE id = ?", UUID.fromString(recurringId));
    }

    private long walletBalance(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /**
     * D-50 + api/09 mục "Bắt kịp kỳ bị bỏ lỡ" và "Xử lý ngày không tồn tại" gộp vào một ca — đây là
     * test giá trị nhất của plan.
     *
     * <p>Khoản tiền nhà ngày 31 hằng tháng, người dùng vắng từ 31/01/2026. Kỳ tháng 2 phải rơi vào
     * 28/02 (2026 không nhuận) nhưng kỳ tháng 3 phải QUAY LẠI ngày 31 — nếu ngày gốc bị ghi đè
     * thành 28 thì từ đó về sau tháng nào cũng chạy ngày 28, sai hoàn toàn ý định ban đầu.
     */
    @Test
    void runDueRecurring_missedThreeMonths_generatesThreePeriodsWithCorrectDates() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.bat.kip@example.com");
        String walletId = createWallet(token, "Vietcombank", 50_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        String recurringId = createRecurring(
                token, walletId, categoryId, "Tiền thuê nhà", 4_500_000, LocalDate.now().plusDays(30), "month");

        // Vắng mặt từ ba tháng trước tính lùi từ hôm nay, lịch đặt vào ngày 31 để ép qua ca ngày
        // không tồn tại. Mốc suy từ LocalDate.now() nên test không mục theo thời gian.
        //
        // Lùi thêm cho tới khi gặp tháng CÓ ngày 31 thay vì withDayOfMonth(31) thẳng: tháng đích
        // có thể chỉ có 30 ngày và LocalDate ném DateTimeException "Invalid date JUNE 31" ngay khi
        // dựng dữ liệu — test đỏ hay xanh tuỳ tháng chạy chứ không tuỳ code sản phẩm (chạy ngày
        // 05/09/2026 thì mốc rơi vào tháng 6, hỏng). Giữ đúng ngày 31 là cố ý: đó mới là ngày ép
        // được cả tháng 30 ngày lẫn tháng 2, tức ca mà D-50 muốn kiểm chứng.
        LocalDate anchorMonth = LocalDate.now().withDayOfMonth(1).minusMonths(3);
        while (anchorMonth.lengthOfMonth() < 31) {
            anchorMonth = anchorMonth.minusMonths(1);
        }
        LocalDate originalStart = anchorMonth.withDayOfMonth(31);
        rewindSchedule(recurringId, originalStart, originalStart);

        recurringRunnerService.runDueRecurring();

        // Dựng danh sách kỳ mong đợi bằng chính quy tắc của đặc tả, độc lập với code sản phẩm:
        // cộng tháng rồi ép lại ĐÚNG NGÀY GỐC (hoặc ngày cuối tháng nếu tháng ngắn hơn) — ngày
        // gốc không bị ghi đè bởi lần trôi trước đó.
        int originalDay = originalStart.getDayOfMonth();
        List<LocalDate> expected = new java.util.ArrayList<>();
        LocalDate cursor = originalStart;
        while (!cursor.isAfter(LocalDate.now())) {
            expected.add(cursor);
            LocalDate shifted = cursor.plusMonths(1);
            cursor = shifted.withDayOfMonth(Math.min(originalDay, shifted.lengthOfMonth()));
        }
        assertThat(expected).hasSizeGreaterThanOrEqualTo(3);

        assertThat(transactionDatesForRecurring(recurringId)).containsExactlyElementsOf(expected);

        // Điểm cốt lõi D-50: có ít nhất một kỳ bị làm tròn xuống ngày cuối tháng ngắn, và kỳ NGAY
        // SAU nó phải quay lại đúng NGÀY GỐC chứ không kẹt ở ngày đã làm tròn.
        for (int i = 0; i < expected.size() - 1; i++) {
            if (expected.get(i).getDayOfMonth() < originalDay) {
                assertThat(expected.get(i + 1).getDayOfMonth())
                        .isEqualTo(Math.min(originalDay, expected.get(i + 1).lengthOfMonth()));
            }
        }

        // Mọi kỳ đều trừ ví thật, không kỳ nào bị bỏ quên.
        assertThat(walletBalance(walletId)).isEqualTo(50_000_000L - expected.size() * 4_500_000L);

        // Lịch đã tiến qua mọi kỳ đã xử lý, và last_run_date là kỳ CUỐI thực sự ghi.
        Map<String, Object> row = recurringRowFromDatabase(recurringId);
        assertThat(((java.sql.Date) row.get("next_run_date")).toLocalDate()).isEqualTo(cursor);
        assertThat(((java.sql.Date) row.get("last_run_date")).toLocalDate())
                .isEqualTo(expected.get(expected.size() - 1));
    }

    /**
     * D-52 — khoản định kỳ trỏ tới ví đã bị xoá cứng khiến {@code TransactionWriter} vi phạm khoá
     * ngoại. Khoản đó phải bị bỏ qua trong im lặng (chỉ log), khoản lành VẪN ghi được, và
     * {@code is_enabled} của khoản hỏng KHÔNG được tự tắt.
     */
    @Test
    void runDueRecurring_oneRecurringFails_othersStillSucceed() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.mot.khoan.loi@example.com");
        String healthyWalletId = createWallet(token, "Ví Lành", 30_000_000);
        String brokenWalletId = createWallet(token, "Ví Sẽ Xoá", 30_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");

        String healthyId = createRecurring(
                token, healthyWalletId, categoryId, "Tiền thuê nhà", 4_500_000, LocalDate.now().plusDays(30), "month");
        String brokenId = createRecurring(
                token, brokenWalletId, categoryId, "Netflix", 260_000, LocalDate.now().plusDays(30), "month");

        LocalDate dueDate = LocalDate.now().minusDays(1);
        rewindSchedule(healthyId, dueDate, dueDate);
        rewindSchedule(brokenId, dueDate, dueDate);

        // Xoá cứng ví của khoản hỏng, bỏ qua khoá ngoại của recurring_transactions để dựng đúng
        // tình huống "ví đã biến mất" mà D-52 nói tới.
        jdbcTemplate.update("ALTER TABLE recurring_transactions DROP CONSTRAINT fk_rec_wallet");
        jdbcTemplate.update("DELETE FROM wallets WHERE id = ?", UUID.fromString(brokenWalletId));

        try {
            // Khẳng định số một: tác vụ KHÔNG được ném lỗi ra ngoài dù có khoản hỏng.
            assertThatCode(() -> recurringRunnerService.runDueRecurring()).doesNotThrowAnyException();

            // Khoản lành vẫn chạy đủ.
            assertThat(transactionDatesForRecurring(healthyId)).containsExactly(dueDate);
            assertThat(walletBalance(healthyWalletId)).isEqualTo(30_000_000L - 4_500_000L);

            // Khoản hỏng không ghi được gì, nhưng KHÔNG bị tự tắt (D-52).
            assertThat(transactionDatesForRecurring(brokenId)).isEmpty();
            Map<String, Object> brokenRow = recurringRowFromDatabase(brokenId);
            assertThat(brokenRow.get("is_enabled")).isEqualTo(true);
            // Lịch của khoản hỏng cũng không nhích lên — mai tác vụ sẽ thử lại đúng kỳ này.
            assertThat(((java.sql.Date) brokenRow.get("next_run_date")).toLocalDate()).isEqualTo(dueDate);
        } finally {
            // Phải dọn khoản trỏ tới ví đã biến mất TRƯỚC khi bật lại ràng buộc, nếu không chính
            // câu ALTER sẽ thất bại và nuốt mất lỗi thật của test.
            jdbcTemplate.update("DELETE FROM recurring_transactions WHERE id = ?", UUID.fromString(brokenId));
            jdbcTemplate.update("ALTER TABLE recurring_transactions ADD CONSTRAINT fk_rec_wallet "
                    + "FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT");
        }
    }

    /**
     * Chạy lại tác vụ phải vô hại. Lần thứ hai không kỳ nào tới hạn nữa vì {@code next_run_date} đã
     * tiến lên; kể cả khi bị kéo ngược về, {@code uq_txn_recurring_date} vẫn là lớp chặn cuối.
     */
    @Test
    void runDueRecurring_calledTwice_doesNotDuplicate() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.chay.hai.lan@example.com");
        String walletId = createWallet(token, "Vietcombank", 50_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        String recurringId = createRecurring(
                token, walletId, categoryId, "Tiền thuê nhà", 4_500_000, LocalDate.now().plusDays(30), "month");

        LocalDate originalStart = LocalDate.of(2026, 1, 31);
        rewindSchedule(recurringId, originalStart, originalStart);

        recurringRunnerService.runDueRecurring();
        List<LocalDate> afterFirstRun = transactionDatesForRecurring(recurringId);
        long balanceAfterFirstRun = walletBalance(walletId);

        recurringRunnerService.runDueRecurring();

        assertThat(transactionDatesForRecurring(recurringId)).isEqualTo(afterFirstRun);
        assertThat(walletBalance(walletId)).isEqualTo(balanceAfterFirstRun);
    }

    /**
     * Lớp chặn cuối ở tầng CSDL ({@code uq_txn_recurring_date}) phải tự đứng vững kể cả khi lịch bị
     * kéo ngược về kỳ đã sinh — không phụ thuộc vào việc kiểm tra trước ở Java (T-04-15).
     */
    @Test
    void runDueRecurring_scheduleRewoundToAlreadyGeneratedPeriod_skipsDuplicates() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.keo.nguoc@example.com");
        String walletId = createWallet(token, "Vietcombank", 50_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        String recurringId = createRecurring(
                token, walletId, categoryId, "Tiền thuê nhà", 4_500_000, LocalDate.now().plusDays(30), "month");

        LocalDate originalStart = LocalDate.of(2026, 1, 31);
        rewindSchedule(recurringId, originalStart, originalStart);
        recurringRunnerService.runDueRecurring();

        List<LocalDate> afterFirstRun = transactionDatesForRecurring(recurringId);
        long balanceAfterFirstRun = walletBalance(walletId);

        // Kéo lịch ngược hẳn về kỳ đầu, ép tác vụ đi lại toàn bộ các kỳ đã sinh.
        rewindSchedule(recurringId, originalStart, originalStart);
        recurringRunnerService.runDueRecurring();

        assertThat(transactionDatesForRecurring(recurringId)).isEqualTo(afterFirstRun);
        assertThat(walletBalance(walletId)).isEqualTo(balanceAfterFirstRun);
    }

    /**
     * Khoản đã tạm dừng không được tác vụ nền đụng tới — {@code findDue} lọc {@code is_enabled} ngay
     * trong SQL để chỉ mục riêng phần {@code idx_rec_due} phát huy tác dụng.
     */
    @Test
    void runDueRecurring_disabledRecurring_isSkipped() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.tam.dung@example.com");
        String walletId = createWallet(token, "Vietcombank", 50_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        String recurringId = createRecurring(
                token, walletId, categoryId, "Tiền thuê nhà", 4_500_000, LocalDate.now().plusDays(30), "month");

        LocalDate dueDate = LocalDate.now().minusDays(1);
        rewindSchedule(recurringId, dueDate, dueDate);

        mockMvc.perform(post("/recurring/" + recurringId + "/pause")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("is_enabled", false))))
                .andExpect(status().isOk());

        recurringRunnerService.runDueRecurring();

        assertThat(transactionDatesForRecurring(recurringId)).isEmpty();
        assertThat(walletBalance(walletId)).isEqualTo(50_000_000L);
    }

    /**
     * {@code end_date} nằm giữa các kỳ bỏ lỡ phải cắt vòng lặp đúng chỗ — không sinh kỳ nào vượt quá
     * ngày kết thúc người dùng đã đặt.
     *
     * <p>Ca này cũng chốt một sửa lỗi so với chữ nghĩa của api/09: đặc tả lọc
     * {@code end_date >= hôm nay}, nhưng ở đây {@code end_date} đã LÙI VỀ QUÁ KHỨ trong khi vẫn còn
     * hai kỳ chưa ghi nằm trước nó. Lọc theo hôm nay thì hai kỳ đó mất hẳn và ví thiếu tiền vĩnh
     * viễn; {@code findDue} vì vậy đối chiếu {@code end_date} với {@code next_run_date}.
     */
    @Test
    void runDueRecurring_endDateInsideMissedPeriods_stopsAtEndDate() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.het.han@example.com");
        String walletId = createWallet(token, "Vietcombank", 50_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        String recurringId = createRecurring(
                token, walletId, categoryId, "Tiền thuê nhà", 4_500_000, LocalDate.now().plusDays(30), "month");

        // Ba kỳ ngày đầu tháng tính lùi từ hôm nay, nhưng end_date cắt ngay sau kỳ thứ hai.
        LocalDate originalStart = LocalDate.now().withDayOfMonth(1).minusMonths(3);
        LocalDate secondPeriod = originalStart.plusMonths(1);
        rewindSchedule(recurringId, originalStart, originalStart);
        jdbcTemplate.update(
                "UPDATE recurring_transactions SET end_date = ? WHERE id = ?",
                java.sql.Date.valueOf(secondPeriod.plusDays(5)),
                UUID.fromString(recurringId));

        recurringRunnerService.runDueRecurring();

        // Kỳ thứ ba nằm sau end_date nên KHÔNG được sinh, dù nó đã tới hạn so với hôm nay.
        assertThat(transactionDatesForRecurring(recurringId)).containsExactly(originalStart, secondPeriod);
        assertThat(walletBalance(walletId)).isEqualTo(50_000_000L - 2 * 4_500_000L);
    }
}
