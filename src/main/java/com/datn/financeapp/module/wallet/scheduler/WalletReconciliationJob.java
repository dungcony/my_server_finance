package com.datn.financeapp.module.wallet.scheduler;

import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled Job thực hiện đối chiếu và cân bằng số dư thực tế cho toàn bộ các ví.
 * Tham chiếu phương thức thực thi chính {@link #run()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletReconciliationJob {

    private final WalletRepository repository;

    /**
     * Thực thi tác vụ đối chiếu số dư ví định kỳ lúc 01:00 sáng (Asia/Ho_Chi_Minh).
     */
    @Transactional
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            repository.reconcileAll();
            log.info("Đã chạy xong tác vụ đối chiếu số dư ví");
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ đối chiếu số dư ví", e);
        }
    }
}
