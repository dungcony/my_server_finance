package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.dto.response.UserAccountResponse;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.event.UserCreateEvent;
import com.datn.financeapp.user.event.UserDeletedEvent;
import com.datn.financeapp.user.event.UserPasswordChangedEvent;
import com.datn.financeapp.user.exception.NoPasswordSetException;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.exception.WrongPasswordException;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.service.AccountService;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;

    @Override
    public UserAccountResponse findById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        return userMapper.toAccountResponse(user);
    }

    @Transactional
    @Override
    public void changePassword(UUID userId, UpdatePassReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getPassword() == null) {
            throw new NoPasswordSetException();
        }

        if (!passwordEncoder.matches(req.oldPassword(), user.getPassword())) {
            throw new WrongPasswordException(ErrorCode.WRONG_OLD_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        eventPublisher.publishEvent(new UserPasswordChangedEvent(userId));
    }

    @Transactional
    @Override
    public UserAccountResponse createEmailUser(String email, String rawPassword, Instant now) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .plan(UserPlan.FREE)
                .status(UserStatus.PENDING_VERIFY)
                .isDeleted(false)
                .createdAt(now)
                .build();
        userRepository.save(user);
        assignDefaultRole(user);
        eventPublisher.publishEvent(new UserCreateEvent(user.getId()));

        return userMapper.toAccountResponse(user);
    }

    @Transactional
    @Override
    public UserAccountResponse createGoogleUser(String email, String googleId, Instant now) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password(null)
                .googleId(googleId)
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(now)
                .lastLoginAt(now)
                .build();
        userRepository.save(user);
        assignDefaultRole(user);
        eventPublisher.publishEvent(new UserCreateEvent(user.getId()));

        return userMapper.toAccountResponse(user);
    }

    private void assignDefaultRole(User user) {
        log.info("đang đăng ký roles.....");
        roleRepository.findByName(RoleName.ROLE_USER).ifPresent(role -> {
            UserRole userRole = new UserRole(user.getId(), role.getId());
            userRole.setUser(user);
            userRole.setRole(role);
            userRoleRepository.save(userRole);
            user.getUserRoles().add(userRole);
        });
    }

    @Transactional
    @Override
    public UserAccountResponse linkGoogleAccount(UUID userId, String googleId, Instant now) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.setGoogleId(googleId);
        user.setConfirm(true);
        user.setLastLoginAt(now);
        userRepository.save(user);

        return userMapper.toAccountResponse(user);
    }

    @Transactional
    @Override
    public void resetPasswordWithCode(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isBlocked() && !u.isDeleted())
                .orElseThrow(UserNotFoundException::new);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Tra tài khoản theo email — <b>chỉ trả dữ liệu, không phán xét trạng thái</b>.
     *
     * <p>Trước đây method này ném {@code UserBlockedException}/{@code UserNotFoundException} ngay
     * khi thấy tài khoản bị khoá hoặc đã xoá. Việc đó hỏng theo hai cách:
     *
     * <ol>
     *   <li><b>Mất bản ghi {@code login_attempts}.</b> {@code AuthService.login} khai báo
     *       {@code noRollbackFor = BusinessException.class} để giữ lại lần đăng nhập hỏng, nhưng
     *       exception ném từ transaction LỒNG ở đây đánh dấu rollback-only — vòng ngoài không gỡ
     *       được, commit nổ {@code UnexpectedRollbackException} và bản ghi biến mất. Hệ quả: cơ
     *       chế khoá tài khoản sau 5 lần sai mật khẩu không đếm được gì.
     *   <li><b>Caller không quyết được thứ tự kiểm tra.</b> Đăng nhập sai mật khẩu vào một tài
     *       khoản bị khoá phải trả {@code INVALID_CREDENTIALS} (không xác nhận tài khoản có tồn
     *       tại); đăng nhập đúng mật khẩu mới được trả {@code ACCOUNT_BLOCKED}. Ném sẵn ở đây thì
     *       caller không còn cơ hội phân biệt.
     * </ol>
     *
     * <p>Trạng thái vẫn đọc được qua {@code UserAccountResponse.isBlocked()}/{@code isDeleted()} —
     * mỗi caller tự kiểm đúng lúc nó cần.
     */
    @Transactional(readOnly = true)
    @Override
    public UserAccountResponse findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toAccountResponse)
                .orElse(null);
    }

    /** Xem {@link #findByEmail(String)} — cùng lý do chỉ trả dữ liệu, không ném theo trạng thái. */
    @Transactional(readOnly = true)
    @Override
    public UserAccountResponse findByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId)
                .map(userMapper::toAccountResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public void block(UUID id) {
        userRepository.setStatusById(id, UserStatus.BLOCKED);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<UUID> findUserIdForPasswordReset(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .filter(u -> !u.isBlocked() && !u.isDeleted())
                .filter(u -> u.getPassword() != null)
                .map(User::getId);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<UserAccountResponse> findActiveSummaryById(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isBlocked() && !u.isDeleted())
                .map(userMapper::toAccountResponse);
    }

    @Override
    public java.util.List<String> findAuthoritiesByUserId(UUID userId) {
        return userRepository.findAuthoritiesByUserId(userId);
    }

    @Override
    public int findTopRoleLevel(UUID userId) {
        return userRepository.findTopRoleLevelByUserId(userId);
    }


}
