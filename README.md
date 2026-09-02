# Backend — Quản lý tài chính cá nhân có AI

Backend REST API (Spring Boot 3.4.x, Java 17) cho ứng dụng Flutter Quản lý tài chính cá nhân có AI. Xem `.planning/PROJECT.md` để biết bối cảnh đầy đủ.

## Yêu cầu môi trường

- Java 17
- Maven 3.9+
- Docker Desktop (dùng cho Postgres dev qua Docker Compose và cho Testcontainers khi chạy `mvn test`)

## Khởi động lần đầu

1. Clone repo, đứng ở thư mục `source/server/` (thư mục chứa file này).
2. Copy file mẫu biến môi trường và điền `JWT_SECRET`:

   ```bash
   cp .env.example .env
   ```

   Sinh giá trị `JWT_SECRET` (base64, tối thiểu 256 bit cho HS256):

   ```bash
   openssl rand -base64 32
   ```

   Windows PowerShell (nếu không có `openssl`):

   ```powershell
   [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
   ```

   Dán giá trị sinh được vào biến `JWT_SECRET` trong `.env`. File `.env` đã bị `.gitignore` chặn — không commit.

3. Khởi động PostgreSQL dev bằng Docker Compose:

   ```bash
   docker compose up -d
   ```

4. Chạy ứng dụng:

   ```bash
   mvn spring-boot:run
   ```

   Hoặc chỉ định rõ profile `dev`:

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   > **Dùng PowerShell trên Windows thì phải bọc dấu nháy:**
   >
   > ```powershell
   > mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
   > ```
   >
   > Không bọc thì PowerShell cắt tham số tại dấu chấm đầu tiên, thành hai mảnh
   > `-Dspring-boot` và `.run.profiles=dev`; Maven hiểu mảnh thứ hai là một
   > lifecycle phase và báo `Unknown lifecycle phase ".run.profiles=dev"`.
   > Quy tắc này áp dụng cho **mọi** tham số `-D...` có dấu chấm.

   Flyway sẽ tự động chạy toàn bộ migration ở `../../db/migration/` (V1 đến V7) lên database `finance_app` lúc khởi động.

## Chạy test

```bash
mvn test
```

Test tích hợp dùng **Testcontainers** (tự khởi động container PostgreSQL riêng biệt cho test, không dùng H2) — **cần Docker Desktop đang chạy sẵn** trên máy trước khi chạy `mvn test`.

## Cấu hình môi trường (biến bắt buộc)

Toàn bộ biến trong `.env.example` là **bắt buộc, không có giá trị mặc định** — ứng dụng sẽ fail-fast lúc khởi động nếu thiếu bất kỳ biến nào (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`). Đây là quyết định chủ động (xem D-01 trong `.planning/phases/01-nen-tang-xac-thuc/01-CONTEXT.md`) để tránh dùng nhầm giá trị mặc định yếu ở môi trường thật.

## Cấu trúc dự án

Package-by-feature — xem chi tiết ở `CLAUDE.md` tại thư mục này.

```
src/main/java/com/datn/financeapp/
├── common/     # response wrapper, exception handler, security, idempotency, rate limit
├── auth/       # đăng ký/đăng nhập/refresh/quên-đổi mật khẩu
└── FinanceAppApplication.java
```

Migration Flyway dùng chung với toàn dự án tại `../../db/migration/` (thư mục gốc DATN) — **không copy** vào đây.

## Trạng thái Phase 1 (Nền tảng & Xác thực)

**Hoàn thành.** 9/9 endpoint auth theo `api/01-XAC-THUC.md` hoạt động qua HTTP thật:

- `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` — vòng đời phiên đăng nhập, JWT access token 1h + refresh token rotation/reuse detection
- `GET /auth/me`, `PATCH /auth/me` — xem/sửa hồ sơ kèm thống kê ví/giao dịch/nhóm
- `POST /auth/change-password`, `/auth/forgot-password`, `/auth/reset-password` — đổi/quên/đặt lại mật khẩu

Hạ tầng dùng chung cho mọi phase sau đã sẵn sàng, không cần dựng lại:

- **Idempotency** (`common/idempotency/`): annotation `@Idempotent` + AOP, bảng `idempotency_keys`, dọn bản ghi quá 24h bằng job `@Scheduled`
- **Rate limit** (`common/ratelimit/`): Bucket4j + Caffeine 3 tầng (`auth` 5/phút/IP, `ai` 30/phút/user, còn lại 120/phút/user), header `X-RateLimit-*` trên mọi response
- **Response wrapper** (`common/response/`, `common/exception/`): khung `{success, data}`/`{success, error}` thống nhất, `GlobalExceptionHandler` gom mọi lỗi

Chạy toàn bộ test suite (cần Docker Desktop đang chạy, dùng Testcontainers):

```bash
mvn test
```

32 test hiện tại (unit + integration Testcontainers + 1 test end-to-end HTTP thật xác nhận Auth + Idempotency + Rate limit phối hợp đúng cùng nhau) đều xanh khi chạy liên tục 1 lần, không filter.

Xem `.planning/ROADMAP.md` Phase 2 cho bước tiếp theo (Ví & Danh mục).
