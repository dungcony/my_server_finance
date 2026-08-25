# Phase 4: Nghiệp vụ phái sinh & Báo cáo - Bản đồ Pattern

**Lập bản đồ:** 2026-08-25
**Số file phân tích:** 62 (mới tạo/sửa)
**Số file có analog:** 62/62 (100% — mọi file đều dựa trên khuôn Phase 1-3 đã chạy được)

## File Classification

| File mới/sửa | Vai trò | Luồng dữ liệu | Analog gần nhất | Chất lượng khớp |
|---|---|---|---|---|
| `notification/entity/Notification.java` | model | CRUD | `wallet/entity/Wallet.java` | role-match |
| `notification/repository/NotificationRepository.java` | model (repository) | CRUD + chống trùng UNIQUE | `wallet/repository/WalletRepository.java` (điều kiện quyền trong SQL) | role-match |
| `notification/service/NotificationService.java` | service | request-response (đọc + patch) | `wallet/service/WalletService.java` (CRUD đơn giản, `@Transactional` từng method) | exact |
| `notification/controller/NotificationController.java` | controller | request-response | `transaction/controller/TransactionController.java` | role-match |
| `notification/dto/*.java` | dto | — | `transaction/dto/TransactionListItemResponse.java`, `TransactionListResponse.java` (phân trang) | exact |
| `budget/entity/Budget.java` | model | CRUD | `wallet/entity/Wallet.java` | exact |
| `budget/entity/BudgetProgressView` (projection interface) | model (view mapping) | request-response | Mẫu `TransactionRepository.SummaryProjection` | exact |
| `budget/repository/BudgetRepository.java` | model (repository) | CRUD | `wallet/repository/WalletRepository.java` | exact |
| `budget/repository/BudgetProgressRepository.java` | model (repository, native query) | request-response | `TransactionRepository` (`search`/`summary` — native query + projection) và `CategoryRepository.findCategoryTree` | exact |
| `budget/service/BudgetService.java` | service | CRUD | `wallet/service/WalletService.java` | exact |
| `budget/service/BudgetAlertListener.java` | service (event listener) | event-driven | **Không có analog trực tiếp trong Phase 1-3** — dựng mới theo Pattern 2 của RESEARCH.md (`@TransactionalEventListener`), tham chiếu kỹ thuật self-invocation của `TransactionWriter`/`IdempotencyTransactionHelper` | role-match (kỹ thuật vay từ `TransactionWriter`) |
| `budget/controller/BudgetController.java` | controller | request-response | `wallet/controller/WalletController.java` | exact |
| `budget/dto/*.java` | dto | — | `wallet/dto/*.java` | exact |
| `debt/entity/Debt.java`, `DebtPayment.java` | model | CRUD | `transaction/entity/Transaction.java` (entity đơn giản, `@Builder`) | exact |
| `debt/repository/DebtRepository.java`, `DebtPaymentRepository.java` | model (repository) | CRUD + đọc lại sau trigger | `WalletRepository.findByIdForUser` + `findCurrentBalanceNative` (đọc lại bằng native query sau UPDATE ngoài entity manager) | exact |
| `debt/service/DebtService.java` | service | request-response + gọi `TransactionWriter` | `wallet/service/WalletTransferService.java` (gọi `TransactionWriter`, validate trước khi ghi, `@Transactional` từng method) | exact |
| `debt/controller/DebtController.java` | controller | request-response | `transaction/controller/TransactionController.java` | exact |
| `debt/dto/*.java` | dto | — | `transaction/dto/CreateTransactionRequest.java`, `TransactionDetailResponse.java` | exact |
| `goal/entity/SavingsGoal.java`, `GoalContribution.java` | model | CRUD | `transaction/entity/Transaction.java` | exact |
| `goal/repository/SavingsGoalRepository.java`, `GoalContributionRepository.java` | model (repository) | CRUD + đọc lại sau trigger | Giống `debt/` — `WalletRepository` pattern | exact |
| `goal/service/GoalService.java` | service | request-response + gọi `TransactionWriter` (D-49 hai chế độ) | `wallet/service/WalletTransferService.java` (đặc biệt nhánh có/không sinh giao dịch, giống `adjustBalance` có nhánh `difference == 0` không ghi) | exact |
| `goal/controller/GoalController.java` | controller | request-response | `transaction/controller/TransactionController.java` | exact |
| `goal/dto/*.java` | dto | — | `debt/dto/*.java` (cùng shape hai chế độ) | exact |
| `recurring/entity/RecurringTransaction.java` | model | CRUD | `transaction/entity/Transaction.java` | exact |
| `recurring/repository/RecurringTransactionRepository.java` | model (repository) | CRUD + quét `next_run_date` | `WalletRepository` (native query có điều kiện quyền) + `TransactionRepository` (native filter) | role-match |
| `recurring/service/RecurringService.java` | service | request-response (CRUD định kỳ) | `wallet/service/WalletService.java` | exact |
| `recurring/service/RecurringRunnerService.java` | service (không `@Transactional` cấp method quét) | batch (catch-up nhiều kỳ) | `transaction/service/TransactionBulkService.java` (KHÔNG `@Transactional`, gọi bean khác trong vòng lặp để mở transaction riêng, bọc try/catch từng dòng) | exact |
| `recurring/service/RecurringPeriodWriter.java` (bean riêng `@Transactional`) | service (bean hạ tầng) | CRUD (ghi 1 kỳ) | `transaction/service/TransactionWriter.java` (bean `@Component` riêng, một method `@Transactional`, tránh self-invocation) | exact |
| `recurring/controller/RecurringController.java` | controller | request-response | `wallet/controller/WalletController.java` | exact |
| `recurring/dto/*.java` | dto | — | `wallet/dto/*.java` | exact |
| `report/repository/ReportQueryFragments.java` | utility (SQL fragment dùng chung) | transform | Đối chiếu `TransactionRepository` — nơi có `t.type <> 'transfer'` rải trong nhiều `@Query`; đây là bước rút gọn thành hằng số | role-match |
| `report/repository/ReportRepository.java` | model (repository, native query) | request-response (tổng hợp) | `TransactionRepository.summary()` (native query + `SummaryProjection`) + `CategoryRepository.findCategoryTree` | exact |
| `report/service/ReportQueryService.java` | service | request-response | `wallet/service/WalletService.java` (`@Transactional(readOnly = true)` cho mọi method đọc) | exact |
| `report/entity/ExportJob.java` | model | CRUD + file-I/O trạng thái | `transaction/entity/Transaction.java` (entity đơn giản `@Builder`) | role-match |
| `report/repository/ExportJobRepository.java` | model (repository) | CRUD | `WalletRepository` (`findByIdAndUserId`-style quyền, `@Modifying` update trạng thái) | exact |
| `report/service/ExportService.java` | service | request-response (tạo job, trả 202) | `wallet/service/WalletTransferService` (`@Transactional` method tạo bản ghi rồi trả kết quả ngay) | role-match |
| `report/export/ExportAsyncRunner.java` (bean riêng `@Async`) | service (bean hạ tầng) | file-I/O (async) | `transaction/service/TransactionWriter.java` (bean `@Component` riêng để giữ đúng Spring AOP proxy — cùng lý do, khác annotation `@Async` thay vì `@Transactional`) | exact (kỹ thuật) |
| `report/export/CsvReportWriter.java` | utility | file-I/O (transform → CSV) | Không có analog trực tiếp — utility thuần JDK (`BufferedWriter`), không phụ thuộc Spring | không có |
| `report/controller/ReportController.java` | controller | request-response + file download | `transaction/controller/TransactionController.java` (style chung) + `wallet/controller/WalletController.java` (endpoint tải/kiểm tra quyền sở hữu) | role-match |
| `report/dto/*.java` | dto | — | `transaction/dto/TransactionListResponse.java`, `wallet/dto/WalletSummaryResponse.java` (tổng hợp theo nhóm) | exact |
| `scheduler/BudgetRenewalJob.java` (JOB-02) | scheduler | batch | `scheduler/IdempotencyCleanupJob.java` | exact |
| `scheduler/RecurringTransactionJob.java` (JOB-03) | scheduler | batch | `scheduler/IdempotencyCleanupJob.java` + gọi `RecurringRunnerService.runDueRecurring()` | exact |
| `scheduler/DebtReminderJob.java` (JOB-04) | scheduler | batch | `scheduler/IdempotencyCleanupJob.java` | exact |
| `scheduler/WalletReconciliationJob.java` (JOB-01) | scheduler | batch | `scheduler/IdempotencyCleanupJob.java` + SQL đối chiếu có sẵn ở `WalletTransferService.reconcile()` | exact |
| `scheduler/ExportCleanupJob.java` (D-45) | scheduler | batch + file-I/O (xoá đĩa) | `scheduler/IdempotencyCleanupJob.java` | exact |
| `transaction/event/TransactionRecordedEvent.java` | model (record sự kiện) | event-driven | Không có analog Phase 1-3 (sự kiện đầu tiên của dự án) — dựng theo mẫu Spring Framework chuẩn, đặt cùng package `transaction/` vì `TransactionWriter` là nơi publish | không có (mới) |
| `common/exception/BusinessException` (bổ sung mã lỗi mới) | model (exception) | — | Sửa trực tiếp file đã có, không tạo mới | exact |
| `common/exception/GlobalExceptionHandler` (không sửa logic, chỉ tham chiếu) | — | — | — | — |
| `db/migration/V9__thong_bao.sql`, `V10__export_jobs.sql` (ở repo gốc DATN) | migration | — | `db/migration/V3__ngan_sach.sql` (bảng đơn giản + index + FK) | exact |
| Test: `budget/BudgetPrivacyIntegrationTest.java` | test | request-response | `wallet/WalletAccessControlIntegrationTest.java` | exact |
| Test: `debt/DebtPaymentIntegrationTest.java` | test | request-response | `wallet/WalletTransferIntegrationTest.java` (gọi `TransactionWriter` gián tiếp qua service, kiểm tra số dư) | role-match |
| Test: `goal/GoalContributionIntegrationTest.java` | test | request-response | Giống `debt/DebtPaymentIntegrationTest.java` | role-match |
| Test: `recurring/RecurringRunnerIntegrationTest.java` | test | batch | `transaction/TransactionBulkIntegrationTest.java` (mỗi dòng 1 transaction riêng, lỗi giữa chừng không cuốn dòng trước) | exact |
| Test: `report/ReportSummaryIntegrationTest.java` | test | request-response | `transaction/TransactionListQueryIntegrationTest.java` | role-match |
| Test: `report/ExportJobIntegrationTest.java` | test | file-I/O + async | Không có analog trực tiếp (REPORT-05 là luồng async đầu tiên) — dựng theo mẫu Testcontainers chung của `WalletTransferConcurrencyTest.java` | role-match |
| Test: `budget/BudgetAlertListenerIntegrationTest.java` | test | event-driven | Không có analog trực tiếp — dựng theo mẫu Testcontainers chung + `@TransactionalEventListener` cần `TestConfiguration` để chờ commit (xem ghi chú ở dưới) | không có (mới) |

## Pattern Assignments

### Hạ tầng dùng chung nhất — `TransactionWriter` (KHÔNG viết lại, chỉ gọi lại)

**Nguồn:** `src/main/java/com/datn/financeapp/transaction/service/TransactionWriter.java`

Đây là điểm nối bắt buộc (D-31) cho `debt/`, `goal/`, `recurring/` — **tuyệt đối không tự viết lại
logic cộng/trừ ví**. Mọi service phái sinh gọi `transactionWriter.write(new TransactionWriteCommand(...))`
giống hệt cách `WalletTransferService.transfer()` đã làm.

**Chữ ký cần nhớ khi build `TransactionWriteCommand`:**
```java
public record TransactionWriteCommand(
        UUID id, UUID userId, UUID walletId, UUID destinationWalletId, UUID categoryId,
        String type, long amount, LocalDate date, String note, String displayName,
        String source, boolean countsInReport, UUID recurringId, UUID draftId, String receiptUrl) {}
```
- `id` truyền `null` để `TransactionWriter` tự sinh UUID (dùng khi service không cần biết trước id).
- `source` cho debt/goal nên đặt giá trị nghiệp vụ rõ ràng (ví dụ `"debt"`, `"goal"`) — kiểm tra cột
  `transactions.source` có CHECK constraint hạn chế giá trị hay không trước khi đặt tên tuỳ ý
  (đối chiếu `db/migration/V2__giao_dich.sql`).
- `recurringId` — dùng cho `RecurringPeriodWriter`, các nơi khác truyền `null`.
- `countsInReport` — debt/goal/recurring nên mặc định `true` trừ khi tài liệu API ghi khác.

**Kết quả trả về:**
```java
public record WriteResult(UUID transactionId, long walletNewBalance, Long destinationWalletNewBalance) {}
```

---

### `debt/service/DebtService.java` (service, request-response + validate trước trigger)

**Analog:** `wallet/service/WalletTransferService.java` (đặc biệt method `adjustBalance`, dòng 104-151)
và Pattern 1 của RESEARCH.md.

**Validate trước khi chạm trigger (D-47)** — sao chép đúng khuôn `BusinessException` với message
tiếng Việt gợi ý cách xử lý, giống style `INSUFFICIENT_BALANCE` ở `WalletTransferService` dòng 70-78:
```java
long remaining = debt.getPrincipalAmount() - debt.getPaidAmount();
if (req.amount() > remaining) {
    throw new BusinessException("EXCEEDS_REMAINING_AMOUNT", HttpStatus.BAD_REQUEST.value(),
            String.format("%s chỉ còn nợ %,d đ. Ghi trả %,d đ, phần dư ghi thành khoản thu riêng.",
                    debt.getCounterpartyName(), remaining, remaining));
}
```

**Gọi `TransactionWriter` rồi CHỈ insert bảng con** — không bao giờ set `paidAmount`/`status` tay
(xem Anti-Pattern trong RESEARCH.md và bảng "Ranh giới trigger" ở dưới).

**Đọc lại sau khi trigger UPDATE (Pitfall 2 của RESEARCH.md)** — dùng kỹ thuật giống hệt
`WalletRepository.findCurrentBalanceNative` (native query bypass Hibernate identity map):
```java
// debt/repository/DebtRepository.java — thêm method tương tự findCurrentBalanceNative
@Query(value = "SELECT * FROM debts WHERE id = :id", nativeQuery = true)
Optional<Debt> findByIdNative(@Param("id") UUID id);
// hoặc dùng entityManager.refresh(debt) nếu cần entity đầy đủ trong cùng transaction
```

**Quyền D-27 giống `WalletRepository`:**
```java
// debt/repository/DebtRepository.java
@Query(value = "SELECT * FROM debts WHERE id = :id AND user_id = :userId", nativeQuery = true)
Optional<Debt> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
```

**D-46 huỷ trả nợ — thứ tự bắt buộc trong MỘT `@Transactional`:**
1. `debtPaymentRepository.delete(payment)` (trigger tự SUM lại, tự mở `settled → outstanding`)
2. Xoá mềm giao dịch qua đúng luồng 3 bước của `TransactionService` Phase 3 (gọi lại service đó,
   không viết lại — xem `TransactionService.delete()`)

**D-48 xoá khoản nợ — xoá CỨNG `debts`, xoá MỀM giao dịch:**
```java
debtRepository.delete(debt); // cascade dọn debt_payments (ON DELETE CASCADE)
// rồi gọi transactionService.delete(...) cho origin_transaction_id VÀ mọi transaction_id
// trong debt_payments đã bị cascade xoá (phải lấy danh sách TRƯỚC khi delete debts)
```

---

### `goal/service/GoalService.java` (service, hai chế độ nạp D-49)

**Analog:** `wallet/service/WalletTransferService.adjustBalance()` (nhánh có sinh giao dịch/không —
dòng 104-151) kết hợp Pattern 1.

```java
if (Boolean.TRUE.equals(req.createTransaction())) { // mặc định true theo api/09 §285-296
    if (goal.getWalletId() == null) {
        throw new BusinessException("GOAL_HAS_NO_WALLET", HttpStatus.BAD_REQUEST.value(),
                "Mục tiêu chưa gắn ví, không thể tạo giao dịch thật.");
    }
    TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
            UUID.randomUUID(), userId, req.sourceWalletId(), goal.getWalletId(), null,
            "transfer", req.amount(), date, req.note(), null, "goal", true, null, null, null));
    goalContributionRepository.save(GoalContribution.builder()
            .goalId(goalId).transactionId(result.transactionId())
            .amount(req.amount()).contributedDate(date).build());
} else {
    goalContributionRepository.save(GoalContribution.builder()
            .goalId(goalId).transactionId(null) // nullable + ON DELETE SET NULL (V4)
            .amount(req.amount()).contributedDate(date).build());
}
// Đọc lại savings_goals sau INSERT — cùng kỹ thuật Pitfall 2 ở trên
```

**Không suy đoán từ `wallet_id` có null hay không** — luôn đọc tường minh trường
`create_transaction` từ request (D-49).

---

### `recurring/service/RecurringRunnerService.java` + `RecurringPeriodWriter.java` (D-50, D-51, D-52)

**Analog:** `transaction/service/TransactionBulkService.java` (toàn bộ file, đặc biệt cấu trúc
KHÔNG `@Transactional` ở method quét + bọc try/catch từng phần tử dòng 63-86) và Pattern 3 của
RESEARCH.md.

**Cấu trúc 2 bean bắt buộc để đúng nghĩa "mỗi kỳ một transaction riêng":**
```java
// recurring/service/RecurringRunnerService.java — KHÔNG @Transactional ở method quét
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringRunnerService {
    private final RecurringTransactionRepository recurringRepository;
    private final RecurringPeriodWriter periodWriter; // bean khác — giữ proxy AOP

    public void runDueRecurring() {
        List<RecurringTransaction> due = recurringRepository.findDue(LocalDate.now());
        for (RecurringTransaction rec : due) {
            try {
                periodWriter.runOneRecurring(rec.getId()); // truyền ID, không truyền entity đã load
            } catch (Exception e) {
                // D-52: log, bỏ qua, KHÔNG tự tắt is_enabled, tiếp tục khoản khác
                log.error("Lỗi khi sinh giao dịch định kỳ {}", rec.getId(), e);
            }
        }
    }
}

// recurring/service/RecurringPeriodWriter.java — bean RIÊNG, mỗi kỳ 1 @Transactional thật
@Component
@RequiredArgsConstructor
public class RecurringPeriodWriter {
    private final TransactionWriter transactionWriter;
    private final RecurringTransactionRepository recurringRepository;

    @Transactional
    public void generateOnePeriod(UUID recurringId, LocalDate periodDate) {
        // D-50: date = periodDate (ngày ĐÚNG kỳ bị bỏ lỡ, không dồn vào hôm nay)
        try {
            transactionWriter.write(new TransactionWriteCommand(/* ..., date = periodDate, recurringId = recurringId */));
        } catch (DataIntegrityViolationException dup) {
            // uq_txn_recurring_date bắt được — đã tồn tại, bỏ qua (idempotent khi job chạy lại)
        }
    }
}
```

**Ngày 29/30/31 giữ nguyên ngày gốc cho kỳ sau** — logic tính `nextRunDate` là utility thuần Java
(`LocalDate`), không có analog trực tiếp trong Phase 1-3, viết mới theo đặc tả `api/09-DINH-KY-MUC-TIEU.md`
§113-125.

---

### `report/repository/ReportQueryFragments.java` + `ReportRepository.java` (D-53, D-54, D-61, Pitfall 5)

**Analog:** `transaction/repository/TransactionRepository.java` (native query + `SummaryProjection`,
dòng 122-158) và `CategoryRepository.findCategoryTree()`.

**Ba điều kiện bắt buộc — hard-code, KHÔNG nhận tham số cho phép tắt (khác `TransactionRepository`):**
```java
public final class ReportQueryFragments {
    private ReportQueryFragments() {}
    public static final String REPORT_ELIGIBLE =
            "t.type <> 'transfer' AND t.counts_in_report = TRUE AND NOT t.is_deleted";
}
```

**Cộng gộp danh mục con** — gọi lại `CategoryRepository.findCategoryTree(categoryId)` rồi bind
mảng UUID y hệt `TransactionRepository.search()` dòng 53:
```java
+ "AND (CAST(:categoryTree AS uuid[]) IS NULL OR t.category_id = ANY(CAST(:categoryTree AS uuid[]))) "
```

**Projection cho tổng hợp** — đúng khuôn `SummaryProjection` (dòng 152-157 `TransactionRepository`):
```java
interface CategoryBreakdownProjection {
    UUID getCategoryId();
    Long getTotalAmount();
}
```

---

### `budget/repository/BudgetProgressRepository.java` (map `v_budget_progress`)

**Analog:** `TransactionRepository.summary()` (native query trả projection) — `v_budget_progress`
đã đóng gói sẵn cộng gộp danh mục con + điều kiện quyền (V6), backend chỉ map, **không tính lại**.

```java
public interface BudgetProgressRepository extends JpaRepository<BudgetProgressView, UUID> {
    @Query(value = "SELECT * FROM v_budget_progress WHERE user_id = :userId AND is_active = TRUE",
            nativeQuery = true)
    List<BudgetProgressProjection> findActiveForUser(@Param("userId") UUID userId);

    interface BudgetProgressProjection {
        UUID getId();
        UUID getCategoryId();
        Long getLimitAmount();
        Long getSpentAmount();
        Long getRemaining();
        java.math.BigDecimal getRatio();
        String getStatus();
        Integer getDaysRemaining();
    }
}
```
Lưu ý view KHÔNG phải bảng entity — không dùng `@Entity`, luôn `nativeQuery = true`.

---

### `budget/service/BudgetAlertListener.java` (event-driven, D-41)

**Không có analog trực tiếp trong Phase 1-3** — pattern mới đầu tiên của dự án dùng
`@TransactionalEventListener`. Tham khảo kỹ thuật self-invocation từ `TransactionWriter`
(bean `@Component` riêng) và nội dung Pattern 2 đầy đủ ở `04-RESEARCH.md` (dòng 267-324) —
sao chép nguyên khối code mẫu ở đó, bao gồm:
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
- Bọc try/catch toàn bộ method — listener lỗi KHÔNG được ném ra ngoài (transaction ghi giao dịch
  đã commit rồi, ném exception ở đây chỉ log, không rollback được gì)
- Chống trùng bulk bằng `INSERT ... ON CONFLICT DO NOTHING` ở tầng repository (UNIQUE CSDL), không
  kiểm tra tồn tại trước ở Java

**Publish event trong `TransactionWriter.write()`** — sửa file đã có, thêm field
`ApplicationEventPublisher` + gọi `eventPublisher.publishEvent(...)` ngay sau khi ghi giao dịch
thành công (trước `return`). Việc sửa `TransactionWriter` **động tới mọi test Phase 1-3** — chạy
lại toàn bộ `mvn test` sau khi sửa (D-58 mục 6).

---

### `report/service/ExportService.java` + `ExportAsyncRunner.java` (D-42..D-45, D-59)

**Analog kỹ thuật tách bean:** `transaction/service/TransactionWriter.java` (lý do tách bean —
tránh self-invocation mất proxy AOP, ở đây là `@Async` thay vì `@Transactional`).

**Analog CRUD tạo bản ghi + trả kết quả ngay:** `wallet/service/WalletTransferService.adjustBalance()`
(dòng 104-151 — validate, ghi 1 bản ghi trong `@Transactional`, trả DTO).

```java
@Service
@RequiredArgsConstructor
public class ExportService {
    private final ExportJobRepository exportJobRepository;
    private final ExportAsyncRunner exportAsyncRunner; // bean khác — giữ @Async proxy

    @Transactional
    public ExportJobResponse createJob(UUID userId, ExportRequest req) {
        if (!"csv".equals(req.format())) {
            throw new BusinessException("FORMAT_NOT_SUPPORTED", HttpStatus.NOT_IMPLEMENTED.value(),
                    "Định dạng " + req.format() + " chưa hỗ trợ, chỉ hỗ trợ csv.");
        }
        UUID jobId = UUID.randomUUID();
        exportJobRepository.save(ExportJob.builder()
                .id(jobId).userId(userId).format("csv").status("processing")
                .createdAt(Instant.now()).build());
        exportAsyncRunner.runExport(jobId, userId, req);
        return new ExportJobResponse(jobId, "processing", "/reports/export/" + jobId);
    }
}
```

**Endpoint tải file (IDOR — `findByIdAndUserId`, không phải `findById` trần):**
```java
@GetMapping("/reports/export/{jobId}/download")
public ResponseEntity<Resource> download(@AuthenticationPrincipal UUID userId, @PathVariable UUID jobId) {
    ExportJob job = exportJobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(),
                    "Không tìm thấy tệp xuất."));
    if (job.getExpiresAt().isBefore(Instant.now())) {
        throw new BusinessException("EXPORT_LINK_EXPIRED", HttpStatus.GONE.value(),
                "Đường dẫn tải đã hết hạn.");
    }
    // trả FileSystemResource — sinh tên file từ UUID, không nối path từ input người dùng
}
```

**`@EnableAsync` còn thiếu** — `FinanceAppApplication.java` hiện chỉ có `@EnableScheduling`
(dòng 8), phải thêm `@EnableAsync` trong cùng lần đổi + khai `TaskExecutor` bean tên
`exportTaskExecutor` (core=2, max=4).

---

### `scheduler/*.java` — 5 job mới (D-57)

**Analog:** `scheduler/IdempotencyCleanupJob.java` (toàn bộ file, 33 dòng) — khuôn duy nhất hiện có,
copy nguyên cấu trúc cho cả 5 job:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionJob {
    private final RecurringRunnerService recurringRunnerService;

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            recurringRunnerService.runDueRecurring(); // method KHÔNG @Transactional — tự bọc mỗi khoản
            log.info("Đã chạy xong tác vụ sinh giao dịch định kỳ");
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ sinh giao dịch định kỳ", e);
        }
    }
}
```

**Lưu ý quan trọng:** `IdempotencyCleanupJob.cleanup()` có `@Transactional` cấp method vì nó chỉ
làm MỘT DELETE. Các job Phase 4 gọi service quét nhiều bản ghi (`JOB-02`, `JOB-03`) **KHÔNG nên**
đặt `@Transactional` ở method `@Scheduled` — để service bên trong tự mở transaction riêng theo
từng đơn vị công việc (D-51/D-52), tránh một lỗi giữa chừng rollback toàn bộ job.

`ExportCleanupJob` (D-45) — xoá file đĩa (JDK `Files.deleteIfExists`) TRƯỚC khi xoá bản ghi
`export_jobs` trong `@Transactional`, để tránh mất bản ghi mà file vẫn còn trên đĩa (ngược lại
chấp nhận được — file rác không nguy hiểm bằng bản ghi trỏ tới file không tồn tại).

---

### Test riêng tư — `budget/BudgetPrivacyIntegrationTest.java` (BUDGET-08, ưu tiên cao nhất D-58)

**Analog:** `wallet/WalletAccessControlIntegrationTest.java` (toàn bộ cấu trúc: `@Testcontainers`,
`@SpringBootTest`, `@AutoConfigureMockMvc`, `NoRateLimitConfig` override `RateLimitFilter`,
`registerAndGetAccessToken` helper, `@BeforeEach cleanTables`).

**Khác biệt cần chỉnh:** kịch bản không phải "A gọi API của B" mà "B chi tiêu, ngân sách CÁ NHÂN
của A cùng danh mục vẫn hiện `spent_amount = 0`" — cần thêm bước tạo ngân sách cho A, tạo giao
dịch cho B cùng danh mục, rồi gọi `GET /budgets/{id}` (hoặc `/budgets/alerts`) bằng token A và
assert `spent_amount == 0`. Đây chính là test chốt lỗ hổng `v_budget_progress` đã vá ở V6.

```java
@Test
void userA_budgetProgress_notAffectedByUserB_spendingSameCategory() throws Exception {
    // 1. Tạo user A, user B, cùng dùng danh mục hệ thống chung (category_id giống nhau)
    // 2. A tạo budget cho category X
    // 3. B tạo transaction expense 1_000_000 vào category X (ví của B)
    // 4. GET /budgets/{budgetOfA} bằng token A -> spent_amount = 0
}
```

**QUAN TRỌNG khi test `@TransactionalEventListener(AFTER_COMMIT)` (BudgetAlertListener):** MockMvc
request thật SẼ commit transaction HTTP request (Spring Boot tự quản lý transaction theo request,
không phải theo test) nên listener chạy đúng như production — KHÔNG cần `@Commit`/`TestTransaction`
đặc biệt như unit test JPA thuần. Nếu dùng `@Transactional` ở CHÍNH class test (như một số test JPA
khác có thể làm) thì listener sẽ KHÔNG chạy vì transaction test tự rollback, không commit — kiểm
tra `WalletTransferConcurrencyTest.java` xem test hiện tại có dùng `@Transactional` ở class hay
không trước khi viết `BudgetAlertListenerIntegrationTest`.

---

## Shared Patterns

### 1. Tách bean để giữ Spring AOP proxy (self-invocation)
**Nguồn:** `transaction/service/TransactionWriter.java` (docblock dòng 12-21) +
`transaction/service/TransactionBulkService.java` (docblock dòng 23-33).
**Áp dụng cho:** `RecurringPeriodWriter` (`@Transactional`), `ExportAsyncRunner` (`@Async`),
`BudgetAlertListener` (`@TransactionalEventListener` — về mặt kỹ thuật ít rủi ro self-invocation
hơn vì luôn được Spring gọi từ ngoài, nhưng vẫn nên là bean riêng theo đúng tinh thần tách trách
nhiệm).
**Quy tắc:** không bao giờ gọi `this.methodMangAnnotation()` trong cùng class — tách sang
`@Component`/`@Service` khác, inject qua constructor, gọi qua field đó.

### 2. Đọc lại bản ghi bằng native query sau UPDATE ngoài entity manager
**Nguồn:** `wallet/repository/WalletRepository.findCurrentBalanceNative()` (dòng 93-104, docblock
giải thích đầy đủ lý do Hibernate identity map trả instance stale).
**Áp dụng cho:** `DebtRepository`/`SavingsGoalRepository` sau khi `debt_payments`/`goal_contributions`
INSERT/DELETE (trigger V4 UPDATE bảng cha ngoài tầm entity manager).

### 3. Điều kiện quyền ngay trong câu SQL, không lọc ở Java
**Nguồn:** `WalletRepository.findByIdForUser()` (dòng 36-41) — mẫu chuẩn D-27/CLAUDE.md §7.
**Áp dụng cho:** mọi repository mới (`BudgetRepository`, `DebtRepository`, `SavingsGoalRepository`,
`RecurringTransactionRepository`, `NotificationRepository`, `ExportJobRepository`) — luôn
`user_id = :currentUser` (Phase 4 chưa có vế `group_id` cho các bảng này trừ `budgets` đã có sẵn
`group_id` ở schema V3, nhưng Phase 4 KHÔNG code phần quyền nhóm — xem `<deferred>` CONTEXT.md).
Không có quyền → `NOT_FOUND` 404, không phải `403`.

### 4. Error handling — `BusinessException` + mã lỗi tiếng Việt rõ ràng
**Nguồn:** `common/exception/BusinessException.java` (toàn bộ, 27 dòng) +
`common/exception/GlobalExceptionHandler.java` (`handleBusiness`, dòng 37-42).
**Áp dụng cho:** mọi service Phase 4. Mã lỗi mới cần thêm: `EXCEEDS_REMAINING_AMOUNT` (400, đã có
sẵn theo `api/08`), `FORMAT_NOT_SUPPORTED` (501), `EXPORT_LINK_EXPIRED` (410), `GOAL_HAS_NO_WALLET`
(400, tự đặt theo Claude's Discretion), `CATEGORY_NOT_ALLOWED`-style cho budget category không phải
loại chi (dù trigger `fn_budgets_validate` đã chặn, validate trước ở service để trả lỗi sạch —
cùng tinh thần D-47).

### 5. `@WebMvcTest` GlobalExceptionHandlerTest — PHẢI thêm mọi Controller mới vào `excludeFilters`
**Nguồn:** `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java`
(dòng 25-45) — đã liệt kê `AuthController`, `WalletController`, `CategoryController`,
`TransactionController`. **Đã dính lỗi này 3 lần** (Phase 1, 2, 3).
**Áp dụng cho:** Phase 4 thêm ~9 controller (`NotificationController`, `BudgetController`,
`DebtController`, `GoalController`, `RecurringController`, `ReportController` — có thể tách thêm
`ExportController` nếu để riêng file) — **mỗi controller mới PHẢI thêm vào mảng `classes = {...}`**
ngay khi tạo, không để tới cuối phase mới nhớ ra.

### 6. Query fragment dùng chung cho báo cáo — ba điều kiện bắt buộc
**Nguồn:** phân tích từ `TransactionRepository.summary()` (dòng 122-127, có `t.type <> 'transfer'`
nhưng KHÔNG tự động có `counts_in_report` vì đó là tham số lọc theo lựa chọn người dùng ở
`transaction/`, khác hẳn ngữ cảnh báo cáo `report/`).
**Áp dụng cho:** MỌI query trong `report/repository/ReportRepository.java` — bắt buộc cả ba:
`t.type <> 'transfer' AND t.counts_in_report = TRUE AND NOT t.is_deleted` (D-61, Pitfall 5 của
RESEARCH.md) — hard-code, không nhận tham số.

### 7. `@Scheduled` với `zone = "Asia/Ho_Chi_Minh"` đặt thẳng trong annotation
**Nguồn:** `scheduler/IdempotencyCleanupJob.java` dòng 23.
**Áp dụng cho:** cả 5 job Phase 4 (`WalletReconciliationJob`, `BudgetRenewalJob`,
`RecurringTransactionJob`, `DebtReminderJob`, `ExportCleanupJob`) — chọn giờ cron KHÁC NHAU trong
đêm (scheduler mặc định 1 thread, xem Anti-Pattern RESEARCH.md).

## No Analog Found

| File | Vai trò | Luồng dữ liệu | Lý do |
|---|---|---|---|
| `transaction/event/TransactionRecordedEvent.java` | model (record sự kiện) | event-driven | Sự kiện Spring đầu tiên của dự án — chưa có `ApplicationEventPublisher`/`@TransactionalEventListener` nào ở Phase 1-3. Dùng nguyên mẫu code ở `04-RESEARCH.md` Pattern 2 (dòng 267-324) |
| `budget/service/BudgetAlertListener.java` | service (event listener) | event-driven | Cùng lý do trên — dùng nguyên mẫu code Pattern 2 của RESEARCH.md, không rút gọn |
| `report/export/CsvReportWriter.java` | utility | file-I/O (transform) | Utility thuần JDK, không có tiền lệ ghi file lên đĩa ở Phase 1-3 (mọi I/O trước đây là DB). Theo RESEARCH.md "Standard Stack": viết tay bằng `BufferedWriter`, escape RFC 4180 thủ công |
| Logic tính `nextRunDate` (ngày 29/30/31 giữ nguyên ngày gốc) | utility (thuần `LocalDate`) | transform | Bài toán lịch thuần Java, không có analog nghiệp vụ trước đó. Đặc tả đầy đủ ở `api/09-DINH-KY-MUC-TIEU.md` §113-125 |
| `budget/BudgetAlertListenerIntegrationTest.java` | test | event-driven | Test đầu tiên cần xác nhận hành vi `AFTER_COMMIT` + chống trùng UNIQUE khi bulk — không có test event-driven nào ở Phase 1-3 để tham chiếu cấu trúc, chỉ tham chiếu khung Testcontainers chung |

## Metadata

**Phạm vi tìm analog:** `src/main/java/com/datn/financeapp/{auth,wallet,category,transaction,common,scheduler}/`,
`src/test/java/com/datn/financeapp/{wallet,transaction,category,common}/`,
`db/migration/V1-V8`
**Số file đã đọc để trích pattern:** 15 file mã nguồn chính (`TransactionWriter`, `TransactionWriteCommand`,
`TransactionBulkService`, `IdempotencyCleanupJob`, `WalletTransferService`, `TransactionRepository`,
`TransactionController`, `WalletRepository`, `Wallet` entity, `BusinessException`,
`GlobalExceptionHandler`, `CategoryRepository`, `WalletAccessControlIntegrationTest`, `WalletService`,
`GlobalExceptionHandlerTest`, `PageRequestParams`) + 2 migration (`V3`, `V4`) + `FinanceAppApplication`
(grep `@EnableScheduling`)
**Ngày trích pattern:** 2026-08-25
