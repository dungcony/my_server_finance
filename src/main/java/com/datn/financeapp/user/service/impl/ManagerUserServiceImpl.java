package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.BlacklistedUserRepository;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.dto.request.UpdateUserRoleReq;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.event.publiser.UserDeletedEvent;
import com.datn.financeapp.user.event.publiser.UserLockedEvent;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.helper.RolePermissionCacheHelper;
import com.datn.financeapp.user.helper.UserAuthCacheHelper;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import com.datn.financeapp.user.service.ManagerUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerUserServiceImpl implements ManagerUserService {

    private final UserRepository userRepository;
    private final RolePermissionCacheHelper rolePermissionCacheHelper;
    private final UserRoleRepository userRoleRepository;
    private final BlacklistedUserRepository blacklistedUserRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;
    private final UserAuthCacheHelper userAuthCacheHelper;

    private static final long DEFAULT_BLACKLIST_TTL_SECONDS = 3600;

    @Transactional
    @Override
    public void lockUser(UUID managerId, BlockUserRequest req) {
        if (req.userId().equals(managerId))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không được phép tự khóa tài khoản của chính mình.");

        User user = getActiveUser(req.userId());

        int targetLevel = getLevel(req.userId());
        int managerLevel = getLevel(managerId);

        // Số nhỏ = quyền cao — không được khóa user có cấp bậc cao hơn hoặc bằng chính mình.
        if (targetLevel <= managerLevel) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được khóa tài khoản có cấp bậc cao hơn hoặc bằng chính mình.");
        }

        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);

        // blacklist token trong Redis
        blacklistedUserRepository.add(req.userId(), req.reason(), DEFAULT_BLACKLIST_TTL_SECONDS);

        // thu hồi toàn bộ Refresh Tokens
        eventPublisher.publishEvent(new UserLockedEvent(req.userId()));

        log.info("User {} đã khóa tài khoản user {} với lý do: {}", managerId, req.userId(), req.reason());
    }

    @Transactional
    @Override
    public void addRoleToUser(UpdateUserRoleReq req) {
        if (req == null || req.roleName() == null || req.userId() == null)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng cung cấp đủ thông tin.");

        User user = getActiveUser(req.userId());
        Role role = getRole(req.roleName());

        int currentUserLevel = getLevel(SecurityContextUtil.currentUserId());

        // Số nhỏ = quyền cao, nên "role cao hơn hoặc bằng mình" nghĩa là level <= currentUserLevel.
        if (role.getLevel() <= currentUserLevel)
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được gán vai trò có cấp bậc cao hơn hoặc bằng chính mình.");

        if (userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId()))
            throw new BusinessException(ErrorCode.USER_ROLE_ALREADY_ASSIGNED);

        UserRole userRole = new UserRole(user.getId(), role.getId());
        userRole.setUser(user);
        userRole.setRole(role);
        userRoleRepository.save(userRole);
        user.getUserRoles().add(userRole);
        userAuthCacheHelper.evictUserAuth(user.getId());
        log.info("User {} đã gán role {} cho user {}", SecurityContextUtil.currentUserId(), role.getName(), req.userId());
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

        // vai trò mặc định gán lúc tạo tài khoản, gỡ đi thì người dùng mất toàn bộ quyền cơ bản
        if (role.getName() == RoleName.ROLE_USER) {
            throw new BusinessException(ErrorCode.USER_DEFAULT_ROLE_NOT_REMOVABLE);
        }

        if (userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())) {
            userRoleRepository.deleteByUserIdAndRoleId(user.getId(), role.getId());
            user.getUserRoles().removeIf(ur -> ur.getRoleId().equals(role.getId()));
            userAuthCacheHelper.evictUserAuth(user.getId());
            log.info("User {} đã thu hồi role {} của user {}", currentUserId, role.getName(), req.userId());
        }
    }

    @Transactional
    @Override
    public UserRes deleteByUserId(UUID userId) {
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
        userAuthCacheHelper.evictUserAuth(userId);

        eventPublisher.publishEvent(new UserDeletedEvent(userId));

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    @Override
    public UserRes findByUserId(UUID userId) {

        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng."));

        int targetLevel = getLevel(userId);

        // Số nhỏ = quyền cao. Không được xem user có role cấp bằng/cao hơn mình
        // (level <= currentLevel) — trả 404 để không lộ user có tồn tại hay không (CORE-05).
        if (targetLevel <= SecurityContextUtil.currentLevel()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng.");
        }

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    @Override
    public List<UserRes> findAllUser() {

        return userRepository.findAllVisibleToLevel(SecurityContextUtil.currentLevel())
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    private User getActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(UserNotFoundException::new);
    }

    private Role getRole(RoleName roleName) {
        if (roleName == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định danh vai trò không được để trống.");
        }
        return rolePermissionCacheHelper.findRoleByName(roleName)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy vai trò."));
    }

    // Level của role mạnh nhất (số nhỏ nhất) mà user này đang giữ — dùng cho cả người bị
    // thao tác lẫn người đang gọi API, không riêng gì admin.
    private int getLevel(UUID userId) {
        return userAuthCacheHelper.getUserLevel(userId);
    }
}
