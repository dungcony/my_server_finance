package com.datn.financeapp.auth.repository;

import com.datn.financeapp.auth.entity.PasswordResetToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// AUTH-06: tra cứu mã đặt lại mật khẩu chưa dùng theo hash.
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
}
