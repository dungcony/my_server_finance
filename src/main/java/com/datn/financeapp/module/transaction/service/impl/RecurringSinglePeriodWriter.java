package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.module.transaction.entity.RecurringTransaction;
import com.datn.financeapp.module.transaction.helper.TransactionWriter;

import java.time.LocalDate;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi giao dịch của một kỳ định kỳ đơn lẻ trong transaction độc lập (Propagation.REQUIRES_NEW).
 * Cung cấp các phương thức ghi như {@link #writeOnePeriod(RecurringTransaction, LocalDate)} và {@link #writeOnePeriodOrThrow(RecurringTransaction, LocalDate)}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringSinglePeriodWriter {

    private final TransactionWriter transactionWriter;

    /**
     * Ghi một kỳ giao dịch định kỳ cho tác vụ nền. Nếu kỳ đã tồn tại sẽ bỏ qua trong im lặng.
     *
     * @param rec        entity giao dịch định kỳ
     * @param periodDate ngày của kỳ giao dịch cần ghi
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeOnePeriod(RecurringTransaction rec, LocalDate periodDate) {
        try {
            writeAndFlush(rec, periodDate);
        } catch (DataIntegrityViolationException duplicate) {
            log.debug(
                    "Kỳ {} của khoản định kỳ {} đã tồn tại, bỏ qua để không ghi trùng",
                    periodDate,
                    rec.getId());
        }
    }

    /**
     * Ghi một kỳ giao dịch định kỳ cho yêu cầu chạy ngay (run-now). Nếu kỳ đã tồn tại sẽ ném ngoại lệ.
     *
     * @param rec        entity giao dịch định kỳ
     * @param periodDate ngày của kỳ giao dịch cần ghi
     * @return {@link UUID} ID của giao dịch vừa phát sinh
     * @throws DataIntegrityViolationException nếu kỳ giao dịch trùng lặp
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID writeOnePeriodOrThrow(RecurringTransaction rec, LocalDate periodDate) {
        return writeAndFlush(rec, periodDate);
    }

    /**
     * {@code flush()} tường minh là bắt buộc: nếu không, Hibernate có quyền hoãn câu INSERT tới lúc
     * commit, khi đó vi phạm {@code uq_txn_recurring_date} sẽ nổ ra NGOÀI khối {@code try} của
     * caller và không còn cơ hội xử lý tử tế. Cùng kỹ thuật đã dùng ở
     * {@code DebtService}/{@code GoalService}.
     */
    private UUID writeAndFlush(RecurringTransaction rec, LocalDate periodDate) {
        return transactionWriter
                .writeAndFlush(RecurringPeriodWriter.buildCommand(rec, periodDate))
                .transactionId();
    }
}
