---
phase: 06-n-i-api-th-t-v-i-app-flutter
plan: 04
subsystem: app
tags: [flutter, integration-test, refresh-token, jwt, budgets, reports]

# Dependency graph
requires: ["06-01", "06-02"]
provides:
  - "TokenStorage.saveForTest — hook test-only để seed cặp token (kể cả access token hết hạn) không qua luồng đăng nhập thật"
  - "integration_test/real_backend/refresh_token_concurrency_test.dart kiểm chứng SC5 (gộp refresh khi nhiều request cùng hết hạn)"
  - "integration_test/real_backend/budgets_and_reports_smoke_test.dart kiểm nối 4 endpoint budgets/reports"
affects: [06-05]

# Tech tracking
tech-stack:
  added:
    - "dart_jsonwebtoken ^2.14.0 (dev_dependency) — ký JWT access token thật với exp quá khứ, dùng riêng trong integration_test/real_backend/"
  patterns:
    - "Test-only hook @visibleForTesting trên TokenStorage.saveForTest — alias của save() có sẵn, không mở thêm bề mặt ghi dữ liệu mới"
    - "Ký JWT giả lập hết hạn bằng ĐÚNG secret+thuật toán backend dùng (HS256, base64-decode trước khi ký) thay vì chuỗi chữ ký sai — vì JwtAuthFilter phân biệt TOKEN_EXPIRED (ExpiredJwtException) khác TOKEN_INVALID (JwtException khác), chỉ TOKEN_EXPIRED kích hoạt AuthInterceptor refresh"
    - "Test tự kiểm tiền điều kiện (precheck 401 + error.code=TOKEN_EXPIRED) bằng Dio trần trước khi chạy phần assertion chính — lỗi hiện rõ ngay tại precheck thay vì mơ hồ ở cuối"

key-files:
  created:
    - source/app/integration_test/real_backend/refresh_token_concurrency_test.dart
    - source/app/integration_test/real_backend/budgets_and_reports_smoke_test.dart
  modified:
    - source/app/lib/core/network/token_storage.dart
    - source/app/pubspec.yaml

key-decisions:
  - "GET /reports/by-category-group trả OBJECT {total, groups: [...]} chứ không phải mảng trần như bản nháp PLAN dùng apiClient.getList() — sửa thành apiClient.get() và assert groups là List, đối chiếu api/06-BAO-CAO.md mục 3 trước khi hoàn tất."
  - "category_id của một ngân sách để gọi /budgets/suggestion nằm ở budgets[].category.id (nested object), không phải trường category_id phẳng như bản nháp PLAN — sửa theo đúng shape thật ở api/05-NGAN-SACH.md mục 1."

requirements-completed: []

# Metrics
duration: ~25min
completed: 2026-08-29
---

# Phase 6 Plan 4: TokenStorage test hook + integration_test SC5 (refresh gộp) và smoke budgets/reports Summary

**Thêm hook test-only `saveForTest` vào `TokenStorage` để seed access token hết hạn ký thật bằng cùng JWT_SECRET/HS256 backend dùng, viết 2 `integration_test/real_backend/*.dart` mới kiểm chứng SC5 (gộp refresh khi nhiều request cùng hết hạn) và kiểm nối 4 endpoint `budgets`/`reports` (bao gồm 2 endpoint D-11 từng nghi ngờ thiếu).**

## Performance

- **Duration:** ~25 phút
- **Started:** 2026-08-29T07:22:00Z (tiếp theo 06-03)
- **Completed:** 2026-08-29T07:31:16Z
- **Tasks:** 3
- **Files modified:** 4 (2 mới hoàn toàn, 2 sửa) — tất cả ở repo `source/app`

## Accomplishments

- `TokenStorage.saveForTest`: alias `@visibleForTesting` của `save()` có sẵn — cho phép test ghi thẳng bất kỳ cặp token nào (kể cả access token đã hết hạn) mà không qua luồng đăng nhập/refresh thật. Không mở thêm bề mặt ghi dữ liệu mới, chỉ đánh dấu rõ ranh giới sử dụng để `flutter analyze`/lint cảnh báo nếu bị gọi nhầm từ mã nghiệp vụ (T-06-06, mitigate).
- `refresh_token_concurrency_test.dart` (SC5, D-13b): đăng nhập thật lấy `refresh_token` hợp lệ + `userId`; tự ký JWT access token THẬT (không phải chuỗi chữ ký sai) với `exp` quá khứ 10 giây, cùng thuật toán HS256 và secret base64 đọc qua `--dart-define=JWT_SECRET`; seed vào `TokenStorage` qua `saveForTest`; **precheck** bằng Dio trần xác nhận backend trả đúng `401` + `error.code=TOKEN_EXPIRED` (không phải `TOKEN_INVALID`) trước khi tiếp tục; bắn 3 request song song (`/wallets`, `/categories`, `/transactions`) không `await` tuần tự; xác nhận cả 3 thành công và phiên không bị thu hồi oan (gọi thêm `/auth/me` vẫn thành công).
- `budgets_and_reports_smoke_test.dart` (bước 6 D-18): đăng nhập, gọi `GET /budgets` (seed có sẵn 1 ngân sách), `GET /budgets/suggestion?category_id=...`, `GET /reports/by-category-group`, `GET /reports/daily-trend` — xác nhận cả 4 endpoint đều gọi thông trên backend thật, kể cả 2 endpoint D-11 (06-CONTEXT.md) từng nghi ngờ có thể thiếu (RESEARCH.md đã xác minh cả hai tồn tại: `BudgetController.java:61`, `ReportController.java:64`).
- Sửa 2 sai lệch trong bản nháp `<action>` của PLAN (thuộc Task 3), phát hiện khi đối chiếu `api/*.md` trước khi hoàn tất:
  1. `GET /reports/by-category-group` trả **object** `{total, groups: [...]}`, không phải mảng trần — PLAN dùng `apiClient.getList(...)` sẽ ném `ApiException` (`_asList` không chấp nhận `Map`). Sửa thành `apiClient.get(...)` và assert `['groups']` là `List`.
  2. `category_id` để gọi `/budgets/suggestion` nằm ở `budgets[].category.id` (nested object theo `api/05-NGAN-SACH.md` mục 1), không phải trường `category_id` phẳng như PLAN viết — sửa lại đường truy cập.
- `flutter analyze integration_test lib/core/network` sạch, không lỗi biên dịch cho cả 2 file mới lẫn 4 file cũ từ 06-02/06-03.
- `flutter pub get` thành công, `dart_jsonwebtoken 2.17.0` thoả `^2.14.0` khai trong `pubspec.yaml`.

## Task Commits

Tất cả commit ở repo `source/app` (nhánh `develop`):

1. **Task 1: hook test-only TokenStorage.saveForTest** - `fa4c3b0` (feat)
2. **Task 2: integration_test SC5 gộp refresh token (D-13b)** - `71bd102` (test)
3. **Task 3: integration_test smoke budgets/reports (bước 6 D-18)** - `7babc11` (test)

**Plan metadata:** (commit này ở repo `source/server`, sau khi tạo SUMMARY)

## Files Created/Modified

- `source/app/lib/core/network/token_storage.dart` (sửa) - thêm `saveForTest` (`@visibleForTesting`) + import `flutter/foundation.dart`
- `source/app/pubspec.yaml` (sửa) - thêm dev dependency `dart_jsonwebtoken ^2.14.0`
- `source/app/integration_test/real_backend/refresh_token_concurrency_test.dart` (mới) - test SC5, ký JWT hết hạn thật, precheck error.code, bắn request song song
- `source/app/integration_test/real_backend/budgets_and_reports_smoke_test.dart` (mới) - test smoke 4 endpoint budgets/reports

## Decisions Made

- Dùng đúng shape thật `GET /reports/by-category-group` = `{total, groups}` (object) thay vì mảng trần theo bản nháp PLAN — đối chiếu `api/06-BAO-CAO.md` mục 3 trước khi hoàn tất Task 3, đúng tinh thần D-09 (backend/app phải khớp `api/*.md`, không suy đoán theo mock).
- Dùng đúng đường truy cập `budgets[].category.id` (nested) thay vì `category_id` phẳng cho tham số `GET /budgets/suggestion?category_id=` — đối chiếu `api/05-NGAN-SACH.md` mục 1.
- Giữ nguyên toàn bộ thiết kế Task 1/Task 2 của PLAN (không có deviation) — JWT ký thật bằng HS256 + secret base64-decode đã đối chiếu trực tiếp với `JwtService.java`/`JwtAuthFilter.java`, khớp hoàn toàn với API `SecretKey(key, isBase64Encoded: true)` của `dart_jsonwebtoken` (đã đọc mã nguồn gói tại `keys.dart`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `GET /reports/by-category-group` gọi sai kiểu response trong bản nháp PLAN**
- **Found during:** Task 3, lúc đối chiếu `api/06-BAO-CAO.md` theo hướng dẫn `<action>` của PLAN
- **Issue:** Bản nháp `<action>` dùng `apiClient.getList('/reports/by-category-group')` nhưng response thật là object `{total, groups: [...]}`, không phải mảng trần — sẽ ném `ApiException(code: internal)` ngay khi chạy vì `ApiClient._asList` từ chối `Map`.
- **Fix:** Đổi sang `apiClient.get(...)`, assert `byCategoryGroup['groups']` là `List<dynamic>`.
- **Files modified:** `source/app/integration_test/real_backend/budgets_and_reports_smoke_test.dart`
- **Commit:** `7babc11`

**2. [Rule 1 - Bug] Tham số `category_id` cho `/budgets/suggestion` đọc sai trường trong bản nháp PLAN**
- **Found during:** Task 3, cùng lúc đối chiếu tài liệu
- **Issue:** Bản nháp PLAN đọc `(budgets.first as Map)['category_id']` nhưng response thật của `GET /budgets` lồng category trong object `category: {id, name, ...}` — trường phẳng `category_id` không tồn tại, sẽ gửi `category_id=null` lên backend.
- **Fix:** Đổi sang `(budgets.first['category'] as Map)['id']`.
- **Files modified:** `source/app/integration_test/real_backend/budgets_and_reports_smoke_test.dart`
- **Commit:** `7babc11`

## Known Stubs

Không có — cả 2 file test đều gọi API thật, không có dữ liệu giả/hardcode nào chảy vào UI.

## Threat Flags

Không phát sinh bề mặt bảo mật mới ngoài threat model đã ghi trong PLAN (T-06-06 `saveForTest`, T-06-07 JWT ký thật hết hạn dùng trong test) — cả hai đã có mitigation/accept rõ ràng trong PLAN, không cần bổ sung.

## Issues Encountered — KHÔNG chạy được thật trên backend/emulator

**Đúng như blocker đã biết trước khi bắt đầu plan này (nêu ở prior_wave_context và 06-01/06-02/06-03 SUMMARY):** môi trường thực thi phiên này **không có Android emulator kết nối** (`flutter devices` chỉ thấy Windows desktop, Chrome, Edge) và **backend không đang chạy** (`curl http://localhost:8080/v1/auth/me` không có phản hồi, exit code 7 — connection refused). Do đó **cả 2 `integration_test/real_backend/*.dart` mới CHƯA được chạy thật** trên máy ảo Pixel 7 với backend thật trong phiên này.

**Đã kiểm chứng đầy đủ bằng các cách khác:**
- `flutter analyze integration_test lib/core/network` sạch — không lỗi biên dịch, không lỗi kiểu, cho cả 2 file mới lẫn 4 file cũ (06-02/06-03).
- `flutter pub get` xác nhận `dart_jsonwebtoken 2.17.0` được giải quyết đúng theo constraint `^2.14.0`.
- Đối chiếu API `SecretKey`/`JWT.sign()` của gói `dart_jsonwebtoken` (đọc trực tiếp mã nguồn tại `keys.dart`/`jwt.dart` trong pub cache) khớp đúng cách gọi trong test.
- Đối chiếu trực tiếp `JwtService.java` (base64-decode secret trước `Keys.hmacShaKeyFor`, thuật toán HS256, claim `sub`+`plan`) và `JwtAuthFilter.java` (bắt riêng `ExpiredJwtException` → `TOKEN_EXPIRED`, các `JwtException` khác → `TOKEN_INVALID`) — xác nhận JWT tự ký trong test dùng đúng shape mà backend sẽ chấp nhận là "hết hạn hợp lệ" chứ không phải "sai chữ ký".
- Đối chiếu từng trường JSON dùng trong `budgets_and_reports_smoke_test.dart` với `api/05-NGAN-SACH.md` (mục 1, 6) và `api/06-BAO-CAO.md` (mục 3, 5) — phát hiện và sửa 2 sai lệch (xem "Deviations from Plan").
- Xác nhận `DevDataSeeder` (06-01) tạo đúng user `seed.user@datn.local`/`SeedPass123!` và seed sẵn 1 ngân sách dùng trong cả 2 test.

**Việc còn lại trước khi coi Task 2/3 kiểm chứng đầy đủ:** chạy trên máy có Android emulator (Pixel 7 API 36) + backend `mvn spring-boot:run` profile `dev` đang chạy, cần thêm biến môi trường `JWT_SECRET` (đọc từ cấu hình dev backend, ví dụ `.env`/biến môi trường máy dev):

```bash
cd source/app
flutter test integration_test/real_backend/refresh_token_concurrency_test.dart --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 --dart-define=JWT_SECRET=$JWT_SECRET -d emulator-5554
flutter test integration_test/real_backend/budgets_and_reports_smoke_test.dart --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 -d emulator-5554
```

Đồng thời D-14 khuyến nghị đếm số lần `/auth/refresh` thật qua log backend trong khoảng thời gian chạy `refresh_token_concurrency_test.dart` — việc này được thực hiện ở checkpoint Task 4 của 06-05-PLAN.md, không thuộc phạm vi tự động hoá của plan này (đã ghi rõ trong docstring của test).

**Không khẳng định 2 test này PASS — chỉ khẳng định chúng biên dịch sạch và đối chiếu đúng hợp đồng API tài liệu (sau khi sửa 2 sai lệch).**

## User Setup Required

Cần chạy lại bộ kiểm chứng ở mục "Issues Encountered" trên máy có Android emulator + backend thật (kèm biến môi trường `JWT_SECRET` cho riêng test SC5, và bước đếm log `/auth/refresh` ở 06-05 Task 4 cho D-14) trước khi coi SC5 của Phase 6 đạt đầy đủ. Không cần cấu hình dịch vụ ngoài nào khác.

## Next Phase Readiness

- 6/6 `integration_test/real_backend/*.dart` của D-12 đã có đủ (SC1, SC2a, SC2b, SC2c, SC4, SC5) cộng thêm 1 file smoke budgets/reports — tổng 7 file test mới trong `integration_test/real_backend/`.
- **Cần chạy thật cả 7 test này** trên môi trường có Android emulator + backend `dev` trước khi đóng Phase 6 (xem "Issues Encountered"), kèm biến `JWT_SECRET` riêng cho `refresh_token_concurrency_test.dart` và bước đếm log `/auth/refresh` (D-14).
- 06-05 (plan cuối) chịu trách nhiệm: chạy toàn bộ 7 test thật, kiểm 3 phép thử nghiệp vụ tay còn lại (D-13 airplane mode), cập nhật tài liệu đóng phase (D-23/D-24/D-25).

---
*Phase: 06-n-i-api-th-t-v-i-app-flutter*
*Completed: 2026-08-29*

## Self-Check: PASSED

- `source/app/lib/core/network/token_storage.dart` tồn tại, có `saveForTest`/`@visibleForTesting` (xác nhận qua `grep`)
- `source/app/integration_test/real_backend/refresh_token_concurrency_test.dart` tồn tại (xác nhận qua `test -f`)
- `source/app/integration_test/real_backend/budgets_and_reports_smoke_test.dart` tồn tại (xác nhận qua `test -f`)
- 3 commit hash (`fa4c3b0`, `71bd102`, `7babc11`) đều tồn tại trong `git log` của repo `source/app`
