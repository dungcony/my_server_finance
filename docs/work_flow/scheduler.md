# Workflow — Scheduler (tác vụ chạy định kỳ)

| Job | Lịch chạy | Code |
|---|---|---|
| Sinh giao dịch định kỳ | Mỗi giờ, phút 0 (đổi được qua `app.recurring.cron`) | [RecTxnScheduler](../../src/main/java/com/datn/financeapp/module/transaction/scheduler/RecTxnScheduler.java) |
| Đối chiếu số dư ví | 01:00 mỗi ngày, giờ VN | [WalletReconciliationJob](../../src/main/java/com/datn/financeapp/module/wallet/scheduler/WalletReconciliationJob.java) |
| Dọn khoá chống ghi trùng | 03:00 mỗi ngày, giờ VN | [IdempotencyCleanupJob](../../src/main/java/com/datn/financeapp/common/idempotency/IdempotencyCleanupJob.java) |
| Dọn đơn thanh toán quá hạn | Mỗi 5 phút sau lần chạy trước (`fixedDelay`) | [PaymentExpiryJob](../../src/main/java/com/datn/financeapp/module/pay/scheduler/PaymentExpiryJob.java) |
| Hạ premium hết hạn về free | 00:05 mỗi ngày, giờ VN | [PremiumExpiryJob](../../src/main/java/com/datn/financeapp/module/user/scheduler/PremiumExpiryJob.java) |
| Dọn nhật ký webhook payOS | 03:30 mỗi ngày, giờ VN | [PaymentWebhookLogCleanupJob](../../src/main/java/com/datn/financeapp/module/pay/scheduler/PaymentWebhookLogCleanupJob.java) |

## Sinh giao dịch định kỳ — mỗi giờ

```
RecTxnScheduler.executeDueSchedules
-> RecTxnScheduleImpl.processDueSchedules
   -> lấy các buổi lịch PENDING, chưa xoá, run_at <= bây giờ
   -> với từng buổi:
        định kỳ đã tắt hoặc đã xoá → bỏ qua
        -> tạo giao dịch thật (occurred_at = run_at, số tiền của buổi)
           -> [trigger trg_transactions_wallet_balance] cộng trừ số dư ví
        -> buổi → SUCCESS
        -> định kỳ vô hạn → thêm một buổi PENDING mới
           định kỳ có ngày kết thúc và hết buổi PENDING → tắt định kỳ
```

Chi tiết từng bước và các lỗi đang mở xem [recurring.md](recurring.md).

## Đối chiếu số dư ví — 01:00

Bình thường số dư ví đã được trigger `trg_transactions_wallet_balance` cập nhật theo từng giao dịch. Job này chỉ là **lưới an toàn**, sửa những ví bị lệch vì lý do khác (sửa tay trong DB, lỗi cũ...).

```
WalletReconciliationJob.run
-> WalletServiceImpl.reconcileAll (một transaction)
   -> WalletRepository.reconcileAll: MỘT câu SQL cho mọi ví chưa xoá
        tính tổng giao dịch chưa xoá của từng ví (thu +, chi và cho vay −)
        -> ví nào balance khác tổng → sửa balance = tổng
        -> mỗi ví vừa sửa ghi một dòng reconcil_history (số dư cũ, số dư đúng)
   -> log số ví đã sửa
```

Xem lịch sử: `GET /wallets/{id}/reconciliations` (chủ ví), `GET /wallets/admin/reconciliations` (admin). Xem [wallet.md](wallet.md).

## Dọn khoá chống ghi trùng — 03:00

Các API có `@Idempotent` (tạo ví, tạo giao dịch...) lưu khoá chống ghi trùng vào bảng `idempotency_keys`.

```
IdempotencyCleanupJob.cleanup
-> xoá các dòng idempotency_keys tạo cách đây hơn 24 giờ
-> log số dòng đã xoá
```

## Dọn đơn thanh toán quá hạn — mỗi 5 phút

```
PaymentExpiryJob.expireOverduePayments
-> PaymentService.findOverdueOrderCodes: đơn PENDING có expires_at < bây giờ, tối đa 100 đơn mỗi lượt
-> với từng đơn (mỗi đơn một transaction, đơn lỗi chỉ ghi log và không chặn các đơn sau):
     PaymentService.expireIfOverdue
     -> khoá đơn; đã được webhook xử lý trong lúc chờ khoá, hoặc chưa tới hạn → bỏ qua
     -> hỏi payOS: đã nhận đủ tiền → ghi PAID và giao hàng
                   chưa nhận đồng nào → huỷ link payOS rồi ghi EXPIRED
                   đã nhận một phần, hoặc huỷ không hiệu lực → log ERROR, giữ PENDING (lượt sau thử lại)
```

Chi tiết xem [payment.md](payment.md).

## Hạ premium hết hạn — 00:05

```
PremiumExpiryJob.run
-> UserService.downgradeExpiredPremium: MỘT câu UPDATE
     users.plan PREMIUM → FREE, với user có plan_expires_at < bây giờ
     (premium không có plan_expires_at, VD tài khoản admin seed, giữ nguyên)
-> log số tài khoản đã hạ
```

Access token cũ vẫn mang `plan = PREMIUM` tới khi hết hạn token; lần refresh sau nhận `FREE`.

## Dọn nhật ký webhook payOS — 03:30

```
PaymentWebhookLogCleanupJob.cleanup
-> xoá các dòng payment_webhook_logs có received_at cũ hơn PAYMENT_WEBHOOK_LOG_RETENTION_DAYS ngày (mặc định 30)
-> log số dòng đã xoá; lỗi DB chỉ ghi log, không làm hỏng scheduler
```
