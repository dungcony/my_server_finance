# Phase 1: Nền tảng & Xác thực - Research

**Researched:** 2026-08-22
**Domain:** Spring Boot 3.x khung dự án + JWT auth + idempotency + rate limiting
**Confidence:** HIGH (stack đã chốt, đã verify version thật qua Maven Central; điểm MEDIUM/LOW được đánh dấu rõ ở từng mục)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Cấu hình môi trường & bí mật**
- D-01: JWT secret và thông tin kết nối DB nạp qua **biến môi trường bắt buộc, không có giá trị mặc định**. App fail-fast khi thiếu — không được viết `${JWT_SECRET:default-nao-do}`.
- D-02: Nạp biến bằng **file `.env` bị gitignore**, kèm **`.env.example` commit vào repo** làm mẫu. `docker-compose.yml` và Spring Boot cùng đọc file này (Spring dùng `spring-dotenv` hoặc cơ chế tương đương).
- D-03: Chia **2 profile: `dev` + `test`**. `application.yml` giữ phần chung, `application-dev.yml` trỏ Postgres local qua Docker, `application-test.yml` cho Testcontainers. **Chưa tạo `prod`**.
- D-04: Postgres cho dev dựng bằng **Docker Compose** (thêm `docker-compose.yml` vào `source/server/`).
- D-05: **Hệ quả bắt buộc của D-01+D-04:** lần chạy đầu sẽ fail-fast nếu chưa chuẩn bị. Phase 1 **phải** giao kèm `source/server/README.md` mô tả đúng trình tự khởi động (copy `.env.example` sang `.env`, `docker compose up -d`, rồi `mvn spring-boot:run`).

**Migration (CORE-09 / CORE-10)**
- D-06: **`V6__va_loi_bao_mat.sql` và `V7__ha_tang_xac_thuc.sql` ĐÃ tồn tại** trong `db/migration/` (commit 533e95b) — trái với mô tả trong ROADMAP.md cũ. 4 bảng hạ tầng (`refresh_tokens`, `idempotency_keys`, `password_reset_tokens`, `login_attempts`) đã có, cột khớp CORE-10.
- D-07: Task đầu tiên của phase là **verify bằng cách chạy thật**: dựng project + trỏ Flyway vào `db/migration/` gốc, chạy `V1` đến `V7` trên một DB sạch. Lỗi phát sinh thì sửa tại chỗ. Không viết lại V6/V7 từ đầu.
- D-08: Planner phải **cập nhật ROADMAP.md Phase 1** để bỏ mô tả "viết migration trước khi viết code Java", thay bằng "verify migration V6/V7 đã có".
- D-09: Flyway trỏ vào `db/migration/` ở thư mục gốc DATN (`../../db/migration`), **không copy** vào `source/server/`. `ddl-auto=validate`.

**Idempotency (CORE-03)**
- D-10: Áp qua **AOP với annotation `@Idempotent`** đặt tường minh lên từng method controller cần bảo vệ — không dùng HandlerInterceptor tự động cho mọi POST.
- D-11: **`/auth/login`, `/auth/refresh`, `/auth/logout` KHÔNG gắn `@Idempotent`** — POST-hành-động, không phải POST-tạo-mới.
- D-12: Luồng: tra `idempotency_keys` — chưa có thì `INSERT ... ON CONFLICT DO NOTHING` với `status='processing'`, chạy nghiệp vụ, lưu `response_status`/`response_body`, đổi `status='completed'`. Key trùng và `completed` thì trả lại `response_body` đã lưu, không chạy lại nghiệp vụ.
- D-13: Key trùng nhưng `status='processing'` → **trả 409 ngay**, không chờ, không chạy song song.
- D-14: Mã lỗi cho D-13 là **`REQUEST_IN_PROGRESS` (409) — mã MỚI, phải bổ sung vào `api/00-QUY-UOC-CHUNG.md` mục 6 trong cùng phase này.**
- D-15: Nhớ key trong **24 giờ**, cần job dọn bản ghi quá hạn trong `scheduler/`.

**Rate limiting (CORE-04)**
- D-16: Triển khai **đầy đủ cả 3 tầng ngay ở Phase 1**:

  | Nhóm | Quota | Đếm theo |
  |---|---|---|
  | Auth (`/auth/**`) | 5/phút | **IP** |
  | AI (`/ai/**`) | 30/phút | `user_id` |
  | Còn lại | 120/phút | `user_id` |

- D-17: Bucket4j in-memory + Caffeine cache (tự evict bucket cũ). Không Redis.
- D-18: Filter rate limit đặt **sau** filter JWT.
- D-19: Header `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` trả trên **mọi response**, không chỉ khi bị chặn.
- D-20: Vượt quota → HTTP `429` với mã `RATE_LIMIT_EXCEEDED`.
- D-21: **Phân biệt rõ với AUTH-07** — CORE-04 đếm *request* theo *IP*; AUTH-07 đếm *lần sai mật khẩu* theo *tài khoản* qua `login_attempts`. Cả hai độc lập, đều phải có.

### Claude's Discretion

- D-22 (Gửi email reset password — AUTH-06): **định nghĩa interface `PasswordResetNotifier` với implementation dev ghi mã reset ra log**, không tích hợp SMTP thật ở Phase 1. Nếu người dùng muốn SMTP thật thì phải hỏi trước khi code.
- D-23 (Phạm vi test Phase 1): **dựng Testcontainers ngay** (không đợi Phase 3) vì các luồng cần kiểm chứng (rotation + reuse detection, idempotency `ON CONFLICT`, khoá 5 lần) đều phụ thuộc hành vi Postgres thật. Ưu tiên test: (1) reuse detection thu hồi toàn bộ phiên, (2) idempotency trả đúng kết quả lần đầu, (3) khoá đăng nhập 15 phút sau 5 lần sai.
- D-24: Chi tiết còn lại (cấu trúc `common/`, exception handler, MapStruct config, tên class) theo `research/SUMMARY.md` và chuẩn Spring Boot — không cần hỏi lại.

### Deferred Ideas (OUT OF SCOPE)

- Mâu thuẫn tài liệu múi giờ báo cáo (`api/00` mục 13, `source/server/CLAUDE.md` quy tắc 7) — sửa ở Phase 4, KHÔNG sửa ở Phase 1.
- Profile `prod` + cấu hình deploy.
- Tích hợp SMTP thật cho email reset password.
- Bảng `export_jobs` (REPORT-05) — Phase 4.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CORE-01 | Response format `{success,data}`/`{success,error}`, lỗi validate trả `error.fields` | Xem `## Kiến trúc — response wrapper & exception handler`, `## Code Examples` |
| CORE-02 | Phân trang chuẩn, lọc `from_date/to_date`/`period`, sort | Không có endpoint phân trang ở Phase 1 (auth không trả list) — chỉ cần dựng sẵn `PageRequest`/DTO chuẩn dùng lại từ Phase 2. Xem `## Ghi chú CORE-02` |
| CORE-03 | Idempotency-Key cho POST tạo mới, nhớ 24h | Xem `## Idempotency — @Idempotent AOP` |
| CORE-04 | Rate limit theo endpoint, header `X-RateLimit-*` | Xem `## Rate limiting — Bucket4j + Caffeine` |
| CORE-05 | Quyền kiểm tra trong SQL, 404 không phải 403 | Áp dụng đầy đủ từ Phase 2 trở đi; Phase 1 chỉ có `/auth/me` tự nhiên đã giới hạn theo `current_user` |
| CORE-06 | Xoá mềm, DELETE idempotent | Không có DELETE endpoint ở Phase 1 |
| CORE-07 | Timestamp UTC | `refresh_tokens`, `password_reset_tokens`, `login_attempts` đều `TIMESTAMPTZ` — JPA `Instant`/`OffsetDateTime` map đúng |
| CORE-08 | `amount` là số nguyên VND | Không có `amount` ở Phase 1 |
| CORE-09 | Verify V6 chạy sạch | Xem `## Xác minh migration V6/V7` |
| CORE-10 | Verify V7 chạy sạch, entity khớp schema | Xem `## Xác minh migration V6/V7`, `## Entity ↔ schema mapping` |
| CORE-11 | Đối chiếu schema thật trước khi tin `api/*.md` | Đã thực hiện trong research này — xem `## Điểm lệch tài liệu phát hiện được` |
| AUTH-01 | Đăng ký, bcrypt≥12, tạo ví Tiền mặt, sinh token | Xem `## Đăng ký — tạo ví Tiền mặt trong cùng transaction` |
| AUTH-02 | Đăng nhập, sai email/password cùng mã lỗi | Xem `## Code Examples` — AuthService.login |
| AUTH-03 | Refresh token 1 lần, reuse → thu hồi toàn bộ | Xem `## Refresh token rotation & reuse detection` |
| AUTH-04 | Logout thu hồi refresh hiện tại; đổi mật khẩu thu hồi mọi refresh khác | Xem `## Refresh token rotation & reuse detection` |
| AUTH-05 | Xem/sửa hồ sơ, không sửa email/plan | DTO tách riêng `UpdateProfileRequest` chỉ có `full_name`/`avatar_url` |
| AUTH-06 | Đổi/quên/đặt lại mật khẩu | Xem `## PasswordResetNotifier (D-22)` |
| AUTH-07 | Khoá 15 phút sau 5 lần sai, log IP | Xem `## Khoá đăng nhập & login_attempts` |
| AUTH-08 | Access token 1h chỉ id/plan/exp; refresh 30 ngày hash DB | Xem `## JWT với jjwt 0.13.x` |
</phase_requirements>

## Summary

Phase 1 dựng một backend Spring Boot 3.4.x/Java 17 từ số không, với 4 mảng kỹ thuật chính: (1) khung response/exception dùng chung, (2) JWT auth đầy đủ vòng đời (đăng ký/đăng nhập/refresh rotation+reuse detection/đổi-quên-đặt lại mật khẩu/khoá tài khoản), (3) idempotency qua AOP, (4) rate limiting 3 tầng qua Bucket4j+Caffeine. Toàn bộ migration V1–V7 đã có sẵn trong `db/migration/` (bao gồm 4 bảng hạ tầng auth) — việc đầu tiên là **verify chạy thật**, không viết lại.

Điểm quan trọng nhất cho planner: `jjwt` phiên bản mới nhất thật sự tồn tại là **0.13.0** (phát hành 2025-08-20, xác nhận qua Maven Central) — CONTEXT.md ghi "0.13.x" là đúng, dùng API builder mới (`Jwts.builder()...signWith(key)`, `Jwts.parser().verifyWith(key).build().parseSignedClaims(jws)`), **không** dùng cú pháp cũ `Jwts.parserBuilder()`/`setSigningKey()` của 0.11.x. Bucket4j cũng đã đổi tên artifact từ `bucket4j-core` sang `bucket4j_jdk17-core` kể từ bản 8.11 — nếu planner/executor gõ `com.bucket4j:bucket4j-core` theo thói quen cũ (dừng ở 8.10.1, phát hành 2024) sẽ mất các bản vá và tính năng mới; artifact đúng cho Java 17 hiện tại là `com.bucket4j:bucket4j_jdk17-core:8.19.0`.

**Primary recommendation:** Dùng đúng artifact/API mới nhất đã verify (bảng version bên dưới), giữ đúng 24 quyết định D-01…D-24 của CONTEXT.md không đổi, và đối chiếu từng entity JPA với cột thật trong V1/V7 trước khi viết Repository — schema đã sai lệch với tài liệu API 4 lần trong quá khứ (theo `db/README.md`).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Response envelope `{success,data}` | API/Backend (`@RestControllerAdvice`, wrapper) | — | Toàn bộ controller phải trả cùng format, xử lý tập trung ở tầng advice |
| JWT sinh/verify | API/Backend (`auth/`, `common/security`) | — | Access token stateless, không lưu DB; verify ở filter mỗi request |
| Refresh token lifecycle | API/Backend (Service) + Database (bảng `refresh_tokens`) | — | Service quyết định rotation/reuse, DB là nguồn sự thật cho trạng thái revoked |
| Idempotency cache | API/Backend (AOP `@Idempotent`) + Database (`idempotency_keys`) | — | Không dùng Redis (1 instance) — DB đủ vai trò cache bền |
| Rate limiting | API/Backend (Filter, sau JWT filter) + in-memory (Caffeine) | — | Không cần tầng ngoài (API Gateway) vì đồ án 1 instance |
| Password reset delivery | API/Backend (interface `PasswordResetNotifier`) | — (chưa có SMTP) | D-22 — giữ hợp đồng ổn định để thay implementation sau |
| Ví "Tiền mặt" khởi tạo | API/Backend (Service dùng chung nhỏ trong `auth/` hoặc `common/`) | Database (bảng `wallets`) | Chỉ 1 insert tối thiểu, không dựng cả module `wallet/` |
| Migration schema | Database (Flyway, `db/migration/` gốc) | — | Flyway sở hữu schema tuyệt đối, JPA chỉ validate |

## Standard Stack

### Core
| Library | Version (verified) | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot (starter-parent) | **3.4.13** hoặc 3.3.x (tuỳ pin) `[VERIFIED: Maven Central 2026-08-22]` | Framework nền | Đã chốt ở CONTEXT/PROJECT, dòng 3.4.x là bản LTS-track mới nhất trước Spring Boot 4 |
| Java | 17 | Ngôn ngữ | Đã chốt |
| Maven | — | Build tool | Đã chốt |
| `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson` | **0.13.0** `[VERIFIED: Maven Central, release 2025-08-20]` | Sinh/verify JWT | API builder hiện đại, an toàn kiểu hơn 0.11.x |
| `org.flywaydb:flyway-core` + `flyway-database-postgresql` | **13.3.0** `[VERIFIED: Maven Central 2026-08-22]` | Migration | Flyway 10+ tách driver Postgres ra module riêng — **bắt buộc** thêm `flyway-database-postgresql` cùng `flyway-core`, thiếu sẽ lỗi "Unsupported Database: PostgreSQL 14.x" khi khởi động |
| `org.postgresql:postgresql` | dùng version Spring Boot BOM quản lý (không tự ghi version) | JDBC driver | — |
| `com.bucket4j:bucket4j_jdk17-core` | **8.19.0** `[VERIFIED: Maven Central 2026-08-22]` | Token bucket rate limit | Artifact đã đổi tên từ `bucket4j-core` sang `bucket4j_jdk17-core` kể từ 8.11 (tách theo JDK target) — dùng đúng artifact mới, KHÔNG dùng `bucket4j-core` (dừng ở 8.10.1, cũ) |
| `com.github.ben-manes.caffeine:caffeine` | **3.2.4** (Spring Boot BOM quản lý version, không cần ghi tay) `[VERIFIED: Maven Central]` | Cache bucket theo IP/user | Chuẩn de-facto cho in-memory cache Java, tự evict |
| `me.paulschwarz:spring-dotenv` | **4.0.0** `[VERIFIED: Maven Central]` | Nạp `.env` vào Spring `Environment` | D-02 yêu cầu `.env`; đây là lib phổ biến nhất cho mục đích này, publish dạng `springboot3-dotenv` cho Spring Boot 3 (kiểm tra artifact con đúng khi thêm dependency — xem `## Cấu hình .env` bên dưới) |
| `org.testcontainers:postgresql` + `junit-jupiter` | BOM **2.0.5** `[VERIFIED: Maven Central 2026-08-22]` | Integration test với Postgres thật | D-23 — bắt buộc dùng ngay Phase 1 |
| `org.mapstruct:mapstruct` + `mapstruct-processor` | **1.6.3** `[VERIFIED: Maven Central]` | Entity ↔ DTO | Chuẩn dự án theo research/SUMMARY.md |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-security` | theo BOM | `SecurityFilterChain` STATELESS, `OncePerRequestFilter` cho JWT | Bắt buộc |
| `spring-boot-starter-validation` | theo BOM | Jakarta Bean Validation cho request DTO | `@Valid` trên `RegisterRequest`, `LoginRequest`, v.v. |
| `lombok` | theo BOM | Giảm boilerplate | Getter/setter/builder entity, DTO |
| `spring-boot-starter-web`, `spring-boot-starter-data-jpa` | theo BOM | REST + JPA | Đã chốt |
| `spring-boot-starter-test`, `spring-security-test` | theo BOM | Unit/slice test | `@WithMockUser` cho test controller |
| `spring-boot-testcontainers` | theo BOM | Tích hợp `@ServiceConnection` | Tự động cấu hình `DataSource` trỏ container test |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `bucket4j_jdk17-core` tự code filter | `bucket4j-spring-boot-starter` (MarcGiffing) | Starter cấu hình qua YAML, không cần code filter — nhưng CONTEXT.md D-18 yêu cầu filter đặt **sau** JWT filter để lấy `user_id`, điều này khó kiểm soát chính xác thứ tự filter chain qua auto-config của starter bên thứ 3. **Khuyến nghị: tự viết `OncePerRequestFilter`**, không dùng starter, để kiểm soát thứ tự và áp dụng bảng quota tùy biến (D-16) |
| `spring-dotenv` | Đọc `.env` thủ công bằng `dotenv-java` + `EnvironmentPostProcessor` tự viết | `spring-dotenv` đã làm sẵn việc này qua `PropertySource`, ít code hơn — giữ theo D-02 khuyến nghị |
| Idempotency AOP tự viết | Redis + Redisson idempotent annotation | D-17 đã loại Redis (1 instance); không áp dụng |

**Installation (đoạn `pom.xml` dependencies cốt lõi, phiên bản đã verify):**
```xml
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.13.0</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.13.0</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.13.0</version>
  <scope>runtime</scope>
</dependency>

<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<dependency>
  <groupId>com.bucket4j</groupId>
  <artifactId>bucket4j_jdk17-core</artifactId>
  <version>8.19.0</version>
</dependency>

<dependency>
  <groupId>me.paulschwarz</groupId>
  <artifactId>springboot3-dotenv</artifactId>
  <version>4.0.0</version>
</dependency>

<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
```

`[ASSUMED]` — tên artifact chính xác `springboot3-dotenv` (thay vì `spring-dotenv`) cho tích hợp auto-config Spring Boot 3 dựa trên tên convention phổ biến của README dự án; **planner nên xác nhận tên artifact chính xác trên trang GitHub `paulschwarz/spring-dotenv` trước khi ghi vào `pom.xml` thật**, vì search tool không lấy được nội dung README chi tiết trong phiên này.

**Version verification:** Toàn bộ version bảng trên đã chạy `curl` trực tiếp Maven Central `maven-metadata.xml` trong phiên nghiên cứu này (2026-08-22) — xem log tool call, không dựa vào training data.

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────┐
                         │   Client (Flutter app)   │
                         └────────────┬─────────────┘
                                      │ HTTPS JSON
                                      ▼
                    ┌──────────────────────────────────┐
                    │   OncePerRequestFilter: JwtAuthFilter │  ← đọc Authorization: Bearer, verify chữ ký
                    │   (đặt TRƯỚC filter rate limit)       │     set SecurityContext nếu hợp lệ
                    └────────────────┬─────────────────┘
                                     ▼
                    ┌──────────────────────────────────┐
                    │  OncePerRequestFilter: RateLimitFilter │  ← D-18: SAU JWT filter
                    │  key = IP (nhóm auth) hoặc user_id (còn lại) │
                    │  luôn set header X-RateLimit-*        │
                    └────────────────┬─────────────────┘
                          429 nếu vượt quota │ ok
                                     ▼
                    ┌──────────────────────────────────┐
                    │        DispatcherServlet          │
                    └────────────────┬─────────────────┘
                                     ▼
                    ┌──────────────────────────────────┐
                    │   AOP Aspect: IdempotencyAspect    │  ← chỉ chạy trên method có @Idempotent
                    │   tra idempotency_keys trước khi   │     (KHÔNG áp cho /auth/login|refresh|logout)
                    │   gọi method thật                  │
                    └────────────────┬─────────────────┘
                                     ▼
                    ┌──────────────────────────────────┐
                    │           Controller               │  ← @Valid request DTO
                    └────────────────┬─────────────────┘
                                     ▼
                    ┌──────────────────────────────────┐
                    │            Service                 │  ← @Transactional, business logic
                    │  AuthService: register/login/refresh│
                    └──────┬───────────────────┬────────┘
                           ▼                   ▼
              ┌────────────────────┐  ┌──────────────────────┐
              │  JPA Repository      │  │  PasswordEncoder (BCrypt) │
              │  users/refresh_tokens│  │  JwtService (jjwt)        │
              │  login_attempts      │  └──────────────────────┘
              └──────────┬───────────┘
                          ▼
              ┌────────────────────────┐
              │   PostgreSQL (Flyway     │  ← db/migration/ gốc, V1-V7
              │   sở hữu schema)          │
              └────────────────────────┘
                          ▲
                          │ mỗi request lỗi → @RestControllerAdvice
                          │ chuyển thành {success:false, error:{...}}
                    ┌─────┴──────────────────┐
                    │  GlobalExceptionHandler  │
                    └─────────────────────────┘

Job nền song song (không nằm trong request path):
  @Scheduled IdempotencyCleanupJob → xoá idempotency_keys > 24h
```

### Recommended Project Structure
```
src/main/java/com/datn/financeapp/
├── common/
│   ├── response/          # ApiResponse<T>, ErrorResponse, PageMeta
│   ├── exception/         # GlobalExceptionHandler (@RestControllerAdvice), custom exceptions
│   ├── security/          # SecurityConfig, JwtAuthFilter, JwtService
│   ├── idempotency/       # @Idempotent annotation, IdempotencyAspect, IdempotencyKeyRepository
│   └── ratelimit/         # RateLimitFilter, BucketRegistry (Caffeine), RateLimitProperties
├── auth/
│   ├── controller/        # AuthController
│   ├── service/            # AuthService, PasswordResetNotifier (+ LogPasswordResetNotifier)
│   ├── repository/         # UserRepository, RefreshTokenRepository, PasswordResetTokenRepository, LoginAttemptRepository
│   ├── entity/              # User, RefreshToken, PasswordResetToken, LoginAttempt
│   └── dto/                 # RegisterRequest, LoginRequest, AuthResponse, ...
├── scheduler/
│   └── IdempotencyCleanupJob.java   # duy nhất 1 job Phase 1 — dọn idempotency_keys >24h
└── FinanceAppApplication.java

src/main/resources/
├── application.yml            # config chung
├── application-dev.yml        # trỏ Postgres Docker local
├── application-test.yml       # cấu hình Testcontainers
└── messages_vi.properties     # thông báo lỗi tiếng Việt

src/test/java/.../auth/
└── AuthIntegrationTest.java   # Testcontainers — reuse detection, idempotency, lockout

docker-compose.yml               # Postgres dev
.env.example                     # mẫu biến môi trường (commit)
.env                              # thật, gitignore
README.md                        # trình tự khởi động (D-05)
```

### Pattern 1: Response wrapper thống nhất

**What:** Một generic `ApiResponse<T>` bọc mọi response thành công, `ErrorResponse` cho thất bại, ánh xạ qua `@RestControllerAdvice`.
**When to use:** Mọi controller — không controller nào tự trả JSON thô.

```java
// common/response/ApiResponse.java
public record ApiResponse<T>(boolean success, T data) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data);
    }
}

// common/response/ErrorResponse.java
public record ErrorResponse(boolean success, ErrorBody error) {
    public record ErrorBody(String code, String message, List<FieldError> fields, Object detail) {}
    public record FieldError(String field, String message) {}
}
```

```java
// common/exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
        var body = new ErrorResponse(false,
            new ErrorResponse.ErrorBody("VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.", fields, null));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BusinessException.class) // custom, mang theo code + http status
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        var body = new ErrorResponse(false,
            new ErrorResponse.ErrorBody(ex.getCode(), ex.getMessage(), null, ex.getDetail()));
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }
}
```

**Nguồn:** mẫu chuẩn Spring Boot `[ASSUMED — dựa trên kinh nghiệm phổ biến, chưa verify qua Context7 vì đây là pattern tự viết không thuộc thư viện cụ thể]`.

### Pattern 2: JWT với jjwt 0.13.x — API mới

**What:** Sinh và verify access token bằng API builder hiện đại của jjwt 0.13.x.
**When to use:** `JwtService` trong `common/security/`.

```java
// Source: https://github.com/jwtk/jjwt (Context7 /jwtk/jjwt, README.adoc, verified 2026-08-22)
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

public class JwtService {

    private final SecretKey key;
    private static final MacAlgorithm ALG = Jwts.SIG.HS256;

    public JwtService(String base64Secret) {
        // Secret nạp từ biến môi trường JWT_SECRET (D-01) — fail-fast nếu null/rỗng
        this.key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(base64Secret));
    }

    public String generateAccessToken(UUID userId, String plan) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("plan", plan)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600))) // AUTH-08: 1 giờ
            .signWith(key, ALG)
            .compact();
    }

    public Claims parseAndValidate(String token) {
        // Ném ExpiredJwtException / SignatureException / MalformedJwtException nếu sai
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
```

**QUAN TRỌNG — đừng dùng cú pháp cũ:** `Jwts.parserBuilder()` (không có `s`, builder cũ) và `.setSigningKey(...)` là API của **jjwt 0.11.x trở về trước**, đã bị loại bỏ. jjwt 0.13.0 dùng `Jwts.parser()` (không `Builder` suffix, trả thẳng `JwtParserBuilder`) và `.verifyWith(key)`. `[VERIFIED: Context7 /jwtk/jjwt README.adoc]`.

**Sinh secret HMAC:** dùng `Jwts.SIG.HS256.key().build()` để generate ngẫu nhiên lúc setup ban đầu (một lần, không phải mỗi request), hoặc decode base64 secret từ `.env`:
```java
// Source: Context7 /jwtk/jjwt, verified 2026-08-22
byte[] keyBytes = new byte[32]; // 256 bit cho HS256
new SecureRandom().nextBytes(keyBytes);
SecretKey key = Keys.hmacShaKeyFor(keyBytes);
```

### Pattern 3: Refresh token rotation + reuse detection (AUTH-03)

**What:** Mỗi lần `/auth/refresh`, revoke token cũ và cấp token mới trong cùng transaction; nếu token đã revoke bị gửi lại → revoke toàn bộ phiên.
**When to use:** `AuthService.refresh(String rawRefreshToken)`.

```java
@Transactional
public AuthResponse refresh(String rawRefreshToken) {
    String hash = sha256Hex(rawRefreshToken);

    // idx_rt_active đã tối ưu cho truy vấn này (WHERE revoked_at IS NULL)
    Optional<RefreshToken> activeOpt = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash);

    if (activeOpt.isEmpty()) {
        // Token không active — có thể do: chưa từng tồn tại, HOẶC đã bị revoke trước đó (dùng lại)
        Optional<RefreshToken> anyOpt = refreshTokenRepository.findByTokenHash(hash);
        if (anyOpt.isPresent()) {
            // REUSE DETECTION: token đã revoke bị gửi lại → khả năng bị đánh cắp
            UUID userId = anyOpt.get().getUserId();
            refreshTokenRepository.revokeAllActiveForUser(userId); // UPDATE ... SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL
        }
        throw new BusinessException("REFRESH_TOKEN_INVALID", 401, "Thẻ làm mới không hợp lệ.");
    }

    RefreshToken current = activeOpt.get();
    if (current.getExpiresAt().isBefore(Instant.now())) {
        throw new BusinessException("REFRESH_TOKEN_INVALID", 401, "Thẻ làm mới đã hết hạn.");
    }

    // Rotation: revoke cũ, cấp mới — cùng 1 transaction
    current.setRevokedAt(Instant.now());
    refreshTokenRepository.save(current);

    String newRawToken = generateSecureRandomToken(); // vd. 256-bit random, base64url
    RefreshToken newToken = RefreshToken.builder()
        .userId(current.getUserId())
        .tokenHash(sha256Hex(newRawToken))
        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
        .build();
    refreshTokenRepository.save(newToken);

    String accessToken = jwtService.generateAccessToken(current.getUserId(), user.getPlan());
    return new AuthResponse(accessToken, newRawToken, 3600);
}
```

**Điểm dễ vấp — race condition khi 2 request refresh cùng lúc dùng cùng 1 token:** vì `token_hash` có `UNIQUE` constraint (`uq_rt_hash`) và index `idx_rt_active` lọc `revoked_at IS NULL`, hai request đồng thời cùng đọc "active" trước khi request đầu commit UPDATE sẽ dẫn tới cả hai đều tưởng token còn active và đều cấp token mới — vi phạm rotation "dùng một lần". Cách phòng tránh: dùng `SELECT ... FOR UPDATE` (pessimistic lock) khi đọc `current` trong repository, hoặc dựa vào `@Transactional` mức `SERIALIZABLE`/`REPEATABLE READ` cho block này. `[ASSUMED]` — CONTEXT.md D-23 liệt kê "reuse detection" là ưu tiên test #1 nhưng không chỉ định cơ chế khoá cụ thể; planner cần quyết định `SELECT FOR UPDATE` hay optimistic lock (`@Version`) và viết test race-condition tương ứng.

**Đăng xuất & đổi mật khẩu (AUTH-04):**
```java
// Logout — chỉ revoke token hiện tại
refreshTokenRepository.revokeByTokenHash(sha256Hex(rawRefreshToken));

// logout_all_devices=true, hoặc sau đổi/reset mật khẩu — revoke TẤT CẢ
refreshTokenRepository.revokeAllActiveForUser(userId);
```

### Pattern 4: Idempotency qua AOP (`@Idempotent`, D-10)

**What:** Annotation đặt trên method controller; Aspect chặn trước khi method thật chạy, tra `idempotency_keys`.
**When to use:** POST tạo mới — Phase 1 chưa có endpoint nào thật sự cần (auth toàn POST-hành-động), nhưng hạ tầng phải dựng xong để Phase 2 (`POST /wallets`) dùng ngay.

```java
// common/idempotency/Idempotent.java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {}
```

```java
// common/idempotency/IdempotencyAspect.java
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyKeyRepository repo;
    private final ObjectMapper objectMapper;
    private final HttpServletRequest request; // request-scoped proxy

    @Around("@annotation(com.datn.financeapp.common.idempotency.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            return pjp.proceed(); // header tuỳ chọn — không có thì chạy bình thường
        }

        UUID userId = SecurityContextUtil.currentUserId();
        String endpoint = request.getMethod() + " " + request.getRequestURI();

        // INSERT ... ON CONFLICT DO NOTHING — chỉ 1 request "thắng" chèn được với status=processing
        int inserted = repo.tryInsertProcessing(key, userId, endpoint); // native query
        if (inserted == 0) {
            // Đã tồn tại — tra trạng thái hiện tại
            IdempotencyKeyEntity existing = repo.findByKeyUserEndpoint(key, userId, endpoint)
                .orElseThrow(); // hiếm khi rơi vào đây (race giữa 2 lệnh) — coi như lỗi tạm thời
            if ("completed".equals(existing.getStatus())) {
                return buildResponseFromCache(existing); // trả nguyên response_body đã lưu
            }
            // D-13: processing → 409 REQUEST_IN_PROGRESS ngay, không chờ
            throw new BusinessException("REQUEST_IN_PROGRESS", 409,
                "Yêu cầu trước đó đang được xử lý, vui lòng thử lại sau.");
        }

        try {
            Object result = pjp.proceed(); // chạy method thật
            repo.markCompleted(key, userId, endpoint, extractHttpStatus(result), extractBody(result, objectMapper));
            return result;
        } catch (Exception ex) {
            // KHÔNG lưu response lỗi vào cache — request lỗi được phép thử lại
            repo.deleteByKeyUserEndpoint(key, userId, endpoint);
            throw ex;
        }
    }
}
```

**Native query gợi ý cho `tryInsertProcessing` (D-12):**
```sql
-- Source: thiết kế theo bảng idempotency_keys trong V7, uq_idem_scope=(idempotency_key,user_id,endpoint)
INSERT INTO idempotency_keys (idempotency_key, user_id, endpoint, status)
VALUES (:key, :userId, :endpoint, 'processing')
ON CONFLICT (idempotency_key, user_id, endpoint) DO NOTHING;
-- Kiểm tra rowcount trả về: 1 = mới chèn (thắng), 0 = đã tồn tại
```

**Lưu ý D-14:** phải thêm dòng `REQUEST_IN_PROGRESS | 409 | ...` vào bảng mã lỗi dùng chung ở `api/00-QUY-UOC-CHUNG.md` mục 6 **trong cùng phase này** — đây là thay đổi tài liệu, không chỉ code.

### Pattern 5: Rate limiting — Bucket4j + Caffeine (D-16 đến D-20)

**What:** `OncePerRequestFilter` đặt sau `JwtAuthFilter`, tra/tạo bucket theo key (IP hoặc user_id) từ Caffeine cache, set header trên mọi response.

```java
// Source: Bucket4j 8.14 reference (bucket4j.com/8.14.0/toc.html, verified 2026-08-22)
// và Context7-style API pattern chuẩn của thư viện
Bucket bucket = Bucket.builder()
    .addLimit(limit -> limit.capacity(120).refillGreedy(120, Duration.ofMinutes(1)))
    .build();

ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
if (probe.isConsumed()) {
    response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
    // request tiếp tục
} else {
    response.setStatus(429);
    long resetEpochSeconds = Instant.now().plusNanos(probe.getNanosToWaitForRefill()).getEpochSecond();
    response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpochSeconds));
    // trả {success:false, error:{code:"RATE_LIMIT_EXCEEDED", ...}}
}
```

```java
// common/ratelimit/RateLimitFilter.java — khung tổng thể
@Component
@Order(...)  // đặt SAU JwtAuthFilter trong SecurityFilterChain (D-18)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> bucketCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5)) // tự evict bucket không dùng — D-17
        .maximumSize(100_000)
        .build();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        RateLimitRule rule = resolveRule(req.getRequestURI()); // bảng cấu hình nhóm→quota (D-16)
        String bucketKey = rule.keyedByIp()
            ? rule.group() + ":" + req.getRemoteAddr()
            : rule.group() + ":" + currentUserId(); // lấy từ SecurityContext — JWT filter đã set trước đó

        Bucket bucket = bucketCache.get(bucketKey, k -> newBucketFor(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        res.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens())); // D-19: LUÔN set
        res.setHeader("X-RateLimit-Reset", String.valueOf(
            Instant.now().plusNanos(probe.getNanosToWaitForRefill()).getEpochSecond()));

        if (!probe.isConsumed()) {
            res.setStatus(429);
            writeErrorBody(res, "RATE_LIMIT_EXCEEDED", "Bạn đã gọi quá nhiều lần, vui lòng thử lại sau.");
            return; // KHÔNG chain.doFilter — chặn tại đây
        }
        chain.doFilter(req, res);
    }
}
```

**Bảng cấu hình nhóm (D-16), gợi ý model:**
```java
public record RateLimitRule(String group, String pathPrefix, int limit, Duration window, boolean keyedByIp) {}

List<RateLimitRule> RULES = List.of(
    new RateLimitRule("auth", "/auth/", 5, Duration.ofMinutes(1), true),
    new RateLimitRule("ai",   "/ai/",   30, Duration.ofMinutes(1), false),
    new RateLimitRule("default", "/",  120, Duration.ofMinutes(1), false)
);
// Match theo prefix dài nhất trước (auth/ai ưu tiên hơn default)
```

**Thứ tự filter trong `SecurityConfig`:**
```java
// Source: pattern chuẩn Spring Security OncePerRequestFilter chaining
http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
http.addFilterAfter(rateLimitFilter, jwtAuthFilter.getClass()); // D-18: sau JWT filter
```
`[ASSUMED]` — `addFilterAfter` nhận `Class` filter đứng trước làm tham chiếu vị trí; cách chính xác hơn là dùng instance đã đăng ký hoặc tự định nghĩa thứ tự bằng `FilterRegistrationBean` với `@Order`. Cần verify khi implement thật vì Spring Security filter chain đôi khi wrap filter tuỳ biến khác với filter chuẩn.

### Pattern 6: Đăng ký — tạo ví "Tiền mặt" trong cùng transaction (AUTH-01)

```java
@Transactional
public AuthResponse register(RegisterRequest req) {
    if (userRepository.existsByEmail(req.email().toLowerCase())) {
        throw new BusinessException("EMAIL_ALREADY_EXISTS", 409, "Email đã có người dùng.");
    }
    User user = User.builder()
        .email(req.email().toLowerCase())          // ck_users_email buộc lowercase — V1
        .passwordHash(passwordEncoder.encode(req.password())) // BCrypt strength >= 12
        .fullName(req.fullName())
        .plan("free")
        .build();
    userRepository.save(user);

    // AUTH-01: ví Tiền mặt số dư 0 — KHÔNG dựng module wallet/, chỉ insert tối thiểu
    // uq_wallets_user_name là UNIQUE (user_id, lower(name)) WHERE NOT is_deleted — "Tiền mặt" luôn hợp lệ vì user mới
    Wallet cashWallet = Wallet.builder()
        .userId(user.getId())
        .name("Tiền mặt")
        .type("cash")
        .initialBalance(0L)
        .currentBalance(0L)
        .build();
    walletRepository.save(cashWallet); // dùng entity Wallet tối thiểu trong common/ hoặc auth/, KHÔNG package wallet/ đầy đủ

    String accessToken = jwtService.generateAccessToken(user.getId(), user.getPlan());
    String rawRefreshToken = issueRefreshToken(user.getId());
    return new AuthResponse(user, accessToken, rawRefreshToken, 3600);
}
```

**Lưu ý ràng buộc DB áp dụng:** `wallets.type` có `CHECK (type IN ('cash','bank','e_wallet','credit_card'))` — dùng đúng chuỗi `"cash"`. `ck_wallets_owner` buộc đúng 1 trong `user_id`/`group_id` — không set `group_id`.

### Anti-Patterns to Avoid
- **Dùng `Jwts.parserBuilder()`/`setSigningKey()` (API 0.11.x cũ):** không tồn tại/không nên dùng trong jjwt 0.13.x — dùng `Jwts.parser().verifyWith(key)`.
- **Load-modify-save cho `refresh_tokens.revoked_at` không khoá:** tạo race condition cho phép dùng lại token 1 lần trong khoảng thời gian ngắn — dùng `SELECT FOR UPDATE` hoặc constraint bổ sung.
- **Gắn `@Idempotent` lên `/auth/login`:** D-11 cấm rõ ràng — sẽ trả token cũ đã cache cho lần đăng nhập thứ hai, nguy hiểm hơn ghi trùng.
- **Đặt `RateLimitFilter` trước `JwtAuthFilter`:** D-18 — sẽ không lấy được `user_id` để tính quota AI/default, phải rơi về đếm theo IP cho mọi endpoint (sai yêu cầu).
- **Copy `db/migration/` vào `source/server/src/main/resources/db/migration/`:** D-09 cấm — Flyway phải trỏ thẳng `../../db/migration` qua `filesystem:` prefix, tránh 2 bản sao trôi dạt.
- **Ghi trigger DB để tự cập nhật `wallets.current_balance`:** vi phạm quy tắc bất biến #5/#2 backend CLAUDE.md — số dư ví luôn do Service cập nhật trong transaction.

## Cấu hình .env & Flyway (D-01, D-02, D-09)

### Nạp biến môi trường bắt buộc, fail-fast (D-01)

```yaml
# application.yml
spring:
  datasource:
    url: ${DB_URL}          # KHÔNG có :default — thiếu biến này Spring Boot fail lúc khởi động
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  flyway:
    locations: filesystem:../../db/migration   # D-09: trỏ gốc DATN, KHÔNG copy
    encoding: UTF-8                              # bắt buộc — file .sql có tiếng Việt có dấu (xem db/README.md)

jwt:
  secret: ${JWT_SECRET}     # base64-encoded, độ dài đủ cho HS256 (>= 256 bit / 32 byte)
```

**Về đường dẫn `filesystem:../../db/migration`:** cấu hình này đúng khi working directory lúc chạy là `source/server/` (chuẩn `mvn spring-boot:run`/IDE). Cần verify đường dẫn tương đối này hoạt động đúng cả khi chạy `mvn test` (Testcontainers) lẫn khi đóng gói JAR chạy từ thư mục khác — `[ASSUMED]` khuyến nghị planner thêm 1 task xác minh cụ thể bằng cách chạy thử cả 2 kịch bản (`mvn spring-boot:run` và `mvn test`), và cân nhắc dùng đường dẫn tuyệt đối qua biến môi trường (`DB_MIGRATION_PATH`) nếu đường dẫn tương đối gây lỗi khi CI/CD hoặc IDE có working directory khác nhau.

### `.env.example` (commit, D-02)
```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/finance_app
DB_USERNAME=postgres
DB_PASSWORD=changeme

# JWT — sinh bằng: openssl rand -base64 32
JWT_SECRET=

# Postgres container (dùng bởi docker-compose.yml)
POSTGRES_DB=finance_app
POSTGRES_USER=postgres
POSTGRES_PASSWORD=changeme
```

### `docker-compose.yml` tối thiểu (D-04)
```yaml
# Source: pattern chuẩn Docker Compose cho Postgres dev, không từ tài liệu riêng của dự án
services:
  postgres:
    image: postgres:16
    env_file: .env
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
volumes:
  pgdata:
```

**Lưu ý version Postgres image:** `db/README.md` dùng ví dụ `postgres:16` trong hướng dẫn Docker chạy migration thủ công — dùng cùng version để nhất quán giữa dev và ví dụ tài liệu, dù CLAUDE.md yêu cầu tối thiểu PostgreSQL 14+.

## Xác minh migration V6/V7 (D-06, D-07)

**Việc đầu tiên của Phase 1** (thay cho "viết V6/V7" đã lỗi thời trong ROADMAP.md — xem D-08): dựng project trỏ Flyway vào `db/migration/` gốc và chạy `V1→V7` trên DB rỗng, xác nhận:

1. Không có lỗi checksum/thứ tự — Flyway version table sạch từ V1 đến V7
2. `v_budget_progress` (V6) tồn tại và có điều kiện phạm vi (không cần test nghiệp vụ ở Phase 1, chỉ cần view compile được — Phase 4 mới test BUDGET-08)
3. `fn_category_tree` (V6) tồn tại, đúng chữ ký `(UUID) RETURNS TABLE(category_id UUID)`
4. `groups.invite_code_expires_at` tồn tại, `NOT NULL`
5. 4 bảng `refresh_tokens`, `idempotency_keys`, `password_reset_tokens`, `login_attempts` tồn tại đúng cột như đã trích ở trên

**Cách verify nhanh nhất:** dùng chính Testcontainers test đầu tiên của Phase 1 (D-23) — một test rỗng chỉ khởi động context với Flyway locations đã cấu hình sẽ tự chạy migration và fail nếu có lỗi SQL.

## Entity ↔ schema mapping — điểm cần đối chiếu chính xác

| Bảng (V1/V7) | Cột quan trọng | Kiểu Postgres | Map JPA gợi ý | Ghi chú |
|---|---|---|---|---|
| `users` | `id` | UUID | `UUID` + `@GeneratedValue` KHÔNG cần vì `DEFAULT gen_random_uuid()` ở DB — dùng `@Id` không auto-generate ở tầng Java, để DB tự sinh (hoặc set `@GeneratedValue(strategy=GenerationType.UUID)` tùy chiến lược) | `email` có `CHECK (email = lower(email))` — Service phải lowercase trước khi insert |
| | `password_hash` | VARCHAR(255) | `String` | BCrypt hash ~60 ký tự, đủ chỗ |
| | `plan` | VARCHAR(20) DEFAULT 'free' | `String` hoặc enum ánh xạ `@Enumerated(STRING)` | `CHECK (plan IN ('free','premium'))` |
| `refresh_tokens` | `token_hash` | VARCHAR(64) UNIQUE | `String` | SHA-256 hex = 64 ký tự đúng khít |
| | `revoked_at` | TIMESTAMPTZ nullable | `Instant` | NULL = còn hiệu lực — **không xoá bản ghi** |
| | `device_info` | VARCHAR(255) nullable | `String` | Optional — có thể lấy từ `User-Agent` header nếu muốn, không bắt buộc theo AUTH-08 |
| `idempotency_keys` | `(idempotency_key, user_id, endpoint)` | UNIQUE constraint `uq_idem_scope` | Composite hoặc 3 field riêng + `@Table(uniqueConstraints=...)` | `status` CHECK chỉ nhận `processing`/`completed` — enum Java nên khớp đúng 2 giá trị |
| | `response_body` | JSONB nullable | `String` (lưu JSON string) hoặc dùng `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6+) | Cần serialize response thật bằng Jackson trước khi lưu |
| `password_reset_tokens` | `token_hash` | VARCHAR(64) UNIQUE | `String` | Giống refresh_tokens — SHA-256 |
| | `used_at` | TIMESTAMPTZ nullable | `Instant` | NULL = chưa dùng |
| `login_attempts` | `email` | VARCHAR(255) NOT NULL (không FK tới users) | `String` | Ghi cả khi email không tồn tại — **không** dùng `user_id` |
| | `ip_address` | INET | `String` (Hibernate không có kiểu INET chuẩn) | `[ASSUMED]` — cần verify: Hibernate 6 có thể cần `@JdbcTypeCode` hoặc custom converter cho kiểu `INET` của Postgres; nếu ánh xạ sai kiểu, `ddl-auto=validate` sẽ fail lúc khởi động. Planner nên dành 1 task nhỏ thử nghiệm ánh xạ này sớm (map như `String` với native query, hoặc dùng `org.hibernate.type.SqlTypes` phù hợp) |
| `wallets` (V1, dùng tối thiểu cho AUTH-01) | `type` | VARCHAR(20) CHECK IN (...) | `String` | Dùng đúng `"cash"` |
| | `current_balance`, `initial_balance` | BIGINT | `Long` | Không bao giờ `Double` (CLAUDE.md quy tắc #3, #8) |

**Cảnh báo `ddl-auto=validate`:** nếu bất kỳ cột nào ở trên bị map sai kiểu (đặc biệt `INET`, `JSONB`, `UUID` default), Spring Boot sẽ **crash ngay lúc khởi động** với `SchemaManagementException`. Đây là điểm chặn task đầu tiên nghiêm trọng nhất theo cảnh báo của CONTEXT.md — planner nên xếp việc "tạo entity tối thiểu + chạy `validate` thành công" thành 1 task riêng, làm ngay sau khi verify migration, trước khi viết business logic.

## Đăng ký — Ghi chú CORE-02

Phase 1 không có endpoint trả danh sách phân trang (auth chỉ trả object đơn). Không cần implement CORE-02 đầy đủ ở phase này, nhưng khuyến nghị dựng sẵn class `PageRequestParams`/`PageResponse<T>` dùng chung trong `common/response/` để Phase 2 (`GET /wallets`) tái sử dụng ngay — tránh mỗi module tự định nghĩa lại cấu trúc `pagination`.

## Khoá đăng nhập & login_attempts (AUTH-07)

```java
@Transactional
public AuthResponse login(LoginRequest req, String ipAddress) {
    String email = req.email().toLowerCase();

    // Đếm số lần sai liên tiếp GẦN NHẤT — dùng idx_la_email (email, attempted_at DESC) WHERE NOT succeeded
    boolean locked = loginAttemptRepository.isLockedOut(email, Instant.now().minus(15, ChronoUnit.MINUTES));
    if (locked) {
        throw new BusinessException("ACCOUNT_LOCKED", 403, "Tài khoản tạm khoá do đăng nhập sai nhiều lần.");
    }

    Optional<User> userOpt = userRepository.findByEmail(email);
    boolean passwordOk = userOpt.isPresent()
        && passwordEncoder.matches(req.password(), userOpt.get().getPasswordHash());

    loginAttemptRepository.save(LoginAttempt.builder()
        .email(email).ipAddress(ipAddress).succeeded(passwordOk).build());

    if (!passwordOk) {
        // AUTH-02: sai email HAY sai mật khẩu đều cùng 1 lỗi — không phân biệt userOpt rỗng hay sai password
        throw new BusinessException("INVALID_CREDENTIALS", 401, "Email hoặc mật khẩu không đúng.");
    }

    User user = userOpt.get();
    user.setLastLoginAt(Instant.now());
    // ... sinh token như register
}
```

**Truy vấn "5 lần sai liên tiếp":** cần logic chính xác — "liên tiếp" nghĩa là 5 lần sai **gần nhất** không bị ngắt bởi 1 lần thành công. Gợi ý native query:
```sql
-- Source: thiết kế dựa trên login_attempts + idx_la_email, chưa có trong tài liệu — [ASSUMED]
SELECT COUNT(*) FROM (
    SELECT succeeded FROM login_attempts
    WHERE email = :email
    ORDER BY attempted_at DESC
    LIMIT 5
) recent
WHERE NOT succeeded
HAVING COUNT(*) FILTER (WHERE succeeded) = 0;  -- không có lần thành công nào chen giữa
```
`[ASSUMED]` — `api/01-XAC-THUC.md` chỉ nói "sau 5 lần sai liên tiếp", không định nghĩa chính xác thuật toán đếm khi có xen kẽ thành công/thất bại hoặc cách xử lý biên (đúng 5 lần sai rồi 1 lần đúng thì có reset không). Cách diễn giải chuẩn ngành (và hợp lý nhất) là: đếm 5 lần **gần nhất theo thời gian**, nếu tất cả đều sai → khoá; một lần đăng nhập đúng bất kỳ lúc nào sẽ "phá" chuỗi sai liên tiếp. Planner nên xác nhận diễn giải này khớp kỳ vọng hoặc đơn giản hoá thành "đếm số lần sai kể từ lần thành công gần nhất, trong cửa sổ 15 phút".

## PasswordResetNotifier (D-22)

```java
// auth/service/PasswordResetNotifier.java
public interface PasswordResetNotifier {
    void sendResetCode(String email, String rawResetCode);
}

// Implementation duy nhất ở Phase 1 — Profile dev/test đều dùng
@Component
@Slf4j
public class LogPasswordResetNotifier implements PasswordResetNotifier {
    @Override
    public void sendResetCode(String email, String rawResetCode) {
        log.info("Mã đặt lại mật khẩu cho {}: {} (hạn 15 phút)", email, rawResetCode);
    }
}
```

`forgot-password` luôn trả 200 bất kể email tồn tại hay không (AUTH-06, chống dò danh sách người dùng) — chỉ gọi `notifier.sendResetCode(...)` khi user thật sự tồn tại, response giống hệt nhau ở cả 2 nhánh.

## Điểm lệch tài liệu phát hiện được (CORE-11)

Trong quá trình đối chiếu `api/01-XAC-THUC.md` với `db/migration/V1__nen_tang.sql` và `V7__ha_tang_xac_thuc.sql` cho research này:

1. **`api/01-XAC-THUC.md` gọi bảng là `user`** (mục "Bảng CSDL liên quan: `users`" — thực ra ghi đúng số nhiều `users` ở đầu file, nhưng phần "Việc máy chủ phải làm khi đăng ký" bước 2 viết *"Tạo bản ghi trong `user`"* — số ít, không khớp tên bảng thật `users`). Đây là lỗi đánh máy nhỏ trong tài liệu, không phải sai lệch schema — planner/executor chỉ cần dùng đúng tên bảng thật `users`, không cần sửa tài liệu vì không gây hiểu lầm nghiêm trọng.
2. **`api/00-QUY-UOC-CHUNG.md` mục 6 (bảng mã lỗi dùng chung) chưa có `REQUEST_IN_PROGRESS`** — đã ghi nhận ở D-14, phải bổ sung trong phase này (task tài liệu, không phải chỉ code).
3. **Không phát hiện lệch nào khác** giữa đặc tả AUTH-01…08 và schema V1/V7 — 4 bảng hạ tầng đã khớp đúng những gì `api/01` mô tả (bcrypt, hash refresh token, hạn 15 phút reset, khoá 5 lần).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Sinh/verify chữ ký JWT | Tự implement HMAC-SHA256 signing | `jjwt` (`Jwts.builder()/.signWith()`, `Jwts.parser()/.verifyWith()`) | Thư viện đã xử lý edge case (clock skew, base64url padding, algorithm confusion attack) |
| Băm mật khẩu | Tự viết hash function | `PasswordEncoder` (`BCryptPasswordEncoder`, `spring-security-crypto`) | Bcrypt cost factor tunable, chống rainbow table, đã audit rộng rãi |
| Token bucket rate limit | Đếm request bằng counter tự viết + cron reset | Bucket4j (`Bucket.builder().addLimit(...)`) | Xử lý đúng refill liên tục (không phải reset theo khối thời gian cứng gây burst ở ranh giới phút) |
| Cache in-memory tự evict | `ConcurrentHashMap` tự viết TTL | Caffeine (`expireAfterAccess`) | Caffeine dùng thuật toán W-TinyLFU, đã tối ưu memory/CPU, tránh leak nếu tự viết sai |
| Random token entropy cao | `UUID.randomUUID()` cho refresh token | `SecureRandom` sinh 256-bit rồi base64url-encode | `UUID.randomUUID()` dùng `SecureRandom` nội bộ nhưng chỉ 122 bit entropy hữu dụng và có cấu trúc cố định (version/variant bits) — với token bảo mật cao như refresh token, sinh trực tiếp từ `SecureRandom` cho toàn quyền kiểm soát độ dài/entropy |

**Key insight:** Toàn bộ hạ tầng bảo mật (JWT, hash, rate limit, cache) của phase này đều có thư viện chuẩn ngành đã audit — không có lý do chính đáng để tự viết bất kỳ phần nào trong số đó, kể cả cho đồ án tốt nghiệp.

## Common Pitfalls

### Pitfall 1: Nhầm API jjwt cũ và mới
**What goes wrong:** Copy code mẫu từ Stack Overflow/blog cũ dùng `Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)` — compile lỗi hoặc method not found trên jjwt 0.13.0.
**Why it happens:** Phần lớn tutorial online (kể cả nhiều nội dung huấn luyện của Claude) vẫn dựa trên jjwt 0.11.x, đã thay API 2 lần kể từ đó (0.12 rồi 0.13).
**How to avoid:** Luôn dùng `Jwts.parser()` (không `Builder`) + `.verifyWith(key)`; `Jwts.builder()...signWith(key, alg)`. Xem `## Pattern 2` ở trên.
**Warning signs:** Lỗi biên dịch `cannot find symbol: method parserBuilder()` hoặc `setSigningKey(Key)`.

### Pitfall 2: Artifact Bucket4j sai tên
**What goes wrong:** Thêm `com.bucket4j:bucket4j-core` vào `pom.xml`, Maven vẫn resolve được (dừng ở 8.10.1, năm 2024) nhưng thiếu API/bugfix của bản 8.11+ mà tài liệu/blog mới hướng dẫn.
**Why it happens:** Đổi tên artifact (JDK-specific naming) diễn ra âm thầm từ bản 8.11, nhiều search kết quả cũ vẫn dùng tên cũ.
**How to avoid:** Dùng `com.bucket4j:bucket4j_jdk17-core:8.19.0` (đã verify Maven Central).
**Warning signs:** `pom.xml` build được nhưng version hiển thị trong `mvn dependency:tree` là `8.10.1` thay vì bản mới.

### Pitfall 3: Flyway thiếu module driver Postgres riêng
**What goes wrong:** Chỉ thêm `flyway-core`, khởi động app báo `FlywayException: Unsupported Database: PostgreSQL 16.x`.
**Why it happens:** Từ Flyway 10, hỗ trợ database cụ thể tách thành module riêng (`flyway-database-postgresql`) thay vì bundled trong `flyway-core`.
**How to avoid:** Luôn thêm cả `flyway-core` (Spring Boot starter tự kéo) VÀ `flyway-database-postgresql` tường minh trong `pom.xml`.
**Warning signs:** App fail ngay lúc khởi động ở bước Flyway validate/migrate, message nhắc "Unsupported Database".

### Pitfall 4: `ddl-auto=validate` fail vì entity không khớp cột thật
**What goes wrong:** Viết entity dựa trên trí nhớ/đoán thay vì đọc `V1__nen_tang.sql`/`V7__ha_tang_xac_thuc.sql` — sai kiểu (`INET`, `JSONB`), sai độ dài `VARCHAR`, thiếu `NOT NULL`, sai default.
**Why it happens:** `api/*.md` không mô tả kiểu cột CSDL chi tiết — dev dễ tự suy đoán.
**How to avoid:** Đối chiếu từng cột với bảng `## Entity ↔ schema mapping` ở trên trước khi viết `@Entity`, đặc biệt `INET`/`JSONB`.
**Warning signs:** `SchemaManagementException: Schema-validation: wrong column type` lúc khởi động.

### Pitfall 5: Rate limit filter đặt sai thứ tự → không lấy được `user_id`
**What goes wrong:** `RateLimitFilter` chạy trước `JwtAuthFilter`, `SecurityContext` chưa có Authentication → mọi request (kể cả AI/default) buộc phải đếm theo IP thay vì `user_id`.
**Why it happens:** Thứ tự filter trong Spring Security dễ set nhầm nếu không tường minh dùng `addFilterAfter`/`addFilterBefore` đúng tham chiếu.
**How to avoid:** D-18 — verify bằng test: gọi 1 endpoint thường (không phải `/auth/**`) với 2 user khác nhau cùng IP, xác nhận quota tính riêng theo từng user chứ không gộp theo IP.
**Warning signs:** Test rate limit thấy quota bị chia sẻ nhầm giữa 2 tài khoản khác nhau cùng máy.

### Pitfall 6: Idempotency aspect lưu response lỗi vào cache
**What goes wrong:** Nếu method thật ném exception (vd. lỗi tạm thời DB) nhưng Aspect vẫn `markCompleted` với status lỗi, request retry hợp lệ sau đó sẽ nhận lại y hệt lỗi cũ thay vì thử lại thành công.
**Why it happens:** Logic AOP không phân biệt "hoàn thành với lỗi nghiệp vụ hợp lệ" (nên cache) và "lỗi hạ tầng tạm thời" (không nên cache).
**How to avoid:** Theo D-12, chỉ `markCompleted` khi method trả về response hợp lệ (kể cả lỗi nghiệp vụ 4xx nếu đó là kết quả xác định — tuỳ diễn giải); khi có exception không mong đợi (5xx), xoá bản ghi `processing` để cho phép thử lại (xem `## Pattern 4`).
**Warning signs:** Client retry sau khi network timeout luôn nhận lại lỗi 500 cũ, không bao giờ thành công dù server đã hết lỗi.

### Pitfall 7: Race condition refresh token rotation
**What goes wrong:** Hai request `/auth/refresh` gửi đồng thời cùng 1 refresh token (vd. do client retry bug) — cả hai đều đọc được token "active" trước khi cái đầu commit `revoked_at`, dẫn tới cấp 2 token mới từ 1 token cũ, phá vỡ tính "dùng một lần".
**Why it happens:** Đọc-rồi-ghi (`findByTokenHashAndRevokedAtIsNull` → `setRevokedAt` → `save`) không có khoá, giữa đọc và ghi có transaction khác chen vào.
**How to avoid:** `SELECT ... FOR UPDATE` khi đọc `current` trong `refresh()`, hoặc thêm `@Version` optimistic lock và catch `OptimisticLockException` để trả `REFRESH_TOKEN_INVALID`.
**Warning signs:** Test tích hợp gửi 2 request refresh song song cùng token thấy cả 2 đều thành công (đúng ra chỉ 1 được thành công).

## Code Examples

Đã trình bày đầy đủ trong các Pattern 1-6 ở trên (response wrapper, JWT, refresh rotation, idempotency AOP, rate limit filter, đăng ký tạo ví). Tất cả code jjwt/Bucket4j đã verify qua Context7/official docs; code Spring Security/AOP là pattern chuẩn `[ASSUMED]` theo kinh nghiệm phổ biến, không có nguồn chính thức cụ thể cho từng dòng.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `jjwt` `Jwts.parserBuilder().setSigningKey()` | `Jwts.parser().verifyWith()` | jjwt 0.12.0 (đầu 2024), tiếp tục ổn định tới 0.13.0 (2025-08-20) | Code mẫu cũ trên mạng không compile được với version project đang dùng |
| `com.bucket4j:bucket4j-core` | `com.bucket4j:bucket4j_jdk17-core` | Từ bucket4j 8.11 | `pom.xml` copy từ tutorial cũ dẫn tới dùng bản 8.10.1 lỗi thời (thiếu tính năng/bugfix mới) |
| Flyway bundled DB support | Module driver riêng (`flyway-database-postgresql`) | Flyway 10.0 | Thiếu module này → app không khởi động được, lỗi "Unsupported Database" |
| Spring Boot 3.x line | Spring Boot 4.x đã bắt đầu release (4.0.x, 4.1.x) | 2025-2026 | Không ảnh hưởng phase này — dự án chốt 3.3.x/3.4.x, không tự nâng lên 4.x |

**Deprecated/outdated:**
- `Jwts.parserBuilder()` — thay bằng `Jwts.parser()` (đổi tên method, builder trả về giống nhau về chức năng nhưng API khác chữ ký).
- `bucket4j-core` (không suffix JDK) — dừng phát triển ở 8.10.1, thay bằng `bucket4j_jdkXX-core`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Tên artifact chính xác cho tích hợp Spring Boot 3 của spring-dotenv là `springboot3-dotenv` (không phải `spring-dotenv` trần) | Standard Stack — Installation | Nếu sai tên artifact, `pom.xml` không resolve được — cần verify trên GitHub `paulschwarz/spring-dotenv` README trước khi code |
| A2 | Cơ chế khoá cụ thể cho race condition refresh token rotation (`SELECT FOR UPDATE` vs `@Version`) chưa được CONTEXT.md chỉ định | Pattern 3 — Refresh token rotation | Nếu không xử lý, test race condition (ưu tiên #1 theo D-23) có thể fail không xác định (flaky) |
| A3 | Đường dẫn tương đối `filesystem:../../db/migration` hoạt động đúng cho cả `mvn spring-boot:run`, `mvn test` (Testcontainers), và JAR đóng gói | Cấu hình .env & Flyway | Nếu working directory khác nhau giữa các kịch bản chạy, Flyway sẽ không tìm thấy migration — cần task verify riêng |
| A4 | Thuật toán chính xác đếm "5 lần sai liên tiếp" (window nào, có bị "phá" bởi 1 lần đúng chen giữa không) | Khoá đăng nhập & login_attempts | `api/01-XAC-THUC.md` không định nghĩa chi tiết — cách hiểu sai có thể khoá tài khoản sớm/muộn hơn ý định thật |
| A5 | Ánh xạ kiểu cột `INET` (Postgres) sang JPA/Hibernate — dùng `String` + native query hay cần `@JdbcTypeCode` riêng | Entity ↔ schema mapping | `ddl-auto=validate` có thể fail lúc khởi động nếu Hibernate không tự nhận diện kiểu này đúng cách |
| A6 | Response wrapper/`@RestControllerAdvice` pattern (Pattern 1) và `RateLimitFilter`/`IdempotencyAspect` code mẫu (Pattern 4, 5) là pattern tự thiết kế theo kinh nghiệm phổ biến, chưa verify qua Context7/official docs cụ thể của Spring | Pattern 1, 4, 5 | Code mẫu có thể cần điều chỉnh chi tiết implementation khi thực thi thật (tên method, cách lấy `HttpServletRequest` request-scoped trong AOP) |

## Open Questions

1. **Race condition khi 2 request refresh đồng thời gửi cùng 1 token (A2)**
   - What we know: CONTEXT.md D-23 xác nhận đây là ưu tiên test #1, nhưng không chỉ định cơ chế khoá.
   - What's unclear: `SELECT FOR UPDATE` hay optimistic lock `@Version` — cả hai đều khả thi, khác nhau về hành vi khi conflict (block vs exception).
   - Recommendation: Planner chọn `SELECT FOR UPDATE` (đơn giản hơn để reasoning đúng, phù hợp quy mô đồ án 1 instance) và viết test tích hợp gửi 2 coroutine/thread refresh song song cùng token, xác nhận đúng 1 thành công.

2. **Thuật toán chính xác "5 lần sai liên tiếp" (A4)**
   - What we know: `api/01-XAC-THUC.md` ghi "Sau 5 lần sai liên tiếp, khoá đăng nhập 15 phút".
   - What's unclear: Có tính cả window thời gian không giới hạn, hay chỉ trong 1 khung thời gian nhất định? Một lần đăng nhập đúng có "reset" bộ đếm không?
   - Recommendation: Diễn giải chuẩn: đếm 5 bản ghi gần nhất (không giới hạn thời gian, `ORDER BY attempted_at DESC LIMIT 5`), nếu toàn bộ đều `succeeded=false` thì khoá 15 phút kể từ lần sai gần nhất. Bất kỳ lần đăng nhập đúng nào cũng phá chuỗi (vì nó nằm trong 5 bản ghi gần nhất và không phải `succeeded=false`).

3. **Đường dẫn Flyway tương đối có ổn định qua mọi kịch bản chạy không (A3)**
   - What we know: D-09 yêu cầu trỏ `../../db/migration`, không copy.
   - What's unclear: Hành vi khi `mvn test` chạy từ Maven reactor có working directory khác `mvn spring-boot:run` hay không trên máy Windows của dev.
   - Recommendation: Task đầu tiên của Phase 1 (verify migration, D-07) nên tự động bao gồm cả việc chạy `mvn test` (không chỉ `spring-boot:run`) để phát hiện sớm nếu đường dẫn tương đối gãy.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 17 | Toàn bộ backend | `[ASSUMED]` chưa probe trực tiếp máy dev trong phiên này | — | Không có fallback — bắt buộc cài nếu thiếu |
| Maven | Build | `[ASSUMED]` chưa probe | — | — |
| Docker / Docker Compose | D-04 Postgres dev, Testcontainers | `[ASSUMED]` chưa probe | — | Nếu Docker không sẵn có trên máy dev, Testcontainers (D-23) không chạy được — đây là dependency chặn cứng, không có fallback hợp lý cho mục tiêu "test luồng Postgres thật" |
| PostgreSQL (qua Docker) | Toàn bộ | Chạy qua container, không cần cài native | 16 (theo ví dụ `db/README.md`) | — |

**Ghi chú:** Phiên research này chạy trong môi trường sandbox không có quyền truy cập máy dev thật của người dùng (thông tin môi trường trong system prompt không xác nhận Docker/Java/Maven đã cài). **Planner nên đưa việc probe môi trường (`java -version`, `mvn -version`, `docker info`) thành bước đầu tiên của Task 1 (cùng lúc verify migration)** — nếu thiếu Docker, toàn bộ D-04/D-23 (Testcontainers) bị chặn và cần giải pháp thay thế (Postgres cài native cho dev, chỉ dùng Testcontainers khi CI có Docker).

**Missing dependencies with no fallback:**
- Docker (nếu thực sự thiếu trên máy dev) — chặn cả D-04 (docker-compose Postgres) và D-23 (Testcontainers). Không có fallback hợp lý vì CONTEXT.md đã loại các phương án khác.

## Security Domain

> `security_enforcement` không có trong `.planning/config.json` đã đọc được ở mục workflow (giá trị mặc định bật) — bao gồm mục này.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT (jjwt 0.13.x) + BCrypt (`spring-security-crypto`, cost ≥ 12 theo `api/01` "Ghi chú triển khai") |
| V3 Session Management | yes | Refresh token rotation + reuse detection (bảng `refresh_tokens`), access token stateless hạn 1h |
| V4 Access Control | partial | Phase 1 chưa có tài nguyên đa người dùng thật sự (chỉ `/auth/me` tự nhiên giới hạn theo JWT subject) — CORE-05 áp dụng đầy đủ từ Phase 2 |
| V5 Input Validation | yes | `spring-boot-starter-validation` (Jakarta Bean Validation) trên mọi request DTO |
| V6 Cryptography | yes | BCrypt cho password, SHA-256 cho refresh/reset token hash (không tự viết — `MessageDigest.getInstance("SHA-256")` là chuẩn JDK, không cần thư viện ngoài) |
| V7 Error Handling & Logging | yes | `login_attempts` ghi IP + kết quả mọi lần đăng nhập (AUTH-07); `GlobalExceptionHandler` không rò rỉ stack trace ra response |

### Known Threat Patterns for stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Dò email hợp lệ qua thông báo lỗi khác nhau | Information Disclosure | `INVALID_CREDENTIALS` dùng chung cho sai email/sai password (AUTH-02); `forgot-password` luôn 200 (AUTH-06) — đã có trong đặc tả, chỉ cần implement đúng |
| Brute-force mật khẩu | Spoofing | Khoá 15 phút sau 5 lần sai (AUTH-07) + rate limit riêng 5/phút/IP cho `/auth/**` (CORE-04) — 2 lớp độc lập theo D-21 |
| Refresh token bị đánh cắp (XSS/log leak) tái sử dụng | Tampering/Elevation | Reuse detection: token đã revoke dùng lại → revoke toàn bộ phiên (AUTH-03) |
| JWT secret yếu / hardcode | Tampering | D-01: secret bắt buộc qua env var, fail-fast nếu thiếu; khuyến nghị sinh bằng `openssl rand -base64 32` (≥256 bit cho HS256) |
| Nhét dữ liệu nhạy cảm vào JWT payload (ai cũng đọc được) | Information Disclosure | AUTH-08: access token chỉ chứa `id`/`plan`/`exp` — đã ghi rõ trong `api/01`, cần tuân thủ nghiêm khi code `JwtService` |
| Timing attack khi so sánh token hash | Information Disclosure | So sánh `token_hash` nên qua truy vấn DB (index lookup, không phải so sánh chuỗi trong Java) — rủi ro thấp vì so sánh trong SQL WHERE, không phải constant-time compare thủ công; đủ an toàn cho quy mô đồ án |

## Sources

### Primary (HIGH confidence)
- Context7 `/jwtk/jjwt` — README.adoc, JwtParserBuilder, keys.md, quick-start.md (fetch 2026-08-22) — cú pháp `Jwts.builder()`, `Jwts.parser().verifyWith()`, `Keys.hmacShaKeyFor()`
- Maven Central `maven-metadata.xml` (curl trực tiếp 2026-08-22): `io.jsonwebtoken:jjwt-api` (0.13.0), `com.bucket4j:bucket4j_jdk17-core` (8.19.0), `org.flywaydb:flyway-database-postgresql` (13.3.0), `org.testcontainers:testcontainers-bom` (2.0.5), `org.mapstruct:mapstruct` (1.6.3), `com.github.ben-manes.caffeine:caffeine` (3.2.4), `me.paulschwarz:spring-dotenv` (4.0.0), `org.springframework.boot:spring-boot-starter-parent` (3.4.13 confirmed available)
- `db/migration/V1__nen_tang.sql`, `V6__va_loi_bao_mat.sql`, `V7__ha_tang_xac_thuc.sql` (đọc trực tiếp file thật) — schema đầy đủ `users`, `wallets`, `refresh_tokens`, `idempotency_keys`, `password_reset_tokens`, `login_attempts`
- `api/00-QUY-UOC-CHUNG.md`, `api/01-XAC-THUC.md` (đọc trực tiếp) — hợp đồng API, mã lỗi, header, quy tắc bảo mật

### Secondary (MEDIUM confidence)
- Bucket4j 8.14.0 Reference (`bucket4j.com/8.14.0/toc.html`, WebFetch 2026-08-22) — `Bucket.builder().addLimit(...)`, `ConsumptionProbe.tryConsumeAndReturnRemaining()`
- WebSearch: đổi tên artifact Bucket4j từ 8.11 (`bucket4j-core` → `bucket4j_jdkXX-core`) — cross-verify với danh sách artifact thật trên Maven Central (`bucket4j_jdk17-postgresql`, `bucket4j_jdk11-core`, v.v. đều tồn tại, xác nhận pattern đặt tên)
- Testcontainers `@ServiceConnection` Spring Boot 3.1+ (WebSearch, nhiều nguồn đồng thuận: Baeldung-style, Medium, JetBrains blog)

### Tertiary (LOW confidence)
- Tên artifact chính xác `springboot3-dotenv` cho spring-dotenv (chưa fetch được README chi tiết — search trả về tiêu đề nhắc "springboot3-dotenv" nhưng chưa xem trực tiếp bảng dependency trên GitHub) — xem A1 trong Assumptions Log
- Code mẫu `GlobalExceptionHandler`, `IdempotencyAspect`, `RateLimitFilter` (Pattern 1, 4, 5) — pattern tự thiết kế dựa trên kinh nghiệm Spring Boot phổ biến, không trích từ 1 nguồn cụ thể nào

## Metadata

**Confidence breakdown:**
- Standard stack (version cụ thể): HIGH — toàn bộ version đã verify trực tiếp qua Maven Central `maven-metadata.xml` trong phiên này, không dựa vào training data
- Kiến trúc/pattern JWT: HIGH cho phần jjwt (Context7 xác nhận), MEDIUM cho phần Spring Security filter chain/AOP (pattern phổ biến nhưng chưa verify qua tài liệu chính thức Spring)
- Idempotency/Rate limit business logic: HIGH cho phần tuân thủ D-10 đến D-21 (trực tiếp từ CONTEXT.md), MEDIUM cho chi tiết implementation Bucket4j API (verify qua official docs) và LOW cho race-condition handling cụ thể (chưa có nguồn xác nhận, đánh dấu `[ASSUMED]`)
- Entity/schema mapping: HIGH cho cấu trúc bảng (đọc trực tiếp SQL thật), LOW cho ánh xạ kiểu `INET`/`JSONB` sang Hibernate (chưa test thật, đánh dấu A5)

**Research date:** 2026-08-22
**Valid until:** ~30 ngày cho phần business logic/CONTEXT.md decisions (ổn định); ~14 ngày cho version thư viện cụ thể (ecosystem Java di chuyển tương đối chậm nhưng jjwt/bucket4j đã cho thấy đổi API/tên gần đây — nên re-verify version trước khi thực thi nếu quá 2 tuần)
