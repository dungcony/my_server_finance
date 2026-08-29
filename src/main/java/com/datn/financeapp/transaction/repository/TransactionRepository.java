package com.datn.financeapp.transaction.repository;

import com.datn.financeapp.transaction.entity.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Transaction}. Khác {@code WalletRepository}, quyền D-27 ở đây suy
 * thẳng từ {@code transactions.user_id} — KHÔNG áp vế nhóm gia đình ({@code group_id}), vì đối
 * chiếu {@code db/migration/V2__giao_dich.sql} xác nhận bảng {@code transactions} không có cột
 * {@code group_id} (giống {@code CategoryRepository}, khác {@code WalletRepository}).
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Quyền D-27: transaction chỉ thuộc về đúng một user, không có nhánh nhóm. */
    Optional<Transaction> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    /**
     * Dùng cho DELETE idempotent (CORE-06): tìm giao dịch theo id + quyền D-27 KHÔNG lọc
     * {@code is_deleted} — cho phép service phân biệt "không tồn tại/không có quyền" (404) với
     * "đã xoá mềm rồi, gọi lại vẫn 200" (không phải lỗi).
     */
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Lịch sử giao dịch đã sinh từ một khoản định kỳ — {@code GET /recurring/{id}} (api/09 mục A).
     * Bỏ bản ghi đã xoá mềm, mới nhất trước.
     *
     * <p>Không có điều kiện quyền: chỉ gọi sau khi service đã kiểm tra quyền trên chính khoản định
     * kỳ, và mọi giao dịch sinh ra từ một khoản đều thuộc cùng {@code user_id} với khoản đó.
     */
    List<Transaction> findAllByRecurringIdAndIsDeletedFalseOrderByDateDesc(UUID recurringId);

    /** {@code run_count} của api/09 mục A1 — số giao dịch còn sống đã sinh từ khoản định kỳ này. */
    long countByRecurringIdAndIsDeletedFalse(UUID recurringId);

    /**
     * Dùng cho D-32 (plan sau): chặn xoá giao dịch đang gắn với một khoản trả nợ
     * ({@code debt_payments.transaction_id}, {@code ON DELETE RESTRICT} + {@code UNIQUE} —
     * db/migration/V4__so_no_muc_tieu.sql). Viết sẵn ở đây vì thuộc điểm nối của entity, dùng
     * lại thay vì viết lại điều kiện EXISTS ở nơi khác.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM debt_payments WHERE transaction_id = :id)", nativeQuery = true)
    boolean existsDebtPaymentLink(@Param("id") UUID id);

    /**
     * TXN-01/TXN-08 (api/04-GIAO-DICH.md mục 1). Điều kiện quyền {@code user_id = :userId} nằm
     * NGAY TRONG câu SQL (T-03-09). {@code categoryTree} PHẢI là mảng UUID lấy từ {@code
     * CategoryRepository.findCategoryTree(categoryId)} (TXN-08) — cộng gộp danh mục con vào cha,
     * KHÔNG tự viết {@code category_id = :categoryId} đơn thuần ở đây hay bất kỳ nơi nào khác.
     * {@code search} bind qua {@code @Param}, không nối chuỗi Java (T-03-08, chống SQL injection).
     */
    @Query(
            value = "SELECT t.* FROM transactions t "
                    + "WHERE t.user_id = :userId AND NOT t.is_deleted "
                    + "AND (CAST(:fromDate AS date) IS NULL OR t.date >= CAST(:fromDate AS date)) "
                    + "AND (CAST(:toDate AS date) IS NULL OR t.date <= CAST(:toDate AS date)) "
                    + "AND (CAST(:type AS text) IS NULL OR t.type = CAST(:type AS text)) "
                    + "AND (CAST(:walletId AS uuid) IS NULL OR t.wallet_id = CAST(:walletId AS uuid) OR t.destination_wallet_id = CAST(:walletId AS uuid)) "
                    + "AND (CAST(:categoryTree AS uuid[]) IS NULL OR t.category_id = ANY(CAST(:categoryTree AS uuid[]))) "
                    + "AND (CAST(:source AS text) IS NULL OR t.source = CAST(:source AS text)) "
                    + "AND (CAST(:countsInReport AS boolean) IS NULL OR t.counts_in_report = CAST(:countsInReport AS boolean)) "
                    + "AND (CAST(:search AS text) IS NULL OR t.display_name ILIKE CONCAT('%',CAST(:search AS text),'%') OR t.note ILIKE CONCAT('%',CAST(:search AS text),'%')) "
                    + "AND (CAST(:minAmount AS bigint) IS NULL OR t.amount >= CAST(:minAmount AS bigint)) "
                    + "AND (CAST(:maxAmount AS bigint) IS NULL OR t.amount <= CAST(:maxAmount AS bigint)) "
                    + "AND (:includeTransfers = TRUE OR t.type <> 'transfer') "
                    + "ORDER BY "
                    + "CASE WHEN CAST(:sortBy AS text) = 'amount' AND CAST(:sortOrder AS text) = 'asc' THEN t.amount END ASC, "
                    + "CASE WHEN CAST(:sortBy AS text) = 'amount' AND CAST(:sortOrder AS text) = 'desc' THEN t.amount END DESC, "
                    + "CASE WHEN CAST(:sortBy AS text) = 'created_at' AND CAST(:sortOrder AS text) = 'asc' THEN t.created_at END ASC, "
                    + "CASE WHEN CAST(:sortBy AS text) = 'created_at' AND CAST(:sortOrder AS text) = 'desc' THEN t.created_at END DESC, "
                    + "CASE WHEN (CAST(:sortBy AS text) IS NULL OR CAST(:sortBy AS text) = 'date') AND CAST(:sortOrder AS text) = 'asc' THEN t.date END ASC, "
                    + "CASE WHEN (CAST(:sortBy AS text) IS NULL OR CAST(:sortBy AS text) = 'date') AND CAST(:sortOrder AS text) = 'desc' THEN t.date END DESC "
                    + "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Transaction> search(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") String type,
            @Param("walletId") UUID walletId,
            @Param("categoryTree") UUID[] categoryTree,
            @Param("source") String source,
            @Param("countsInReport") Boolean countsInReport,
            @Param("search") String search,
            @Param("minAmount") Long minAmount,
            @Param("maxAmount") Long maxAmount,
            @Param("includeTransfers") boolean includeTransfers,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /** Cùng điều kiện WHERE với {@link #search}, không phân trang — phục vụ {@code total_items}. */
    @Query(
            value = "SELECT COUNT(*) FROM transactions t "
                    + "WHERE t.user_id = :userId AND NOT t.is_deleted "
                    + "AND (CAST(:fromDate AS date) IS NULL OR t.date >= CAST(:fromDate AS date)) "
                    + "AND (CAST(:toDate AS date) IS NULL OR t.date <= CAST(:toDate AS date)) "
                    + "AND (CAST(:type AS text) IS NULL OR t.type = CAST(:type AS text)) "
                    + "AND (CAST(:walletId AS uuid) IS NULL OR t.wallet_id = CAST(:walletId AS uuid) OR t.destination_wallet_id = CAST(:walletId AS uuid)) "
                    + "AND (CAST(:categoryTree AS uuid[]) IS NULL OR t.category_id = ANY(CAST(:categoryTree AS uuid[]))) "
                    + "AND (CAST(:source AS text) IS NULL OR t.source = CAST(:source AS text)) "
                    + "AND (CAST(:countsInReport AS boolean) IS NULL OR t.counts_in_report = CAST(:countsInReport AS boolean)) "
                    + "AND (CAST(:search AS text) IS NULL OR t.display_name ILIKE CONCAT('%',CAST(:search AS text),'%') OR t.note ILIKE CONCAT('%',CAST(:search AS text),'%')) "
                    + "AND (CAST(:minAmount AS bigint) IS NULL OR t.amount >= CAST(:minAmount AS bigint)) "
                    + "AND (CAST(:maxAmount AS bigint) IS NULL OR t.amount <= CAST(:maxAmount AS bigint)) "
                    + "AND (:includeTransfers = TRUE OR t.type <> 'transfer')",
            nativeQuery = true)
    long countSearch(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") String type,
            @Param("walletId") UUID walletId,
            @Param("categoryTree") UUID[] categoryTree,
            @Param("source") String source,
            @Param("countsInReport") Boolean countsInReport,
            @Param("search") String search,
            @Param("minAmount") Long minAmount,
            @Param("maxAmount") Long maxAmount,
            @Param("includeTransfers") boolean includeTransfers);

    /**
     * Tổng thu/chi trên TOÀN BỘ kết quả lọc (không phân trang) — LUÔN loại {@code type =
     * 'transfer'} bất kể {@code includeTransfers} (CLAUDE.md §2 bất biến: transfer không phải
     * thu, không phải chi, dù nó vẫn xuất hiện trong danh sách {@link #search}).
     */
    @Query(
            value = "SELECT "
                    + "COALESCE(SUM(CASE WHEN t.type = 'income' THEN t.amount END), 0) AS total_income, "
                    + "COALESCE(SUM(CASE WHEN t.type = 'expense' THEN t.amount END), 0) AS total_expense "
                    + "FROM transactions t "
                    + "WHERE t.user_id = :userId AND NOT t.is_deleted AND t.type <> 'transfer' "
                    + "AND (CAST(:fromDate AS date) IS NULL OR t.date >= CAST(:fromDate AS date)) "
                    + "AND (CAST(:toDate AS date) IS NULL OR t.date <= CAST(:toDate AS date)) "
                    + "AND (CAST(:type AS text) IS NULL OR t.type = CAST(:type AS text)) "
                    + "AND (CAST(:walletId AS uuid) IS NULL OR t.wallet_id = CAST(:walletId AS uuid) OR t.destination_wallet_id = CAST(:walletId AS uuid)) "
                    + "AND (CAST(:categoryTree AS uuid[]) IS NULL OR t.category_id = ANY(CAST(:categoryTree AS uuid[]))) "
                    + "AND (CAST(:source AS text) IS NULL OR t.source = CAST(:source AS text)) "
                    + "AND (CAST(:countsInReport AS boolean) IS NULL OR t.counts_in_report = CAST(:countsInReport AS boolean)) "
                    + "AND (CAST(:search AS text) IS NULL OR t.display_name ILIKE CONCAT('%',CAST(:search AS text),'%') OR t.note ILIKE CONCAT('%',CAST(:search AS text),'%')) "
                    + "AND (CAST(:minAmount AS bigint) IS NULL OR t.amount >= CAST(:minAmount AS bigint)) "
                    + "AND (CAST(:maxAmount AS bigint) IS NULL OR t.amount <= CAST(:maxAmount AS bigint))",
            nativeQuery = true)
    SummaryProjection summary(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") String type,
            @Param("walletId") UUID walletId,
            @Param("categoryTree") UUID[] categoryTree,
            @Param("source") String source,
            @Param("countsInReport") Boolean countsInReport,
            @Param("search") String search,
            @Param("minAmount") Long minAmount,
            @Param("maxAmount") Long maxAmount);

    /** Projection cho {@link #summary} — Spring Data JPA tự map cột theo tên getter. */
    interface SummaryProjection {
        Long getTotalIncome();

        Long getTotalExpense();
    }
}
