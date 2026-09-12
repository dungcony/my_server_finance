package com.datn.financeapp.budget.repository;

import com.datn.financeapp.budget.entity.Budget;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Ánh xạ view {@code v_budget_progress} (bản ĐÃ VÁ ở V6) bằng native query + interface projection.
 *
 * <p><b>Cố ý KHÔNG dùng {@code @Entity}:</b> đây là view chỉ đọc, không có vòng đời persistence.
 * Khai báo entity sẽ khiến Hibernate {@code ddl-auto=validate} soi cấu trúc view và mở đường cho
 * việc lỡ tay {@code save()} vào một thứ không ghi được.
 *
 * <p><b>Backend KHÔNG tính lại gì ở đây.</b> View đã gói sẵn ba quy tắc bất biến, tính một lần
 * cho mọi nơi dùng: cộng gộp danh mục con qua {@code fn_category_tree} (CLAUDE.md §1), chỉ lấy
 * {@code type = 'expense'} nên {@code transfer} tự bị loại (§2), và điều kiện phạm vi quyền
 * (§7 — chính là lỗ hổng V3 đã vá ở V6, có {@code BudgetPrivacyIntegrationTest} chốt hồi quy).
 * Mọi tính toán {@code spent_amount} viết tay trong Java đều là lỗi.
 *
 * <p><b>Vì sao tham số domain của {@code Repository<>} là {@link Budget} chứ không phải chính
 * projection:</b> Spring Data JPA bắt buộc kiểu domain phải là entity ĐƯỢC QUẢN LÝ (nằm trong
 * metamodel), nếu không sẽ ném {@code Not a managed type} ngay lúc khởi tạo context. Projection
 * là kiểu TRẢ VỀ của từng query, không phải kiểu domain. Khai {@link Budget} ở đây là hợp lý
 * nhất: view {@code v_budget_progress} chính là bảng {@code budgets} kèm các cột tính sẵn, và
 * interface này KHÔNG kế thừa {@code JpaRepository} nên không lộ ra method ghi nào.
 *
 * <p>Điều kiện {@code user_id = :currentUser} ở mỗi method là lớp quyền THỨ HAI (chọn đúng ngân
 * sách của người gọi); lớp thứ nhất nằm bên trong view (chọn đúng giao dịch được tính vào ngân
 * sách đó). Hai lớp giải quyết hai câu hỏi khác nhau, đều cần thiết.
 */
public interface BudgetProgressRepository extends Repository<Budget, UUID> {

    @Query(value = "SELECT * FROM v_budget_progress WHERE id = :id AND user_id = :currentUser", nativeQuery = true)
    Optional<BudgetProgressProjection> findByIdForUser(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(
            value = """
                    SELECT * FROM v_budget_progress WHERE user_id = :currentUser
                    AND (CAST(:isActive AS boolean) IS NULL OR is_active = :isActive)
                    AND (CAST(:periodType AS text) IS NULL OR period_type = :periodType)
                    ORDER BY start_date DESC
                    """,
            nativeQuery = true)
    List<BudgetProgressProjection> findAllForUser(
            @Param("currentUser") UUID currentUser,
            @Param("isActive") Boolean isActive,
            @Param("periodType") String periodType);

    /**
     * Dùng cho {@code BudgetAlertListener} (D-41): tìm mọi ngân sách đang hiệu lực mà giao dịch
     * vừa ghi RƠI VÀO cây danh mục của nó.
     *
     * <p>Chiều so sánh ở đây là chiều NGƯỢC với trực giác và là chỗ dễ sai nhất: không phải "ngân
     * sách nào có {@code category_id} bằng danh mục của giao dịch", mà "ngân sách nào có
     * {@code category_id} là gốc của một cây CHỨA danh mục của giao dịch". Ghi 50k vào "Cà phê"
     * phải kích hoạt cả ngân sách đặt ở "Ăn uống" (danh mục CHA) — lọc bằng so sánh bằng nhau
     * trong Java sẽ bỏ sót đúng ca này, khiến cảnh báo im lặng không bao giờ bắn.
     *
     * <p>Thêm điều kiện ngày trong kỳ để không cảnh báo ngân sách kỳ khác (view đã tính
     * {@code spent_amount} theo kỳ của chính nó, nhưng giao dịch ngoài kỳ thì không làm
     * {@code spent_amount} đó thay đổi — cảnh báo lúc ấy là nhiễu).
     */
    @Query(
            value = """
                    SELECT * FROM v_budget_progress WHERE user_id = :currentUser AND is_active = TRUE
                    AND :transactionDate BETWEEN start_date AND end_date
                    AND :categoryId IN (SELECT * FROM fn_category_tree(category_id))
                    """,
            nativeQuery = true)
    List<BudgetProgressProjection> findActiveByUserAndCategoryInTree(
            @Param("currentUser") UUID currentUser,
            @Param("categoryId") UUID categoryId,
            @Param("transactionDate") LocalDate transactionDate);

    // Các cột của {@code v_budget_progress}; Spring Data map theo tên getter -> tên cột.
    interface BudgetProgressProjection {
        UUID getId();

        UUID getCategoryId();

        UUID getWalletId();

        Long getLimitAmount();

        Long getSpentAmount();

        Long getRemaining();

        BigDecimal getRatio();

        // {@code normal} | {@code near_limit} | {@code over_limit} — ngưỡng 0.8 tính sẵn ở view.
        String getStatus();

        Integer getDaysRemaining();

        String getPeriodType();

        LocalDate getStartDate();

        LocalDate getEndDate();

        Boolean getAutoRenew();

        Boolean getIsActive();
    }
}
