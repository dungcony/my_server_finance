---
phase: 06-n-i-api-th-t-v-i-app-flutter
plan: 03
subsystem: app
tags: [flutter, integration-test, wallet-transfer, transaction-edit, idempotency]

# Dependency graph
requires: ["06-01", "06-02"]
provides:
  - "integration_test/real_backend/transfer_excluded_from_report_test.dart kiểm chứng SC2b (transfer không vào báo cáo)"
  - "integration_test/real_backend/edit_transaction_3step_test.dart kiểm chứng SC2c (hoàn tác 3 bước khi sửa giao dịch)"
  - "integration_test/real_backend/idempotency_test.dart kiểm chứng SC4 (2 POST cùng Idempotency-Key chỉ sinh 1 giao dịch)"
affects: [06-04, 06-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Cả 3 test tự tạo ví riêng (POST /wallets, tên hậu tố timestamp) thay vì dùng ví seed dùng chung — cô lập khỏi 06-04 chạy song song cùng wave 3"
    - "Test idempotency dùng thẳng tham số idempotencyKey có sẵn của ApiClient.post, không cần dựng Dio trần"

key-files:
  created:
    - source/app/integration_test/real_backend/transfer_excluded_from_report_test.dart
    - source/app/integration_test/real_backend/edit_transaction_3step_test.dart
    - source/app/integration_test/real_backend/idempotency_test.dart

key-decisions:
  - "Sửa field đọc tổng chi tháng từ GET /reports/home: đối chiếu api/06-BAO-CAO.md xác nhận đúng field là period_summary.total_expense (KHÔNG phải total_expense phẳng hay summary.total_expense như bản nháp PLAN đưa ra) — dùng field đúng trong test thay vì suy đoán."
  - "Giữ nguyên toàn bộ cấu trúc test khác của PLAN (không có deviation nghiệp vụ) — mọi field JSON khác (source_wallet_id, destination_wallet_id, wallet_id, category_id, parent_category_id, transaction.id, current_balance) đã đối chiếu khớp api/02-VI.md và api/04-GIAO-DICH.md."

requirements-completed: []

# Metrics
duration: ~15min
completed: 2026-08-29
---

# Phase 6 Plan 3: Integration test nhóm ghi/sửa trên backend thật (SC2b, SC2c, SC4) Summary

**3 `integration_test/real_backend/*.dart` mới gọi trực tiếp `ApiClient` (không qua UI), tự tạo ví riêng cô lập khỏi seed dùng chung, kiểm chứng ba quy tắc nghiệp vụ bất biến khó phát hiện nhất: transfer không vào báo cáo (SC2b), sửa giao dịch hoàn tác đúng 3 bước không cộng dồn chênh lệch (SC2c), và idempotency 2 POST cùng key chỉ sinh 1 bản ghi (SC4).**

## Performance

- **Duration:** ~15 phút
- **Started:** 2026-08-29T07:05:00Z (tiếp theo 06-02)
- **Completed:** 2026-08-29T07:20:15Z
- **Tasks:** 3
- **Files modified:** 3 (tất cả mới, ở repo `source/app`)

## Accomplishments

- `transfer_excluded_from_report_test.dart`: đăng nhập bằng `seed.user@datn.local`, tự tạo 2 ví riêng (`Test SC2b A/B <timestamp>`), đọc `period_summary.total_expense` từ `GET /reports/home` trước/sau khi `POST /wallets/transfer` 500.000đ giữa 2 ví — assert bằng nhau, xác nhận `type=transfer` bị loại khỏi mọi báo cáo thu-chi (CLAUDE.md quy tắc bất biến #2).
- `edit_transaction_3step_test.dart`: tự tạo 2 ví riêng, ghi chi 100.000đ ở ví A, sửa (`PUT /transactions/{id}`) thành chi 80.000đ ở ví B — kiểm cả hai vế: ví A hoàn đủ 100.000đ (`balanceAAfter == balanceABefore`), ví B chỉ trừ đúng 80.000đ (`balanceBAfter == balanceBBefore - 80000`, không phải trừ thêm 20.000đ chênh lệch — sai kinh điển của CLAUDE.md quy tắc bất biến #4).
- `idempotency_test.dart`: gửi 2 lần `POST /transactions` với `Idempotency-Key` cố định `integration-test-fixed-key-001` qua tham số `idempotencyKey` có sẵn của `ApiClient.post` — assert cùng `transaction.id`, và đối chiếu bằng cách lọc `GET /transactions?from_date=<hôm nay>` chỉ thấy đúng 1 bản ghi khớp `id` đó.
- Sửa một sai lệch nhỏ trong bản nháp `<action>` của PLAN: field chứa "tổng chi tháng" ở `GET /reports/home` là `period_summary.total_expense`, không phải `total_expense`/`summary.total_expense` — đối chiếu `api/06-BAO-CAO.md` mục 1 trước khi hoàn tất, dùng đúng field thật thay vì cả hai phương án đoán trong PLAN.
- `flutter analyze integration_test/real_backend/` sạch, không lỗi biên dịch cho cả 3 file mới lẫn 2 file cũ của 06-02.

## Task Commits

Tất cả commit ở repo `source/app` (nhánh `develop`):

1. **Task 1: integration_test chuyển tiền không vào báo cáo (SC2b)** - `95f249a` (test)
2. **Task 2: integration_test sửa giao dịch hoàn tác 3 bước (SC2c)** - `68a28a3` (test)
3. **Task 3: integration_test idempotency 2 POST cùng key (SC4, D-13a)** - `7e390fc` (test)

**Plan metadata:** (commit này ở repo `source/server`, sau khi tạo SUMMARY)

## Files Created/Modified

- `source/app/integration_test/real_backend/transfer_excluded_from_report_test.dart` (mới) - test SC2b, gọi `ApiClient` trực tiếp, tự tạo ví riêng
- `source/app/integration_test/real_backend/edit_transaction_3step_test.dart` (mới) - test SC2c, gọi `ApiClient` trực tiếp, tự tạo ví riêng
- `source/app/integration_test/real_backend/idempotency_test.dart` (mới) - test SC4, dùng tham số `idempotencyKey` có sẵn của `ApiClient.post`

## Decisions Made

- Dùng đúng field `period_summary.total_expense` (đối chiếu `api/06-BAO-CAO.md` §1 trước khi hoàn tất task 1) thay vì để nguyên biểu thức phỏng đoán `homeBefore['total_expense'] ?? homeBefore['summary']?['total_expense']` mà bản nháp PLAN đưa ra — đây không phải deviation nghiệp vụ, chỉ là làm rõ field thật theo đúng hướng dẫn `<action>` của Task 1 ("Đối chiếu tên trường thật... trước khi hoàn tất").
- Cả 3 test tự tạo ví riêng qua `POST /wallets` với tên hậu tố `DateTime.now().microsecondsSinceEpoch` — tuân đúng lưu ý cô lập của PLAN để an toàn khi 06-04 (cùng wave 3) chạy song song và cũng thao tác `/wallets`.
- Test idempotency dùng tham số `idempotencyKey` có sẵn của `ApiClient.post()` (`api_client.dart` dòng 90-103), không dựng `Dio` trần — giải quyết đúng Open Question A4 mà RESEARCH.md/PATTERNS.md đã xác nhận có sẵn.

## Deviations from Plan

None — kế hoạch thực thi đúng như viết. Việc chốt field `period_summary.total_expense` là làm đúng hướng dẫn rõ ràng của `<action>` Task 1 ("Đối chiếu tên trường thật... sửa cho khớp shape thật của response"), không phải một sai lệch cần Rule 1-4.

## Known Stubs

Không có — cả 3 file test đều gọi API thật, không có dữ liệu giả/hardcode nào chảy vào UI.

## Issues Encountered — KHÔNG chạy được thật trên backend/emulator

**Đúng như blocker đã biết trước khi bắt đầu plan này:** môi trường thực thi phiên này **không có Android emulator kết nối** (`flutter devices` chỉ thấy Windows desktop, Chrome, Edge) và **backend không đang chạy** (`curl http://localhost:8080/v1/auth/me` không có phản hồi HTTP thật — cổng không mở). Do đó **cả 3 `integration_test/real_backend/*.dart` mới CHƯA được chạy thật** trên máy ảo Pixel 7 với backend thật trong phiên này.

**Đã kiểm chứng đầy đủ bằng các cách khác:**
- `flutter analyze integration_test/real_backend/` sạch — không lỗi biên dịch, không lỗi kiểu.
- Đối chiếu kỹ từng trường JSON dùng trong cả 3 test với tài liệu nguồn sự thật:
  - `api/02-VI.md` mục 4 (Tạo ví: `name`/`type`/`initial_balance`, response phẳng không wrapper), mục "Chuyển tiền" (`source_wallet_id`/`destination_wallet_id`), mục "Số dư ví" (`current_balance` là tiền thật đến hết hôm nay — đúng ý nghĩa test task 2 cần).
  - `api/04-GIAO-DICH.md` mục "Tạo giao dịch" (response `{transaction, new_balance, affected_budgets}`), mục "Sửa giao dịch" (`PUT /transactions/{id}` nhận cùng bộ trường, ba bước hoàn tác-ghi mới-áp dụng mới), tham số `from_date`.
  - `api/03-DANH-MUC.md` (`parent_category_id` là trường phân biệt cha/con, đúng như 2 test dùng để tìm danh mục cha).
  - `api/06-BAO-CAO.md` mục 1 (`GET /reports/home` → `period_summary.total_expense` — đã sửa field đúng, xem "Decisions Made").
- Xác nhận `DevDataSeeder` (06-01) tạo đúng user `seed.user@datn.local`/`SeedPass123!` dùng trong cả 3 test.

**Việc còn lại trước khi coi Task 1/2/3 kiểm chứng đầy đủ:** chạy trên máy có Android emulator (Pixel 7 API 36) + backend `mvn spring-boot:run` profile `dev` đang chạy:

```bash
cd source/app
flutter test integration_test/real_backend/transfer_excluded_from_report_test.dart --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 -d emulator-5554
flutter test integration_test/real_backend/edit_transaction_3step_test.dart --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 -d emulator-5554
flutter test integration_test/real_backend/idempotency_test.dart --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 -d emulator-5554
```

Đồng thời D-14 khuyến nghị thêm một lần kiểm bằng `psql` sau bước 3 của `idempotency_test.dart`:
```sql
SELECT COUNT(*) FROM transactions WHERE amount = 15000 AND is_deleted = false;
-- kỳ vọng = 1
```

**Không khẳng định 3 test này PASS — chỉ khẳng định chúng biên dịch sạch và đối chiếu đúng hợp đồng API tài liệu.**

## User Setup Required

Cần chạy lại bộ kiểm chứng ở mục "Issues Encountered" trên máy có Android emulator + backend thật (kèm bước `psql` D-14) trước khi coi SC2b/SC2c/SC4 của Phase 6 đạt đầy đủ. Không cần cấu hình dịch vụ ngoài nào khác.

## Next Phase Readiness

- 5/6 `integration_test/real_backend/*.dart` của D-12 đã có (SC1, SC2a, SC2b, SC2c, SC4) — còn thiếu test cho nhóm `PUT`/`DELETE` khác (nếu 06-04 phủ) và báo cáo/ngân sách còn lại.
- **Cần chạy thật cả 5 test này** trên môi trường có Android emulator + backend `dev` trước khi đóng Phase 6 (xem "Issues Encountered").
- 06-04 (cùng wave 3) an toàn chạy song song vì cả 3 test của 06-03 tự tạo ví riêng, không đụng `wallets[0]`/`[1]` seed dùng chung.

---
*Phase: 06-n-i-api-th-t-v-i-app-flutter*
*Completed: 2026-08-29*

## Self-Check: PASSED

- Cả 3 file `.dart` mới tồn tại trên đĩa, xác nhận qua `ls -la` (`transfer_excluded_from_report_test.dart` 2952 bytes, `edit_transaction_3step_test.dart` 3864 bytes, `idempotency_test.dart` 3342 bytes)
- 3 commit hash (`95f249a`, `68a28a3`, `7e390fc`) đều tồn tại trong `git log` của repo `source/app`
