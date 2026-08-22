package com.datn.financeapp.auth.repository;

import com.datn.financeapp.auth.entity.LoginAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    /**
     * 5 lần gần nhất (cả thành công lẫn thất bại) — thuật toán "5 lần sai liên tiếp, một lần
     * đúng chen giữa phá chuỗi" xử lý ở tầng Service bằng Java thuần (AuthService.isLockedOut),
     * không viết native SQL phức tạp (theo khuyến nghị RESEARCH.md Open Question #2).
     */
    List<LoginAttempt> findTop5ByEmailOrderByAttemptedAtDesc(String email);
}
