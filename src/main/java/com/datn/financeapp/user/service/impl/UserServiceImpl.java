package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.user.dto.request.UserCreateReq;
import com.datn.financeapp.user.dto.request.UserGetReq;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.event.publiser.UserCreateEvent;
import com.datn.financeapp.user.exception.UserBlockedException;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import com.datn.financeapp.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;

    @Override
    public UserRes create(UserCreateReq req) {

        User user = userMapper.toEntity(req);

        user.setId(UUID.randomUUID());
        user.setPlan(UserPlan.FREE);

        if (user.getStatus() == null)
            user.setStatus(UserStatus.PENDING_VERIFY);

        user.setDeleted(false);

        userRepository.save(user);
        assignDefaultRole(user);
        eventPublisher.publishEvent(new UserCreateEvent(user.getId()));
        return userMapper.toResponse(user);
    }

    @Override
    public UserRes get(UserGetReq req) {

        if (req.email() != null)
            return userRepository.findByEmail(req.email())
                    .filter(u -> u.isDeleted() == req.deleted())
                    .map(userMapper::toResponse)
                    .orElseThrow(UserNotFoundException::new);


        if (req.id() != null)
            return userRepository.findById(req.id())
                    .filter(u -> u.isDeleted() == req.deleted())
                    .map(userMapper::toResponse)
                    .orElseThrow(UserNotFoundException::new);

        return null;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<UUID, UserRes> getNames(List<UUID> ids, List<String> emails) {
        Map<UUID, UserRes> result = new HashMap<>();
        List<User> users = new ArrayList<>();

        if (ids != null && emails != null)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);

        if (ids != null)
            users = userRepository.findAllById(ids);
        if (emails != null)
            users = userRepository.findAllByEmailIn(emails);

        for (var u : users) {

            UserRes user;

            if (u.isDeleted() || u.isLocked()) {
                user = new UserRes(
                        "Người dùng App",
                        u.getStatus(),
                        u.isDeleted()
                );
            } else user = userMapper.toResponse(u);

            result.put(u.getId(), user);
        }

        return result;
    }

    @Override
    public UserRes updateStatus(String email, UserStatus status) {
        User user = userRepository.findByEmail(email)
                .filter(u -> !u.isDeleted())
                .orElseThrow(UserNotFoundException::new);

        user.setStatus(status);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserRes updatePass(String email, String password) {
        User user = userRepository.findByEmail(email)
                .filter(u -> !u.isLocked() && !u.isDeleted())
                .orElseThrow(UserNotFoundException::new);

        if (passwordEncoder.matches(password, user.getPassword()))
            throw new BusinessException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);

        user.setPassword(passwordEncoder.encode(password));
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserRes resolveGoogleUser(String email, String googleId, Instant now) {
        // đã từng đăng nhập Google
        User user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null)
            return createGoogleUser(email, googleId, now);

        if (user.getGoogleId() == null)
            user.setGoogleId(googleId);

        else if (!user.getGoogleId().equals(googleId))
            // nên gửi lỗi khác nhưng chưa nghĩ ra
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);

        validateAccountForLogin(userMapper.toResponse(user));

        // Google đã xác thực email nên coi như đã confirm
        user.setStatus(UserStatus.ACTIVE);

        return userMapper.toResponse(user);
    }

    @Override
    public boolean existByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public void validUser(String email) {
        User user = userRepository.findByEmail(email)
                .filter(u -> !u.isDeleted())
                .orElseThrow(UserNotFoundException::new);

        if (!user.isConfirm())
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_VERIFIED);

        if (user.isLocked())
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED);
    }


    //-------------------------------------PRIVATE-------------------------------//


    private UserRes createGoogleUser(String email, String googleId, Instant now) {
        return persistNewUser(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password(null)
                .googleId(googleId)
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(now)
                .build());
    }

    // đoạn lặp của createEmailUser và createGoogleUser
    private UserRes persistNewUser(User user) {
        userRepository.save(user);
        assignDefaultRole(user);
        eventPublisher.publishEvent(new UserCreateEvent(user.getId()));
        return userMapper.toResponse(user);
    }


    private void validateAccountForLogin(UserRes user) {
        if (user == null || user.isDeleted())
            throw new UserNotFoundException();

        if (user.isBlocked())
            throw new UserBlockedException();
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
}
