package com.datn.financeapp.wallet.repository;

import com.datn.financeapp.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Wallet}. Mọi method chọn/sửa/xoá MỘT ví theo id phải kèm điều kiện
 * quyền D-27 ngay trong câu SQL ({@code user_id = :currentUser OR group_id IN (...)}) — vế
 * nhóm hiện luôn trả rỗng (chưa ai vào {@code group_members}) nhưng viết sẵn để không phải rà
 * lại khi Group ra đời ở Phase 5.
 *
 * Lưu ý schema thật (đối chiếu {@code db/migration/V1__nen_tang.sql}, không phải theo mô tả
 * trong PLAN/CONTEXT — CLAUDE.md quy tắc 0): {@code group_members} lọc thành viên đang hoạt
 * động bằng cột {@code is_active BOOLEAN}, KHÔNG có cột {@code status}.
 */
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    long countByUserIdAndIsDeletedFalse(UUID userId);

    boolean existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(UUID userId, String name);

    @Query(
            value = "SELECT COALESCE(MAX(sort_order), -1) FROM wallets WHERE user_id = :userId AND NOT is_deleted",
            nativeQuery = true)
    Integer findMaxSortOrderByUserId(@Param("userId") UUID userId);

    @Query(
            value = "SELECT * FROM wallets w WHERE w.id = :id AND NOT w.is_deleted "
                    + "AND (w.user_id = :currentUser OR w.group_id IN "
                    + "(SELECT group_id FROM group_members WHERE user_id = :currentUser AND is_active))",
            nativeQuery = true)
    Optional<Wallet> findByIdForUser(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    /**
     * Dùng cho DELETE idempotent (CORE-06): tìm ví theo id + quyền D-27 KHÔNG lọc
     * {@code is_deleted} — cho phép service phân biệt "không tồn tại/không có quyền" (404) với
     * "đã xoá mềm rồi, gọi lại vẫn 200" (không phải lỗi).
     */
    @Query(
            value = "SELECT * FROM wallets w WHERE w.id = :id "
                    + "AND (w.user_id = :currentUser OR w.group_id IN "
                    + "(SELECT group_id FROM group_members WHERE user_id = :currentUser AND is_active))",
            nativeQuery = true)
    Optional<Wallet> findByIdForUserIncludingDeleted(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(
            value = "SELECT * FROM wallets w WHERE NOT w.is_deleted "
                    + "AND (w.user_id = :currentUser OR (:includeShared = TRUE AND w.group_id IN "
                    + "(SELECT group_id FROM group_members WHERE user_id = :currentUser AND is_active))) "
                    + "AND (:type IS NULL OR w.type = :type) "
                    + "AND (:onlyInTotal IS NULL OR w.include_in_total = :onlyInTotal) "
                    + "ORDER BY w.sort_order",
            nativeQuery = true)
    List<Wallet> findAllForUser(
            @Param("currentUser") UUID currentUser,
            @Param("type") String type,
            @Param("onlyInTotal") Boolean onlyInTotal,
            @Param("includeShared") boolean includeShared);

    @Modifying
    @Query("UPDATE Wallet w SET w.sortOrder = :order WHERE w.id = :id AND w.userId = :currentUser")
    int updateSortOrder(@Param("id") UUID id, @Param("order") int order, @Param("currentUser") UUID currentUser);

    /**
     * Khoá bi quan (SELECT ... FOR UPDATE) — dùng trước khi đọc/ghi {@code current_balance} để
     * tránh lost-update khi có request đồng thời trên cùng ví (transfer/adjust-balance). Mẫu
     * theo {@code RefreshTokenRepository.findActiveByTokenHashForUpdate}. KHÔNG kiểm tra quyền
     * D-27 ở đây — caller phải tự kiểm tra {@code wallet.getUserId()} sau khi lock, vì 2 ví
     * trong 1 lần transfer có thể thuộc 2 chủ sở hữu khác nhau và ta cần phân biệt rõ ví nào
     * sai quyền để trả đúng lỗi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id AND NOT w.isDeleted")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Atomic UPDATE số dư — KHÔNG load-modify-save (CLAUDE.md backend mục 3). {@code delta} có
     * thể âm (trừ) hoặc dương (cộng).
     */
    @Modifying
    @Query("UPDATE Wallet w SET w.currentBalance = w.currentBalance + :delta WHERE w.id = :id")
    int adjustBalance(@Param("id") UUID id, @Param("delta") long delta);
}
