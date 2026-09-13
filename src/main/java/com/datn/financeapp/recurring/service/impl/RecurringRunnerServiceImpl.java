package com.datn.financeapp.recurring.service.impl;

import com.datn.financeapp.recurring.service.RecurringRunnerService;

import com.datn.financeapp.recurring.entity.RecurringTransaction;
import com.datn.financeapp.recurring.repository.RecurringTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tác vụ quét khoản định kỳ tới hạn (RECUR-03, api/09 mục A4).
 *
 * <p><b>Method {@link #runDueRecurring()} CỐ Ý KHÔNG mang {@code @Transactional}.</b> Nếu bọc
 * transaction ở đây, mọi khoản định kỳ sẽ nằm chung một transaction khổng lồ và một khoản hỏng sẽ
 * cuốn đổ toàn bộ những khoản đã ghi thành công trước đó — phá thẳng D-52. Không có annotation thì
 * mỗi lời gọi {@code periodWriter.runOneRecurring(...)} tự mở transaction riêng qua proxy AOP, y
 * hệt cách {@code TransactionBulkService} gọi {@code TransactionWriter} (D-34/D-35a).
 *
 * <p>{@code @Scheduled} KHÔNG đặt ở đây — Plan 07 gom mọi tác vụ nền vào package
 * {@code scheduler/} theo D-57. Lớp này chỉ cung cấp phần nghiệp vụ để job đó gọi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringRunnerServiceImpl implements RecurringRunnerService {

    private final RecurringTransactionRepository recurringRepository;
    private final RecurringPeriodWriter periodWriter;

    /**
     * Quét mọi khoản đang bật đã tới hạn và sinh đủ giao dịch cho từng kỳ bị bỏ lỡ.
     *
     * <p><b>D-52 — một khoản hỏng không được làm chết cả tác vụ.</b> Ví bị xoá, danh mục bị xoá,
     * hay bất kỳ lỗi hệ thống nào cũng chỉ ghi log rồi đi tiếp khoản khác.
     *
     * <p><b>Và KHÔNG tự tắt {@code is_enabled} của khoản hỏng.</b> Tự tắt là im lặng bỏ cuộc: tiền
     * nhà ngừng ghi mà người dùng không hề biết, tới lúc phát hiện thì sổ đã sai vài tháng. Cứ để
     * bật, mai tác vụ thử lại — lỗi nằm ở log cho người vận hành xử lý.
     */
    public void runDueRecurring() {
        List<RecurringTransaction> due = recurringRepository.findDue(LocalDate.now());
        log.debug("Có {} khoản định kỳ tới hạn", due.size());

        for (RecurringTransaction rec : due) {
            try {
                // Truyền ID chứ không truyền entity đã load: bean con tự nạp lại trong transaction
                // của chính nó, tránh dùng instance thuộc persistence context đã đóng.
                periodWriter.runOneRecurring(rec.getId());
            } catch (Exception e) {
                log.error(
                        "Lỗi khi sinh giao dịch định kỳ {} của người dùng {}",
                        rec.getId(),
                        rec.getUserId(),
                        e);
            }
        }
    }
}
