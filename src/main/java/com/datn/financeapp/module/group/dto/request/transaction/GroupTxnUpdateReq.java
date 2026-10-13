package com.datn.financeapp.module.group.dto.request.transaction;

import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * body khi cập nhật 1 giao dịch group chỉ cho cập nhật 1 số feild nhất định
 *
 * @param amount       cập nhật tiền
 * @param occurredAt   thời gian giao dịch
 * @param status       cập nhật trạng thái
 * @param categoryCode mã danh mục
 * @param transactorId người thực hiện giao dịch
 * @param note         ghi chú
 * @param participants người tham gia
 */
public record GroupTxnUpdateReq(
        @NotNull(message = "txnId là bắt buộc") UUID txnId,
        @Positive(message = "Số tiền phải lớn hơn 0.")
        @Max(value = 999999999999L, message = "Số tiền vượt quá giới hạn cho phép.")
        Long amount,
        GroupTxnStatus status,
        GroupTxnType type,
        MoneySource moneySource,
        Instant occurredAt,
        String categoryCode,
        UUID transactorId,
        @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự.") String note,
        List<@Valid GroupTxnParticipantReq> participants) {

}
