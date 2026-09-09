package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.ChangePasswordRequest;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.dto.response.UserAccountResponse;
import com.datn.financeapp.user.dto.response.UserSummaryResponse;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service quản lý vòng đời tài khoản, bảo mật và xác thực người dùng (User Account).
 */
public interface UserAccountService {

    // Đổi mật khẩu tài khoản.
    void changePassword(UUID userId, ChangePasswordRequest req);

    // Xóa mềm tài khoản.
    void deleteAccount(UUID userId, DeleteAccountRequest req);

    // Tạo tài khoản người dùng mới qua đăng ký email.
    UserAccountResponse createEmailUser(String email, String rawPassword, Instant now);

    // Tạo tài khoản người dùng mới qua Google Login.
    UserAccountResponse createGoogleUser(String email, String googleId, Instant now);

    // Liên kết tài khoản Google với tài khoản hiện tại theo email.
    UserAccountResponse linkGoogleAccount(UUID userId, String googleId, Instant now);

    // Cập nhật thời điểm đăng nhập gần nhất.
    void recordLoginSuccess(UUID userId, Instant now);

    // Đặt lại mật khẩu mới sau khi xác thực mã OTP thành công.
    void resetPasswordWithCode(UUID userId, String newPassword);

    // Tìm tài khoản theo email phục vụ xác thực đăng nhập.
    Optional<UserAccountResponse> findForAuthByEmail(String email);

    // Tìm tài khoản theo googleId phục vụ xác thực Google.
    Optional<UserAccountResponse> findForAuthByGoogleId(String googleId);

    // Kiểm tra email đã tồn tại hay chưa.
    boolean existsByEmail(String email);

    // Tìm userId hợp lệ để gửi mã đặt lại mật khẩu (phải chưa bị khóa/xóa và có password).
    Optional<UUID> findUserIdForPasswordReset(String email);

    // Lấy tóm tắt tài khoản còn hoạt động (chưa bị khóa, chưa bị xóa mềm).
    Optional<UserAccountResponse> findActiveSummaryById(UUID userId);

    // Xác thực email người dùng (kích hoạt tài khoản: status = ACTIVE, is_confirm = true).
    void confirmUserEmail(String email);
}
