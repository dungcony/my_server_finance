package com.datn.financeapp.module.user.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.service.PaymentService;
import com.datn.financeapp.module.user.config.PremiumPlanProperties;
import com.datn.financeapp.module.user.dto.request.UserCreateReq;
import com.datn.financeapp.module.user.dto.request.UserGetReq;
import com.datn.financeapp.module.user.dto.request.UserUpdateReq;
import com.datn.financeapp.module.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.module.user.dto.response.UserRes;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.entity.UserRole;
import com.datn.financeapp.module.user.enums.PremiumPlan;
import com.datn.financeapp.module.user.enums.RoleName;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.event.publiser.UserCreateEvent;
import com.datn.financeapp.module.user.exception.UserBlockedException;
import com.datn.financeapp.module.user.exception.UserNotFoundException;
import com.datn.financeapp.module.user.helper.RolePermissionCacheHelper;
import com.datn.financeapp.module.user.mapper.UserMapper;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.user.repository.UserRoleRepository;
import com.datn.financeapp.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RolePermissionCacheHelper rolePermissionCacheHelper;
    private final UserRoleRepository userRoleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;
    private final PaymentService paymentService;
    private final PremiumPlanProperties premiumPlanProperties;
    private final Clock clock;

    @Override
    public UserRes create(UserCreateReq req) {

        User user = userMapper.toEntity(req);

        user.setId(UUID.randomUUID());
        user.setPlan(UserPlan.FREE);

        if (user.getStatus() == null)
            user.setStatus(UserStatus.PENDING_VERIFY);

        user.setIsDeleted(false);

        userRepository.save(user);
        assignDefaultRole(user);
        eventPublisher.publishEvent(new UserCreateEvent(user.getId()));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true, noRollbackFor = UserNotFoundException.class)
    @Override
    public UserRes get(UserGetReq req) {

        if (req.email() != null)
            return userRepository.findByEmail(req.email())
                    .filter(u -> u.getIsDeleted() == req.deleted())
                    .map(userMapper::toResponse)
                    .orElseThrow(UserNotFoundException::new);


        if (req.id() != null)
            return userRepository.findById(req.id())
                    .filter(u -> u.getIsDeleted() == req.deleted())
                    .map(userMapper::toResponse)
                    .orElseThrow(UserNotFoundException::new);

        return null;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = UserNotFoundException.class)
    public UserRes getById(UUID id, boolean includeDeleted) {
        if (id == null)
            return null;

        return userRepository.findById(id)
                .filter(u -> includeDeleted || Boolean.FALSE.equals(u.getIsDeleted()))
                .map(userMapper::toResponse)
                .orElseThrow(UserNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = UserNotFoundException.class)
    public UserRes getByEmail(String email, boolean includeDeleted) {
        if (email == null)
            return null;

        return userRepository.findByEmail(email)
                .filter(u -> includeDeleted || Boolean.FALSE.equals(u.getIsDeleted()))
                .map(userMapper::toResponse)
                .orElseThrow(UserNotFoundException::new);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<UUID, UserNameDisplayRes> getNames(List<UUID> ids) {
        Map<UUID, UserNameDisplayRes> result = new HashMap<>();

        // không có id nào thì khỏi chạm CSDL, và tránh truyền null vào mệnh đề IN
        if (ids == null || ids.isEmpty())
            return result;

        var users = userRepository.findDisplay(ids);

        for (var u : users) {
            result.put(u.id(), u);
        }

        return result;
    }

    @Override
    public UserRes updateStatus(String email, UserStatus status) {
        User user = userRepository.findByEmail(email)
                .filter(u -> !u.getIsDeleted())
                .orElseThrow(UserNotFoundException::new);

        if (user.isLocked())
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED);

        user.setStatus(status);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserRes updatePass(String email, String password) {
        User user = userRepository.findByEmail(email)
                .filter(u -> !u.getIsDeleted())
                .orElseThrow(UserNotFoundException::new);

        if (user.isLocked())
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED);

        if (passwordEncoder.matches(password, user.getPassword()))
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_SAME_AS_OLD);

        user.setPassword(passwordEncoder.encode(password));
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserRes resolveGoogleUser(String email, String googleId) {
        // đã từng đăng nhập Google
        User user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null)
            return createGoogleUser(email, googleId);

        if (user.getGoogleId() == null)
            user.setGoogleId(googleId);

        else if (!user.getGoogleId().equals(googleId))
            // nên gửi lỗi khác nhưng chưa nghĩ ra
            throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);

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
                .filter(u -> !u.getIsDeleted())
                .orElseThrow(UserNotFoundException::new);

        // kiểm tra khoá trước: tài khoản bị khoá không còn ở trạng thái ACTIVE nên nếu kiểm tra xác thực trước,
        // nó sẽ bị báo nhầm là "chưa xác thực" và nhánh báo khoá không bao giờ chạy tới
        if (user.isLocked())
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED);

        if (!user.isConfirm())
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
    }

    @Override
    @Transactional
    public void update(UUID id, UserUpdateReq req) {
        int updated = userRepository.updatePartial(
                id,
                req.password(),
                req.status(),
                req.plan(),
                req.firstName(),
                req.lastName(),
                req.avatarUrl(),
                req.isDeleted(),
                clock.instant());

        if (updated == 0)
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }


    @Override
    public PaymentCreateRes createPremiumUpgrade(UUID userId, PremiumPlan plan) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);

        if (isPremiumActive(user, clock.instant()))
            throw new BusinessException(ErrorCode.PREMIUM_ALREADY_ACTIVE);

        return paymentService.createPayment(new PaymentCreateReq(
                userId, PaymentType.PREMIUM_UPGRADE, plan.name(), premiumPlanProperties.priceOf(plan)));
    }

    @Override
    public void upgradeToPremium(UUID userId, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("months phải lớn hơn 0");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);
        Instant now = clock.instant();

        Instant base = now;
        if (isPremiumActive(user, now)) {
            // premium không hạn thì không có gì để cộng thêm
            if (user.getPlanExpiresAt() == null) {
                log.info("User {} đã premium không thời hạn, bỏ qua nâng cấp", userId);
                return;
            }
            // đã còn hạn mà vẫn nhận được tiền (hai đơn cùng được trả): cộng dồn để không mất tiền của user
            base = user.getPlanExpiresAt();
        }

        user.setPlan(UserPlan.PREMIUM);
        user.setPlanExpiresAt(base.atZone(ZoneOffset.UTC).plusMonths(months).toInstant());
        userRepository.save(user);
        log.info("User {} nâng cấp premium đến {}", userId, user.getPlanExpiresAt());
    }

    @Override
    public int downgradeExpiredPremium() {
        return userRepository.downgradeExpiredPremium(UserPlan.FREE, UserPlan.PREMIUM, clock.instant());
    }

    //-------------------------------------PRIVATE-------------------------------//

    // premium chưa có hạn (planExpiresAt null) được coi là còn hiệu lực vô thời hạn
    private boolean isPremiumActive(User user, Instant now) {
        return user.getPlan() == UserPlan.PREMIUM
                && (user.getPlanExpiresAt() == null || user.getPlanExpiresAt().isAfter(now));
    }


    private UserRes createGoogleUser(String email, String googleId) {
        return persistNewUser(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password(null)
                .googleId(googleId)
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
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
        rolePermissionCacheHelper.findRoleByName(RoleName.ROLE_USER).ifPresent(role -> {
            UserRole userRole = new UserRole(user.getId(), role.getId());
            UserRole save = userRoleRepository.save(userRole);
            user.getUserRoles().add(save);
        });
    }
}
