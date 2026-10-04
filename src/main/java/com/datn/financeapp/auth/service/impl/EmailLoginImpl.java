package com.datn.financeapp.auth.service.impl;

import com.datn.financeapp.auth.dto.request.EmailLoginRequest;
import com.datn.financeapp.auth.dto.request.TokenCreateReq;
import com.datn.financeapp.auth.dto.response.AuthRes;
import com.datn.financeapp.auth.entity.LoginAttempt;
import com.datn.financeapp.auth.helper.ClientInfo;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.service.LoginService;
import com.datn.financeapp.auth.service.TokenService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.user.dto.request.UserGetReq;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailLoginImpl implements LoginService<EmailLoginRequest> {
    private final UserService userService;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    // phải giữ noRollbackFor, nếu không bản ghi lần đăng nhập sai bị rollback và cơ chế khoá 5 lần mất tác dụng
    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public AuthRes login(EmailLoginRequest req, ClientInfo client) {
        String email = req.email().toLowerCase().trim();

        if (isLockedOut(email))
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);

        UserRes user = null;
        try {
            user = userService.get(new UserGetReq(email));
        } catch (UserNotFoundException e) {
            // bỏ qua, user sẽ bằng null và tiếp tục lưu lịch sử sai
        }

        // lấy trạng thái đăng nhập
        // user = null không tìm thấy
        // password = null sai thông tin
        // password sai -> sai thông tin
        // 1 trong 3 sai thì là đăng nhập sai
        boolean isSuccess = user != null
                && user.password() != null
                && passwordEncoder.matches(req.password(), user.password());

        saveLoginAttemp(email, isSuccess, client, Instant.now());

        if (!isSuccess)
            throw new BusinessException(ErrorCode.AUTH_CREDENTIALS_INVALID);

        if (user.isBlocked())
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED);

        if (!user.isConfirm())
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);

        return new AuthRes(
                user,
                tokenService.create(new TokenCreateReq(
                        user.id(),
                        user.email(),
                        user.plan().name(),
                        user.getAuthorities(),
                        user.getTopRoleLevel()
                ))
        );
    }

    //---------------------------------PRIVATE-------------------------------------------//


    private void saveLoginAttemp(String email, boolean isSusscess, ClientInfo client, Instant now) {
        loginAttemptRepository.save(LoginAttempt.builder()
                .id(UUID.randomUUID())
                .email(email)
                .ipAddress(client.ipAddress())
                .succeeded(isSusscess)
                .userAgent(client.userAgent())
                .attemptedAt(now)
                .build());

    }

    private boolean isLockedOut(String email) {
        // BƯỚC 1: Lấy 5 lần đăng nhập gần đây nhất của email này
        List<LoginAttempt> recent = loginAttemptRepository.findTop5ByEmailOrderByAttemptedAtDesc(email);

        // BƯỚC 2: Nếu chưa đủ 5 lần đăng nhập -> chưa đủ điều kiện khóa -> cho qua
        int maxConsecutiveFailures = 5;
        int lockMinutes = 15;

        if (recent.size() < maxConsecutiveFailures)
            return false;

        // BƯỚC 3: Kiểm tra xem cả 5 lần đó có PHẢI ĐỀU THẤT BẠI (nhập sai pass) hay không
        // (Nếu có 1 lần đăng nhập đúng xen vào giữa thì chuỗi sai bị ngắt -> không khóa)
        boolean allFailed = recent.stream().noneMatch(LoginAttempt::getSucceeded);
        if (!allFailed)
            return false;

        // BƯỚC 4: Kiểm tra thời gian của lần sai thứ 5 (lần gần nhất - recent.get(0))
        Instant mostRecentFailure = recent.get(0).getAttemptedAt();

        // Nếu lần sai gần nhất diễn ra trong vòng 15 phút trở lại đây -> TRẢ VỀ TRUE (ĐANG BỊ KHÓA)

        return mostRecentFailure.isAfter(Instant.now().minus(lockMinutes, ChronoUnit.MINUTES));
    }
}
