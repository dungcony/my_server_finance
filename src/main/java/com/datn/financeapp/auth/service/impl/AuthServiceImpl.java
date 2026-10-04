package com.datn.financeapp.auth.service.impl;

import com.datn.financeapp.auth.dto.request.*;
import com.datn.financeapp.auth.dto.response.AuthRes;
import com.datn.financeapp.auth.dto.response.RegisterRes;
import com.datn.financeapp.auth.dto.response.TokenRes;
import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.helper.ForgotPasswordRateLimiter;
import com.datn.financeapp.auth.helper.OtpHelper;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.auth.service.TokenService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.user.dto.request.UserCreateReq;
import com.datn.financeapp.user.dto.request.UserGetReq;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.service.UserService;
import com.datn.financeapp.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Transactional
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long OTP_TTL_MINUTES = 15L;
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final UserService userService;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    private final OtpRepository otpRepository;

    @Override
    public RegisterRes register(RegisterRequest req) {
        Instant now = Instant.now();

        if (userService.existByEmail(req.email()))
            throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);

        UserRes user = userService.create(
                UserCreateReq.forEmail(
                        req.email(),
                        passwordEncoder.encode(req.password()),
                        now
                )
        );
        sendEmail(req.email(), OtpType.REGISTER_OTP, now);

        return new RegisterRes(
                user.id(),
                req.email()
        );
    }


    @Override
    public AuthRes verifyEmail(VerifyEmailRequest req) {
        verifyOtp(req.email(), req.code(), OtpType.REGISTER_OTP);

        otpRepository.deleteByTypeAndEmail(OtpType.REGISTER_OTP, req.email());

        var user = userService.updateStatus(req.email(), UserStatus.ACTIVE);

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


    @Override
    public void resendVerification(ResendVerificationRequest req) {
        Instant now = Instant.now();

        // Tài khoản đã xoá hoặc bị khoá im lặng như req.email() chưa đăng ký — không gửi mã, không báo
        // lỗi, để người gửi không suy ra được trạng thái tài khoản.
        var u = userService.get(new UserGetReq(req.email()));

        if (u.isBlocked())
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED);

        if (u.isConfirm())
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_ALREADY_VERIFIED);

        OtpModel otp = otpRepository.findByTypeAndEmail(OtpType.REGISTER_OTP, req.email())
                .orElse(null);

        if (otp != null && Duration.between(otp.getCreatedAt(), now).getSeconds() < 60)
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "Vui lòng chờ 60 giây trước khi yêu cầu gửi lại mã.");

        sendEmail(req.email(), OtpType.REGISTER_OTP, now);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public TokenRes refresh(RefreshRequest req) {
        var userId = tokenService.checkRefreshAndGetUserId(req.refreshToken());

        UserRes user;
        try {
            user = userService.get(new UserGetReq(userId));
        } catch (UserNotFoundException e) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        if (user.isBlocked()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED);
        }

        return tokenService.create(
                new TokenCreateReq(
                        user.id(),
                        user.email(),
                        user.plan().name(),
                        user.getAuthorities(),
                        user.getTopRoleLevel()
                )
        );
    }


    @Override
    public void logout(UUID userId, String refreshToken, boolean isAll) {
        if (isAll) {
            tokenService.revokeAllByUserId(userId);
            return;
        }

        if (refreshToken != null)
            tokenService.revokeRefresh(refreshToken);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest req) {

        // đếm lượt trước khi tra email: email lạ cũng tốn lượt, và hết lượt thì không có thư nào được gửi
        forgotPasswordRateLimiter.consume(req.email());

        userService.validUser(req.email());

        sendEmail(req.email(), OtpType.PASSWORD_RESET_OTP, Instant.now());
    }

    @Override
    public void resetPassword(ResetPasswordRequest req) {

        verifyOtp(req.email(), req.resetCode(), OtpType.PASSWORD_RESET_OTP);
        userService.updatePass(req.email(), req.newPassword());
        otpRepository.deleteByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, req.email());

        tokenService.revokeAllByEmail(req.email());
    }

    //--------------------------------------------PRIVATE----------------------------------------//

    private void verifyOtp(String email, String otp, OtpType type) {
        OtpModel otpM = otpRepository.findByTypeAndEmail(type, email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_CODE_INVALID));

        if (otpM.getCode().equals(otp))
            return;

        registerWrongAttempt(otpM, type, email);
        throw new BusinessException(ErrorCode.AUTH_CODE_INVALID);
    }

    // đếm lần nhập sai, đủ giới hạn thì huỷ mã để không dò mã 6 chữ số từng bước được
    private void registerWrongAttempt(OtpModel otp, OtpType type, String email) {
        otp.setAttempts(otp.getAttempts() + 1);

        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            otpRepository.deleteByTypeAndEmail(type, email);
            return;
        }

        // lưu lại phải giữ hạn gốc, nếu không mỗi lần nhập sai lại kéo dài thêm cả hạn của mã
        long elapsedMinutes = Duration.between(otp.getCreatedAt(), Instant.now()).toMinutes();
        otp.setTtl(Math.max(1L, OTP_TTL_MINUTES - elapsedMinutes));
        otpRepository.save(otp);
    }

    private void sendEmail(String email, OtpType type, Instant now) {
        String otp = OtpHelper.create(6);
        otpRepository.save(OtpModel.builder()
                .email(email)
                .type(type)
                .code(otp)
                .ttl(OTP_TTL_MINUTES)
                .createdAt(now)
                .build());

        if (type.equals(OtpType.REGISTER_OTP)) {
            emailService.sendVerificationOtp(email, otp);

            return;
        }
        emailService.sendPasswordResetCode(email, otp);
    }

}
