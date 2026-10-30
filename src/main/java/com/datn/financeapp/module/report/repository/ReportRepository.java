package com.datn.financeapp.module.report.repository;

import com.datn.financeapp.module.transaction.entity.Transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Repository thực hiện các truy vấn SQL native cho báo cáo thống kê.
 * Cung cấp các phương thức truy vấn dữ liệu như {@link #summary}, {@link #byCategoryGroup},
 * {@link #byCategoryInGroup}, {@link #byCategoryParentLevel}, {@link #byCategoryChildLevel},
 * {@link #childrenDetail}, {@link #dailyExpense}, {@link #monthlyTrend} và {@link #eligibleTransactions}.
 */
public interface ReportRepository extends Repository<Transaction, UUID> {

    /**
     * Truy vấn tóm tắt tổng thu, tổng chi và số lượng giao dịch trong khoảng thời gian.
     */
    @Query(
            value = """
                    SELECT
                      CAST(COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount END), 0) AS BIGINT) AS total_income,
                      CAST(COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount END), 0) AS BIGINT) AS total_expense,
                      CAST(COUNT(*) AS BIGINT) AS transaction_count
                    FROM transactions t
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                      AND """ + ReportQueryFragments.WALLET_FILTER,
            nativeQuery = true)
    SummaryProjection summary(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("walletId") UUID walletId);

    /**
     * Projection chứa thông tin tổng hợp thu chi.
     */
    interface SummaryProjection {
        Long getTotalIncome();

        Long getTotalExpense();

        Long getTransactionCount();
    }

    /**
     * Truy vấn tổng tiền thu/chi gom nhóm theo nhóm danh mục lớn (danh mục gốc level 0).
     */
    @Query(
            value = """
                    SELECT
                      root.id AS category_group_id,
                      root.code AS code,
                      root.name AS name,
                      root.color AS color,
                      root.icon_code AS icon_code,
                      COALESCE(i.path_data, '') AS icon_path,
                      CAST(SUM(t.amount) AS BIGINT) AS amount,
                      CAST(COUNT(*) AS BIGINT) AS transaction_count
                    FROM transactions t
                    JOIN categories c ON c.code = t.category_code
                    JOIN categories root ON root.code = split_part(c.path, '/', 1) AND root.level = 0 AND NOT root.is_deleted
                    LEFT JOIN icons i ON i.code = root.icon_code
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND """ + ReportQueryFragments.WALLET_FILTER + """
                      AND t.type = :type
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY root.id, root.code, root.name, root.color, root.icon_code, i.path_data
                    ORDER BY amount DESC
                    """,
            nativeQuery = true)
    List<CategoryGroupAmountProjection> byCategoryGroup(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("type") String type,
            @Param("walletId") UUID walletId);

    /**
     * Projection thông tin tổng tiền theo nhóm danh mục.
     */
    interface CategoryGroupAmountProjection {
        UUID getCategoryGroupId();

        String getCode();

        String getName();

        String getColor();

        String getIconCode();

        String getIconPath();

        Long getAmount();

        Long getTransactionCount();
    }

    /**
     * Truy vấn thông tin danh mục con bên trong một nhóm danh mục chỉ định.
     */
    @Query(
            value = """
                    SELECT
                      c.id AS category_id,
                      c.code AS code,
                      c.name AS name,
                      CAST(SUM(t.amount) AS BIGINT) AS amount,
                      CAST(COUNT(*) AS BIGINT) AS transaction_count
                    FROM transactions t
                    JOIN categories c ON c.code = t.category_code
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND """ + ReportQueryFragments.WALLET_FILTER + """
                      AND t.type = :type
                      AND split_part(c.path, '/', 1) = :rootCode
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY c.id, c.code, c.name
                    ORDER BY amount DESC
                    """,
            nativeQuery = true)
    List<CategoryAmountProjection> byCategoryInGroup(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("type") String type,
            @Param("rootCode") String rootCode,
            @Param("walletId") UUID walletId);

    /**
     * Projection thông tin tổng tiền theo danh mục.
     */
    interface CategoryAmountProjection {
        UUID getCategoryId();

        String getCode();

        String getName();

        Long getAmount();

        Long getTransactionCount();
    }

    /**
     * Truy vấn cơ cấu chi tiêu/thu nhập theo cấp danh mục cha (đã cộng gộp danh mục con).
     */
    @Query(
            value = """
                    SELECT
                      root.id AS category_id,
                      root.code AS code,
                      root.name AS name,
                      root.icon_code AS icon_code,
                      COALESCE(i.path_data, '') AS icon_path,
                      root.color AS color,
                      CAST(SUM(t.amount) AS BIGINT) AS amount,
                      CAST(COUNT(*) AS BIGINT) AS transaction_count,
                      NOT root.is_leaf AS has_children
                    FROM transactions t
                    JOIN categories c ON c.code = t.category_code
                    JOIN categories root ON root.code = split_part(c.path, '/', 1) AND root.level = 0 AND NOT root.is_deleted
                    LEFT JOIN icons i ON i.code = root.icon_code
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND """ + ReportQueryFragments.WALLET_FILTER + """
                      AND t.type = :type
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY root.id, root.code, root.name, root.icon_code, i.path_data, root.color, root.is_leaf
                    ORDER BY amount DESC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<CategoryParentAmountProjection> byCategoryParentLevel(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("walletId") UUID walletId);

    /**
     * Projection thông tin tổng tiền theo danh mục cấp cha.
     */
    interface CategoryParentAmountProjection {
        UUID getCategoryId();

        String getCode();

        String getName();

        String getIconCode();

        String getIconPath();

        String getColor();

        Long getAmount();

        Long getTransactionCount();

        Boolean getHasChildren();
    }

    /**
     * Truy vấn chi tiết các danh mục con thuộc một danh mục cha gốc.
     */
    @Query(
            value = """
                    SELECT
                      c.id AS category_id,
                      c.code AS code,
                      c.name AS name,
                      CAST(SUM(t.amount) AS BIGINT) AS amount,
                      CAST(COUNT(*) AS BIGINT) AS transaction_count
                    FROM transactions t
                    JOIN categories c ON c.code = t.category_code
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND """ + ReportQueryFragments.WALLET_FILTER + """
                      AND t.type = :type
                      AND split_part(c.path, '/', 1) = :parentCode
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY c.id, c.code, c.name
                    ORDER BY amount DESC
                    """,
            nativeQuery = true)
    List<CategoryAmountProjection> childrenDetail(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("type") String type,
            @Param("parentCode") String parentCode,
            @Param("walletId") UUID walletId);

    /**
     * Truy vấn cơ cấu chi tiêu/thu nhập theo từng danh mục cấp con (không cộng gộp).
     */
    @Query(
            value = """
                    SELECT
                      c.id AS category_id,
                      c.code AS code,
                      c.name AS name,
                      c.icon_code AS icon_code,
                      COALESCE(i.path_data, '') AS icon_path,
                      c.color AS color,
                      CAST(SUM(t.amount) AS BIGINT) AS amount,
                      CAST(COUNT(*) AS BIGINT) AS transaction_count,
                      FALSE AS has_children
                    FROM transactions t
                    JOIN categories c ON c.code = t.category_code
                    LEFT JOIN icons i ON i.code = c.icon_code
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND """ + ReportQueryFragments.WALLET_FILTER + """
                      AND t.type = :type
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY c.id, c.code, c.name, c.icon_code, i.path_data, c.color
                    ORDER BY amount DESC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<CategoryParentAmountProjection> byCategoryChildLevel(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("walletId") UUID walletId);

    /**
     * Truy vấn tổng tiền chi tiêu theo từng ngày trong khoảng thời gian theo múi giờ.
     */
    @Query(
            value = """
                    SELECT
                      (t.occurred_at AT TIME ZONE :timeZone)::date AS date,
                      CAST(SUM(t.amount) AS BIGINT) AS amount
                    FROM transactions t
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND """ + ReportQueryFragments.WALLET_FILTER + """
                      AND t.type = 'EXPENSE'
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY 1
                    ORDER BY date
                    """,
            nativeQuery = true)
    List<DailyAmountProjection> dailyExpense(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("timeZone") String timeZone,
            @Param("walletId") UUID walletId);

    /**
     * Projection chi tiêu theo ngày.
     */
    interface DailyAmountProjection {
        LocalDate getDate();

        Long getAmount();
    }

    /**
     * Truy vấn xu hướng tổng thu và chi theo từng tháng.
     */
    @Query(
            value = """
                    SELECT
                      date_trunc('month', t.occurred_at AT TIME ZONE :timeZone)::date AS month,
                      CAST(COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount END), 0) AS BIGINT) AS total_income,
                      CAST(COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount END), 0) AS BIGINT) AS total_expense
                    FROM transactions t
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    GROUP BY 1
                    ORDER BY month
                    """,
            nativeQuery = true)
    List<MonthlyAmountProjection> monthlyTrend(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("timeZone") String timeZone);

    /**
     * Projection tổng thu chi theo tháng.
     */
    interface MonthlyAmountProjection {
        LocalDate getMonth();

        Long getTotalIncome();

        Long getTotalExpense();
    }

    /**
     * Truy vấn các giao dịch đủ điều kiện báo cáo trong khoảng thời gian.
     */
    @Query(
            value = """
                    SELECT t.* FROM transactions t
                    WHERE t.user_id = :userId
                      AND """ + ReportQueryFragments.REPORT_ELIGIBLE + """
                      AND """ + ReportQueryFragments.WALLET_FILTER + """
                      AND t.occurred_at >= :startInstant AND t.occurred_at <= :endInstant
                    ORDER BY t.occurred_at DESC, t.created_at DESC
                    """,
            nativeQuery = true)
    List<Transaction> eligibleTransactions(
            @Param("userId") UUID userId,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant,
            @Param("walletId") UUID walletId);
}
