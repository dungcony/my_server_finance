package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.transaction.entity.RecurringTransaction;
import com.datn.financeapp.module.transaction.enums.TransactionSource;
import com.datn.financeapp.module.transaction.repository.RecurringTransactionRepository;

import java.time.LocalDate;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Component hạ tầng hỗ trợ sinh các kỳ giao dịch thực tế từ khoản định kỳ.
 * Cung cấp các phương thức chính như {@link #runOneRecurring(UUID)} để xử lý định kỳ và {@link #runSinglePeriodNow(UUID)} cho chạy ngay.
 */
@Component
@RequiredArgsConstructor
public class RecurringPeriodWriter {

    /**
     * Chặn trên số kỳ xử lý trong một lần chạy để tránh vòng lặp vô hạn.
     */
    private static final int MAX_PERIODS_PER_RUN = 5000;

    private final RecurringTransactionRepository recurringRepository;
    private final RecurringSinglePeriodWriter singlePeriodWriter;

    /**
     * Chạy sinh giao dịch cho tất cả các kỳ đến hạn của một khoản định kỳ và cập nhật lịch chạy tiếp theo.
     *
     * @param recurringId ID khoản định kỳ cần chạy
     */
    @Transactional
    public void runOneRecurring(UUID recurringId) {
        RecurringTransaction rec = recurringRepository
                .findById(recurringId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy khoản định kỳ " + recurringId));

        LocalDate today = LocalDate.now();
        LocalDate cursor = rec.getNextRunDate();
        int guard = 0;

        while (!cursor.isAfter(today)
                && (rec.getEndDate() == null || !cursor.isAfter(rec.getEndDate()))
                && guard++ < MAX_PERIODS_PER_RUN) {
            // Lời gọi cross-bean: mở transaction con REQUIRES_NEW cho riêng kỳ này (D-51).
            singlePeriodWriter.writeOnePeriod(rec, cursor);
            cursor = RecurringDateCalculator.nextRunDate(
                    rec.getStartDate(), cursor, rec.getFrequency(), rec.getInterval());
        }

        rec.setNextRunDate(cursor);
        recurringRepository.save(rec);
    }

    /**
     * Kích hoạt chạy ngay một kỳ giao dịch định kỳ vào ngày hôm nay.
     *
     * @param recurringId ID khoản định kỳ
     * @return {@link UUID} ID giao dịch phát sinh vừa ghi
     * @throws BusinessException nếu kỳ hôm nay đã được chạy trước đó
     */
    @Transactional
    public UUID runSinglePeriodNow(UUID recurringId) {
        RecurringTransaction rec = recurringRepository
                .findById(recurringId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy khoản định kỳ " + recurringId));

        try {
            return singlePeriodWriter.writeOnePeriodOrThrow(rec, LocalDate.now());
        } catch (DataIntegrityViolationException duplicate) {
            // Ràng buộc CSDL là nơi phát hiện, nhưng người dùng phải nhận lỗi nghiệp vụ rõ nghĩa
            // chứ không phải 500 lộ tên constraint.
            throw new BusinessException(ErrorCode.RECURRING_ALREADY_RUN_TODAY);
        }
    }

    /**
     * Xây dựng command tạo giao dịch từ thông tin khoản định kỳ và ngày phát sinh.
     *
     * @param rec  entity giao dịch định kỳ
     * @param date ngày thực hiện giao dịch
     * @return {@link TransactionWriteCommand} command dùng cho TransactionWriter
     */
    static TransactionWriteCommand buildCommand(RecurringTransaction rec, LocalDate date) {
        return new TransactionWriteCommand(
                UUID.randomUUID(),
                rec.getUserId(),
                rec.getWalletId(),
                rec.getCategoryId(),
                rec.getType(),
                rec.getAmount(),
                date,
                rec.getNote(),
                rec.getName(),
                TransactionSource.RECURRING,
                rec.getId(),
                null);
    }
}
