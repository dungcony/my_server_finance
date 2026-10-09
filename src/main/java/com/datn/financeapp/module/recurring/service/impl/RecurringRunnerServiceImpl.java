package com.datn.financeapp.module.recurring.service.impl;

import com.datn.financeapp.module.recurring.service.RecurringRunnerService;

import com.datn.financeapp.module.recurring.entity.RecurringTransaction;
import com.datn.financeapp.module.recurring.repository.RecurringTransactionRepository;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service thực thi quét và chạy các khoản định kỳ đến hạn.
 * Triển khai giao diện {@link RecurringRunnerService}, tương tác với {@link RecurringTransactionRepository} và {@link RecurringPeriodWriter}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringRunnerServiceImpl implements RecurringRunnerService {

    private final RecurringTransactionRepository recurringRepository;
    private final RecurringPeriodWriter periodWriter;

    /**
     * {@inheritDoc}
     */
    @Override
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
