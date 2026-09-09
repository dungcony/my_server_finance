package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.transaction.service.TransactionService;
import com.datn.financeapp.user.dto.request.UpdateMeRequest;
import com.datn.financeapp.user.dto.response.UserDetailResponse;
import com.datn.financeapp.user.dto.response.UserStatsResponse;
import com.datn.financeapp.user.dto.response.UserSummaryResponse;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.service.UserProfileService;
import com.datn.financeapp.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final TransactionService transactionService;
    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    @Override
    public UserDetailResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        long walletCount = walletService.countActiveWallets(userId);
        long transactionCount = transactionService.countActiveByUserId(userId);
        Long groupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members WHERE user_id = ? AND is_active",
                Long.class, userId);

        UserStatsResponse stats = new UserStatsResponse(
                walletCount, transactionCount, groupCount == null ? 0 : groupCount);

        return userMapper.toDetail(user, stats);
    }

    @Transactional
    @Override
    public UserSummaryResponse updateMe(UUID userId, UpdateMeRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (req.username() != null) {
            user.setUsername(req.username());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        userRepository.save(user);

        return userMapper.toSummary(user);
    }
}
