---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
plan: 01
subsystem: api
tags: [spring-boot, jpa, postgresql, flyway, notification, native-query]

# Dependency graph
requires:
  - phase: 03-giao-dich
    provides: TransactionWriter, mẫu quyền D-27 (WalletRepository.findByIdForUser), khuôn Testcontainers
provides:
  - Bảng notifications (V9) — hộp thư thông báo trong app, UNIQUE chống trùng cảnh báo ngân sách
  - Bảng export_jobs (V10) — hạ tầng cho REPORT-05, chưa dùng ở plan này
  - GET /notifications, PATCH /notifications/{id}/read
  - api/11-THONG-BAO.md — đặc tả API mới
  - NotificationRepository.insertBudgetAlertIfNotExists — điểm nối sẵn cho Plan 02 (budget/)
affects: [04-02-ngan-sach, 04-03-so-no, 04-06-bao-cao]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "UNIQUE index đầy đủ (không WHERE) thay cho partial UNIQUE khi cần dùng với ON CONFLICT qua Spring Data JPA native query"
    - "Biểu thức ngày trong index expression phải IMMUTABLE — (created_at AT TIME ZONE 'UTC')::date thay vì created_at::date trần"

key-files:
  created:
    - db/migration/V9__thong_bao.sql
    - db/migration/V10__export_jobs.sql
    - api/11-THONG-BAO.md
    - source/server/src/main/java/com/datn/financeapp/notification/entity/Notification.java
    - source/server/src/main/java/com/datn/financeapp/notification/repository/NotificationRepository.java
    - source/server/src/main/java/com/datn/financeapp/notification/service/NotificationService.java
    - source/server/src/main/java/com/datn/financeapp/notification/controller/NotificationController.java
    - source/server/src/main/java/com/datn/financeapp/notification/dto/NotificationListItemResponse.java
    - source/server/src/main/java/com/datn/financeapp/notification/dto/NotificationListResponse.java
    - source/server/src/test/java/com/datn/financeapp/notification/NotificationIntegrationTest.java
  modified:
    - CLAUDE.md
    - api/00-QUY-UOC-CHUNG.md
    - source/server/src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java

key-decisions:
  - "uq_notif_budget_alert đổi từ partial UNIQUE INDEX (WHERE type = 'budget_alert') sang UNIQUE đầy đủ — ON CONFLICT qua Spring Data JPA native query không tương thích ổn định với partial index"
  - "Biểu thức ngày trong uq_notif_budget_alert dùng (created_at AT TIME ZONE 'UTC')::date thay vì created_at::date trần — Postgres từ chối cast phụ thuộc session timezone trong index expression (yêu cầu IMMUTABLE)"

patterns-established:
  - "notification/ package feature-first đầy đủ đầu tiên của Phase 4 — mẫu tham chiếu cho budget/, debt/, goal/, recurring/, report/ ở các plan sau"

requirements-completed: []  # BUDGET-06/JOB-04 khai ở frontmatter PLAN nhưng CHƯA hoàn thành đầy đủ —
  # plan này chỉ xây hạ tầng lưu trữ/đọc thông báo. BUDGET-06 (danh sách cảnh báo severity) hoàn
  # thành ở Plan 02, JOB-04 (job nhắc nợ chạy lịch) hoàn thành ở Plan 07. Không mark-complete ở
  # REQUIREMENTS.md để tránh sai lệch trạng thái.

# Metrics
duration: đã thực thi ở phiên trước (không có timestamp bắt đầu/kết thúc ghi lại); phiên này chỉ verify + hoàn thiện tài liệu
completed: 2026-08-27
---

# Phase 4 Plan 01: Hạ tầng thông báo Summary

**Bảng `notifications`/`export_jobs` mới (V9/V10) và hai điểm cuối `GET /notifications`/`PATCH /notifications/{id}/read` với quyền kiểm tra ngay trong SQL, làm nền cho cảnh báo ngân sách (D-41) và nhắc nợ (JOB-04) ở các plan sau.**

## Performance

- **Duration:** Đã thực thi trong phiên làm việc trước (commit lúc 2026-08-27 21:11–21:19); phiên này thực hiện lại đầy đủ bước verify (chạy toàn bộ `mvn test`, kiểm tra migration V1→V10, đối chiếu acceptance criteria) và hoàn thiện SUMMARY/STATE/ROADMAP còn thiếu.
- **Tasks:** 2/2 hoàn thành
- **Files modified:** 13 (5 ở repo gốc DATN, 8 ở repo source/server)

## Accomplishments
- Bảng `notifications` (V9) tồn tại với UNIQUE chống trùng cảnh báo ngân sách khi bulk nhiều event cùng lúc, và bảng `export_jobs` (V10) chuẩn bị sẵn cho REPORT-05
- `GET /notifications` (phân trang chuẩn, lọc `is_read`) và `PATCH /notifications/{id}/read` (idempotent, 404 khi không có quyền) hoạt động đúng, verify qua Testcontainers thật
- `api/11-THONG-BAO.md` — đặc tả API mới hoàn chỉnh, ghi rõ nguyên tắc "lịch sử tại thời điểm phát sinh" (D-41) và phạm vi không làm (D-39)
- `CLAUDE.md` gốc và `api/00-QUY-UOC-CHUNG.md` đã cập nhật bản đồ tài liệu (`api/01` đến `api/11`)
- `NotificationRepository.insertBudgetAlertIfNotExists` chuẩn bị sẵn cho Plan 02 (`budget/`) gọi lại

## Task Commits

Cả hai task được commit atomic ở đúng repo tương ứng (lưu ý: hai repo git độc lập — `DATN/` gốc và `source/server/`):

**Repo gốc `DATN/`:**
1. **Task 1: Migration V9/V10 + api/11-THONG-BAO.md** - `c33f48f` (feat(db,api): thêm hạ tầng thông báo và export_jobs cho Phase 4)
2. **Fix phát hiện khi chạy Testcontainers thật:** `2bf1ebe` (fix(db): sửa uq_notif_budget_alert dùng biểu thức ngày IMMUTABLE)

**Repo `source/server/`:**
1. **Task 2: Entity/Repository/Service/Controller + test tích hợp** - `0c0474c` (feat(notification): thêm module notification với 2 endpoint đọc/đánh dấu đã đọc)

_Note: fix `2bf1ebe` là một auto-fix Rule 1 (bug) phát hiện khi chạy test thật lần đầu — Postgres từ chối cast `created_at::date` trực tiếp trong index expression vì phụ thuộc session timezone. Đã sửa ngay trong migration V9 và ghi chú đầy đủ lý do trong file SQL._

## Files Created/Modified

**Repo gốc `DATN/`:**
- `db/migration/V9__thong_bao.sql` - Bảng `notifications`, UNIQUE chống trùng cảnh báo (IMMUTABLE date expression)
- `db/migration/V10__export_jobs.sql` - Bảng `export_jobs` cho REPORT-05 (Plan 06 dùng)
- `api/11-THONG-BAO.md` - Đặc tả API mới: GET/PATCH notifications
- `CLAUDE.md` - Cập nhật bản đồ tài liệu "api/01 đến api/11"
- `api/00-QUY-UOC-CHUNG.md` - Thêm dòng 11-THONG-BAO.md vào bảng danh sách file

**Repo `source/server/`:**
- `notification/entity/Notification.java` - Entity ánh xạ bảng `notifications`
- `notification/repository/NotificationRepository.java` - 5 method native query, quyền `user_id = :currentUser` trong SQL, `insertBudgetAlertIfNotExists` dự phòng cho Plan 02
- `notification/service/NotificationService.java` - `list()`/`markRead()`, `NOT_FOUND` khi không có quyền
- `notification/controller/NotificationController.java` - `GET /notifications`, `PATCH /notifications/{id}/read`
- `notification/dto/NotificationListItemResponse.java`, `NotificationListResponse.java` - DTO phân trang chuẩn
- `common/exception/GlobalExceptionHandlerTest.java` - Thêm `NotificationController` vào `excludeFilters`
- `notification/NotificationIntegrationTest.java` - 4 test case Testcontainers thật (quyền, idempotent, 404, lọc is_read)

## Decisions Made
- Đổi `uq_notif_budget_alert` từ partial UNIQUE INDEX sang UNIQUE đầy đủ (bỏ mệnh đề `WHERE type = 'budget_alert'`) vì `ON CONFLICT` qua Spring Data JPA native query cần khai lại đúng biểu thức WHERE, dễ lệch câu chữ giữa migration và repository — chấp nhận áp UNIQUE cho mọi `type`, vô hại vì các loại thông báo khác cũng hợp lý không trùng lặp theo (user, reference, ngày, loại)
- Biểu thức ngày trong index dùng `(created_at AT TIME ZONE 'UTC')::date` thay vì `created_at::date` trần — phát hiện lỗi "functions in index expression must be marked IMMUTABLE" khi chạy Testcontainers thật lần đầu

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Postgres từ chối index expression `created_at::date` không IMMUTABLE**
- **Found during:** Task 1/2 — chạy Testcontainers thật lần đầu để verify migration
- **Issue:** Cast `TIMESTAMPTZ -> DATE` trực tiếp trong index expression phụ thuộc timezone của session (không xác định trước tại thời điểm tạo index), Postgres từ chối với lỗi "functions in index expression must be marked IMMUTABLE"
- **Fix:** Đổi biểu thức thành `(created_at AT TIME ZONE 'UTC')::date` — quy về UTC cố định nên IMMUTABLE. Đồng thời đổi `uq_notif_budget_alert` từ partial UNIQUE INDEX sang UNIQUE đầy đủ để tương thích với `ON CONFLICT` trong native query
- **Files modified:** `db/migration/V9__thong_bao.sql`, `NotificationRepository.java` (câu `ON CONFLICT` khớp lại biểu thức mới)
- **Verification:** `mvn test -Dtest=NotificationIntegrationTest` xanh 4/4, migration V1→V10 chạy sạch qua Testcontainers
- **Commit:** `2bf1ebe`

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug phát hiện khi chạy test thật)
**Impact on plan:** Fix cần thiết để migration chạy được trên Postgres thật — không phải scope creep, đúng tinh thần "chọn phương án chạy được thay vì giữ nguyên cú pháp ban đầu" đã ghi sẵn trong plan (Task 2 action, đoạn "ưu tiên chọn phương án chạy được").

## Issues Encountered

Docker Desktop chưa chạy khi bắt đầu phiên verify này — đã khởi động thủ công và chờ container sẵn sàng trước khi chạy `mvn test` với Testcontainers. Không ảnh hưởng tới nội dung plan, chỉ là bước chuẩn bị môi trường.

## User Setup Required

None - không có cấu hình dịch vụ ngoài nào cần thiết lập thủ công.

## Verification Results

- `mvn test -Dtest=NotificationIntegrationTest,GlobalExceptionHandlerTest` — xanh (4/4 + 3/3, 0 lỗi)
- `mvn test` toàn bộ suite (26 test class) — xanh 100% (0 Failures, 0 Errors) — không có regression trên Phase 1-3
- Migration Flyway V1→V10 chạy sạch qua Testcontainers, không lỗi checksum/thứ tự
- `grep -rn "AT TIME ZONE\|giờ Việt Nam" api/11-THONG-BAO.md` — rỗng, tài liệu mới không dính lỗi múi giờ
- Tất cả acceptance criteria của Task 1 và Task 2 trong `04-01-PLAN.md` đã xác nhận đạt (grep trực tiếp trên file thật)

## Next Phase Readiness

- Bảng `notifications` sẵn sàng cho Plan 02 (`budget/` — cảnh báo ngân sách qua `TransactionalEventListener`) và Plan 03 (`debt/` — nhắc nợ JOB-04) insert vào
- Bảng `export_jobs` sẵn sàng cho Plan 06 (`report/`) dùng ngay, không cần mở lại migration
- `api/11-THONG-BAO.md` là nguồn sự thật cho hợp đồng API, không cần đặc tả lại
- Không có blocker nào cho Plan 02

## Self-Check: PASSED

Đã xác nhận toàn bộ 13 file claim (5 repo gốc DATN, 8 repo source/server) tồn tại trên đĩa và
cả 3 commit hash claim (`c33f48f`, `2bf1ebe`, `0c0474c`) tồn tại trong lịch sử git. Không có
mục nào MISSING.

---
*Phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o*
*Completed: 2026-08-27*
