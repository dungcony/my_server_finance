package com.datn.financeapp.module.user.service;

import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.user.dto.request.UserCreateReq;
import com.datn.financeapp.module.user.dto.request.UserGetReq;
import com.datn.financeapp.module.user.dto.request.UserUpdateReq;
import com.datn.financeapp.module.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.module.user.dto.response.UserRes;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.PremiumPlan;
import com.datn.financeapp.module.user.enums.UserStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserService {

    UserRes create(UserCreateReq req);

    UserRes get(UserGetReq req);

    /**
     * lấy thông tin theo id
     *
     * @param id             id user
     * @param includeDeleted true -> lấy cả xoá và k, false -> chỉ lấy chưa xoá
     */
    UserRes getById(UUID id, boolean includeDeleted);

    /**
     * lấy thông tin theo email
     *
     * @param email          email user
     * @param includeDeleted true -> lấy cả xoá và k, false -> chỉ lấy chưa xoá
     */
    UserRes getByEmail(String email, boolean includeDeleted);

    Map<UUID, UserNameDisplayRes> getNames(List<UUID> ids);

    UserRes updateStatus(String email, UserStatus status);

    UserRes updatePass(String email, String password);

    // Đăng nhập Google: đã liên kết googleId thì trả về luôn, trùng email thì liên kết vào tài khoản đó,
    // chưa có gì thì tạo mới. Ném UserNotFoundException/UserBlockedException nếu tài khoản không đủ điều kiện.
    UserRes resolveGoogleUser(String email, String googleId);

    boolean existByEmail(String email);

    void validUser(String email);

    void update(UUID user, UserUpdateReq req);
    // Tạo đơn nâng cấp premium. Đang premium còn hạn thì ném PREMIUM_ALREADY_ACTIVE;
    // còn đơn chờ thanh toán thì huỷ link cũ và tạo đơn mới.
    PaymentCreateRes createPremiumUpgrade(UUID userId, PremiumPlan plan);

    // Được gọi khi thanh toán thành công: đặt plan PREMIUM và hạn dùng thêm số tháng đã mua.
    void upgradeToPremium(UUID userId, int months);

    // Hạ các tài khoản premium đã hết hạn về FREE, trả về số tài khoản bị hạ.
    int downgradeExpiredPremium();
}

