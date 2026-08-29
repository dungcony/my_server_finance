# Phase 6: Nối API thật với app Flutter - Bản đồ Pattern

**Lập bản đồ:** 2026-08-29
**Số file phân tích:** 11
**Analog tìm được:** 9 / 11

## Phân loại file

| File mới/sửa | Repo | Vai trò | Luồng dữ liệu | Analog gần nhất | Chất lượng khớp |
|---|---|---|---|---|---|
| `src/main/resources/application.yml` | server | config | request-response | chính nó (thêm 1 dòng) | trực tiếp |
| `common/ratelimit/RateLimitProperties.java` | server | config/middleware | request-response | chính nó (sửa hằng số) | trực tiếp |
| `src/test/java/.../AuthIdempotencyRateLimitEndToEndTest.java` | server | test | request-response | chính nó (sửa path literal) | trực tiếp |
| `transaction/dto/CreateTransactionResponse.java` | server | model/DTO | CRUD | chính nó + `budget/dto/BudgetAlertResponse.java` | role-match |
| `transaction/service/TransactionService.java` (điền `affectedBudgets`) | server | service | CRUD | `budget/service/BudgetAlertListener.java` | role-match mạnh |
| `common/seed/DevDataSeeder.java` (mới) | server | config/seed | batch | `report/config/AsyncConfig.java` (cấu trúc `@Configuration`) + `AuthIdempotencyRateLimitEndToEndTest.java` (idempotent theo email/kiểm tồn tại) | partial-match (không có analog CommandLineRunner sẵn) |
| `lib/core/network/mock/mock_interceptor.dart` | app | middleware (Dio interceptor) | request-response | chính nó (thêm điều kiện) | trực tiếp |
| `lib/core/config/app_config.dart` | app | config | — | chính nó (đổi `defaultValue`) | trực tiếp |
| `source/app/CHUYEN-SANG-API-THAT.md` | app | docs | — | — | N/A (tài liệu) |
| `integration_test/real_backend/*.dart` (6 file mới) | app | test | request-response | `integration_test/add_transaction_flow_test.dart` | role-match, khác về network layer |
| `api/04-GIAO-DICH.md` (đối chiếu `affected_budgets`) | docs | docs | — | — | N/A (tài liệu) |

## Pattern Assignments

### `src/main/resources/application.yml` (config)

**Analog:** chính file này (`D:\PTIT\DATN\source\server\src\main\resources\application.yml`)

Hiện trạng toàn văn (26 dòng) — thêm đúng 1 khoá `server.servlet.context-path` dưới khối `spring:` hoặc cấp cao nhất:

```yaml
spring:
  application:
    name: financeapp
  jackson:
    property-naming-strategy: SNAKE_CASE
  datasource:
    url: ${DB_URL}
    ...
```

**Thêm (D-05):**
```yaml
server:
  servlet:
    context-path: /v1
```

Không có khối `server:` nào tồn tại hiện tại — đây là khối mới, không phải sửa dòng có sẵn.

---

### `common/ratelimit/RateLimitProperties.java` (config/middleware, request-response)

**Analog:** chính file này — `D:\PTIT\DATN\source\server\src\main\java\com\datn\financeapp\common\ratelimit\RateLimitProperties.java`

**Hiện trạng (dòng 20-23) — nguồn lỗi âm thầm khi thêm context-path:**
```java
public static final List<RateLimitRule> RULES = List.of(
        new RateLimitRule("auth", "/auth/", 5, Duration.ofMinutes(1), true),
        new RateLimitRule("ai", "/ai/", 30, Duration.ofMinutes(1), false),
        new RateLimitRule("default", "/", 120, Duration.ofMinutes(1), false));
```

**Sửa cần làm:** đổi `pathPrefix` thành `"/v1/auth/"`, `"/v1/ai/"` (RESEARCH.md khuyến nghị gắn cứng vì context-path đã chốt D-04, đơn giản hơn đọc `@Value` động). Rule `"default"` giữ `"/"` — không đổi vì đây là fallback catch-all không so `startsWith`.

**Kiểm chứng lại bằng:** `mvn test -Dtest=AuthIdempotencyRateLimitEndToEndTest` (đã có sẵn, xem file dưới).

**Không sửa `SecurityConfig.java`** — `requestMatchers()` tự loại trừ context-path (RESEARCH.md §"Spring Security — AN TOÀN"), path pattern giữ nguyên `/auth/register`, `/auth/login`, ...

---

### `src/test/java/.../AuthIdempotencyRateLimitEndToEndTest.java` (test, request-response)

**Analog:** chính file này — `D:\PTIT\DATN\source\server\src\test\java\com\datn\financeapp\auth\AuthIdempotencyRateLimitEndToEndTest.java`

**Điểm phải sửa (dòng 91):**
```java
return restTemplate.exchange(
        "/auth/register",
        org.springframework.http.HttpMethod.POST,
        ...
```
Đổi thành `"/v1/auth/register"` — vì `TestRestTemplate` xây URL tuyệt đối, KHÔNG tự cộng context-path (khác `MockMvc`). Đây là **duy nhất 1 test** cần sửa trong toàn bộ 55 file test dùng path tuyệt đối (RESEARCH.md §5).

**Pattern cấu trúc test có thể tái dùng cho task khác của phase (D-12 phía Java nếu cần):**
- `@Testcontainers` + `@Container @ServiceConnection PostgreSQLContainer<?>` — dựng CSDL thật
- `@DynamicPropertySource` để bơm `jwt.secret` test riêng
- `@BeforeEach cleanTables()` xoá dữ liệu qua repository, không qua SQL thô

---

### `transaction/dto/CreateTransactionResponse.java` + `TransactionService.java` (service, CRUD) — điền `affected_budgets`

**Vấn đề:** `CreateTransactionResponse.affectedBudgets` luôn là `List.of()` (constructor 2-tham số, dòng 13-15 của DTO). Đặc tả `api/04-GIAO-DICH.md` dòng 249-264 yêu cầu trả thật.

**DTO hiện tại** (`D:\PTIT\DATN\source\server\src\main\java\com\datn\financeapp\transaction\dto\CreateTransactionResponse.java`, toàn văn 19 dòng):
```java
public record CreateTransactionResponse(TransactionResponse transaction, NewBalance newBalance, List<Object> affectedBudgets) {

    public CreateTransactionResponse(TransactionResponse transaction, NewBalance newBalance) {
        this(transaction, newBalance, List.of());
    }

    public record NewBalance(UUID walletId, Long balance) {
    }
}
```
Kiểu `List<Object>` cần đổi thành kiểu cụ thể — dùng lại shape tương tự `BudgetAlertResponse`/`BudgetProgressProjection` (đọc thêm khi implement, chưa trích ở đây vì record `AffectedBudget` phía app đã có shape mẫu).

**App đã có model mẫu cho response mong đợi** (`source/app/lib/features/transaction/data/models/transaction_request.dart:161-178`):
```dart
final List<AffectedBudget> affectedBudgets;
// ...
affectedBudgets: ((json['affected_budgets'] as List<dynamic>?) ?? const [])
    .map((e) => AffectedBudget.fromJson(e as Map<String, dynamic>))
    .toList(),
// ...
Iterable<AffectedBudget> get warnings =>
    affectedBudgets.where((b) => b.needsAttention);
```
→ Backend cần trả object có ít nhất: mã ngân sách, `needs_attention` (boolean hoặc suy từ `status != 'normal'`), tên/mã danh mục, số liệu tiến độ.

**Nguồn logic tái dùng — `budget/service/BudgetAlertListener.java`** (toàn văn 101 dòng, đã đọc):
Pattern cộng gộp danh mục con + lọc theo ngân sách bị ảnh hưởng đã có sẵn:
```java
List<BudgetProgressProjection> affected = budgetProgressRepository.findActiveByUserAndCategoryInTree(
        event.userId(), event.categoryId(), event.date());

for (BudgetProgressProjection budget : affected) {
    if ("normal".equals(budget.getStatus())) {
        continue;
    }
    ...
}
```
`BudgetProgressProjection` (interface, `budget/repository/BudgetProgressRepository.java` dòng 78+) đã có `getId()`, `getCategoryId()`, `getWalletId()`, `getLimitAmount()`, `getSpentAmount()`, `getRemaining()`, `getRatio()`, `getStatus()`.

**Khuyến nghị hiện thực (theo RESEARCH.md Pitfall 3 + Open Question 1):**
1. Tạo DTO mới `AffectedBudgetResponse` (record) trong `transaction/dto/`, map trực tiếp từ `BudgetProgressProjection`.
2. Trong `TransactionService`, sau khi ghi giao dịch `expense` có `categoryId`, gọi `budgetProgressRepository.findActiveByUserAndCategoryInTree(userId, categoryId, date)` — **gọi trực tiếp trong luồng đồng bộ của `create()`/`update()`/`duplicate()`** (khác với `BudgetAlertListener` chạy `AFTER_COMMIT` bất đồng bộ), vì response `POST /transactions` cần trả ngay trong cùng request, không thể đợi event listener.
3. Cả 3 chỗ dựng `CreateTransactionResponse` trong `TransactionService.java` (dòng 121, 197, 283) cần đổi từ constructor 2-tham số sang constructor 3-tham số truyền danh sách thật.
4. Chỉ tính `affectedBudgets` khi `type == "expense"` và `categoryId != null` — giữ đúng điều kiện `BudgetAlertListener` đã áp dụng (dòng 56).

**Import pattern cần thêm vào `TransactionService.java`** (đã có sẵn style import trong file, dòng 1-44):
```java
import com.datn.financeapp.budget.repository.BudgetProgressRepository;
import com.datn.financeapp.budget.repository.BudgetProgressRepository.BudgetProgressProjection;
```

---

### `common/seed/DevDataSeeder.java` (mới — config/seed, batch)

**Không có analog CommandLineRunner/`@Profile` nào tồn tại trong codebase** — đây là pattern hoàn toàn mới cho backend. Ghép hai nguồn gần nhất:

**1. Cấu trúc `@Configuration`/`@Component` theo profile — mẫu từ `report/config/AsyncConfig.java`** (toàn văn 27 dòng):
```java
package com.datn.financeapp.report.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "exportTaskExecutor")
    public Executor exportTaskExecutor() {
        ...
    }
}
```
→ Áp dụng: package mới `common/seed/`, class `DevDataSeeder implements CommandLineRunner`, gắn `@Component @Profile("dev")`.

**2. Idempotent theo "kiểm tồn tại trước khi tạo" — mẫu từ `AuthIdempotencyRateLimitEndToEndTest.cleanTables()`/`register()`** (đã trích ở trên) minh hoạ cách repository test dùng `JdbcTemplate`-style thao tác trực tiếp; cho seed thật nên dùng `JdbcTemplate` + `INSERT ... ON CONFLICT (unique_key) DO NOTHING` cho user/ví/danh mục (đã đọc `application.yml` xác nhận `JdbcTemplate` là stack chuẩn của dự án — dùng cho "hot path" theo `CLAUDE.md`).

**Ràng buộc CSDL đã xác minh — bảng `budgets` KHÔNG có UNIQUE tự nhiên** (`D:\PTIT\DATN\db\migration\V3__ngan_sach.sql` dòng 5-33, toàn văn constraint đã đọc):
```sql
CREATE TABLE budgets (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID,
    group_id     UUID,
    category_id  UUID        NOT NULL,
    wallet_id    UUID,
    limit_amount BIGINT      NOT NULL,
    period_type  VARCHAR(10) NOT NULL,
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,
    auto_renew   BOOLEAN     NOT NULL DEFAULT TRUE,
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_bud_user     FOREIGN KEY (user_id)     REFERENCES users      (id) ON DELETE CASCADE,
    ...
    CONSTRAINT ck_bud_owner CHECK (
        (user_id IS NOT NULL AND group_id IS NULL) OR
        (user_id IS NULL     AND group_id IS NOT NULL)
    )
);
```
→ Với `budgets`, seed phải **kiểm tồn tại bằng SELECT trước** (ví dụ `WHERE user_id = ? AND category_id = ? AND is_active = TRUE`) rồi mới INSERT, không thể dùng `ON CONFLICT` (không có UNIQUE index phù hợp). Việc này khẳng định A1 của RESEARCH.md (UNIQUE constraint chưa đọc) — giờ đã đọc: xác nhận KHÔNG có UNIQUE nào ngoài PK.

**Khuyến nghị khung file:**
```java
package com.datn.financeapp.common.seed;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // 1. user thử — INSERT ... ON CONFLICT (email) DO NOTHING (users.email đã UNIQUE theo V1)
        // 2. 2 ví — kiểm tồn tại theo (user_id, name) trước khi INSERT (wallets không có UNIQUE tên)
        // 3. cây danh mục "Ăn uống" -> "Cà phê" — categories có thể đã có UNIQUE (user_id, name, parent_category_id); xác minh V1 trước khi code
        // 4. một ngân sách — SELECT tồn tại trước INSERT (không có UNIQUE)
    }
}
```

**Lưu ý D-16 (chạy lại được):** log rõ ràng khi bỏ qua vì đã tồn tại (giống style log của `BudgetAlertListener`/`GlobalExceptionHandler` dùng `log.error`/`log.info` qua Lombok `@Slf4j`).

---

### `lib/core/network/mock/mock_interceptor.dart` + `api_client.dart` (middleware, request-response) — ngoại lệ `/ai/*`

**Phát hiện quan trọng (giải quyết Open Question 2 của RESEARCH.md):** điều kiện `useMock` **không nằm trong `MockInterceptor`** — nó nằm ở nơi khởi tạo Dio, `api_client.dart` dòng 24-28:
```dart
if (AppConfig.useMock) {
  // Chặn trước mọi thứ: yêu cầu không bao giờ ra tới mạng.
  _dio.interceptors.add(MockInterceptor());
  refreshClient.interceptors.add(MockInterceptor());
}
```
→ Khi `useMock == false`, `MockInterceptor` **hoàn toàn không được gắn vào Dio** — không có cách nào cho request `/ai/*` "rơi vào mock" nếu chỉ sửa `mock_interceptor.dart` một mình.

**Cách hiện thực đúng theo D-02 (không phải như RESEARCH.md phác thảo ban đầu, mà đơn giản hơn):** sửa điều kiện gắn interceptor ở `api_client.dart` thành **luôn gắn `MockInterceptor`**, nhưng interceptor tự quyết định path nào mock path nào đi tiếp:

```dart
// api_client.dart — đổi điều kiện gắn interceptor
_dio.interceptors.add(MockInterceptor());   // luôn gắn — không còn if (AppConfig.useMock)
refreshClient.interceptors.add(MockInterceptor());
```

```dart
// mock_interceptor.dart — thêm nhánh quyết định
class MockInterceptor extends Interceptor {
  /// D-02 (06-CONTEXT.md): nhóm `ai` luôn dùng mock kể cả khi USE_MOCK=false,
  /// vì backend Phase 5 (AiController) chưa tồn tại. Gỡ khi Phase 5 xong.
  static const _alwaysMockPrefixes = ['/ai/'];

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final shouldMock = AppConfig.useMock ||
        _alwaysMockPrefixes.any(options.path.startsWith);
    if (!shouldMock) {
      handler.next(options);
      return;
    }
    final result = await MockRouter.instance.handle(options);
    handler.resolve(_response(options, result.status, result.json));
  }
  ...
}
```

**Cần import `AppConfig`** vào `mock_interceptor.dart` (hiện tại file này KHÔNG import `app_config.dart` — chỉ import `dio`, `mock_router.dart`).

---

### `lib/core/config/app_config.dart` (config)

**Analog:** chính file này (toàn văn 29 dòng, đã đọc)

**Sửa (D-21) — dòng 21:**
```dart
static const useMock = bool.fromEnvironment('USE_MOCK', defaultValue: true);
```
→ đổi `defaultValue: true` thành `defaultValue: false`.

**Comment dòng 18-20 cũng nên cập nhật** (không bắt buộc theo D-21 nhưng nhất quán tài liệu — hiện ghi "Mặc định bật vì backend chưa dựng", giờ backend đã nối xong).

---

### `integration_test/real_backend/*.dart` (6 file mới — test, request-response, chạy backend thật)

**Analog gần nhất (khác quan trọng):** `integration_test/add_transaction_flow_test.dart` (toàn văn 29 dòng, đã đọc) — pattern UI-driven test qua `app.main()` + `tester.pumpAndSettle()`.

**Điểm giống:**
```dart
import 'package:finance_ai/main.dart' as app;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('...', (tester) async {
    app.main();
    await tester.pumpAndSettle();
    ...
  });
}
```

**Điểm PHẢI khác — đây là chỗ quan trọng nhất cho planner:**
1. **Không truyền `--dart-define` trong code** — file 3 test cũ không có logic phân biệt mock/thật, vì chúng LUÔN chạy mock (mặc định `useMock=true` khi không truyền cờ). Test mới trong `real_backend/` phải chạy với lệnh:
   ```bash
   flutter test integration_test/real_backend/category_rollup_test.dart \
     --dart-define=USE_MOCK=false \
     --dart-define=API_BASE_URL=http://10.0.2.2:8080/v1 \
     -d emulator-5554
   ```
   `[ASSUMED — cú pháp chưa chạy thử thật, xem A2 RESEARCH.md]`. Không có cách "ép" `USE_MOCK=false` từ bên trong file `.dart` — đây là compile-time constant (`bool.fromEnvironment`), phải truyền ở dòng lệnh.
2. **Cần backend đang chạy thật** (`mvn spring-boot:run` profile `dev`, seed đã chạy) trước khi test — khác 3 test cũ tự đủ vì mock chạy in-memory.
3. **Idempotency test (`idempotency_test.dart`) cần set `Idempotency-Key` cố định thủ công** — không dùng UI để tạo giao dịch (UI luôn qua `ApiClient.post()` tự sinh key ngẫu nhiên mỗi lần, xem `api_client.dart` dòng 90-103: `headers: {'Idempotency-Key': idempotencyKey ?? _uuid.v4()}`). Cần gọi trực tiếp `Dio`/`ApiClient` với `idempotencyKey` truyền tay thay vì thao tác qua UI:
   ```dart
   // ApiClient.post đã hỗ trợ sẵn tham số idempotencyKey — dùng trực tiếp thay vì qua UI
   final apiClient = ApiClient(tokenStorage: ..., onSessionExpired: () {});
   final r1 = await apiClient.post('/transactions', body: payload, idempotencyKey: 'integration-test-fixed-key-001');
   final r2 = await apiClient.post('/transactions', body: payload, idempotencyKey: 'integration-test-fixed-key-001');
   expect(r1['transaction']['id'], r2['transaction']['id']);
   ```
   Đây là API thật của `ApiClient.post()` (đã xác minh chữ ký ở `api_client.dart` dòng 90-103, giải quyết A4 của RESEARCH.md — **có tham số `idempotencyKey` sẵn, không cần Dio trần**).
4. **Refresh token concurrency test (`refresh_token_concurrency_test.dart`)** — `AuthInterceptor._refreshing` (biến instance, `auth_interceptor.dart` dòng 33) đã gộp refresh; test kiểm bằng cách bắn nhiều request cùng lúc qua cùng một `ApiClient` instance (không tạo nhiều `ApiClient`, vì `_refreshing` là state instance-level) sau khi access token hết hạn — A5 của RESEARCH.md (seed token hết hạn) **chưa có test hook trong `TokenStorage`** (đã đọc toàn văn `token_storage.dart` 54 dòng — không có setter test-only). Planner cần cân nhắc thêm hook hoặc dựa vào D-13 "thử tay cắt mạng thật" làm phương án chính, test tự động chỉ verify logic gộp bằng cách mock `_refreshing` gián tiếp qua thời gian chờ ngắn hơn `access-token-expiry-seconds: 3600` (khó, JWT `exp` cố định) — RESEARCH.md A5 vẫn còn mở, planner nên ưu tiên phép thử tay D-13 cho tiêu chí này và giữ test tự động ở mức tối thiểu (kiểm cấu trúc gọi, không ép token hết hạn thật).
5. Giữ nguyên 3 file cũ (`add_transaction_flow_test.dart`, `overview_updates_test.dart`, `transaction_book_scroll_test.dart`) — không sửa, đặt file mới ở thư mục con `integration_test/real_backend/`.

**Package cần trong `pubspec.yaml`:** đã có sẵn `integration_test` SDK package (RESEARCH.md xác nhận), không cần thêm.

---

## Shared Patterns

### Đối chiếu tài liệu khi backend lệch (D-09/D-10/D-24)
**Nguồn:** `CONTEXT.md` D-09 — bất kỳ lệch nào giữa backend và `api/*.md` → sửa backend, trừ khi `api/*.md` mâu thuẫn `db/migration/V*.sql`/nghiệp vụ.
**Áp dụng cho:** mọi task chạm `affected_budgets`, context-path, rate-limit.

### Idempotent theo "kiểm tồn tại trước" khi không có UNIQUE constraint
**Nguồn:** phân tích schema `budgets` (`V3__ngan_sach.sql`), không có UNIQUE khả dụng.
**Áp dụng cho:** `DevDataSeeder.java` — mọi INSERT vào `budgets` phải SELECT-before-INSERT; INSERT vào `users`/`categories` (có khả năng UNIQUE, cần xác minh `V1__nen_tang.sql` trước khi code) có thể dùng `ON CONFLICT DO NOTHING`.

### `@Slf4j` + Lombok logging style
**Nguồn:** `BudgetAlertListener.java` dòng 38 (`@Slf4j`), dòng 83 (`log.error(...)`).
**Áp dụng cho:** `DevDataSeeder.java` — log khi bỏ qua bản ghi đã tồn tại, log khi seed hoàn tất.

### Response `{success, data}` không đổi
**Nguồn:** `api/00-QUY-UOC-CHUNG.md` §4, đã verify khớp `GlobalExceptionHandler`/`MockError.toJson()`.
**Áp dụng cho:** mọi test `integration_test/real_backend/*.dart` — luôn bóc `response['data']` giống cách `ApiClient._unwrap()` làm, không parse response thô.

## Không tìm được analog

| File | Vai trò | Luồng dữ liệu | Lý do |
|---|---|---|---|
| `common/seed/DevDataSeeder.java` | config/seed | batch | Không có `CommandLineRunner`/`ApplicationRunner`/`@Profile` nào tồn tại trong codebase backend — pattern hoàn toàn mới, ghép từ `AsyncConfig.java` (cấu trúc bean theo profile) + suy luận từ schema `V3__ngan_sach.sql` (thiếu UNIQUE) |
| `integration_test/real_backend/refresh_token_concurrency_test.dart` | test | event-driven (concurrency) | Không có test hook nào trong `TokenStorage` để seed token hết hạn thủ công (A5 RESEARCH.md) — cần quyết định thêm hook test-only hay dựa hoàn toàn vào thử tay D-13 |

## Metadata

**Phạm vi tìm analog:** `source/server/src/main/java/com/datn/financeapp/` (toàn bộ package `common/`, `transaction/`, `budget/`, `report/config/`), `source/server/src/test/java/com/datn/financeapp/auth/`, `source/server/src/main/resources/`, `source/app/lib/core/`, `source/app/integration_test/`, `db/migration/V3__ngan_sach.sql`
**Số file quét trực tiếp (Read toàn văn hoặc đoạn lớn):** 15
**Ngày lập bản đồ pattern:** 2026-08-29
