package com.datn.financeapp.common.seed;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dựng dữ liệu nền cố định cho phép thử Phase 6 (D-15): 1 user thử, 2 ví, 1 ngân sách gắn danh
 * mục hệ thống "Ăn uống" (đã có sẵn từ V5, KHÔNG tạo lại cây danh mục). Chạy lại được (D-16) —
 * mọi bước đều SELECT-tồn-tại-trước-khi-INSERT hoặc ON CONFLICT DO NOTHING.
 *
 * <p>Chỉ chạy khi Spring profile là {@code dev} — KHÔNG chạy ở profile mặc định/test/prod.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements CommandLineRunner {

    private static final String SEED_EMAIL = "seed.user@datn.local";
    private static final String SEED_PASSWORD = "SeedPass123!";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        UUID userId = ensureUser();
        UUID walletA = ensureWallet(userId, "Ví A", "cash");
        UUID walletB = ensureWallet(userId, "Ví B", "bank");
        UUID anUongCategoryId = findSystemCategoryId("Ăn uống", "expense");
        ensureBudget(userId, anUongCategoryId);
        log.info(
                "DevDataSeeder hoàn tất: user={}, walletA={}, walletB={}, category(Ăn uống)={}",
                userId, walletA, walletB, anUongCategoryId);
    }

    // users.email có UNIQUE (uq_users_email, V1) — ON CONFLICT DO NOTHING an toàn.
    private UUID ensureUser() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, first_name, last_name, plan, status) VALUES (?, ?, ?, ?, 'free', 'active') "
                        + "ON CONFLICT (email) DO NOTHING",
                SEED_EMAIL, passwordEncoder.encode(SEED_PASSWORD), "Thử Phase 6", "Người dùng");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, SEED_EMAIL);
    }

    /**
     * wallets có UNIQUE INDEX uq_wallets_user_name trên (user_id, lower(name)) WHERE user_id IS
     * NOT NULL AND NOT is_deleted (V1) — dùng ON CONFLICT theo đúng biểu thức index đó.
     */
    private UUID ensureWallet(UUID userId, String name, String type) {
        jdbcTemplate.update(
                "INSERT INTO wallets (user_id, name, type, initial_balance, current_balance) "
                        + "VALUES (?, ?, ?, 1000000, 1000000) "
                        + "ON CONFLICT (user_id, lower(name)) WHERE user_id IS NOT NULL AND NOT is_deleted DO NOTHING",
                userId, name, type);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM wallets WHERE user_id = ? AND lower(name) = lower(?) AND NOT is_deleted",
                UUID.class, userId, name);
    }

    // Danh mục hệ thống "Ăn uống"/"Cà phê" đã có sẵn từ V5 (user_id IS NULL) — chỉ đọc, không tạo.
    private UUID findSystemCategoryId(String name, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND parent_category_id IS NULL "
                        + "AND type = ? AND name = ? AND NOT is_deleted",
                UUID.class, type, name);
    }

    /**
     * budgets KHÔNG có UNIQUE constraint nào ngoài PK (V3__ngan_sach.sql đã xác nhận) — PHẢI
     * SELECT-tồn-tại-trước-khi-INSERT, không dùng được ON CONFLICT.
     */
    private void ensureBudget(UUID userId, UUID categoryId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budgets WHERE user_id = ? AND category_id = ? AND is_active = TRUE",
                Integer.class, userId, categoryId);
        if (existing != null && existing > 0) {
            log.info("Ngân sách seed đã tồn tại cho user={} category={}, bỏ qua.", userId, categoryId);
            return;
        }
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        jdbcTemplate.update(
                "INSERT INTO budgets (user_id, category_id, limit_amount, period_type, start_date, end_date, auto_renew, is_active) "
                        + "VALUES (?, ?, 3000000, 'month', ?, ?, TRUE, TRUE)",
                userId, categoryId, start, end);
    }
}
