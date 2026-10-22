package com.datn.financeapp.module.user.repository;

import com.datn.financeapp.module.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Override
    @EntityGraph(attributePaths = {"userRoles.role"})
    @NonNull
    Optional<User> findById(@NonNull UUID id);

    @EntityGraph(attributePaths = {"userRoles.role"})
    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByIdAndIsDeletedFalse(UUID userId);

    @Query("""
            select COUNT(u) > 0
            from User u
            where u.isDeleted = false
            and u.email = :email
            """)
    boolean existsByEmail(String email);

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
    List<String> findAuthoritiesByUserId(UUID userId);

    /**
     * Level của role MẠNH NHẤT user đang giữ. Quy ước "số nhỏ = quyền cao" nên role
     * mạnh
     * nhất ứng với MIN(level), không phải MAX. COALESCE về 2147483647
     * (Integer.MAX_VALUE)
     * khi user không có role nào — phải là số CỰC LỚN (= yếu nhất), không phải 0,
     * nếu không
     * user không role sẽ bị hiểu nhầm là mạnh nhất hệ thống (0 nhỏ hơn mọi level
     * thật).
     */
    @Query(value = """
            SELECT COALESCE(MIN(r.level), 2147483647) FROM roles r
            JOIN user_roles ur ON r.id = ur.role_id
            WHERE ur.user_id = :userId
            """, nativeQuery = true)
    int findTopRoleLevelByUserId(UUID userId);

    /**
     * CORE: chỉ trả user có role mạnh nhất YẾU HƠN {@code callerLevel} — lọc ngay
     * trong
     * câu truy vấn (không lấy hết rồi lọc ở code), dùng cho phân cấp quản lý (vd
     * quản lý
     * không được thấy admin/quản lý khác cùng hoặc cao cấp hơn mình). Vì số nhỏ =
     * quyền cao,
     * "role mạnh hơn hoặc bằng caller" nghĩa là level <= callerLevel.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.userRoles ur
            LEFT JOIN FETCH ur.role r
            WHERE NOT EXISTS (
                SELECT 1 FROM UserRole ur2 JOIN ur2.role r2
                WHERE ur2.userId = u.id AND r2.level <= :callerLevel
            )
            """)
    List<User> findAllVisibleToLevel(int callerLevel);

    @Query("""
            SELECT new com.datn.financeapp.module.user.dto.response.UserNameDisplayRes(
                u.id,
                u.firstName,
                u.lastName,
                u.status,
                u.isDeleted
            )
            FROM User u
            WHERE u.id IN :ids
            """)
    List<UserNameDisplayRes> findDisplay(List<UUID> ids);

    // Khoá dòng user để hai request mua gói không cùng qua bước kiểm tra "đang premium còn hạn".
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.isDeleted = false")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    // Chỉ hạ user có hạn đã qua; premium không hạn (planExpiresAt null) giữ nguyên.
    @Modifying(clearAutomatically = true)
    @Query("""
            update User u set u.plan = :free
            where u.plan = :premium and u.planExpiresAt is not null and u.planExpiresAt < :now
            """)
    int downgradeExpiredPremium(@Param("free") UserPlan free,
                                @Param("premium") UserPlan premium,
                                @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update User u set
                u.password  = coalesce(:password, u.password),
                u.status    = coalesce(:status, u.status),
                u.plan      = coalesce(:plan, u.plan),
                u.firstName = coalesce(:firstName, u.firstName),
                u.lastName  = coalesce(:lastName, u.lastName),
                u.avatarUrl = coalesce(:avatarUrl, u.avatarUrl),
                u.isDeleted = coalesce(:isDeleted, u.isDeleted),
                u.updatedAt = :now
            where u.id = :id
            """)
    int updatePartial(@Param("id") UUID id,
                      @Param("password") String password,
                      @Param("status") UserStatus status,
                      @Param("plan") UserPlan plan,
                      @Param("firstName") String firstName,
                      @Param("lastName") String lastName,
                      @Param("avatarUrl") String avatarUrl,
                      @Param("isDeleted") Boolean isDeleted,
                      @Param("now") Instant now);
}
