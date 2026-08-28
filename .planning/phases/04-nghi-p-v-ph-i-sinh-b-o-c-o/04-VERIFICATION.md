---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
verified: 2026-08-28T15:40:00Z
status: gaps_found
score: 5/7 must-haves verified
overrides_applied: 0
gaps:
  - truth: "Các job nền (đối chiếu số dư, lặp ngân sách, sinh định kỳ, nhắc nợ) tự chạy hằng ngày theo lịch"
    status: partial
    reason: >
      Job DebtReminderJob tồn tại, chạy đúng lịch @Scheduled(zone=Asia/Ho_Chi_Minh), nhưng logic
      nghiệp vụ bên trong DebtReminderWorker.evaluateAndNotify sai theo đúng đặc tả api/08 mục 9
      và theo requirement DEBT-07/JOB-04: (1) điều kiện `daysUntilDue < 0 && daysUntilDue % 7 == 0`
      bỏ sót mốc quá hạn đầu tiên (daysUntilDue == 0), người vỡ hạn hôm nay không được nhắc cho
      tới 7 ngày sau; (2) `LocalDate.now()` không neo múi giờ Asia/Ho_Chi_Minh dù job khai zone
      đúng trong @Scheduled, nên trên môi trường JVM chạy UTC cả ba mốc lệch một ngày; (3) không
      có cơ chế chống trùng ở tầng CSDL (insertGenericNotification không có ON CONFLICT) — job
      chạy lại trong cùng ngày (retry, deploy lại, 2 instance) sẽ sinh thông báo trùng cho đúng
      một khoản nợ đang ở mốc nhắc. SUMMARY.md của Plan 07 khẳng định
      "insertGenericNotification dùng chung cho mọi loại thông báo không cần ON CONFLICT" — khẳng
      định này sai, đã bị chính code review của phase (04-REVIEW.md, CR-01) chỉ ra và xác nhận lại
      qua đối chiếu mã nguồn trực tiếp.
    artifacts:
      - path: "src/main/java/com/datn/financeapp/debt/service/DebtReminderWorker.java"
        issue: "Mốc quá hạn thiếu ca ==0, LocalDate.now() không múi giờ, không chống trùng ở CSDL"
    missing:
      - "Sửa điều kiện: daysUntilDue == 7 | daysUntilDue == 1 | daysUntilDue <= 0 && (-daysUntilDue) % 7 == 0"
      - "Thay LocalDate.now() bằng LocalDate.now(ZoneId.of(\"Asia/Ho_Chi_Minh\"))"
      - "Thêm method chống trùng theo ngày (UNIQUE index + ON CONFLICT) riêng cho type=debt_reminder, theo đúng khuôn insertBudgetAlertIfNotExists"
      - "Bổ sung test cho daysUntilDue=0, daysUntilDue=-7, và gọi job hai lần trong cùng một ngày"
  - truth: "Báo cáo tổng quan/theo kỳ/xu hướng loại trừ hoàn toàn giao dịch transfer và cộng gộp danh mục con; xuất báo cáo trả 202 kèm job_id và có thể poll tới khi có link tải"
    status: partial
    reason: >
      Phần "trả 202 kèm job_id, poll tới khi có link tải" có lỗ hổng khiến job có thể kẹt vĩnh
      viễn ở status=processing, không bao giờ đạt "có link tải" lẫn không báo lỗi cho người dùng
      poll. ExportAsyncRunner.runExport mang cả @Async và @Transactional trên cùng method, và bắt
      Exception ngay TRONG transaction đó rồi gọi markFailed cùng transaction đã bị PostgreSQL huỷ
      (current transaction is aborted) khi lỗi là lỗi CSDL — markFailed tự ném exception mới, bản
      ghi export_jobs kẹt ở processing mãi mãi vì ExportCleanupJob chỉ quét status=completed. Còn
      có race thứ hai: ExportService.createJob (@Transactional) gọi exportAsyncRunner.runExport
      trước khi transaction tạo bản ghi export_jobs commit — @Async có thể chạy trước khi bản ghi
      tồn tại trong CSDL, khiến markCompleted/markFailed khớp 0 dòng và trôi qua im lặng. Đã xác
      nhận: không có test nào exercise nhánh lỗi CSDL hay race trước-commit trong
      ExportJobIntegrationTest — chỉ test đường vui (202→processing→completed) và 501.
    artifacts:
      - path: "src/main/java/com/datn/financeapp/report/export/ExportAsyncRunner.java"
        issue: "markFailed cùng transaction đã abort với phần ghi file — mất khả năng ghi trạng thái lỗi"
      - path: "src/main/java/com/datn/financeapp/report/service/ExportService.java"
        issue: "Kích hoạt @Async trước khi transaction tạo export_jobs commit — race điều kiện"
    missing:
      - "Tách markCompleted/markFailed sang bean riêng @Transactional(REQUIRES_NEW), không dùng chung transaction với phần đọc/ghi file export"
      - "Hoãn exportAsyncRunner.runExport tới sau khi transaction tạo job commit (TransactionSynchronizationManager.registerSynchronization afterCommit, hoặc @TransactionalEventListener AFTER_COMMIT)"
      - "Cân nhắc thêm job dọn các bản ghi processing quá cũ (ví dụ >1 giờ) để tự phục hồi sau sự cố"
deferred: []
human_verification: []
---

# Phase 4: Nghiệp vụ phái sinh & Báo cáo — Báo cáo xác minh

**Mục tiêu phase:** User quản lý ngân sách, sổ nợ, giao dịch định kỳ, mục tiêu tiết kiệm và xem
báo cáo thống kê — tất cả xây trên nền giao dịch đã ổn định ở Phase 3; các background job hằng
ngày giữ dữ liệu nhất quán mà không cần người dùng can thiệp.

**Thời điểm xác minh:** 2026-08-28
**Trạng thái:** gaps_found
**Xác minh lại:** Không — lần xác minh đầu tiên

## Đánh giá đạt mục tiêu

### Các sự thật quan sát được (Observable Truths)

| # | Sự thật | Trạng thái | Bằng chứng |
|---|---------|-----------|------------|
| 1 | User xem tiến độ ngân sách tính động (3 trạng thái, cộng gộp danh mục con) + gợi ý hạn mức TB 3 kỳ | ✓ VERIFIED | `v_budget_progress` (V6) có `over_limit`/`near_limit` đúng ngưỡng 100%/80%, `fn_category_tree` cộng gộp. `BudgetService` đọc thẳng view, không tính lại `spent_amount`. `BUDGET-05` gợi ý hạn mức đã có test riêng theo SUMMARY 04-02 |
| 2 | Tạo khoản nợ sinh đúng 1 giao dịch thật; trả nợ cộng dồn `paid_amount`, tự `settled`; huỷ trả nợ hoàn tác đúng | ✓ VERIFIED | `DebtService` gọi lại `TransactionWriter`; tôn trọng ranh giới trigger V4 tuyệt đối (đọc lại bằng `findPaidAmountNative`/`findStatusNative` ở nhánh `addPayment`); test `DebtLifecycleIntegrationTest`, `DebtPaymentIntegrationTest` tồn tại và pass |
| 3 | Khoản định kỳ tự sinh giao dịch đúng ngày đến hạn kể cả 29/30/31, không sinh trùng, bắt kịp kỳ bị bỏ lỡ | ✓ VERIFIED | `RecurringDateCalculator.applyOriginalDay` xử lý đúng "ngày gốc không trôi"; `RecurringPeriodWriter` mỗi kỳ một transaction riêng (bean tách biệt tránh self-invocation), dựa vào `uq_txn_recurring_date` (V2) chống trùng ở CSDL; `MAX_PERIODS_PER_RUN` bảo hiểm vòng lặp |
| 4 | Nạp/rút mục tiêu tiết kiệm đúng 2 chế độ, tự chuyển trạng thái khi đạt/vượt target | ✓ VERIFIED | `GoalService` tôn trọng ranh giới trigger `trg_goal_contributions_sync`, không tự ghi `saved_amount`/`status` (trừ `cancelled` chủ động); test `GoalContributionIntegrationTest` |
| 5 | Báo cáo loại trừ hoàn toàn transfer + cộng gộp danh mục con; xuất báo cáo trả 202 kèm `job_id`, poll tới khi có link tải | ✗ FAILED (một phần) | Phần loại transfer/cộng gộp: **đúng** — `ReportQueryFragments.REPORT_ELIGIBLE` hard-code `type <> 'transfer' AND counts_in_report = TRUE AND NOT is_deleted`. Phần "poll tới khi có link tải": **có lỗ hổng** — `ExportAsyncRunner`/`ExportService` có thể khiến job kẹt vĩnh viễn ở `processing` khi gặp lỗi CSDL hoặc race trước-commit (xem CR-02, gaps ở trên) |
| 6 | Job nền (đối chiếu ví, lặp ngân sách, sinh định kỳ, nhắc nợ) tự chạy hằng ngày theo lịch | ✗ FAILED (một phần) | Cả 4 job `@Scheduled` (+ dọn export) đều tồn tại đúng cấu trúc `zone = "Asia/Ho_Chi_Minh"`, try/catch bọc ngoài, gọi đúng service tương ứng. Nhưng nội dung nghiệp vụ của `DebtReminderJob` sai (xem CR-01, gaps ở trên) — job "chạy" đúng lịch nhưng sinh kết quả sai/lệch/trùng |
| 7 | Test riêng tư ngân sách BUDGET-08 xanh: B chi tiền vào danh mục X, ngân sách cá nhân của A cùng danh mục vẫn `spent_amount = 0` | ✓ VERIFIED | `BudgetPrivacyIntegrationTest` chạy thật qua Testcontainers, PASS (`Tests run: 1, Failures: 0`). Assertion đúng: `spent_amount == 0` cho A, `== 1_000_000` cho B. `v_budget_progress` (V6) có điều kiện phạm vi `(b.user_id = t.user_id) OR (b.group_id ... ví chung)` |

**Điểm:** 5/7 sự thật xác minh đầy đủ. Sự thật #5 và #6 xác minh **một phần** — phần cộng gộp/
loại transfer của báo cáo và cấu trúc lịch chạy của job đều đúng, nhưng có lỗi nghiệp vụ thật bên
trong khiến hai criteria này không đạt được đầy đủ ý nghĩa "tự chạy... mà không cần người dùng
can thiệp" (job nhắc nợ sai/trùng) và "poll tới khi có link tải" (job export có thể kẹt vĩnh viễn).

### Nghệ thuật phẩm bắt buộc (Required Artifacts)

| Artifact | Kỳ vọng | Trạng thái | Chi tiết |
|---|---|---|---|
| `db/migration/V9__thong_bao.sql`, `V10__export_jobs.sql` | Bảng notifications/export_jobs | ✓ VERIFIED | Cả 2 migration chạy sạch (Flyway log xác nhận version 9, 10 applied) |
| `budget/service/BudgetAlertListener.java` | `@TransactionalEventListener(AFTER_COMMIT)` cảnh báo ngân sách | ✓ VERIFIED | Đúng pattern, có `REQUIRES_NEW` |
| `debt/service/DebtService.java` | Nghiệp vụ sổ nợ đầy đủ, ranh giới trigger | ✓ VERIFIED | Đúng, trừ `toListItem` đọc entity thay vì đọc lại ở vài nhánh (WR-05, không phá vỡ success criteria) |
| `goal/service/GoalService.java` | Hai chế độ nạp, ranh giới trigger | ✓ VERIFIED | Đúng |
| `recurring/service/RecurringPeriodWriter.java` | Bean riêng @Transactional, mỗi kỳ 1 transaction | ✓ VERIFIED | Đúng |
| `report/repository/ReportQueryFragments.java` | `REPORT_ELIGIBLE` hằng số loại transfer | ✓ VERIFIED | Đúng, hard-code không tham số tắt |
| `report/export/ExportAsyncRunner.java` | Bean riêng @Async | ⚠️ STUB (nghiệp vụ) | Tồn tại, wired đúng để tránh self-invocation, nhưng logic markFailed nằm sai transaction — xem CR-02 |
| `scheduler/DebtReminderJob.java` + `debt/service/DebtReminderWorker.java` | Job nhắc nợ 3 mốc | ⚠️ STUB (nghiệp vụ) | Job tồn tại, cron đúng, nhưng nội dung nhắc sai mốc/múi giờ/chống trùng — xem CR-01 |
| `scheduler/WalletReconciliationJob.java`, `BudgetRenewalJob.java`, `RecurringTransactionJob.java`, `ExportCleanupJob.java` | 4 job còn lại | ✓ VERIFIED | Đúng cấu trúc, gọi đúng service |

### Xác minh liên kết chính (Key Link Verification)

| Từ | Đến | Qua | Trạng thái | Chi tiết |
|---|---|---|---|---|
| `TransactionWriter` | `TransactionRecordedEvent` | `ApplicationEventPublisher.publishEvent` | ✓ WIRED | |
| `BudgetAlertListener` | `TransactionRecordedEvent` | `@TransactionalEventListener(AFTER_COMMIT)` | ✓ WIRED | |
| `DebtService` | `TransactionWriter` | `transactionWriter.write(...)` | ✓ WIRED | |
| `GoalService` | `TransactionWriter` | `transactionWriter.write` khi `create_transaction=true` | ✓ WIRED | |
| `RecurringRunnerService` | `RecurringPeriodWriter` | gọi bean khác, method không `@Transactional` ở tầng quét | ✓ WIRED | |
| `ExportService.createJob` | `ExportAsyncRunner.runExport` | gọi trực tiếp **trước khi transaction commit** | ⚠️ PARTIAL | Race điều kiện — @Async có thể chạy trước khi bản ghi export_jobs tồn tại trong CSDL (xem CR-02) |
| `scheduler/*Job.java` | service tương ứng (`renewExpiredBudgets`, `runDueRecurring`, `reconcileAllWallets`, `sendDueReminders`) | gọi trực tiếp trong `@Scheduled` | ✓ WIRED | Cả 5 job đều gọi đúng phương thức |

### Truy vết Requirements

| Requirement | Plan nguồn | Trạng thái | Bằng chứng |
|---|---|---|---|
| BUDGET-01..06, 08 | 04-02 | ✓ SATISFIED | View + service đúng, test BUDGET-08 pass |
| BUDGET-07 | 04-07 | ✓ SATISFIED | `renewExpiredBudgets`/`BudgetRenewalWorker` đúng, chống trùng qua điều kiện `findAutoRenewExpired` |
| DEBT-01..06 | 04-03 | ✓ SATISFIED | |
| DEBT-07 | 04-03 (truy vấn) + 04-07 (job) | ✗ BLOCKED | Truy vấn/job tồn tại nhưng nội dung nhắc sai theo đặc tả api/08 mục 9 (xem gap CR-01) |
| RECUR-01..03 | 04-05 | ✓ SATISFIED | |
| GOAL-01..04 | 04-04 | ✓ SATISFIED | |
| REPORT-01..04 | 04-06 | ✓ SATISFIED | |
| REPORT-05 | 04-06 | ✗ BLOCKED | Trả 202 + job_id đúng, nhưng "poll tới khi có link tải" không đảm bảo — job có thể kẹt processing vĩnh viễn (xem gap CR-02) |
| JOB-01 | 04-07 | ✓ SATISFIED | |
| JOB-02 | 04-07 | ✓ SATISFIED | |
| JOB-03 | 04-07 | ✓ SATISFIED | |
| JOB-04 | 04-01 (hạ tầng) + 04-07 (job) | ✗ BLOCKED | Cùng gap DEBT-07 — job chạy đúng lịch nhưng logic nhắc sai |

Không có requirement ID nào bị mồ côi (orphaned) — toàn bộ BUDGET-01..08, DEBT-01..07, RECUR-01..03,
GOAL-01..04, REPORT-01..05, JOB-01..04 đều xuất hiện trong `requirements:` của ít nhất một plan.

### Anti-Patterns phát hiện

| File | Dòng | Pattern | Mức độ | Ảnh hưởng |
|---|---|---|---|---|
| `debt/service/DebtReminderWorker.java` | 26-36 | Logic điều kiện sai + thiếu chống trùng CSDL | 🛑 Blocker | Phá vỡ Success Criteria #6 (job nhắc nợ), requirement DEBT-07/JOB-04 |
| `report/export/ExportAsyncRunner.java` + `ExportService.java` | 52-72 / 34-53 | Transaction boundary sai + race @Async trước commit | 🛑 Blocker | Phá vỡ Success Criteria #5 (poll tới khi có link tải), requirement REPORT-05 |
| `report/export/CsvReportWriter.java` | 55-63 | Chưa vô hiệu hoá công thức CSV (CSV injection) | ⚠️ Warning | Rủi ro bảo mật khi mở file bằng Excel — không phá vỡ success criteria của phase này nhưng nên vá sớm |
| `report/repository/ReportRepository.java`, `ReportQueryService.java` | nhiều | `home()` gộp ví nhóm nhưng `summary()`/mọi query khác chỉ phạm vi cá nhân | ⚠️ Warning | Không phá vỡ success criteria (nhóm gia đình thuộc Phase 5), nhưng là mâu thuẫn nội tại đã tồn tại ngay trong Phase 4 — nên chốt hướng trước khi Phase 5 mở khoá `group_id` |
| `recurring/service/RecurringService.java` | 153-155 | `update()` không validate `interval >= 1` như `create()` | ⚠️ Warning | Không phá vỡ success criteria — lỗi rơi xuống 500 thay vì 400 |
| `debt/service/DebtService.java` | 525-560 | `toListItem` đọc entity thay vì đọc lại sau trigger (bất đối xứng với `addPayment`) | ⚠️ Warning | Chưa lộ hậu quả thật trong luồng hiện tại |
| `report/service/ReportQueryService.java` | nhiều | `LocalDate.now()` không múi giờ trên toàn tầng nghiệp vụ (budget renewal, recurring runner, debt reminder, report range) | ⚠️ Warning (gốc rễ của CR-01) | Cùng gốc rễ với gap #6 — nên sửa tập trung bằng `AppClock.today()` |

### Kiểm tra hành vi (Behavioral Spot-Checks)

| Hành vi | Lệnh | Kết quả | Trạng thái |
|---|---|---|---|
| Test riêng tư ngân sách BUDGET-08 | `mvn test -Dtest=BudgetPrivacyIntegrationTest` | 1/1 pass | ✓ PASS |
| Test job nhắc nợ (đường vui, mốc 7 ngày/3 ngày) | `mvn test -Dtest=DebtReminderJobIntegrationTest` | 2/2 pass | ✓ PASS (nhưng KHÔNG che phủ nhánh quá hạn — gap vẫn tồn tại) |
| Test export báo cáo (đường vui + 501) | `mvn test -Dtest=ExportJobIntegrationTest` | 5/5 pass | ✓ PASS (nhưng KHÔNG che phủ nhánh lỗi CSDL/race — gap vẫn tồn tại) |
| Migration V9/V10 chạy sạch trên PostgreSQL thật | Flyway log khi chạy Testcontainers | version 9, 10 applied thành công | ✓ PASS |

### Yêu cầu xác minh thủ công (con người)

Không có mục nào cần xác minh thủ công — cả hai gap đều xác minh được hoàn toàn bằng đọc mã nguồn
và chạy test tự động, không phụ thuộc UI/hành vi thời gian thực/dịch vụ ngoài.

### Tóm tắt khoảng trống (Gaps)

Phase 4 đạt chất lượng cao ở phần lớn nghiệp vụ (ngân sách, sổ nợ, mục tiêu, định kỳ, phần đọc
báo cáo) — các quy tắc bất biến khó nhất (ranh giới trigger V4, cộng gộp danh mục con, loại
transfer, chống trùng CSDL cho recurring/budget alert) đều được tôn trọng đúng và có test thật
qua Testcontainers xác nhận.

Tuy nhiên có **2 lỗi nghiệp vụ thật** (không phải chỉ là warning code-style) trực tiếp phá vỡ đúng
hai Success Criteria của ROADMAP:

1. **Job nhắc nợ (Success Criteria #6, DEBT-07/JOB-04)** — logic bên trong `DebtReminderWorker`
   bỏ sót mốc quá hạn đầu tiên, lệch múi giờ, và có thể sinh thông báo trùng khi job chạy lại
   trong cùng ngày. Job "tự chạy hằng ngày theo lịch" đúng về mặt cấu trúc (`@Scheduled` đúng
   cron/zone) nhưng sai về mặt kết quả — không đạt ý nghĩa thật của success criteria.

2. **Xuất báo cáo bất đồng bộ (Success Criteria #5, REPORT-05)** — cơ chế `@Async` + `@Transactional`
   trộn lẫn sai khiến job export có thể kẹt vĩnh viễn ở `status=processing` khi gặp lỗi CSDL hoặc
   khi thắng race trước lúc bản ghi `export_jobs` commit. "Poll tới khi có link tải" không được
   đảm bảo trong các trường hợp lỗi — người dùng sẽ thấy trạng thái "đang xử lý" vô thời hạn.

Cả hai gap đã được `04-REVIEW.md` (code review tự động chạy trước) phát hiện độc lập và được xác
nhận lại bằng cách đọc trực tiếp mã nguồn trong lần xác minh này — không dựa vào SUMMARY.md. Hai
lỗi không phải false positive: đọc mã xác nhận đúng cả ba nhánh hỏng của CR-01 và cả hai lỗ hổng
của CR-02, và bộ test hiện có (161 test PASS toàn dự án) không che phủ các nhánh lỗi này — nên
CI xanh không phát hiện được.

Cả hai gap đều có fix rõ ràng, phạm vi hẹp (đã có code mẫu trong `04-REVIEW.md`), không đòi hỏi
thiết kế lại kiến trúc. Đề xuất một closure plan ngắn xử lý cả CR-01 và CR-02 cùng lúc, kèm test
bổ sung cho các nhánh biên chưa được che phủ.

---

_Xác minh: 2026-08-28T15:40:00Z_
_Người xác minh: Claude (gsd-verifier)_
