package com.datn.financeapp.recurring.mapper;

import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.recurring.dto.response.RecurringDetailResponse;
import com.datn.financeapp.recurring.dto.response.RecurringListItemResponse;
import com.datn.financeapp.recurring.dto.response.RunNowResponse;
import com.datn.financeapp.recurring.entity.RecurringTransaction;
import com.datn.financeapp.transaction.dto.response.GeneratedTransactionResponse;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import org.mapstruct.Mapper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface RecurringMapper {

    default RecurringListItemResponse toListItemResponse(
            RecurringTransaction rec,
            WalletRefResponse wallet,
            CategoryRefResponse category,
            long runCount) {
        if (rec == null) {
            return null;
        }

        return new RecurringListItemResponse(
                rec.getId(),
                rec.getDisplayName(),
                rec.getType(),
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
                rec.getFrequency(),
                rec.getInterval(),
                buildScheduleLabel(rec),
                rec.getStartDate(),
                rec.getEndDate(),
                rec.getNextRunDate(),
                rec.getLastRunDate(),
                rec.getNextRunDate() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), rec.getNextRunDate()),
                rec.getIsEnabled(),
                runCount,
                rec.getNote());
    }

    default RecurringDetailResponse.GeneratedTransaction toGeneratedTransaction(GeneratedTransactionResponse t) {
        if (t == null) {
            return null;
        }
        return new RecurringDetailResponse.GeneratedTransaction(
                t.id(), t.date(), t.amount(), t.type(), t.source());
    }

    default RecurringDetailResponse toDetailResponse(
            RecurringListItemResponse base,
            List<RecurringDetailResponse.GeneratedTransaction> history) {
        return new RecurringDetailResponse(base, history);
    }

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

    default String buildScheduleLabel(RecurringTransaction rec) {
        if (rec == null || rec.getFrequency() == null) {
            return null;
        }
        int interval = rec.getInterval() != null ? rec.getInterval() : 1;
        return switch (rec.getFrequency()) {
            case "day" -> interval == 1 ? "Hằng ngày" : "Mỗi " + interval + " ngày";
            case "week" -> interval == 1 ? "Hằng tuần" : "Mỗi " + interval + " tuần";
            case "month" -> (interval == 1 ? "Hằng tháng" : "Mỗi " + interval + " tháng")
                    + (rec.getStartDate() != null ? " vào ngày " + rec.getStartDate().getDayOfMonth() : "");
            case "year" -> (interval == 1 ? "Hằng năm" : "Mỗi " + interval + " năm")
                    + (rec.getStartDate() != null
                            ? " vào ngày " + rec.getStartDate().getDayOfMonth() + " tháng " + rec.getStartDate().getMonthValue()
                            : "");
            default -> rec.getFrequency();
        };
    }
}
