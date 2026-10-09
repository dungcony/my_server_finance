package com.datn.financeapp.module.report.repository;

import com.datn.financeapp.module.transaction.entity.Transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Repository thực hiện các truy vấn SQL native cho báo cáo thống kê.
 * Cung cấp các phương thức truy vấn dữ liệu như {@link #summary(UUID, LocalDate, LocalDate, UUID)}, {@link #byCategoryGroup(UUID, LocalDate, LocalDate, String, UUID)},
 * {@link #byCategoryInGroup(UUID, LocalDate, LocalDate, String, UUID, UUID)}, {@link #byCategoryParentLevel(UUID, LocalDate, LocalDate, String, int, UUID)},
 * {@link #byCategoryChildLevel(UUID, LocalDate, LocalDate, String, int, UUID)}, {@link #dailyExpense(UUID, LocalDate, LocalDate, UUID)},
 * {@link #monthlyTrend(UUID, LocalDate, LocalDate)} và {@link #eligibleTransactions(UUID, LocalDate, LocalDate, UUID)}.
 */
public interface ReportRepository extends Repository<Transaction, UUID> {

    /**
     * Truy vấn tóm tắt tổng thu, tổng chi và số lượng giao dịch trong khoảng thời gian.
     *
     * @param userId   ID người dùng
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     * @param walletId ID ví lọc dữ liệu (tùy chọn)
     * @return projection {@link SummaryProjection} chứa thông tin tóm tắt
     */
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

    /**
     * Projection chứa thông tin tổng hợp thu chi.
     */
    interface SummaryProjection {
        Long getTotalIncome();

        Long getTotalExpense();

        Long getTransactionCount();
    }

    /**
     * Truy vấn tổng tiền thu/chi gom nhóm theo nhóm danh mục lớn.
     *
     * @param userId   ID người dùng
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     * @param type     loại giao dịch
     * @param walletId ID ví lọc dữ liệu
     * @return danh sách các projection {@link CategoryGroupAmountProjection}
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

    /**
     * Projection thông tin tổng tiền theo nhóm danh mục.
     */
    interface CategoryGroupAmountProjection {
        UUID getCategoryGroupId();

        String getName();

        String getColor();

        Long getAmount();

        Long getTransactionCount();
    }

    /**
     * Truy vấn thông tin danh mục bên trong một nhóm danh mục chỉ định.
     *
     * @param userId          ID người dùng
     * @param fromDate        ngày bắt đầu
     * @param toDate          ngày kết thúc
     * @param type            loại giao dịch
     * @param categoryGroupId ID nhóm danh mục
     * @param walletId        ID ví lọc dữ liệu
     * @return danh sách các projection {@link CategoryAmountProjection}
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

    /**
     * Projection thông tin tổng tiền theo danh mục.
     */
    interface CategoryAmountProjection {
        UUID getCategoryId();

        String getName();

        Long getAmount();

        Long getTransactionCount();
    }

    /**
     * Truy vấn cơ cấu chi tiêu/thu nhập theo cấp danh mục cha (đã cộng gộp danh mục con).
     *
     * @param userId   ID người dùng
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     * @param type     loại giao dịch
     * @param limit    số lượng danh mục tối đa
     * @param walletId ID ví lọc dữ liệu
     * @return danh sách các projection {@link CategoryParentAmountProjection}
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

    /**
     * Projection thông tin tổng tiền theo danh mục cấp cha.
     */
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
     * Truy vấn chi tiết các danh mục con thuộc một danh mục cha.
     *
     * @param userId       ID người dùng
     * @param fromDate     ngày bắt đầu
     * @param toDate       ngày kết thúc
     * @param type         loại giao dịch
     * @param parentId     ID danh mục cha
     * @param categoryTree mảng ID cây danh mục
     * @param walletId     ID ví lọc dữ liệu
     * @return danh sách các projection {@link CategoryAmountProjection}
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

    /**
     * Truy vấn cơ cấu chi tiêu/thu nhập theo từng danh mục cấp con (không cộng gộp).
     *
     * @param userId   ID người dùng
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     * @param type     loại giao dịch
     * @param limit    số lượng danh mục tối đa
     * @param walletId ID ví lọc dữ liệu
     * @return danh sách các projection {@link CategoryParentAmountProjection}
     */
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
     * Truy vấn tổng tiền chi tiêu theo từng ngày trong khoảng thời gian.
     *
     * @param userId    ID người dùng
     * @param startDate ngày bắt đầu
     * @param endDate   ngày kết thúc
     * @param walletId  ID ví lọc dữ liệu
     * @return danh sách các projection {@link DailyAmountProjection}
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

    /**
     * Projection chi tiêu theo ngày.
     */
    interface DailyAmountProjection {
        LocalDate getDate();

        Long getAmount();
    }

    /**
     * Truy vấn xu hướng tổng thu và chi theo từng tháng.
     *
     * @param userId   ID người dùng
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     * @return danh sách các projection {@link MonthlyAmountProjection}
     */
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
     *
     * @param userId   ID người dùng
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     * @param walletId ID ví lọc dữ liệu
     * @return danh sách các entity {@link Transaction}
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
