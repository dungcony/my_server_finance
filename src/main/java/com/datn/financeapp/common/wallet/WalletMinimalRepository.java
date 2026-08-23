package com.datn.financeapp.common.wallet;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository tối thiểu chỉ phục vụ AUTH-01 (tạo ví Tiền mặt lúc đăng ký) và AUTH-05
 * ({@code stats.wallet_count} ở GET /auth/me) — module wallet/ đầy đủ thuộc Phase 2, không
 * thêm method truy vấn nào khác ở đây.
 */
public interface WalletMinimalRepository extends JpaRepository<WalletMinimal, UUID> {

    long countByUserIdAndIsDeletedFalse(UUID userId);
}
