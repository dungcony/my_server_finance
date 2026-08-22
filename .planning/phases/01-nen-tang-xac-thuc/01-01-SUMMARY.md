---
phase: 01-nen-tang-xac-thuc
plan: 01
subsystem: infra
tags: [spring-boot, maven, flyway, postgresql, testcontainers, docker-compose, jpa, hibernate]

# Dependency graph
requires: []
provides:
  - "Dự án Maven Spring Boot 3.4.13/Java 17 chạy được (compile + spring-boot:run + test)"
  - "Migration V1-V7 đã verify chạy sạch trên PostgreSQL thật (docker-compose dev, Testcontainers test)"
  - "6 entity JPA tối thiểu khớp đúng schema thật: User, RefreshToken, PasswordResetToken, LoginAttempt, IdempotencyKeyEntity, WalletMinimal"
  - "Cấu hình môi trường fail-fast qua .env bắt buộc (D-01/D-02), 2 profile dev/test (D-03), docker-compose Postgres dev (D-04), README khởi động (D-05)"
  - "SchemaSmokeTest — bằng chứng ddl-auto=validate không lỗi qua Testcontainers thật"
affects: [01-02, 01-03, 01-04, 01-05, 01-06]

# Tech tracking
tech-stack:
  added:
    - "Spring Boot 3.4.13 (parent), Java 17"
    - "jjwt 0.13.0 (jjwt-api/impl/jackson)"
    - "flyway-core + flyway-database-postgresql (Flyway 10+ driver riêng)"
    - "bucket4j_jdk17-core 8.19.0"
    - "caffeine (theo Spring Boot BOM)"
    - "springboot3-dotenv 5.1.0 (đổi từ 4.0.0 trong RESEARCH.md — version đó không tồn tại)"
    - "mapstruct 1.6.3 + mapstruct-processor"
    - "lombok"
    - "Testcontainers (quản lý bởi Spring Boot BOM, 1.20.6 — không tự import testcontainers-bom 2.x)"
  patterns:
    - "Entity id set bằng UUID.randomUUID() ở tầng Service trước khi save, không dựa vào DEFAULT gen_random_uuid() của cột"
    - "user_id lưu dạng UUID trần (không @ManyToOne) trong RefreshToken/PasswordResetToken/IdempotencyKeyEntity để tránh lazy-loading không cần thiết ở Phase 1"
    - "@JdbcTypeCode(SqlTypes.INET) cho cột INET (LoginAttempt.ipAddress), @JdbcTypeCode(SqlTypes.JSON) cho cột JSONB (IdempotencyKeyEntity.responseBody)"
    - "Cột Postgres CHAR(n) phải khai @Column(columnDefinition=\"bpchar(n)\") — Postgres báo physical type là bpchar, không phải char/varchar"
    - "JVM ép -Duser.timezone=UTC ở cả spring-boot-maven-plugin và maven-surefire-plugin để tránh lỗi kết nối Postgres khi host OS dùng zone ID không chuẩn IANA (Windows 'Asia/Saigon')"

key-files:
  created:
    - pom.xml
    - src/main/java/com/datn/financeapp/FinanceAppApplication.java
    - src/main/resources/application.yml
    - src/main/resources/application-dev.yml
    - src/main/resources/application-test.yml
    - docker-compose.yml
    - .env.example
    - .gitignore
    - README.md
    - src/main/java/com/datn/financeapp/auth/entity/User.java
    - src/main/java/com/datn/financeapp/auth/entity/RefreshToken.java
    - src/main/java/com/datn/financeapp/auth/entity/PasswordResetToken.java
    - src/main/java/com/datn/financeapp/auth/entity/LoginAttempt.java
    - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyKeyEntity.java
    - src/main/java/com/datn/financeapp/common/wallet/WalletMinimal.java
    - src/test/java/com/datn/financeapp/SchemaSmokeTest.java
  modified: []

key-decisions:
  - "springboot3-dotenv version thật là 5.1.0, không phải 4.0.0 như RESEARCH.md ghi (4.0.0 không tồn tại trên Maven Central — verify trực tiếp trước khi ghi vào pom.xml)"
  - "Không tự import org.testcontainers:testcontainers-bom — dùng version 1.20.6 do Spring Boot 3.4.13 quản lý sẵn, vì Testcontainers 2.x đổi tên artifact (junit-jupiter -> testcontainers-junit-jupiter, postgresql -> testcontainers-postgresql) không tương thích ngược với artifactId cũ mà pom.xml/RESEARCH.md dùng"
  - "Ép -Duser.timezone=UTC cho JVM chạy app và test — máy dev Windows báo zone ID 'Asia/Saigon' không nằm trong danh sách PostgreSQL JDBC chấp nhận cho tham số khởi động TimeZone"
  - "WalletMinimal.color dùng columnDefinition=\"bpchar(7)\" thay vì length=7 hay columnDefinition=\"char(7)\" — Postgres báo physical type CHAR là bpchar, Hibernate ddl-auto=validate so khớp chuỗi type chứ không suy luận"

requirements-completed: [CORE-09, CORE-10, CORE-11]

# Metrics
duration: 32min
completed: 2026-08-22
---

# Phase 1 Plan 01: Khung dự án Spring Boot + Entity xác thực Summary

**Dự án Maven Spring Boot 3.4.13/Java 17 khởi động sạch với Flyway migrate đủ V1-V7 trên PostgreSQL thật (docker-compose dev, Testcontainers test), 6 entity JPA khớp đúng schema (bao gồm ánh xạ INET/JSONB/CHAR chuẩn xác), `ddl-auto=validate` xanh.**

## Performance

- **Duration:** 32 phút
- **Started:** 2026-08-22T16:01:00Z (ước lượng từ session start)
- **Completed:** 2026-08-22T16:33:39Z
- **Tasks:** 2/2 hoàn thành
- **Files modified:** 16 file mới (không sửa file có sẵn nào)

## Accomplishments
- Dựng project Maven từ số không: `pom.xml` với toàn bộ dependency đã verify version thật trên Maven Central (jjwt 0.13.0, bucket4j_jdk17-core 8.19.0, flyway-database-postgresql, mapstruct 1.6.3)
- Cấu hình môi trường fail-fast đúng D-01 đến D-05: `.env` bắt buộc không có default, `.gitignore` tạo trước `.env` (không có khoảnh khắc nào `.env` lọt qua git), `docker-compose.yml` Postgres dev, 2 profile `dev`/`test`, README hướng dẫn đầy đủ
- Verify bằng cách chạy thật: migration V1→V7 chạy sạch cả qua `mvn spring-boot:run` (docker-compose Postgres) lẫn `mvn test` (Testcontainers) — đúng yêu cầu D-07
- 6 entity JPA tối thiểu khớp chính xác cột thật trong V1/V7, `ddl-auto=validate` không ném `SchemaManagementException`
- `SchemaSmokeTest` xanh — bằng chứng khách quan schema/entity ăn khớp qua Testcontainers PostgreSQL 16 thật

## Task Commits

Each task was committed atomically:

1. **Task 1: Khởi tạo Maven project + cấu hình môi trường + Docker Compose** - `41809c6` (feat)
2. **Task 2: Viết entity tối thiểu khớp schema thật + verify migration V1-V7 qua Testcontainers** - `98c0160` (feat)

**Plan metadata:** (commit theo sau khi ghi SUMMARY.md)

## Files Created/Modified
- `pom.xml` — Maven project, Java 17, Spring Boot 3.4.13, toàn bộ dependency đã verify + fix version dotenv + timezone JVM args
- `src/main/java/com/datn/financeapp/FinanceAppApplication.java` — entrypoint chuẩn `@SpringBootApplication`
- `src/main/resources/application.yml` — cấu hình chung, biến môi trường bắt buộc, Flyway trỏ `filesystem:../../db/migration`
- `src/main/resources/application-dev.yml`, `application-test.yml` — 2 profile theo D-03
- `docker-compose.yml` — Postgres 16 dev container đọc `.env`
- `.env.example` — mẫu biến môi trường commit vào repo
- `.gitignore` — chặn `.env`, `target/`, tạo TRƯỚC khi ghi `.env`
- `README.md` — trình tự khởi động đầy đủ (D-05)
- `src/main/java/com/datn/financeapp/auth/entity/User.java` — khớp bảng `users`
- `src/main/java/com/datn/financeapp/auth/entity/RefreshToken.java` — khớp bảng `refresh_tokens`
- `src/main/java/com/datn/financeapp/auth/entity/PasswordResetToken.java` — khớp bảng `password_reset_tokens`
- `src/main/java/com/datn/financeapp/auth/entity/LoginAttempt.java` — khớp bảng `login_attempts`, cột `INET`
- `src/main/java/com/datn/financeapp/common/idempotency/IdempotencyKeyEntity.java` — khớp bảng `idempotency_keys`, cột `JSONB`
- `src/main/java/com/datn/financeapp/common/wallet/WalletMinimal.java` — entity tối thiểu cho AUTH-01, ghi rõ Javadoc "Phase 2" cho module wallet đầy đủ
- `src/test/java/com/datn/financeapp/SchemaSmokeTest.java` — Testcontainers PostgreSQL 16, verify `ddl-auto=validate`

## Decisions Made
- `springboot3-dotenv` phiên bản thật dùng được là **5.1.0** — RESEARCH.md ghi 4.0.0 (đánh dấu `[ASSUMED]` A1) không tồn tại trên Maven Central, đã verify trực tiếp qua `curl` maven-metadata.xml trước khi ghi vào `pom.xml`
- Không import `org.testcontainers:testcontainers-bom` riêng — giữ nguyên version 1.20.6 do `spring-boot-starter-parent:3.4.13` quản lý, vì Testcontainers 2.x đổi tên artifact hoàn toàn không tương thích ngược (`junit-jupiter`→`testcontainers-junit-jupiter`, `postgresql`→`testcontainers-postgresql`) — dùng artifact cũ theo đúng plan nghĩa là phải giữ version BOM cũ
- Ép `-Duser.timezone=UTC` cho cả `spring-boot-maven-plugin` và `maven-surefire-plugin` — máy dev Windows trả về zone ID `Asia/Saigon` mà PostgreSQL JDBC driver không chấp nhận cho tham số khởi động `TimeZone` (chỉ nhận ID chuẩn IANA như `Asia/Ho_Chi_Minh`)
- `WalletMinimal.color` dùng `columnDefinition="bpchar(7)"` — Postgres CHAR(7) báo physical type là `bpchar`, không phải `char`/`varchar`; nếu chỉ set `length=7` hay `columnDefinition="char(7)"`, Hibernate `ddl-auto=validate` luôn báo lệch type

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Version `springboot3-dotenv:4.0.0` không tồn tại trên Maven Central**
- **Found during:** Task 1 (viết `pom.xml`)
- **Issue:** Plan yêu cầu verify tên/version artifact trước khi ghi vào `pom.xml` (RESEARCH.md đánh dấu `[ASSUMED]` A1). `curl` trực tiếp Maven Central trả về 404 cho version 4.0.0
- **Fix:** Query `maven-metadata.xml`, xác nhận artifact `me.paulschwarz:springboot3-dotenv` tồn tại nhưng version khả dụng mới nhất là `5.1.0` — dùng version này thay
- **Files modified:** `pom.xml`
- **Verification:** `mvn -q -DskipTests compile` thành công
- **Committed in:** `41809c6`

**2. [Rule 3 - Blocking] `testcontainers-bom:2.0.5` đổi tên artifact, không resolve được `junit-jupiter`/`postgresql` như pom.xml yêu cầu**
- **Found during:** Task 1 (compile lần đầu)
- **Issue:** `mvn compile` báo `'dependencies.dependency.version' for org.testcontainers:junit-jupiter:jar is missing` — vì import `testcontainers-bom:2.0.5` (RESEARCH.md pin cứng) không còn quản lý artifact tên cũ `junit-jupiter`/`postgresql`, đã đổi thành `testcontainers-junit-jupiter`/`testcontainers-postgresql` từ Testcontainers 2.x
- **Fix:** Bỏ import `testcontainers-bom` riêng, để Spring Boot 3.4.13 tự quản lý Testcontainers 1.20.6 (đã tương thích artifact tên cũ)
- **Files modified:** `pom.xml`
- **Verification:** `mvn -q -DskipTests compile` thành công
- **Committed in:** `41809c6`

**3. [Rule 3 - Blocking] JVM lấy timezone host Windows "Asia/Saigon" khiến PostgreSQL JDBC từ chối kết nối**
- **Found during:** Task 2 (chạy `SchemaSmokeTest` lần đầu)
- **Issue:** `FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"` — máy Windows dev báo zone ID không chuẩn IANA, PostgreSQL server không nhận trong gói bắt tay JDBC, chặn hoàn toàn Flyway/Hibernate khởi động
- **Fix:** Thêm `-Duser.timezone=UTC` vào `maven-surefire-plugin` (test) và `spring-boot-maven-plugin` (`mvn spring-boot:run`) — nhất quán với quy ước dự án lưu mọi `TIMESTAMPTZ` theo UTC
- **Files modified:** `pom.xml`
- **Verification:** `mvn test -Dtest=SchemaSmokeTest` xanh; `mvn spring-boot:run` khởi động thành công, Flyway migrate V1-V7 qua docker-compose Postgres
- **Committed in:** `98c0160`

**4. [Rule 1 - Bug] `WalletMinimal.color` sai kiểu cột — `ddl-auto=validate` báo lệch type `bpchar` vs `varchar(7)`**
- **Found during:** Task 2 (chạy `SchemaSmokeTest` lần 2 sau khi sửa timezone)
- **Issue:** Entity khai `@Column(columnDefinition = "char(7)")` rồi `length = 7` — cả hai đều khiến Hibernate kỳ vọng `varchar(7)`, trong khi Postgres báo physical type thật của cột `CHAR(7)` là `bpchar`
- **Fix:** Đổi thành `@Column(columnDefinition = "bpchar(7)")` khớp đúng type Postgres thật trả về
- **Files modified:** `src/main/java/com/datn/financeapp/common/wallet/WalletMinimal.java`
- **Verification:** `mvn test -Dtest=SchemaSmokeTest` exit code 0, không còn `SchemaManagementException`
- **Committed in:** `98c0160`

---

**Total deviations:** 4 auto-fixed (3 blocking, 1 bug)
**Impact on plan:** Cả 4 đều là lỗi chặn cứng lúc chạy thật (không tự phát hiện được khi chỉ đọc RESEARCH.md) — đúng tinh thần D-07 "verify bằng cách chạy thật, lỗi phát sinh thì sửa tại chỗ". Không có scope creep, không đổi kiến trúc đã chốt trong CONTEXT.md.

## Issues Encountered
Không có vấn đề nào ngoài 4 deviation đã liệt kê ở trên — toàn bộ đã giải quyết ngay trong quá trình thực thi, không có blocker còn tồn đọng.

## User Setup Required

Không có bước thủ công bên ngoài. `.env` đã được tạo sẵn tại `source/server/.env` với `JWT_SECRET` sinh ngẫu nhiên qua `openssl rand -base64 32` để dev có thể chạy ngay `mvn spring-boot:run` sau `docker compose up -d` mà không cần tự tạo file — đã verify `.env` không lọt vào git (`git status --short` không liệt kê).

## Next Phase Readiness

- Nền tảng Maven/Spring Boot/Flyway/entity đã sẵn sàng cho Plan 02 (response wrapper, exception handler) và các plan tiếp theo của Phase 1 (JWT service, AuthController, idempotency AOP, rate limiting)
- 6 entity trong `auth/entity/`, `common/idempotency/`, `common/wallet/` có thể dùng ngay cho Repository ở Plan 03+
- Không có blocker nào cho Plan 02

---
*Phase: 01-nen-tang-xac-thuc*
*Completed: 2026-08-22*

## Self-Check: PASSED

Toàn bộ 16 file trong `key-files.created` xác nhận tồn tại trên đĩa; commit `41809c6` và `98c0160` xác nhận có trong `git log`.
