package com.datn.financeapp.recurring.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.recurring.entity.RecurringTransaction;
import com.datn.financeapp.recurring.repository.RecurringTransactionRepository;
import com.datn.financeapp.recurring.util.RecurringDateCalculator;
import com.datn.financeapp.transaction.service.TransactionWriteCommand;
import com.datn.financeapp.transaction.service.TransactionWriter;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean hạ tầng sinh giao dịch cho một khoản định kỳ. {@code @Component} (không phải
 * {@code @Service}) vì cùng loại với {@link TransactionWriter} — chỉ lo "ghi cho đúng", không chứa
 * nghiệp vụ request-response.
 *
 * <p><b>Vì sao phải là bean RIÊNG, tách khỏi {@link RecurringRunnerService}.</b> Spring cài
 * {@code @Transactional} bằng proxy AOP: gọi {@code this.method()} trong cùng một bean đi thẳng
 * vào đối tượng thật, KHÔNG qua proxy, nên annotation mất tác dụng trong im lặng. Tách hẳn sang
 * bean khác là cách duy nhất khiến lời gọi từ vòng lặp quét thực sự mở transaction mới. Cùng khuôn
 * với {@code TransactionBulkService} → {@code TransactionWriter} (D-34/D-35a).
 *
 * <p><b>Ranh giới transaction — chỗ dễ hiểu sai nhất của D-51/D-52.</b> Ba mức lồng nhau:
 *
 * <ul>
 *   <li>Giữa các KHOẢN khác nhau: độc lập hoàn toàn. {@link RecurringRunnerService} bọc try/catch
 *       quanh từng khoản nên khoản hỏng không kéo đổ khoản lành (D-52).
 *   <li>Giữa các KỲ của cùng một khoản: mỗi kỳ một transaction RIÊNG qua
 *       {@link RecurringSinglePeriodWriter} ({@code REQUIRES_NEW}). Kỳ 5 lỗi thì 4 kỳ trước vẫn
 *       giữ nguyên, tác vụ ngày mai bắt tiếp từ kỳ 5 (D-51).
 *   <li>Bản thân việc đẩy {@code next_run_date}: nằm trong transaction của
 *       {@link #runOneRecurring}, commit sau cùng khi mọi kỳ đã xong.
 * </ul>
 *
 * <p><b>Vì sao BẮT BUỘC phải tách transaction theo từng kỳ, không thể chỉ try/catch trong một
 * transaction chung.</b> PostgreSQL huỷ nguyên transaction ngay khi có một câu lệnh vi phạm ràng
 * buộc: mọi câu sau đó đều bị từ chối với {@code current transaction is aborted}. Nghĩa là bắt
 * {@link DataIntegrityViolationException} rồi "đi tiếp" trong CÙNG transaction là ảo tưởng — kỳ kế
 * tiếp chắc chắn cũng hỏng. Chỉ khi kỳ trùng nằm trong transaction con đã rollback GỌN thì
 * transaction cha mới còn dùng được.
 */
@Component
@RequiredArgsConstructor
public class RecurringPeriodWriter {

    /**
     * Chặn trên số kỳ xử lý trong một lần chạy. Bảo hiểm chống vòng lặp vô hạn nếu dữ liệu lỗi (ví
     * dụ {@code interval} bị sửa tay thành 0 lách được CHECK), đủ rộng để bắt kịp cả chục năm lịch
     * ngày.
     */
    private static final int MAX_PERIODS_PER_RUN = 5000;

    private final RecurringTransactionRepository recurringRepository;
    private final RecurringSinglePeriodWriter singlePeriodWriter;

    /**
     * Sinh đủ giao dịch cho mọi kỳ đã tới hạn của một khoản, rồi đẩy lịch tới kỳ kế tiếp.
     *
     * <p><b>D-50 — mỗi giao dịch mang ĐÚNG ngày đáng lẽ phải chạy</b> ({@code cursor}), không dồn
     * vào hôm nay. Hai lý do: báo cáo theo tháng phản ánh đúng thực tế thay vì vọt bất thường ở một
     * tháng, và {@code uq_txn_recurring_date} là UNIQUE trên {@code (recurring_id, date)} — dồn
     * mọi kỳ vào một ngày thì ràng buộc này sẽ nuốt mất tất cả trừ kỳ đầu tiên.
     *
     * <p>Ghi được ngày quá khứ vì D-36 đã bỏ luật chặn ngày; số dư ví cộng trừ ngay theo CLAUDE.md
     * quy tắc 5.
     */
    @Transactional
    public void runOneRecurring(UUID recurringId) {
        RecurringTransaction rec = recurringRepository
                .findById(recurringId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy khoản định kỳ " + recurringId));

        LocalDate today = LocalDate.now();
        LocalDate cursor = rec.getNextRunDate();
        LocalDate lastGenerated = null;
        int guard = 0;

        while (!cursor.isAfter(today)
                && (rec.getEndDate() == null || !cursor.isAfter(rec.getEndDate()))
                && guard++ < MAX_PERIODS_PER_RUN) {
            // Lời gọi cross-bean: mở transaction con REQUIRES_NEW cho riêng kỳ này (D-51).
            singlePeriodWriter.writeOnePeriod(rec, cursor);
            lastGenerated = cursor;
            cursor = RecurringDateCalculator.nextRunDate(
                    rec.getStartDate(), cursor, rec.getFrequency(), rec.getInterval());
        }

        rec.setNextRunDate(cursor);
        if (lastGenerated != null) {
            // Ghi kỳ CUỐI CÙNG thực sự xử lý, không phải "hôm nay": đây là mốc để đối chiếu khi có
            // nghi vấn tác vụ chạy sót.
            rec.setLastRunDate(lastGenerated);
        }
        recurringRepository.save(rec);
    }

    /**
     * {@code POST /recurring/{id}/run-now} (api/09 mục A3) — ghi MỘT giao dịch ngày hôm nay và
     * KHÔNG đụng tới {@code next_run_date}/{@code last_run_date}: lần chạy tự động vẫn diễn ra đúng
     * lịch cũ. Đây là khác biệt duy nhất nhưng cốt lõi so với {@link #runOneRecurring}.
     *
     * <p>Không kiểm tra {@code is_enabled}: run-now là hành động chủ động của người dùng (trả tiền
     * nhà sớm), khác hẳn tác vụ nền vốn phải tôn trọng trạng thái tạm dừng.
     *
     * <p>Ở đây transaction bao trọn một lời ghi duy nhất nên bắt {@link DataIntegrityViolationException}
     * tại chỗ là an toàn: không có câu lệnh nào chạy sau nó trong cùng transaction.
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
            throw new BusinessException(ErrorCode.ALREADY_RUN_TODAY);
        }
    }

    /**
     * Khoản định kỳ chỉ có {@code expense}/{@code income} (ck_rec_type) nên
     * {@code destinationWalletId} luôn null và {@code categoryId} luôn có — vừa khớp
     * {@code ck_txn_shape} của bảng {@code transactions}.
     *
     * <p>{@code source = "auto"} đánh dấu giao dịch do máy sinh; {@code recurringId} là thứ khiến
     * {@code uq_txn_recurring_date} có hiệu lực.
     */
    static TransactionWriteCommand buildCommand(RecurringTransaction rec, LocalDate date) {
        return new TransactionWriteCommand(
                UUID.randomUUID(),
                rec.getUserId(),
                rec.getWalletId(),
                null,
                rec.getCategoryId(),
                rec.getType(),
                rec.getAmount(),
                date,
                rec.getNote(),
                rec.getDisplayName(),
                "auto",
                true,
                rec.getId(),
                null,
                null);
    }
}
