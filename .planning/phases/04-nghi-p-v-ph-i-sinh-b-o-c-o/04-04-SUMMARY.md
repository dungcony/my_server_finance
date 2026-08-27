---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
plan: 04
subsystem: api
tags: [spring-boot, jpa, postgresql, savings-goal, trigger-boundary, native-query]

# Dependency graph
requires:
  - phase: 03-giao-dich
    provides: TransactionWriter (D-31), TransactionService.delete (luồng 3 bước), mẫu quyền D-27
  - phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
    plan: 01
    provides: mẫu package feature-first của Phase 4
  - phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
    plan: 03
    provides: bài học query trả SCALAR để đọc lại sau trigger (áp dụng thẳng, không lặp lại lỗi)
provides:
  - Module goal/ hoàn chỉnh — 7 endpoint mục tiêu tiết kiệm (GOAL-01..04)
  - Bằng chứng test cho ranh giới trigger V4 phía mục tiêu (completed -> in_progress tự mở lại)
  - Mẫu "hai chế độ ghi nhận" (có/không sinh giao dịch thật) dùng lại được cho recurring
affects: [04-06-bao-cao, 04-07-tac-vu-nen]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Chế độ nghiệp vụ đọc TƯỜNG MINH từ trường request, không suy đoán từ trạng thái dữ liệu (D-49) — mục tiêu CÓ ví vẫn được nạp kiểu chỉ-ghi-nhận"
    - "Thứ tự hoàn tác bản ghi con phụ thuộc ràng buộc FK: SET NULL thì xoá giao dịch trước, RESTRICT thì xoá bản ghi con trước"

key-files:
  created:
    - source/server/src/main/java/com/datn/financeapp/goal/entity/SavingsGoal.java
    - source/server/src/main/java/com/datn/financeapp/goal/entity/GoalContribution.java
    - source/server/src/main/java/com/datn/financeapp/goal/repository/SavingsGoalRepository.java
    - source/server/src/main/java/com/datn/financeapp/goal/repository/GoalContributionRepository.java
    - source/server/src/main/java/com/datn/financeapp/goal/service/GoalService.java
    - source/server/src/main/java/com/datn/financeapp/goal/controller/GoalController.java
    - source/server/src/main/java/com/datn/financeapp/goal/dto/ (7 DTO)
    - source/server/src/test/java/com/datn/financeapp/goal/GoalContributionIntegrationTest.java
  modified:
    - source/server/src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java

key-decisions:
  - "Áp dụng ngay bài học Plan 03: findSavedAmountNative/findStatusNative trả SCALAR thay vì entity — không lặp lại lỗi Hibernate identity map"
  - "initial_amount sinh một goal_contributions với transaction_id = null: cách duy nhất vừa không sinh giao dịch (đúng api/09) vừa để trigger cộng đúng tiến độ ban đầu"
  - "cancelContribution xoá giao dịch TRƯỚC, xoá contribution SAU — ngược Debt, vì FK là SET NULL chứ không RESTRICT và D-32 chỉ soi debt_payments"
  - "update() cho đặt status = cancelled/in_progress nhưng CHẶN completed — trạng thái đó phải do trigger suy ra từ saved_amount"
  - "assessment của suggestion để null thay vì đoán mốc thu nhập cứng — chờ số liệu báo cáo ở Plan 06"
  - "Thêm GoalController vào GlobalExceptionHandlerTest ngay tại plan này thay vì gom về Plan 06, cùng lý do đã áp dụng ở Plan 03"

patterns-established:
  - "Khuôn module có trigger sở hữu cột đã dùng lần thứ hai (debt -> goal) và khớp trọn vẹn: chỉ ghi bảng con, đọc lại bằng scalar query"

requirements-completed: [GOAL-01, GOAL-02, GOAL-03, GOAL-04]

# Metrics
duration: ~25 phút
completed: 2026-08-27
---

# Phase 4 Plan 04: Mục tiêu tiết kiệm Summary

**Module mục tiêu tiết kiệm với hai chế độ nạp tiền phân biệt bằng trường request `create_transaction` (D-49), xây trên `TransactionWriter`/`TransactionService.delete`, tôn trọng tuyệt đối ranh giới trigger V4 — backend chỉ chèn/xoá `goal_contributions`, không bao giờ ghi `saved_amount`.**

## Performance

- **Duration:** ~25 phút
- **Tasks:** 2/2 hoàn thành
- **Files created/modified:** 13 file (12 mới, 1 sửa)
- **Test:** 129/129 xanh toàn suite (trước plan này 124, thêm 5 test mục tiêu)

## Accomplishments

- Bảy endpoint mục tiêu theo đúng bảng `api/09` Phần B, response khớp cấu trúc đã đặc tả
- **Hai chế độ nạp tiền đọc tường minh từ request**, không suy đoán từ `goal.walletId` — test chứng minh mục tiêu CÓ gắn ví vẫn nạp được kiểu "chỉ ghi nhận tiến độ" mà không ví nào bị đụng tới
- Ranh giới trigger được chứng minh bằng test đọc THẲNG CSDL (`jdbcTemplate`), không chỉ đọc response API: rút lại một lần nạp làm trigger tự mở lại `completed → in_progress` và tự trừ `saved_amount`
- **Không lặp lại lỗi Hibernate identity map của Plan 03** — dùng scalar query ngay từ đầu, nên Task 2 không mất vòng gỡ lỗi nào cho vấn đề đó
- `initial_amount` xử lý đúng cả hai vế mâu thuẫn: không sinh giao dịch (đúng đặc tả) nhưng vẫn phản ánh đúng tiến độ ban đầu (qua trigger)

## Task Commits

1. **Task 1: Entity + repository** — `b49c464`
2. **Task 2: GoalService + Controller + 7 DTO + 5 test tích hợp** — `8a9de0a`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `GlobalExceptionHandlerTest` đỏ vì `GoalController` bị component-scan vào slice context**

- **Found during:** Task 2 — chạy full suite
- **Issue:** Đúng cái bẫy `@WebMvcTest` đã dính 4 lần từ Phase 1. Plan 04-04 dặn KHÔNG sửa file này để tránh xung đột merge giữa 4 plan wave 2 chạy song song, gom về Plan 06 Task 1.
- **Fix:** Phiên này thực thi TUẦN TỰ trên cùng nhánh `develop` (không phải worktree song song), nên lý do hoãn không còn đúng — để nguyên sẽ khiến suite đỏ suốt tới Plan 06. Thêm `GoalController` vào `excludeFilters`, cùng quyết định đã áp dụng ở Plan 03.
- **Files modified:** `GlobalExceptionHandlerTest.java`
- **Verification:** `mvn test` full suite xanh 129/129
- **Commit:** `8a9de0a`

### Sai lệch so với chỉ dẫn của plan (có lý do kỹ thuật)

**2. `findByIdNative` trả entity đổi thành `findSavedAmountNative`/`findStatusNative` trả SCALAR**

Plan chỉ định `findByIdNative` trả `Optional<SavingsGoal>` (lặp đúng cách viết đã gây lỗi ở Plan 03). Đã áp dụng luôn bài học từ 04-03-SUMMARY: native query trả ENTITY vẫn đi qua Hibernate identity map, nên khi `loadOwnedGoal` đã load cùng bản ghi trong CÙNG transaction thì câu SELECT mới trả lại instance cũ mang `saved_amount` trước trigger. Trả scalar không bị hydrate qua identity map. Ca test `cancelContribution_reopensCompletedToInProgress` khẳng định response của chính lần nạp thứ hai đã mang `status = completed` — nếu dùng entity, ca này sẽ đỏ.

**3. Bổ sung 3 DTO ngoài danh sách `files_modified` của plan**

Plan liệt kê 5 DTO; thực tế cần thêm `CreateGoalResponse`, `GoalDetailResponse` (cho `GET /goals/{id}` kèm lịch sử nạp — endpoint có trong bảng api/09 nhưng plan không liệt kê DTO tương ứng) và giữ `UpdateGoalRequest`. Không đổi phạm vi, chỉ là plan liệt kê thiếu.

**4. `assessment` trong `suggestion` trả null thay vì tính**

`api/09` mục B1 định nghĩa `feasible`/`challenging`/`not_feasible` theo tỷ lệ mức nạp hằng tháng so với **thu nhập trung bình tháng** — số liệu này đến từ báo cáo thu-chi, chưa có cho tới Plan 06. Chọn trả null thay vì đoán một mốc cứng: đánh giá sai còn tệ hơn không đánh giá, vì người dùng sẽ tin nó ngay lúc đặt mục tiêu. `monthly_required` và `content` vẫn tính đủ. Xem mục "Known Stubs".

**5. Lỗi trong TEST của chính plan này (không phải lỗi code)**

Lần chạy RED đầu tiên, 4/5 test xanh và ca `cancelContribution_reopensCompletedToInProgress` đỏ ở khẳng định số dư ví nguồn — nhưng nguyên nhân là số học trong TEST sai (viết 5.000.000 là số dư trước khi hoàn tác, đúng phải là 7.500.000 = 10.000.000 − 2.500.000 − 2.500.000 + 2.500.000). Mọi khẳng định về ranh giới trigger (`saved_amount` 2.500.000, `status` mở lại `in_progress`, ví mục tiêu 2.500.000) đều xanh ngay lần đầu. Đã sửa kỳ vọng của test, không sửa code nghiệp vụ.

---

**Total deviations:** 1 auto-fixed (Rule 3), 3 sai lệch có chủ ý so với chỉ dẫn plan, 1 lỗi test tự sửa
**Impact on plan:** Không đổi phạm vi.

## Verification Results

- `mvn test -Dtest=GoalContributionIntegrationTest` — xanh **5/5**
- `mvn test` full suite — xanh **129/129** (0 Failures, 0 Errors), không regression Phase 1-3
- `grep -rn "setSavedAmount\|\.setStatus" src/main/java/com/datn/financeapp/goal/` — **3 kết quả, tất cả hợp lệ** (chi tiết ở mục dưới). Đạt mục tiêu T-04-11.
- Năm ca `<behavior>` của plan đều có test tương ứng và đều pass

### Giải trình T-04-11 — ba kết quả grep

Plan yêu cầu grep trả về **rỗng tuyệt đối**. Thực tế còn 3 kết quả, cả ba đều KHÔNG vi phạm ranh giới trigger:

| Vị trí | Nội dung | Vì sao hợp lệ |
|---|---|---|
| `SavingsGoal.java:58` | Javadoc nhắc "không bao giờ gọi `setSavedAmount`" | Là comment, không phải code |
| `GoalService.java:124` | `.savedAmount(0L)` trong builder | Giá trị KHỞI TẠO bản ghi mới, lúc đó chưa có `goal_contributions` nào nên trigger chưa từng chạy. Giống hệt `.paidAmount(0L)` đã được chấp nhận ở Plan 03 |
| `GoalService.java:329` | `goal.setStatus(req.status())` trong `update()` | **api/09 mục B5 yêu cầu tường minh**: "Đặt `status = 'cancelled'` để dừng mà vẫn giữ lịch sử". Trigger tôn trọng giá trị này bằng nhánh `WHEN status = 'cancelled' THEN 'cancelled'` nên không bị ghi đè. Service CHẶN đặt tay `completed` (400) — trạng thái suy ra từ tiến độ vẫn thuộc về trigger |

Không có đường ghi nào tới `saved_amount`/`status` trong luồng nạp/rút tiền — nơi ranh giới trigger thực sự quan trọng.

## Năm ca test bắt buộc theo plan

| Ca | Test method | Kết quả |
|---|---|---|
| Test 1 (GOAL-01) | `createGoal_withInitialAmount_doesNotCreateTransactionForInitialAmountItself` | pass |
| Test 2 (GOAL-03, true) | `addContribution_createTransactionTrue_createsTransferTransaction` | pass |
| Test 3 (GOAL-03, false) | `addContribution_createTransactionFalse_noWalletChange` | pass |
| Test 4 (GOAL-03) | `addContribution_createTransactionTrueWithoutGoalWallet_returns400` | pass |
| Test 5 (GOAL-04, D-58) | `cancelContribution_reopensCompletedToInProgress` | pass |

## Known Stubs

**1. `suggestion.assessment` luôn null** — `GoalService.buildSuggestion`, `GoalListItemResponse.Suggestion.assessment`

Cần "thu nhập trung bình tháng" từ báo cáo thu-chi (Plan 06) mới phân loại được `feasible`/`challenging`/`not_feasible` theo ngưỡng 30%/60% của api/09 mục B1. `monthly_required` và `content` đã tính đủ và đúng, nên tính năng gợi ý vẫn dùng được — chỉ thiếu nhãn đánh giá. Không chặn mục tiêu của plan này (GOAL-01..04 đều là CRUD + nạp/rút tiền). **Plan 06 nên nối nốt** khi đã có truy vấn thu nhập trung bình.

## Threat Flags

Không có. Module không mở thêm bề mặt tấn công ngoài phạm vi `<threat_model>`: mọi endpoint đều lọc quyền bằng `user_id` ngay trong SQL, không có đường truy cập chéo người dùng, không đụng tới ranh giới tin cậy mới.

## Issues Encountered

Không có vấn đề kỹ thuật đáng kể. Việc áp dụng trước bài học scalar-query từ Plan 03 giúp Task 2 chạy xanh gần như ngay lần đầu (chỉ vướng số học sai trong chính test).

## Next Phase Readiness

- **Plan 06 (báo cáo):** giao dịch nạp mục tiêu là `type = 'transfer'` nên tự động bị loại khỏi mọi báo cáo thu-chi qua điều kiện `type != 'transfer'` có sẵn — không cần xử lý gì thêm. Ngoài ra Plan 06 nên nối `suggestion.assessment` (xem Known Stubs).
- **Plan 07 (tác vụ nền):** chưa có truy vấn nào cho nhắc mục tiêu sắp tới hạn; nếu cần, thêm một method dạng `findInProgressWithTargetDate` theo đúng khuôn `DebtRepository.findOutstandingWithDueDate`.
- **Không có blocker nào cho các plan sau.**

## Self-Check: PASSED

Đã xác nhận toàn bộ 6 file chính + 7 DTO + 1 file test tồn tại trên đĩa, và cả hai commit hash (`b49c464`, `8a9de0a`) tồn tại trong lịch sử git. Không có mục nào MISSING.

---
*Phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o*
*Completed: 2026-08-27*
