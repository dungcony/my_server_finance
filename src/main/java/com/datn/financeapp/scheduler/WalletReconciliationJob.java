package com.datn.financeapp.scheduler;

import com.datn.financeapp.wallet.service.WalletTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * JOB-01 (D-57, api/02-VI.md mục 10). Đối chiếu số dư TOÀN BỘ ví hằng ngày, tự sửa lệch. Chạy 1h
 * sáng giờ Việt Nam. Bọc try/catch để một lần lỗi không làm crash scheduler pool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletReconciliationJob {

    private final WalletTransferService walletTransferService;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            walletTransferService.reconcileAllWallets();
            log.info("Đã chạy xong tác vụ đối chiếu số dư ví");
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ đối chiếu số dư ví", e);
        }
    }
}
