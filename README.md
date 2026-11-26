# Finance App — Backend Server

Backend REST API (Java 17, Spring Boot 3.4) cho hệ thống **Quản lý Tài chính Cá nhân & Chi tiêu Nhóm**, xây dựng theo kiến trúc **package-by-feature** với 13 module nghiệp vụ, bảo mật Stateless JWT + Google OAuth2, phân quyền RBAC, cổng thanh toán PayOS, tối ưu truy vấn/trigger PostgreSQL qua Flyway và kiểm thử tích hợp bằng Testcontainers.

---

## Mục lục

- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Kiến trúc & Module chính](#kiến-trúc--module-chính)
- [Yêu cầu môi trường](#yêu-cầu-môi-trường)
- [Hướng dẫn cài đặt & chạy từ đầu](#hướng-dẫn-cài-đặt--chạy-từ-đầu)
- [Tài liệu API, UI & Công cụ test](#tài-liệu-api-ui--công-cụ-test)
- [Nạp dữ liệu mẫu (Seed Data)](#nạp-dữ-liệu-mẫu-seed-data)
- [Kiểm thử & Đo hiệu năng](#kiểm-thử--đo-hiệu-năng)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)

---

## Công nghệ sử dụng

- **Core:** Java 17, Spring Boot 3.4.13, Spring Web, Spring Data JPA (Hibernate), Spring Validation, Spring AOP
- **Bảo mật:** Spring Security (Stateless), JWT (`jjwt` 0.13.0 - HS256), Google OAuth2 (`google-api-client`), Redis user blacklist, RBAC (Role & Permission)
- **Database & Migration:** PostgreSQL 17, Flyway (`classpath:db/migration_v2`)
- **Cache & Rate Limit:** Redis 7, Caffeine, Bucket4j (giới hạn tần suất 3 tầng)
- **Tích hợp bên thứ 3:** PayOS (`payos-java` — thanh toán & webhook nâng cấp Premium), Cloudinary (lưu trữ ảnh), Gmail SMTP (gửi email OTP/thông báo)
- **Tài liệu & Hạ tầng:** SpringDoc OpenAPI / Swagger UI, Docker Compose, Bruno API Collection
- **Kiểm thử & Hiệu năng:** JUnit 5, Mockito, Spring Security Test, Testcontainers (PostgreSQL), k6

---

## Kiến trúc & Module chính

### Hạ tầng dùng chung (`common/`)

- **`security/`**: Xác thực Stateless qua Bearer JWT, giải mã Google `id_token`, kiểm tra blacklist user trên Redis và phân quyền method-level (`@PreAuthorize`).
- **`ratelimit/`**: Rate limit 3 tầng qua Bucket4j + Caffeine, đính kèm header `X-RateLimit-*`:
  - `/v1/auth/**`: 5 request/phút theo IP
  - `/v1/ai/**`: 30 request/phút theo `user_id`
  - Các endpoint còn lại: 120 request/phút theo `user_id`
- **`idempotency/`**: Annotation `@Idempotent` + AOP lưu key tại bảng `idempotency_keys`, tự động dọn dẹp theo lịch `@Scheduled`.
- **`logging/`**: Request tracing log, đo thời gian xử lý service/SQL (`ServicePerformanceAspect`, `SqlStatementInspector`), che thông tin nhạy cảm (`LogMasker`) và luồng stream log cho Admin.
- **`response/` & `exception/`**: Chuẩn hóa response `{success, data}` / `{success, error}` qua `ApiResponse` và `GlobalExceptionHandler`.
- **`mail/` & `upload/`**: Gửi email OTP/thông báo bất đồng bộ qua SMTP và upload file lên Cloudinary.

### 13 Module nghiệp vụ (`module/`)

| Nhóm | Module | Chức năng chính |
| :--- | :--- | :--- |
| **Xác thực & Tài khoản** | `auth`, `user` | Đăng ký, đăng nhập (Email/Google), xác thực email OTP, refresh token, quên/đổi mật khẩu, quản lý user/role/permission, nâng cấp gói Premium |
| **Ví & Danh mục** | `wallet`, `category`, `icon` | Quản lý ví tiền (trigger DB tự tạo ví mặc định khi tạo user), lịch sử đối soát số dư, cây danh mục thu/chi & icon |
| **Giao dịch** | `transaction` | Giao dịch cá nhân (trigger DB tự đồng bộ số dư ví) và giao dịch định kỳ kèm scheduler |
| **Chi tiêu nhóm** | `group` | Nhóm chi tiêu, quản lý thành viên, quỹ nhóm, chia tiền/giao dịch nhóm, cân bằng công nợ thành viên và báo cáo nhóm |
| **Kế hoạch tài chính** | `budget`, `debt`, `goal` | Ngân sách & cảnh báo vượt mức, quản lý khoản vay/nợ & trả nợ, mục tiêu tiết kiệm & đóng góp |
| **Thanh toán & Báo cáo** | `pay`, `report`, `notification` | Tạo link thanh toán PayOS & xử lý webhook nâng cấp Premium, báo cáo thống kê + xuất CSV (`ExportJob`), thông báo theo sự kiện |

---

## Yêu cầu môi trường

- **Java JDK 17**
- **Maven 3.9+** (chạy bằng lệnh `mvn` trên hệ thống)
- **Docker & Docker Compose** (chạy PostgreSQL 17, Redis 7 và cần thiết khi chạy Integration Test với Testcontainers)
- *(Tùy chọn)* **Bruno** (để mở bộ collection test API trong `bruno/`), **k6** (để chạy load test)

---

## Hướng dẫn cài đặt & chạy từ đầu

### Bước 1: Clone mã nguồn

```bash
git clone <url-repository>
cd my_server_finance
```

### Bước 2: Khởi động PostgreSQL & Redis bằng Docker Compose

```bash
docker compose up -d
```

Mặc định Docker Compose khởi chạy:
- **PostgreSQL 17** (`postgres_dev`): cổng `5432`, database `finance`, user `postgres`, password `123456`
- **Redis 7 Alpine** (`redis_dev`): cổng `6379`

*(Có thể thay đổi tên DB, port, user/password qua biến môi trường trong `.env`.)*

### Bước 3: Tạo và cấu hình file `.env`

```bash
cp .env.example .env
```

Lớp khởi chạy (`App.java`) sẽ tự động đọc file `.env` ở thư mục gốc khi start ứng dụng.

1. **Sinh `JWT_SECRET`** (chuỗi Base64 tối thiểu 256-bit cho thuật toán `HS256`):
   ```bash
   openssl rand -base64 32
   ```
   *(Trên Windows PowerShell nếu không có `openssl`:)*
   ```powershell
   [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
   ```

2. **Chọn Spring Profile** trong `.env` (chú ý dùng dấu `=` đúng chuẩn file `.env`, ví dụ `PROFILES_ACTIVE=dev`):
   - **Chạy local nhanh (`PROFILES_ACTIVE=dev`)**: Đọc cấu hình từ `src/main/resources/application-dev.yaml` (cổng `8080`, context-path `/v1`, PostgreSQL `localhost:5432/finance`, Flyway `classpath:db/migration_v2`).
   - **Chạy cấu hình tùy biến / triển khai (`PROFILES_ACTIVE=prod`)**: Đọc toàn bộ tham số từ biến môi trường trong `.env` (`PORT`, `API_PATH`, `DB_*`, `REDIS_*`, `MAIL_*`, `FLYWAY_LOCATIONS`, `JWT_SECRET`, `CLOUDINARY_*`, `PAYOS_*`, `PREMIUM_PRICE_*`, `PAYMENT_TOPUP_*`, `GOOGLE_WEB_CLIENT_ID`).

### Bước 4: Khởi chạy server

- **Linux / macOS:**
  ```bash
  mvn spring-boot:run
  ```
- **Windows PowerShell** *(bọc dấu nháy quanh `-D...` để PowerShell không tách chuỗi tại dấu chấm)*:
  ```powershell
  mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
  ```
- Hoặc dùng script khởi chạy kèm tối ưu JVM/GC log trên Windows:
  ```powershell
  .\run.ps1
  ```

Khi ứng dụng khởi động lần đầu, **Flyway** tự động chạy các script migration trong `src/main/resources/db/migration_v2/` (`V1` đến `V19`) để khởi tạo bảng, trigger, view, danh mục/icon mẫu và tài khoản quản trị mặc định:
- **Email / tài khoản:** `admin`
- **Mật khẩu:** `admin` *(đã gán sẵn `ROLE_ADMIN` và `ROLE_USER` — hãy đổi mật khẩu trước khi đưa lên môi trường thật)*

---

## Tài liệu API, UI & Công cụ test

Khi server chạy với profile `dev` (`http://localhost:8080/v1`):

- **Swagger UI:** [http://localhost:8080/v1/swagger-ui/index.html](http://localhost:8080/v1/swagger-ui/index.html)
- **OpenAPI JSON:** [http://localhost:8080/v1/v3/api-docs](http://localhost:8080/v1/v3/api-docs) (bản snapshot có sẵn tại `api-docs.json`)
- **Web UI tích hợp (Static UI):** [http://localhost:8080/v1/](http://localhost:8080/v1/)
- **Bruno API Collection:** Mở thư mục `bruno/` trong ứng dụng Bruno và chọn environment **`Local`** (`baseUrl: http://localhost:8080/v1`) để test 14 nhóm endpoint (`01-Auth` đến `14-Admin-Logs`).
- **Tài liệu thiết kế & API:** Tham khảo thêm trong thư mục `docs/` (`docs/api/`, `docs/role_per.md`, `docs/work_flow/`).

---

## Nạp dữ liệu mẫu (Seed Data)

Thư mục `src/main/resources/db/seed/` cung cấp bộ SQL tạo dữ liệu mô phỏng quy mô lớn (100.000 người dùng, ví, giao dịch cá nhân/nhóm, gói định kỳ) tương thích với schema `migration_v2`.

Xem hướng dẫn chi tiết và thứ tự chạy (`reset_db.sql` và `01` → `06`) tại [`src/main/resources/db/seed/README.md`](src/main/resources/db/seed/README.md).

---

## Kiểm thử & Đo hiệu năng

### Chạy Unit Test & Integration Test

> Integration Test sử dụng **Testcontainers (PostgreSQL)** nên cần **Docker đang chạy** trước khi thực thi. Unit Test chạy độc lập, không cần Docker.

```bash
# Chỉ chạy Unit Test (nhanh, không cần Docker)
mvn test -Dtest="com/datn/financeapp/unitTest/**"

# Chỉ chạy Integration Test (cần Docker)
mvn test -Dtest="com/datn/financeapp/integrationTest/**"

# Chạy toàn bộ Test Suite (Unit + Integration)
mvn test
```

Trên Windows PowerShell, có thể dùng script `test.ps1` (hỗ trợ menu tương tác và tự nhận diện Docker trên Windows/WSL):

```powershell
.\test.ps1               # Menu chọn chế độ (mặc định: Unit Test)
.\test.ps1 -Unit         # Chỉ chạy Unit Test
.\test.ps1 -Integration  # Chỉ chạy Integration Test
.\test.ps1 -All          # Chạy toàn bộ Unit + Integration Test
.\test.ps1 -Module wallet
```

### Đo hiệu năng & Load Test

- **Kiểm tra N+1 query, thời gian xử lý & scale bộ nhớ (Windows PowerShell):**
  ```powershell
  .\performan-test.ps1
  ```
- **Load test hành trình người dùng với k6:**
  ```bash
  k6 run scripts/user-journey-load-test.js
  ```
  *(Có thể tùy chỉnh qua biến `-e RPS=1500 -e DURATION=1m`; kịch bản mặc định nhắm vào dữ liệu seed quy mô lớn.)*

---

## Cấu trúc thư mục

```text
my_server_finance/
├── bruno/                          # Bộ API collection & environment cho Bruno
├── docs/                           # Tài liệu phân tích, luồng nghiệp vụ & mô tả API
├── scripts/                        # Kịch bản k6 load test
├── src/
│   ├── main/
│   │   ├── java/com/datn/financeapp/
│   │   │   ├── common/             # Security, Rate Limit, Idempotency, Logging, Mail, Upload, Exception
│   │   │   ├── module/             # 13 module nghiệp vụ (auth, user, wallet, transaction, group, pay, report, ...)
│   │   │   └── App.java            # Điểm khởi chạy ứng dụng & tự nạp .env
│   │   └── resources/
│   │       ├── db/migration_v2/    # Flyway SQL migrations (V1 -> V19)
│   │       ├── db/seed/            # Dữ liệu mẫu quy mô lớn
│   │       ├── static/             # Giao diện Web tĩnh (admin/user)
│   │       └── application*.yaml   # Cấu hình ứng dụng theo profile (dev/prod)
│   └── test/
│       ├── java/com/datn/financeapp/
│       │   ├── unitTest/           # Unit test theo từng module
│       │   └── integrationTest/    # Integration test với Testcontainers PostgreSQL
│       └── resources/
│           └── application-test.yml
├── .env.example                    # Mẫu biến môi trường
├── docker-compose.yml              # PostgreSQL 17 & Redis 7
├── pom.xml                         # Cấu hình Maven & dependencies
├── run.ps1                         # Script chạy server trên Windows
├── test.ps1                        # Script chạy Unit/Integration test trên Windows
└── performan-test.ps1              # Script chạy kiểm thử hiệu năng & N+1
```
