package com.datn.financeapp.wallet.repository;

import com.datn.financeapp.wallet.entity.Wallet;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA cho {@link Wallet}. Ở plan này chỉ giữ method tối thiểu cần cho
 * {@code AuthService} (đếm ví cho {@code stats.wallet_count} ở GET /auth/me) — plan 02-02 sẽ
 * mở rộng thêm method CRUD/quyền vào chính interface này, không tạo file mới.
 */
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    long countByUserIdAndIsDeletedFalse(UUID userId);
}
