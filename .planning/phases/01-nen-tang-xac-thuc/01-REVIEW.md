---
phase: 01-nen-tang-xac-thuc
reviewed: 2026-08-23T02:11:06Z
depth: standard
files_reviewed: 65
files_reviewed_list:
  - pom.xml
  - src/main/java/com/datn/financeapp/FinanceAppApplication.java
  - src/main/java/com/datn/financeapp/auth/controller/AuthController.java
  - src/main/java/com/datn/financeapp/auth/dto/AuthResponse.java
  - src/main/java/com/datn/financeapp/auth/dto/ChangePasswordRequest.java
  - src/main/java/com/datn/financeapp/auth/dto/ForgotPasswordRequest.java
  - src/main/java/com/datn/financeapp/auth/dto/LoginRequest.java
  - src/main/java/com/datn/financeapp/auth/dto/LogoutRequest.java
  - src/main/java/com/datn/financeapp/auth/dto/RefreshRequest.java
  - src/main/java/com/datn/financeapp/auth/dto/RefreshResponse.java
  - src/main/java/com/datn/financeapp/auth/dto/RegisterRequest.java
  - src/main/java/com/datn/financeapp/auth/dto/ResetPasswordRequest.java
  - src/main/java/com/datn/financeapp/auth/dto/UpdateProfileRequest.java
  - src/main/java/com/datn/financeapp/auth/dto/UserDetailDto.java
  - src/main/java/com/datn/financeapp/auth/dto/UserStatsDto.java
  - src/main/java/com/datn/financeapp/auth/dto/UserSummaryDto.java
  - src/main/java/com/datn/financeapp/auth/entity/LoginAttempt.java
  - src/main/java/com/datn/financeapp/auth/entity/PasswordResetToken.java
  - src/main/java/com/datn/financeapp/auth/entity/RefreshToken.java
  - src/main/java/com/datn/financeapp/auth/entity/User.java
  - src/main/java/com/datn/financeapp/auth/repository/LoginAttemptRepository.java
  - src/main/java/com/datn/financeapp/auth/repository/PasswordResetTokenRepository.java
  - src/main/java/com/datn/financeapp/auth/repository/RefreshTokenRepository.java
  - src/main/java/com/datn/financeapp/auth/repository/UserRepository.java
  - src/main/java/com/datn/financeapp/auth/service/AuthService.java
  - src/main/java/com/datn/financeapp/auth/service/LogPasswordResetNotifier.java
  - src/main/java/com/datn/financeapp/auth/service/PasswordResetNotifier.java
  - src/main/java/com/datn/financeapp/common/exception/BusinessException.java
  - src/main/java/com/datn/financeapp/common/exception/GlobalExceptionHandler.java
  - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyAspect.java
  - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyKeyEntity.java
  - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyKeyRepository.java
  - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyTransactionHelper.java
  - src/main/java/com/datn/financeapp/common/idempotency/Idempotent.java
  - src/main/java/com/datn/financeapp/common/ratelimit/RateLimitFilter.java
  - src/main/java/com/datn/financeapp/common/ratelimit/RateLimitProperties.java
  - src/main/java/com/datn/financeapp/common/ratelimit/RateLimitRule.java
  - src/main/java/com/datn/financeapp/common/response/ApiResponse.java
  - src/main/java/com/datn/financeapp/common/response/ErrorResponse.java
  - src/main/java/com/datn/financeapp/common/response/PageMeta.java
  - src/main/java/com/datn/financeapp/common/response/PageRequestParams.java
  - src/main/java/com/datn/financeapp/common/security/ClientIpResolver.java
  - src/main/java/com/datn/financeapp/common/security/FilterAutoRegistrationConfig.java
  - src/main/java/com/datn/financeapp/common/security/JwtAuthFilter.java
  - src/main/java/com/datn/financeapp/common/security/JwtService.java
  - src/main/java/com/datn/financeapp/common/security/SecurityConfig.java
  - src/main/java/com/datn/financeapp/common/security/SecurityContextUtil.java
  - src/main/java/com/datn/financeapp/common/wallet/WalletMinimal.java
  - src/main/java/com/datn/financeapp/common/wallet/WalletMinimalRepository.java
  - src/main/java/com/datn/financeapp/scheduler/IdempotencyCleanupJob.java
  - src/main/resources/application-dev.yml
  - src/main/resources/application-test.yml
  - src/main/resources/application.yml
  - src/main/resources/messages_vi.properties
  - src/test/java/com/datn/financeapp/SchemaSmokeTest.java
  - src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java
  - src/test/java/com/datn/financeapp/auth/AuthLoginLockoutIntegrationTest.java
  - src/test/java/com/datn/financeapp/auth/AuthProfilePasswordIntegrationTest.java
  - src/test/java/com/datn/financeapp/auth/AuthRefreshRotationIntegrationTest.java
  - src/test/java/com/datn/financeapp/auth/AuthRegisterLoginIntegrationTest.java
  - src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java
  - src/test/java/com/datn/financeapp/common/idempotency/IdempotencyAspectIntegrationTest.java
  - src/test/java/com/datn/financeapp/common/ratelimit/RateLimitFilterTest.java
  - src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-08-23T02:11:06Z
**Depth:** standard
**Files Reviewed:** 65
**Status:** issues_found

## Summary

Phase 1 dựng nền tảng xác thực khá chắc chắn. Các luồng rủi ro cao nhất — băm mật khẩu (BCrypt cost 12), sinh/verify JWT (HS256, `verifyWith` chặn `alg=none`, không nhét dữ liệu nhạy cảm vào claim), refresh token rotation + reuse detection (khoá đúng race condition bằng `SELECT ... FOR UPDATE`, có test đồng thời xác nhận chỉ 1 trong 2 request song song thành công), khoá đăng nhập 5 lần/15 phút, chống dò email (login và forgot-password đều trả cùng một phản hồi bất kể email tồn tại hay không), và cơ chế idempotency (`INSERT ... ON CONFLICT DO NOTHING` + xử lý đúng bẫy self-invocation của Spring AOP) — đều triển khai đúng và có test tích hợp trên Postgres thật xác nhận. Không tìm thấy lỗ hổng SQL injection (mọi native query dùng tham số hoá, không nối chuỗi), không có secret nào bị commit (`.env` bị gitignore, `.env.example` chỉ có giá trị mẫu).

Hai vấn đề đáng chú ý nhất đều xoay quanh việc xác định địa chỉ IP của client — một cấu hình sai khiến `X-Forwarded-For` không có tác dụng ở tầng rate-limit, và một cấu hình khác lại tin tưởng `X-Forwarded-For` một cách vô điều kiện ở tầng ghi log/khoá tài khoản. Không cái nào nghiêm trọng tới mức critical trong bối cảnh đồ án (chưa có reverse proxy thật trước backend), nhưng cả hai đều là lỗi thật, đáng sửa trước khi triển khai sau một proxy/CDN.

## Warnings

### WR-01: `RateLimitFilter` không dùng `ClientIpResolver` — X-Forwarded-For vô tác dụng ở tầng rate-limit, nhưng lại được tin tưởng vô điều kiện ở tầng ghi log/khoá tài khoản

**File:** `src/main/java/com/datn/financeapp/common/ratelimit/RateLimitFilter.java:48` và `src/main/java/com/datn/financeapp/common/security/ClientIpResolver.java:15-21`

**Vấn đề:** Có hai cách lấy "IP của client" song song trong cùng phase, không nhất quán:

- `RateLimitFilter.doFilterInternal` dùng thẳng `req.getRemoteAddr()` (dòng 48) cho nhóm `auth` (`keyedByIp=true`) — bỏ qua hoàn toàn `ClientIpResolver`.
- `ClientIpResolver.resolve()` (dùng cho `login_attempts.ip_address` qua `AuthController.login`, dòng 58) đọc thẳng header `X-Forwarded-For` do **client tự gửi**, không có allowlist proxy tin cậy nào giới hạn việc chấp nhận header này.

**Kịch bản lỗi cụ thể:**
1. *Rate-limit theo IP bị vô hiệu hoá sau khi có proxy thật (tương lai):* nếu dự án triển khai sau Nginx/Cloudflare, `req.getRemoteAddr()` luôn là IP của proxy (một địa chỉ duy nhất) cho MỌI client — bucket `auth:{proxy_ip}` bị mọi người dùng dùng chung, ai đăng nhập sai trước sẽ khoá rate-limit của người khác (self-inflicted DoS), ngược hẳn mục đích D-16 ("Đếm theo IP").
2. *Giả mạo IP trong nhật ký/điều tra sự cố ngay ở giai đoạn hiện tại (chưa có proxy):* vì chưa có proxy tin cậy nào đứng trước backend, `ClientIpResolver` tin bất kỳ giá trị `X-Forwarded-For` nào client tự gửi. Một client gọi `POST /auth/login` với header `X-Forwarded-For: 1.2.3.4` (giả mạo) — bản ghi `login_attempts.ip_address` sẽ lưu `1.2.3.4` thay vì IP thật, làm sai lệch mục đích điều tra "một IP dò nhiều tài khoản" (comment tại V7 `idx_la_ip`) và có thể được dùng để né các cơ chế giám sát/chặn theo IP thật ở tầng vận hành sau này (khoá đăng nhập bản thân vẫn đúng vì khoá theo email, không theo IP — nhưng nhật ký kiểm toán bị vô hiệu).

**Đề xuất:**
- Hợp nhất về một hàm resolve IP duy nhất dùng ở cả hai nơi.
- Chỉ tin `X-Forwarded-For` khi `remoteAddr` nằm trong danh sách proxy tin cậy đã cấu hình (allowlist địa chỉ nội bộ/CIDR của reverse proxy thật); nếu không có proxy nào được khai báo tin cậy, luôn dùng `remoteAddr` bất kể header.
```java
public static String resolve(HttpServletRequest request, Set<String> trustedProxyIps) {
    String remoteAddr = request.getRemoteAddr();
    if (!trustedProxyIps.contains(remoteAddr)) {
        return remoteAddr; // Không tin proxy chưa khai báo -> bỏ qua X-Forwarded-For
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
    }
    return remoteAddr;
}
```
Ở Phase 1 (chưa deploy sau proxy), cách an toàn và đơn giản nhất là bỏ hẳn việc đọc `X-Forwarded-For`, dùng `remoteAddr` cho cả hai nơi, và để lại một TODO/ghi chú rõ ràng cho phase deploy thật (`prod` profile) khi có reverse proxy — lúc đó thêm allowlist theo cấu hình.

---

### WR-02: `changePassword` thu hồi TOÀN BỘ refresh token — lệch tài liệu `api/01-XAC-THUC.md` mục 7 ("trừ phiên hiện tại")

**File:** `src/main/java/com/datn/financeapp/auth/service/AuthService.java:290-307`

**Đây là lệch hợp đồng tài liệu đã biết trước và có chủ đích** (comment tại dòng 284-288 giải thích rõ lý do: request `POST /auth/change-password` theo `api/01-XAC-THUC.md` mục 7 chỉ nhận `old_password`/`new_password`, không mang refresh token nào để nhận diện "phiên hiện tại" cần giữ lại). Ghi lại ở đây theo đúng yêu cầu review (mọi lệch hợp đồng tài liệu phải được liệt kê), không phải một defect logic ẩn — code làm đúng những gì có thể làm với dữ liệu đầu vào hiện có, và kết quả (thu hồi nhiều hơn, không thu hồi ít hơn) an toàn hơn đặc tả gốc.

**Vấn đề còn tồn đọng cần quyết định (không phải bug, là câu hỏi sản phẩm):** hành vi thực tế là refresh token của TẤT CẢ thiết bị (kể cả thiết bị vừa đổi mật khẩu) đều bị thu hồi — người dùng vừa đổi mật khẩu trên điện thoại sẽ bị buộc đăng xuất refresh ngay trên chính điện thoại đó sau khi access token 1h hiện tại hết hạn. Đây có thể là trải nghiệm không mong muốn nếu app không xử lý bằng cách tự động gọi lại `/auth/login` ngầm sau đổi mật khẩu.

**Đề xuất:** Không cần sửa code ở Phase 1 (hành vi hiện tại an toàn). Nếu muốn khớp đúng "trừ phiên hiện tại" như tài liệu viết, `api/01-XAC-THUC.md` mục 7 cần được cập nhật để `ChangePasswordRequest` nhận thêm `refresh_token` hiện tại (giống cách `LogoutRequest` đã phải suy luận thêm field tương tự) — nhưng đó là quyết định sản phẩm cần người dùng xác nhận, không tự ý đổi API. Đề xuất: cập nhật tài liệu `api/01-XAC-THUC.md` mục 7 để phản ánh đúng hành vi thật ("thu hồi toàn bộ, không có ngoại lệ") thay vì để tài liệu và code trôi dạt — theo đúng nguyên tắc ở `CLAUDE.md` gốc mục "Khi thay đổi nghiệp vụ".

## Info

### IN-01: `messages_vi.properties` không được dùng ở đâu — tài nguyên chết

**File:** `src/main/resources/messages_vi.properties`

**Vấn đề:** File định nghĩa 4 khoá (`error.validation`, `error.internal`, `error.unauthenticated`, `error.forbidden`) nhưng `GlobalExceptionHandler` (dòng 33, 47, 55, 62) hardcode thẳng chuỗi tiếng Việt trong Java thay vì đọc qua `MessageSource`/`@Value("${...}")`. Không có `Locale`/`MessageSource` bean nào tham chiếu tới file này ở bất kỳ đâu trong 65 file đã đổi.

**Đề xuất:** Hoặc (a) xoá file này nếu không có ý định dùng i18n message bundle trong tương lai gần, hoặc (b) nối `GlobalExceptionHandler` vào `MessageSource` thật để tránh hai nguồn sự thật cho cùng một câu thông báo lỗi (nếu sau này sửa message ở properties mà quên sửa Java, hai nơi lệch nhau âm thầm — đúng kiểu lỗi mà `CLAUDE.md` backend đã cảnh báo về "hai tài liệu song song không gặp nhau ở runtime").

### IN-02: `UpdateProfileRequest.avatarUrl` không có ràng buộc định dạng/độ dài

**File:** `src/main/java/com/datn/financeapp/auth/dto/UpdateProfileRequest.java:9`

**Vấn đề:** `avatarUrl` là `String` trần, không có `@URL`, không có `@Size`/`@Pattern` giới hạn scheme. Cột DB tương ứng (`avatar_url TEXT`) không giới hạn độ dài nên không tràn được ở tầng CSDL, nhưng client (app Flutter) có thể vô tình lưu một chuỗi tuỳ ý (kể cả `javascript:...` hoặc chuỗi cực dài) làm "avatar url" nếu không tự validate ở tầng UI.

**Đề xuất (không khẩn cấp, không thuộc `api/01-XAC-THUC.md` yêu cầu tường minh):** cân nhắc thêm `@Size(max = 2048)` tối thiểu để tránh input bất thường; việc validate scheme `http(s)://` chỉ cần thiết nếu backend từng render giá trị này ra HTML ở đâu đó (hiện tại không có, nên rủi ro XSS thực tế là 0 ở phase này).

### IN-03: `isLockedOut` chỉ đếm 5 bản ghi gần nhất theo `email`, không phân biệt hoa/thường ở tầng truy vấn

**File:** `src/main/java/com/datn/financeapp/auth/service/AuthService.java:376-387`, `src/main/java/com/datn/financeapp/auth/repository/LoginAttemptRepository.java:15`

**Vấn đề:** `AuthService.login` luôn lowercase email trước khi gọi `isLockedOut`/lưu `LoginAttempt` (dòng 125, 140), nên trên thực tế không có cách nào để bản ghi `login_attempts.email` khác hoa/thường xuất hien — không phải bug hiện tại. Ghi chú ở mức info vì đây là bất biến ngầm (implicit invariant) chỉ được đảm bảo bởi kỷ luật gọi `.toLowerCase()` đúng chỗ mọi lần, không được ép buộc ở tầng truy vấn (`findTop5ByEmailOrderByAttemptedAtDesc` không có `LOWER(email) = LOWER(:email)`). Nếu sau này có thêm một entry point khác ghi `LoginAttempt` (vd. import log cũ, admin tool) mà quên lowercase, cơ chế khoá tài khoản sẽ bị chia thành hai "danh tính" khác nhau cho cùng một email, làm giảm hiệu lực khoá 5 lần.

**Đề xuất:** Không cần sửa ngay — chỉ cần giữ kỷ luật lowercase tại mọi điểm ghi `LoginAttempt`/tra cứu `User`. Nếu muốn triệt để, thêm `CHECK (email = lower(email))` tương tự bảng `users` đã có (thuộc phạm vi migration, không phải Phase 1).

---

## Các mảng đã kiểm tra và không có phát hiện

- **Băm mật khẩu:** BCrypt cost 12 (`SecurityConfig.passwordEncoder`), đúng yêu cầu "≥12" của `api/01-XAC-THUC.md`. Không nơi nào log/serialize `passwordHash` ra response (`UserSummaryDto`/`UserDetailDto` không có field này).
- **JWT:** thuật toán cố định HS256 qua `Jwts.SIG.HS256`, `verifyWith(key)` (API jjwt 0.13.x mới, không dùng `parserBuilder` lỗi thời) — chặn được algorithm confusion (`alg=none`). Access token chỉ chứa `sub`/`plan`/`exp`, không có trường nhạy cảm, khớp "Ghi chú triển khai" của `api/01`. Secret nạp bắt buộc qua `${JWT_SECRET}`, không có default, `Keys.hmacShaKeyFor` fail-fast nếu secret quá ngắn.
- **Refresh token rotation & reuse detection:** hash SHA-256 lưu DB (không lưu bản gốc), khoá đúng race condition bằng `PESSIMISTIC_WRITE`, có test tích hợp xác nhận 2 request đồng thời chỉ 1 thành công, và test xác nhận reuse token đã revoke thu hồi toàn bộ phiên.
- **Khoá đăng nhập 5 lần/15 phút:** đúng thuật toán "5 bản ghi gần nhất toàn bộ thất bại", có một lần đúng chen giữa thì phá chuỗi — có test bao phủ cả 3 nhánh (khoá, hết hạn tự nhiên, chen giữa không khoá).
- **Chống dò email (user enumeration):** `login` trả cùng `INVALID_CREDENTIALS` cho sai email lẫn sai mật khẩu; `forgotPassword` luôn trả 200 kể cả email không tồn tại, không tạo token/không gọi notifier ở nhánh không tồn tại.
- **SQL injection:** mọi native query (`IdempotencyKeyRepository`, `RefreshTokenRepository`, `getMe` trong `AuthService`) đều dùng tham số hoá qua `:param`/`?`, không nối chuỗi.
- **Idempotency:** đúng luồng `INSERT ... ON CONFLICT DO NOTHING` → `processing`/`completed`, xử lý đúng bẫy self-invocation Spring AOP bằng `IdempotencyTransactionHelper` tách bean riêng, không gắn `@Idempotent` lên `/auth/login|refresh|logout` (đúng D-11).
- **Transaction boundaries:** `noRollbackFor = BusinessException.class` dùng đúng chỗ (login, refresh) kèm giải thích rõ lý do trong Javadoc — nếu không có annotation này, bằng chứng lockout/reuse-detection sẽ bị rollback theo cùng exception.
- **Secret trong repo:** `.env` bị gitignore (không nằm trong danh sách file đã đổi), `.env.example` chỉ chứa giá trị mẫu (`changeme`, `JWT_SECRET=` rỗng).
- **Đối chiếu schema thật:** entity `RefreshToken`/`PasswordResetToken`/`LoginAttempt`/`IdempotencyKeyEntity` khớp đúng cột/kiểu của `V7__ha_tang_xac_thuc.sql` (kể cả `INET`, `JSONB`, `UNIQUE`).
- **Riêng tư mặc định (nguyên tắc 3 CLAUDE.md gốc):** Phase 1 chưa có query nào cần điều kiện quyền `user_id = current_user OR group_id IN (...)` (đúng phạm vi phase — chưa dựng `wallet/`/`transaction/`). Ví "Tiền mặt" tạo lúc đăng ký gán đúng `user_id`, `group_id = null`.

---

_Reviewed: 2026-08-23T02:11:06Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
