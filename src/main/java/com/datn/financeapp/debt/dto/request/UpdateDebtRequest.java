package com.datn.financeapp.debt.dto.request;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body {@code PATCH /debts/{id}} (api/08 mục 8). CHỈ ba trường này sửa được.
 *
 * <p>{@code principal_amount}, {@code type} và {@code wallet_id} CỐ Ý KHÔNG có mặt ở đây — ba
 * trường đó đã gắn với giao dịch gốc, sửa sẽ làm sai số dư ví. Muốn đổi thì xoá khoản nợ và tạo
 * lại. Nếu client vẫn gửi lên, Jackson bỏ qua (không cấu hình FAIL_ON_UNKNOWN_PROPERTIES) — nên
 * service kiểm tra tường minh và trả {@code PRINCIPAL_NOT_EDITABLE} thay vì im lặng bỏ qua.
 */
public record UpdateDebtRequest(
        @Size(max = 100, message = "Tên người liên quan tối đa 100 ký tự.") String counterpartyName,
        LocalDate dueDate,
        String note) {
}
