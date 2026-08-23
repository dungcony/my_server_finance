---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: "Hoàn thành 01-06-PLAN.md - đóng Phase 1: ROADMAP khớp thực tế, test end-to-end Auth+Idempotency+RateLimit, fix flaky JwtServiceTest"
last_updated: "2026-08-23T09:47:04.238Z"
last_activity: 2026-08-23
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 10
  completed_plans: 6
  percent: 60
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-08-22)

**Core value:** Mọi API tuân thủ 3 nguyên tắc bất biến — ghi giao dịch nhanh, AI chỉ đề xuất qua `ai_drafts` chờ duyệt, riêng tư mặc định kiểm tra quyền ngay trong câu SQL
**Current focus:** Phase 01 — nen-tang-xac-thuc

## Current Position

Phase: 2
Plan: Not started
Status: Ready to plan
Last activity: 2026-08-23

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**

- Total plans completed: 6
- Average duration: - min
- Total execution time: 0 giờ

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 6 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01 P01 | 32min | 2 tasks | 16 files |
| Phase 01-nen-tang-xac-thuc P02 | 52min | 2 tasks | 13 files |
| Phase 01-nen-tang-xac-thuc P03 | 35min | 2 tasks | 13 files |
| Phase 01 P04 | 46 | 2 tasks | 17 files |
| Phase 01 P05 | 28 | 2 tasks | 14 files |
| Phase 01 P06 | 24 | 2 tasks | 5 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Granularity coarse → 5 phase, gộp Auth vào Nền tảng, Wallet+Category chung 1 phase, Budget/Debt/Recurring/Goal/Report/JOB chung 1 phase
- [Roadmap]: Group đặt gần cuối (Phase 5, trước AI) vì là tầng quyền phủ lên wallet/transaction/budget đã ổn định — xem "Ghi chú về thứ tự phase" trong ROADMAP.md
- [Roadmap]: Testcontainers PostgreSQL bắt buộc từ Phase 3 (giao dịch) và dùng lại cho bộ test riêng tư nhóm ở Phase 5
- springboot3-dotenv phien ban thuc dung duoc la 5.1.0, khong phai 4.0.0 nhu RESEARCH.md ghi
- Khong tu import testcontainers-bom rieng - giu version 1.20.6 do Spring Boot 3.4.13 quan ly, vi Testcontainers 2.x doi ten artifact khong tuong thich nguoc
- Ep -Duser.timezone=UTC cho JVM chay app va test - may dev Windows bao zone ID Asia/Saigon khong duoc PostgreSQL JDBC chap nhan
- Cot Postgres CHAR(n) phai khai columnDefinition=bpchar(n) - Postgres bao physical type la bpchar khong phai char/varchar
- Ép `project.build.sourceEncoding=UTF-8` và `-Dfile.encoding=UTF-8` cho surefire/spring-boot-maven-plugin — JVM Windows đọc Windows-1252 mặc định làm hỏng mọi chuỗi tiếng Việt
- `GlobalExceptionHandlerTest` dùng `@WebMvcTest` với `excludeFilters` loại `SecurityConfig`/`JwtAuthFilter` — `@Configuration` bean không bị controllers-scoping lọc như `@Controller`
- `JwtServiceTest` bắt `JwtException` (lớp cha) thay vì chỉ `SignatureException` cho test algorithm-confusion — jjwt 0.13.x có thể ném `WeakKeyException` hoặc `SignatureException` tuỳ đường verify
- Tách IdempotencyTransactionHelper thành bean riêng vì self-invocation trong @Aspect bean không đi qua Spring AOP proxy, khiến @Transactional bị bỏ qua âm thầm nếu gọi method nội bộ cùng class
- GlobalExceptionHandlerTest phải excludeFilters thêm RateLimitFilter khỏi @WebMvcTest — mọi @Component mới trong common/ có nguy cơ bị @WebMvcTest tự nạp nhầm vào context slice test
- Test Testcontainers cho idempotency_keys phải insert user thật qua JdbcTemplate trước — FK fk_idem_user ràng buộc user_id, UserRepository thật chưa tồn tại tới Plan 04
- Thêm @Transactional(noRollbackFor = BusinessException.class) cho login()/refresh() — rollback mặc định xoá bằng chứng login_attempts/revoke cùng lúc với exception, phá vỡ lockout và reuse detection
- FilterAutoRegistrationConfig tắt Spring Boot tự đăng ký Filter bean vào servlet container — JwtAuthFilter/RateLimitFilter từng chạy 2 lần/request, rate limit tiêu 2 token/request
- Thêm spring.jackson.property-naming-strategy=SNAKE_CASE toàn cục vào application.yml — JSON field đúng snake_case theo api/00-QUY-UOC-CHUNG.md cho mọi DTO hiện tại/tương lai
- LogoutRequest suy luận thêm field refreshToken ngoài logout_all_devices — api/01 mục 4 không đặc tả rõ trường mang token, dùng cùng cấu trúc refresh_token như /auth/refresh
- changePassword thu hoi TOAN BO refresh token (khong co ngoai le phien hien tai) vi request khong mang refresh token nao de giu lai - D-24
- forgotPassword luon return void ca 2 nhanh, khong throw phan biet - response giong het nhau chong do email (T-05-01)
- GET /auth/me dung JdbcTemplate native COUNT cho transactions/group_members - Phase 1 chua co repository nghiep vu rieng, ngoai le hop ly cho toi khi module do ton tai
- ROADMAP.md Phase 1 cap nhat khop thuc te D-08 - khong con mo ta sai viet V6/V7 truoc khi viet code Java, 6/6 plan Complete
- CORE-05/06/08 xac nhan chua co be mat kiem chung o Phase 1 (khong multi-user/DELETE/amount), CORE-07 da hoan thanh (Instant khop TIMESTAMPTZ) - verify bang grep that
- Test end-to-end de nguyen RateLimitFilter that (khong no-op nhu cac test truoc) - verify JwtAuthFilter->RateLimitFilter->IdempotencyAspect->AuthController phoi hop dung qua HTTP that
- Sua JwtServiceTest flaky bang cach dao bit byte GIUA mang signature da giai ma base64url thay vi doi ky tu cuoi chuoi ma hoa (co the roi vao padding, la no-op)

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

None yet.

### Blockers/Concerns

[Issues that affect future work]

- Hai điểm rủi ro nghiệp vụ cao nhất cần research sâu hơn khi lập plan chi tiết: transaction 3-bước sửa/xoá (Phase 3) và quyền riêng tư nhóm gia đình (Phase 5)
- **Phase 1 phải viết migration V6/V7 trước khi viết code Java** (CORE-09/CORE-10) — V1–V5 chỉ có 15 bảng, thiếu `refresh_tokens`/`idempotency_keys`/`password_reset_tokens`/`login_attempts`. Không có V7 thì AUTH-03 chết ngay task đầu
- **`v_budget_progress` có lỗ hổng quyền chưa vá** (thiếu điều kiện phạm vi người dùng, cộng chi tiêu của mọi user khi ngân sách không chỉ định ví) — CORE-09 vá, BUDGET-08 test chốt ở Phase 4
- **ROADMAP Phase 1 đã lỗi thời:** vẫn ghi "phải viết V6/V7 trước khi viết code Java", nhưng V6/V7 ĐÃ tồn tại trong `db/migration/` (commit 533e95b). Task đầu của phase là verify bằng cách chạy thật, và planner phải sửa lại mô tả trong ROADMAP (xem D-06/D-07/D-08)
- **Cần thêm mã lỗi mới `REQUEST_IN_PROGRESS` (409) vào `api/00-QUY-UOC-CHUNG.md` mục 6** — đặc tả hiện thiếu mã cho tình huống Idempotency-Key trùng khi lần đầu đang xử lý (xem D-14). Sửa trong Phase 1
- **Hai chỗ tài liệu còn sai về múi giờ báo cáo** (`api/00` mục 13 và `source/server/CLAUDE.md` quy tắc 7 vẫn ghi `AT TIME ZONE`) — mâu thuẫn CORE-07/REPORT-01. Thuộc Phase 4, đã ghi vào `<deferred>` của 01-CONTEXT.md
- **Ranh giới trigger dễ hiểu sai:** "không viết trigger" chỉ áp dụng cho số dư ví. `paid_amount`/`saved_amount`/`status` của debt/goal do trigger V4 sở hữu, backend không ghi — xem chi tiết ở Phase 4 trong ROADMAP.md

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v2 | FUTURE-01 đến FUTURE-06 (đa tiền tệ, AI thật, bill splitting, audit trail, multi-attachment, label) | Deferred | Định nghĩa requirements ban đầu |

## Session Continuity

Last session: 2026-08-23T02:05:36.108Z
Stopped at: Hoàn thành 01-06-PLAN.md - đóng Phase 1: ROADMAP khớp thực tế, test end-to-end Auth+Idempotency+RateLimit, fix flaky JwtServiceTest
Resume file: None

**Planned Phase:** 02 (vi-danh-muc) — 4 plans — 2026-08-23T09:47:04.225Z
