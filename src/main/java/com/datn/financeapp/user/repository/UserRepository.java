package com.datn.financeapp.user.repository;

import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    @Query(value = """
            SELECT DISTINCT p.name FROM permissions p
            JOIN role_permissions rp ON p.id = rp.permission_id
            JOIN user_roles ur ON rp.role_id = ur.role_id
            WHERE ur.user_id = :userId
            UNION
            SELECT DISTINCT r.name FROM roles r
            JOIN user_roles ur ON r.id = ur.role_id
            WHERE ur.user_id = :userId
            """, nativeQuery = true)
    List<String> findAuthoritiesByUserId(@Param("userId") UUID userId);

    /**
     * Level của role MẠNH NHẤT user đang giữ. Quy ước "số nhỏ = quyền cao" nên role mạnh
     * nhất ứng với MIN(level), không phải MAX. COALESCE về 2147483647 (Integer.MAX_VALUE)
     * khi user không có role nào — phải là số CỰC LỚN (= yếu nhất), không phải 0, nếu không
     * user không role sẽ bị hiểu nhầm là mạnh nhất hệ thống (0 nhỏ hơn mọi level thật).
     */
    @Query(value = """
            SELECT COALESCE(MIN(r.level), 2147483647) FROM roles r
            JOIN user_roles ur ON r.id = ur.role_id
            WHERE ur.user_id = :userId
            """, nativeQuery = true)
    int findTopRoleLevelByUserId(@Param("userId") UUID userId);

    /**
     * CORE: chỉ trả user có role mạnh nhất YẾU HƠN {@code callerLevel} — lọc ngay trong
     * câu truy vấn (không lấy hết rồi lọc ở code), dùng cho phân cấp quản lý (vd quản lý
     * không được thấy admin/quản lý khác cùng hoặc cao cấp hơn mình). Vì số nhỏ = quyền cao,
     * "role mạnh hơn hoặc bằng caller" nghĩa là level <= callerLevel.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.userRoles ur
            LEFT JOIN FETCH ur.role r
            LEFT JOIN FETCH r.rolePermissions
            WHERE NOT EXISTS (
                SELECT 1 FROM UserRole ur2 JOIN ur2.role r2
                WHERE ur2.userId = u.id AND r2.level <= :callerLevel
            )
            """)
    List<User> findAllVisibleToLevel(@Param("callerLevel") int callerLevel);

    @org.springframework.lang.NonNull
    @Override
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.userRoles ur
            LEFT JOIN FETCH ur.role r
            LEFT JOIN FETCH r.rolePermissions
            WHERE u.isDeleted = false
            """)
    List<User> findAll();

    @Transactional
    @Modifying
    @Query("""
            update User u
            set u.status = :status
            where u.id = :id
            """)
    void setStatusById(
            @Param("id") UUID id,
            @Param("status") UserStatus status);
}
