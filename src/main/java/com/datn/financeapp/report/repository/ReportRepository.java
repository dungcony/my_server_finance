package com.datn.financeapp.report.repository;

import com.datn.financeapp.transaction.entity.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Native query cho 6 điểm cuối báo cáo (REPORT-01..04, api/06-BAO-CAO.md). MỌI query nối chuỗi
 * {@link ReportQueryFragments#REPORT_ELIGIBLE} — không tự viết lại ba điều kiện bắt buộc ở nơi
 * khác. Cộng gộp danh mục con dùng {@code t.category_id = ANY(:categoryTree)} với mảng lấy từ
 * {@code CategoryRepository.findCategoryTree(categoryId)} khi cần lọc theo MỘT danh mục cha cụ
 * thể; các truy vấn nhóm theo TOÀN BỘ danh mục (by-category, by-category-group) tự cộng gộp bằng
 * cách JOIN {@code categories} hai lần (bản thân + cha) và GROUP BY theo id cha suy ra.
 *
 * <p>{@code Repository<Transaction, UUID>} (không phải {@code JpaRepository}) vì đây thuần là nơi
 * gom các câu SQL chỉ đọc, không cần CRUD chuẩn — theo đúng mẫu {@code BudgetProgressRepository}.
 */
public interface ReportRepository extends Repository<Transaction, UUID> {

    /** REPORT-summary (api/06 mục 2) — tổng thu/chi/số giao dịch trong kỳ, lọc thêm theo ví. */
    @Query(
            value = "SELECT "
                    + "COALESCE(SUM(CASE WHEN t.type = 'income' THEN t.amount END), 0) AS total_income, "
                    + "COALESCE(SUM(CASE WHEN t.type = 'expense' THEN t.amount END), 0) AS total_expense, "
                    + "COUNT(*) AS transaction_count "
                    + "FROM transactions t "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND t.date >= :fromDate AND t.date <= :toDate "
                    + "AND (CAST(:walletId AS uuid) IS NULL OR t.wallet_id = CAST(:walletId AS uuid) OR t.destination_wallet_id = CAST(:walletId AS uuid))",
            nativeQuery = true)
    SummaryProjection summary(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("walletId") UUID walletId);

    interface SummaryProjection {
        Long getTotalIncome();

        Long getTotalExpense();

        Long getTransactionCount();
    }

    /**
     * REPORT-by-category-group (api/06 mục 3) — tổng theo {@code category_group_id}, cộng gộp
     * danh mục con vào cha TRƯỚC (qua {@code parent_category_id}), sau đó GROUP BY nhóm lớn của
     * danh mục cha suy ra (con luôn thừa hưởng nhóm của cha trong nghiệp vụ hai cấp).
     */
    @Query(
            value = "SELECT cg.id AS category_group_id, cg.name AS name, cg.color AS color, "
                    + "SUM(t.amount) AS amount, COUNT(*) AS transaction_count "
                    + "FROM transactions t "
                    + "JOIN categories c ON c.id = t.category_id "
                    + "JOIN categories root ON root.id = COALESCE(c.parent_category_id, c.id) "
                    + "JOIN category_groups cg ON cg.id = root.category_group_id "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND " + ReportQueryFragments.WALLET_FILTER + " "
                    + "AND t.type = :type "
                    + "AND t.date >= :fromDate AND t.date <= :toDate "
                    + "GROUP BY cg.id, cg.name, cg.color "
                    + "ORDER BY amount DESC",
            nativeQuery = true)
    List<CategoryGroupAmountProjection> byCategoryGroup(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") String type,
            @Param("walletId") UUID walletId);

    interface CategoryGroupAmountProjection {
        UUID getCategoryGroupId();

        String getName();

        String getColor();

        Long getAmount();

        Long getTransactionCount();
    }

    /**
     * Chi tiết từng danh mục (cha hoặc con) BÊN TRONG một nhóm lớn — dùng để dựng {@code category}
     * lồng trong mỗi phần tử của {@code by-category-group} (api/06 mục 3, "mở rộng tại chỗ").
     * Trả về danh mục CHA đã cộng gộp con — cùng cách tính với {@link #byCategoryParentLevel}.
     */
    @Query(
            value = "SELECT root.id AS category_id, root.name AS name, "
                    + "SUM(t.amount) AS amount "
                    + "FROM transactions t "
                    + "JOIN categories c ON c.id = t.category_id "
                    + "JOIN categories root ON root.id = COALESCE(c.parent_category_id, c.id) "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND " + ReportQueryFragments.WALLET_FILTER + " "
                    + "AND t.type = :type AND root.category_group_id = :categoryGroupId "
                    + "AND t.date >= :fromDate AND t.date <= :toDate "
                    + "GROUP BY root.id, root.name "
                    + "ORDER BY amount DESC",
            nativeQuery = true)
    List<CategoryAmountProjection> byCategoryInGroup(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") String type,
            @Param("categoryGroupId") UUID categoryGroupId,
            @Param("walletId") UUID walletId);

    interface CategoryAmountProjection {
        UUID getCategoryId();

        String getName();

        Long getAmount();
    }

    /**
     * REPORT-by-category, {@code level=parent} (api/06 mục 4) — cộng gộp MỌI con vào cha, kể cả
     * giao dịch gán thẳng vào cha (không thuộc con nào). {@code icon}/{@code color} lấy từ chính
     * danh mục cha.
     */
    @Query(
            value = "SELECT root.id AS category_id, root.name AS name, root.icon_id AS icon_id, "
                    + "root.color AS color, SUM(t.amount) AS amount, COUNT(*) AS transaction_count, "
                    + "EXISTS(SELECT 1 FROM categories ch WHERE ch.parent_category_id = root.id "
                    + "AND NOT ch.is_deleted) AS has_children "
                    + "FROM transactions t "
                    + "JOIN categories c ON c.id = t.category_id "
                    + "JOIN categories root ON root.id = COALESCE(c.parent_category_id, c.id) "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND " + ReportQueryFragments.WALLET_FILTER + " "
                    + "AND t.type = :type "
                    + "AND t.date >= :fromDate AND t.date <= :toDate "
                    + "GROUP BY root.id, root.name, root.icon_id, root.color "
                    + "ORDER BY amount DESC "
                    + "LIMIT :limit",
            nativeQuery = true)
    List<CategoryParentAmountProjection> byCategoryParentLevel(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("walletId") UUID walletId);

    interface CategoryParentAmountProjection {
        UUID getCategoryId();

        String getName();

        UUID getIconId();

        String getColor();

        Long getAmount();

        Long getTransactionCount();

        Boolean getHasChildren();
    }

    /**
     * Chi tiết từng con BÊN TRONG một danh mục cha — dùng để dựng {@code children_detail} của
     * {@code level=parent} (api/06 mục 4). {@code category_id IS NULL} nghĩa là giao dịch gán
     * thẳng vào cha, không thuộc con nào ("Không phân loại ... tiết" theo mẫu api/06).
     */
    @Query(
            value = "SELECT c.id AS category_id, COALESCE(c.name, '') AS name, "
                    + "SUM(t.amount) AS amount, COUNT(*) AS transaction_count "
                    + "FROM transactions t "
                    + "LEFT JOIN categories c ON c.id = t.category_id AND c.id <> :parentId "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND " + ReportQueryFragments.WALLET_FILTER + " "
                    + "AND t.type = :type "
                    + "AND t.category_id = ANY(CAST(:categoryTree AS uuid[])) "
                    + "AND t.date >= :fromDate AND t.date <= :toDate "
                    + "GROUP BY c.id, c.name "
                    + "ORDER BY amount DESC",
            nativeQuery = true)
    List<CategoryAmountProjection> childrenDetail(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") String type,
            @Param("parentId") UUID parentId,
            @Param("categoryTree") UUID[] categoryTree,
            @Param("walletId") UUID walletId);

    /** REPORT-by-category, {@code level=child} (api/06 mục 4) — tách riêng từng danh mục con, KHÔNG cộng gộp. */
    @Query(
            value = "SELECT c.id AS category_id, c.name AS name, c.icon_id AS icon_id, "
                    + "c.color AS color, SUM(t.amount) AS amount, COUNT(*) AS transaction_count, "
                    + "FALSE AS has_children "
                    + "FROM transactions t "
                    + "JOIN categories c ON c.id = t.category_id "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND " + ReportQueryFragments.WALLET_FILTER + " "
                    + "AND t.type = :type "
                    + "AND t.date >= :fromDate AND t.date <= :toDate "
                    + "GROUP BY c.id, c.name, c.icon_id, c.color "
                    + "ORDER BY amount DESC "
                    + "LIMIT :limit",
            nativeQuery = true)
    List<CategoryParentAmountProjection> byCategoryChildLevel(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("walletId") UUID walletId);

    /**
     * REPORT-daily-trend (api/06 mục 5) — tổng chi từng ngày trong tháng. {@code endDate} PHẢI
     * được tính ở tầng Java bằng {@code LEAST(cuối tháng, hôm nay)} TRƯỚC khi truyền vào, để
     * {@code current_line} không kéo dài quá ngày hôm nay khi tháng đang chạy chưa kết thúc.
     */
    @Query(
            value = "SELECT t.date AS date, SUM(t.amount) AS amount "
                    + "FROM transactions t "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND " + ReportQueryFragments.WALLET_FILTER + " "
                    + "AND t.type = 'expense' AND t.date >= :startDate AND t.date <= :endDate "
                    + "GROUP BY t.date "
                    + "ORDER BY t.date",
            nativeQuery = true)
    List<DailyAmountProjection> dailyExpense(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("walletId") UUID walletId);

    interface DailyAmountProjection {
        LocalDate getDate();

        Long getAmount();
    }

    /** REPORT-monthly-trend (api/06 mục 6) — tổng thu/chi từng tháng, tối đa 24 tháng gần nhất. */
    @Query(
            value = "SELECT date_trunc('month', t.date)::date AS month, "
                    + "COALESCE(SUM(CASE WHEN t.type = 'income' THEN t.amount END), 0) AS total_income, "
                    + "COALESCE(SUM(CASE WHEN t.type = 'expense' THEN t.amount END), 0) AS total_expense "
                    + "FROM transactions t "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND t.date >= :fromDate AND t.date <= :toDate "
                    + "GROUP BY month "
                    + "ORDER BY month",
            nativeQuery = true)
    List<MonthlyAmountProjection> monthlyTrend(
            @Param("userId") UUID userId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    interface MonthlyAmountProjection {
        LocalDate getMonth();

        Long getTotalIncome();

        Long getTotalExpense();
    }

    /**
     * Danh sách giao dịch đủ điều kiện báo cáo trong kỳ — dùng CHO CẢ export CSV (Task 3, cùng bộ
     * lọc báo cáo với các endpoint đọc) và {@code recent_transactions}/top_spending phái sinh.
     */
    @Query(
            value = "SELECT t.* FROM transactions t "
                    + "WHERE t.user_id = :userId AND " + ReportQueryFragments.REPORT_ELIGIBLE + " "
                    + "AND " + ReportQueryFragments.WALLET_FILTER + " "
                    + "AND t.date >= :fromDate AND t.date <= :toDate "
                    + "ORDER BY t.date DESC, t.created_at DESC",
            nativeQuery = true)
    List<Transaction> eligibleTransactions(
            @Param("userId") UUID userId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("walletId") UUID walletId);
}
