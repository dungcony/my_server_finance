# Roadmap: Backend Quản lý tài chính cá nhân có AI

## Overview

Backend Spring Boot (Java 17, Maven, PostgreSQL, Flyway) hiện thực hoá đúng theo đặc tả `api/*.md` và schema `THIET-KE-CSDL.md` đã chốt sẵn. Hành trình đi từ nền tảng kỹ thuật dùng chung (response format, JWT, idempotency, rate limit) → dữ liệu lõi ít phụ thuộc (ví, danh mục) → module rủi ro cao nhất là giao dịch (mọi cập nhật số dư đi qua đây) → các module đọc/sinh dữ liệu phụ thuộc giao dịch (ngân sách, báo cáo, sổ nợ, định kỳ, mục tiêu, các background job liên quan) → cuối cùng là tầng quyền phức tạp nhất (nhóm gia đình) và trợ lý AI rule-based (phụ thuộc giao dịch đã ổn định). Granularity coarse (5 phase) gộp các bước kỹ thuật liên quan chặt để mỗi phase là một cột mốc chạy được, kiểm thử được độc lập.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 1: Nền tảng & Xác thực** - Khung project, response/error format, idempotency, rate limit, JWT auth đầy đủ vòng đời tài khoản
- [ ] **Phase 2: Ví & Danh mục** - CRUD ví/chuyển tiền/đối chiếu số dư và cây danh mục 2 cấp + kho icon
- [ ] **Phase 3: Giao dịch** - CRUD/bulk/sửa-xoá đúng 3 bước, cộng gộp danh mục con — module lõi rủi ro cao nhất
- [ ] **Phase 4: Nghiệp vụ phái sinh & Báo cáo** - Ngân sách, sổ nợ, định kỳ, mục tiêu tiết kiệm, báo cáo, và toàn bộ background jobs liên quan
- [ ] **Phase 5: Nhóm gia đình & Trợ lý AI** - Tầng quyền chia sẻ ví/ngân sách nhóm với riêng tư tuyệt đối, và AI rule-based (parse-text/OCR mẫu) qua ai_drafts

## Phase Details

### Phase 1: Nền tảng & Xác thực
**Goal**: Có một backend Spring Boot chạy được, mọi response tuân thủ format chung, có thể đăng ký/đăng nhập/làm mới phiên an toàn, và có sẵn hạ tầng idempotency + rate limit cho mọi phase sau dùng lại.
**Depends on**: Nothing (first phase)
**Requirements**: CORE-01, CORE-02, CORE-03, CORE-04, CORE-05, CORE-06, CORE-07, CORE-08, CORE-09, CORE-10, CORE-11, AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-08
**Kỹ thuật (từ SUMMARY.md)**:
  - Cấu trúc package-by-feature (`auth/`, `common/`, `scheduler/` khởi tạo rỗng cho sau)
  - `common/`: response wrapper `{success, data}`/`{success, error}`, `@RestControllerAdvice` convert `MethodArgumentNotValidException` → `error.fields`, message tiếng Việt qua `messages_vi.properties`
  - JWT: `io.jsonwebtoken:jjwt` 0.13.x, access token stateless (chỉ `sub`/`plan`/`exp`), refresh token lưu bảng `refresh_tokens` băm SHA-256, rotation + reuse detection (thu hồi toàn bộ phiên)
  - `SecurityFilterChain` STATELESS, `OncePerRequestFilter` tuỳ biến cho JWT
  - Idempotency: bảng DB `idempotency_keys`, `INSERT ... ON CONFLICT DO NOTHING`, áp dụng qua `HandlerInterceptor`/AOP `@Idempotent`
  - Rate limit: Bucket4j in-memory + Caffeine cache, filter đặt sau filter JWT, theo IP (auth) hoặc `user_id` (còn lại)
  - `ddl-auto=validate`, Flyway sở hữu schema, trỏ tới `db/migration/` gốc (không copy)
  - **Việc đầu tiên của phase — verify migration đã có, không viết mới:** `V6__va_loi_bao_mat.sql` (CORE-09) và `V7__ha_tang_xac_thuc.sql` (CORE-10) đã tồn tại sẵn trong `db/migration/` (commit 533e95b) trước khi Phase 1 bắt đầu lập kế hoạch chi tiết. Việc đầu tiên là dựng project trỏ Flyway vào `db/migration/` gốc và chạy thật V1→V7 trên PostgreSQL sạch để xác nhận không lỗi checksum/thứ tự, đúng cột/kiểu dữ liệu cho 4 bảng hạ tầng Auth/Idempotency — không phải viết migration mới (đã xác nhận xanh qua `01-01-SUMMARY.md`, cả `mvn spring-boot:run` lẫn Testcontainers)
  - **Ranh giới trigger:** quy tắc thật là *không trigger cho số dư ví*. Schema đã có sẵn 6 trigger, trong đó `trg_debt_payments_sync`/`trg_goal_contributions_sync` (V4) **sở hữu** `paid_amount`/`saved_amount`/`status` — liên quan tới Phase 4, không phải phase này, nhưng ghi ở đây để không diễn đạt sai lan sang các phase sau
**Success Criteria** (what must be TRUE):
  1. `V6` và `V7` chạy sạch trên PostgreSQL: `v_budget_progress` đã có điều kiện phạm vi người dùng, `fn_category_tree` lọc `is_deleted`, `groups` có cột hạn mã mời, và 4 bảng hạ tầng xác thực tồn tại
  2. Gọi bất kỳ endpoint nào cũng nhận response đúng khung `{success, data}` hoặc `{success, error}`, lỗi validate nhiều trường trả hết một lượt trong `error.fields`
  3. User đăng ký bằng email/password nhận được access + refresh token, và một ví "Tiền mặt" số dư 0 được tạo sẵn
  4. User đăng nhập sai mật khẩu 5 lần liên tiếp bị khoá 15 phút; refresh token dùng lại sau khi đã dùng một lần sẽ thu hồi toàn bộ phiên
  5. Gọi lại cùng một request POST kèm `Idempotency-Key` trong 24h trả lại đúng kết quả lần đầu, không tạo bản ghi trùng
  6. Gọi endpoint auth quá 5 lần/phút/IP (hoặc endpoint thường quá 120 lần/phút/user) bị chặn kèm header `X-RateLimit-*`
**Plans**:
- [x] 01-01-PLAN.md — Khung Maven, cấu hình môi trường, verify migration V1-V7, entity tối thiểu
- [x] 01-02-PLAN.md — common/ (response wrapper, exception handler, JWT service, SecurityConfig)
- [x] 01-03-PLAN.md — Idempotency AOP + Rate limiting 3 tầng
- [x] 01-04-PLAN.md — Auth lõi: register/login/refresh/logout
- [x] 01-05-PLAN.md — Auth còn lại: profile, đổi/quên/đặt lại mật khẩu, test khoá đăng nhập
- [x] 01-06-PLAN.md — Đóng phase: cập nhật ROADMAP, test end-to-end, verify toàn bộ suite

### Phase 2: Ví & Danh mục
**Goal**: User quản lý đầy đủ ví tiền (kể cả chuyển tiền giữa ví, đối chiếu số dư) và cây danh mục 2 cấp gắn icon, làm nền cho module giao dịch ở phase sau.
**Depends on**: Phase 1
**Requirements**: WALLET-01, WALLET-02, WALLET-03, WALLET-04, WALLET-05, WALLET-06, WALLET-07, CAT-01, CAT-02, CAT-03, CAT-04, CAT-05, CAT-06
**Kỹ thuật (từ SUMMARY.md)**:
  - JPA làm chủ đạo cho CRUD ví/danh mục; native SQL/JdbcTemplate cho `UPDATE ... SET balance = balance + :delta` (atomic update, tránh lost-update thay vì load-modify-save)
  - Row-level lock (`PESSIMISTIC_WRITE`) trên ví khi chuyển tiền
  - Gọi hàm SQL có sẵn `fn_category_tree` (qua repository method dùng chung) để chuẩn bị cho cộng gộp danh mục con ở các phase sau
  - MapStruct cho Entity ↔ DTO
  - CORE-05 (kiểm tra quyền trong SQL, trả 404) áp dụng ngay từ đây cho mọi query ví/danh mục
**Success Criteria** (what must be TRUE):
  1. User tạo/sửa/xoá (mềm)/sắp xếp ví; không sửa được `current_balance`/`type` qua PATCH; không xoá được ví cuối cùng hoặc ví còn giao dịch nếu chưa xác nhận `delete_transactions`
  2. User chuyển tiền giữa 2 ví trong một giao dịch DB tạo đúng một bản ghi `type=transfer`, mặc định cho phép chuyển dù thiếu số dư trừ khi `fail_if_insufficient=true`
  3. Gọi API đối chiếu số dư ví trả đúng kết quả theo công thức `initial_balance + thu - chi - chuyển đi + chuyển đến`, ghi log khi lệch
  4. User tạo danh mục cha/con tối đa 2 tầng, con cùng `type` với cha; không xoá được danh mục còn con/giao dịch/ngân sách đang dùng, danh mục hệ thống không sửa/xoá được
  5. User xem danh sách nhóm lớn và kho icon lọc theo `icon_group`/`search`
**Plans**: TBD

### Phase 3: Giao dịch
**Goal**: User ghi nhận, sửa, xoá, nhân bản giao dịch expense/income/transfer với số dư ví luôn chính xác tuyệt đối kể cả trong các thao tác sửa/xoá phức tạp — đây là module lõi mà mọi phase sau phụ thuộc.
**Depends on**: Phase 2
**Requirements**: TXN-01, TXN-02, TXN-03, TXN-04, TXN-05, TXN-06, TXN-07, TXN-08
**Kỹ thuật (từ SUMMARY.md)**:
  - **Testcontainers PostgreSQL bắt buộc** ngay từ phase này (không H2, vì cần constraint/hàm SQL thật của schema V1-V5)
  - Ưu tiên test cao nhất: atomicity 3 bước sửa giao dịch (hoàn tác cũ → ghi mới → áp dụng mới) trong 1 `@Transactional`, tránh transaction self-invocation làm mất `@Transactional` (tách Service riêng)
  - Atomic UPDATE số dư ví (không load-modify-save), pessimistic lock khi cần
  - `fn_category_tree` bọc thành 1 repository method dùng chung cho TXN-08 (cộng gộp danh mục con) — tái dùng lại ở Budget/Report sau này
  - `@EntityGraph`/`JOIN FETCH` hoặc DTO projection để tránh N+1 khi list giao dịch kèm category/wallet/icon
  - Index `(user_id,date)`, `(wallet_id,date)`, `(category_id,date)` — verify đã có trong migration thật
  - `amount` luôn kiểu `Long`, không bao giờ `Double`/`float`
**Success Criteria** (what must be TRUE):
  1. User tạo giao dịch expense/income/transfer đúng validate theo `type` (category bắt buộc/rỗng, destination bắt buộc/rỗng), không cho ngày tương lai, số dư ví cập nhật đúng trong cùng 1 DB transaction
  2. User sửa (PUT) một giao dịch đổi cả số tiền lẫn ví — số dư ví cũ và ví mới đều đúng tuyệt đối, không có trường hợp cộng/trừ chênh lệch sai
  3. User xoá (mềm) một giao dịch hoàn tác đúng ảnh hưởng lên số dư ví; gọi xoá lại (idempotent) vẫn trả 200
  4. User tạo bulk tối đa 50 giao dịch, dòng lỗi bị bỏ qua và báo trong `row_errors` mà không từ chối toàn bộ lô
  5. Lọc/xem tổng `summary` theo danh mục cha tự động cộng gộp giao dịch của mọi danh mục con, loại trừ giao dịch `transfer`
**Plans**: TBD

### Phase 4: Nghiệp vụ phái sinh & Báo cáo
**Goal**: User quản lý ngân sách, sổ nợ, giao dịch định kỳ, mục tiêu tiết kiệm và xem báo cáo thống kê — tất cả xây trên nền giao dịch đã ổn định ở Phase 3; các background job hằng ngày giữ dữ liệu nhất quán mà không cần người dùng can thiệp.
**Depends on**: Phase 3
**Requirements**: BUDGET-01, BUDGET-02, BUDGET-03, BUDGET-04, BUDGET-05, BUDGET-06, BUDGET-07, BUDGET-08, DEBT-01, DEBT-02, DEBT-03, DEBT-04, DEBT-05, DEBT-06, DEBT-07, RECUR-01, RECUR-02, RECUR-03, GOAL-01, GOAL-02, GOAL-03, GOAL-04, REPORT-01, REPORT-02, REPORT-03, REPORT-04, REPORT-05, JOB-01, JOB-02, JOB-03, JOB-04
**Kỹ thuật (từ SUMMARY.md)**:
  - Ngân sách: tính động lúc gọi API (không lưu `spent_amount`), map view `v_budget_progress` qua native query/DTO, dùng lại `fn_category_tree`
  - Sổ nợ/định kỳ/mục tiêu: tạo/huỷ giao dịch thật tái sử dụng đúng logic 3-bước và atomic update số dư đã xây ở Phase 3 (gọi lại Service giao dịch, không viết lại logic cập nhật ví)
  - **Ranh giới trigger — đọc kỹ trước khi code debt/goal:** `debts.paid_amount`, `debts.status`, `savings_goals.saved_amount`, `savings_goals.status` do trigger `trg_debt_payments_sync`/`trg_goal_contributions_sync` (V4) sở hữu. Backend **chỉ** chèn/xoá bản ghi `debt_payments`/`goal_contributions`, **tuyệt đối không ghi 4 cột trên**. Trigger tính lại bằng `SUM()` nên nếu backend cũng ghi thì giá trị backend bị ghi đè — chạy "đúng một cách tình cờ", rất khó hiểu khi debug sau này
  - **Tránh nhầm:** quy tắc "không viết trigger CSDL" chỉ áp dụng cho **số dư ví** (`wallets.current_balance`) — số dư ví do backend chủ động cập nhật trong transaction. Debt/goal thì ngược lại
  - RECUR-03: chống trùng bằng UNIQUE `(recurring_id, date)` ở DB (đã có trong schema), xử lý ngày 29/30/31 không tồn tại, bắt kịp kỳ bị bỏ lỡ
  - `@Scheduled` với `zone = "Asia/Ho_Chi_Minh"` trực tiếp trong cron annotation cho toàn bộ job của phase này (đối chiếu số dư, lặp ngân sách, sinh định kỳ, nhắc nợ); mỗi job bọc try/catch để không crash scheduler pool
  - Báo cáo: query fragment dùng chung `excludeTransfer()`, gom nhóm theo ngày/tháng dùng thẳng `transactions.date` (kiểu `DATE`, **không có múi giờ — không chuyển đổi gì cả**; đây là thiết kế cố ý tránh hẳn lớp bug múi giờ), chú ý N+1
  - REPORT-05 (xuất file async): trả 202 + `job_id`, poll trạng thái — cân nhắc dùng lại cơ chế job tương tự `@Scheduled`/`CompletableFuture` cho tác vụ nền, không cần Quartz
**Success Criteria** (what must be TRUE):
  1. User xem tiến độ ngân sách tính động (đúng 3 trạng thái normal/near_limit/over_limit, cộng gộp danh mục con) và nhận gợi ý hạn mức AI dựa trên trung bình 3 kỳ gần nhất
  2. User tạo khoản nợ sinh đúng một giao dịch thật cập nhật số dư ví; ghi một lần trả nợ cộng dồn `paid_amount` và tự chuyển `status=settled` khi trả đủ; huỷ lần trả nợ hoàn tác đúng
  3. Khoản định kỳ tự sinh giao dịch đúng ngày đến hạn kể cả ngày 29/30/31, không sinh trùng khi job chạy lại, và bắt kịp các kỳ bị bỏ lỡ nếu user vắng mặt lâu
  4. User nạp/rút tiền mục tiêu tiết kiệm đúng theo 2 chế độ (chuyển tiền thật hoặc chỉ ghi nhận), tự chuyển trạng thái khi đạt/vượt target
  5. Báo cáo tổng quan/theo kỳ/xu hướng loại trừ hoàn toàn giao dịch transfer và cộng gộp danh mục con; xuất báo cáo trả 202 kèm `job_id` và có thể poll tới khi có link tải
  6. Các job nền (đối chiếu số dư, lặp ngân sách, sinh định kỳ, nhắc nợ) tự chạy hằng ngày theo lịch mà không cần gọi API thủ công
  7. Test riêng tư ngân sách (BUDGET-08) chạy xanh: B chi tiền vào danh mục X, ngân sách cá nhân của A cùng danh mục vẫn hiện `spent_amount = 0` — chốt lỗ hổng `v_budget_progress` đã vá ở CORE-09
**Plans**: TBD

### Phase 5: Nhóm gia đình & Trợ lý AI
**Goal**: User chia sẻ ví/ngân sách trong nhóm gia đình với riêng tư cá nhân tuyệt đối được kiểm chứng bằng test tự động, và có thể dùng trợ lý AI rule-based để nhập nhanh giao dịch qua câu nói hoặc ảnh hoá đơn, luôn qua bước duyệt của người dùng.
**Depends on**: Phase 4
**Requirements**: GROUP-01, GROUP-02, GROUP-03, GROUP-04, GROUP-05, GROUP-06, AI-01, AI-02, AI-03, AI-04, AI-05, AI-06, JOB-05
**Kỹ thuật (từ SUMMARY.md)**:
  - Group thêm tầng quyền phức tạp lên mọi module đã xây (wallet/transaction/budget) — điều kiện quyền `w.user_id = current_user OR w.group_id IN (nhóm active)` phải nằm ngay trong JPQL/native query, không lấy hết rồi lọc ở code
  - **Testcontainers bắt buộc** cho bộ test riêng tư nhóm: xác nhận user A không đọc được bất kỳ dữ liệu cá nhân nào của user B kể cả khi B là owner nhóm chung
  - Không có quyền → 404 (không phải 403), trừ khi biết chắc tài nguyên tồn tại nhưng thiếu vai trò trong nhóm (ví dụ owner-only action)
  - AI: rule-based parse-text bằng từ khoá (không gọi model ngoài), OCR dùng dữ liệu mẫu cố định `engine=sample`, hợp đồng response giữ nguyên để thay model thật sau (FUTURE-02)
  - Mọi kết quả AI ghi vào `ai_drafts` với `status=pending`, không bao giờ tạo thẳng `transactions`; khi duyệt (approve/approve-bulk) mới gọi lại Service giao dịch của Phase 3 để tạo giao dịch thật + cập nhật số dư, đồng thời ghi `user_corrections`
  - JOB-05 dọn `ai_drafts` discarded sau 90 ngày dùng lại cơ chế `@Scheduled` của Phase 4
**Success Criteria** (what must be TRUE):
  1. User tạo nhóm (tối đa 5 nhóm/người), mời thành viên qua mã (hạn 7 ngày, tối đa 10 người/nhóm) và tham gia bằng mã mời
  2. Owner chuyển quyền chủ nhóm hoặc xoá nhóm (chọn `transfer_to_owner`/`delete_all` cho ví chung); thành viên tự rời nhóm giữ nguyên lịch sử giao dịch trên ví chung
  3. Bộ test tự động xác nhận: user A gọi bất kỳ API ví/giao dịch/ngân sách/nợ/mục tiêu cá nhân của user B đều nhận 404, kể cả khi A là owner chung nhóm với B
  4. User gửi một câu tiếng Việt hoặc ảnh hoá đơn nhận về các bản nháp trong `ai_drafts` với `status=pending` kèm mức tin cậy — không có giao dịch thật nào được tạo cho tới khi duyệt
  5. User duyệt (approve/approve-bulk tối đa 20) bản nháp tạo đúng giao dịch thật, cập nhật số dư ví, và ghi lại `user_corrections` nếu người dùng có sửa trước khi duyệt
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Nền tảng & Xác thực | 6/6 | Complete | 2026-08-23 |
| 2. Ví & Danh mục | 0/TBD | Not started | - |
| 3. Giao dịch | 0/TBD | Not started | - |
| 4. Nghiệp vụ phái sinh & Báo cáo | 0/TBD | Not started | - |
| 5. Nhóm gia đình & Trợ lý AI | 0/TBD | Not started | - |

## Ghi chú về thứ tự phase

Gợi ý ban đầu trong `research/SUMMARY.md` chia 8 bước theo phụ thuộc kỹ thuật thuần tuý (transaction, budget, debt/recurring/goal, report, group, ai tách riêng). Với granularity coarse, roadmap này gộp:
- Bước 1 (Nền tảng) + Auth → **Phase 1** (cùng là hạ tầng dùng chung, không phụ thuộc dữ liệu nghiệp vụ)
- Bước 2 (Wallet + Category) → **Phase 2** (cùng là dữ liệu lõi ít phụ thuộc, cả hai đều là điều kiện tiên quyết của giao dịch)
- Bước 3 (Transaction) → **Phase 3** giữ riêng, không gộp, vì đây là module rủi ro cao nhất và mọi phase sau đều gọi lại logic của nó
- Bước 4 (Budget) + 5 (Debt/Recurring/Goal) + 6 (Report) + phần lớn JOB → **Phase 4** (tất cả đọc/ghi dựa trên giao dịch đã ổn định, đều dùng chung mẫu `@Scheduled`/`fn_category_tree`/`excludeTransfer()`, tách riêng thành phase nhỏ hơn sẽ vụn vặt so với granularity coarse)
- Bước 7 (Group) + 8 (AI) → **Phase 5**, đặt **Group trước AI trong cùng phase, và cả hai đứng cuối roadmap**

**Lý do Group đứng gần cuối (không đứng sớm hơn):** Group không phải là dữ liệu độc lập — nó là một **tầng quyền phủ lên** wallet, transaction, budget đã xây ở các phase trước (ví chung, ngân sách chung). Nếu làm Group trước khi các module đó ổn định, sẽ phải sửa lại điều kiện quyền trong query của từng module nhiều lần khi API còn thay đổi. Làm Group sau khi wallet/transaction/budget đã hoàn thiện và có test bao phủ giúp việc thêm điều kiện `OR w.group_id IN (...)` vào các query hiện có là một thay đổi có kiểm soát, dễ viết test hồi quy riêng tư (rủi ro nghiệp vụ cao nhất của toàn dự án theo PROJECT.md).

**Lý do AI đứng sau Group (không đứng trước):** AI-04 (duyệt bản nháp) gọi lại Service giao dịch để tạo giao dịch thật — nếu giao dịch khi đó đã hỗ trợ ví chung nhóm (Phase 5 trước AI trong cùng phase), AI có thể duyệt bản nháp vào đúng ví cá nhân hoặc ví chung mà không cần sửa lại luồng duyệt sau này. Ngoài ra AI không có phụ thuộc dữ liệu kỹ thuật vào Group, nên thứ tự Group → AI trong cùng Phase 5 là tối ưu về rủi ro tích hợp, không phải bắt buộc tuyệt đối.

---
*Roadmap created: 2026-08-22*
