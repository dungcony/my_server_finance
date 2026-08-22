---
phase: 01-nen-tang-xac-thuc
plan: 02
subsystem: auth
tags: [spring-security, jjwt, jwt, exception-handling, bcrypt, vietnamese-encoding]

# Dependency graph
requires:
  - phase: 01-01
    provides: "Dự án Maven Spring Boot chạy được, pom.xml có sẵn jjwt 0.13.0/spring-boot-starter-security/spring-boot-starter-validation, application.yml có jwt.secret=${JWT_SECRET}"
provides:
  - "common/response/ — ApiResponse<T>, ErrorResponse, PageMeta, PageRequestParams (khung {success,data}/{success,error} theo api/00 mục 4)"
  - "common/exception/ — BusinessException tổng quát + GlobalExceptionHandler gom mọi lỗi (validate/business/auth/access-denied/catch-all) thành đúng khung, không lộ message nội bộ"
  - "common/security/ — JwtService (sinh/verify JWT jjwt 0.13.x API mới), JwtAuthFilter (OncePerRequestFilter đọc Bearer token), SecurityConfig (SecurityFilterChain STATELESS + BCryptPasswordEncoder cost 12), SecurityContextUtil (lấy userId hiện tại)"
  - "pom.xml vá lỗi mã hoá UTF-8 cho toàn bộ chuỗi tiếng Việt trong code/test (project.build.sourceEncoding + -Dfile.encoding=UTF-8 cho surefire/spring-boot-maven-plugin)"
affects: [01-03, 01-04, 01-05, 01-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "GlobalExceptionHandler dùng @RestControllerAdvice tập trung — mọi controller phase sau không tự bắt exception, chỉ throw BusinessException(code, httpStatus, message[, detail])"
    - "JwtService.generateAccessToken(userId, plan) chỉ nhận đúng 2 tham số cố định — mọi claim mới phải sửa method signature, không có cơ chế thêm claim tuỳ ý (chống elevation-of-privilege qua claim giả mạo)"
    - "JwtAuthFilter không tự chặn request khi thiếu/sai token — chỉ set/không-set SecurityContext, để entry point (AuthenticationException -> GlobalExceptionHandler) trả 401 thống nhất"
    - "@WebMvcTest slice test cho GlobalExceptionHandler phải loại SecurityConfig/JwtAuthFilter qua excludeFilters — nếu không sẽ tự nạp toàn bộ security filter chain thật, phá slice test cô lập"
    - "project.build.sourceEncoding=UTF-8 + -Dfile.encoding=UTF-8 bắt buộc cho JVM chạy trên Windows dev — mặc định Windows-1252 làm hỏng mọi chuỗi tiếng Việt literal trong code lẫn response test assertion"

key-files:
  created:
    - src/main/java/com/datn/financeapp/common/response/ApiResponse.java
    - src/main/java/com/datn/financeapp/common/response/ErrorResponse.java
    - src/main/java/com/datn/financeapp/common/response/PageMeta.java
    - src/main/java/com/datn/financeapp/common/response/PageRequestParams.java
    - src/main/java/com/datn/financeapp/common/exception/BusinessException.java
    - src/main/java/com/datn/financeapp/common/exception/GlobalExceptionHandler.java
    - src/main/resources/messages_vi.properties
    - src/main/java/com/datn/financeapp/common/security/JwtService.java
    - src/main/java/com/datn/financeapp/common/security/JwtAuthFilter.java
    - src/main/java/com/datn/financeapp/common/security/SecurityConfig.java
    - src/main/java/com/datn/financeapp/common/security/SecurityContextUtil.java
    - src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java
    - src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java
  modified:
    - pom.xml

key-decisions:
  - "Thêm project.build.sourceEncoding=UTF-8 và -Dfile.encoding=UTF-8 vào surefire/spring-boot-maven-plugin — phát hiện khi test Vietnamese message assertion trả về mojibake dù source file đã UTF-8; JVM Windows mặc định đọc bytecode/String literal theo Windows-1252"
  - "GlobalExceptionHandlerTest dùng @WebMvcTest với excludeFilters loại SecurityConfig + JwtAuthFilter — @Configuration bean không bị @WebMvcTest controllers-scoping lọc như @Controller, nên SecurityConfig thật (cần JwtService) luôn bị nạp vào context slice test trừ khi loại tường minh"
  - "JwtServiceTest kiểm tra algorithm confusion (T-02-01) bằng token ký HS384 với key riêng — jjwt 0.13.x ném JwtException (SignatureException hoặc WeakKeyException tuỳ đường verify) khi key HS256 đã cấu hình không khớp, không có trường hợp nào chấp nhận nhầm"

patterns-established:
  - "Response wrapper: mọi endpoint trả ApiResponse.of(data) khi thành công; mọi lỗi throw BusinessException hoặc để GlobalExceptionHandler tự bắt exception hạ tầng"
  - "JWT: chỉ JwtService được phép sinh/verify token; JwtAuthFilter/SecurityContextUtil là điểm truy cập duy nhất tới danh tính user hiện tại ở tầng downstream"

requirements-completed: [CORE-01, CORE-02, AUTH-08]

# Metrics
duration: 52min
completed: 2026-08-22
---

# Phase 1 Plan 02: Tầng common — response wrapper, exception handler, hạ tầng JWT Summary

**Response wrapper {success,data}/{success,error} thống nhất qua GlobalExceptionHandler tập trung, cùng JwtService sinh/verify token theo đúng API jjwt 0.13.x mới (Jwts.parser().verifyWith(key)) và SecurityFilterChain STATELESS sẵn sàng cho AuthController ở Plan 03/04.**

## Performance

- **Duration:** 52 phút
- **Started:** 2026-08-22T15:59:00Z (ước lượng từ session start)
- **Completed:** 2026-08-22T16:51:00Z
- **Tasks:** 2/2 hoàn thành
- **Files modified:** 13 file mới, 1 file sửa (`pom.xml`)

## Accomplishments
- `common/response/` hoàn chỉnh: `ApiResponse<T>`, `ErrorResponse`, `PageMeta`, `PageRequestParams` — dựng sẵn CORE-02 cho Phase 2 dù Phase 1 chưa có endpoint phân trang
- `GlobalExceptionHandler` gom đúng 5 loại exception (`MethodArgumentNotValidException` trả **hết** mọi field lỗi một lượt, `BusinessException`, `AuthenticationException`, `AccessDeniedException`, catch-all `Exception`) thành khung `{success,error}` chuẩn `api/00-QUY-UOC-CHUNG.md`, không lộ message/stack trace nội bộ ra response (chỉ log SLF4J server-side) — mitigation T-02-02
- `JwtService` sinh/verify JWT bằng đúng API jjwt 0.13.x hiện đại, access token chỉ chứa `sub`/`plan`/`exp` theo AUTH-08, chống algorithm confusion (T-02-01) và secret yếu fail-fast qua `WeakKeyException` (T-02-03)
- `SecurityConfig` STATELESS với `JwtAuthFilter` gắn trước `UsernamePasswordAuthenticationFilter`, 5 endpoint auth công khai đúng danh sách, `BCryptPasswordEncoder` cost 12 sẵn cho Plan 04 (`AuthService`)
- Phát hiện và vá lỗi mã hoá tiếng Việt toàn dự án (Rule 3 — chặn bất kỳ code/test nào dùng chuỗi tiếng Việt, bao gồm cả Wave 1 di sản) — không có `project.build.sourceEncoding` khiến JVM Windows đọc Windows-1252, làm hỏng mọi message lỗi tiếng Việt

## Task Commits

Each task was committed atomically:

1. **Task 1: Response wrapper + GlobalExceptionHandler + message tiếng Việt** - `fafba89` (feat)
2. **Task 2: JwtService + JwtAuthFilter + SecurityConfig** - `8769142` (feat)

**Plan metadata:** (commit theo sau khi ghi SUMMARY.md)

## Files Created/Modified
- `src/main/java/com/datn/financeapp/common/response/ApiResponse.java` — response wrapper thành công `{success:true, data}`
- `src/main/java/com/datn/financeapp/common/response/ErrorResponse.java` — response wrapper lỗi `{success:false, error:{code,message,fields,detail}}`
- `src/main/java/com/datn/financeapp/common/response/PageMeta.java`, `PageRequestParams.java` — khung phân trang dùng lại từ Phase 2 (CORE-02)
- `src/main/java/com/datn/financeapp/common/exception/BusinessException.java` — exception nghiệp vụ tổng quát mang mã + HTTP status + detail
- `src/main/java/com/datn/financeapp/common/exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` gom mọi exception
- `src/main/resources/messages_vi.properties` — placeholder message tiếng Việt dùng chung
- `src/main/java/com/datn/financeapp/common/security/JwtService.java` — sinh/verify JWT jjwt 0.13.x
- `src/main/java/com/datn/financeapp/common/security/JwtAuthFilter.java` — `OncePerRequestFilter` đọc `Authorization: Bearer`
- `src/main/java/com/datn/financeapp/common/security/SecurityConfig.java` — `SecurityFilterChain` STATELESS + `PasswordEncoder`
- `src/main/java/com/datn/financeapp/common/security/SecurityContextUtil.java` — helper lấy `userId` hiện tại
- `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java` — `@WebMvcTest` slice, 3 test case (validate nhiều trường, business exception, runtime exception không lộ message)
- `src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java` — unit test thuần, 4 test case (sinh/parse đúng, hết hạn, sai chữ ký, algorithm confusion)
- `pom.xml` — thêm `project.build.sourceEncoding=UTF-8`, `project.reporting.outputEncoding=UTF-8`, `-Dfile.encoding=UTF-8` cho `maven-surefire-plugin` và `spring-boot-maven-plugin`

## Decisions Made
- Vá mã hoá UTF-8 ở cấp `pom.xml` thay vì chỉ sửa từng file — vấn đề ảnh hưởng **mọi** chuỗi tiếng Việt trong toàn dự án (kể cả code Wave 1 đã commit), không riêng gì Plan 02
- `GlobalExceptionHandlerTest` loại `SecurityConfig`/`JwtAuthFilter` khỏi `@WebMvcTest` context qua `excludeFilters` — giữ đúng tinh thần "test-only controller, không đụng controller nghiệp vụ thật" của plan, đồng thời không để security filter chain thật (cần `JwtService` bean, vốn không có trong slice test) làm sập context
- `JwtServiceTest` bắt `JwtException` (lớp cha) thay vì chỉ `SignatureException` cho test algorithm-confusion — jjwt 0.13.x có thể ném `WeakKeyException` hoặc `SignatureException` tuỳ đường verify nội bộ, cả hai đều là "từ chối đúng"; test vẫn giữ đúng ý đồ ban đầu (không chấp nhận nhầm token ký bằng thuật toán/key khác)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Thiếu `project.build.sourceEncoding`, mọi chuỗi tiếng Việt bị hỏng khi build/test trên JVM Windows**
- **Found during:** Task 1 (chạy `GlobalExceptionHandlerTest` lần đầu — response message tiếng Việt hiện ra dạng `?� c� l?i x?y ra...`)
- **Issue:** `pom.xml` không khai `project.build.sourceEncoding`, và `maven-surefire-plugin`/`spring-boot-maven-plugin` không ép `-Dfile.encoding=UTF-8` — JVM chạy trên Windows dev đọc theo code page mặc định Windows-1252 thay vì UTF-8, làm hỏng mọi `String` literal tiếng Việt trong Java lẫn trong JSON response test assertion. Đây là yêu cầu cứng của `<language>` (toàn bộ output tiếng Việt phải đúng dấu) và của CLAUDE.md gốc (giao tiếp/message tiếng Việt)
- **Fix:** Thêm `project.build.sourceEncoding=UTF-8`, `project.reporting.outputEncoding=UTF-8` vào `<properties>`; thêm `-Dfile.encoding=UTF-8` vào `argLine` của `maven-surefire-plugin` và `jvmArguments` của `spring-boot-maven-plugin` (giữ nguyên `-Duser.timezone=UTC` đã có từ Wave 1)
- **Files modified:** `pom.xml`
- **Verification:** `mvn test -Dtest=GlobalExceptionHandlerTest` — test case assert đúng chuỗi `"Đã có lỗi xảy ra, vui lòng thử lại sau."` (có dấu đầy đủ) pass
- **Committed in:** `fafba89`

**2. [Rule 3 - Blocking] `@WebMvcTest` không nạp được `TestController` nội bộ do visibility, sau đó nạp nhầm `SecurityConfig` thật gây sập context**
- **Found during:** Task 1 (chạy `GlobalExceptionHandlerTest` — ban đầu `NoResourceFoundException` vì controller test-only không được đăng ký; sau khi sửa lại gặp `UnsatisfiedDependencyException` vì `SecurityConfig` thật đòi `JwtService` bean không có trong slice test)
- **Issue:** (a) `TestController`/`TestDto` nested class ban đầu package-private khiến `@WebMvcTest(controllers=...)` không đăng ký được bean; (b) sau khi thêm `SecurityConfig` ở Task 2, `@WebMvcTest` tự nạp `@Configuration` bean này (không bị lọc theo `controllers=` như `@Controller`), kéo theo `JwtAuthFilter` cần `JwtService` — bean không tồn tại trong slice test cô lập
- **Fix:** (a) Đổi `TestController`/`TestDto` thành `public static`; (b) dùng `@WebMvcTest(excludeFilters = ...)` loại tường minh `SecurityConfig.class` và `JwtAuthFilter.class` khỏi component scan, giữ `@Import({GlobalExceptionHandler.class, TestController.class})` để chỉ nạp đúng những gì cần test
- **Files modified:** `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java`
- **Verification:** `mvn test -Dtest=GlobalExceptionHandlerTest,JwtServiceTest` — 7/7 pass; `mvn test` toàn bộ project (bao gồm `SchemaSmokeTest` cũ từ Wave 1) — 8/8 pass, xác nhận `SecurityConfig`/`JwtAuthFilter` thật vẫn boot đúng trong context đầy đủ
- **Committed in:** `8769142`

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Cả hai là lỗi chặn cứng phát sinh khi chạy test thật (không tự phát hiện được khi chỉ đọc PLAN.md), đúng tinh thần D-07 "verify bằng cách chạy thật". Không có scope creep, không đổi kiến trúc `common/security`/`common/exception` đã chốt trong PLAN.md.

## Issues Encountered
Không có vấn đề nào ngoài 2 deviation đã liệt kê ở trên — toàn bộ đã giải quyết ngay trong quá trình thực thi, không có blocker còn tồn đọng.

## User Setup Required

Không có bước thủ công bên ngoài. Toàn bộ hạ tầng dùng `.env`/`JWT_SECRET` đã có sẵn từ Plan 01.

## Next Phase Readiness

- `common/response/`, `common/exception/`, `common/security/` hoàn chỉnh — Plan 03 (idempotency AOP) và Plan 04/05 (AuthController/AuthService) có thể import trực tiếp `ApiResponse`, `BusinessException`, `JwtService`, `SecurityContextUtil` ngay
- `SecurityConfig` đã permitAll đúng 5 endpoint auth công khai theo tên route dự kiến (`/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`) — Plan 04 chỉ cần tạo `AuthController` khớp đúng path, không cần sửa lại `SecurityConfig`
- Lỗi mã hoá UTF-8 đã vá ở cấp `pom.xml` — mọi plan sau (bao gồm cả code/test Wave 1 cũ nếu build lại) đều được hưởng lợi, không cần vá lại từng nơi
- Không có blocker nào cho Plan 03

---
*Phase: 01-nen-tang-xac-thuc*
*Completed: 2026-08-22*

## Self-Check: PASSED

Toàn bộ 13 file trong `key-files.created` + `pom.xml` (modified) xác nhận tồn tại trên đĩa; commit `fafba89` và `8769142` xác nhận có trong `git log`.
