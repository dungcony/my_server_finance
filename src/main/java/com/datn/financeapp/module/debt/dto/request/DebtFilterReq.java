package com.datn.financeapp.module.debt.dto.request;

import com.datn.financeapp.module.debt.enums.DebtStatus;
import com.datn.financeapp.module.debt.enums.DebtType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO nhận tiêu chí lọc danh sách khoản nợ (Sổ nợ).
 *
 * @param name           Từ khóa tìm kiếm theo tên khoản nợ / đối tác (tùy chọn)
 * @param type           Loại khoản nợ ({@link DebtType}; LENDING, BORROWING; tùy chọn)
 * @param status         Trạng thái khoản nợ ({@link DebtStatus}; INPROCESS, COMPLETED; tùy chọn)
 * @param walletId       ID ví tiền liên kết (tùy chọn)
 * @param issuedDateFrom Ngày tạo nợ từ ngày (tùy chọn)
 * @param issuedDateTo   Ngày tạo nợ đến ngày (tùy chọn)
 * @param dueDateFrom    Hạn trả nợ từ ngày (tùy chọn)
 * @param dueDateTo      Hạn trả nợ đến ngày (tùy chọn)
 * @param isOverdue      Lọc các khoản nợ đã quá hạn chưa tất toán (tùy chọn)
 * @param minAmount      Số tiền gốc tối thiểu (tùy chọn)
 * @param maxAmount      Số tiền gốc tối đa (tùy chọn)
 */
public record DebtFilterReq(
        String name,
        DebtType type,
        DebtStatus status,
        UUID walletId,
        LocalDate issuedDateFrom,
        LocalDate issuedDateTo,
        LocalDate dueDateFrom,
        LocalDate dueDateTo,
        Boolean isOverdue,
        Long minAmount,
        Long maxAmount
) {
}
