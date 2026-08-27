package com.datn.financeapp.transaction.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Sự kiện phát ra SAU KHI {@code TransactionWriter.write()} ghi xong một giao dịch (D-41).
 *
 * <p>{@code TransactionWriter} chỉ bắn sự kiện và KHÔNG biết ai lắng nghe — nhờ vậy module
 * {@code budget/} (và các module phái sinh sau này) phụ thuộc một chiều vào {@code transaction/},
 * không tạo phụ thuộc vòng tròn. Người nghe đăng ký bằng
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} nên tác vụ phụ trợ chạy NGOÀI
 * transaction ghi giao dịch: không làm chậm luồng ghi, và listener lỗi không rollback giao dịch
 * đã commit.
 *
 * <p>Record bất biến chứa đúng các trường nguyên thuỷ người nghe cần — không mang theo entity
 * JPA (tránh truy cập entity đã detach sau khi transaction đóng).
 */
public record TransactionRecordedEvent(
        UUID transactionId,
        UUID userId,
        UUID walletId,
        UUID categoryId,
        String type,
        long amount,
        LocalDate date) {
}
