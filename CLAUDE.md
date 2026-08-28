# CLAUDE.md — Backend

Hướng dẫn cho Claude Code khi làm việc trong `source/server/` — backend Spring Boot của dự án Quản lý tài chính cá nhân có AI.

**Đọc trước:** [CLAUDE.md gốc](../../CLAUDE.md) ở thư mục DATN — chứa 3 nguyên tắc bất biến, quy tắc nghiệp vụ, bản đồ tài liệu (`api/*.md`, `THIET-KE-CSDL.md`) áp dụng cho toàn dự án bao gồm cả backend này.

## Dự án GSD

Project này được khởi tạo và quản lý qua GSD (Get Shit Done):

| Cần biết gì | Đọc file |
|---|---|
| Bối cảnh, requirements, quyết định | [.planning/PROJECT.md](.planning/PROJECT.md) |
| Danh sách 80 requirements (13 nhóm) | [.planning/REQUIREMENTS.md](.planning/REQUIREMENTS.md) |
| 5 phase thực thi, success criteria | [.planning/ROADMAP.md](.planning/ROADMAP.md) |
| Đang ở đâu, việc tiếp theo | [.planning/STATE.md](.planning/STATE.md) |
| Research stack Spring Boot | [.planning/research/SUMMARY.md](.planning/research/SUMMARY.md) |

**Bắt đầu phiên làm việc mới:** đọc STATE.md trước để biết đang ở phase nào. Dùng `/gsd-plan-phase <N>` để lập kế hoạch chi tiết cho một phase, `/gsd-progress` để xem tiến độ tổng thể.

## Ngăn xếp công nghệ đã chốt

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ | Java 17 |
| Framework | Spring Boot 3.3.x/3.4.x |
| Build tool | Maven |
| CSDL | PostgreSQL 14+ |
| Migration | Flyway — **dùng chung `db/migration/` ở thư mục gốc DATN, không copy vào đây** |
| Data access | Spring Data JPA (chủ đạo) + native SQL/JdbcTemplate cho các điểm nóng (atomic balance update, `fn_category_tree`, view `v_budget_progress`) |
| Auth | JWT qua `jjwt` — access token stateless 1h, refresh token băm SHA-256 lưu DB 30 ngày, rotation + reuse detection |
| Idempotency | Bảng DB `idempotency_keys`, không dùng Redis |
| Rate limit | Bucket4j in-memory + Caffeine |
| Job nền | `@Scheduled` với `zone="Asia/Ho_Chi_Minh"`, không dùng Quartz |
| Test | Testcontainers PostgreSQL cho integration test — **không dùng H2** (H2 không hỗ trợ constraint/hàm SQL của schema V1-V5) |

Chi tiết lý do từng lựa chọn: [.planning/research/SUMMARY.md](.planning/research/SUMMARY.md).

## Cấu trúc package

**Package-by-feature**, không phải package-by-layer:

```
com.datn.financeapp
├── common/          # response wrapper, exception handler, security, idempotency, rate limit
├── auth/            # api/01-XAC-THUC
├── wallet/          # api/02-VI
├── category/        # api/03-DANH-MUC
├── transaction/      # api/04-GIAO-DICH — module lõi, rủi ro cao nhất
├── budget/          # api/05-NGAN-SACH
├── report/          # api/06-BAO-CAO
├── ai/              # api/07-AI
├── debt/            # api/08-SO-NO
├── recurring/       # api/09 phần A
├── goal/            # api/09 phần B
├── group/           # api/10-NHOM-GIA-DINH
└── scheduler/       # các @Scheduled job
```

Mỗi package nghiệp vụ tự chứa Controller/Service/Repository/dto/entity của mình. Không tạo `controller/`, `service/`, `repository/` ở cấp cao nhất.

## Quy tắc bắt buộc riêng cho backend này

Kế thừa toàn bộ 8 quy tắc nghiệp vụ bất biến ở CLAUDE.md gốc, cộng thêm:

0. **Đối chiếu với schema thật trước khi tin vào tài liệu.** `api/*.md` và `db/migration/V*.sql` là hai tài liệu được viết song song và **không bao giờ gặp nhau ở runtime** — nên chúng lệch nhau âm thầm, không có gì báo lỗi. App Flutter không dính vấn đề này vì compiler ép buộc; backend thì không có cơ chế tương đương. Trước khi lập kế hoạch hay code một phase, **mở `db/migration/V*.sql` đọc bảng/cột/trigger thật**, đừng tin mô tả trong `api/` hay `.planning/`.
   Đã có bốn lỗi được tìm ra đúng theo cách này: `v_budget_progress` thiếu điều kiện quyền, `groups` thiếu cột hạn mã mời mà `api/10` yêu cầu, `transactions.date` là `DATE` chứ không phải `TIMESTAMPTZ`, và 4 bảng hạ tầng xác thực chưa tồn tại.
1. **Không tự bịa API hoặc trường dữ liệu chưa có trong `api/*.md`.** Nếu thiếu/mâu thuẫn, dừng lại hỏi thay vì đoán.
2. **Không viết trigger CSDL cho số dư ví** (`wallets.current_balance`) — logic này nằm ở tầng Service, gói trong `@Transactional`.
   **Nhưng schema đã có sẵn 6 trigger**, trong đó `trg_debt_payments_sync`/`trg_goal_contributions_sync` (V4) **sở hữu** `debts.paid_amount`, `debts.status`, `savings_goals.saved_amount`, `savings_goals.status` — backend chỉ chèn/xoá bản ghi `debt_payments`/`goal_contributions`, **tuyệt đối không ghi bốn cột đó**. Bảng đầy đủ ở [db/README.md](../../db/README.md) mục "Trigger có sẵn".
3. **Cập nhật `current_balance` phải atomic** (`UPDATE ... SET balance = balance + :delta`), không load-modify-save qua entity — tránh lost-update, đặc biệt với ví chung nhóm gia đình.
4. **Sửa/xoá giao dịch phải đúng 3 bước** trong cùng 1 `@Transactional`: hoàn tác ảnh hưởng cũ → ghi giá trị mới → áp dụng ảnh hưởng mới. Tránh transaction self-invocation (gọi method `@Transactional` từ trong cùng class) làm mất annotation.
5. **Mọi truy vấn ví/giao dịch/ngân sách/nợ/mục tiêu phải kiểm tra quyền ngay trong JPQL/native query**, không load hết rồi lọc ở code Java. Không có quyền → 404.
6. **Mọi thống kê/lọc theo danh mục cha phải cộng gộp danh mục con** — dùng lại một repository method gọi `fn_category_tree`, không lặp điều kiện lọc ở nhiều nơi.
7. **Mọi báo cáo phải loại `type='transfer'`.** Gom nhóm báo cáo theo ngày/tháng dùng thẳng cột `transactions.date` (kiểu `DATE` thuần, không có thành phần giờ) — KHÔNG chuyển đổi múi giờ dưới bất kỳ hình thức nào. Đây là thiết kế cố ý để tránh hẳn lớp bug múi giờ.
8. **`amount` luôn kiểu `Long`**, không bao giờ `Double`/`float` ở bất kỳ tầng nào.
9. **AI (parse-text/OCR) không bao giờ tự tạo `transactions`** — luôn qua `ai_drafts.status='pending'`, chờ duyệt; khi duyệt phải ghi `user_corrections`.

## Ngôn ngữ

Giao tiếp, commit message: **tiếng Việt**. Code (biến, hàm, class, comment kỹ thuật): **tiếng Anh** chuẩn Java. JSON field `snake_case`, map sang `camelCase` ở tầng model Java nhưng giữ nguyên khoá khi (de)serialize.

**Tên định danh trong code LUÔN là tiếng Anh — không có ngoại lệ cho code test.** Quy tắc này
áp dụng cho mọi tên biến, tên method, tên class, tên hàm helper, **kể cả tên method `@Test` và
helper trong file test**. Không viết tiếng Việt không dấu trong định danh.

| Sai | Đúng |
|---|---|
| `void dangKy(...)` | `void register(...)` |
| `void loginSai(...)` | `void loginWithWrongPassword(...)` |
| `sai5LanLienTiep_LanThu6DuDungPassword_VanBiAccountLocked()` | `after5FailedAttempts_6thWithCorrectPassword_isStillLocked()` |
| `void tokenBiSuaChuKy_nemSignatureException()` | `void tamperedSignature_throwsSignatureException()` |

Tên method test đặt theo mẫu `<điều kiện>_<hành động>_<kết quả mong đợi>` bằng tiếng Anh.

Chỗ ĐƯỢC dùng tiếng Việt trong file code: giá trị chuỗi dữ liệu nghiệp vụ (`"Tiền mặt"`,
`"Ăn uống"`), thông điệp lỗi trả cho người dùng, và comment giải thích — comment viết tiếng
Việt có dấu đầy đủ. Riêng định danh thì tuyệt đối tiếng Anh.
