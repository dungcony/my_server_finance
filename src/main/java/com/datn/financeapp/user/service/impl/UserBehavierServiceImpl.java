package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.request.UpdateProfileRequest;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.event.publiser.UserDeletedEvent;
import com.datn.financeapp.user.event.publiser.UserPasswordChangedEvent;
import com.datn.financeapp.user.exception.*;
import com.datn.financeapp.user.helper.PasswordGenerator;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.service.UserBehavierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserBehavierServiceImpl implements UserBehavierService {


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final PasswordGenerator passwordGenerator;

    @Transactional(readOnly = true)
    @Override
    public UserRes getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        return userMapper.toResponse(user);
    }

    @Transactional
    @Override
    public UserRes updateMe(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (req.firstName() != null) {
            user.setFirstName(req.firstName());
        }
        if (req.lastName() != null) {
            user.setLastName(req.lastName());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    @Transactional
    @Override
    public void deleteMe(String password) {

        UUID uid = SecurityContextUtil.currentUserId();
        User user = userRepository.findById(uid)
                .orElseThrow(UserNotFoundException::new);

        if (user.getPassword() != null
                && (password == null || !passwordEncoder.matches(password, user.getPassword()))) {
            throw new WrongPasswordException(ErrorCode.WRONG_PASSWORD);
        }

        user.setDeleted(true);
        userRepository.save(user);

        eventPublisher.publishEvent(new UserDeletedEvent(uid));
    }

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

        if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
            throw new PasswordSameAsOldException();
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        eventPublisher.publishEvent(new UserPasswordChangedEvent(userId));
    }

    @Override
    public void createPassword(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // chỉ tài khoản chưa có mật khẩu (đăng nhập Google thuần) mới được tạo
        if (user.getPassword() != null) {
            throw new PasswordAlreadySetException();
        }

        String rawPassword = passwordGenerator.generate();
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);

        emailService.sendGeneratedPassword(user.getEmail(), rawPassword);
    }
}
