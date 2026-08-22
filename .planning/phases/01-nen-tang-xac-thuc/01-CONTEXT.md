# Phase 1: Nền tảng & Xác thực - Context

**Gathered:** 2026-08-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Khung project Spring Boot chạy được, mọi response tuân thủ format chung `{success, data}`/`{success, error}`, đăng ký/đăng nhập/làm mới phiên an toàn đầy đủ vòng đời tài khoản, và hạ tầng idempotency + rate limit dùng lại được cho mọi phase sau.

**Không thuộc phase này:** bất kỳ nghiệp vụ tài chính nào (ví, danh mục, giao dịch, ngân sách…). Package `wallet/`, `category/`, `transaction/`… chưa tạo — ngoại lệ duy nhất là ví "Tiền mặt" tạo lúc đăng ký (AUTH-01), viết tối thiểu ngay trong `auth/` hoặc một service dùng chung nhỏ, không dựng cả module `wallet/`.

</domain>

<decisions>
## Implementation Decisions

### Cấu hình môi trường & bí mật

- **D-01:** JWT secret và thông tin kết nối DB nạp qua **biến môi trường bắt buộc, không có giá trị mặc định**. App fail-fast khi thiếu — không được viết `${JWT_SECRET:default-nao-do}`.
- **D-02:** Nạp biến bằng **file `.env` bị gitignore**, kèm **`.env.example` commit vào repo** làm mẫu. `docker-compose.yml` và Spring Boot cùng đọc file này (Spring dùng `spring-dotenv` hoặc cơ chế tương đương).
- **D-03:** Chia **2 profile: `dev` + `test`**. `application.yml` giữ phần chung, `application-dev.yml` trỏ Postgres local qua Docker, `application-test.yml` cho Testcontainers. **Chưa tạo `prod`** — thêm sau nếu cần deploy.
- **D-04:** Postgres cho dev dựng bằng **Docker Compose** (thêm `docker-compose.yml` vào `source/server/`).
- **D-05:** **Hệ quả bắt buộc của D-01+D-04:** vì secret không có default và DB chạy trong container, lần chạy đầu sẽ fail-fast nếu chưa chuẩn bị. Phase 1 **phải** giao kèm `source/server/README.md` mô tả đúng trình tự khởi động (copy `.env.example` sang `.env`, `docker compose up -d`, rồi `mvn spring-boot:run`). Không có tài liệu này thì tiêu chí "backend chạy được" chưa đạt.

### Migration (CORE-09 / CORE-10)

- **D-06:** **`V6__va_loi_bao_mat.sql` và `V7__ha_tang_xac_thuc.sql` ĐÃ tồn tại** trong `db/migration/` (commit 533e95b) — trái với mô tả trong ROADMAP.md ("phải viết V6/V7 trước"). 4 bảng hạ tầng (`refresh_tokens`, `idempotency_keys`, `password_reset_tokens`, `login_attempts`) đã có, cột khớp yêu cầu CORE-10.
- **D-07:** Task đầu tiên của phase là **verify bằng cách chạy thật**: dựng project + trỏ Flyway vào `db/migration/` gốc, chạy `V1` đến `V7` trên một DB sạch. Lỗi phát sinh thì sửa tại chỗ. Không viết lại V6/V7 từ đầu.
- **D-08:** Planner phải **cập nhật ROADMAP.md Phase 1** để bỏ mô tả "viết migration trước khi viết code Java", thay bằng "verify migration V6/V7 đã có". Tài liệu và thực tế không được trôi dạt.
- **D-09:** Flyway trỏ vào `db/migration/` ở thư mục gốc DATN (`../../db/migration`), **không copy** vào `source/server/`. `ddl-auto=validate`.

### Idempotency (CORE-03)

- **D-10:** Áp qua **AOP với annotation `@Idempotent`** đặt tường minh lên từng method controller cần bảo vệ — không dùng HandlerInterceptor tự động cho mọi POST.
  **Lý do:** rủi ro "quên gắn annotation" nhẹ hơn rủi ro "quên loại trừ endpoint". Nếu áp tự động mà quên loại trừ `/auth/login`, lần đăng nhập thứ hai sẽ nhận lại **token cũ đã cache** — lỗi nguy hiểm hơn hẳn. Ngoài ra `api/00` viết "mọi endpoint POST **tạo mới**", tức đặc tả đã phân biệt POST-tạo-mới với POST-hành-động; annotation diễn đạt đúng sự phân biệt đó.
- **D-11:** **`/auth/login`, `/auth/refresh`, `/auth/logout` KHÔNG gắn `@Idempotent`.** Đây là POST-hành-động, không phải POST-tạo-mới.
- **D-12:** Luồng xử lý: tra `idempotency_keys` — chưa có thì `INSERT ... ON CONFLICT DO NOTHING` với `status='processing'`, chạy nghiệp vụ, lưu `response_status`/`response_body`, đổi `status='completed'`. Key trùng và `completed` thì trả lại `response_body` đã lưu, không chạy lại nghiệp vụ.
- **D-13:** Key trùng nhưng `status='processing'` (lần đầu chưa xong) → **trả 409 ngay**, không chờ, không chạy song song.
- **D-14:** Mã lỗi cho D-13 là **`REQUEST_IN_PROGRESS` (409) — mã MỚI, phải bổ sung vào bảng mã dùng chung ở `api/00-QUY-UOC-CHUNG.md` mục 6 trong cùng phase này.** Không tái dùng `DUPLICATE` vì app cần phân biệt: gặp `DUPLICATE` thì báo lỗi cho người dùng, gặp `REQUEST_IN_PROGRESS` thì im lặng retry sau vài giây.
- **D-15:** Nhớ key trong **24 giờ**, cần job dọn bản ghi quá hạn. Job này đặt trong `scheduler/` — package đó khởi tạo ở phase này (ROADMAP ghi "khởi tạo rỗng cho sau", nay có đúng 1 job thật).

### Rate limiting (CORE-04)

- **D-16:** Triển khai **đầy đủ cả 3 tầng ngay ở Phase 1**, dù phase này mới chỉ có endpoint auth:

  | Nhóm | Quota | Đếm theo |
  |---|---|---|
  | Auth (`/auth/**`) | 5/phút | **IP** (chưa đăng nhập nên chưa biết user) |
  | AI (`/ai/**`) | 30/phút | `user_id` |
  | Còn lại | 120/phút | `user_id` |

  **Lý do:** mục tiêu phase là "hạ tầng dùng lại cho mọi phase sau"; làm mỗi tầng auth thì Phase 2 phải mở lại filter. Chi phí chênh lệch chỉ là bảng cấu hình (nhóm endpoint → quota, chiều đếm) thay cho hằng số cứng.
- **D-17:** Bucket4j in-memory + Caffeine cache (tự evict bucket cũ). Không Redis — dự án chạy 1 instance.
- **D-18:** Filter rate limit đặt **sau** filter JWT, để tầng AI/thường lấy được `user_id` từ token.
- **D-19:** Header `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` trả trên **mọi response**, không chỉ khi bị chặn — khớp ví dụ `Remaining: 117` ở `api/00` (con số đó chỉ có nghĩa trên response thành công) và giúp app biết trước sắp hết quota.
- **D-20:** Vượt quota → HTTP `429` với mã `RATE_LIMIT_EXCEEDED` (đã có sẵn trong `api/00`).
- **D-21:** **Phân biệt rõ với AUTH-07 — hai cơ chế độc lập, đều phải có:** CORE-04 đếm *số request* từ *một IP* (chống spam nói chung); AUTH-07 đếm *lần sai mật khẩu* của *một tài khoản* qua bảng `login_attempts`, khoá 15 phút sau 5 lần (chống dò mật khẩu người cụ thể). Không cái nào thay thế cái nào.

### Claude's Discretion

Hai vùng người dùng không chọn bàn. Đề xuất mặc định dưới đây để planner **không phải tự đoán** — nếu planner thấy lý do đổi thì nêu rõ trong PLAN.

- **D-22 (Gửi email reset password — AUTH-06):** Không tài liệu nào của dự án nói dùng SMTP gì. Mặc định: **định nghĩa một interface `PasswordResetNotifier` với một implementation dev ghi mã reset ra log**, không tích hợp SMTP thật ở Phase 1. Lý do: đồ án chưa có hạ tầng email; endpoint `/auth/forgot-password` vẫn trả 200 đúng đặc tả và `/auth/reset-password` vẫn test được đầy đủ; thay implementation thật sau không đụng nghiệp vụ. **Nếu người dùng muốn SMTP thật (Gmail app password / Mailtrap) thì phải hỏi trước khi code.**
- **D-23 (Phạm vi test Phase 1):** ROADMAP nói Testcontainers bắt buộc *từ Phase 3*. Mặc định cho Phase 1: **dựng Testcontainers ngay** vì D-03 đã có profile `test` và các luồng cần kiểm chứng (refresh token rotation + reuse detection, idempotency `ON CONFLICT`, khoá 5 lần) đều **phụ thuộc hành vi Postgres thật** (unique constraint, `INSERT ... ON CONFLICT`) — mock không chứng minh được gì. Ưu tiên test: (1) reuse detection thu hồi toàn bộ phiên, (2) idempotency trả đúng kết quả lần đầu, (3) khoá đăng nhập 15 phút sau 5 lần sai.
- **D-24:** Chi tiết kỹ thuật còn lại (cấu trúc `common/`, cách tổ chức exception handler, MapStruct config, tên class cụ thể) theo `research/SUMMARY.md` và chuẩn Spring Boot — không cần hỏi lại.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Hợp đồng API (nguồn sự thật)
- `api/00-QUY-UOC-CHUNG.md` — **đọc trước tiên**. Mục 4 (khung response), mục 6 (bảng mã lỗi dùng chung — **cần thêm `REQUEST_IN_PROGRESS` theo D-14**), mục 10 (header `X-RateLimit-*`), mục 11 (Idempotency-Key), mục 7 (phân trang)
- `api/01-XAC-THUC.md` — 9 endpoint auth, dữ liệu gửi/nhận, mã lỗi riêng, mục "Ghi chú triển khai" (bcrypt hệ số ≥12, nội dung JWT, khoá 5 lần/15 phút, ghi log IP)

### Schema thật (đối chiếu bắt buộc — CORE-11)
- `db/migration/V7__ha_tang_xac_thuc.sql` — **4 bảng Phase 1 dùng trực tiếp**: `refresh_tokens`, `idempotency_keys`, `password_reset_tokens`, `login_attempts`. Đọc đúng tên cột trước khi viết entity
- `db/migration/V6__va_loi_bao_mat.sql` — vá `v_budget_progress`, `fn_category_tree`, cột hạn mã mời của `groups`. Phase 1 chỉ cần chạy sạch, chưa dùng tới
- `db/migration/V1__nen_tang.sql` — bảng `users`, `wallets` (cần cho AUTH-01/AUTH-05)
- `db/README.md` — mục "Trigger có sẵn"; lý do không viết trigger cho số dư ví

### Quyết định dự án
- `source/server/.planning/PROJECT.md` — constraints, Key Decisions
- `source/server/.planning/REQUIREMENTS.md` — CORE-01 đến CORE-11, AUTH-01 đến AUTH-08 (bản đầy đủ)
- `source/server/.planning/research/SUMMARY.md` — stack, dependency cho `pom.xml`, pitfall checklist. **Đọc kèm cảnh báo ở đầu file**: tài liệu này viết trước khi đối chiếu schema thật, đã có 1 khuyến nghị sai (múi giờ)
- `source/server/CLAUDE.md` — cấu trúc package-by-feature, 10 quy tắc bắt buộc. **Lưu ý quy tắc 7 đang sai — xem deferred**
- `CLAUDE.md` (gốc DATN) — 3 nguyên tắc bất biến, 8 quy tắc nghiệp vụ

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
**Không có.** `source/server/` hiện chỉ chứa `.planning/`, `CLAUDE.md`, `.git`. Chưa có `pom.xml`, chưa có một dòng Java nào. Phase 1 dựng project từ số không.

### Established Patterns
Chưa có pattern code nào để noi theo — Phase 1 **thiết lập** pattern cho 4 phase sau:
- Khung response `{success, data}` / `{success, error}` mà mọi controller phase sau dùng lại
- `@RestControllerAdvice` gom lỗi, convert `MethodArgumentNotValidException` sang `error.fields`, message tiếng Việt qua `messages_vi.properties`
- Cách lấy user hiện tại từ SecurityContext — mọi query có điều kiện quyền (CORE-05) sẽ gọi
- Annotation `@Idempotent` mà mọi POST tạo mới phase sau gắn vào

### Integration Points
- **Flyway trỏ `db/migration/` gốc** (`../../db/migration`): điểm nối duy nhất với tài sản có sẵn của dự án. Cấu hình sai đường dẫn là lỗi chặn ngay task đầu
- **`scheduler/`**: khởi tạo ở phase này với đúng 1 job dọn `idempotency_keys` quá 24h. Phase 4 thêm 4 job nữa vào đây
- **`common/`**: nơi đặt response wrapper, exception handler, security filter, idempotency AOP, rate limit filter. Mọi package nghiệp vụ phase sau đều import từ đây

</code_context>

<specifics>
## Specific Ideas

- Success Criteria #3 của ROADMAP yêu cầu đăng ký tạo sẵn ví "Tiền mặt" số dư 0 — **không** vì thế mà dựng cả module `wallet/` ở phase này. Viết tối thiểu đủ để chèn một bản ghi `wallets`; module `wallet/` đầy đủ thuộc Phase 2.
- `refresh_tokens.token_hash` là SHA-256 (VARCHAR(64) hex), **không phải bcrypt** — token vốn đã là chuỗi ngẫu nhiên entropy cao, bcrypt chậm có chủ đích chỉ cần cho mật khẩu. Comment trong V7 đã ghi rõ.
- `refresh_tokens` **không xoá bản ghi khi thu hồi**, chỉ set `revoked_at` — cần thế để phát hiện tái sử dụng token đã thu hồi (AUTH-03).
- `login_attempts` ghi **cả lần thành công lẫn thất bại** kèm IP (AUTH-07), không chỉ ghi lần sai.
- AUTH-02: sai email và sai mật khẩu trả **cùng** mã `INVALID_CREDENTIALS`; AUTH-06: `/auth/forgot-password` **luôn** trả 200 kể cả email không tồn tại. Cả hai là biện pháp chống dò danh sách người dùng — đừng "sửa" thành thông báo cụ thể hơn cho thân thiện.

</specifics>

<deferred>
## Deferred Ideas

### Mâu thuẫn tài liệu cần sửa ở Phase 4 (không sửa ở Phase 1)
- **`api/00-QUY-UOC-CHUNG.md` mục 13** vẫn viết *"khi gom nhóm theo ngày hoặc tháng, máy chủ tính theo giờ Việt Nam"* — mâu thuẫn với CORE-07 và REPORT-01 đã sửa: `transactions.date` là kiểu `DATE` (ngày lịch thuần, không múi giờ), gom nhóm dùng thẳng cột này, **không** `AT TIME ZONE`.
- **`source/server/CLAUDE.md` quy tắc 7** mắc đúng lỗi đó: *"tính theo giờ Việt Nam (`AT TIME ZONE 'Asia/Ho_Chi_Minh'` trong SQL) khi group theo ngày/tháng"* — sai, phải sửa.
- Hai chỗ này thuộc phạm vi báo cáo (Phase 4). Ghi lại ở đây để không rơi mất — **agent Phase 4 phải sửa cả hai trước khi code `report/`**, nếu không sẽ code theo mô tả sai.

### Ngoài phạm vi Phase 1
- Profile `prod` + cấu hình deploy (log gọn, tắt show SQL) — thêm khi thực sự cần demo deploy
- Tích hợp SMTP thật cho email reset password — xem D-22, hiện dùng notifier ghi log
- Bảng `export_jobs` (REPORT-05) — V7 cố ý chưa tạo, để Phase 4 thiết kế sát nhu cầu thật

</deferred>

---

*Phase: 01-nen-tang-xac-thuc*
*Context gathered: 2026-08-22*
