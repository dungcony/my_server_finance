---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
plan: 07
subsystem: infra
tags: [spring-scheduled, cron, budget, debt, wallet, notifications, export-jobs, transactional-self-invocation]

# Dependency graph
requires:
  - phase: 04-02
    provides: "BudgetService, BudgetRepository, v_budget_progress"
  - phase: 04-03
    provides: "DebtService, DebtRepository, trigger trg_debt_payments_sync"
  - phase: 04-05
    provides: "RecurringRunnerService.runDueRecurring()"
  - phase: 04-06
    provides: "ExportJobRepository, ExportJob entity, export_jobs table"
provides:
  - "5 job @Scheduled hoạt động đầy đủ: WalletReconciliationJob, BudgetRenewalJob, RecurringTransactionJob, DebtReminderJob, ExportCleanupJob"
  - "BudgetService.renewExpiredBudgets/renewOneBudget (JOB-02)"
  - "DebtService.sendDueReminders (JOB-04)"
  - "WalletTransferService.reconcileAllWallets (JOB-01)"
  - "NotificationRepository.insertGenericNotification dùng chung cho mọi loại thông báo không cần ON CONFLICT"
affects: [05-nhom-gia-dinh]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Worker bean riêng cho @Transactional method gọi từ vòng lặp cùng class — tránh transaction self-invocation (BudgetRenewalWorker, DebtReminderWorker, WalletTransferService.ReconciliationWorker, ExportCleanupJob.ExportCleanupWorker)"
    - "Job @Scheduled mỏng: bọc try/catch toàn bộ run(), method nghiệp vụ top-level KHÔNG @Transactional để mỗi bản ghi có transaction riêng, method xử lý-một-bản-ghi MỚI mang @Transactional"

key-files:
  created:
    - source/server/src/main/java/com/datn/financeapp/scheduler/WalletReconciliationJob.java
    - source/server/src/main/java/com/datn/financeapp/scheduler/BudgetRenewalJob.java
    - source/server/src/main/java/com/datn/financeapp/scheduler/RecurringTransactionJob.java
    - source/server/src/main/java/com/datn/financeapp/scheduler/DebtReminderJob.java
    - source/server/src/main/java/com/datn/financeapp/scheduler/ExportCleanupJob.java
    - source/server/src/main/java/com/datn/financeapp/budget/service/BudgetRenewalWorker.java
    - source/server/src/main/java/com/datn/financeapp/debt/service/DebtReminderWorker.java
    - source/server/src/test/java/com/datn/financeapp/scheduler/BudgetRenewalJobIntegrationTest.java
    - source/server/src/test/java/com/datn/financeapp/scheduler/DebtReminderJobIntegrationTest.java
    - source/server/src/test/java/com/datn/financeapp/scheduler/WalletReconciliationJobIntegrationTest.java
  modified:
    - source/server/src/main/java/com/datn/financeapp/budget/repository/BudgetRepository.java
    - source/server/src/main/java/com/datn/financeapp/budget/service/BudgetService.java
    - source/server/src/main/java/com/datn/financeapp/debt/repository/DebtRepository.java
    - source/server/src/main/java/com/datn/financeapp/debt/service/DebtService.java
    - source/server/src/main/java/com/datn/financeapp/notification/repository/NotificationRepository.java
    - source/server/src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
    - source/server/src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java

key-decisions:
  - "Giờ cron khác nhau trong đêm cho cả 5 job (1h/2h/3h30/4h/4h30) — scheduler mặc định 1 thread, job trùng giờ sẽ đợi nhau"
  - "renewExpiredBudgets/sendDueReminders/reconcileAllWallets ở method top-level KHÔNG @Transactional — mỗi bản ghi xử lý độc lập trong transaction riêng của worker bean, một bản ghi lỗi không cuốn theo bản ghi trước đó"
  - "Tách BudgetRenewalWorker/DebtReminderWorker/WalletTransferService.ReconciliationWorker/ExportCleanupJob.ExportCleanupWorker thành bean riêng vì self-invocation @Transactional trong cùng class bị bỏ qua AOP proxy — phát hiện qua test thất bại, không phải đọc trước"
  - "ExportCleanupJob xoá file đĩa TRƯỚC rồi mới xoá bản ghi CSDL (D-45) — bọc lỗi từng file riêng để một file lỗi xoá không chặn file khác"

patterns-established:
  - "Mọi @Scheduled job mới nên copy đúng khuôn IdempotencyCleanupJob: @Component + @RequiredArgsConstructor + @Slf4j, method run() bọc try/catch toàn bộ, log tiếng Việt kết quả"
  - "Nếu service top-level không @Transactional cần gọi method @Transactional theo vòng lặp cho từng bản ghi độc lập, PHẢI đặt method đó ở bean khác (không cùng class) — self-invocation không đi qua Spring AOP proxy"

requirements-completed: [BUDGET-07, JOB-01, JOB-02, JOB-03, JOB-04]

# Metrics
duration: 55min
completed: 2026-08-28
---

# Phase 04 Plan 07: Tác vụ nền hằng ngày Summary

**5 job `@Scheduled` (đối chiếu ví, lặp kỳ ngân sách, sinh giao dịch định kỳ, nhắc nợ, dọn export) chạy giờ khác nhau trong đêm, mỗi bản ghi lỗi bọc riêng không crash scheduler pool**

## Performance

- **Duration:** 55 phút
- **Started:** 2026-08-28T14:20:00Z (ước tính)
- **Completed:** 2026-08-28T15:15:00Z
- **Tasks:** 2/2
- **Files modified:** 20 (7 tạo mới ở Task 1 gián tiếp qua sửa service có sẵn, 5 job mới + 2 worker mới + 3 test mới ở Task 2)

## Accomplishments
- `WalletReconciliationJob` (1h), `BudgetRenewalJob` (2h), `RecurringTransactionJob` (3h30), `DebtReminderJob` (4h), `ExportCleanupJob` (4h30) — tất cả `@Scheduled(zone = "Asia/Ho_Chi_Minh")`, giờ khác nhau
- `BudgetService.renewExpiredBudgets` tự lặp kỳ ngân sách `auto_renew=true` đã hết hạn, chống tạo trùng bằng `existsOverlapping` (lớp bảo vệ Java) + `ex_bud_no_overlap` (lớp bảo vệ CSDL)
- `DebtService.sendDueReminders` quét TOÀN HỆ THỐNG khoản nợ outstanding, nhắc đúng 3 mốc: còn 7 ngày / còn 1 ngày / quá hạn nhắc lại mỗi 7 ngày
- `WalletTransferService.reconcileAllWallets` tái dùng nguyên công thức đối chiếu của endpoint thủ công (WALLET-07), không viết lại
- `ExportCleanupJob` xoá file đĩa trước bản ghi CSDL, bọc lỗi từng file riêng
- Phát hiện và sửa lỗi transaction self-invocation ở cả 3 method mới (renewOneBudget, evaluateAndNotify, reconcileOneWalletAutoFix) qua test thất bại thực tế — tách thành bean worker riêng

## Task Commits

Mỗi task được commit atomically:

1. **Task 1: Bổ sung method service còn thiếu** - `20c0e09` (feat)
2. **Task 2: 5 job @Scheduled + test chống trùng/bọc lỗi** - `2756350` (feat, gồm cả sửa lỗi self-invocation phát hiện trong lúc chạy test)

**Plan metadata:** (commit này)

## Files Created/Modified
- `scheduler/WalletReconciliationJob.java` - JOB-01, cron 1h sáng
- `scheduler/BudgetRenewalJob.java` - JOB-02, cron 2h sáng
- `scheduler/RecurringTransactionJob.java` - JOB-03, cron 3h30 sáng
- `scheduler/DebtReminderJob.java` - JOB-04, cron 4h sáng
- `scheduler/ExportCleanupJob.java` - dọn `export_jobs` quá hạn (D-45), cron 4h30 sáng
- `budget/service/BudgetRenewalWorker.java` - bean riêng cho `renewOneBudget` (@Transactional qua đúng proxy)
- `debt/service/DebtReminderWorker.java` - bean riêng cho `evaluateAndNotify` (@Transactional qua đúng proxy)
- `budget/service/BudgetService.java` - thêm `renewExpiredBudgets`, `endOfPeriod` chuyển sang `static` package-private để worker dùng lại
- `debt/service/DebtService.java` - thêm `sendDueReminders`
- `wallet/service/WalletTransferService.java` - thêm `reconcileAllWallets`, tách `ReconciliationWorker` nested bean cho `reconcileOneWalletAutoFix`/`doReconcile`
- `budget/repository/BudgetRepository.java` - `findAutoRenewExpired`, `existsOverlapping`
- `debt/repository/DebtRepository.java` - `findAllOutstandingWithDueDate` (quét toàn hệ thống, khác `findOutstandingWithDueDate` theo user)
- `wallet/repository/WalletRepository.java` - `findAllActiveWalletIds`
- `notification/repository/NotificationRepository.java` - `insertGenericNotification` (không `ON CONFLICT`, dùng cho `budget_renewed`/`debt_reminder`)
- `scheduler/BudgetRenewalJobIntegrationTest.java`, `DebtReminderJobIntegrationTest.java`, `WalletReconciliationJobIntegrationTest.java` - 5 test case theo `<behavior>` của plan

## Decisions Made
- Giữ đúng khuôn cron do plan chỉ định (1h/2h/3h30/4h/4h30), không tự đổi
- `renewExpiredBudgets`/`sendDueReminders`/`reconcileAllWallets` để KHÔNG `@Transactional` ở top-level đúng theo `<interfaces>` của plan
- Phát sinh ngoài kế hoạch: phải tách 3 method con (`renewOneBudget`, `evaluateAndNotify`, `reconcileOneWalletAutoFix`) sang bean riêng vì bản nháp ban đầu trong plan gợi ý đặt chúng làm method `@Transactional` khác trong CÙNG class gọi qua vòng lặp — đây chính là bẫy self-invocation mà `WalletTransferService` đã tự cảnh báo trong Javadoc lớp nhưng plan pseudo-code không né được. Ba bean mới: `BudgetRenewalWorker`, `DebtReminderWorker`, `WalletTransferService.ReconciliationWorker` (nested static, theo đúng khuôn `ExportCleanupJob.ExportCleanupWorker` viết cùng lúc)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Sửa lỗi transaction self-invocation ở renewOneBudget/evaluateAndNotify/reconcileOneWalletAutoFix**
- **Found during:** Task 2, khi chạy `mvn test` cho 3 file test mới — cả 3 test case ghi CSDL đều thất bại với `InvalidDataAccessApiUsageException: Executing an update/delete query`
- **Issue:** Pseudo-code của plan (Task 1) đặt `renewOneBudget`, `evaluateAndNotify`, `reconcileOneWalletAutoFix` làm method `@Transactional` riêng nhưng gọi từ method top-level KHÔNG `@Transactional` trong CÙNG class qua `this.xxx()` (ví dụ `renewExpiredBudgets()` gọi `this.renewOneBudget()`). Self-invocation bỏ qua Spring AOP proxy nên `@Transactional` không có hiệu lực — mọi câu `@Modifying` bên trong ném lỗi vì không có transaction đang mở
- **Fix:** Tách 3 method đó ra bean `@Component` riêng (`BudgetRenewalWorker`, `DebtReminderWorker`) và một nested static bean (`WalletTransferService.ReconciliationWorker`), gọi qua field injection thay vì `this` — proxy Spring hoạt động đúng. `endOfPeriod` trong `BudgetService` đổi từ `private` sang `static` package-private để worker tái dùng logic tính ngày cuối kỳ, không viết lại
- **Files modified:** `BudgetService.java`, `BudgetRenewalWorker.java` (mới), `DebtService.java`, `DebtReminderWorker.java` (mới), `WalletTransferService.java`
- **Verification:** Chạy lại 5 test case — cả 5 pass; chạy `mvn test` toàn bộ Phase 1-4 — 161/161 pass
- **Committed in:** `2756350` (cùng commit Task 2, sửa trước khi commit vì phát hiện ngay lúc chạy test)

---

**Total deviations:** 1 auto-fixed (1 bug — Rule 1)
**Impact on plan:** Bắt buộc để đúng nghiệp vụ (chống tạo trùng ngân sách, nhắc nợ, tự sửa số dư ví đều cần transaction thật). Không phình phạm vi — chỉ tái cấu trúc cách gọi, không đổi hành vi nghiệp vụ đã đặc tả trong plan.

## Issues Encountered
- Môi trường worktree lồng 2 cấp (`.claude/worktrees/agent-.../`) làm cấu hình `spring.flyway.locations: filesystem:../../db/migration` (tương đối) trỏ sai thư mục khi chạy `mvn test` trực tiếp trong worktree — phải set biến môi trường `SPRING_FLYWAY_LOCATIONS=filesystem:D:/PTIT/DATN/db/migration` (đường dẫn tuyệt đối) riêng cho lần chạy test này. KHÔNG sửa `application.yml` (sẽ phá cấu hình đúng ở checkout chính) — chỉ là workaround cục bộ cho phiên chạy test của agent này trong worktree, không phải thay đổi cần commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Toàn bộ 31 requirement ID của Phase 4 đã có plan triển khai, BUDGET-07 + JOB-01..04 hoàn thành ở plan này
- Test tích luỹ Phase 1-4: 161/161 pass, sẵn sàng cho `/gsd-verify-work` đóng Phase 4
- Không có blocker mới cho Phase 5 (nhóm gia đình + AI drafts)

---
*Phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o*
*Completed: 2026-08-28*

## Self-Check: PASSED

Tất cả 11 file (5 job, 2 worker, 3 test, SUMMARY.md) đã xác nhận tồn tại trên đĩa; 2 commit hash
(`20c0e09`, `2756350`) đã xác nhận có trong `git log`.
