package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.category.dto.response.CategoryResponse;
import com.datn.financeapp.module.transaction.dto.response.RecurringDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.RecurringListItemResponse;
import com.datn.financeapp.module.transaction.dto.response.RunNowResponse;
import com.datn.financeapp.module.transaction.dto.response.TransactionDetailResponse;
import com.datn.financeapp.module.transaction.entity.RecurringTransaction;
import com.datn.financeapp.module.wallet.dto.response.WalletRefResponse;
import org.mapstruct.Mapper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Mapper chuyển đổi giữa entity {@link RecurringTransaction} và các DTO response liên quan.
 * Cung cấp các phương thức ánh xạ chính như {@link #toListItemResponse(RecurringTransaction, WalletRefResponse, CategoryResponse, long)}
 * và {@link #toDetailResponse(RecurringListItemResponse, List)}.
 */
@Mapper(componentModel = "spring")
public interface RecurringMapper {

    /**
     * Chuyển đổi entity {@link RecurringTransaction} sang DTO dạng item danh sách.
     *
     * @param rec      entity giao dịch định kỳ
     * @param wallet   thông tin ví tham chiếu
     * @param category thông tin danh mục tham chiếu
     * @param runCount số lần đã sinh giao dịch
     * @return DTO {@link RecurringListItemResponse} chứa thông tin giao dịch định kỳ
     */
    default RecurringListItemResponse toListItemResponse(
            RecurringTransaction rec,
            WalletRefResponse wallet,
            CategoryResponse category,
            long runCount) {
        if (rec == null) {
            return null;
        }

        return new RecurringListItemResponse(
                rec.getId(),
                rec.getName(),
                rec.getType().name().toLowerCase(),
                rec.getAmount(),
                wallet == null ? null : new RecurringListItemResponse.WalletRef(wallet.id(), wallet.name()),
                category == null
                        ? null
                        : new RecurringListItemResponse.CategoryRef(
                        category.id(),
                        category.name(),
                        category.icon() == null
                                ? null
                                : new RecurringListItemResponse.IconRef(
                                category.icon().code(), category.icon().pathData()),
                        category.color()),
                rec.getFrequency().name().toLowerCase(),
                rec.getInterval(),
                buildScheduleLabel(rec),
                rec.getStartDate(),
                rec.getEndDate(),
                rec.getNextRunDate(),
                rec.getNextRunDate() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), rec.getNextRunDate()),
                rec.getIsEnabled(),
                runCount,
                rec.getNote());
    }

    /**
     * Chuyển đổi thông tin giao dịch phát sinh sang dạng tóm tắt trong lịch sử.
     *
     * @param t chi tiết giao dịch đã được sinh ra từ khoản định kỳ
     * @return DTO {@link RecurringDetailResponse.GeneratedTransaction} tóm tắt
     */
    default RecurringDetailResponse.GeneratedTransaction toGeneratedTransaction(TransactionDetailResponse t) {
        if (t == null) {
            return null;
        }
        return new RecurringDetailResponse.GeneratedTransaction(
                t.id(), t.date(), t.amount(), t.type(), t.source());
    }

    /**
     * Ghép nối DTO cơ bản và lịch sử giao dịch phát sinh thành DTO chi tiết đầy đủ.
     *
     * @param base    DTO dạng item danh sách cơ bản
     * @param history danh sách lịch sử giao dịch đã phát sinh
     * @return DTO {@link RecurringDetailResponse} chi tiết
     */
    default RecurringDetailResponse toDetailResponse(
            RecurringListItemResponse base,
            List<RecurringDetailResponse.GeneratedTransaction> history) {
        return new RecurringDetailResponse(base, history);
    }

    /**
     * Chuyển đổi dữ liệu sau khi chạy ngay giao dịch định kỳ sang DTO phản hồi.
     *
     * @param transactionId ID giao dịch mới phát sinh
     * @param type          loại giao dịch
     * @param amount        số tiền
     * @param date          ngày ghi nhận
     * @param walletId      ID ví
     * @param balance       số dư mới của ví
     * @return DTO {@link RunNowResponse}
     */
    default RunNowResponse toRunNowResponse(
            UUID transactionId,
            String type,
            Long amount,
            LocalDate date,
            UUID walletId,
            Long balance) {
        return new RunNowResponse(
                new RunNowResponse.TransactionSummary(transactionId, type, amount, date),
                new RunNowResponse.NewBalance(walletId, balance));
    }

    /**
     * Xây dựng câu mô tả lịch lặp dạng ngôn ngữ tự nhiên.
     *
     * @param rec entity giao dịch định kỳ
     * @return chuỗi mô tả lịch lặp (ví dụ: "Hằng tháng vào ngày 1")
     */
    default String buildScheduleLabel(RecurringTransaction rec) {
        if (rec == null || rec.getFrequency() == null) {
            return null;
        }
        int interval = rec.getInterval() != null ? rec.getInterval() : 1;
        return switch (rec.getFrequency()) {
            case WEEK -> interval == 1 ? "Hằng tuần" : "Mỗi " + interval + " tuần";
            case MONTH -> (interval == 1 ? "Hằng tháng" : "Mỗi " + interval + " tháng")
                    + (rec.getStartDate() != null ? " vào ngày " + rec.getStartDate().getDayOfMonth() : "");
            case QUARTER -> (interval == 1 ? "Hằng quý" : "Mỗi " + interval + " quý")
                    + (rec.getStartDate() != null ? " vào ngày " + rec.getStartDate().getDayOfMonth() : "");
            case YEAR -> (interval == 1 ? "Hằng năm" : "Mỗi " + interval + " năm")
                    + (rec.getStartDate() != null
                    ? " vào ngày " + rec.getStartDate().getDayOfMonth() + " tháng " + rec.getStartDate().getMonthValue()
                    : "");
        };
    }
}
