---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Phase 4 planned (7 plans)
last_updated: "2026-08-25T14:50:02.480Z"
last_activity: 2026-08-24
progress:
  total_phases: 5
  completed_phases: 3
  total_plans: 21
  completed_plans: 14
  percent: 67
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-08-22)

**Core value:** Mọi API tuân thủ 3 nguyên tắc bất biến — ghi giao dịch nhanh, AI chỉ đề xuất qua `ai_drafts` chờ duyệt, riêng tư mặc định kiểm tra quyền ngay trong câu SQL
**Current focus:** Phase 03 — giao-dich

## Current Position

Phase: 4
Plan: 04-01 → 04-07 đã lập kế hoạch, chưa thực thi
Status: Ready to execute
Last activity: 2026-08-25

Progress: [█████████░] 86%

## Performance Metrics

**Velocity:**

- Total plans completed: 14
- Average duration: - min
- Total execution time: 0 giờ

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 6 | - | - |
| 02 | 4 | - | - |
| 03 | 4 | - | - |

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
| Phase 02-vi-danh-muc P01 | 7min | 2 tasks | 8 files |
| Phase 02 P02 | 40min | 2 tasks | 13 files |
| Phase 02-vi-danh-muc P03 | 35min | 2 tasks | 19 files |
| Phase 02-vi-danh-muc P04 | 55min | 2 tasks | 11 files |
| Phase 03-giao-dich P01 | 14min | 2 tasks | 10 files |
| Phase 03-giao-dich P02 | 45min | 3 tasks | 14 files |

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
- Copy nguyên trạng WalletMinimal sang Wallet, giữ columnDefinition=bpchar(7); không thêm field mới ở plan 02-01
- WalletRepository chỉ giữ 1 method tối thiểu ở plan 02-01, mở rộng CRUD/quyền vào cùng file ở plan 02-02, không tạo repository mới
- Điều kiện quyền D-27 dùng group_members.is_active (BOOLEAN), KHÔNG phải status = 'active' — cột status không tồn tại trong db/migration/V1__nen_tang.sql, PLAN/CONTEXT Phase 2 mô tả sai theo mẫu chưa đối chiếu schema thật
- DELETE /wallets/{id} dùng findByIdForUserIncludingDeleted (không lọc is_deleted) để phân biệt 404 với no-op 200 idempotent theo CORE-06
- @WebMvcTest slice test (GlobalExceptionHandlerTest) phải liệt kê tường minh mọi Controller nghiệp vụ mới vào excludeFilters, nếu không Spring component-scan kéo controller vào context thiếu Service bean
- Danh mục KHÔNG áp dụng vế nhóm gia đình của D-27 - categories.user_id không liên kết group_id trong schema V1 thật (khác wallets); danh mục hệ thống user_id IS NULL luôn hiển thị cho mọi người theo api/03-DANH-MUC.md muc 9
- has_budget trong CategoryDetailResponse.stats va CATEGORY_HAS_BUDGET luon bo qua o Phase 2 - bang budgets chua co service, TODO Phase 4 noi that
- Tách WalletTransferService riêng khỏi WalletService để tránh transaction self-invocation - nhóm transfer/adjust-balance/reconcile đều thao tác trực tiếp current_balance qua lock + atomic UPDATE
- Khoá 2 ví theo UUID.compareTo() cố định (nhỏ trước, lớn sau) bất kể vai trò nguồn/đích trước khi SELECT FOR UPDATE - chống deadlock A->B/B->A đồng thời
- POST /wallets/{id}/reconcile khong gan @Idempotent - la hanh dong do loi phai luon tinh lai, khong duoc cache ket qua cu
- TransactionWriter đọc số dư mới bằng native SQL (findCurrentBalanceNative) thay vì JPQL findByIdForUpdate - tránh Hibernate identity map trả instance cache cũ khi caller đã load ví cùng transaction truoc do
- TransactionWriter là @Component (bean hạ tầng D-31), không phải @Service - một method @Transactional duy nhất, dùng chung cho TransactionService/WalletTransferService/Phase 4
- @JsonInclude(NON_NULL) đặt trực tiếp trên WalletResponse/WalletDetailResponse (không cấu hình Jackson toàn cục) để bỏ hẳn projected_balance khi null theo D-37
- DESTINATION_WALLET_NOT_ALLOWED là mã lỗi mới (400), đối xứng CATEGORY_NOT_ALLOWED - vá lỗ hổng tài liệu chưa từng mô tả ca chi/thu kèm ví đích dù ck_txn_shape (V2) đã chặn cứng ở CSDL
- update()/delete() giao dịch gọi trực tiếp TransactionRepository.save() + WalletRepository.adjustBalance() trong CÙNG một @Transactional, không qua TransactionWriter - writer chỉ INSERT bản ghi mới, không phù hợp sửa tại chỗ
- validateShape() để package-private (không private, không public) trong TransactionService - chuẩn bị điểm nối cho TransactionBulkService (plan 03-04) gọi lại đúng logic validate từng dòng bulk

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

Last session: --stopped-at
Stopped at: Phase 4 context gathered
Resume file: --resume-file

**Planned Phase:** 03 (giao-dich) — 4 plans — 2026-08-23T15:19:49.096Z
