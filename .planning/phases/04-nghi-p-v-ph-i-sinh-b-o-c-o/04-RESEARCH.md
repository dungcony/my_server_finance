# Phase 4: Nghiệp vụ phái sinh & Báo cáo - Research

**Researched:** 2026-08-25
**Domain:** Spring Boot 3.4.x nghiệp vụ phái sinh (ngân sách/sổ nợ/định kỳ/mục tiêu/báo cáo) trên trigger PostgreSQL sẵn có + Spring `@Scheduled`/`@TransactionalEventListener`
**Confidence:** HIGH (đối chiếu trực tiếp schema thật + mã nguồn Phase 1-3 đã chạy được) / MEDIUM cho vài điểm thư viện CSV chưa verify version registry

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Hạ tầng thông báo — hợp đồng API MỚI (D-38..D-41)**
- D-38: Tạo bảng `notifications` bằng migration mới (V9/V10 tự chọn số). Viết vào `db/migration/` gốc DATN, không tạo thư mục riêng.
- D-39: Hai endpoint đọc — `GET /notifications` (phân trang) và `PATCH /notifications/{id}/read`. KHÔNG làm `DELETE`/`unread-count`.
- D-40: Viết hợp đồng mới vào `api/11-THONG-BAO.md`. Phải cập nhật bảng "Bản đồ tài liệu" trong `CLAUDE.md` gốc (đang ghi "api/01 đến api/10") trong CÙNG lần thay đổi.
- D-41: Cảnh báo ngân sách sinh NGAY khi ghi giao dịch, qua Spring event xử lý SAU KHI COMMIT. `TransactionWriter` bắn `TransactionRecordedEvent`, module `budget/` đăng ký `@TransactionalEventListener(phase = AFTER_COMMIT)`. Không đảo chiều phụ thuộc, không làm chậm luồng ghi, chống trùng bulk bằng UNIQUE ở CSDL (khoá theo ngân sách + ngày + mức độ). `GET /budgets/alerts` vẫn là nguồn sự thật hiện tại (tính tại chỗ); bản ghi `notifications` là lịch sử tại thời điểm vượt ngưỡng, KHÔNG tự sửa lại khi xoá giao dịch sau đó.

**Xuất báo cáo REPORT-05 (D-42..D-45)**
- D-42: Migration tạo bảng `export_jobs`. Không dùng bộ nhớ, không tái dùng `idempotency_keys`.
- D-43: Tệp lưu trên đĩa máy chủ; `download_url` là đường dẫn TƯƠNG ĐỐI của backend (`/reports/export/{job_id}/download`), xác thực bằng JWT sẵn có, kiểm tra quyền sở hữu job trước khi trả tệp. Hết hạn qua `expires_at` → 410 GONE. KHÔNG sinh token tải riêng trong URL.
- D-44: Phase này CHỈ hỗ trợ `csv`. `pdf`/`excel` trả 501 NOT_IMPLEMENTED. Phải sửa `api/06-BAO-CAO.md` mục 7 trong cùng lần thay đổi.
- D-45: Thêm job dọn tệp/bản ghi export quá hạn hằng ngày, dùng lại khuôn `@Scheduled` của `IdempotencyCleanupJob`.

**Sổ nợ & Mục tiêu — ranh giới trigger (D-46..D-49)**
- Nhắc lại: `debts.paid_amount`, `debts.status`, `savings_goals.saved_amount`, `savings_goals.status` do trigger V4 sở hữu. Backend chỉ chèn/xoá bản ghi `debt_payments`/`goal_contributions`. Muốn biết trạng thái sau khi chèn thì đọc lại.
- D-46: DEBT-04 huỷ một lần trả nợ — xoá `debt_payments` TRƯỚC, rồi mới xoá mềm giao dịch. Cả hai trong một `@Transactional`. Đây là API mà D-32 (Phase 3) trỏ người dùng sang: `DELETE /debts/{id}/payments/{payment_id}`.
- D-47: Trả vượt số còn nợ — CHẶN (giữ nguyên luật CSDL `EXCEEDS_REMAINING_AMOUNT`), nhưng validate trước ở tầng service để trả lỗi 400 sạch với thông điệp tiếng Việt gợi ý cách xử lý. Trigger vẫn là lưới an toàn tầng CSDL.
- D-48: DEBT-06 xoá khoản nợ — xoá CỨNG bản ghi `debts` (không có cột `is_deleted`, thiết kế cố ý), giao dịch liên quan xoá MỀM qua luồng 3 bước. `debt_payments` tự cascade.
- D-49: GOAL-03 hai chế độ nạp dùng đúng trường `create_transaction` (api/09 §285-296), không bịa trường mới. `true` (mặc định) → yêu cầu goal có `wallet_id`, sinh `transfer` qua `TransactionWriter`. `false` → `goal_contributions.transaction_id = NULL`. Không suy đoán từ việc goal có `wallet_id` hay không.

**Giao dịch định kỳ (D-50..D-52)**
- D-50: Kỳ bỏ lỡ ghi ĐÚNG ngày đáng lẽ phải chạy, không dồn vào hôm nay. Điều kiện để `uq_txn_recurring_date` hoạt động đúng.
- D-51: Sinh nhiều kỳ — MỖI KỲ MỘT DB TRANSACTION RIÊNG (cùng nguyên tắc D-34 Phase 3 bulk).
- D-52: Khoản định kỳ lỗi kéo dài — ghi log, bỏ qua, chạy tiếp khoản khác. KHÔNG tự tắt `is_enabled`.

**Báo cáo (D-53..D-55)**
- Hai nguyên tắc bắt buộc từ CLAUDE.md §1-2 (không hỏi lại): loại `type = 'transfer'`, cộng gộp danh mục con qua `fn_category_tree`. Bọc thành query fragment dùng chung (`excludeTransfer()`).
- D-53: `GET /reports/home` là MỘT endpoint gộp trả đủ dữ liệu màn Tổng quan.
- D-54: KHÔNG cache kết quả báo cáo — luôn tính lại.
- D-55: Sửa `source/server/CLAUDE.md` dòng 75 trong phase này — bỏ `AT TIME ZONE 'Asia/Ho_Chi_Minh'` khi group theo ngày/tháng vì `transactions.date` là `DATE` thuần không có múi giờ.

**Chia plan và test (D-56..D-58)**
- D-56: 6-7 plan theo ranh giới nghiệp vụ, thứ tự: (1) Hạ tầng thông báo — đi đầu, (2) Ngân sách, (3) Sổ nợ, (4) Mục tiêu tiết kiệm, (5) Định kỳ, (6) Báo cáo, (7) Tác vụ nền.
- D-57: Gom cả bốn tác vụ nền (JOB-01..04) + job dọn export vào MỘT plan riêng cuối phase.
- D-58: Test theo luồng rủi ro — ưu tiên BUDGET-08, trigger debt/goal mở lại trạng thái, định kỳ ngày 31 qua tháng 2 + bắt kịp nhiều kỳ, kỳ lỗi giữa chừng, báo cáo transfer + cộng gộp danh mục con, hồi quy Phase 1-3.

### Claude's Discretion

Không cần hỏi lại, làm theo chuẩn dự án, `api/*.md` và pattern Phase 1-3:
- Tên bảng/cột cụ thể trong migration mới (`notifications`, `export_jobs`) và số hiệu V9/V10
- Cấu trúc package con trong `budget/`, `debt/`, `goal/`, `recurring/`, `report/`, `notification/` (feature-first)
- Tên class/method — tiếng Anh, không ngoại lệ kể cả tên method test
- Tổ chức DTO, cách chống N+1 trong truy vấn báo cáo
- Cách bọc `excludeTransfer()` và `fn_category_tree` thành fragment dùng chung
- Ánh xạ view `v_budget_progress` (native query hay DTO projection)
- Thư viện/cách viết csv (viết tay hay dùng thư viện nhỏ)
- Thứ tự task trong từng plan (nhưng plan hạ tầng thông báo phải đi đầu theo D-56)
- BUDGET-05 gợi ý hạn mức: công thức đã rõ ở `api/05` §273-279, không cần bàn thêm

### Deferred Ideas (OUT OF SCOPE)

- Xuất pdf/excel (D-44) — chỉ csv ở phase này.
- `DELETE /notifications` và `GET /notifications/unread-count` (D-39).
- Gửi thông báo đẩy thật (FCM/email) — bảng `notifications` chỉ là hộp thư trong app.
- Bản ghi cảnh báo ngân sách không tự sửa lại khi người dùng xoá giao dịch (D-41) — chấp nhận có chủ đích.
- JOB-05 dọn `ai_drafts` discarded 90 ngày — thuộc Phase 5.
- Tầng quyền nhóm gia đình phủ lên ngân sách/báo cáo — Phase 5.
- Mock data app Flutter cho module Phase 4.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BUDGET-01 | Danh sách ngân sách, tính động, cộng gộp danh mục con, 3 trạng thái | `v_budget_progress` (V6) đã làm sẵn — mục "Ánh xạ v_budget_progress" |
| BUDGET-02 | Tổng quan + chi tiết ngân sách kèm dự báo ngày hết | Mục "Dự báo ngày hết ngân sách" |
| BUDGET-03 | Tạo ngân sách, chặn trùng qua `ex_bud_no_overlap` | Mục "Migration V3 budgets — ràng buộc EXCLUDE" |
| BUDGET-04 | Sửa/xoá ngân sách, không sửa `category_id`/`period_type` | Mục "Chuẩn REST CRUD ngân sách" |
| BUDGET-05 | Gợi ý hạn mức TB 3 kỳ × 1.05 | Mục "BUDGET-05 gợi ý hạn mức" |
| BUDGET-06 | Danh sách cảnh báo severity | Mục "Bảng `notifications` + `GET /budgets/alerts`" |
| BUDGET-07 | Job tự động lặp kỳ ngân sách | Mục "JOB-02 lặp kỳ ngân sách" |
| BUDGET-08 | Test riêng tư ngân sách (Testcontainers) | Mục "Test riêng tư BUDGET-08" |
| DEBT-01..07 | Sổ nợ đầy đủ, ranh giới trigger | Mục "Ranh giới trigger debt/goal" + "Luồng nghiệp vụ debt" |
| RECUR-01..03 | Định kỳ, ngày 29/30/31, bắt kịp kỳ bỏ lỡ | Mục "RECUR-03 sinh giao dịch định kỳ" |
| GOAL-01..04 | Mục tiêu tiết kiệm, 2 chế độ nạp | Mục "Luồng nghiệp vụ goal" |
| REPORT-01..05 | Báo cáo, xuất async | Mục "Nguyên tắc báo cáo" + "REPORT-05 xuất file async" |
| JOB-01..04 | 4 job nền hằng ngày | Mục "Cấu hình @Scheduled" |
</phase_requirements>

## Summary

Phase 4 xây trên nền `TransactionWriter` (D-31) đã ổn định — không viết lại logic cập nhật số dư ví. Rủi ro kỹ thuật chính không nằm ở việc học công nghệ mới (toàn bộ dùng Spring Data JPA/JdbcTemplate/`@Scheduled` đã có mẫu từ Phase 1-3) mà nằm ở **ranh giới quyền sở hữu dữ liệu giữa backend và 6 trigger PostgreSQL có sẵn**, và ở **hai điểm tài liệu API còn sai/thiếu mà bắt buộc phải sửa trong phase này**.

Đã xác nhận qua đọc trực tiếp `db/migration/V3, V4, V6, V8`: view `v_budget_progress` (V6) đã tự cộng gộp danh mục con và có điều kiện quyền vá sẵn — backend chỉ cần map view này, không viết lại logic tính. Trigger `trg_debt_payments_sync`/`trg_goal_contributions_sync` (V4) sở hữu 4 cột `paid_amount`/`status`/`saved_amount`/`status`, chạy `AFTER INSERT OR UPDATE OR DELETE ... FOR EACH ROW`, luôn `SUM()` lại toàn bảng con và `RETURN NULL` (không sửa `NEW`) — backend **bắt buộc phải SELECT lại bản ghi cha sau khi INSERT/DELETE bảng con** vì các UPDATE của trigger không phản ánh vào entity Java đang giữ trong Hibernate persistence context.

Hai phát hiện quan trọng ngoài phạm vi CONTEXT.md đã liệt kê: (1) `transactions.counts_in_report` (thêm ở V8) là nguyên tắc thứ BA mà mọi báo cáo phải lọc, ngoài `type != 'transfer'` và cộng gộp danh mục con — CONTEXT chưa nhắc rõ điểm này; (2) lỗi múi giờ báo cáo (`AT TIME ZONE 'Asia/Ho_Chi_Minh'`) không chỉ nằm ở `source/server/CLAUDE.md` dòng 75 như D-55 ghi, mà còn tồn tại nguyên vẹn ở CẢ `api/00-QUY-UOC-CHUNG.md` mục 13 VÀ `api/06-BAO-CAO.md` mục "Ba nguyên tắc chung #3" — ba chỗ sai, không phải một.

**Primary recommendation:** Map `v_budget_progress` bằng interface projection native query; gọi lại đúng `TransactionWriter.write()` cho mọi giao dịch phái sinh (debt/goal/recurring); SELECT lại bản ghi debt/goal sau khi ghi bảng con thay vì tự tính; mỗi kỳ định kỳ/mỗi export là một `@Transactional` riêng theo đúng mẫu `TransactionBulkService` đã có; sửa cả ba chỗ tài liệu sai múi giờ trong cùng lần đổi model báo cáo.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tính tiến độ ngân sách (`spent_amount`/`status`) | Database (view) | API/Backend (đọc) | `v_budget_progress` đã đóng gói toàn bộ logic cộng gộp + quyền; backend chỉ map, không tính lại |
| Đồng bộ `paid_amount`/`saved_amount`/`status` | Database (trigger) | — | Trigger V4 sở hữu tuyệt đối, backend KHÔNG được ghi |
| Ghi giao dịch thật (debt/goal/recurring) | API/Backend | Database (adjustBalance atomic) | Gọi lại `TransactionWriter`, không viết lại logic cập nhật ví |
| Cảnh báo ngân sách theo thời gian thực | API/Backend (event listener) | Database (UNIQUE chống trùng) | `@TransactionalEventListener(AFTER_COMMIT)`, không chặn luồng ghi |
| Sinh giao dịch định kỳ hằng ngày | API/Backend (`@Scheduled`) | Database (`uq_txn_recurring_date`) | Backend quét + gọi TransactionWriter, DB là lưới an toàn cuối chống trùng |
| Xuất báo cáo CSV | API/Backend (`@Async`) | Local disk (file) | Không CDN/S3 — đĩa máy chủ + endpoint tải có JWT |
| Báo cáo thu chi/xu hướng | API/Backend (query fragment dùng chung) | Database (index sẵn có) | Tính tại chỗ, không cache — dữ liệu vài nghìn dòng/user |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.13 (đã chốt trong `pom.xml`, xác nhận đọc trực tiếp) | Framework nền | Dự án đã dùng từ Phase 1 |
| Spring Data JPA | theo Spring Boot BOM 3.4.13 | CRUD budget/debt/goal/recurring/notification | Đã dùng nhất quán Phase 1-3 |
| Spring JDBC (`JdbcTemplate`) | theo Spring Boot BOM | Query báo cáo phức tạp, native mapping `v_budget_progress` | Đã dùng ở `TransactionService.detail()` cho `ai_drafts` |
| `spring-boot-starter-aop` | đã có trong pom.xml | (Không cần mới cho Phase 4 — `@TransactionalEventListener` không cần AOP riêng) | — |
| MapStruct | theo `${mapstruct.version}` đã khai trong pom.xml | Entity ↔ DTO | Dự án đã dùng Phase 1-3 [VERIFIED: pom.xml] |
| Testcontainers PostgreSQL | đã có `spring-boot-testcontainers` + `postgresql` test scope | Test tích hợp trigger/view thật | Bắt buộc từ Phase 3, H2 không hỗ trợ EXCLUDE/trigger PL/pgSQL |
| Lombok | đã có | Giảm boilerplate entity/DTO | Đã dùng |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@Scheduled` (Spring, built-in) | Spring Boot BOM | JOB-01..04 + job dọn export | KHÔNG cần thêm dependency — `@EnableScheduling` đã bật ở `FinanceAppApplication` [VERIFIED: đọc mã nguồn] |
| `@Async` + `CompletableFuture` (Spring, built-in) | Spring Boot BOM | REPORT-05 xuất CSV bất đồng bộ | Cần thêm `@EnableAsync` (CHƯA có trong `FinanceAppApplication` — phải bổ sung), và khai báo `TaskExecutor` bean riêng cho export |
| `java.io.BufferedWriter` / thủ công viết CSV | JDK 17 built-in | Sinh file csv | Format đơn giản (số, ngày, tiếng Việt có dấu UTF-8 với BOM cho Excel mở đúng); tránh thêm dependency mới không cần thiết theo "Claude's Discretion" |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Viết CSV tay | Apache Commons CSV / OpenCSV | Thư viện xử lý escape dấu phẩy/xuống dòng chuẩn hơn, nhưng thêm dependency cho tính năng đơn giản (số tiền, ngày, tên danh mục — ít ký tự đặc biệt). Khuyến nghị: viết tay với `StringEscapeUtils`-style quoting thủ công (bọc field có dấu phẩy/dấu ngoặc kép/xuống dòng trong `"..."`, escape `"` thành `""`) |
| `ThreadPoolTaskScheduler` riêng cho `@Scheduled` | Giữ nguyên scheduler mặc định của Spring (1 thread) | 5 job đều chạy hằng ngày ở giờ khác nhau (không đụng nhau về thời gian) — 1 thread vẫn đủ vì các cron time khác nhau. Cân nhắc `ThreadPoolTaskScheduler` (pool size 2-3) CHỈ nếu lo job A chạy lâu (vd. quét toàn bộ ví) chặn job B tới giờ — xem mục "Cấu hình @Scheduled" |
| `@Async` executor mặc định (`SimpleAsyncTaskExecutor`) | `ThreadPoolTaskExecutor` khai báo tường minh | Mặc định tạo thread mới không giới hạn — với export CSV (I/O ngắn, ít đồng thời ở đồ án) rủi ro thấp nhưng nên giới hạn pool (vd. core=2, max=4) để tránh nhiều request export cùng lúc tạo quá nhiều thread |

**Installation:** Không cần thêm dependency mới vào `pom.xml` cho Phase 4 nếu chọn viết CSV tay và dùng `@Async`/`@Scheduled` built-in của Spring. Chỉ cần bổ sung `@EnableAsync` vào `FinanceAppApplication` hoặc một `@Configuration` riêng.

**Version verification:** `spring-boot-starter-parent` 3.4.13 đã xác nhận trực tiếp trong `pom.xml` [VERIFIED: đọc file]. Không cần verify thêm version nào khác vì không thêm dependency mới.

## Architecture Patterns

### System Architecture Diagram

```
[POST /transactions...]                    [POST /debts, /goals/{id}/contributions,
        │                                   /recurring run-now hoặc @Scheduled quét]
        ▼                                              │
 TransactionService.create()/update()                  ▼
        │                                    debt/goal/recurring Service
        ▼                                              │
 TransactionWriter.write() ──INSERT──► transactions    │ gọi lại
        │                                              ▼
        │ (sau commit)                       TransactionWriter.write()
        ▼                                    (cùng điểm ghi duy nhất D-31)
 ApplicationEventPublisher                              │
   .publishEvent(TransactionRecordedEvent)               ▼
        │                                     transactions + wallets.current_balance
        ▼                                     (atomic UPDATE, không load-modify-save)
 @TransactionalEventListener(AFTER_COMMIT)               │
   BudgetAlertListener (budget/)                          │
        │                                                 ▼
        ▼                                     debt_payments / goal_contributions
 v_budget_progress (SELECT lại,                (INSERT/DELETE bảng con)
  không tính tay) → so ratio                              │
        │                                                 ▼
        ▼                                     TRIGGER trg_debt_payments_sync /
 INSERT notifications                          trg_goal_contributions_sync
  (UNIQUE chặn trùng bulk)                     → tự SUM() → UPDATE debts.paid_amount/
                                                  status, savings_goals.saved_amount/status
                                                            │
                                                            ▼
                                                  Service SELECT LẠI bản ghi cha
                                                  (không tự suy đoán rồi ghi đè)

[GET /reports/*] ─► Report Service ─► query fragment dùng chung:
    excludeTransfer() AND counts_in_report = TRUE AND category_id IN fn_category_tree(...)
    ─► tính tại chỗ, KHÔNG cache ─► DTO trả về

[GET /reports/export?format=csv] ─► ExportService.create()
    ─► INSERT export_jobs (status=processing) ─► trả 202 + job_id
    ─► @Async writeCSV(jobId) chạy nền ─► ghi file lên đĩa
    ─► UPDATE export_jobs SET status=completed, file_path, expires_at
    [poll] GET /reports/export/{job_id} ─► đọc export_jobs
    [tải] GET /reports/export/{job_id}/download ─► kiểm tra owner + expires_at
        → còn hạn: trả file; hết hạn: 410 GONE

[@Scheduled hằng ngày, zone=Asia/Ho_Chi_Minh]
    JOB-01 đối chiếu số dư ví     ─┐
    JOB-02 lặp kỳ ngân sách        ├─ mỗi job try/catch riêng, không crash scheduler pool
    JOB-03 sinh giao dịch định kỳ  │  (mỗi kỳ = 1 @Transactional riêng, D-51)
    JOB-04 nhắc nợ đến hạn        ─┘
    + job dọn export_jobs quá hạn (D-45)
```

### Recommended Project Structure
```
src/main/java/com/datn/financeapp/
├── notification/         # D-56: đi đầu — bảng notifications, GET/PATCH endpoint
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   └── service/
├── budget/                # v_budget_progress mapping, CRUD, suggestion, alerts, BudgetAlertListener
│   ├── controller/ dto/ entity/ repository/ service/
│   └── event/              # BudgetAlertListener (@TransactionalEventListener)
├── debt/                   # debts + debt_payments, KHÔNG ghi paid_amount/status
├── goal/                   # savings_goals + goal_contributions, KHÔNG ghi saved_amount/status
├── recurring/              # recurring_transactions + job sinh giao dịch (RECUR-03/JOB-03)
├── report/                 # report queries + export_jobs + ExportService (@Async)
│   └── export/
├── scheduler/              # JOB-01..04 + job dọn export — MỘT plan cuối (D-57)
└── transaction/
    └── event/              # TransactionRecordedEvent (record), publish trong TransactionWriter
```

### Pattern 1: Đọc lại bản ghi sau khi trigger CSDL cập nhật (Debt/Goal)

**What:** Sau khi INSERT/DELETE `debt_payments` hoặc `goal_contributions`, trigger `AFTER INSERT OR UPDATE OR DELETE` chạy trong CÙNG transaction PostgreSQL, `UPDATE`s bảng cha và `RETURN NULL`. Vì đây là câu UPDATE trực tiếp trên CSDL (không đi qua Hibernate entity manager), entity `Debt`/`SavingsGoal` đã load trước đó trong persistence context sẽ giữ giá trị CŨ (stale) nếu không query lại.

**When to use:** Mọi nơi cần đọc `paid_amount`/`status`/`saved_amount`/`status` ngay sau khi ghi/xoá bảng con trong cùng request.

**Example:**
```java
// Source: đối chiếu db/migration/V4__so_no_muc_tieu.sql (fn_debt_payments_sync, RETURN NULL,
// AFTER trigger) + mẫu WalletRepository.findCurrentBalanceNative() đã dùng đúng kỹ thuật này
// cho wallets.current_balance sau adjustBalance() (TransactionWriter.java dòng 37-43).
@Transactional
public DebtPaymentResponse addPayment(UUID userId, UUID debtId, CreatePaymentRequest req) {
    Debt debt = debtRepository.findByIdAndUserId(debtId, userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", 404, "Không tìm thấy khoản nợ."));

    // Validate TRƯỚC ở tầng service (D-47) để trả lỗi 400 sạch, trigger vẫn là lưới an toàn.
    long remaining = debt.getPrincipalAmount() - debt.getPaidAmount();
    if (req.amount() > remaining) {
        throw new BusinessException("EXCEEDS_REMAINING_AMOUNT", 400,
                String.format("%s chỉ còn nợ %,d đ. Ghi trả %,d đ, phần dư ghi thành khoản thu riêng.",
                        debt.getCounterpartyName(), remaining, remaining));
    }

    // Tạo giao dịch thật qua TransactionWriter (không viết lại logic cập nhật ví)
    TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(/* ... */));

    // Chỉ INSERT bảng con — KHÔNG ghi debt.paidAmount/status
    debtPaymentRepository.save(DebtPayment.builder()
            .debtId(debtId).transactionId(result.transactionId())
            .amount(req.amount()).paidDate(req.paidDate() != null ? req.paidDate() : LocalDate.now())
            .build());

    // BẮT BUỘC đọc lại — entity manager không biết trigger vừa UPDATE bảng debts
    Debt reloaded = debtRepository.findById(debtId).orElseThrow();
    return DebtPaymentResponse.of(reloaded, result);
}
```

**Lưu ý kỹ thuật JPA cụ thể:** `debtRepository.findById(debtId)` sau khi entity `debt` đã bị load trong CÙNG persistence context (Hibernate first-level cache / identity map) sẽ **KHÔNG tự động query lại DB** — Hibernate trả về đúng instance đã cache (stale). Có ba cách xử lý, chọn một:
1. **`entityManager.refresh(debt)`** — buộc Hibernate query lại DB cho đúng entity đang giữ, đây là cách rõ ràng nhất về ý định.
2. **Native query riêng** (mẫu `findCurrentBalanceNative` đã dùng cho ví) — bypass entity manager hoàn toàn, đơn giản nếu chỉ cần vài cột.
3. **Method riêng dùng `@Query(...)` JPQL** kèm `@QueryHints({@QueryHint(name = "javax.persistence.cache.storeMode", value = "REFRESH")})` hoặc đơn giản gọi lại trong transaction MỚI (không khuyến nghị vì mất tính atomic).

Khuyến nghị: dùng cách (1) `entityManager.refresh()` nếu cần đủ entity đầy đủ để trả DTO, hoặc cách (2) nếu chỉ cần 1-2 trường (nhanh, nhất quán với pattern `WalletRepository.findCurrentBalanceNative`).

### Pattern 2: Sự kiện sau commit cho cảnh báo ngân sách (D-41)

**What:** `TransactionWriter.write()` publish `TransactionRecordedEvent` (record bất biến chứa `userId`, `walletId`, `categoryId`, `type`, `date`) qua `ApplicationEventPublisher`. Module `budget/` lắng nghe bằng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.

**When to use:** Mọi tác vụ phụ trợ (side-effect) không được phép chặn hoặc rollback luồng ghi giao dịch chính.

**Example:**
```java
// Source: mẫu Spring Framework chuẩn cho @TransactionalEventListener + kỹ thuật
// self-invocation đã áp dụng ở TransactionWriter/IdempotencyTransactionHelper trong dự án.

// transaction/event/TransactionRecordedEvent.java
public record TransactionRecordedEvent(
        UUID transactionId, UUID userId, UUID walletId, UUID categoryId,
        String type, long amount, LocalDate date) {}

// Trong TransactionWriter.write() — publisher là @Component riêng, không tự publish trong
// cùng class để tránh nhầm phase (ApplicationEventPublisher.publishEvent() mặc định BEFORE_COMMIT
// nếu gọi ngoài @TransactionalEventListener context — publishEvent tự "đợi" tới commit đúng
// nghĩa handler @TransactionalEventListener; publisher.publishEvent() chỉ cần gọi TRONG
// transaction đang mở, Spring tự defer việc GỌI HANDLER tới sau commit).
eventPublisher.publishEvent(new TransactionRecordedEvent(
        transactionId, cmd.userId(), cmd.walletId(), cmd.categoryId(), cmd.type(), cmd.amount(), cmd.date()));

// budget/event/BudgetAlertListener.java
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertListener {

    private final BudgetProgressRepository budgetProgressRepository; // map v_budget_progress
    private final NotificationRepository notificationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionRecorded(TransactionRecordedEvent event) {
        if ("transfer".equals(event.type()) || event.categoryId() == null) return; // chỉ expense có category
        try {
            List<BudgetProgressRow> affected = budgetProgressRepository
                    .findActiveByUserAndCategory(event.userId(), event.categoryId());
            for (BudgetProgressRow bp : affected) {
                if (bp.status().equals("normal")) continue; // chỉ near_limit/over_limit mới cảnh báo
                String severity = bp.status().equals("over_limit") ? "critical" : "alert";
                // UNIQUE (budget_id, alert_date, severity) ở tầng CSDL chặn trùng khi bulk 50 dòng
                // bắn 50 event cùng ngân sách/mức độ — dùng ON CONFLICT DO NOTHING, không kiểm tra
                // tồn tại trước ở tầng Java (race condition giữa các event xử lý song song).
                notificationRepository.insertBudgetAlertIfNotExists(
                        event.userId(), bp.budgetId(), severity, LocalDate.now());
            }
        } catch (Exception e) {
            // Listener lỗi KHÔNG được rollback transaction đã commit (đã commit rồi) — chỉ log.
            log.error("Lỗi khi xử lý cảnh báo ngân sách cho giao dịch {}", event.transactionId(), e);
        }
    }
}
```

**Vì sao KHÔNG cần `@Async` trên listener:** `AFTER_COMMIT` đã chạy sau khi transaction ghi giao dịch commit xong — luồng HTTP response của request ghi giao dịch **vẫn phải đợi listener chạy xong** (trừ khi thêm `@Async`) vì `@TransactionalEventListener` mặc định đồng bộ với thread gọi. Với BUDGET-06 chỉ đọc 1 view + insert vài dòng notification, chi phí thấp, không cần thêm độ phức tạp của `@Async` (tránh vi phạm nguyên tắc "ghi nhanh hơn quên" bằng cách phức tạp hoá không cần thiết) — nhưng NẾU đo được listener làm chậm response đáng kể, cân nhắc thêm `@Async` sau.

### Pattern 3: Sinh nhiều kỳ định kỳ — mỗi kỳ một transaction riêng (D-51)

**What:** Đúng khuôn `TransactionBulkService` đã có ở Phase 3 — service quét không mang `@Transactional`, gọi `TransactionWriter.write()` (bean khác, có `@Transactional` riêng) trong vòng lặp để mỗi lần gọi tự mở transaction MỚI qua Spring AOP proxy thật.

**When to use:** RECUR-03/JOB-03 khi một khoản định kỳ bị bỏ lỡ nhiều kỳ.

**Example:**
```java
// Source: mẫu TransactionBulkService.createBulk() (Phase 3, đã chạy test thật)
// recurring/service/RecurringRunnerService.java — KHÔNG @Transactional ở method này.
public void runDueRecurring() {
    List<RecurringTransaction> due = recurringRepository.findDue(LocalDate.now());
    for (RecurringTransaction rec : due) {
        try {
            runOneRecurring(rec); // gọi qua bean riêng, mỗi lần 1 transaction
        } catch (Exception e) {
            // D-52: log, bỏ qua, KHÔNG tự tắt is_enabled, tiếp tục khoản khác
            log.error("Lỗi khi sinh giao dịch định kỳ {}", rec.getId(), e);
        }
    }
}

// Bean/method riêng có @Transactional — catch-up loop bên trong CÙNG 1 khoản định kỳ
// nhưng generatePeriod() gọi TransactionWriter (bean khác) => mỗi period vẫn 1 transaction
// riêng theo nghĩa D-51 (transaction của generatePeriod() BAO gồm: kiểm tra trùng + ghi
// transactionWriter.write() + update next_run_date — coi MỘT KỲ là đơn vị atomic, không phải
// "ghi giao dịch" và "update next_run_date" tách rời).
@Transactional
public void runOneRecurring(RecurringTransaction rec) {
    LocalDate cursor = rec.getNextRunDate();
    LocalDate today = LocalDate.now();
    LocalDate originalDay = rec.getStartDate(); // giữ ngày gốc cho tính kỳ sau (29/30/31)

    while (!cursor.isAfter(today) && (rec.getEndDate() == null || !cursor.isAfter(rec.getEndDate()))) {
        try {
            generateOnePeriod(rec, cursor); // INSERT transaction (D-50: date = cursor, đúng kỳ)
        } catch (DataIntegrityViolationException dup) {
            // uq_txn_recurring_date bắt được — đã có giao dịch, bỏ qua, KHÔNG rollback cả loop
            log.warn("Giao dịch định kỳ {} ngày {} đã tồn tại, bỏ qua", rec.getId(), cursor);
        }
        cursor = nextRunDate(originalDay, cursor, rec.getFrequency(), rec.getInterval());
    }
    rec.setNextRunDate(cursor);
    rec.setLastRunDate(today);
    recurringRepository.save(rec);
}
```

**Lưu ý:** Vòng lặp `runOneRecurring` bọc `@Transactional` ở CẤP khoản định kỳ (một khoản = một transaction bao mọi kỳ bị bỏ lỡ của NÓ), khác với `runDueRecurring` không có `@Transactional` (mỗi khoản độc lập với khoản khác, D-52). Đây là điểm KHÁC D-51 diễn giải đơn giản "mỗi kỳ một transaction" — cần làm rõ với planner: nếu muốn ĐÚNG NGHĨA ĐEN "mỗi kỳ 1 transaction database riêng" (để kỳ 5 lỗi không rollback kỳ 1-4), `generateOnePeriod()` phải là method `@Transactional` trên MỘT BEAN KHÁC (không phải `this.generateOnePeriod()` trong cùng class — mất proxy AOP do self-invocation), gọi từ vòng lặp không mang transaction bao ngoài. Xem "Common Pitfalls" bên dưới.

### Pattern 4: Xuất báo cáo async với `@Async` + polling bằng bảng `export_jobs`

**What:** `POST /reports/export` (nhưng theo `api/06` ghi là GET — xem "Open Questions" về mâu thuẫn phương thức HTTP) tạo bản ghi `export_jobs(status='processing')`, trả 202 ngay, kích hoạt `@Async` method chạy nền ghi CSV lên đĩa rồi UPDATE trạng thái.

**Example:**
```java
// report/export/ExportService.java
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ExportJobRepository exportJobRepository;
    private final ReportQueryService reportQueryService; // dùng lại các query fragment báo cáo

    @Transactional
    public ExportJobResponse createJob(UUID userId, ExportRequest req) {
        if (!"csv".equals(req.format())) {
            throw new BusinessException("FORMAT_NOT_SUPPORTED", 501,
                    "Định dạng " + req.format() + " chưa hỗ trợ ở phiên bản này, chỉ hỗ trợ csv.");
        }
        UUID jobId = UUID.randomUUID();
        exportJobRepository.save(ExportJob.builder()
                .id(jobId).userId(userId).format("csv").status("processing")
                .createdAt(Instant.now()).build());
        exportAsyncRunner.runExport(jobId, userId, req); // gọi qua bean khác — tránh self-invocation mất @Async proxy
        return new ExportJobResponse(jobId, "processing", "/reports/export/" + jobId);
    }
}

// report/export/ExportAsyncRunner.java — BEAN RIÊNG, giống lý do tách TransactionWriter/
// IdempotencyTransactionHelper: @Async cũng dùng Spring AOP proxy, self-invocation làm mất annotation.
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportAsyncRunner {

    private final ExportJobRepository exportJobRepository;
    private final ReportQueryService reportQueryService;

    @Async("exportTaskExecutor")
    public void runExport(UUID jobId, UUID userId, ExportRequest req) {
        try {
            Path file = CsvReportWriter.write(reportQueryService.buildExportData(userId, req));
            exportJobRepository.markCompleted(jobId, file.toString(),
                    Instant.now().plus(24, ChronoUnit.HOURS));
        } catch (Exception e) {
            log.error("Lỗi khi xuất báo cáo job {}", jobId, e);
            exportJobRepository.markFailed(jobId, e.getMessage());
        }
    }
}

// Config bắt buộc — FinanceAppApplication hoặc @Configuration riêng
@EnableAsync
// + bean TaskExecutor tên "exportTaskExecutor", core=2, max=4, queue=50
```

**Endpoint tải file:**
```java
@GetMapping("/reports/export/{jobId}/download")
public ResponseEntity<Resource> download(@AuthenticationPrincipal UUID userId, @PathVariable UUID jobId) {
    ExportJob job = exportJobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", 404, "Không tìm thấy tệp xuất."));
    if (job.getExpiresAt().isBefore(Instant.now())) {
        throw new BusinessException("EXPORT_LINK_EXPIRED", 410, "Đường dẫn tải đã hết hạn.");
    }
    // ... trả FileSystemResource
}
```

### Anti-Patterns to Avoid
- **Ghi trực tiếp `debts.paid_amount += amount` hoặc `savings_goals.saved_amount += amount`:** trigger sẽ ghi đè bằng `SUM()` ngay sau đó trong CÙNG statement (trigger `AFTER`) — giá trị backend viết biến mất, "chạy đúng một cách tình cờ" chỉ khi Java tính đúng bằng đúng công thức trigger dùng, cực khó debug khi lệch.
- **Gọi `debtRepository.save(debt)` sau khi sửa `status` thủ công (trừ ngoại lệ `write-off`):** trigger AFTER sẽ chạy lại nếu có UPDATE trên `debt_payments` sau đó, ghi đè `status` — chỉ DEBT-05 (write-off) là ngoại lệ hợp lệ duy nhất được ghi `status` trực tiếp (đã có nhánh giữ nguyên `written_off` trong trigger).
- **Gọi `this.someTransactionalMethod()` trong cùng class cho catch-up loop hoặc export:** mất Spring AOP proxy, `@Transactional`/`@Async` bị bỏ qua âm thầm (bài học đã ghi trong STATE.md từ `IdempotencyTransactionHelper`).
- **Cache kết quả báo cáo (Caffeine, Redis, hay biến static):** vi phạm D-54 trực tiếp — ghi giao dịch xong xem báo cáo vẫn thấy số cũ.
- **Tự tính `spent_amount` bằng Java thay vì đọc `v_budget_progress`:** trùng lặp logic cộng gộp danh mục con + điều kiện quyền đã viết đúng ở CSDL, dễ lệch khi V6 sửa view mà Java không sửa theo.
- **Dùng `@Scheduled` mặc định của Spring rồi giả định nó chạy song song:** mặc định Spring TaskScheduler dùng 1 thread — nếu JOB-01 (quét toàn bộ ví) chạy lâu đúng lúc JOB-02 tới giờ, JOB-02 sẽ ĐỢI JOB-01 xong. Với 5 job đặt giờ khác nhau trong đêm, rủi ro thấp nhưng cần biết rõ hành vi này khi chọn giờ cron.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cộng gộp danh mục con | Điều kiện lọc cây tự viết ở từng query | `CategoryRepository.findCategoryTree()` (đã bọc `fn_category_tree`) | Đã có, dùng lại nguyên vẹn từ Phase 3 |
| Tính tiến độ ngân sách | Query Java tự SUM `transactions` theo điều kiện | Map `v_budget_progress` qua native query/projection | View đã đóng gói đúng công thức + quyền, sửa 1 chỗ (CSDL) áp dụng mọi nơi |
| Đồng bộ `paid_amount`/`saved_amount` | Cộng dồn thủ công trong Service | Chỉ INSERT/DELETE bảng con, đọc lại sau | Trigger V4 đã làm, ghi tay sẽ bị ghi đè hoặc gây nhầm lẫn nguồn sự thật |
| Chống trùng giao dịch định kỳ | Kiểm tra tồn tại bằng SELECT trước INSERT (race condition) | Dựa vào `uq_txn_recurring_date` + bắt `DataIntegrityViolationException` | UNIQUE index đã có sẵn trong schema, là lớp bảo vệ CUỐI đáng tin hơn check-then-act ở Java |
| Đối chiếu số dư ví | Viết lại công thức tính | SQL mẫu có sẵn ở `db/README.md` mục "Đối chiếu số dư định kỳ" | Đã viết sẵn, kiểm chứng đúng theo thiết kế ngày tương lai (D-36/D-37) |
| Cơ chế job bất đồng bộ | Quartz, message queue | `@Scheduled` + `@Async` built-in Spring | Constraint dự án đã chốt "không dùng Quartz", khối lượng công việc nhỏ (đồ án, 1 backend instance) |

**Key insight:** Phần lớn "hạ tầng khó" của phase này (cộng gộp danh mục con, quyền ngân sách, chống trùng định kỳ, tính số dư) đã được giải quyết sẵn ở tầng CSDL từ Phase 1-3. Rủi ro thật sự nằm ở việc **backend vô tình làm lại việc CSDL đã làm** (ghi đè trigger) hoặc **quên đọc lại state sau side-effect CSDL** — không phải ở việc thiếu công cụ.

## Common Pitfalls

### Pitfall 1: Ghi đè giá trị trigger vừa tính (paid_amount/saved_amount/status)
**What goes wrong:** Service tự cộng `debt.setPaidAmount(debt.getPaidAmount() + amount)` rồi `save()` sau khi đã `save()` bản ghi `debt_payments` trong CÙNG transaction — cả hai UPDATE (của Java và của trigger) xảy ra trong 1 transaction PostgreSQL, thứ tự phụ thuộc lúc nào Hibernate flush. Kết quả có thể ĐÚNG tình cờ (nếu Java flush trước, trigger SUM() sau ghi đè đúng bằng) hoặc SAI (nếu Java flush sau, ghi đè giá trị trigger đã tính đúng bằng giá trị Java tính sai/stale).
**Why it happens:** Lập trình viên quen pattern "tính rồi lưu" từ các module Phase 1-3 chưa có trigger tương tự (`wallets.current_balance` do backend chủ động).
**How to avoid:** KHÔNG bao giờ gọi setter trên `paidAmount`/`status` (trừ write-off)/`savedAmount`/`status` của goal trong code Phase 4. Review checklist: grep `setPaidAmount\|setSavedAmount` trong diff trước khi merge mỗi plan debt/goal.
**Warning signs:** Test integration cho DEBT-03/GOAL-03 pass ở lần chạy đầu nhưng field-order-dependent — chạy lại vài lần cho kết quả khác nhau, hoặc pass khi test 1 mình nhưng fail khi chạy cùng test khác (side-effect từ flush order).

### Pitfall 2: Đọc entity stale sau khi trigger UPDATE bảng cha
**What goes wrong:** `debtRepository.findById(debtId)` ngay sau `debtPaymentRepository.save(...)` trong CÙNG method/transaction trả về entity với `paidAmount` CŨ vì Hibernate identity map trả instance đã cache thay vì query lại DB, giống hệt bug đã gặp và note kỹ ở `TransactionWriter`/`WalletRepository.findCurrentBalanceNative` cho `wallets.current_balance`.
**Why it happens:** Hibernate first-level cache không biết PostgreSQL trigger vừa chạy UPDATE ngoài tầm kiểm soát entity manager.
**How to avoid:** Dùng `entityManager.refresh(entity)` hoặc native query riêng để đọc `paidAmount`/`status`/`savedAmount` MỚI, đúng kỹ thuật đã áp dụng cho ví ở Phase 3 (`findCurrentBalanceNative`).
**Warning signs:** Response API trả `status: outstanding` dù CSDL thực tế đã `settled` (kiểm bằng `psql` trực tiếp thấy khác response API).

### Pitfall 3: Self-invocation làm mất `@Transactional`/`@Async` trong catch-up loop và export
**What goes wrong:** Gọi `this.generateOnePeriod(...)` hoặc `this.runExport(...)` từ method khác trong CÙNG class — Spring AOP proxy (cả `@Transactional` lẫn `@Async`) bị bỏ qua âm thầm, không có exception, chỉ là hành vi khác kỳ vọng (chạy đồng bộ thay vì async, hoặc gộp vào transaction bao ngoài thay vì transaction riêng).
**Why it happens:** Java method call trong cùng instance không đi qua proxy Spring tạo ra — bài học đã ghi rõ trong `STATE.md` từ `IdempotencyTransactionHelper` (Phase 1).
**How to avoid:** Tách `generateOnePeriod`/`runExport` sang bean/class riêng (`@Component`), inject và gọi qua bean đó — đúng mẫu `TransactionWriter` (bean riêng D-31) và `ExportAsyncRunner` ở Pattern 4.
**Warning signs:** Test "kỳ 5 lỗi thì kỳ 1-4 vẫn giữ" fail vì tất cả rollback cùng nhau; test export "job chạy nền không block response" fail vì response 202 tới sau khi file đã ghi xong (không thực sự async).

### Pitfall 4: Nhầm HTTP method của `GET /reports/export` — tài liệu ghi GET nhưng có side-effect tạo job
**What goes wrong:** `api/06-BAO-CAO.md` mục 7 ghi `GET /reports/export` nhưng hành vi mô tả là TẠO một `export_jobs` mới mỗi lần gọi (side-effect, không idempotent theo nghĩa REST chuẩn) — GET đáng lẽ không có side-effect.
**Why it happens:** Tài liệu API viết trước khi có thiết kế `export_jobs` chi tiết (REQUIREMENTS.md dòng 31 xác nhận "hoãn tới Phase 4 để thiết kế sát nhu cầu thật").
**How to avoid:** Đây là điểm CẦN NGƯỜI DÙNG QUYẾT trước khi code — xem "Open Questions". Đề xuất: đổi endpoint tạo job thành `POST /reports/export` (khớp quy ước `api/00` mục 14 "Tạo mới → POST"), giữ `GET /reports/export/{job_id}` cho polling. Phải sửa `api/06-BAO-CAO.md` trong cùng lần đổi.
**Warning signs:** Test tạo export job dùng GET nhưng gửi body — không đúng convention HTTP, một số HTTP client/proxy không cho GET có body.

### Pitfall 5: Quên lọc `counts_in_report` ở báo cáo (nguyên tắc thứ 3, chưa có trong CONTEXT.md)
**What goes wrong:** Query báo cáo chỉ lọc `type != 'transfer'` và cộng gộp danh mục con, quên thêm `AND counts_in_report = TRUE` — giao dịch điều chỉnh số dư (`source = 'adjustment'`, sinh từ WALLET-06 kiểm kê Phase 2) khi người dùng chọn không tính vào báo cáo vẫn bị cộng vào thu/chi.
**Why it happens:** V8 (migration thêm cột này) đến SAU thời điểm CONTEXT.md Phase 4 liệt kê "hai nguyên tắc bắt buộc" — CONTEXT chỉ nhắc lại 2 nguyên tắc cũ từ CLAUDE.md gốc, chưa cập nhật nguyên tắc thứ 3 mới xuất hiện ở V8.
**How to avoid:** Query fragment dùng chung (`excludeTransfer()` theo Claude's Discretion) nên đặt tên đúng phạm vi hơn, ví dụ `reportEligibleFilter()`, và LUÔN gồm cả ba điều kiện: `type != 'transfer' AND counts_in_report = TRUE AND is_deleted = FALSE`. Đã thấy pattern này đúng ở `TransactionRepository.summary()` Phase 3 (dòng `t.type <> 'transfer'` nhưng KHÔNG lọc `counts_in_report` — cần kiểm tra lại xem Phase 3 summary có cố ý bỏ qua bộ lọc này hay chưa, xem "Open Questions").
**Warning signs:** Test BUDGET-08-tương tự: user kiểm kê ví hụt 150k (source=adjustment, counts_in_report có thể true/false tuỳ chọn của user), báo cáo tổng chi tháng lệch tuỳ có/không lọc.

### Pitfall 6: Tài liệu múi giờ sai ở BA chỗ, không phải một (khác D-55 ghi)
**What goes wrong:** D-55 chỉ định sửa `source/server/CLAUDE.md` dòng 75, dựa trên ghi chú "api/00 mục 13 đã được sửa ở phase trước". Đọc trực tiếp `api/00-QUY-UOC-CHUNG.md` mục 13 (dòng 292-298) và `api/06-BAO-CAO.md` mục "Ba nguyên tắc chung #3" (dòng 43-45) ở thời điểm research này (2026-08-25) xác nhận **CẢ HAI vẫn còn nguyên câu "tính theo giờ Việt Nam"/"AT TIME ZONE"** — `git log --oneline -- api/00-QUY-UOC-CHUNG.md` chỉ có 3 commit, không có commit nào riêng sửa múi giờ.
**Why it happens:** CONTEXT.md Phase 4 ghi nhận sai trạng thái đã sửa (có thể nhầm với một phase discuss khác, hoặc thay đổi dự kiến chưa thực hiện).
**How to avoid:** Sửa CẢ BA vị trí trong cùng lần thay đổi: `source/server/CLAUDE.md` dòng 75, `api/00-QUY-UOC-CHUNG.md` mục 13, `api/06-BAO-CAO.md` mục "Ba nguyên tắc chung #3". Nội dung thay thế: gom nhóm theo ngày/tháng dùng thẳng `transactions.date` (kiểu `DATE`, không có thành phần giờ, không cần và không được chuyển múi giờ).
**Warning signs:** Grep `AT TIME ZONE\|giờ Việt Nam\|múi giờ` trong toàn bộ `api/*.md` và `CLAUDE.md` trước khi đóng phase — phải trả về 0 kết quả liên quan tới gom nhóm báo cáo.

## Code Examples

### Map `v_budget_progress` qua interface projection
```java
// Source: đối chiếu trực tiếp db/migration/V6__va_loi_bao_mat.sql (view đã vá) +
// mẫu SummaryProjection đã dùng trong TransactionRepository (Phase 3, đã chạy test thật)
public interface BudgetProgressRepository extends JpaRepository<BudgetProgressView, UUID> {

    @Query(value = "SELECT * FROM v_budget_progress WHERE user_id = :userId AND is_active = TRUE "
            + "AND (:isActive IS NULL OR is_active = :isActive) "
            + "AND (:periodType IS NULL OR period_type = :periodType)", nativeQuery = true)
    List<BudgetProgressProjection> findAllForUser(
            @Param("userId") UUID userId, @Param("isActive") Boolean isActive, @Param("periodType") String periodType);

    interface BudgetProgressProjection {
        UUID getId();
        UUID getCategoryId();
        Long getLimitAmount();
        Long getSpentAmount();
        Long getRemaining();
        java.math.BigDecimal getRatio();
        String getStatus();
        Integer getDaysRemaining();
        // ... các cột còn lại của view
    }
}
```
Lưu ý: `v_budget_progress` KHÔNG phải entity table nên không map bằng `@Entity` — dùng `nativeQuery = true` trả `List<Projection>` (Spring Data JPA tự bind theo tên getter, đã dùng đúng mẫu này ở `TransactionRepository.SummaryProjection`).

### Query fragment dùng chung cho báo cáo (đề xuất tên `reportEligibleFilter`)
```java
// report/repository/ReportQueryFragments.java — hằng số SQL string dùng chung, KHÔNG entity
public final class ReportQueryFragments {
    private ReportQueryFragments() {}

    /** Ba điều kiện bắt buộc cho MỌI truy vấn báo cáo: loại transfer, loại adjustment không
     * tính báo cáo, loại đã xoá mềm. (CLAUDE.md §1-2 + V8 counts_in_report). */
    public static final String REPORT_ELIGIBLE =
            "t.type <> 'transfer' AND t.counts_in_report = TRUE AND NOT t.is_deleted";
}
```

### `fn_debt_payments_sync` hành vi thật (đối chiếu trực tiếp V4, không suy đoán)
```sql
-- Source: db/migration/V4__so_no_muc_tieu.sql dòng 78-110 (nguyên văn đã đọc)
CREATE TRIGGER trg_debt_payments_sync
    AFTER INSERT OR UPDATE OR DELETE ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION fn_debt_payments_sync();
-- Trigger SELECT ... FOR UPDATE khoá debts trước khi UPDATE, RAISE EXCEPTION nếu tổng vượt
-- principal_amount (ERRCODE check_violation) — KHÔNG cần backend tự khoá debt lại, trigger
-- đã khoá đúng bản ghi liên quan qua FOR UPDATE trong transaction.
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Quartz cho job phức tạp | `@Scheduled` built-in Spring | Dự án chốt từ đầu (research/SUMMARY.md) | Ít cấu hình hơn, đủ cho khối lượng đồ án |
| Cache kết quả báo cáo (Redis/Caffeine) | Tính tại chỗ mỗi request (D-54) | Quyết định Phase 4 | Đơn giản hơn, đúng với "vài nghìn dòng/user" |

**Deprecated/outdated:** Không có — toàn bộ stack Phase 4 dùng đúng phiên bản/pattern đã chốt từ Phase 1, không đổi.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `@TransactionalEventListener(AFTER_COMMIT)` đủ nhanh để không cần `@Async` riêng cho BudgetAlertListener | Pattern 2 | Nếu view `v_budget_progress` chậm khi nhiều ngân sách, luồng ghi giao dịch bị kéo dài — cần đo thực tế, thêm `@Async` nếu cần |
| A2 | 1 thread scheduler mặc định của Spring đủ cho 5 job hằng ngày đặt giờ khác nhau | Standard Stack / Alternatives Considered | Nếu job JOB-01 (quét toàn bộ ví) chạy lâu bất thường và trùng giờ job khác, cần `ThreadPoolTaskScheduler` — chưa đo runtime thật |
| A3 | Viết CSV tay (không thêm thư viện) đủ đáp ứng REPORT-05 vì dữ liệu ít ký tự đặc biệt | Standard Stack / Alternatives Considered | Nếu `note`/`display_name` chứa dấu phẩy, xuống dòng, dấu ngoặc kép tiếng Việt phức tạp, cần escape đúng chuẩn RFC 4180 — rủi ro thấp nhưng cần test riêng |
| A4 | `POST /reports/export` (không phải GET như api/06 ghi) là hướng đúng để sửa tài liệu | Common Pitfalls #4 | Đây là mâu thuẫn CHƯA được người dùng xác nhận — planner/discuss-phase cần chốt trước khi code |

**Nếu bảng này trống:** Không áp dụng — có 4 điểm cần xác nhận.

## Open Questions

1. **`GET /reports/export` hay `POST /reports/export`?**
   - What we know: `api/06-BAO-CAO.md` mục 7 ghi `GET /reports/export` với tham số qua query string, nhưng hành vi là tạo `export_jobs` mới (side-effect) mỗi lần gọi — vi phạm quy ước `api/00` mục 14 ("Tạo mới → POST").
   - What's unclear: Có phải lỗi đánh máy trong tài liệu gốc, hay cố ý dùng GET vì tham số đơn giản (không cần body)?
   - Recommendation: Đổi sang `POST /reports/export` với body JSON (khớp `api/00` mục 14), sửa `api/06-BAO-CAO.md` trong cùng lần đổi D-44. Cần xác nhận với người dùng trước khi khoá vào plan.

2. **`TransactionRepository.summary()` (Phase 3) đã lọc `counts_in_report` chưa — có cần sửa lại Phase 3 không?**
   - What we know: Đọc mã nguồn `TransactionRepository.java` xác nhận `summary()` (dòng 122-150) CÓ tham số `:countsInReport` nhưng đây là **tham số lọc theo yêu cầu người dùng** (`Boolean countsInReport` — có thể null để không lọc), KHÔNG PHẢI điều kiện cứng luôn `= TRUE` cho báo cáo phái sinh Phase 4.
   - What's unclear: Các endpoint `GET /reports/*` (Phase 4, entity/query mới, KHÔNG dùng lại `TransactionRepository.summary()` của Phase 3) có tự thêm `counts_in_report = TRUE` cứng hay không — đây là quyết định thiết kế mới của Phase 4, không phải sửa lại Phase 3.
   - Recommendation: Query mới ở `report/` package PHẢI hard-code `counts_in_report = TRUE` (không nhận tham số cho phép tắt) — khác hẳn `TransactionRepository.search()`/`summary()` ở `transaction/` package (nơi người dùng được chọn lọc theo `counts_in_report` khi xem SỔ giao dịch, không phải báo cáo tổng hợp).

3. **Ranh giới danh mục "Không phân loại" trong `GET /reports/by-category` (`level=child`) có bao gồm cả giao dịch của danh mục CON đã bị xoá mềm không?**
   - What we know: `fn_category_tree` (bản V6 đã vá) loại trừ danh mục xoá mềm khỏi kết quả cây — nghĩa là giao dịch gắn với danh mục con đã xoá vẫn tồn tại trong `transactions` nhưng `category_id` đó không còn nằm trong `fn_category_tree(parent_id)`.
   - What's unclear: Giao dịch này có nên tính vào tổng của danh mục cha hay không (nó "thuộc về" cha về mặt tiền bạc dù danh mục con đã bị xoá)?
   - Recommendation: Vì `fk_txn_category` là `ON DELETE RESTRICT`, danh mục con có giao dịch KHÔNG THỂ bị xoá cứng — chỉ xoá mềm cũng bị chặn nếu còn giao dịch (theo CAT-04 Phase 2, "không xoá được danh mục còn con/giao dịch/ngân sách đang dùng"). Do đó tình huống này về lý thuyết không xảy ra trong dữ liệu hợp lệ — không cần xử lý đặc biệt, nhưng nên có 1 test xác nhận giả định này đúng.

## Environment Availability

Không áp dụng — Phase 4 không thêm phụ thuộc ngoài (không CDN, không FCM/email thật, không Quartz, không thư viện CSV mới bắt buộc). Toàn bộ chạy trên PostgreSQL 14+ và Spring Boot đã có từ Phase 1.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (`junit-jupiter`) + Testcontainers PostgreSQL — đã cấu hình từ Phase 1/3 |
| Config file | `src/test/resources/application-test.yml` (đã tồn tại, xác nhận qua `find`) |
| Quick run command | `mvn test -Dtest=BudgetProgressIntegrationTest` (hoặc tên class tương ứng plan) |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BUDGET-08 | Ngân sách cá nhân A không thấy chi tiêu của B | integration (Testcontainers) | `mvn test -Dtest=BudgetPrivacyIntegrationTest` | ❌ Wave 0 |
| DEBT-04 | Huỷ trả nợ mở lại `settled → outstanding` | integration | `mvn test -Dtest=DebtPaymentIntegrationTest` | ❌ Wave 0 |
| GOAL-04 | Rút nạp mở lại `completed → in_progress` | integration | `mvn test -Dtest=GoalContributionIntegrationTest` | ❌ Wave 0 |
| RECUR-03 | Ngày 31 qua tháng 2, bắt kịp nhiều kỳ | integration | `mvn test -Dtest=RecurringRunnerIntegrationTest` | ❌ Wave 0 |
| RECUR-03 (D-52) | Kỳ lỗi giữa chừng, các kỳ trước vẫn giữ | integration | cùng file trên, test case riêng | ❌ Wave 0 |
| REPORT-01 | Loại transfer + cộng gộp danh mục con + counts_in_report | integration | `mvn test -Dtest=ReportSummaryIntegrationTest` | ❌ Wave 0 |
| REPORT-05 | Export async trả 202, poll tới completed | integration | `mvn test -Dtest=ExportJobIntegrationTest` | ❌ Wave 0 |
| D-41 | Cảnh báo ngân sách sinh sau commit, bulk 50 dòng không trùng | integration | `mvn test -Dtest=BudgetAlertListenerIntegrationTest` | ❌ Wave 0 |
| Hồi quy Phase 1-3 | Toàn bộ suite vẫn xanh sau khi thêm event | full suite | `mvn test` | ✅ đã tồn tại |

### Sampling Rate
- **Per task commit:** chạy test class liên quan tới module vừa sửa
- **Per wave merge:** `mvn test` (full suite, bắt buộc theo D-58 mục 6 "toàn bộ test Phase 1+2+3 vẫn xanh")
- **Phase gate:** Full suite green trước `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/.../budget/BudgetPrivacyIntegrationTest.java` — covers BUDGET-08 (ưu tiên cao nhất theo D-58)
- [ ] `src/test/java/.../debt/DebtPaymentIntegrationTest.java` — covers DEBT-03/04
- [ ] `src/test/java/.../goal/GoalContributionIntegrationTest.java` — covers GOAL-03/04
- [ ] `src/test/java/.../recurring/RecurringRunnerIntegrationTest.java` — covers RECUR-03
- [ ] `src/test/java/.../report/ReportSummaryIntegrationTest.java` — covers REPORT-01..04
- [ ] `src/test/java/.../report/ExportJobIntegrationTest.java` — covers REPORT-05
- [ ] `src/test/java/.../budget/BudgetAlertListenerIntegrationTest.java` — covers D-41 (bulk 50 dòng không trùng thông báo)
- [ ] Framework: không cần cài mới — Testcontainers/JUnit đã có từ Phase 1

*(Không có gap về framework — chỉ thiếu file test theo module mới của Phase 4.)*

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | Có (kế thừa) | JWT `Bearer` đã có từ Phase 1, không đổi |
| V3 Session Management | Có (kế thừa) | Không đổi ở Phase 4 |
| V4 Access Control | Có | Mọi query budget/debt/goal/recurring/notification/export PHẢI kèm `user_id = :currentUser` trong SQL (CLAUDE.md §7) — đặc biệt endpoint tải file export (D-43: kiểm tra owner + `expires_at`) |
| V5 Input Validation | Có | Validate `amount > 0`, `EXCEEDS_REMAINING_AMOUNT` ở tầng service trước khi chạm trigger (D-47); Bean Validation cho DTO như Phase 1-3 |
| V6 Cryptography | Không thay đổi | Không thêm mã hoá mới ở Phase 4 |

### Known Threat Patterns for {stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|----------------------|
| IDOR đọc `export_jobs` của user khác qua `job_id` đoán được | Information Disclosure | `findByIdAndUserId` (không phải `findById` trần) khi tải file — đúng mẫu `TransactionRepository.findByIdAndUserId` đã có |
| Path traversal khi ghi/đọc file export lên đĩa | Tampering | Sinh tên file từ `UUID` (không dùng input người dùng), lưu trong thư mục cấu hình cố định, không nối path từ request |
| Rò rỉ dữ liệu ngân sách nhóm qua `v_budget_progress` nếu sửa view thiếu điều kiện quyền | Information Disclosure | Không bao giờ sửa `v_budget_progress` mà bỏ điều kiện `(b.user_id... OR b.group_id...)` — đây chính là lỗ hổng V6 đã vá, BUDGET-08 test chốt |
| Đoán `notification.id`/`export_job.id` để đọc thông báo/tệp người khác | Information Disclosure | UUID không tuần tự (đã là quy ước toàn dự án — `gen_random_uuid()`), NHƯNG vẫn PHẢI kiểm tra `user_id` trong query, không dựa vào UUID khó đoán làm cơ chế bảo mật duy nhất |

## Sources

### Primary (HIGH confidence)
- `db/migration/V1__nen_tang.sql` đến `V8__dieu_chinh_so_du.sql` — đọc trực tiếp toàn bộ nội dung
- `db/README.md` — đọc trực tiếp toàn bộ, đặc biệt mục "Trigger có sẵn" và "Việc phải làm ở tầng ứng dụng"
- `api/00-QUY-UOC-CHUNG.md`, `api/05-NGAN-SACH.md`, `api/06-BAO-CAO.md`, `api/08-SO-NO.md`, `api/09-DINH-KY-MUC-TIEU.md` — đọc trực tiếp toàn bộ
- Mã nguồn Phase 1-3: `TransactionWriter.java`, `TransactionService.java`, `TransactionBulkService.java`, `TransactionRepository.java`, `WalletRepository.java`, `CategoryRepository.java`, `GlobalExceptionHandler.java`, `IdempotencyCleanupJob.java`, `BusinessException.java`, `TransactionWriteCommand.java`, `pom.xml`, `application.yml`, `FinanceAppApplication.java`
- `.planning/phases/04-.../04-CONTEXT.md`, `04-DISCUSSION-LOG.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md` — đọc trực tiếp toàn bộ
- `git log --oneline -- api/00-QUY-UOC-CHUNG.md` — xác nhận mục 13 chưa được sửa như D-55 giả định

### Secondary (MEDIUM confidence)
- Không có — toàn bộ nghiên cứu dựa trên đọc trực tiếp file dự án, không cần WebSearch vì không có công nghệ mới bên ngoài stack đã chốt

### Tertiary (LOW confidence)
- Không có

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — không thêm dependency mới, toàn bộ đã verify qua `pom.xml` thật
- Architecture: HIGH — đối chiếu trực tiếp migration + mã nguồn đã chạy test thật ở Phase 1-3
- Pitfalls: HIGH — pattern trigger đọc trực tiếp từ SQL, pattern self-invocation đã có bài học ghi trong STATE.md dự án
- Mâu thuẫn tài liệu (múi giờ, GET/POST export): HIGH về việc PHÁT HIỆN mâu thuẫn (đọc trực tiếp), nhưng CẦN người dùng quyết định hướng sửa

**Research date:** 2026-08-25
**Valid until:** 30 ngày (stack ổn định, không phụ thuộc thư viện bên ngoài dễ đổi version) — nhưng nếu `api/06-BAO-CAO.md`/`api/00` được sửa trước khi plan bắt đầu, cần đọc lại phần "Open Questions"/"Common Pitfalls #4/#6"
