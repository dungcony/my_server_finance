---
phase: 06-n-i-api-th-t-v-i-app-flutter
plan: 02
subsystem: app
tags: [flutter, dio, mock-interceptor, integration-test]

# Dependency graph
requires: ["06-01"]
provides:
  - "MockInterceptor luôn gắn vào Dio, chỉ ép mock cho /ai/*, các nhóm còn lại ra mạng thật khi USE_MOCK=false"
  - "CHUYEN-SANG-API-THAT.md có ví dụ lệnh chạy đúng prefix /v1"
  - "2 integration_test/real_backend/*.dart (auth_me, category_rollup) sẵn sàng chạy khi có emulator + backend thật"
affects: [06-03, 06-04, 06-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "MockInterceptor tự quyết định mock/forward theo path (_alwaysMockPrefixes), không còn nhánh if (useMock) ở nơi gắn interceptor"
    - "integration_test/real_backend/*.dart gọi trực tiếp ApiClient (không qua UI app.main()) để cô lập kiểm chứng tầng mạng"

key-files:
  created:
    - source/app/integration_test/real_backend/auth_me_test.dart
    - source/app/integration_test/real_backend/category_rollup_test.dart
  modified:
    - source/app/lib/core/network/mock/mock_interceptor.dart
    - source/app/lib/core/network/api_client.dart
    - source/app/CHUYEN-SANG-API-THAT.md

key-decisions:
  - "Giữ nguyên cách hiện thực D-02 theo PATTERNS.md (luôn gắn MockInterceptor + interceptor tự quyết định path) thay vì cách RESEARCH.md phác thảo ban đầu (điều kiện useMock nằm trong interceptor mà interceptor không được gắn khi useMock=false)"
  - "2 test mới gọi ApiClient trực tiếp, không qua app.main()/UI — SC1/SC2a tập trung kiểm chứng đường mạng/prefix/vòng đời thẻ và cộng gộp danh mục, không phải luồng UI"

requirements-completed: []

# Metrics
duration: ~20min
completed: 2026-08-29
---

# Phase 6 Plan 2: MockInterceptor ngoại lệ /ai/* + integration_test đầu tiên trên backend thật Summary

**Sửa MockInterceptor để luôn gắn vào Dio nhưng chỉ còn ép mock cho `/ai/*` khi `USE_MOCK=false` (D-02), sửa ví dụ sai `/api/v1` trong tài liệu app (D-07), và viết 2 `integration_test/real_backend/*.dart` gọi trực tiếp `ApiClient` kiểm chứng SC1 (`/auth/me`) và SC2a (cộng gộp danh mục con) trên backend thật.**

## Performance

- **Duration:** ~20 phút
- **Started:** 2026-08-29T04:14:56Z (tiếp theo 06-01)
- **Completed:** 2026-08-29T04:21:13Z
- **Tasks:** 3
- **Files modified:** 5 (3 sửa, 2 mới) — tất cả ở repo `source/app`

## Accomplishments

- `api_client.dart` không còn nhánh `if (AppConfig.useMock)` khi gắn `MockInterceptor` — interceptor giờ **luôn** được gắn vào cả `_dio` và `refreshClient`, tự quyết định mock hay forward theo path
- `mock_interceptor.dart` thêm `_alwaysMockPrefixes = ['/ai/']`: khi `USE_MOCK=false`, mọi path khác `/ai/*` gọi `handler.next(options)` ra mạng thật; path `/ai/*` vẫn luôn được `MockRouter` xử lý bất kể cờ — đúng D-02 vì backend Phase 5 (`AiController`) chưa tồn tại
- `CHUYEN-SANG-API-THAT.md` §1 sửa dòng lệnh ví dụ từ `http://10.0.2.2:8080/api/v1` thành `http://10.0.2.2:8080/v1`, khớp `app_config.dart` (đã đúng sẵn, không sửa) và D-04/D-05
- 2 file `integration_test/real_backend/*.dart` mới: `auth_me_test.dart` (đăng nhập bằng `seed.user@datn.local`/`SeedPass123!` qua `DevDataSeeder` của 06-01, gọi `GET /auth/me`, kiểm `email`/`id`) và `category_rollup_test.dart` (ghi chi vào danh mục con "Cà phê", lọc `GET /transactions?category_id=<Ăn uống>`, xác nhận giao dịch xuất hiện — kiểm chứng phép thử §2.1)
- 3 file `integration_test/*.dart` gốc (mock) không bị đụng tới — xác nhận qua `git status`, chỉ thư mục `real_backend/` mới xuất hiện

## Task Commits

Tất cả commit ở repo `source/app` (nhánh `develop`):

1. **Task 1: MockInterceptor với ngoại lệ /ai/* (D-02) + sửa tài liệu prefix URL (D-07)** - `1844782` (feat)
2. **Task 2: integration_test auth/me trên backend thật (SC1, D-12)** - `d25de5a` (test)
3. **Task 3: integration_test cộng gộp danh mục con (SC2a, phép thử §2.1)** - `db0582c` (test)

**Plan metadata:** (commit này ở repo `source/server`, sau khi tạo SUMMARY)

## Files Created/Modified

- `source/app/lib/core/network/api_client.dart` - bỏ điều kiện `if (AppConfig.useMock)` quanh việc gắn `MockInterceptor`, luôn gắn cho `_dio` và `refreshClient`
- `source/app/lib/core/network/mock/mock_interceptor.dart` - thêm `import '../../config/app_config.dart'`, hằng `_alwaysMockPrefixes`, nhánh `handler.next(options)` khi `!AppConfig.useMock && !forceMock`
- `source/app/CHUYEN-SANG-API-THAT.md` - sửa dòng lệnh ví dụ `--dart-define=API_BASE_URL` từ `/api/v1` thành `/v1`
- `source/app/integration_test/real_backend/auth_me_test.dart` (mới) - test SC1, gọi `ApiClient` trực tiếp
- `source/app/integration_test/real_backend/category_rollup_test.dart` (mới) - test SC2a, gọi `ApiClient` trực tiếp

## Decisions Made

- Dùng đúng cách hiện thực D-02 theo `06-PATTERNS.md`: sửa điểm gắn interceptor ở `api_client.dart` (luôn gắn), không sửa theo hướng RESEARCH.md phác thảo ban đầu (điều kiện `useMock` nằm hẳn trong `MockInterceptor` nhưng interceptor lại không được gắn nếu `!useMock` — logic không khả thi)
- 2 test mới gọi trực tiếp `ApiClient(tokenStorage:..., onSessionExpired:...)` thay vì `app.main()` + `tester.pumpAndSettle()` như 3 test mock cũ — SC1/SC2a của D-18 bước 1-2 tập trung xác nhận đường mạng/prefix/vòng đời thẻ/cộng gộp danh mục, không cần dựng UI
- Sửa nhỏ `filtered.items.map((e) => e['id'])` bỏ cast `as Map<String, dynamic>` thừa (do `getPaged<T>` với `parse = (json) => json` đã suy luận đúng kiểu `T`) — phát hiện qua `flutter analyze`, không đổi hành vi

## Deviations from Plan

None - kế hoạch thực thi đúng như viết. Một sửa nhỏ (bỏ cast thừa trong `category_rollup_test.dart`) là làm sạch warning từ `flutter analyze`, không phải deviation nghiệp vụ.

## Issues Encountered

- **Không có Android emulator kết nối trong môi trường thực thi này** (`flutter devices` chỉ thấy Windows desktop, Chrome, Edge) và **backend không đang chạy** (`docker info` thấy Docker Desktop có sẵn nhưng chưa xác nhận Testcontainers PostgreSQL cho backend dev, port 8080 đang rảnh) — do đó **2 `integration_test/real_backend/*.dart` mới CHƯA được chạy thật** trên máy ảo Pixel 7 với backend thật trong phiên này. Đã kiểm chứng đầy đủ bằng:
  - `flutter analyze` sạch cho cả `lib/core/network/` và `integration_test/real_backend/` (không lỗi biên dịch)
  - Đối chiếu kỹ từng trường JSON dùng trong test (`access_token`, `refresh_token`, `email`, `id`, `parent_category_id`, `children`, `transaction.id`, `category_id` filter) với `api/01-XAC-THUC.md`, `api/03-DANH-MUC.md`, `api/04-GIAO-DICH.md` — khớp hoàn toàn với giả định trong PLAN
  - Xác nhận `DevDataSeeder` (06-01) tạo đúng user `seed.user@datn.local`/`SeedPass123!`
  - Xác nhận cây danh mục hệ thống "Ăn uống" → "Cà phê" tồn tại sẵn từ `V5__du_lieu_he_thong.sql` (không cần tạo trong test)

  **Việc còn lại trước khi coi Task 2/3 kiểm chứng đầy đủ:** chạy trên máy có Android emulator (Pixel 7 API 36) + backend `mvn spring-boot:run` profile `dev` đang chạy:
  ```bash
  cd source/app
  flutter test integration_test/real_backend/auth_me_test.dart --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 -d emulator-5554
  flutter test integration_test/real_backend/category_rollup_test.dart --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 -d emulator-5554
  flutter test integration_test/add_transaction_flow_test.dart
  ```

## User Setup Required

Cần chạy lại bộ kiểm chứng ở mục "Issues Encountered" trên máy có Android emulator + backend thật trước khi coi SC1/SC2a của Phase 6 đạt đầy đủ. Không cần cấu hình dịch vụ ngoài nào khác.

## Next Phase Readiness

- `MockInterceptor` đã sẵn sàng cho các plan tiếp theo (06-03 trở đi) nối nhóm ghi/sửa/xoá — chỉ `/ai/*` còn bị chặn, mọi nhóm khác đã ra mạng thật khi `USE_MOCK=false`
- 2 `integration_test/real_backend/*.dart` đã có làm khuôn mẫu (đăng nhập trực tiếp qua `ApiClient`, không qua UI) cho các test tiếp theo của D-12 (idempotency, refresh concurrency, chuyển tiền, sửa/xoá 3 bước)
- **Cần chạy thật 2 test này + `add_transaction_flow_test.dart`** trên môi trường có Android emulator trước khi đóng Phase 6 (xem "Issues Encountered")

---
*Phase: 06-n-i-api-th-t-v-i-app-flutter*
*Completed: 2026-08-29*

## Self-Check: PASSED

- Tất cả 6 file (5 file app + 1 SUMMARY.md) đều tồn tại trên đĩa
- 3 commit hash (`1844782`, `d25de5a`, `db0582c`) đều tồn tại trong `git log` của repo `source/app`
