---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
plan: 02
subsystem: api
tags: [spring-boot, jpa, postgresql, budget, spring-events, native-query, testcontainers]

# Dependency graph
requires:
  - phase: 03-giao-dich
    provides: TransactionWriter (D-31), khuôn Testcontainers, mẫu quyền D-27
  - phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
    plan: 01
    provides: bảng notifications (V9), NotificationRepository.insertBudgetAlertIfNotExists
provides:
  - TransactionRecordedEvent — sự kiện dùng chung cho MỌI module phái sinh Phase 4 nghe sau khi ghi giao dịch
  - 8 endpoint /budgets (BUDGET-01..06) trên nền v_budget_progress
  - BudgetAlertListener — cảnh báo ngân sách tự sinh sau commit (D-41)
  - BudgetProgressRepository — mẫu map view chỉ đọc qua projection, không dùng @Entity
affects: [04-03-so-no, 04-04-muc-tieu, 04-05-dinh-ky, 04-06-bao-cao, 04-07-tac-vu-nen]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Spring Data JPA: Repository<T, ID> bắt buộc T là entity ĐƯỢC QUẢN LÝ; projection là kiểu TRẢ VỀ của query, không phải kiểu domain"
    - "@TransactionalEventListener(AFTER_COMMIT) phải kèm @Transactional(REQUIRES_NEW) khi handler có ghi CSDL — transaction gốc đã đóng"
    - "Map view chỉ đọc bằng interface projection + native query, không khai @Entity cho view"

key-files:
  created:
    - source/server/src/main/java/com/datn/financeapp/transaction/event/TransactionRecordedEvent.java
    - source/server/src/main/java/com/datn/financeapp/budget/entity/Budget.java
    - source/server/src/main/java/com/datn/financeapp/budget/repository/BudgetRepository.java
    - source/server/src/main/java/com/datn/financeapp/budget/repository/BudgetProgressRepository.java
    - source/server/src/main/java/com/datn/financeapp/budget/service/BudgetService.java
    - source/server/src/main/java/com/datn/financeapp/budget/service/BudgetAlertListener.java
    - source/server/src/main/java/com/datn/financeapp/budget/controller/BudgetController.java
    - source/server/src/main/java/com/datn/financeapp/budget/dto/BudgetListItemResponse.java
    - source/server/src/main/java/com/datn/financeapp/budget/dto/CreateBudgetRequest.java
    - source/server/src/main/java/com/datn/financeapp/budget/dto/UpdateBudgetRequest.java
    - source/server/src/main/java/com/datn/financeapp/budget/dto/BudgetSummaryResponse.java
    - source/server/src/main/java/com/datn/financeapp/budget/dto/BudgetSuggestionResponse.java
    - source/server/src/main/java/com/datn/financeapp/budget/dto/BudgetAlertResponse.java
    - source/server/src/test/java/com/datn/financeapp/budget/BudgetCrudIntegrationTest.java
    - source/server/src/test/java/com/datn/financeapp/budget/BudgetPrivacyIntegrationTest.java
    - source/server/src/test/java/com/datn/financeapp/budget/BudgetAlertListenerIntegrationTest.java
  modified:
    - source/server/src/main/java/com/datn/financeapp/transaction/service/TransactionWriter.java
    - source/server/src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java

key-decisions:
  - "BudgetProgressRepository khai Repository<Budget, UUID> thay vì Repository<Projection, UUID> — Spring Data yêu cầu kiểu domain là entity được quản lý, chữ ký gợi ý trong PLAN không khởi tạo được context"
  - "BudgetAlertListener bắt buộc có @Transactional(REQUIRES_NEW) — AFTER_COMMIT chạy khi transaction gốc đã đóng, thiếu annotation thì INSERT ném TransactionRequiredException"
  - "findActiveByUserAndCategoryInTree so sánh theo chiều NGƯỢC (:categoryId IN fn_category_tree(budgets.category_id)) và thêm điều kiện ngày trong kỳ"
  - "BudgetAlertResponse.suggestion trả null — api/05 minh hoạ action move_limit nhưng không đặc tả nào có endpoint đó, không bịa API"
  - "Thêm BudgetController vào excludeFilters ngay tại plan này thay vì hoãn tới Plan 06 — thực thi tuần tự trên develop, không có worktree song song nên lý do hoãn (xung đột merge) không còn"

patterns-established:
  - "TransactionRecordedEvent là điểm nối một chiều: mọi module phái sinh sau này nghe sự kiện, không module nào gọi ngược vào transaction/"
  - "Module đọc view tính sẵn: backend chỉ map, mọi quy tắc nghiệp vụ (cộng gộp danh mục con, loại transfer, phạm vi quyền) nằm một chỗ duy nhất trong SQL của view"

requirements-completed: [BUDGET-01, BUDGET-02, BUDGET-03, BUDGET-04, BUDGET-05, BUDGET-06, BUDGET-08]

# Metrics
duration: ~50 phút
completed: 2026-08-27
---

# Phase 4 Plan 02: Ngân sách Summary

**Module ngân sách đầy đủ 8 endpoint đọc thẳng từ view `v_budget_progress` (không tính lại một dòng logic nào ở Java), cộng cơ chế `TransactionRecordedEvent` → `@TransactionalEventListener(AFTER_COMMIT)` tự sinh cảnh báo vào `notifications` — điểm nối đầu tiên của nhóm module phái sinh vào hạ tầng dùng chung Phase 1-3.**

## Performance

- **Duration:** ~50 phút
- **Tasks:** 3/3 hoàn thành
- **Files modified:** 18 (16 tạo mới, 2 sửa)
- **Test:** 104 → 112 (thêm 8 test case mới, 0 regression)

## Accomplishments

- `TransactionWriter` bắn `TransactionRecordedEvent` sau mọi lần ghi giao dịch (CRUD người dùng, bulk, transfer, adjustment, và mọi module Phase 4 sau này) mà **không đổi chữ ký public** — toàn bộ 104 test Phase 1-3 xanh ngay lần chạy đầu
- 8 endpoint `/budgets` đúng bảng "Danh sách điểm cuối" của `api/05-NGAN-SACH.md`: danh sách kèm tiến độ, tổng quan, chi tiết, tạo (có `@Idempotent`), sửa, xoá, gợi ý hạn mức, cảnh báo
- Chặn ngân sách trùng bằng ràng buộc CSDL `ex_bud_no_overlap` (EXCLUDE gist) thay vì SELECT kiểm tra trước — không có khe hở race condition giữa hai request song song
- Cảnh báo ngân sách tự sinh sau khi giao dịch commit, **cộng gộp danh mục con đúng chiều** (chi vào "Cà phê" kích hoạt ngân sách đặt ở "Ăn uống"), chống trùng bulk 50 dòng bằng UNIQUE tầng CSDL
- **BUDGET-08 (ưu tiên cao nhất D-58) xanh** — chốt vĩnh viễn lỗ hổng `v_budget_progress` đã vá ở V6, kèm bước đối chứng chứng minh test không "xanh giả"

## Task Commits

1. **Task 1: TransactionRecordedEvent + TransactionWriter publish event** — `9fedec3`
2. **Task 2: Budget entity/repository/service/controller + CRUD test** — `fa5e64f`
3. **Task 3: BudgetAlertListener + test riêng tư BUDGET-08 + test event bulk** — `7e2f36e`

## Files Created/Modified

**Tạo mới:**
- `transaction/event/TransactionRecordedEvent.java` — record 7 trường nguyên thuỷ, không mang entity JPA (tránh truy cập entity đã detach sau khi transaction đóng)
- `budget/entity/Budget.java` — ánh xạ V3 thật: **không có `spent_amount`, không có `is_deleted`**
- `budget/repository/BudgetRepository.java` — 4 method, điều kiện quyền `user_id = :currentUser` trong SQL; `sumExpenseInPeriod` dùng `fn_category_tree` cho BUDGET-05
- `budget/repository/BudgetProgressRepository.java` — map `v_budget_progress` qua interface projection, 3 method native query
- `budget/service/BudgetService.java` — 8 nghiệp vụ, dự báo `projected_depletion_date` theo công thức api/05 mục 2.3
- `budget/service/BudgetAlertListener.java` — `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`
- `budget/controller/BudgetController.java` — 8 route, thứ tự khai báo đặt route tĩnh trước `/{id}`
- `budget/dto/*.java` — 6 DTO record
- `budget/BudgetCrudIntegrationTest.java` (4 case), `BudgetPrivacyIntegrationTest.java` (1 case), `BudgetAlertListenerIntegrationTest.java` (3 case)

**Sửa:**
- `transaction/service/TransactionWriter.java` — thêm `ApplicationEventPublisher`, publish trước `return`
- `common/exception/GlobalExceptionHandlerTest.java` — thêm `BudgetController` vào `excludeFilters`

## Decisions Made

- **`BudgetProgressRepository extends Repository<Budget, UUID>`** thay vì `Repository<Projection, UUID>` như PLAN gợi ý. Spring Data JPA bắt buộc kiểu domain phải nằm trong metamodel; projection là kiểu **trả về** của từng query, không phải kiểu domain. Khai `Budget` là hợp lý nhất — view chính là bảng `budgets` kèm cột tính sẵn — và interface không kế thừa `JpaRepository` nên không lộ method ghi nào.
- **`@Transactional(REQUIRES_NEW)` trên listener là bắt buộc**, không phải trang trí. `AFTER_COMMIT` chạy khi transaction gốc đã commit và đóng, không còn transaction nào để `@Modifying` INSERT bám vào. Transaction mới cũng đúng ngữ nghĩa: cảnh báo lỗi thì giao dịch của người dùng vẫn giữ nguyên.
- **Truy vấn tìm ngân sách theo chiều ngược:** `:categoryId IN (SELECT * FROM fn_category_tree(category_id))` — tìm ngân sách nào có cây danh mục CHỨA danh mục của giao dịch, không phải so bằng nhau. Thêm điều kiện `:transactionDate BETWEEN start_date AND end_date` để không cảnh báo ngân sách kỳ khác (giao dịch ngoài kỳ không làm `spent_amount` của kỳ đó đổi, cảnh báo lúc ấy là nhiễu).
- **`BudgetAlertResponse.suggestion` trả `null`.** `api/05` mục 7 minh hoạ gợi ý kèm `action: "move_limit"` nhưng **không đặc tả nào có endpoint chuyển hạn mức giữa hai ngân sách** — theo quy tắc "không tự bịa API", để `null` cho tới khi hợp đồng được bổ sung.
- **`endOfPeriod` dùng `plusMonths(1).minusDays(1)`** thay vì "ngày cuối tháng" cứng, để kỳ bắt đầu giữa tháng (client tự chọn `start_date`) vẫn dài đúng một tháng thay vì bị cụt. Với `start_date` mặc định (ngày 1) hai cách cho kết quả giống hệt nhau.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Chữ ký `BudgetProgressRepository` trong PLAN không khởi tạo được Spring context**
- **Found during:** Task 2 — chạy `BudgetCrudIntegrationTest` lần đầu
- **Issue:** PLAN gợi ý `Repository<BudgetProgressProjection, UUID>`. Spring Data JPA ném `IllegalArgumentException: Not a managed type: interface ...BudgetProgressProjection` ngay lúc tạo bean, làm sập toàn bộ ApplicationContext (cả 4 test case đỏ vì cùng một nguyên nhân)
- **Fix:** Đổi kiểu domain sang entity được quản lý `Budget`; projection giữ nguyên vai trò kiểu trả về của từng query. Ghi rõ lý do trong Javadoc để plan sau không lặp lại
- **Files modified:** `budget/repository/BudgetProgressRepository.java`
- **Verification:** `mvn test -Dtest=BudgetCrudIntegrationTest` xanh 4/4
- **Commit:** `fa5e64f`

**2. [Rule 3 - Blocking] Listener AFTER_COMMIT thiếu transaction để INSERT**
- **Found during:** Task 3 — pha GREEN
- **Issue:** Handler `@TransactionalEventListener(AFTER_COMMIT)` chạy sau khi transaction gốc đã đóng; `@Modifying` INSERT không có transaction nào để bám vào
- **Fix:** Thêm `@Transactional(propagation = REQUIRES_NEW)` trên method handler
- **Files modified:** `budget/service/BudgetAlertListener.java`
- **Verification:** 3/3 test `BudgetAlertListenerIntegrationTest` xanh
- **Commit:** `7e2f36e`

**3. [Rule 3 - Blocking] `GlobalExceptionHandlerTest` đỏ do component-scan `BudgetController`**
- **Found during:** Task 3 — chạy full suite sau khi thêm listener
- **Issue:** Đúng cái bẫy `@WebMvcTest` slice mà `04-CONTEXT.md` cảnh báo "đã dính 3 lần, dễ dính lần thứ tư". PLAN Task 2 chỉ thị **hoãn** việc thêm `excludeFilters` tới Plan 06 để tránh xung đột merge giữa 4 plan wave 2 chạy song song trên worktree riêng
- **Fix:** Kiểm tra `git worktree list` — plan này thực thi **tuần tự trên `develop`, chỉ có một worktree duy nhất**, không có plan nào chạy song song. Lý do hoãn không còn hiệu lực, trong khi acceptance criteria của Task 3 yêu cầu "full suite vẫn xanh". Thêm `BudgetController.class` vào `excludeFilters` ngay tại plan này
- **Files modified:** `common/exception/GlobalExceptionHandlerTest.java`
- **Verification:** `mvn test` full suite xanh 112/112
- **Commit:** `7e2f36e`
- **Ảnh hưởng tới Plan 06:** Task 1 của `04-06-PLAN.md` gom việc thêm 4 controller vào `excludeFilters`. Dòng `BudgetController.class` **đã có sẵn** — Plan 06 chỉ cần thêm 3 dòng còn lại (`DebtController`, `GoalController`, `RecurringController`) và không được thêm trùng.

---

**Total deviations:** 3 auto-fixed (đều Rule 3 - blocking, không có scope creep)
**Impact on plan:** Cả ba đều là điều chỉnh kỹ thuật bắt buộc để code chạy được, không đổi phạm vi nghiệp vụ. Hai lỗi đầu là chữ ký/annotation mà chỉ runtime thật mới lộ ra — đúng tinh thần "đối chiếu với thực tế chạy được thay vì giữ nguyên bản nháp".

## Issues Encountered

Không có blocker. Ba lỗi gặp phải đều thuộc loại chỉ lộ ra khi chạy Spring context/Testcontainers thật, đã xử lý ngay trong task tương ứng.

## User Setup Required

None — không có cấu hình dịch vụ ngoài nào cần thiết lập thủ công.

## Verification Results

- `mvn test -Dtest=BudgetCrudIntegrationTest` — xanh 4/4
- `mvn test -Dtest=BudgetPrivacyIntegrationTest,BudgetAlertListenerIntegrationTest` — xanh 4/4
- `mvn test` full suite — **xanh 112/112** (0 Failures, 0 Errors), không có regression trên Phase 1-3
- `grep "SUM(" BudgetService.java` — rỗng, không có phép tính `spent_amount` viết tay
- `grep -c "user_id = :currentUser" BudgetRepository.java` — 7 lần, quyền nằm trong SQL
- `grep "isDeleted" Budget.java` — rỗng, đối chiếu đúng schema V3 thật (CORE-11)
- `grep "TransactionPhase.AFTER_COMMIT" BudgetAlertListener.java` — có
- Migration Flyway V1→V10 chạy sạch qua Testcontainers

### Đối chiếu must_haves của PLAN

| Truth | Kết quả |
|---|---|
| Danh sách ngân sách kèm tiến độ tính động, đúng 3 trạng thái | Đạt — đọc từ `v_budget_progress` |
| Tạo ngân sách bị chặn trùng qua `ex_bud_no_overlap` | Đạt — `createBudget_duplicatePeriod_returns409` |
| Sửa `category_id`/`period_type` bị từ chối | Đạt — `updateBudget_changeCategoryId_returns400CategoryNotEditable` |
| User A không thấy chi tiêu của user B (BUDGET-08) | Đạt — `userA_budgetSpentAmount_notAffectedByUserB_sameCategory` |
| Ghi expense vượt ngưỡng sinh notifications sau commit | Đạt — `expenseExceedsBudget_createsNotificationAfterCommit` |
| Bulk 50 dòng không tạo notifications trùng | Đạt — `bulkFiftyRows_sameBudgetSameDay_createsOnlyOneNotification` |

## Known Stubs

Không có stub nào chặn mục tiêu của plan. Một điểm trả `null` **có chủ đích, đã ghi rõ lý do**:

| Vị trí | Nội dung | Lý do |
|---|---|---|
| `BudgetService.alerts()` | `BudgetAlertResponse.suggestion` luôn `null` | `api/05` mục 7 minh hoạ `action: "move_limit"` nhưng không đặc tả nào có endpoint chuyển hạn mức. Theo quy tắc "không tự bịa API", để `null` cho tới khi hợp đồng được bổ sung. Không chặn BUDGET-06: `severity`/`title`/`content`/`ratio` đều đầy đủ. |

Phạm vi hoãn có chủ đích khác (thuộc plan/phase sau, đúng kế hoạch):
- Vế quyền `group_id` trong `BudgetRepository`/`BudgetProgressRepository` — Phase 5 (`04-CONTEXT.md` mục Deferred)
- BUDGET-07 (tự lặp kỳ ngân sách mới) — Plan 07, tác vụ nền

## Next Phase Readiness

- **`TransactionRecordedEvent` sẵn sàng cho mọi module phái sinh còn lại** — Plan 03/04/05 chỉ cần thêm listener riêng, không phải sửa `TransactionWriter` lần nữa
- Mẫu `BudgetProgressRepository` (map view chỉ đọc qua projection) dùng lại được cho `report/` ở Plan 06
- **Lưu ý cho Plan 06 Task 1:** `BudgetController.class` đã có trong `excludeFilters`, chỉ cần thêm 3 controller còn lại, không thêm trùng
- Không có blocker nào cho các plan tiếp theo

## Self-Check: PASSED

Đã xác nhận toàn bộ 18 file claim (16 tạo mới, 2 sửa) tồn tại trên đĩa và cả 3 commit hash claim
(`9fedec3`, `fa5e64f`, `7e2f36e`) tồn tại trong lịch sử git. Không có mục nào MISSING.

---
*Phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o*
*Completed: 2026-08-27*
