package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.response.UserAccountResponse;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.event.UserCreateEvent;
import com.datn.financeapp.user.event.UserPasswordChangedEvent;
import com.datn.financeapp.user.exception.*;
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

    @Transactional(readOnly = true)
    @Override
    public UserAccountResponse findByEmail(String email) {
        return
                userRepository.findByEmail(email)
                        .map(userMapper::toAccountResponse)
                        .orElse(null);
    }

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

    @Override
    public void validateAccountForLogin(UserAccountResponse user) {
        if (user == null || user.isDeleted()) {
            throw new UserNotFoundException();
        }
        if (user.isBlocked()) {
            throw new UserBlockedException();
        }
    }
}
