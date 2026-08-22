# Research Summary: Backend Spring Boot Stack

**Ngày nghiên cứu:** 2026-08-22
**Ngữ cảnh:** Java 17, Spring Boot 3.3.x/3.4.x, Maven, PostgreSQL 14+, Flyway (V1-V5 đã có), đồ án tốt nghiệp (1 instance)

## Cấu trúc project

**Package-by-feature**, không phải package-by-layer. Mỗi nhóm nghiệp vụ (`auth/`, `wallet/`, `category/`, `transaction/`, `budget/`, `report/`, `ai/`, `debt/`, `recurring/`, `goal/`, `group/`) tự chứa Controller/Service/Repository/dto/entity của mình. `common/` chứa response wrapper, exception handler, security, idempotency, rate limit dùng chung. `scheduler/` chứa các job nền.

`transaction/` là module lõi — ưu tiên làm sớm và kỹ nhất vì debt/goal/recurring đều sinh bản ghi `transactions`.

## Data access

**Spring Data JPA làm chủ đạo** (`ddl-auto=validate`, Flyway sở hữu schema) **+ native SQL/JdbcTemplate có chủ đích** cho:
- Cập nhật `current_balance` bằng `UPDATE ... SET balance = balance + :delta` (atomic, tránh lost-update) thay vì load-modify-save
- Gọi hàm SQL có sẵn `fn_category_tree` để cộng gộp danh mục con
- Map view `v_budget_progress` qua native query/DTO
- Đối chiếu số dư định kỳ (job nền)

Row-level lock (`PESSIMISTIC_WRITE`) cho ví khi cần, đặc biệt ví chung nhóm gia đình. MapStruct cho Entity ↔ DTO.

## JWT / Auth

Thư viện `io.jsonwebtoken:jjwt` 0.13.x (API builder mới). Access token stateless (không lưu DB, chỉ chứa `sub`/`exp`/`plan`). Refresh token lưu bảng `refresh_tokens` riêng, **băm SHA-256** (không cần bcrypt vì đã random entropy cao), **rotation**: mỗi lần refresh phát hành token mới + revoke token cũ; tái sử dụng token đã revoke → revoke toàn bộ phiên user (reuse detection). `SecurityFilterChain` STATELESS + `OncePerRequestFilter` tuỳ biến.

## Validation

`spring-boot-starter-validation` (Jakarta Bean Validation) cho DTO request. `@RestControllerAdvice` toàn cục convert `MethodArgumentNotValidException` sang đúng format `error.fields: [{field, message}]`. Message tiếng Việt qua `messages_vi.properties`. Validation nghiệp vụ phức tạp (khớp category-type, ví đích khác ví nguồn) đưa vào Service, không nhét vào annotation.

## Idempotency-Key

**Bảng DB `idempotency_keys`** (không cần Redis cho 1 instance): `INSERT ... ON CONFLICT DO NOTHING` với `status=processing`, trả lại `response_body` đã lưu nếu key trùng và `status=completed`. Job dọn bản ghi >24h. Áp dụng qua `HandlerInterceptor` hoặc AOP `@Idempotent`.

## Rate limiting

**Bucket4j** (`bucket4j-core`) in-memory (không cần Redis cho 1 instance), bucket theo IP (auth) hoặc `user_id` (AI, còn lại) qua `OncePerRequestFilter` đặt sau filter JWT. Cache bucket bằng Caffeine để tự evict.

## Background jobs

**`@Scheduled` là đủ**, không cần Quartz (chỉ cần khi multi-instance hoặc lịch động runtime — không phải case này). Dùng `zone = "Asia/Ho_Chi_Minh"` trực tiếp trong cron annotation. 3 job chính: đối chiếu số dư ví, sinh giao dịch định kỳ, tự động lặp ngân sách — cộng thêm nhắc nợ, dọn ai_drafts. Mỗi job bọc try/catch để không crash scheduler pool.

## Testing

**Testcontainers PostgreSQL bắt buộc** cho integration test (không dùng H2 — H2 không hỗ trợ constraint/trigger/hàm SQL đặc thù của schema V1-V5). Spring Boot 3.1+ dùng `@ServiceConnection` để cấu hình gọn. Ưu tiên test: transaction atomicity 3-bước, đối chiếu số dư, quyền riêng tư nhóm (user A không đọc được dữ liệu user B).

## Pitfall checklist (rà trước mỗi phase liên quan)

| Pitfall | Phòng tránh |
|---|---|
| Transaction self-invocation làm mất `@Transactional` | Tách logic ra Service khác được inject |
| N+1 query khi list giao dịch kèm category/wallet/icon | `@EntityGraph`/`JOIN FETCH` hoặc DTO projection |
| Thiếu index | Verify `(user_id,date)`, `(wallet_id,date)`, `(category_id,date)` có trong migration thật |
| UTC vs giờ VN khi group theo ngày | `AT TIME ZONE 'Asia/Ho_Chi_Minh'` trong SQL, không xử lý sau ở Java |
| Rounding lỗi số tiền | `amount` luôn kiểu `Long`, không bao giờ `Double`/`float` |
| Lost update trên `current_balance` | Atomic UPDATE hoặc pessimistic lock, không đọc-sửa-ghi qua entity |
| Lấy hết rồi lọc quyền ở code | Điều kiện quyền luôn trong JPQL/native query |
| Quên loại transfer khỏi báo cáo | Query fragment dùng chung `excludeTransfer()` |
| Quên cộng gộp danh mục con | Bọc thành 1 repository method dùng chung gọi `fn_category_tree` |

## Dependency chính cho pom.xml

`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql`, `flyway-core` + `flyway-database-postgresql`, `spring-boot-starter-security`, `jjwt-api/impl/jackson` (0.13.x), `spring-boot-starter-validation`, `bucket4j-core`, `lombok`, `mapstruct` + `mapstruct-processor`, `caffeine`, test: `spring-boot-starter-test`, `spring-security-test`, `testcontainers` (`junit-jupiter`, `postgresql`, `spring-boot-testcontainers`).

## Gợi ý thứ tự phase (đầu vào cho roadmapper)

1. **Nền tảng**: cấu trúc project, `common/` (response, exception, security skeleton), Auth (JWT + refresh_tokens), idempotency + rate limit filter
2. **Lõi dữ liệu**: `wallet/` + `category/` (ít phụ thuộc)
3. **Giao dịch**: `transaction/` — trọng tâm rủi ro cao nhất, cần Testcontainers ngay
4. **Ngân sách**: `budget/` — phụ thuộc transaction, dùng `fn_category_tree`/`v_budget_progress`
5. **Mở rộng sinh giao dịch**: `debt/`, `recurring/` + `goal/` — cần job `@Scheduled`
6. **Báo cáo**: `report/` — đọc nhiều, chú ý N+1 và múi giờ
7. **Nhóm gia đình**: `group/` — thêm tầng quyền phức tạp lên mọi module trên, rủi ro riêng tư cao nhất
8. **AI**: `ai/` — ai_drafts + user_corrections, phụ thuộc transaction đã ổn định

Hai điểm rủi ro nghiệp vụ cao nhất cần research sâu hơn khi lập plan chi tiết: **transaction 3-bước sửa/xoá** và **quyền riêng tư nhóm gia đình**.
