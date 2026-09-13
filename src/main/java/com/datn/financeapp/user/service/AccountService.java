package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.dto.response.UserAccountResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service quản lý vòng đời tài khoản, bảo mật và xác thực người dùng (User Account).
 */
public interface AccountService {
    UserAccountResponse findById(UUID userId);

    // Đổi mật khẩu tài khoản.
    void changePassword(UUID userId, UpdatePassReq req);

    // Tạo tài khoản người dùng mới qua đăng ký email.
    UserAccountResponse createEmailUser(String email, String rawPassword, Instant now);

    // Tạo tài khoản người dùng mới qua Google Login.
    UserAccountResponse createGoogleUser(String email, String googleId, Instant now);

    // Liên kết tài khoản Google với tài khoản hiện tại theo email.
    UserAccountResponse linkGoogleAccount(UUID userId, String googleId, Instant now);

    // Đặt lại mật khẩu mới sau khi xác thực mã OTP thành công.
    void resetPasswordWithCode(UUID userId, String newPassword);

    // Tìm tài khoản theo email phục vụ xác thực đăng nhập.
    UserAccountResponse findByEmail(String email);

    // Tìm tài khoản theo googleId phục vụ xác thực Google.
    UserAccountResponse findByGoogleId(String googleId);

    // Kiểm tra email đã tồn tại hay chưa.
    boolean existsByEmail(String email);

    void block(UUID id);

    // Tìm userId hợp lệ để gửi mã đặt lại mật khẩu (phải chưa bị khóa/xóa và có password).
    Optional<UUID> findUserIdForPasswordReset(String email);

    // Lấy tóm tắt tài khoản còn hoạt động (chưa bị khóa, chưa bị xóa mềm).
    Optional<UserAccountResponse> findActiveSummaryById(UUID userId);

    // Lấy danh sách quyền và vai trò hệ thống của người dùng.
    List<String> findAuthoritiesByUserId(UUID userId);

    // Level của role MẠNH NHẤT (số nhỏ nhất) trong các role đang gán cho user — nhúng vào JWT claim "roles_level_top".
    int findTopRoleLevel(UUID userId);
}
