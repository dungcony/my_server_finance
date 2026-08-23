---
phase: 03-giao-dich
plan: 03
subsystem: transaction
tags: [spring-boot, jpa, postgresql, testcontainers, fn-category-tree, reporting]

# Dependency graph
requires:
  - phase: 03-giao-dich (plan 01)
    provides: "Transaction entity, TransactionRepository, TransactionWriter"
  - phase: 03-giao-dich (plan 02)
    provides: "TransactionService (create/update/delete/duplicate), TransactionController, TransactionResponse"
provides:
  - "GET /transactions — lọc đủ tham số, phân trang, summary loại trừ type='transfer'"
  - "GET /transactions/by-date — gom theo ngày, nhãn Hôm nay/Hôm qua theo giờ Việt Nam, day_total loại transfer"
  - "GET /transactions/{id} — chi tiết, không thuộc quyền trả 404"
  - "TransactionService.resolveCategoryTree() — điểm gọi fn_category_tree DUY NHẤT của toàn hệ thống"
  - "TransactionFilterParams — bộ tham số lọc dùng chung cho list() và listByDate()"
  - "TransactionListItemResponse, TransactionListResponse, TransactionByDateResponse, TransactionDetailResponse, TransactionSummaryDto"
affects: [03-giao-dich (plan 04), 04-ngan-sach-bao-cao (báo cáo Phase 4 sẽ dùng lại resolveCategoryTree và nguyên tắc loại transfer)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "fn_category_tree bọc một lần trong CategoryRepository.findCategoryTree(), TransactionService gọi qua đúng một private method resolveCategoryTree() — cả 3 query (search/countSearch/summary) nhận cùng mảng UUID[] categoryTree, không nơi nào tự viết category_id = :categoryId"
    - "Câu summary hard-code t.type <> 'transfer' ngay trong SQL, KHÔNG phụ thuộc tham số includeTransfers — transfer vẫn hiện trong danh sách nhưng không bao giờ vào tổng thu/chi (CLAUDE.md §2)"
    - "listByDate() gom nhóm bằng Collectors/LinkedHashMap ở tầng Java thay vì GROUP BY SQL — danh sách đã đủ nhỏ sau khi lọc theo kỳ, tránh câu SQL lồng phức tạp"
    - "Toàn bộ tham số lọc bind qua @Param của JPA native query, search dùng CONCAT('%',:search,'%') bên trong SQL — không nối chuỗi Java (T-03-08)"
    - "Điều kiện t.user_id = :userId nằm ngay trong câu query, không lọc ở tầng Java (T-03-09); detail() không tìm thấy trả 404 chứ không phải 403"

key-files:
  created:
    - src/main/java/com/datn/financeapp/transaction/dto/TransactionListItemResponse.java
    - src/main/java/com/datn/financeapp/transaction/dto/TransactionListResponse.java
    - src/main/java/com/datn/financeapp/transaction/dto/TransactionByDateResponse.java
    - src/main/java/com/datn/financeapp/transaction/dto/TransactionDetailResponse.java
    - src/main/java/com/datn/financeapp/transaction/dto/TransactionFilterParams.java
    - src/main/java/com/datn/financeapp/transaction/dto/TransactionSummaryDto.java
    - src/test/java/com/datn/financeapp/transaction/TransactionCategoryTreeFilterIntegrationTest.java
    - src/test/java/com/datn/financeapp/transaction/TransactionListQueryIntegrationTest.java
  modified:
    - src/main/java/com/datn/financeapp/transaction/repository/TransactionRepository.java
    - src/main/java/com/datn/financeapp/transaction/service/TransactionService.java
    - src/main/java/com/datn/financeapp/transaction/controller/TransactionController.java

key-decisions:
  - "resolveDateRange() đổi từ LocalDate.now() trần trụi sang LocalDate.now(VIETNAM_ZONE) — trước đó lệch với dayLabel() vốn đã dùng giờ Việt Nam, gây rủi ro kỳ báo cáo và nhãn ngày tính theo hai mốc khác nhau khi máy chạy ở múi giờ khác"
  - "summary loại transfer ngay trong SQL thay vì lọc ở Java — đảm bảo mọi lối gọi đều đúng, không phụ thuộc người viết code sau có nhớ lọc hay không"

requirements-completed: [TXN-01, TXN-02, TXN-08]

# Metrics
duration: 55min
completed: 2026-08-24
---

# Phase 3 Plan 3: Ba endpoint đọc giao dịch Summary

**GET /transactions (lọc + phân trang + summary), GET /transactions/by-date (gom theo ngày), GET /transactions/{id} (chi tiết) — cộng gộp danh mục con vào cha qua đúng một điểm gọi `fn_category_tree`, và loại `type='transfer'` khỏi mọi con số thu-chi.**

## Performance

- **Duration:** ~55 phút (gồm hai lần agent thực thi bị ngắt do lỗi API, orchestrator tiếp quản phần cuối)
- **Tasks:** 2
- **Files modified:** 11 (8 tạo mới, 3 sửa)

## Accomplishments

- `GET /transactions`: lọc theo `from_date`/`to_date`/`period`/`type`/`wallet_id`/`category_id`/`source`/`counts_in_report`/`search`/`min_amount`/`max_amount`/`include_transfers`, phân trang chuẩn `api/00-QUY-UOC-CHUNG.md`, kèm khối `summary`
- `GET /transactions/by-date`: gom giao dịch theo ngày, `day_label` tính theo `Asia/Ho_Chi_Minh` (`"Hôm nay"`/`"Hôm qua"`/thứ trong tuần tiếng Việt), `month_year` dạng `"tháng M yyyy"`, `day_total` loại trừ transfer, không phân trang theo đặc tả
- `GET /transactions/{id}`: chi tiết giao dịch; bản ghi của người khác trả `404` chứ không phải `403` — không lộ việc bản ghi có tồn tại (T-03-09)
- **TXN-08 — cộng gộp danh mục con:** `resolveCategoryTree()` là điểm gọi `fn_category_tree` duy nhất trong `TransactionService`; cả `search`, `countSearch` và `summary` đều nhận cùng một mảng `UUID[]`. Không nơi nào tự viết `category_id = :categoryId`
- **CLAUDE.md §2 — loại transfer:** câu `summary` hard-code `t.type <> 'transfer'` bất kể tham số `include_transfers`; giao dịch chuyển tiền vẫn hiện trong danh sách nhưng không bao giờ cộng vào `total_income`/`total_expense`
- Sửa điểm lệch múi giờ: `resolveDateRange()` trước dùng `LocalDate.now()` trần trụi trong khi `dayLabel()` dùng giờ Việt Nam — nay thống nhất `VIETNAM_ZONE`
- 6 test tích hợp mới qua HTTP thật (Testcontainers PostgreSQL), tổng **95/95 test toàn dự án PASS**, không regression

## Task Commits

1. **Task 1 + Task 2** — `01e8222` (feat): toàn bộ DTO, repository query, service, controller và 2 lớp test. Hai task gộp một commit vì agent thực thi bị ngắt giữa chừng hai lần bởi lỗi API; orchestrator kiểm tra lại working tree, viết nốt `TransactionListQueryIntegrationTest` còn thiếu, chạy full test rồi commit một lần.

## Files Created/Modified

- `transaction/dto/TransactionListItemResponse.java` - Phần tử danh sách giao dịch
- `transaction/dto/TransactionListResponse.java` - Bao ngoài: data + pagination + summary
- `transaction/dto/TransactionByDateResponse.java` - period_summary + days[] (mỗi day có transaction[])
- `transaction/dto/TransactionDetailResponse.java` - Chi tiết một giao dịch
- `transaction/dto/TransactionFilterParams.java` - Bộ tham số lọc dùng chung list()/listByDate()
- `transaction/dto/TransactionSummaryDto.java` - total_income/total_expense/difference
- `transaction/repository/TransactionRepository.java` - +search/countSearch/summary (native query, bind tham số)
- `transaction/service/TransactionService.java` - +list()/detail()/listByDate()/resolveCategoryTree()/dayLabel()/monthYearLabel()/buildPeriodLabel(), sửa resolveDateRange() sang giờ Việt Nam
- `transaction/controller/TransactionController.java` - +3 endpoint GET
- `TransactionCategoryTreeFilterIntegrationTest.java` - 2 test: lọc cha gồm giao dịch con, summary loại transfer
- `TransactionListQueryIntegrationTest.java` - 4 test: gom theo ngày + nhãn, day_total loại transfer, lọc/phân trang, detail 404 với người khác

## Decisions Made

- Loại transfer khỏi summary bằng điều kiện cứng trong SQL thay vì lọc ở tầng Java — mọi lối gọi đều đúng, không phụ thuộc người viết code sau có nhớ lọc hay không.
- Gom nhóm theo ngày ở tầng Java (`LinkedHashMap` giữ thứ tự giảm dần) thay vì `GROUP BY` SQL — dữ liệu đã đủ nhỏ sau khi lọc theo kỳ, tránh câu SQL lồng khó đọc.
- Thống nhất `VIETNAM_ZONE` cho cả `resolveDateRange()` và `dayLabel()` — đây là sai lệch tiềm ẩn phát hiện khi rà lại, chưa gây lỗi test nào nhưng sẽ sai trên CI chạy múi giờ khác.

## Deviations from Plan

Plan chia làm 2 task (Task 1: list + detail, Task 2: by-date) với commit riêng. Thực tế gộp thành một commit `01e8222` vì agent thực thi bị ngắt hai lần bởi lỗi API/stall khi đang sửa dở `TransactionService`; orchestrator tiếp quản, kiểm tra lại toàn bộ working tree, bổ sung phần còn thiếu rồi commit một lần sau khi full test xanh. Nội dung và tiêu chí nghiệm thu của cả hai task đều đạt đủ.

## Issues Encountered

- Agent thực thi plan này bị ngắt hai lần (lỗi API "response stopped arriving", sau đó stall watchdog 600s). Không mất việc vì công việc dở nằm nguyên trong working tree; orchestrator đọc lại rồi làm nốt.
- Phát hiện `resolveDateRange()` dùng `LocalDate.now()` không có múi giờ — đã sửa (xem Decisions).

## User Setup Required

None.

## Next Phase Readiness

- Plan 03-04 (`POST /transactions/bulk`) dùng lại `TransactionService.validateShape()` (package-private từ plan 03-02) và `TransactionWriter`.
- Phase 4 (ngân sách, báo cáo) dùng lại `resolveCategoryTree()` và nguyên tắc loại transfer đã cố định ở tầng SQL.
- Không có blocker.

---
*Phase: 03-giao-dich*
*Completed: 2026-08-24*

## Self-Check: PASSED

- FOUND: src/main/java/com/datn/financeapp/transaction/dto/TransactionListItemResponse.java
- FOUND: src/main/java/com/datn/financeapp/transaction/dto/TransactionByDateResponse.java
- FOUND: src/test/java/com/datn/financeapp/transaction/TransactionListQueryIntegrationTest.java
- COMMIT: 01e8222 feat(03-03): ba endpoint đọc giao dịch, cộng gộp danh mục con qua fn_category_tree
- TEST: 95/95 PASS (mvn test, BUILD SUCCESS)
- CRITERIA: grep "findCategoryTree" TransactionService.java → đúng 1 điểm gọi (dòng 492)
- CRITERIA: grep "Asia/Ho_Chi_Minh" TransactionService.java → có kết quả
