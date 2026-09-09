package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.user.dto.request.ChangePasswordRequest;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.dto.response.UserAccountResponse;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.event.UserDeletedEvent;
import com.datn.financeapp.user.event.UserPasswordChangedEvent;
import com.datn.financeapp.user.exception.NoPasswordSetException;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.exception.WrongPasswordException;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.service.UserAccountService;
import com.datn.financeapp.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;

    @Transactional
    @Override
    public void changePassword(UUID userId, ChangePasswordRequest req) {
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
    public void deleteAccount(UUID userId, DeleteAccountRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getPassword() != null
                && (req == null || req.password() == null || !passwordEncoder.matches(req.password(), user.getPassword()))) {
            throw new WrongPasswordException(ErrorCode.WRONG_PASSWORD);
        }

        user.setDeleted(true);
        userRepository.save(user);

        // Rời các nhóm gia đình đang tham gia (bảng group_members)
        jdbcTemplate.update(
                "UPDATE group_members SET is_active = FALSE WHERE user_id = ? AND is_active", userId);

        eventPublisher.publishEvent(new UserDeletedEvent(userId));
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
        walletService.createDefaultCashWallet(user.getId(), now);

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
        walletService.createDefaultCashWallet(user.getId(), now);

        return userMapper.toAccountResponse(user);
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
    public void recordLoginSuccess(UUID userId, Instant now) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(now);
            userRepository.save(user);
        });
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

    @Transactional(readOnly = true)
    @Override
    public Optional<UserAccountResponse> findForAuthByEmail(String email) {
        return userRepository.findByEmail(email).map(userMapper::toAccountResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<UserAccountResponse> findForAuthByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId).map(userMapper::toAccountResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
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

    @Transactional
    @Override
    public void confirmUserEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        user.setConfirm(true);
        userRepository.save(user);
    }
}
