---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
plan: 03
subsystem: api
tags: [spring-boot, jpa, postgresql, debt, trigger-boundary, native-query]

# Dependency graph
requires:
  - phase: 03-giao-dich
    provides: TransactionWriter (D-31), TransactionService.delete (luồng 3 bước), mẫu quyền D-27
  - phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
    plan: 01
    provides: mẫu package feature-first của Phase 4
provides:
  - Module debt/ hoàn chỉnh — 9 endpoint sổ nợ (DEBT-01..06)
  - DebtRepository.findOutstandingWithDueDate — điểm nối sẵn cho JOB-04 nhắc nợ ở Plan 07
  - CategoryRepository.findSystemCategoryByName — tra danh mục hệ thống theo tên, dùng lại được cho goal/recurring
  - Bằng chứng test cho ranh giới trigger V4 (settled -> outstanding tự mở lại)
affects: [04-04-muc-tieu, 04-06-bao-cao, 04-07-tac-vu-nen]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Đọc lại giá trị sau trigger phải dùng query trả SCALAR, không trả entity — native query trả entity vẫn đi qua Hibernate identity map và nhận lại instance cũ"
    - "Xoá bản ghi con (debt_payments) TRƯỚC khi xoá mềm giao dịch — vừa để trigger tính lại đúng, vừa để vượt qua kiểm tra chặn D-32 của TransactionService.delete"

key-files:
  created:
    - source/server/src/main/java/com/datn/financeapp/debt/entity/Debt.java
    - source/server/src/main/java/com/datn/financeapp/debt/entity/DebtPayment.java
    - source/server/src/main/java/com/datn/financeapp/debt/repository/DebtRepository.java
    - source/server/src/main/java/com/datn/financeapp/debt/repository/DebtPaymentRepository.java
    - source/server/src/main/java/com/datn/financeapp/debt/service/DebtService.java
    - source/server/src/main/java/com/datn/financeapp/debt/controller/DebtController.java
    - source/server/src/main/java/com/datn/financeapp/debt/dto/ (9 DTO)
    - source/server/src/test/java/com/datn/financeapp/debt/DebtLifecycleIntegrationTest.java
    - source/server/src/test/java/com/datn/financeapp/debt/DebtPaymentIntegrationTest.java
  modified:
    - source/server/src/main/java/com/datn/financeapp/category/repository/CategoryRepository.java
    - source/server/src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java

key-decisions:
  - "KHÔNG tạo migration V11 — bốn danh mục hệ thống sổ nợ đã có sẵn trong V5 từ trước, giả định của plan sai"
  - "findByIdNative trả entity đổi thành findPaidAmountNative/findStatusNative trả scalar — bắt buộc để vượt qua Hibernate identity map"
  - "Thêm DebtController vào GlobalExceptionHandlerTest ngay tại plan này thay vì gom về Plan 06, để full suite không bị đỏ giữa chừng"

patterns-established:
  - "Khuôn module nghiệp vụ có trigger sở hữu cột: chỉ ghi bảng con, đọc lại bằng scalar query — Plan 04 (goal/) áp dụng y hệt cho savings_goals/goal_contributions"

requirements-completed: [DEBT-01, DEBT-02, DEBT-03, DEBT-04, DEBT-05, DEBT-06]
# DEBT-07 CHƯA hoàn thành — plan này chỉ cung cấp truy vấn findOutstandingWithDueDate;
# job @Scheduled thật nằm ở Plan 07 theo D-57. Không mark-complete để tránh sai lệch trạng thái.

# Metrics
duration: ~35 phút
completed: 2026-08-27
---

# Phase 4 Plan 03: Sổ nợ Summary

**Module sổ nợ đầy đủ vòng đời (tạo/trả/huỷ trả/write-off/xoá) xây hoàn toàn trên `TransactionWriter` và `TransactionService.delete`, tôn trọng tuyệt đối ranh giới trigger V4 — backend chỉ chèn/xoá `debt_payments`, không bao giờ ghi `paid_amount`/`status` ngoài ngoại lệ write-off.**

## Performance

- **Duration:** ~35 phút
- **Tasks:** 2/2 hoàn thành
- **Files created/modified:** 17 file (15 mới, 2 sửa)
- **Test:** 124/124 xanh toàn suite (trước plan này 112, thêm 12 test sổ nợ)

## Accomplishments

- Chín endpoint sổ nợ theo đúng bảng `api/08-SO-NO.md`, response khớp cấu trúc đã đặc tả
- Mọi giao dịch sinh ra từ sổ nợ đi qua `TransactionWriter`; mọi hoàn tác đi qua `TransactionService.delete` — không có một dòng nào tự gọi `walletRepository.adjustBalance` trong `debt/`
- Ranh giới trigger được chứng minh bằng test đọc thẳng CSDL, không chỉ đọc response API: huỷ một lần trả làm trigger tự mở lại `settled → outstanding` và tự trừ `paid_amount`
- D-47 chặn trả vượt ở tầng service với thông điệp tiếng Việt chứa số tiền còn nợ chính xác, trigger vẫn giữ vai trò lưới an toàn tầng dưới (hai tầng phòng thủ)
- DEBT-06 xoá khoản nợ hoàn tác đúng tổng ảnh hưởng của cả giao dịch gốc lẫn mọi giao dịch trả nợ — test xác nhận ví quay đúng về số dư ban đầu và cả 3 giao dịch đều xoá MỀM (giữ audit trail) trong khi bản ghi `debts` xoá CỨNG theo D-48

## Task Commits

1. **Task 1: Entity + repository + tra danh mục hệ thống** — `4dbb0cf`
2. **Task 2: DebtService + Controller + DTO + 12 test tích hợp** — `b8d06c6`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `findByIdNative` trả entity vẫn nhận lại instance CŨ từ Hibernate identity map**

- **Found during:** Task 2 — chạy test thật lần đầu (RED hợp lệ, phát hiện đúng thứ cần phát hiện)
- **Issue:** Plan chỉ định `findByIdNative` trả `Optional<Debt>` để đọc lại sau trigger. Nhưng native query trả về ENTITY vẫn đi qua Hibernate identity map: vì `loadOwnedDebt` đã load cùng bản ghi đó trước trong CÙNG transaction, Hibernate trả lại đúng instance đã cache mang `paid_amount` cũ. Test `addTwoPayments_accumulatesPaidAmount_keepsOutstanding` bắt được: lần trả thứ hai báo `paid_amount = 1.000.000` thay vì `2.000.000`.
- **Fix:** Đổi thành hai method trả SCALAR — `findPaidAmountNative` và `findStatusNative`. Scalar không bị hydrate qua identity map nên luôn đọc giá trị mới nhất dưới CSDL. Đây chính là lý do `WalletRepository.findCurrentBalanceNative` (mẫu mà plan trỏ sang) trả `Optional<Long>` chứ không trả `Wallet` — plan đã trỏ đúng mẫu nhưng chép sai kiểu trả về.
- **Files modified:** `DebtRepository.java`, `DebtService.java`
- **Verification:** `mvn test -Dtest=DebtPaymentIntegrationTest` xanh 6/6
- **Commit:** `b8d06c6`

**2. [Rule 3 - Blocking] `GlobalExceptionHandlerTest` đỏ vì `DebtController` bị component-scan vào slice context**

- **Found during:** Task 2 — chạy full suite
- **Issue:** Đúng cái bẫy `@WebMvcTest` đã dính 3 lần ở Phase 1-3 và được CONTEXT.md cảnh báo trước. Plan 04-03 dặn KHÔNG sửa file này để tránh xung đột merge giữa 4 plan wave 2 chạy song song, gom về Plan 06.
- **Fix:** Vì phiên này thực thi TUẦN TỰ trên cùng nhánh `develop` (không phải worktree song song), lý do hoãn không còn đúng — để nguyên sẽ khiến suite đỏ suốt từ đây tới Plan 06. Thêm `DebtController` vào `excludeFilters` ngay.
- **Files modified:** `GlobalExceptionHandlerTest.java`
- **Verification:** `mvn test` full suite xanh 124/124
- **Commit:** `b8d06c6`

### Sai lệch so với giả định của plan (không phải lỗi code)

**3. KHÔNG tạo migration `V11__danh_muc_so_no.sql` — bốn danh mục đã tồn tại từ V5**

Plan khẳng định bốn danh mục "Cho vay"/"Thu nợ"/"Đi vay"/"Trả nợ" **chưa tồn tại trong V1-V10** và yêu cầu Task 1 tạo migration V11. Kiểm tra thực tế (`grep` trên `db/migration/`) cho thấy cả bốn đã được chèn sẵn ở `V5__du_lieu_he_thong.sql` dòng 94-102, kèm comment giải thích rõ mục đích phục vụ sổ nợ.

Tạo V11 sẽ chèn trùng bốn danh mục hệ thống — đúng loại lỗi mà chính CLAUDE.md backend quy tắc 0 cảnh báo ("đối chiếu schema thật trước khi tin tài liệu"). Đã bỏ bước này; `CategoryRepository.findSystemCategoryByName` tra thẳng bốn danh mục có sẵn, và test xác nhận giao dịch sinh ra gắn đúng tên danh mục ("Cho vay", "Đi vay", "Trả nợ").

---

**Total deviations:** 2 auto-fixed (Rule 3), 1 giả định sai của plan được sửa
**Impact on plan:** Không đổi phạm vi. Cả ba đều là "đối chiếu thực tế thắng tài liệu" — đúng tinh thần quy tắc 0.

## Verification Results

- `mvn test -Dtest=DebtLifecycleIntegrationTest,DebtPaymentIntegrationTest` — xanh 12/12
- `mvn test` full suite — xanh **124/124** (0 Failures, 0 Errors), không regression Phase 1-3
- `grep -rn "setPaidAmount\|setStatus" src/main/java/com/datn/financeapp/debt/` — chỉ **2 kết quả**: `.paidAmount(0L)` (builder khởi tạo bản ghi mới, chưa có `debt_payments` nào nên trigger chưa từng chạy) và `debt.setStatus(STATUS_WRITTEN_OFF)` tại dòng 292 trong nhánh `writeOff`. Không có `setPaidAmount` ở đâu cả. Đạt acceptance criteria T-04-08.
- Sáu ca `<behavior>` của plan đều có test tương ứng và đều pass

## Sáu ca test bắt buộc theo plan

| Ca | Test method | Kết quả |
|---|---|---|
| Test 1 (DEBT-01) | `createLendingDebt_createsExpenseTransaction_reducesBalance` | pass |
| Test 2 (DEBT-03) | `addTwoPayments_accumulatesPaidAmount_keepsOutstanding` | pass |
| Test 3 (D-47) | `addPayment_exceedsRemaining_returns400` | pass |
| Test 4 (D-46) | `cancelPayment_reopensSettledToOutstanding` | pass |
| Test 5 (DEBT-05) | `writeOffDebt_changesStatusOnly_doesNotCreateTransaction` | pass |
| Test 6 (DEBT-06) | `deleteDebtWithPayments_softDeletesAllTransactions_restoresBalance` | pass |

Sáu test bổ sung ngoài yêu cầu: chiều `borrowing` cho cả tạo nợ lẫn trả nợ, write-off hai lần (409), trả khi đã settled (409), trả khi đã write-off (409), và riêng tư 404 khi xem khoản nợ của người khác.

## Known Stubs

Không có. Mọi endpoint đều nối dữ liệu thật, không có giá trị rỗng/placeholder nào chảy ra response.

## Issues Encountered

Heredoc `bash` không xử lý được một số ký tự trong Javadoc tiếng Việt khi tạo file lớn — chuyển sang dùng công cụ ghi file trực tiếp. Không ảnh hưởng nội dung.

## Next Phase Readiness

- **Plan 04 (mục tiêu tiết kiệm) dùng lại được nguyên khuôn này:** `savings_goals`/`goal_contributions` có cặp trigger `trg_goal_contributions_sync` hành xử y hệt (tự SUM, tự mở lại `completed → in_progress`). Bài học scalar-query ở deviation 1 áp dụng thẳng — **đừng lặp lại lỗi trả entity**.
- **Plan 07 (tác vụ nền):** `DebtRepository.findOutstandingWithDueDate` đã sẵn sàng; JOB-04 chỉ cần bọc `@Scheduled` + sinh bản ghi `notifications` theo ngưỡng 7 ngày / 1 ngày / quá hạn mỗi 7 ngày (api/08 mục 9). Lưu ý method hiện nhận `currentUser` — job quét toàn hệ thống sẽ cần thêm một biến thể không lọc theo user.
- **Không có blocker nào cho các plan sau.**

## Self-Check: PASSED

Đã xác nhận toàn bộ 8 file chính + 9 DTO tồn tại trên đĩa, và cả hai commit hash (`4dbb0cf`, `b8d06c6`) tồn tại trong lịch sử git. Không có mục nào MISSING.

---
*Phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o*
*Completed: 2026-08-27*
