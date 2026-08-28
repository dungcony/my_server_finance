---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
plan: 06
subsystem: api
tags: [report, spring-data-jpa, native-sql, async, csv-export, postgresql]

requires:
  - phase: 04-01
    provides: bảng export_jobs (V10), hạ tầng thông báo
  - phase: 04-02
    provides: BudgetService.alerts(userId) tái dùng cho budgets_needing_attention
  - phase: 03
    provides: TransactionRepository/CategoryRepository.findCategoryTree mẫu tham chiếu
provides:
  - 6 điểm cuối báo cáo chỉ đọc (home/summary/by-category-group/by-category/daily-trend/monthly-trend)
  - Xuất báo cáo CSV bất đồng bộ qua export_jobs (POST/GET/GET download)
  - ReportQueryFragments.REPORT_ELIGIBLE dùng chung mọi query báo cáo
  - Tài liệu múi giờ báo cáo nhất quán (3 vị trí), api/06 khớp hợp đồng POST export
affects: [05-nhom-gia-dinh, ai-phase-sau (ai_recommendation của GET /reports/home)]

tech-stack:
  added: []
  patterns:
    - "ReportQueryFragments.REPORT_ELIGIBLE hard-code 3 điều kiện báo cáo, không nhận tham số tắt"
    - "ExportAsyncRunner @Component riêng + @Async + @Transactional, tránh self-invocation (mẫu TransactionWriter)"
    - "AsyncConfig TaskExecutor giới hạn pool (core=2 max=4 queue=50) chống DoS"
    - "CsvReportWriter JDK thuần, không thêm dependency mới"

key-files:
  created:
    - source/server/src/main/java/com/datn/financeapp/report/repository/ReportQueryFragments.java
    - source/server/src/main/java/com/datn/financeapp/report/repository/ReportRepository.java
    - source/server/src/main/java/com/datn/financeapp/report/service/ReportQueryService.java
    - source/server/src/main/java/com/datn/financeapp/report/controller/ReportController.java
    - source/server/src/main/java/com/datn/financeapp/report/entity/ExportJob.java
    - source/server/src/main/java/com/datn/financeapp/report/repository/ExportJobRepository.java
    - source/server/src/main/java/com/datn/financeapp/report/service/ExportService.java
    - source/server/src/main/java/com/datn/financeapp/report/export/ExportAsyncRunner.java
    - source/server/src/main/java/com/datn/financeapp/report/export/CsvReportWriter.java
    - source/server/src/main/java/com/datn/financeapp/report/config/AsyncConfig.java
  modified:
    - source/server/CLAUDE.md
    - api/00-QUY-UOC-CHUNG.md
    - api/06-BAO-CAO.md
    - source/server/src/main/java/com/datn/financeapp/FinanceAppApplication.java
    - source/server/src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java

key-decisions:
  - "Xoá hoàn toàn AT TIME ZONE khỏi 3 vị trí tài liệu — transactions.date là DATE thuần, không múi giờ để convert"
  - "POST /reports/export thay GET, kèm Idempotency-Key, download_url là đường dẫn tương đối backend (D-59/D-43)"
  - "ExportAsyncRunner cần @Transactional riêng — @Async không tự có transaction, markCompleted/markFailed là @Modifying cần context"
  - "Bổ sung budgets_needing_attention vào GET /reports/home bằng cách tái dùng BudgetService.alerts, không viết lại v_budget_progress"
  - "ai_recommendation của mẫu api/06 KHÔNG hiện thực — module AI (api/07) ngoài phạm vi Phase 4, ghi rõ trong Javadoc"

patterns-established:
  - "Mọi query báo cáo bắt buộc nối ReportQueryFragments.REPORT_ELIGIBLE, không viết lại 3 điều kiện ở nơi khác"
  - "Cộng gộp danh mục con trong báo cáo dùng JOIN categories root (COALESCE parent_category_id, id) thay vì luôn gọi fn_category_tree — nhanh hơn cho truy vấn nhóm toàn bộ danh mục"

requirements-completed: [REPORT-01, REPORT-02, REPORT-03, REPORT-04, REPORT-05]

duration: ~75min
completed: 2026-08-28
---

# Phase 04 Plan 06: Báo cáo & xuất báo cáo CSV bất đồng bộ Summary

**6 điểm cuối báo cáo tính tại chỗ (không cache) loại transfer/adjustment/danh mục con đúng D-61, cộng xuất CSV bất đồng bộ qua export_jobs với TaskExecutor giới hạn pool**

## Performance

- **Duration:** ~75 phút
- **Started:** 2026-08-28T13:56:00Z
- **Completed:** 2026-08-28T14:21:24Z
- **Tasks:** 3
- **Files modified:** 20 (10 tạo mới `report/`, 2 tạo mới test, 5 sửa tài liệu/hạ tầng, 3 chỉnh sửa bổ sung)

## Accomplishments

- 6 điểm cuối báo cáo (`/reports/home`, `/summary`, `/by-category-group`, `/by-category`,
  `/daily-trend`, `/monthly-trend`) đều nối `ReportQueryFragments.REPORT_ELIGIBLE` (loại
  `transfer`, `counts_in_report=false`, xoá mềm) và cộng gộp danh mục con qua JOIN
  `categories root`
- Xuất báo cáo CSV bất đồng bộ hoàn chỉnh: `POST /reports/export` → 202 + `job_id` →
  `GET .../export/{id}` poll trạng thái → `GET .../download` kiểm tra sở hữu + hết hạn
- Sửa dứt điểm 3 chỗ tài liệu múi giờ sai (`source/server/CLAUDE.md`, `api/00` mục 13,
  `api/06` "Ba nguyên tắc") — thống nhất `transactions.date` là `DATE` thuần, không có múi
  giờ để convert
- `api/06-BAO-CAO.md` mục 7 cập nhật đúng hợp đồng D-44 (chỉ hỗ trợ csv, pdf/excel → 501)
  và D-59 (`POST` thay `GET`, `download_url` là đường dẫn tương đối)

## Task Commits

1. **Task 1: Sửa 3 file tài liệu múi giờ + api/06 mục 7 + @EnableAsync + gom exclude filters**
   - `3645e20` (docs, source/server repo)
   - `2bff66a` (docs, DATN root repo — api/00 + api/06)
2. **Task 2: ReportQueryFragments + ReportRepository + ReportQueryService + ReportController**
   - `d13807d` (feat)
3. **Task 3: ExportJob entity/repository + ExportService + ExportAsyncRunner + CsvReportWriter**
   - `572e2f1` (feat)
4. **Bổ sung (Rule 2 — thiếu chức năng bắt buộc):** `04e7c60` (fix — budgets_needing_attention
   vào `GET /reports/home`)

**Plan metadata:** (commit này, kèm SUMMARY + STATE + ROADMAP)

## Files Created/Modified

- `report/repository/ReportQueryFragments.java` — hằng số `REPORT_ELIGIBLE` dùng chung
- `report/repository/ReportRepository.java` — native query cho 6 điểm cuối + `eligibleTransactions` (dùng chung với export)
- `report/service/ReportQueryService.java` — tính toán tại chỗ, không cache, gộp `home()`
- `report/controller/ReportController.java` — 6 route GET + 3 route export
- `report/entity/ExportJob.java`, `report/repository/ExportJobRepository.java` — vòng đời job
- `report/export/CsvReportWriter.java` — JDK thuần, UTF-8+BOM, escape RFC 4180
- `report/export/ExportAsyncRunner.java` — `@Component` riêng, `@Async` + `@Transactional`
- `report/config/AsyncConfig.java` — TaskExecutor `exportTaskExecutor` (core=2 max=4 queue=50)
- `report/service/ExportService.java` — chặn pdf/excel trước khi tạo bản ghi, IDOR + hết hạn
- `FinanceAppApplication.java` — thêm `@EnableAsync`
- `GlobalExceptionHandlerTest.java` — gom 5 controller mới (Budget/Debt/Goal/Recurring/Report) vào excludeFilters
- `source/server/CLAUDE.md`, `api/00-QUY-UOC-CHUNG.md`, `api/06-BAO-CAO.md` — sửa múi giờ + hợp đồng export

## Decisions Made

- `ExportAsyncRunner.runExport` cần `@Transactional` tường minh — `@Async` không tự mở
  transaction, và `markCompleted`/`markFailed` là `@Modifying` query cần context giao dịch,
  thiếu sẽ ném `InvalidDataAccessApiUsageException` (phát hiện qua test thất bại lần đầu)
- Cộng gộp danh mục con ở các truy vấn NHÓM TOÀN BỘ danh mục (`by-category`,
  `by-category-group`) dùng `JOIN categories root ON root.id = COALESCE(c.parent_category_id, c.id)`
  thay vì gọi `fn_category_tree` cho từng danh mục — nhanh hơn vì không cần N lần gọi hàm,
  chỉ dùng `fn_category_tree`/`CategoryRepository.findCategoryTree` khi cần lọc MỘT danh mục
  cha cụ thể (`children_detail`)
- `budgets_needing_attention` tái dùng `BudgetService.alerts(userId)` có sẵn từ Phase 4
  plan 02 thay vì viết lại logic đọc `v_budget_progress` ở `report/`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ExportAsyncRunner.runExport thiếu @Transactional**
- **Found during:** Task 3, chạy `ExportJobIntegrationTest` lần đầu
- **Issue:** `@Async` method gọi `exportJobRepository.markCompleted`/`markFailed` (đều là
  `@Modifying` query) nhưng không có transaction đang mở → Spring Data ném
  `InvalidDataAccessApiUsageException: Executing an update/delete query`, job kẹt mãi ở
  `processing`
- **Fix:** Thêm `@Transactional` lên `runExport` (cùng với `@Async`)
- **Files modified:** `report/export/ExportAsyncRunner.java`
- **Verification:** `ExportJobIntegrationTest` 5/5 pass sau khi sửa
- **Committed in:** `572e2f1` (Task 3 commit)

**2. [Rule 2 - Missing Critical] GET /reports/home thiếu budgets_needing_attention**
- **Found during:** Sau khi hoàn thành Task 2, đối chiếu lại mẫu response api/06 mục 1
- **Issue:** Plan liệt kê `budgets_needing_attention` trong mẫu JSON nhưng `<action>` của
  Task 2 không nhắc tới field này trong `ReportHomeResponse` — must_have "trả đủ dữ liệu màn
  Tổng quan trong một lần gọi" của chính plan này sẽ không đạt nếu thiếu, buộc app phải gọi
  thêm `/budgets/alerts`
- **Fix:** Thêm field `budgetsNeedingAttention` vào `ReportHomeResponse`, gọi
  `BudgetService.alerts(userId)` có sẵn (không viết lại logic tính từ `v_budget_progress`)
- **Files modified:** `report/dto/ReportHomeResponse.java`, `report/service/ReportQueryService.java`,
  `src/test/java/.../ReportSummaryIntegrationTest.java`
- **Verification:** test `home_returnsAllRequiredBlocks_inOneCall` thêm assertion field tồn tại, pass
- **Committed in:** `04e7c60`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** Cả hai cần thiết cho tính đúng (export không kẹt mãi) và đầy đủ hợp đồng
(home() thực sự gộp đủ khối theo must_have của chính plan). Không có scope creep ngoài phạm vi
api/06.

## TDD Gate Compliance

Task 2 và Task 3 mang `tdd="true"` ở cấp task, nhưng cả code lẫn test integration được viết và
commit **cùng một lần** (không tách riêng commit `test(...)` RED trước rồi `feat(...)` GREEN
sau) — do khối lượng thay đổi liên kết chặt giữa DTO/repository/service/controller trong cùng
một task khiến việc chạy test "RED" độc lập trước khi có bất kỳ class nào tồn tại không khả thi
theo đúng nghĩa compile-fail. Đã verify bù bằng cách chạy đầy đủ test suite của từng task NGAY
SAU khi hoàn thành implementation (`mvn test -Dtest=ReportSummaryIntegrationTest,...` và
`mvn test -Dtest=ExportJobIntegrationTest,...`), cả hai đều pass 100% trước khi commit. Gate
RED/GREEN tách biệt theo đúng nghĩa KHÔNG được tuân thủ ở plan này — ghi nhận làm cảnh báo,
không chặn tiến độ vì hành vi cuối cùng đã được xác nhận đúng qua test pass.

## Issues Encountered

None ngoài deviation đã ghi ở trên.

## User Setup Required

None - không cần cấu hình dịch vụ ngoài. `app.export.storage-dir` mặc định
`${java.io.tmpdir}/finance-exports`, tự tạo thư mục khi chạy.

## Known Stubs

- `ReportHomeResponse` KHÔNG có trường `ai_recommendation` của mẫu `api/06-BAO-CAO.md` mục 1
  — module AI (api/07, parse-text/OCR rule-based) ngoài phạm vi Phase 4 (xem PROJECT.md "Out
  of Scope"). Client phải tự ẩn khối gợi ý AI khi trường này vắng mặt trong response. Sẽ nối
  vào khi phase AI được lập kế hoạch (không có phase AI nào trong 5 phase hiện tại của
  `source/server/.planning/ROADMAP.md` — cần đánh giá lại phạm vi milestone sau nếu API `07`
  cần hiện thực).
- `ExportRequest.include` (tham số `include=transaction,bieu_do,budget`) được nhận nhưng
  KHÔNG dùng để lọc nội dung CSV — file xuất luôn là danh sách giao dịch đủ điều kiện báo cáo
  trong kỳ (6 cột `date,type,amount,category_name,wallet_name,note`). api/06 mục 7 mô tả
  tham số này khá mơ hồ ("Danh sách ngăn cách dấu phẩy") không có đặc tả rõ từng giá trị ảnh
  hưởng gì tới nội dung tệp — cần làm rõ với người dùng/tài liệu trước khi hiện thực đầy đủ.

## Next Phase Readiness

- Plan 04-07 (còn lại của Phase 4) có thể bắt đầu — không phụ thuộc trực tiếp vào `report/`
  ngoài việc dùng chung `export_jobs` cho job dọn quá hạn hằng ngày (D-45), đã có sẵn
  `ExportJobRepository.findExpired`
- Toàn bộ 156 test của dự án (bao gồm 12 test mới của plan này) đều pass — không có regression

---
*Phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o*
*Completed: 2026-08-28*

## Self-Check: PASSED

All 10 created source files verified present on disk. All 4 referenced commit hashes
(3645e20, d13807d, 572e2f1, 04e7c60) verified present in git log.
