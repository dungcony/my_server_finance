package com.datn.financeapp.module.user.scheduler;

import com.datn.financeapp.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hạ các tài khoản premium đã hết hạn về FREE mỗi ngày.
 * Access token cũ vẫn mang plan premium đến khi hết hạn token; lần refresh kế tiếp sẽ nhận plan mới.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PremiumExpiryJob {

    private final UserService userService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            int downgraded = userService.downgradeExpiredPremium();
            log.info("Đã hạ {} tài khoản premium hết hạn về free", downgraded);
        } catch (Exception e) {
            log.error("Lỗi khi hạ tài khoản premium hết hạn", e);
        }
    }
}
