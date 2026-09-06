package com.datn.financeapp.recurring.service;

import com.datn.financeapp.recurring.entity.RecurringTransaction;
import com.datn.financeapp.transaction.service.TransactionWriter;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi giao dịch của ĐÚNG MỘT kỳ, trong transaction RIÊNG của kỳ đó (D-51).
 *
 * <p>Bean thứ ba của module, tách khỏi {@link RecurringPeriodWriter} vì hai lý do bắt buộc:
 *
 * <ol>
 *   <li>{@code REQUIRES_NEW} chỉ có tác dụng khi lời gọi đi qua proxy AOP — gọi
 *       {@code this.writeOnePeriod()} trong cùng bean sẽ bỏ qua annotation trong im lặng.
 *   <li>Kỳ trùng phải rollback GỌN trong phạm vi của nó. PostgreSQL huỷ nguyên transaction khi có
 *       câu lệnh vi phạm ràng buộc ({@code current transaction is aborted}), nên nếu kỳ trùng nằm
 *       chung transaction với các kỳ khác thì mọi kỳ sau nó cũng chết theo — đúng thứ mà
 *       {@code uq_txn_recurring_date} lẽ ra chỉ nên bỏ qua một kỳ.
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringSinglePeriodWriter {

    private final TransactionWriter transactionWriter;

    /**
     * Dùng cho tác vụ nền: kỳ đã tồn tại thì BỎ QUA trong im lặng, không ném ra ngoài. Nhờ vậy chạy
     * lại tác vụ là thao tác vô hại (idempotent), kể cả khi hai tiến trình quét song song —
     * {@code uq_txn_recurring_date} là trọng tài cuối cùng chứ không phải một lệnh SELECT kiểm tra
     * trước vốn luôn có khe hở giữa lúc đọc và lúc ghi (T-04-15).
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
     * Dùng cho {@code run-now}: kỳ đã tồn tại thì NÉM {@link DataIntegrityViolationException} để
     * {@link RecurringPeriodWriter#runSinglePeriodNow} đổi thành lỗi nghiệp vụ 409 — người dùng bấm
     * nút cần biết vì sao không có gì xảy ra, khác hẳn tác vụ nền chạy âm thầm.
     *
     * @return id giao dịch vừa ghi
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
