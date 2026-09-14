package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.user.dto.request.UpdateMeRequest;
import com.datn.financeapp.user.dto.response.UserProfileResponse;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.event.UserDeletedEvent;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.exception.WrongPasswordException;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.service.ProfileService;
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
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    @Override
    public UserProfileResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        return userMapper.toProfile(user);
    }

    @Transactional
    @Override
    public UserProfileResponse updateMe(UUID userId, UpdateMeRequest req) {
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

        return userMapper.toProfile(user);
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

        // Rời mọi nhóm gia đình đang tham gia (api/01-EXTEND-USER-PROFILE.md mục 3): đánh dấu
        // is_active = FALSE chứ KHÔNG xoá dòng — giao dịch chung người này từng tạo vẫn phải tra
        // ngược được về chủ sở hữu, xoá dòng thì lịch sử nhóm mất chỗ bám.
        //
        // Dùng SQL thô vì module group/ chưa tồn tại (Phase 5 backend); khi có rồi thì chuyển
        // thành lời gọi service của module đó.
        jdbcTemplate.update(
                "UPDATE group_members SET is_active = FALSE WHERE user_id = ? AND is_active", uid);

        eventPublisher.publishEvent(new UserDeletedEvent(uid));
    }
}
