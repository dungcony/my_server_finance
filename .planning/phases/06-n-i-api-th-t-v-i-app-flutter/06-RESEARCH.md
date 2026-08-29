# Phase 6: Nối API thật với app Flutter - Research

**Researched:** 2026-08-29
**Domain:** Tích hợp app Flutter (Dio/Riverpod) với backend Spring Boot thật — không có công nghệ mới, rủi ro nằm ở lệch hợp đồng ngầm giữa hai repo
**Confidence:** HIGH (mọi khẳng định trong phần đối chiếu endpoint/DTO/rate-limit/idempotency đều `[VERIFIED: đọc mã nguồn trực tiếp]`)

<user_constraints>
## User Constraints (từ CONTEXT.md)

### Quyết định đã chốt (D-01 … D-25)

**Phụ thuộc Phase 5 và nhóm `ai`**
- D-01: Nối 6 nhóm trước, hoãn nhóm `ai` — backend chưa có `GroupController`/`AiController` (Phase 5 chưa code).
- D-02: Màn AI khi `USE_MOCK=false` vẫn giữ mock riêng cho `/ai/*` — `MockInterceptor` cần danh sách ngoại lệ vẫn bị chặn dù cờ mock tắt.
- D-03: Success Criteria #6 (duyệt bản nháp AI có sửa → `user_corrections`) không kiểm chứng được trong phase này, dời sang phase nối `ai` sau khi Phase 5 xong.

**Base URL**
- D-04: Prefix chốt là `/v1`, không phải `/api/v1` (căn cứ `api/00-QUY-UOC-CHUNG.md` §2: `api.` là subdomain).
- D-05: Backend thêm `server.servlet.context-path: /v1` trong `application.yml`.
- D-06: App không sửa mã — `app_config.dart` đã mặc định `http://10.0.2.2:8080/v1`.
- D-07: Sửa dòng ví dụ sai `http://10.0.2.2:8080/api/v1` trong `CHUYEN-SANG-API-THAT.md` §1 thành `/v1`.
- D-08: Giữ `10.0.2.2`, không đổi thành `localhost`.

**Xử lý khi backend lệch hợp đồng**
- D-09: `api/*.md` là nguồn sự thật. Backend trả lệch → sửa backend, không đổi app/mock.
- D-10: Chỉ khi `api/*.md` thực sự sai (mâu thuẫn `db/migration/V*.sql` hoặc nghiệp vụ) mới cập nhật tài liệu, cùng lần thay đổi.
- D-11: Phát hiện backend thiếu hẳn endpoint trong 6 nhóm phạm vi → bổ sung ngay trong Phase 6.

**Kiểm chứng**
- D-12: Viết `integration_test` Flutter chạy với backend thật cho ba phép thử nghiệp vụ §2 của `CHUYEN-SANG-API-THAT.md`.
- D-13: Tiêu chí #4 (idempotency) và #5 (gom refresh) kiểm cả hai cách: `integration_test` chủ động + thử tay cắt mạng thật trên máy ảo.
- D-14: Kiểm số bản ghi bằng truy vấn CSDL trực tiếp, không chỉ nhìn giao diện. Đếm `/auth/refresh` bằng log backend.

**Dữ liệu thử**
- D-15: Dùng cả script seed lẫn tạo tay. Script seed (ở `source/server`) dựng nền cố định: user thử, 2 ví, cây danh mục "Ăn uống" → "Cà phê", một ngân sách.
- D-16: Script seed phải chạy lại được (idempotent hoặc reset sạch).

**Điều kiện tiên quyết**
- D-17: Đóng 2 gap Phase 4 trước khi bắt đầu nối — `DebtReminderWorker` và `ExportAsyncRunner`.

**Tổ chức công việc**
- D-18: Plan chia theo nhóm API, bám trình tự 8 bước ở §8 của `CHUYEN-SANG-API-THAT.md`.
- D-19: `source/server` và `source/app` là hai repo git độc lập — commit riêng từng repo, cùng một plan. Luôn `cd` tuyệt đối và kiểm `phase_name` trả về trước lệnh GSD ghi dữ liệu.
- D-20: Sau mỗi bước chạy lại phép thử tương ứng ở §2 ngay.

**Cờ mock**
- D-21: Sau khi nối xong, đổi `AppConfig.useMock` `defaultValue` thành `false`.
- D-22: Không xoá `core/network/mock/` — giữ nguyên, vẫn chạy được.

**Cập nhật tài liệu khi đóng phase**
- D-23: Cập nhật cột "Backend phải làm gì" trong `CHUYEN-SANG-API-THAT.md` thành ghi chú chỗ thực tế đã vấp.
- D-24: Mọi sai lệch tài liệu/API phát hiện phải sửa lại ở `api/*.md` trong cùng phase.
- D-25: Bảng Progress trong `ROADMAP.md` đang lỗi thời — sửa lại khi đóng phase.

### Quyền quyết định của Claude (Claude's Discretion)
- Cơ chế cụ thể danh sách ngoại lệ `/ai/*` trong `MockInterceptor` (D-02): hằng số danh sách đường dẫn, hay cờ `MOCK_AI_ONLY` riêng.
- Hình thức script seed (D-15): SQL thuần, Flyway callback riêng `dev`, hay chuỗi lời gọi HTTP.
- Cách đo số lượt `/auth/refresh` (D-14): log level, bộ đếm metric, hay đọc bảng `refresh_tokens`.
- Chia bao nhiêu plan cho 8 bước — có thể gộp các bước nhỏ.

### Ý tưởng hoãn lại (Deferred — NGOÀI PHẠM VI)
- Nối nhóm `ai` (`/ai/parse-text`, `/ai/drafts`, duyệt bản nháp + `user_corrections`) — phụ thuộc Phase 5.
- Nối `/debts`, `/goals`, `/recurring`, `/notifications`, `/groups` — app chưa dựng màn hình dùng chúng. Thuộc Phase 7 của app.
- Code Phase 5 backend (`GroupController`, `AiController`, test riêng tư nhóm Testcontainers).
</user_constraints>

<phase_requirements>
## Yêu cầu của phase

Không có REQ-ID bắt buộc — phase này chủ yếu là tích hợp, không sinh yêu cầu nghiệp vụ mới (theo `ROADMAP.md` §Phase 6: "Requirements: TBD"). Thay vào đó, 8 Success Criteria của `ROADMAP.md` §Phase 6 đóng vai trò yêu cầu — xem bảng dưới, mỗi tiêu chí đã map với phần nghiên cứu tương ứng.

| Success Criterion (rút gọn) | Research hỗ trợ |
|---|---|
| 1. `USE_MOCK=false` đăng nhập được, `GET /auth/me` đúng | §1 Ma trận đối chiếu endpoint — auth; §3 context-path |
| 2. Ba phép thử nghiệp vụ (cộng gộp danh mục, transfer, sửa 3 bước) đúng trên backend thật | §2 So khung JSON; §4 Ba giả định an toàn — mục c |
| 3. Bốn màn chính hiển thị đúng dữ liệu backend thật | §2 So khung JSON toàn bộ 6 nhóm |
| 4. `Idempotency-Key` trùng chỉ sinh 1 giao dịch | §4 mục a; §3 cảnh báo `IdempotencyAspect` + context-path |
| 5. Refresh token gộp 1 lần gọi, không thu hồi oan | §4 mục b |
| 6. Duyệt AI có sửa ghi `user_corrections` | HOÃN theo D-03 — không thuộc phạm vi kiểm chứng phase này |
| 7. `core/network/mock/` vẫn chạy được với `USE_MOCK=true` | §6 Ngoại lệ `/ai/*` trong `MockInterceptor` |
| 8. Cập nhật tài liệu | §1, §3, §7 (mọi phát hiện lệch phải đưa vào `CHUYEN-SANG-API-THAT.md`/`api/*.md`) |
</phase_requirements>

## Summary

Backend Spring Boot đã hiện thực **đầy đủ cả 6 nhóm API trong phạm vi** (`auth`, `wallets`, `categories`, `transactions`, `budgets`, `reports`) — không có endpoint nào thiếu hẳn, kể cả hai endpoint CONTEXT.md nghi ngờ (`/budgets/suggestion`, `/reports/by-category-group`) đều đã tồn tại và hoạt động (`BudgetController.java:61`, `ReportController.java:64`). Hai gap Phase 4 mà D-17 yêu cầu đóng trước khi bắt đầu (`DebtReminderWorker`, `ExportAsyncRunner`) **thực ra đã được hiện thực đầy đủ** trong mã hiện tại (`DebtReminderWorker.java`, `ExportAsyncRunner.java`, có test `DebtReminderJobIntegrationTest`, `ExportJobIntegrationTest`) — `STATE.md` chỉ đang ghi lỗi thời, cần cập nhật lại thay vì code thêm.

Rủi ro thật của phase nằm ở ba chỗ cụ thể, tất cả đã xác minh bằng đọc mã: (1) **`CreateTransactionResponse.affectedBudgets` luôn trả rỗng** dù `BudgetService` đã tồn tại — app có UI thật (`TransactionMutationResult.warnings`) phụ thuộc trường này, đây là kiểu lỗi "không lỗi nào hiện ra" đúng như CONTEXT.md cảnh báo; (2) **`server.servlet.context-path: /v1` (D-05) sẽ làm sai quota rate-limit nhóm `auth`/`ai`** vì `RateLimitProperties.resolve()` so khớp `startsWith("/auth/")` trực tiếp trên `getRequestURI()` — khi thêm context-path, URI thực tế trở thành `/v1/auth/...` và filter không rơi vào rule "auth" nữa, tự động fallback về rule "default" 120/phút thay vì 5/phút; (3) **một test tích hợp dùng `TestRestTemplate` với path cứng không có `/v1`** (`AuthIdempotencyRateLimitEndToEndTest`) sẽ 404 thật sự sau khi thêm context-path, vì đây là HTTP thật qua cổng ngẫu nhiên, không đi qua `MockMvc` (tự cộng context-path). Ba điểm này phải được đưa vào task đầu tiên của plan cùng lúc với việc thêm context-path, không tách rời.

Không có `integration_test` Flutter nào hiện tại chạy trên backend thật — cả ba file trong `integration_test/` đều chạy hoàn toàn qua `MockInterceptor` (không truyền `--dart-define`). Không có script seed dữ liệu nào tồn tại ở `source/server`. Cả hai đều phải xây mới hoàn toàn.

**Khuyến nghị chính:** Thực hiện đúng theo D-18 (theo trình tự 8 bước), nhưng chèn một sub-task ngay ở bước đầu tiên (`/auth/me`) để sửa đồng thời `RateLimitProperties`, `IdempotencyAspect` và test path cứng khi thêm `context-path: /v1` — không tách các thay đổi này ra plan riêng vì chúng cùng một nguyên nhân gốc (context-path) và cùng phải kiểm chứng lại bằng test end-to-end đã có sẵn (`AuthIdempotencyRateLimitEndToEndTest`).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Chuyển đổi mock ↔ backend thật | Client (Flutter, tầng Dio) | — | `AppConfig.useMock` + `MockInterceptor` đã tách sẵn ở tầng network, không đụng nghiệp vụ |
| Base URL / context-path | API/Backend (Spring Boot config) | Client (đọc `AppConfig.baseUrl`, không sửa) | `server.servlet.context-path` là cấu hình servlet container, một chỗ duy nhất |
| Rate limit theo nhóm endpoint | API/Backend (`RateLimitFilter`) | — | Nằm hoàn toàn ở filter chain backend, app không biết đến quota |
| Idempotency-Key | Client (gắn header) + API/Backend (lưu, trả lại) | Database (bảng `idempotency_keys`) | App tạo khoá, backend chịu trách nhiệm lưu trữ + trả lại đúng response |
| Cộng gộp danh mục con | API/Backend (`fn_category_tree` qua SQL) | Database (hàm SQL gốc) | Toàn bộ logic nằm ở SQL dùng chung, backend chỉ gọi lại |
| Loại `transfer` khỏi báo cáo | API/Backend (`ReportQueryFragments.REPORT_ELIGIBLE`) | Database (cột `type`, `counts_in_report`) | Hằng số SQL dùng chung một chỗ cho mọi query report |
| Sửa/xoá giao dịch hoàn tác 3 bước | API/Backend (`TransactionService` trong `@Transactional`) | Database (atomic UPDATE số dư) | Business logic nặng, không thể đẩy xuống DB trigger theo quy tắc dự án |
| Kiểm chứng tự động (integration_test) | Client (Flutter `integration_test/`) | API/Backend (chạy thật qua Testcontainers hoặc dev server) | Test chạy trên app nhưng gọi HTTP thật tới backend đang chạy |
| Script seed dữ liệu thử | API/Backend (`source/server`, theo D-15) | Database (ghi trực tiếp qua Flyway/JDBC) | Backend sở hữu schema, script seed thuộc repo backend |

## Standard Stack

Không có thư viện mới cần thêm — phase này dùng lại 100% stack đã có ở cả hai repo.

### Core (đã có sẵn, không cài thêm)
| Thành phần | Vị trí | Vai trò trong phase 6 |
|---|---|---|
| Dio + `MockInterceptor`/`AuthInterceptor` | `source/app/lib/core/network/` | Đổi `useMock=false` để gọi thật, giữ nguyên interceptor logic |
| `integration_test` SDK package | `source/app/pubspec.yaml` (đã khai `dev_dependencies`) | Viết test chạy trên backend thật (D-12) |
| Spring Boot 3.4.13 + Spring Security 6 | `source/server/pom.xml` | `server.servlet.context-path` là cấu hình chuẩn, không cần thư viện thêm |
| Testcontainers PostgreSQL | `source/server` (đã dùng từ Phase 3) | Có thể tái dùng cho test idempotency/rate-limit sau khi sửa context-path |

### Alternatives Considered — Script seed (D-15, Claude's Discretion)
| Phương án | Đánh giá | Khuyến nghị |
|---|---|---|
| SQL thuần chạy tay (`psql -f seed.sql`) | Đơn giản nhất, nhưng không tự chạy lại được an toàn nếu không viết `ON CONFLICT`/`TRUNCATE` thủ công; tách khỏi vòng đời Maven | Dùng nếu chỉ cần seed một lần thủ công trước demo |
| Flyway callback riêng cho profile `dev` (`afterMigrate.sql` có điều kiện) | Flyway callback KHÔNG hỗ trợ điều kiện theo Spring profile natively — phải tự kiểm profile trong Java callback (`FlywayConfigurationCustomizer`), phức tạp hơn cần thiết; rủi ro chạy nhầm ở profile khác nếu cấu hình sai | Không khuyến nghị — độ phức tạp không tương xứng lợi ích |
| `CommandLineRunner`/`ApplicationRunner` có `@Profile("dev")` | Chạy trong vòng đời Spring Boot, dùng lại `JdbcTemplate`/repository đã có, dễ viết idempotent bằng `INSERT ... ON CONFLICT DO NOTHING` hoặc kiểm tồn tại trước khi tạo, dễ test bằng cách gọi lại bean trực tiếp trong integration test | **Khuyến nghị** — khớp D-16 (chạy lại được), khớp D-15 (thuộc `source/server`), không cần thêm dependency |
| Chuỗi lời gọi HTTP (script gọi chính API vừa dựng) | Kiểm chứng luôn API hoạt động đúng lúc seed, nhưng phụ thuộc server đã chạy + JWT hợp lệ, phức tạp hoá việc "seed trước khi test" | Có thể dùng bổ sung cho phần giao dịch (D-15 đã nói "giao dịch tạo qua UI app"), không thay thế seed nền tảng |

**Khuyến nghị:** `CommandLineRunner` `@Profile("dev")` trong `source/server`, đặt ở package riêng (ví dụ `common/seed/DevDataSeeder.java`), dùng `JdbcTemplate` với `INSERT ... ON CONFLICT (unique_key) DO NOTHING` cho user/ví/danh mục con, kiểm tồn tại trước khi tạo ngân sách (không có UNIQUE tự nhiên trên `budgets`). `[ASSUMED]` — chưa xác minh chi tiết cấu trúc UNIQUE constraint của `budgets` để đảm bảo idempotent; planner cần đọc `V*.sql` bảng `budgets` trước khi viết seed.

## Ma trận đối chiếu endpoint (quan trọng nhất)

Nguồn app: `source/app/lib/core/network/mock/mock_router.dart` (đọc toàn văn). Nguồn backend: 9 file `*Controller.java` trong `source/server/src/main/java/com/datn/financeapp/*/controller/`.

### auth — `AuthController.java` (`@RequestMapping("/auth")`)
| App gọi (mock_router.dart) | Backend có? | File + method |
|---|---|---|
| `POST /auth/login` | ✓ | `AuthController.java:56` `login()` |
| `POST /auth/register` | ✓ | `AuthController.java:50` `register()` |
| `POST /auth/refresh` | ✓ | `AuthController.java:63` `refresh()` |
| `POST /auth/logout` | ✓ | `AuthController.java:68` `logout()` |
| `GET /auth/me` | ✓ | `AuthController.java:74` `me()` |

Backend có thêm (app hiện chưa gọi qua mock_router, nhưng tồn tại và có thể dùng khi cần): `PATCH /auth/me`, `POST /auth/change-password`, `POST /auth/forgot-password`, `POST /auth/reset-password`.

### wallets — `WalletController.java` (`@RequestMapping("/wallets")`)
| App gọi | Backend có? | File + method |
|---|---|---|
| `GET /wallets` | ✓ | `WalletController.java:52` `list()` |
| `GET /wallets/summary` | ✓ | `WalletController.java:61` `summary()` |
| `POST /wallets/transfer` | ✓ | `WalletController.java:104` `transfer()` |
| `GET /wallets/{id}` | ✓ | `WalletController.java:67` `detail()` |

Backend có thêm (ngoài phạm vi mock_router hiện tại nhưng tồn tại đầy đủ): `POST /wallets`, `PATCH /wallets/{id}`, `DELETE /wallets/{id}`, `PATCH /wallets/reorder`, `POST /wallets/{id}/adjust-balance`, `POST /wallets/{id}/reconcile`.

### categories — `CategoryController.java` (path trần, không `@RequestMapping` cấp class)
| App gọi | Backend có? | File + method |
|---|---|---|
| `GET /categories` | ✓ | `CategoryController.java:40` `list()` |

Backend có thêm: `GET /categories/{id}`, `POST /categories`, `PATCH /categories/{id}`, `DELETE /categories/{id}`, `PATCH /categories/reorder`, `GET /category-groups`, `GET /icons`.

### transactions — `TransactionController.java` (`@RequestMapping("/transactions")`)
| App gọi | Backend có? | File + method |
|---|---|---|
| `GET /transactions` | ✓ | `TransactionController.java:54` `list()` |
| `GET /transactions/by-date` | ✓ | `TransactionController.java:91` `listByDate()` |
| `POST /transactions` | ✓ | `TransactionController.java:128` `create()` |
| `PUT /transactions/{id}` | ✓ (mock chưa có tuyến, backend có) | `TransactionController.java:145` `update()` |
| `DELETE /transactions/{id}` | ✓ (mock chưa có tuyến, backend có) | `TransactionController.java:152` `delete()` |
| `GET /transactions/{id}` | ✓ | `TransactionController.java:122` `detail()` |

Backend có thêm: `POST /transactions/bulk`, `POST /transactions/{id}/duplicate`.

**Lưu ý:** mock ghi "PUT/DELETE chưa có tuyến" trong `CHUYEN-SANG-API-THAT.md` — điều này mô tả **mock**, không phải backend. Backend đã có đầy đủ. Không cần bổ sung gì ở đây, chỉ cần nối.

### budgets — `BudgetController.java` (`@RequestMapping("/budgets")`)
| App gọi | Backend có? | File + method |
|---|---|---|
| `GET /budgets` | ✓ | `BudgetController.java:47` `list()` |
| `GET /budgets/summary` | ✓ | `BudgetController.java:55` `summary()` |
| `GET /budgets/suggestion` | ✓ (CONTEXT.md D-11 nghi ngờ — **đã xác minh có, không thiếu**) | `BudgetController.java:61` `suggestion()` |
| `GET /budgets/{id}` | ✓ | `BudgetController.java:73` `detail()` |
| `POST /budgets` | ✓ (mock chưa có tuyến, backend có) | `BudgetController.java:79` `create()` |

Backend có thêm: `GET /budgets/alerts`, `PATCH /budgets/{id}`, `DELETE /budgets/{id}`.

### reports — `ReportController.java` (`@RequestMapping("/reports")`)
| App gọi | Backend có? | File + method |
|---|---|---|
| `GET /reports/home` | ✓ | `ReportController.java:47` `home()` |
| `GET /reports/daily-trend` | ✓ | `ReportController.java:86` `dailyTrend()` |
| `GET /reports/by-category-group` | ✓ (CONTEXT.md D-11 nghi ngờ — **đã xác minh có, không thiếu**) | `ReportController.java:64` `byCategoryGroup()` |
| `GET /reports/by-category` | ✓ | `ReportController.java:74` `byCategory()` |

Backend có thêm: `GET /reports/summary`, `GET /reports/monthly-trend`, `POST /reports/export`, `GET /reports/export/{jobId}`, `GET /reports/export/{jobId}/download`.

### ai — ngoài phạm vi nối lần này (D-01/D-02)
`mock_router.dart` chỉ mô phỏng `GET /ai/drafts` và `POST /ai/parse-text` — backend **không có** `AiController` (đã xác minh: `find src/main/java -name "*Controller.java"` không trả về file nào chứa `Ai`). Đúng như CONTEXT.md ghi nhận.

### Kết luận ma trận đối chiếu
**Không có endpoint nào backend thiếu hẳn trong 6 nhóm phạm vi phase 6.** CONTEXT.md D-11 nêu nghi ngờ về `/budgets/suggestion` và `/reports/by-category-group` — cả hai đã được xác minh tồn tại và hoạt động `[VERIFIED: đọc mã nguồn TransactionController/BudgetController/ReportController]`. **D-11 không phát sinh việc code endpoint mới cho phase này** — cần cập nhật CONTEXT.md/kỳ vọng của planner để không lập task thừa "bổ sung endpoint thiếu".

## Đối chiếu khung JSON response

### Wallets — `current_balance` vs `projected_balance` (điểm dễ sai nhất)
Backend `WalletResponse` (record, `@JsonInclude(NON_NULL)`):
```java
// source/server/src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java
public record WalletResponse(
        UUID id, String name, String type, Long currentBalance,
        Boolean includeInTotal, boolean isShared, UUID groupId,
        String icon, String color, Integer sortOrder,
        Instant createdAt, Long projectedBalance) {}
```
App đọc đúng ngữ nghĩa (`source/app/lib/features/wallet/data/models/wallet.dart:63`):
```dart
projectedBalance: json['projected_balance'] as int?,
```
**Kết luận:** `[VERIFIED]` — khớp hoàn toàn với quy tắc CLAUDE.md #5 và `db/README.md`. `currentBalance` đã trừ ngược giao dịch tương lai, `projectedBalance` chỉ xuất hiện khi có giao dịch tương lai (`@JsonInclude(NON_NULL)` bỏ hẳn trường thay vì trả `null`). Không cần sửa gì.

### Transactions — `affected_budgets` LUÔN RỖNG dù backend đã có Budget module (lệch nghiêm trọng)
Backend `CreateTransactionResponse` (`source/server/src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionResponse.java`):
```java
public record CreateTransactionResponse(TransactionResponse transaction, NewBalance newBalance, List<Object> affectedBudgets) {
    public CreateTransactionResponse(TransactionResponse transaction, NewBalance newBalance) {
        this(transaction, newBalance, List.of());   // <-- luôn rỗng
    }
    public record NewBalance(UUID walletId, Long balance) {}
}
```
Comment trong file ghi: *"Trường `affectedBudgets` thuộc Phase 4 (module ngân sách chưa tồn tại) — Phase 3 luôn trả rỗng, KHÔNG bịa dữ liệu."* — comment này viết đúng lúc Phase 3, nhưng **chưa được cập nhật sau khi Phase 4 hoàn thành**.

`[VERIFIED: grep toàn bộ TransactionService.java]` — tất cả 3 chỗ dựng `CreateTransactionResponse` (dòng 121, 197, 283 của `TransactionService.java`) đều dùng constructor 2-tham số (không truyền `affectedBudgets`), nên trường này **luôn là danh sách rỗng** ở mọi luồng tạo/sửa/nhân bản giao dịch, kể cả hiện tại khi `BudgetService` đã tồn tại đầy đủ.

App đã có UI logic phụ thuộc trường này (`source/app/lib/features/transaction/data/models/transaction_request.dart:161-178`):
```dart
final List<AffectedBudget> affectedBudgets;
// ...
affectedBudgets: ((json['affected_budgets'] as List<dynamic>?) ?? const [])
    .map((e) => AffectedBudget.fromJson(e as Map<String, dynamic>))
    .toList(),
// ...
/// Ngân sách cần báo cho người dùng ngay sau khi lưu.
Iterable<AffectedBudget> get warnings =>
    affectedBudgets.where((b) => b.needsAttention);
```
Đặc tả `api/04-GIAO-DICH.md` dòng 249-264 xác nhận yêu cầu: *"Trả kèm `affected_budgets` để ứng dụng cảnh báo ngay tại chỗ sau khi lưu, không phải gọi thêm lần nữa."*

**Kết luận:** `[VERIFIED]` — đây là kiểu lỗi "app vẫn chạy bình thường, không lỗi nào hiện ra" giống hệt ba quy tắc bất biến mà CONTEXT.md cảnh báo, nhưng KHÔNG nằm trong danh sách ba phép thử §2 của `CHUYEN-SANG-API-THAT.md` nên rất dễ bị bỏ sót khi kiểm chứng phase này bằng tay. Backend đã có sẵn `BudgetService.alerts(userId)` (dùng cho `GET /budgets/alerts`, `BudgetController.java:67`) — có thể tái dùng logic tương tự (lọc theo `category_id` của giao dịch vừa tạo, dùng `fn_category_tree` ngược như `BudgetAlertListener.java` đã làm) để lấp `affectedBudgets` thay vì viết mới. **Đây là việc phải sửa trong Phase 6** theo D-09 (backend lệch đặc tả → sửa backend), tương đương một endpoint "thiếu tính năng" chứ không phải thiếu hẳn route.

### Khung lỗi (`{success, error}`)
Backend `GlobalExceptionHandler` (`source/server/src/main/java/com/datn/financeapp/common/exception/GlobalExceptionHandler.java`) và app `MockError.toJson()` (`source/app/lib/core/network/mock/mock_error.dart`) đều tạo đúng `{code, message, fields?}` lồng trong `error`. `[VERIFIED]` — khớp `api/00-QUY-UOC-CHUNG.md` §4.3/4.4/6, không phát hiện lệch.

### Cộng gộp danh mục con — hai cách hiện thực khác nhau, cùng ngữ nghĩa
- `TransactionService`/`BudgetRepository`/`BudgetProgressRepository`: gọi trực tiếp `fn_category_tree(:categoryId)` cho từng truy vấn 1-category (`BudgetRepository.java:59`, `BudgetProgressRepository.java:70`, `CategoryRepository.java:24`).
- `ReportRepository` (nhóm toàn bộ danh mục cho báo cáo `by-category`/`by-category-group`): dùng `COALESCE(parent_category_id, id)` + JOIN thay vì gọi `fn_category_tree` lặp lại — đã ghi nhận trong `STATE.md`: *"Cong gop danh muc con o truy van NHOM TOAN BO danh muc dung JOIN categories root COALESCE(parent_category_id, id) thay vi goi fn_category_tree tung danh muc"*. Vì cây danh mục tối đa 2 cấp (CLAUDE.md), `COALESCE(parent_category_id, id)` tương đương `fn_category_tree` cho trường hợp gộp-toàn-bộ. `[VERIFIED nguồn: comment trong ReportRepository.java + STATE.md]` — không phải lỗi, nhưng khác cách hiện thực nên **phải test riêng** thay vì giả định đã đúng vì `fn_category_tree` đã test ở nơi khác.

## Cấu hình `server.servlet.context-path: /v1` (D-05) — hệ quả kỹ thuật

**Hiện trạng xác nhận:** `[VERIFIED: grep "context-path" src/main/resources/]` — không có dòng nào cấu hình context-path ở `application.yml`, `application-dev.yml`, `application-test.yml`. Mọi controller phục vụ trần (`/auth`, `/wallets`, `/categories`, `/transactions`, `/budgets`, `/reports`, `/debts`, `/goals`, `/recurring`, `/notifications`).

### 1. Spring Security (`SecurityConfig.java`) — AN TOÀN, không cần sửa
`[VERIFIED qua WebSearch + tài liệu Spring Security chính thức]` — `requestMatchers()` trong `authorizeHttpRequests()` **tự động loại trừ context-path**; pattern nên viết tương đối với context-path (không bao gồm nó). `SecurityConfig.java` hiện có:
```java
.requestMatchers("/auth/register", "/auth/login", "/auth/refresh",
        "/auth/forgot-password", "/auth/reset-password").permitAll()
```
Các path này **không cần thêm `/v1`** — Spring Security tự khớp đúng dù thêm context-path. Không sửa gì ở đây.

### 2. `RateLimitFilter`/`RateLimitProperties` — SẼ VỠ ÂM THẦM, phải sửa
`[VERIFIED: đọc RateLimitProperties.java]`:
```java
public static final List<RateLimitRule> RULES = List.of(
        new RateLimitRule("auth", "/auth/", 5, Duration.ofMinutes(1), true),
        new RateLimitRule("ai", "/ai/", 30, Duration.ofMinutes(1), false),
        new RateLimitRule("default", "/", 120, Duration.ofMinutes(1), false));

public RateLimitRule resolve(String requestUri) {
    return RULES.stream()
            .filter(r -> !r.pathPrefix().equals("/") && requestUri.startsWith(r.pathPrefix()))
            ...
```
`resolve()` nhận `requestUri` từ `RateLimitFilter.java:46` (`req.getRequestURI()` — trả về đường dẫn ĐẦY ĐỦ bao gồm context-path). Sau khi thêm `context-path: /v1`, `getRequestURI()` cho `/v1/auth/login` sẽ trả về `/v1/auth/login`, và `"/v1/auth/login".startsWith("/auth/")` là **false** → rơi vào rule "default" (120/phút/user thay vì 5/phút/IP). Đây là **lỗ hổng bảo mật âm thầm**: mất hoàn toàn quota chống brute-force cho `/auth/login` và quota `/ai/` cũng bị ảnh hưởng tương tự.

**Sửa:** đổi `pathPrefix` thành `"/v1/auth/"`, `"/v1/ai/"` (gắn cứng theo context-path đã chốt D-04), hoặc đọc context-path động qua `@Value("${server.servlet.context-path:}")` rồi nối chuỗi lúc khởi tạo bean — cách sau bền hơn nếu context-path còn đổi trong tương lai (ví dụ `/v2`).

### 3. `IdempotencyAspect` — thay đổi giá trị lưu trữ, không lỗi runtime nhưng cần biết
`[VERIFIED: đọc IdempotencyAspect.java]`:
```java
String endpoint = request.getMethod() + " " + request.getRequestURI();
```
Dùng để tra `findByIdempotencyKeyAndUserIdAndEndpoint`. Không có lỗi logic — trước và sau khi thêm context-path, `endpoint` vẫn nhất quán trong nội bộ hệ thống (luôn có `/v1` hoặc luôn không có). Nhưng **nếu có dữ liệu `idempotency_keys` cũ ghi trước khi thêm context-path** (ví dụ trong CSDL dev đã seed từ trước), key cũ sẽ không khớp `endpoint` mới sau khi đổi — không phải bug, chỉ cần biết để không nhầm lẫn khi test lại idempotency trên CSDL dev cũ. Khuyến nghị: xoá sạch bảng `idempotency_keys` (hoặc reset toàn bộ CSDL dev) ngay sau khi thêm context-path, trước khi bắt đầu kiểm chứng D-13.

### 4. springdoc/Swagger — KHÔNG áp dụng
`[VERIFIED: grep "springdoc\|swagger" pom.xml → không có kết quả]` — dự án không dùng springdoc/Swagger, không có tác động.

### 5. Test hiện có — 1 file sẽ 404 THẬT, phần còn lại an toàn
`[VERIFIED: đếm 55 file test dùng path tuyệt đối kiểu `get("/auth/me")`, 32 file `@SpringBootTest` + `@AutoConfigureMockMvc`, `@WebMvcTest` dùng `MockMvc`]`:
- **An toàn (không cần sửa):** Mọi test dùng `MockMvc` (dù qua `@WebMvcTest` hay `@SpringBootTest` + `@AutoConfigureMockMvc`, gọi `mockMvc.perform(get("/wallets"))`...) — `MockMvc` chạy trong cùng `DispatcherServlet` context nên **tự động áp dụng context-path** giống HTTP thật, request path tương đối vẫn khớp đúng route. Đây là phần lớn (243 lời gọi `mockMvc.perform`).
- **SẼ VỠ (404):** `AuthIdempotencyRateLimitEndToEndTest.java` — dùng `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` + `TestRestTemplate`, gọi HTTP thật qua cổng ngẫu nhiên (`restTemplate.exchange("/auth/register", ...)`), path KHÔNG tự cộng context-path vì `TestRestTemplate` xây URL tuyệt đối từ root. Sau khi thêm `context-path: /v1`, path phải sửa thành `/v1/auth/register`.
  `[VERIFIED: grep "RANDOM_PORT" src/test/java → duy nhất 1 file]`

**Kết luận:** thêm `context-path: /v1` chỉ cần sửa 3 chỗ cụ thể — `RateLimitProperties.RULES` (2 dòng), `AuthIdempotencyRateLimitEndToEndTest` (đổi path literal), và xoá dữ liệu `idempotency_keys` cũ trên CSDL dev nếu có. Không cần sửa `SecurityConfig`, không có springdoc để lo, phần lớn test (`MockMvc`) tự động đúng.

## Ba giả định an toàn app đặt vào backend

### a. Idempotency-Key trả lại phản hồi cũ trong 24h
**Mã backend:** `source/server/src/main/java/com/datn/financeapp/common/idempotency/IdempotencyAspect.java` — AOP `@Around("@annotation(...Idempotent)")`. Luồng: `INSERT ... ON CONFLICT DO NOTHING` với `status=processing` → nếu insert thất bại (key đã tồn tại), tra bản ghi cũ: `completed` → trả lại `response_body` đã lưu (không chạy lại nghiệp vụ); `processing` → ném `409 REQUEST_IN_PROGRESS` ngay lập tức. `[VERIFIED]`

**Cách kiểm chứng từ ngoài (cho `integration_test` D-12/D-13):**
1. Gửi `POST /transactions` với `Idempotency-Key: test-key-1` — nhận `201`, ghi lại `transaction.id`.
2. Gửi lại **y hệt** request với cùng header — kỳ vọng nhận **cùng `transaction.id`**, cùng `status 201` cache, KHÔNG có bản ghi mới trong CSDL.
3. Đếm số dòng `transactions` khớp điều kiện vừa tạo trong CSDL bằng truy vấn trực tiếp (D-14) — phải bằng 1.
4. Gửi song song 2 request cùng key trước khi request đầu hoàn tất (mô phỏng bằng `Future.wait` hai lời gọi Dio không `await` tuần tự) — một request nhận `201`, request kia nhận `409 REQUEST_IN_PROGRESS` (không phải lỗi 500 hay data trùng).

**Endpoint nào gắn `@Idempotent`:** `POST /wallets`, `POST /wallets/transfer`, `POST /wallets/{id}/adjust-balance`, `POST /categories`, `POST /transactions`, `POST /transactions/bulk`, `POST /transactions/{id}/duplicate`, `POST /budgets`, `POST /reports/export`. **Không gắn:** `PUT`/`PATCH`/`DELETE` (tự idempotent theo nghiệp vụ), `/auth/**` (POST-hành-động, loại trừ theo D-11 của Phase 1), `/wallets/{id}/reconcile` (hành động dò lỗi phải luôn tính lại).

### b. Refresh token rotation + reuse detection
**Mã backend:** `source/server/src/main/java/com/datn/financeapp/auth/service/AuthService.java` — comment dòng 162 xác nhận: *"AUTH-03: rotation dùng một lần + reuse detection, khoá đúng race condition"*. Dòng 181: reuse detection gọi `refreshTokenRepository.revokeAllActiveForUser(revoked.getUserId())` khi phát hiện token cũ bị dùng lại. `[VERIFIED]`

**Cách kiểm chứng từ ngoài:**
1. Login, lấy `refresh_token` R1.
2. Gọi `POST /auth/refresh` với R1 → nhận `access_token` mới + `refresh_token` R2. R1 giờ đã bị thu hồi (rotation).
3. Gọi lại `POST /auth/refresh` với R1 (đã dùng) → kỳ vọng `401`/lỗi refresh-invalid, VÀ toàn bộ phiên (kể cả R2) bị thu hồi (reuse detection) — verify bằng cách gọi `GET /auth/me` với access token cấp từ R2, kỳ vọng **cũng thất bại** sau bước reuse.
4. Bắn nhiều request cùng lúc với access token đã hết hạn (D-13) — `AuthInterceptor` phía app gộp lại thành 1 lần gọi `/auth/refresh`; verify bằng đếm log backend hoặc đọc bảng `refresh_tokens` (D-14) xem chỉ có đúng 1 lần rotation xảy ra trong khoảng thời gian đó.

### c. Ba quy tắc nghiệp vụ bất biến (cộng gộp danh mục, transfer, sửa 3 bước)
Đã xác minh vị trí hiện thực ở mục "Đối chiếu khung JSON" và "Ma trận đối chiếu endpoint" phía trên:
- Cộng gộp danh mục con: `fn_category_tree` qua `CategoryRepository.findCategoryTree()`, dùng lại ở `BudgetRepository`, `BudgetProgressRepository`, `TransactionService` (cộng gộp lọc `category_id`).
- Loại `transfer`: `ReportQueryFragments.REPORT_ELIGIBLE = "t.type <> 'transfer' AND t.counts_in_report = TRUE AND NOT t.is_deleted"` dùng chung mọi query report.
- Sửa/xoá 3 bước: `TransactionService.update()`/`delete()` gọi `TransactionRepository.save()` + `WalletRepository.adjustBalance()` trong cùng `@Transactional` (ghi nhận ở `STATE.md`: *"update()/delete() giao dịch gọi trực tiếp TransactionRepository.save() + WalletRepository.adjustBalance() trong CÙNG một @Transactional, không qua TransactionWriter"*).

**Cách kiểm chứng:** đúng ba phép thử §2 của `CHUYEN-SANG-API-THAT.md` (đã copy nguyên văn vào phần Specifics của CONTEXT.md) — thực hiện qua UI app trên backend thật, đối chiếu số liệu bằng truy vấn CSDL trực tiếp (D-14), không chỉ nhìn giao diện.

## `integration_test` Flutter chạy với backend thật (D-12/D-13)

**Hiện trạng:** `source/app/integration_test/` đã tồn tại 3 file (`add_transaction_flow_test.dart`, `overview_updates_test.dart`, `transaction_book_scroll_test.dart`), nhưng **cả ba đều chạy hoàn toàn qua `MockInterceptor`** — không truyền `--dart-define`, gọi `app.main()` trực tiếp nên dùng `AppConfig.useMock` mặc định (`true`). `[VERIFIED: đọc 3 file, không thấy `--dart-define` hay override config nào]`. Cần viết mới, không sửa 3 file cũ (chúng vẫn hữu ích làm test mock riêng).

### Cấu trúc thư mục khuyến nghị
```
integration_test/
├── add_transaction_flow_test.dart       # (giữ nguyên — test mock)
├── overview_updates_test.dart           # (giữ nguyên — test mock)
├── transaction_book_scroll_test.dart    # (giữ nguyên — test mock)
├── real_backend/                        # MỚI — test chạy trên backend thật
│   ├── category_rollup_test.dart        # phép thử §2.1
│   ├── transfer_excluded_from_report_test.dart  # phép thử §2.2
│   ├── edit_transaction_3step_test.dart # phép thử §2.3
│   ├── idempotency_test.dart            # D-13a
│   └── refresh_token_concurrency_test.dart  # D-13b
```

### Cách truyền `--dart-define` khi chạy trên máy ảo Pixel 7
`[ASSUMED — cú pháp Flutter chuẩn, chưa test thật trên máy ảo trong phiên research này]`:
```bash
flutter test integration_test/real_backend/category_rollup_test.dart \
  --dart-define=USE_MOCK=false \
  --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 \
  -d emulator-5554
```
Hoặc dùng `flutter drive` nếu cần điều khiển chi tiết hơn qua `integration_test_driver`. Backend phải đang chạy thật (`mvn spring-boot:run` với profile `dev`) trước khi chạy lệnh test — test này **không tự spin Testcontainers**, khác với test Java phía server.

### Mẫu gửi 2 `POST` cùng `Idempotency-Key` (D-13a)
`ApiClient` đã tự gắn header (theo CONTEXT.md `code_context`), nên test cần override hoặc gọi trực tiếp `Dio`/`ApiClient` với cùng key cố định thay vì để `TransactionWriter` tự sinh key mới mỗi lần:
```dart
// integration_test/real_backend/idempotency_test.dart — phác thảo, cần điều chỉnh theo API thật của ApiClient
final dio = Dio(BaseOptions(baseUrl: AppConfig.baseUrl));
const key = 'integration-test-fixed-key-001';
final r1 = await dio.post('/transactions', data: payload,
    options: Options(headers: {'Idempotency-Key': key}));
final r2 = await dio.post('/transactions', data: payload,
    options: Options(headers: {'Idempotency-Key': key}));
expect(r1.data['data']['transaction']['id'], r2.data['data']['transaction']['id']);
```
`[ASSUMED]` — cần đọc `ApiClient`/`TransactionWriter` thật (`source/app/lib/core/network/api_client.dart`, chưa đọc trong phiên này) để biết interface chính xác cho việc set `Idempotency-Key` thủ công trong test, vì nghiệp vụ bình thường app tự sinh key ngẫu nhiên mỗi request mới.

### Mẫu bắn nhiều request song song với access token hết hạn (D-13b)
Không thể ép token hết hạn thật trong integration_test dễ dàng (JWT có `exp` cố định 3600s theo `application.yml`). Hai lựa chọn:
1. **Seed** một access token đã hết hạn thủ công (ký bằng cùng `jwt.secret`, `exp` trong quá khứ) và tiêm vào `TokenStorage` trước khi bắn request — cần app expose test hook để ghi token trực tiếp (chưa xác minh có sẵn không).
2. Chờ thật (không khả thi cho CI, nhưng khả thi cho thử tay theo D-13 "kèm một lần thử tay").

`[ASSUMED]` — khuyến nghị dùng cách 1 cho `integration_test` tự động (cần thêm một hook test-only vào `TokenStorage`, đánh dấu rõ chỉ dùng cho test), và cách "thử tay cắt mạng thật" của D-13 dùng airplane mode/`adb shell svc wifi disable` để mô phỏng độ trễ khiến token tự hết hạn giữa chừng trên máy ảo Pixel 7.

## Danh sách ngoại lệ `/ai/*` trong `MockInterceptor` (D-02)

**Mã hiện tại** (`source/app/lib/core/network/mock/mock_interceptor.dart`, toàn văn 39 dòng):
```dart
class MockInterceptor extends Interceptor {
  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final result = await MockRouter.instance.handle(options);
    handler.resolve(_response(options, result.status, result.json));
  }
  ...
}
```
Hiện tại **không có** nhánh điều kiện nào — mọi request đều bị chặn khi `useMock=true`, và khi `useMock=false` interceptor này **không được gắn vào Dio** (giả định dựa trên cách `AppConfig.useMock` mô tả — cần xác nhận thêm ở `dio_client.dart`/nơi đăng ký interceptor, chưa đọc trong phiên này). `[ASSUMED — chưa xác minh chỗ đăng ký interceptor theo điều kiện `useMock`]`.

**Đề xuất cách hiện thực ít xâm lấn nhất:** thêm một danh sách hằng số path ngoại lệ, kiểm tra ngay trong `onRequest` trước khi quyết định gọi mạng thật hay mock:
```dart
class MockInterceptor extends Interceptor {
  /// D-02 (06-CONTEXT.md): nhóm `ai` luôn dùng mock kể cả khi USE_MOCK=false,
  /// vì backend Phase 5 (AiController) chưa tồn tại. Gỡ khi Phase 5 xong.
  static const _alwaysMockPrefixes = ['/ai/'];

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final result = await MockRouter.instance.handle(options);
    handler.resolve(_response(options, result.status, result.json));
  }
}
```
Điểm mấu chốt: interceptor **đã luôn mock 100%** khi được gắn (không có logic bật/tắt theo path). Muốn có ngoại lệ `/ai/*` giữ mock còn phần khác ra mạng thật, cách ít xâm lấn nhất là **không gắn `MockInterceptor` cho toàn bộ Dio nữa**, mà chuyển điều kiện lên tầng đăng ký interceptor — nếu `useMock == false`, vẫn gắn `MockInterceptor` nhưng giới hạn nó chỉ xử lý các request khớp `_alwaysMockPrefixes`, còn lại gọi `handler.next(options)` để đi tiếp ra Dio thật:
```dart
@override
Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
  if (!AppConfig.useMock && !_alwaysMockPrefixes.any(options.path.startsWith)) {
    handler.next(options);  // để Dio gọi mạng thật
    return;
  }
  final result = await MockRouter.instance.handle(options);
  handler.resolve(_response(options, result.status, result.json));
}
```
`[ASSUMED]` — đây là thiết kế đề xuất dựa trên đọc mã hiện có, CHƯA xác minh interceptor này có đang được gắn có điều kiện ở nơi khác hay không (ví dụ có thể `useMock` đã được kiểm tra ở chỗ khởi tạo `Dio` thay vì trong interceptor). Planner cần đọc thêm `source/app/lib/core/network/dio_client.dart` (hoặc file tương đương khởi tạo `Dio`) trước khi viết task cụ thể.

## Hai gap Phase 4 (D-17) — ĐÃ ĐÓNG, không cần code thêm

**Xác minh trực tiếp:** cả hai đã tồn tại và có test tích hợp:
- `DebtReminderWorker` — `source/server/src/main/java/com/datn/financeapp/debt/service/DebtReminderWorker.java` (41 dòng), package-private `@Component`, method `evaluateAndNotify(Debt debt)` với `@Transactional`, ba mốc nhắc (7 ngày, 1 ngày, quá hạn lặp mỗi 7 ngày), ghi `NotificationRepository.insertGenericNotification(...)`. Được gọi từ `DebtService.sendDueReminders()`, chạy theo lịch qua `DebtReminderJob` (`@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Ho_Chi_Minh")`). Có test `src/test/java/com/datn/financeapp/scheduler/DebtReminderJobIntegrationTest.java`.
- `ExportAsyncRunner` — `source/server/src/main/java/com/datn/financeapp/report/export/ExportAsyncRunner.java` (118 dòng), `@Async("exportTaskExecutor")` + `@Transactional`, đọc `ReportRepository.eligibleTransactions()` (cùng bộ lọc với endpoint đọc, đảm bảo export khớp báo cáo người dùng thấy), ghi CSV qua `CsvReportWriter`, cập nhật `ExportJobRepository.markCompleted`/`markFailed`. `AsyncConfig` đã cấu hình `ThreadPoolTaskExecutor` riêng (core=2, max=4, queue=50), `@EnableAsync` đã bật ở `FinanceAppApplication.java`. Có test `src/test/java/com/datn/financeapp/report/ExportJobIntegrationTest.java`.

**Kiểm chứng build:** `mvn -q -o compile` chạy sạch, không lỗi biên dịch. Chạy thử `mvn -q -o test -Dtest='!*IntegrationTest,!*EndToEndTest'` (loại các test cần Testcontainers) → 18 test chạy, 0 lỗi (2 test bị skip do môi trường research không có Docker daemon sẵn sàng lúc chạy, không liên quan mã nguồn).

**Kết luận:** `STATE.md` hiện ghi *"7/7 plan xong; kiểm chứng 5/7 success criteria — cần đóng 2 gap"* — dòng này **lỗi thời**, mã đã đủ. D-17 ("đóng 2 gap trước khi bắt đầu nối") **thực chất không còn việc gì để làm** ngoài việc **chạy lại full test suite Phase 4 với Docker sẵn sàng để xác nhận xanh và cập nhật STATE.md/ROADMAP.md** — không cần plan code riêng cho D-17, chỉ cần một bước xác nhận + cập nhật tài liệu ở đầu Phase 6 (có thể gộp vào D-25 luôn, vì cả hai đều là sửa tài liệu trạng thái lỗi thời).

**Khối lượng ước tính cho planner:** 1 task nhỏ (chạy `mvn test` đầy đủ với Docker, đối chiếu 7 success criteria Phase 4, sửa `STATE.md` + `ROADMAP.md`), KHÔNG cần tách plan riêng.

## Common Pitfalls

### Pitfall 1: Tưởng D-11 yêu cầu code endpoint mới
**Điều gì sai:** CONTEXT.md D-11 nêu nghi ngờ `/budgets/suggestion`, `/reports/by-category-group` có thể thiếu, kèm chỉ dẫn "bổ sung ngay trong Phase 6" nếu thiếu.
**Vì sao xảy ra:** CONTEXT.md được viết dựa trên đọc `mock_router.dart` (nguồn phạm vi) mà chưa đối chiếu trực tiếp mã Java.
**Cách tránh:** Đã xác minh trong RESEARCH.md này — cả hai endpoint đều tồn tại và hoạt động. Planner **không cần** lập task "bổ sung endpoint thiếu" cho D-11.
**Dấu hiệu cảnh báo:** nếu vẫn thấy nghi ngờ endpoint thiếu, luôn `grep -rn "@GetMapping\|@PostMapping" src/main/java/**/*Controller.java` trước khi kết luận.

### Pitfall 2: Thêm context-path mà quên sửa RateLimitProperties
**Điều gì sai:** Rate limit nhóm `auth`/`ai` âm thầm mất tác dụng (rơi vào rule "default" 120/phút thay vì 5/phút), không có exception hay log lỗi nào báo hiệu.
**Vì sao xảy ra:** `RateLimitProperties.resolve()` so khớp chuỗi cứng `startsWith("/auth/")` trên `getRequestURI()` (bao gồm context-path), độc lập với cơ chế path-matching của Spring Security (tự loại trừ context-path).
**Cách tránh:** Sửa `pathPrefix` thành `/v1/auth/`, `/v1/ai/` cùng lúc với việc thêm `context-path: /v1`, verify lại bằng `AuthIdempotencyRateLimitEndToEndTest` (sau khi sửa path literal trong chính test đó).
**Dấu hiệu cảnh báo:** gọi `/v1/auth/login` sai mật khẩu >5 lần liên tục không bị khoá 429/khoá IP.

### Pitfall 3: Bỏ sót `affected_budgets` vì không nằm trong ba phép thử §2
**Điều gì sai:** Backend trả `affected_budgets: []` vĩnh viễn, app không hiện cảnh báo ngân sách ngay sau khi ghi giao dịch — không phải lỗi runtime, chỉ là tính năng im lặng không hoạt động.
**Vì sao xảy ra:** CONTEXT.md liệt kê 4 giả định an toàn nhưng không có `affected_budgets` trong danh sách; ba phép thử §2 của `CHUYEN-SANG-API-THAT.md` cũng không kiểm trường này.
**Cách tránh:** Thêm việc sửa `TransactionService` để điền `affectedBudgets` thật (tái dùng logic tương tự `BudgetAlertListener`) vào task của nhóm `transactions` (bước 4 trong trình tự 8 bước D-18), kèm test thủ công: ghi một khoản chi khiến ngân sách vượt `near_limit`/`over_limit`, kiểm response `POST /transactions` có `affected_budgets` không rỗng.
**Dấu hiệu cảnh báo:** response `POST /transactions` luôn có `"affected_budgets": []` bất kể ngân sách đã gần/vượt hạn mức.

### Pitfall 4: Tưởng `IdempotencyAspect` sẽ lỗi khi thêm context-path
**Điều gì sai:** Không phải lỗi thật — chỉ là giá trị `endpoint` lưu trong `idempotency_keys` đổi từ `POST /transactions` thành `POST /v1/transactions`. Logic vẫn đúng vì nhất quán nội bộ.
**Cách tránh:** Không cần sửa code, chỉ cần xoá dữ liệu `idempotency_keys` cũ trên CSDL dev (nếu có) trước khi bắt đầu kiểm chứng D-13, tránh nhầm lẫn khi debug ("tại sao key cũ không match?").

### Pitfall 5: Viết `integration_test` mới đè lên 3 file cũ đang chạy mock
**Điều gì sai:** Nếu sửa 3 file `integration_test/*.dart` hiện có để thêm `--dart-define`, sẽ phá vỡ luồng test mock đang chạy tốt (dùng cho CI không cần backend).
**Cách tránh:** Tạo thư mục con `integration_test/real_backend/` riêng (xem cấu trúc đề xuất ở trên), giữ nguyên 3 file gốc.

## Environment Availability

| Dependency | Yêu cầu bởi | Có sẵn | Version | Fallback |
|---|---|---|---|---|
| Flutter SDK | Toàn bộ integration_test | ✓ | 3.47.0 (Dart 3.13.0) | — |
| Docker | Testcontainers PostgreSQL cho test Java, seed script nếu chạy container | ✓ (client 29.3.1) nhưng **daemon không phản hồi lúc kiểm tra trong phiên research** | 29.3.1 | Cần xác nhận Docker Desktop đang chạy trước khi thực thi plan — không phải thiếu cài đặt, có thể chỉ chưa khởi động |
| adb (Android Debug Bridge) | Máy ảo Pixel 7, D-13 thử tay cắt mạng | ✓ | 1.0.41 (37.0.1) | — |
| PostgreSQL 14+ | Backend chạy thật (`mvn spring-boot:run` profile `dev`) | `[Chưa xác minh]` — cần kiểm `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env var đã cấu hình cho profile `dev` trên máy phát triển | — | Testcontainers chỉ dùng cho test tự động, không thay thế được cho chạy `mvn spring-boot:run` phục vụ app thật |
| Máy ảo Android (Pixel 7 API 36) | Toàn bộ phép thử tay và `integration_test` trên thiết bị | `[Chưa xác minh trong phiên research này]` — CONTEXT.md ghi máy ảo đã tồn tại (theo `MOI-TRUONG-PHAT-TRIEN.md`), không kiểm tra lại | — | — |

**Thiếu, không có fallback:** Docker daemon cần khởi động trước khi chạy `mvn test` đầy đủ (bao gồm các `*IntegrationTest`/`*EndToEndTest`) — nếu daemon không chạy, planner nên thêm bước xác nhận `docker info` thành công như một tiền điều kiện của task đầu tiên.

## Validation Architecture

### Test Framework
| Thuộc tính | Giá trị |
|---|---|
| Framework (backend) | JUnit 5 + Spring Boot Test + Testcontainers PostgreSQL 16, cấu hình sẵn |
| Framework (app) | `flutter_test` + `integration_test` SDK package (đã khai trong `pubspec.yaml`) |
| Config file (backend) | `src/main/resources/application-test.yml`, không có file cấu hình JUnit riêng ngoài mặc định Maven Surefire |
| Config file (app) | Không có file cấu hình `integration_test` riêng — chạy trực tiếp qua `flutter test integration_test/...` |
| Lệnh chạy nhanh (backend) | `mvn -q -o test -Dtest='!*IntegrationTest,!*EndToEndTest'` (18 test, ~vài giây, không cần Docker) |
| Lệnh chạy đầy đủ (backend) | `mvn test` (cần Docker daemon chạy cho Testcontainers) |
| Lệnh chạy (app, backend thật) | `flutter test integration_test/real_backend/<file>.dart --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 -d <device-id>` |

### Yêu cầu phase → Test map
| Success Criterion | Loại test | Lệnh | File tồn tại? |
|---|---|---|---|
| SC1 — `/auth/me` đúng sau login thật | `integration_test` mới | `flutter test integration_test/real_backend/auth_me_test.dart --dart-define=USE_MOCK=false ...` | ❌ Wave 0 |
| SC2a — cộng gộp danh mục con | `integration_test` mới | `flutter test integration_test/real_backend/category_rollup_test.dart ...` | ❌ Wave 0 |
| SC2b — transfer không vào báo cáo | `integration_test` mới | `flutter test integration_test/real_backend/transfer_excluded_from_report_test.dart ...` | ❌ Wave 0 |
| SC2c — sửa giao dịch hoàn tác 3 bước | `integration_test` mới | `flutter test integration_test/real_backend/edit_transaction_3step_test.dart ...` | ❌ Wave 0 |
| SC4 — idempotency 2 POST cùng key | `integration_test` mới + thử tay | `flutter test integration_test/real_backend/idempotency_test.dart ...` | ❌ Wave 0 |
| SC5 — gộp refresh token | `integration_test` mới + thử tay (D-13) | `flutter test integration_test/real_backend/refresh_token_concurrency_test.dart ...` | ❌ Wave 0 |
| Context-path không phá rate-limit | Java integration test (sửa lại path literal) | `mvn test -Dtest=AuthIdempotencyRateLimitEndToEndTest` (cần Docker) | ✓ đã có, cần sửa path |
| 2 gap Phase 4 (DebtReminderWorker/ExportAsyncRunner) vẫn xanh | Java integration test | `mvn test -Dtest=DebtReminderJobIntegrationTest,ExportJobIntegrationTest` (cần Docker) | ✓ đã có |

### Tần suất lấy mẫu
- **Mỗi task commit:** chạy nhanh phía đang sửa (`mvn -q -o test -Dtest='!*IntegrationTest,!*EndToEndTest'` cho backend, hoặc phép thử tay tương ứng cho app theo D-20 "sau mỗi bước chạy lại phép thử ngay").
- **Mỗi wave merge:** `mvn test` đầy đủ (cần Docker) + toàn bộ `integration_test/real_backend/`.
- **Cổng phase:** cả hai bộ suite xanh trước khi `/gsd-verify-work`, cộng thêm phép thử tay D-13 (cắt mạng thật trên máy ảo).

### Khoảng trống Wave 0
- [ ] `integration_test/real_backend/auth_me_test.dart` — SC1
- [ ] `integration_test/real_backend/category_rollup_test.dart` — SC2a
- [ ] `integration_test/real_backend/transfer_excluded_from_report_test.dart` — SC2b
- [ ] `integration_test/real_backend/edit_transaction_3step_test.dart` — SC2c
- [ ] `integration_test/real_backend/idempotency_test.dart` — SC4
- [ ] `integration_test/real_backend/refresh_token_concurrency_test.dart` — SC5
- [ ] Script seed `source/server` (`CommandLineRunner` `@Profile("dev")`) — điều kiện tiên quyết để `integration_test` dựng lại đúng tình huống (D-16)
- [ ] Sửa `RateLimitProperties.RULES` + `AuthIdempotencyRateLimitEndToEndTest` path literal — điều kiện tiên quyết trước khi thêm context-path được coi là "xong"

## Security Domain

### ASVS Categories áp dụng
| ASVS Category | Áp dụng | Kiểm soát chuẩn |
|---|---|---|
| V2 Authentication | có | JWT + BCrypt cost 12 (`SecurityConfig.passwordEncoder()`), đã có từ Phase 1, không đổi trong phase này |
| V3 Session Management | có | Refresh token rotation + reuse detection (`AuthService`), STATELESS session — phase 6 chỉ kiểm chứng lại, không sửa logic |
| V4 Access Control | có | Điều kiện quyền `user_id = current_user OR group_id IN (...)` đã nằm trong query — phase 6 không mở thêm access control mới (nhóm `ai`/`groups` ngoài phạm vi) |
| V5 Input Validation | có (gián tiếp) | `@Valid` + Bean Validation đã có ở mọi DTO, không có input mới ở phase này |
| V6 Cryptography | có | JWT `io.jsonwebtoken:jjwt` 0.13.x — không đổi |

### Mẫu rủi ro liên quan tới phase này
| Mẫu | STRIDE | Giảm thiểu chuẩn |
|---|---|---|
| Rate-limit brute-force `/auth/login` mất tác dụng do lệch path sau khi thêm context-path | Elevation of Privilege (mất lớp chống brute-force) | Sửa `RateLimitProperties.RULES` đồng thời với D-05, verify lại bằng `AuthIdempotencyRateLimitEndToEndTest` (§3 mục 2 phía trên) |
| `Idempotency-Key` cũ trên CSDL dev gây kết quả kiểm chứng sai lệch sau khi đổi context-path | Tampering (dữ liệu test không đáng tin) | Xoá bảng `idempotency_keys` trên CSDL dev sau khi thêm context-path, trước khi chạy D-13 |
| App gọi nhầm `/api/v1` (theo tài liệu cũ sai) thay vì `/v1` | Không áp dụng STRIDE trực tiếp — lỗi cấu hình, không phải tấn công | D-06/D-07 đã chốt: app không sửa, chỉ sửa dòng ví dụ sai trong tài liệu |

## Assumptions Log

| # | Khẳng định | Mục | Rủi ro nếu sai |
|---|---|---|---|
| A1 | UNIQUE constraint của bảng `budgets` chưa được đọc kỹ để đảm bảo script seed idempotent | Standard Stack — Alternatives Considered (script seed) | Script seed có thể tạo trùng ngân sách mỗi lần chạy lại, phá D-16 |
| A2 | Cú pháp `flutter test integration_test/... --dart-define=... -d <device>` chưa chạy thử thật trên máy ảo Pixel 7 trong phiên research này | Mục "`integration_test` Flutter chạy với backend thật" | Có thể cần `flutter drive` thay vì `flutter test` tuỳ cấu hình driver hiện có của project — cần verify khi bắt đầu plan |
| A3 | Cách `MockInterceptor` được gắn/không gắn vào Dio dựa trên `useMock` — chưa đọc file khởi tạo `Dio`/`dio_client.dart` | Mục "Danh sách ngoại lệ `/ai/*`" | Đề xuất cách hiện thực D-02 có thể sai vị trí sửa nếu điều kiện `useMock` đã nằm ở chỗ khác (ví dụ đăng ký interceptor có điều kiện thay vì trong chính interceptor) |
| A4 | `ApiClient`/`TransactionWriter` interface chính xác cho việc set `Idempotency-Key` thủ công trong test tự động chưa được đọc trực tiếp | Mục "Mẫu gửi 2 POST cùng Idempotency-Key" | Mẫu code phác thảo trong RESEARCH.md có thể không khớp API thật của `ApiClient`, planner cần đọc `source/app/lib/core/network/api_client.dart` trước khi viết task chi tiết |
| A5 | Cách seed access token hết hạn thủ công cho D-13b (ký JWT bằng cùng secret, `exp` quá khứ) khả thi trong `integration_test` — chưa xác minh `TokenStorage` có test hook | Mục "Mẫu bắn nhiều request song song với access token hết hạn" | Có thể cần cách khác (ví dụ đợi thật, hoặc thêm test-only setter) nếu `TokenStorage` không có cách ghi trực tiếp |
| A6 | PostgreSQL đã cấu hình sẵn cho profile `dev` trên máy phát triển (biến môi trường `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`) — chưa xác minh trong phiên này | Environment Availability | Nếu chưa cấu hình, cần thêm bước setup trước khi `mvn spring-boot:run` chạy được |
| A7 | Máy ảo Pixel 7 API 36 đã tồn tại và sẵn sàng sử dụng — dựa theo CONTEXT.md, không kiểm tra lại `emulator -list-avds` trong phiên này | Environment Availability | Nếu máy ảo chưa tồn tại/hỏng, cần dựng lại theo `MOI-TRUONG-PHAT-TRIEN.md` trước khi bắt đầu phase |

**Không có claim compliance/bảo mật cấp cao nào chỉ dựa trên `[ASSUMED]`** — mọi phát hiện về context-path, rate-limit, idempotency, endpoint đối chiếu đều `[VERIFIED]` qua đọc mã trực tiếp.

## Open Questions

1. **`affected_budgets` — có nên sửa trong Phase 6 hay tách plan riêng?**
   - Đã biết: backend trả rỗng vĩnh viễn, app có UI phụ thuộc, đặc tả `api/04` yêu cầu trả thật, `BudgetService.alerts()` có logic tương tự tái dùng được.
   - Chưa rõ: mức độ ưu tiên so với 8 bước D-18 đã chốt — đây không nằm trong danh sách D-11 gốc (endpoint thiếu hẳn) mà là "endpoint tồn tại nhưng field sai/rỗng", CONTEXT.md D-09 áp dụng ("backend lệch → sửa backend") nhưng chưa có quyết định rõ về việc có tính là "trong phạm vi Phase 6" hay để lại backlog riêng.
   - Khuyến nghị: đưa vào bước 4 (nhóm ghi, `POST /transactions`) của trình tự 8 bước D-18, vì cùng nhóm endpoint và cùng lúc kiểm chứng số dư ví đã đổi đúng.

2. **`MockInterceptor` có logic bật/tắt theo `useMock` ở đâu chính xác?**
   - Đã biết: file `mock_interceptor.dart` hiện tại không có nhánh điều kiện nào.
   - Chưa rõ: điều kiện `useMock` có thể nằm ở nơi khởi tạo `Dio` (đăng ký interceptor có điều kiện) — chưa đọc file đó trong phiên nghiên cứu này.
   - Khuyến nghị: planner đọc `source/app/lib/core/network/dio_client.dart` (hoặc tên tương đương) trước khi viết task D-02 cụ thể.

3. **Bảng `budgets` có UNIQUE constraint nào dùng được cho seed idempotent không?**
   - Đã biết: `api/05-NGAN-SACH.md` có nhắc "kiểm trùng danh mục trong cùng kỳ" khi tạo ngân sách (nghĩa là có ràng buộc nghiệp vụ, có thể có UNIQUE ở DB).
   - Chưa rõ: tên constraint chính xác trong `db/migration/V*.sql`.
   - Khuyến nghị: đọc migration bảng `budgets` trước khi viết `DevDataSeeder`.

## Sources

### Primary (HIGH confidence — đọc mã nguồn trực tiếp)
- `source/server/src/main/java/com/datn/financeapp/*/controller/*.java` (9 file) — ma trận đối chiếu endpoint
- `source/server/src/main/java/com/datn/financeapp/common/idempotency/IdempotencyAspect.java` — cơ chế idempotency
- `source/server/src/main/java/com/datn/financeapp/common/ratelimit/RateLimitProperties.java`, `RateLimitFilter.java` — phát hiện lỗi context-path
- `source/server/src/main/java/com/datn/financeapp/common/security/SecurityConfig.java` — xác nhận an toàn với context-path
- `source/server/src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java`, `source/app/lib/features/wallet/data/models/wallet.dart` — đối chiếu current_balance/projected_balance
- `source/server/src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionResponse.java`, `TransactionService.java`, `source/app/lib/features/transaction/data/models/transaction_request.dart` — phát hiện affected_budgets luôn rỗng
- `source/server/src/main/java/com/datn/financeapp/debt/service/DebtReminderWorker.java`, `report/export/ExportAsyncRunner.java` — xác nhận D-17 đã đóng
- `source/app/lib/core/network/mock/mock_router.dart`, `mock_interceptor.dart` — nguồn phạm vi và cơ chế mock
- `source/app/integration_test/*.dart` (3 file) — xác nhận chưa có test chạy backend thật
- `source/server/src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java` — xác nhận test dùng TestRestTemplate + RANDOM_PORT
- `mvn -q -o compile`, `mvn -q -o test -Dtest='!*IntegrationTest,!*EndToEndTest'` — xác nhận build/test baseline xanh (18 test, 0 lỗi mã nguồn)
- `docker --version`, `adb version`, `flutter --version` — kiểm tra môi trường

### Secondary (MEDIUM confidence)
- [Authorize HttpServletRequests :: Spring Security](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) — xác nhận `requestMatchers()` tự loại trừ context-path

### Tertiary (LOW confidence / chưa xác minh)
- Cú pháp chính xác `flutter test --dart-define ... -d <device>` cho integration_test trên máy ảo — dựa vào kiến thức huấn luyện, chưa chạy thử thật (xem A2)
- Cấu trúc `ApiClient`/`TokenStorage` cho việc set Idempotency-Key/token thủ công trong test — chưa đọc file (xem A4, A5)

## Metadata

**Confidence breakdown:**
- Ma trận đối chiếu endpoint: HIGH — đọc toàn văn 9 controller + mock_router.dart, không suy đoán
- Context-path / rate-limit / idempotency: HIGH — đọc mã trực tiếp, xác nhận qua WebSearch cho phần Spring Security
- Script seed / integration_test cụ thể: MEDIUM — khuyến nghị dựa trên pattern đã thấy trong dự án, nhưng chưa đọc `ApiClient`/`TokenStorage`/`dio_client.dart` trực tiếp
- 2 gap Phase 4: HIGH — xác nhận bằng đọc mã + chạy build/test thật

**Ngày nghiên cứu:** 2026-08-29
**Hiệu lực tới:** ~14 ngày — dự án đang trong giai đoạn phát triển tích cực (nhiều commit/ngày), mã có thể thay đổi nhanh; nên đọc lại các file trọng tâm nếu planner bắt đầu công việc sau khoảng thời gian này.
