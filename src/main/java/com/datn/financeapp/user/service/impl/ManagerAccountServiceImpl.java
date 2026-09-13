package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.BlacklistedUserRepository;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.dto.request.UpdateUserRoleReq;
import com.datn.financeapp.user.dto.response.UserAccountResponse;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.event.UserDeletedEvent;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import com.datn.financeapp.user.service.ManagerAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerAccountServiceImpl implements ManagerAccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthService authService;
    private final BlacklistedUserRepository blacklistedUserRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;

    private static final long DEFAULT_BLACKLIST_TTL_SECONDS = 3600;
    // Quy ước "số nhỏ = quyền cao" — user không có role nào phải là YẾU NHẤT (số cực lớn).
    private static final int NO_ROLE_LEVEL = Integer.MAX_VALUE;

    @Transactional
    @Override
    public void blockUser(BlockUserRequest req) {
        UUID currentUserId = SecurityContextUtil.currentUserId();
        if (req.userId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không được phép tự khóa tài khoản của chính mình.");
        }

        User user = getActiveUser(req.userId());

        int targetLevel = getLevel(req.userId());
        int currentUserLevel = getLevel(currentUserId);

        // Số nhỏ = quyền cao — không được khóa user có cấp bậc cao hơn hoặc bằng chính mình.
        if (targetLevel <= currentUserLevel) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được khóa tài khoản có cấp bậc cao hơn hoặc bằng chính mình.");
        }

        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);

        // 1. Blacklist token trong Redis
        blacklistedUserRepository.blacklist(req.userId(), req.reason(), DEFAULT_BLACKLIST_TTL_SECONDS);

        // 2. Thu hồi toàn bộ Refresh Tokens
        authService.revokeAllTokensForUser(req.userId());

        log.info("User {} đã khóa tài khoản user {} với lý do: {}", currentUserId, req.userId(), req.reason());
    }

    @Transactional
    @Override
    public void addRoleToUser(UpdateUserRoleReq req) {
        if (req == null || req.roleName() == null || req.userId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng cung cấp đủ thông tin.");
        }

        User user = getActiveUser(req.userId());
        Role role = getRole(req.roleName());

        int currentUserLevel = getLevel(SecurityContextUtil.currentUserId());

        // Số nhỏ = quyền cao, nên "role cao hơn hoặc bằng mình" nghĩa là level <= currentUserLevel.
        if (role.getLevel() <= currentUserLevel) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được gán vai trò có cấp bậc cao hơn hoặc bằng chính mình.");
        }

        if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())) {
            UserRole userRole = new UserRole(user.getId(), role.getId());
            userRole.setUser(user);
            userRole.setRole(role);
            userRoleRepository.save(userRole);
            user.getUserRoles().add(userRole);
            log.info("User {} đã gán role {} cho user {}", SecurityContextUtil.currentUserId(), role.getName(), req.userId());
        }
    }

    @Transactional
    @Override
    public void removeRoleToUser(UpdateUserRoleReq req) {
        if (req == null || req.roleName() == null || req.userId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng cung cấp đủ thông tin.");
        }
        User user = getActiveUser(req.userId());
        Role role = getRole(req.roleName());

        UUID currentUserId = SecurityContextUtil.currentUserId();
        int currentUserLevel = getLevel(currentUserId);

        // Số nhỏ = quyền cao — không được thu hồi role có cấp bậc cao hơn hoặc bằng chính mình
        // (vd tránh 1 người yếu hơn tự ý gỡ role của người mạnh hơn).
        if (role.getLevel() <= currentUserLevel) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được thu hồi vai trò có cấp bậc cao hơn hoặc bằng chính mình.");
        }

        if (userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())) {
            userRoleRepository.deleteByUserIdAndRoleId(user.getId(), role.getId());
            user.getUserRoles().removeIf(ur -> ur.getRoleId().equals(role.getId()));
            log.info("User {} đã thu hồi role {} của user {}", currentUserId, role.getName(), req.userId());
        }
    }

    @Transactional
    @Override
    public UserAccountResponse deleteByUserId(UUID userId) {
        UUID currentUserId = SecurityContextUtil.currentUserId();
        if (userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không được phép tự xóa tài khoản của chính mình.");
        }

        User user = getActiveUser(userId);

        int targetLevel = getLevel(userId);
        int currentUserLevel = getLevel(currentUserId);

        // Số nhỏ = quyền cao — không được xóa user có cấp bậc cao hơn hoặc bằng chính mình.
        if (targetLevel <= currentUserLevel) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được xóa tài khoản có cấp bậc cao hơn hoặc bằng chính mình.");
        }

        user.setDeleted(true);
        userRepository.save(user);

        eventPublisher.publishEvent(new UserDeletedEvent(userId));

        return userMapper.toAccountResponse(user);
    }

    @Transactional(readOnly = true)
    @Override
    public UserAccountResponse findByUserId(UUID userId) {

        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng."));

        int targetLevel = getLevel(userId);

        // Số nhỏ = quyền cao. Không được xem user có role cấp bằng/cao hơn mình
        // (level <= currentLevel) — trả 404 để không lộ user có tồn tại hay không (CORE-05).
        if (targetLevel <= SecurityContextUtil.currentLevel()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng.");
        }

        return userMapper.toAccountResponse(user);
    }

    @Transactional(readOnly = true)
    @Override
    public List<UserAccountResponse> findAllUser() {

        return userRepository.findAllVisibleToLevel(SecurityContextUtil.currentLevel())
                .stream()
                .map(userMapper::toAccountResponse)
                .toList();
    }

    private User getActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(UserNotFoundException::new);
    }

    private Role getRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy vai trò."));
    }

    // Level của role mạnh nhất (số nhỏ nhất) mà user này đang giữ — dùng cho cả người bị
    // thao tác lẫn người đang gọi API, không riêng gì admin.
    private int getLevel(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> u.getUserRoles().stream()
                        .map(UserRole::getRole)
                        .filter(Objects::nonNull)
                        .mapToInt(Role::getLevel)
                        .min()
                        .orElse(NO_ROLE_LEVEL))
                .orElse(NO_ROLE_LEVEL);
    }
}
