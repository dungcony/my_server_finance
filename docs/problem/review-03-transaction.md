# Lỗi module Transaction (giao dịch cá nhân)

[← Tổng quan](review-00-tong-quan.md)

<a id="t6"></a>

## T6. Chưa chặn sửa giao dịch gốc của khoản nợ — Vừa

- **Đã sửa:** xoá mềm giao dịch gốc của khoản nợ (`debts.origin_transaction_id`) hoặc giao dịch trả nợ
  (`debt_payments`) bị trigger `trg_transactions_block_debt_delete`
  ([V9__trigger_debt.sql](../../src/main/resources/db/migration_v2/V9__trigger_debt.sql)) chặn.
- **Còn lại:**
    - Sửa `amount` hoặc `type` của giao dịch gốc không bị chặn, vì trigger chỉ chạy khi `is_deleted` đổi. Khoản nợ và
      giao dịch gốc có thể lệch số tiền.
    - Trigger báo lỗi bằng `ERRCODE = 'P0001'` với thông điệp `TRANSACTION_LINKED_TO_DEBT`, mà chuỗi này không có trong
      [TriggerProperty.java](../../src/main/java/com/datn/financeapp/common/properties/TriggerProperty.java)
      (`CONSTRAINT_MAP`). Vì vậy mã lỗi API trả về chưa rõ, có thể là 500 thay vì 409 (chưa xác minh).
- **Ghi chú:** Lỗi này liên quan module `debt` đang tạm bỏ qua, có thể để sau.
