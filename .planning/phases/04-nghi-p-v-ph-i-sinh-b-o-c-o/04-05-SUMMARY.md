---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
plan: 05
subsystem: api
tags: [spring-boot, jpa, postgresql, recurring, transaction-boundary, requires-new, scheduled-job]

# Dependency graph
requires:
  - phase: 03-giao-dich
    provides: TransactionWriter (D-31), mẫu 2-bean của TransactionBulkService (D-34/D-35a), mẫu quyền D-27
  - phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
    plan: 01
    provides: mẫu package feature-first của Phase 4
provides:
  - Module recurring/ hoàn chỉnh — 7 endpoint giao dịch định kỳ (RECUR-01..03)
  - RecurringRunnerService.runDueRecurring() sẵn sàng cho @Scheduled ở Plan 07 (D-57)
  - RecurringDateCalculator — logic ngày 29/30/31 giữ nguyên ngày gốc, tái dùng được
  - Bằng chứng test cho ranh giới transaction lồng nhau khi một phần tử lỗi
affects: [04-06-bao-cao, 04-07-tac-vu-nen]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Ba tầng transaction lồng nhau: quét (không @Transactional) -> khoản (@Transactional) -> kỳ (REQUIRES_NEW). Bắt buộc vì PostgreSQL huỷ nguyên transaction khi vi phạm ràng buộc"
    - "flush() tường minh sau khi ghi để vi phạm ràng buộc nổ ra ĐÚNG chỗ có try/catch, thay vì lúc commit"
    - "Tên cột trùng từ khoá SQL (interval) phải bọc nháy kép trong @Column"

key-files:
  created:
    - source/server/src/main/java/com/datn/financeapp/recurring/util/RecurringDateCalculator.java
    - source/server/src/main/java/com/datn/financeapp/recurring/entity/RecurringTransaction.java
    - source/server/src/main/java/com/datn/financeapp/recurring/repository/RecurringTransactionRepository.java
    - source/server/src/main/java/com/datn/financeapp/recurring/service/RecurringService.java
    - source/server/src/main/java/com/datn/financeapp/recurring/service/RecurringRunnerService.java
    - source/server/src/main/java/com/datn/financeapp/recurring/service/RecurringPeriodWriter.java
    - source/server/src/main/java/com/datn/financeapp/recurring/service/RecurringSinglePeriodWriter.java
    - source/server/src/main/java/com/datn/financeapp/recurring/controller/RecurringController.java
    - source/server/src/main/java/com/datn/financeapp/recurring/dto/ (7 DTO)
    - source/server/src/test/java/com/datn/financeapp/recurring/RecurringDateCalculatorTest.java
    - source/server/src/test/java/com/datn/financeapp/recurring/RecurringCrudIntegrationTest.java
    - source/server/src/test/java/com/datn/financeapp/recurring/RecurringRunnerIntegrationTest.java
  modified:
    - source/server/src/main/java/com/datn/financeapp/transaction/repository/TransactionRepository.java
    - source/server/src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java
    - api/09-DINH-KY-MUC-TIEU.md (repo DATN — sửa điều kiện quét)

key-decisions:
  - "Thêm bean thứ BA (RecurringSinglePeriodWriter, REQUIRES_NEW) ngoài mẫu 2-bean của plan: PostgreSQL huỷ nguyên transaction khi vi phạm ràng buộc nên catch-rồi-đi-tiếp trong CÙNG transaction là bất khả thi"
  - "Sửa findDue so end_date với next_run_date thay vì hôm nay — điều kiện của api/09 bỏ sót các kỳ chưa ghi nằm trước một end_date đã qua"
  - "DELETE là xoá CỨNG vì bảng không có is_deleted; an toàn nhờ fk_txn_recurring ON DELETE SET NULL"
  - "start_date KHÔNG cho sửa qua PATCH — nó là mốc gốc quyết định ngày trong tháng của mọi kỳ tương lai"
  - "last_run_date ghi kỳ CUỐI thực sự xử lý, không phải LocalDate.now() — mốc đối chiếu khi nghi tác vụ chạy sót"
  - "Thêm RecurringController vào GlobalExceptionHandlerTest ngay tại plan này thay vì gom về Plan 06 (không chạy song song nên không có rủi ro xung đột merge)"

patterns-established:
  - "Khuôn tách transaction theo phần tử khi một phần tử lỗi không được cuốn phần tử khác — dùng lại được cho mọi tác vụ nền của Plan 07"

requirements-completed: [RECUR-01, RECUR-02, RECUR-03]

# Metrics
duration: ~40 phút
completed: 2026-08-27
---

# Phase 4 Plan 5: Giao dịch định kỳ Summary

Module `recurring/` đầy đủ với cấu trúc ba tầng transaction lồng nhau cho phép một kỳ hoặc một khoản lỗi mà không cuốn theo phần còn lại, cộng logic ngày 29/30/31 giữ nguyên ngày gốc qua nhiều kỳ liên tiếp.

## Đã làm gì

**Task 1 — nền tảng tính ngày** (commit `3350a3c`). `RecurringDateCalculator` là utility thuần Java, mỗi lần tính đều ép lại ngày trong tháng theo `start_date` GỐC. Đây là điểm cốt lõi của D-50: `LocalDate.plusMonths` của JDK tự làm tròn xuống ngày cuối tháng, nhưng nó làm tròn dựa trên con trỏ hiện tại vốn có thể đã bị làm tròn ở kỳ trước — cộng dồn liên tiếp thì ngày "trôi" xuống 28 và ở lại đó vĩnh viễn. Kèm entity (chú ý cột `interval` trùng từ khoá SQL) và repository với điều kiện quyền nằm trong SQL.

**Task 2 — nghiệp vụ và tác vụ quét** (commit `767c9ac`). 7 endpoint theo api/09 Phần A, cộng ba bean tách vai trò rõ ràng, cộng 12 test tích hợp.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Thêm bean thứ ba `RecurringSinglePeriodWriter` với `REQUIRES_NEW`**
- **Found during:** Task 2, khi test `runDueRecurring_calledTwice_doesNotDuplicate` chạy
- **Issue:** Mẫu 2-bean trong plan bắt `DataIntegrityViolationException` ngay trong vòng lặp catch-up rồi "đi tiếp" — nhưng PostgreSQL huỷ NGUYÊN transaction khi có câu lệnh vi phạm ràng buộc (`current transaction is aborted`). Mọi câu sau đó trong cùng transaction đều bị từ chối, nên việc "bỏ qua kỳ trùng rồi xử lý kỳ tiếp theo" là bất khả thi nếu các kỳ chung transaction.
- **Fix:** Tách mỗi kỳ vào một transaction con `REQUIRES_NEW` ở bean riêng. Kỳ trùng rollback gọn trong phạm vi nó, transaction cha còn nguyên vẹn để đẩy `next_run_date`. Vẫn giữ đúng tinh thần D-51 (kỳ 5 lỗi thì 4 kỳ trước vẫn giữ), thậm chí chặt hơn mẫu trong plan.
- **Files modified:** `RecurringPeriodWriter.java`, `RecurringSinglePeriodWriter.java` (mới)
- **Commit:** `767c9ac`

**2. [Rule 1 - Bug] `findDue` so `end_date` với `next_run_date` thay vì hôm nay**
- **Found during:** Task 2, test `runDueRecurring_endDateInsideMissedPeriods_stopsAtEndDate` không sinh giao dịch nào
- **Issue:** api/09 mục A4 ghi điều kiện `end_date >= hôm nay`. Điều kiện đó bỏ sót ca có thật: khoản kết thúc tháng trước nhưng người dùng vắng mặt từ trước đó nữa, vẫn còn vài kỳ **chưa ghi nằm trước `end_date`**. Lọc theo hôm nay thì những kỳ đó không bao giờ được sinh — số dư thiếu vĩnh viễn mà con số vẫn "trông hợp lý", đúng kiểu lỗi D-50 cảnh báo.
- **Fix:** Đổi sang `end_date >= next_run_date`. Vòng lặp bắt kịp vẫn cắt đúng tại `end_date` nên không kỳ nào vượt quá hạn.
- **Files modified:** `RecurringTransactionRepository.java`, và `api/09-DINH-KY-MUC-TIEU.md` ở repo DATN (commit `c6eceb7`) theo quy tắc "code lệch tài liệu thì sửa tài liệu trong cùng lần thay đổi"
- **Commit:** `767c9ac`

**3. [Rule 3 - Blocking] `flush()` tường minh sau mỗi lần ghi**
- **Found during:** Task 2
- **Issue:** Hibernate có quyền hoãn câu INSERT tới lúc commit. Khi đó vi phạm `uq_txn_recurring_date` nổ ra NGOÀI khối `try`, biến lỗi 409 dự kiến thành 500.
- **Fix:** Gọi `transactionRepository.flush()` ngay sau `transactionWriter.write(...)`, cùng kỹ thuật đã dùng ở `DebtService`/`GoalService`.
- **Commit:** `767c9ac`

**4. [Rule 3 - Blocking] `CAST(:param AS ...)` trong bộ lọc tuỳ chọn của `findAllForUser`**
- **Issue:** Plan viết `:isEnabled IS NULL` trần; PostgreSQL không suy được kiểu tham số ở vế `IS NULL`.
- **Fix:** Bọc `CAST(...)` theo đúng khuôn `SavingsGoalRepository.findAllForUser` đã chạy được.
- **Commit:** `767c9ac`

**5. [Rule 3 - Blocking] Thêm `RecurringController` vào `excludeFilters` của `GlobalExceptionHandlerTest`**
- **Issue:** Controller mới bị `@WebMvcTest` component-scan vào slice context, kéo theo `RecurringService` không tồn tại ở đó — 3 test của lớp này lỗi.
- **Fix:** Loại trừ tường minh ngay tại plan này. Plan viết "gom về Plan 06 để tránh xung đột merge giữa 4 plan wave 2 chạy song song", nhưng thực tế các plan chạy TUẦN TỰ trên cùng nhánh nên không có rủi ro đó — hoãn lại chỉ để suite đỏ vô ích.
- **Commit:** `767c9ac`

### Sai lệch trong text của plan (không phải lỗi code)

- Đoạn code mẫu ở `<interfaces>` dùng `TransactionWriteCommand` thiếu tham số `countsInReport` so với record thật ở Phase 3. Đã dùng chữ ký thật.
- Hai test trong plan đặt mốc ngày cứng (`2026-01-31` → kỳ vọng đúng 3 kỳ). Hôm nay là 2026-08-27 nên thực tế có 7 kỳ. Đã viết lại test suy mốc từ `LocalDate.now()` để không mục theo thời gian, và kỳ vọng được dựng độc lập bằng chính quy tắc của đặc tả thay vì gọi lại code sản phẩm.

## Điểm cần biết cho plan sau

**Plan 07 (tác vụ nền):** `RecurringRunnerService.runDueRecurring()` đã sẵn sàng, chỉ cần bọc `@Scheduled(zone = "Asia/Ho_Chi_Minh")` trong package `scheduler/`. Không thêm `@Transactional` ở tầng gọi — sẽ phá vỡ D-52.

**Khuôn tách transaction dùng lại được:** ba tầng (quét không transaction → phần tử có transaction → thao tác con `REQUIRES_NEW`) là mẫu tổng quát cho mọi tác vụ nền cần "một phần tử lỗi không cuốn phần tử khác". Bốn job của Plan 07 nên theo đúng khuôn này.

**Cảnh báo về `@Transactional` lồng nhau:** khi transaction con `REQUIRES_NEW` ném lỗi ra ngoài, transaction cha bị đánh dấu rollback-only và ném `UnexpectedRollbackException` ở ranh giới của nó. Đây là hành vi ĐÚNG (lịch của khoản hỏng không được nhích lên) và `RecurringRunnerService` bắt trọn — nhưng nếu Plan 07 thấy exception này trong log thì đó là D-52 đang hoạt động, không phải lỗi.

## Verification

- `mvn test -Dtest=RecurringDateCalculatorTest,RecurringCrudIntegrationTest,RecurringRunnerIntegrationTest` — 18/18 pass
- `mvn test` full suite — **147/147 pass** (129 trước plan này, +18 mới)

## Known Stubs

Không có. Toàn bộ 7 endpoint đều nối dữ liệu thật, không có giá trị giả nào chảy ra response.

## Self-Check: PASSED
