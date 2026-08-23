# Phase 2: Ví & Danh mục - Context

**Gathered:** 2026-08-23
**Status:** Ready for planning

<domain>
## Phase Boundary

User quản lý đầy đủ ví tiền (CRUD, sắp xếp, chuyển tiền giữa hai ví, đối chiếu số dư, điều chỉnh số dư theo kiểm kê) và cây danh mục hai cấp gắn biểu tượng — làm nền cho module giao dịch ở Phase 3.

**14 requirement:** WALLET-01…08, CAT-01…06.

Ngoài phạm vi: giao dịch (Phase 3), ngân sách/báo cáo (Phase 4), nhóm gia đình (Phase 5).

</domain>

<decisions>
## Implementation Decisions

### Đối chiếu số dư — thu hẹp phạm vi WALLET-07

- **D-25:** **Giữ endpoint `POST /wallets/{id}/reconcile`, BỎ job `@Scheduled` chạy hằng ngày.**
  Lý do bỏ job: đồ án chạy trên máy cá nhân, không chạy 24/7, nên job định kỳ gần như không bao
  giờ nổ — chi phí duy trì không đổi lấy được giá trị nào.
  Lý do GIỮ endpoint: test của WALLET-08 (điều chỉnh số dư) dùng chính phép đối chiếu này để
  chứng minh việc sinh giao dịch bù không phá vỡ sổ sách (`số dư lưu == số dư tính lại`). Bỏ luôn
  endpoint thì mất cách kiểm chứng đó.
  **Đã cập nhật `REQUIREMENTS.md` WALLET-07 và `ROADMAP.md` tiêu chí 3 cho khớp — planner không
  cần sửa lại.** `api/02-VI.md` mục 10 giữ nguyên (đặc tả endpoint vẫn đúng), chỉ phần "nên chạy
  tự động hằng ngày" là khuyến nghị không áp dụng ở milestone này.

### Entity Wallet — xử lý di sản Phase 1

- **D-26:** **Thay thế hẳn `common/wallet/WalletMinimal` bằng `wallet/entity/Wallet` đầy đủ cột**
  (bổ sung `sortOrder`; `isDeleted` đã có sẵn), xoá package `common/wallet/`.
  Phase 1 tạo `WalletMinimal` làm entity tạm chỉ để `AuthService.register` sinh ví "Tiền mặt" và
  `GET /auth/me` đếm `stats.wallet_count` — Javadoc của chính nó đã ghi "module wallet/ đầy đủ
  thuộc Phase 2".
  **Không giữ hai entity song song cùng ánh xạ bảng `wallets`**: Hibernate có thể giữ hai bản sao
  cùng một ví trong persistence context, sửa qua entity này thì entity kia không thấy — lỗi âm
  thầm rất khó truy.
  **Điểm chạm phải sửa:** `AuthService` (1 file main) + 5 file test đang inject
  `WalletMinimalRepository` (`AuthRegisterLogin`, `AuthLoginLockout`, `AuthProfilePassword`,
  `AuthRefreshRotation`, `AuthIdempotencyRateLimitEndToEnd`). Phần lớn chỉ là đổi `import`.
  **Bắt buộc chạy lại toàn bộ test suite Phase 1 (35 test) sau khi thay** — xanh hết mới coi là xong.

### Kiểm tra quyền trong SQL (CORE-05)

- **D-27:** **Viết sẵn CẢ HAI vế điều kiện quyền ngay từ Phase 2**, dù nhóm gia đình mãi Phase 5
  mới làm:
  ```sql
  WHERE w.user_id = :currentUser
     OR w.group_id IN (SELECT group_id FROM group_members
                       WHERE user_id = :currentUser AND is_active = true)
  ```
  Vế nhóm hiện luôn trả rỗng (chưa ai vào nhóm) nên không ảnh hưởng kết quả, nhưng tới Phase 5
  không phải rà lại từng câu truy vấn để thêm — sót một câu là hoặc lộ dữ liệu người khác, hoặc
  thành viên không thấy ví chung.
  Áp dụng cho **mọi** query ví và danh mục, không riêng endpoint chi tiết.
  Không có quyền → **404**, không phải 403 (không để lộ bản ghi có tồn tại hay không).
  Kiểm tra ngay trong câu SQL, **không** lấy hết rồi lọc ở tầng Java.

### Phạm vi test

- **D-28:** **Tập trung luồng rủi ro, không phủ đều CRUD.** Bốn nhóm phải test kỹ:
  1. **Chuyển tiền đồng thời trên cùng một ví** — chứng minh không lost-update (hai luồng chạy song song, số dư cuối phải đúng)
  2. **Điều chỉnh số dư (WALLET-08)** — cả hai chiều (thiếu → expense, dư → income), chênh = 0 không tạo giao dịch, và **đối chiếu sau điều chỉnh vẫn khớp**
  3. **Quyền truy cập** — user A gọi ví/danh mục của user B phải nhận **404**
  4. **Cây danh mục hai cấp** — chặn tạo cấp ba, chặn con khác `type` với cha, chặn xoá danh mục còn con/đang được dùng
  CRUD thường chỉ test đường chính (happy path), không viết test lặp cho từng trường hợp lỗi đã
  có ràng buộc CSDL bảo vệ.
  Tiếp tục dùng **Testcontainers PostgreSQL thật** như Phase 1 (D-23) — các luồng trên đều phụ
  thuộc hành vi Postgres thật (`SELECT ... FOR UPDATE`, CHECK constraint, trigger).

### Cách chia plan

- **D-29:** **Tách plan Ví và plan Danh mục riêng** theo đúng ranh giới nghiệp vụ (hai mảng không
  phụ thuộc nhau). Cấu hình `parallelization: false` nên vẫn chạy lần lượt, nhưng tách đúng ranh
  giới giúp dễ theo dõi và lỗi ở mảng nào biết ngay.
  Plan thay thế `WalletMinimal` (D-26) phải nằm ở **wave đầu tiên** — nó chạm code Phase 1 nên
  cần xác nhận 35 test cũ vẫn xanh trước khi xây tiếp lên trên.

### Claude's Discretion

Những thứ không cần hỏi lại, làm theo chuẩn dự án và `api/*.md`:
- Cấu trúc package con trong `wallet/` và `category/` (theo feature-first như `auth/` ở Phase 1)
- Cách tổ chức DTO, dùng MapStruct hay mapper thủ công
- Tên class/method cụ thể (tiếng Anh — xem mục "Ngôn ngữ" ở `CLAUDE.md`)
- Cách bọc `fn_category_tree` thành repository method dùng chung
- Thứ tự task trong từng plan

</decisions>

<specifics>
## Specific Ideas

- **Điều chỉnh số dư là kiểm kê, không phải sửa số dư.** Người dùng đếm tiền thật rồi nhập con số
  thực tế; hệ thống **không ghi đè** `current_balance` mà sinh một giao dịch bù đúng phần chênh.
  Nhờ vậy phép đối chiếu vẫn đúng nguyên vẹn và vẫn truy được tiền đã đi đâu. Chi tiết đầy đủ ở
  `DATN/source/server/.planning/notes/2026-08-23-kiem-ke-so-du-vi.md`.
- **Đừng nhầm hai việc nghe giống nhau:** `adjust-balance` (mục 9) là *người dùng chủ động kiểm kê*
  — lệch là bình thường, do quên ghi. `reconcile` (mục 10) là *máy dò lỗi hệ thống* — lệch nghĩa là
  backend có bug. Không nhét chung một endpoint.
- Người dùng ưu tiên **giải thích được cho hội đồng** hơn là phủ test tối đa — chọn test chứng minh
  được tính đúng đắn ở chỗ dễ mất tiền, thay vì đếm số lượng test.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**
Đường dẫn tính từ **thư mục gốc `DATN/`** (không phải từ repo `source/server/`) — vì `api/` và `db/` nằm ở repo cha.
`api/` và `db/` là **nguồn sự thật** — code phải khớp chúng, không phải ngược lại.

### Quy ước chung (đọc trước tiên)
- `DATN/api/00-QUY-UOC-CHUNG.md` — khung response `{success,data}`/`{success,error}` (§4), bảng mã lỗi dùng chung (§6), phân trang (§7), Idempotency-Key (§11)
- `DATN/CLAUDE.md` — bảy quy tắc nghiệp vụ bất biến; đặc biệt §1 (cộng gộp danh mục con), §2 (loại `transfer` khỏi báo cáo), §3 (số tiền luôn dương), §5 (số dư lưu sẵn), §7 (quyền trong SQL, trả 404)
- `DATN/source/server/CLAUDE.md` — quy tắc riêng backend; mục "Ngôn ngữ" (định danh code luôn tiếng Anh, **không ngoại lệ cho test**)

### Ví
- `DATN/api/02-VI.md` — toàn bộ 10 mục. §5 (không sửa `current_balance`/`type`), §6 (xoá ví: `delete_transactions`, không xoá ví cuối), §8 (chuyển tiền, `fail_if_insufficient`), §9 (điều chỉnh số dư — WALLET-08), §10 (đối chiếu — xem D-25)

### Danh mục
- `DATN/api/03-DANH-MUC.md` — §1 (cây hai cấp), §4 (danh mục hệ thống không sửa: `SYSTEM_CATEGORY_NOT_EDITABLE`), §5 (không xoá khi còn con/đang dùng), §7 (nhóm lớn cố định), §8 (kho icon — nhận `icon_id`, không nhận tệp ảnh)

### Schema thật (thắng `api/*.md` khi hai bên lệch)
- `DATN/db/migration/V1__nen_tang.sql` — bảng `wallets`, `categories`, `icons`, `category_groups`; ràng buộc `ck_cat_type`, `uq_cat_name_root`/`uq_cat_name_child`, trigger `fn_categories_validate` (chặn ba cấp, chặn con khác type cha)
- `DATN/db/migration/V6__va_loi_bao_mat.sql` — `fn_category_tree` (đã lọc xoá mềm) — **dùng lại hàm này, không tự viết điều kiện lọc**
- `DATN/db/migration/V8__dieu_chinh_so_du.sql` — `transactions.counts_in_report`, giá trị `adjustment` cho `source`, hai danh mục hệ thống "Cập nhật số dư"
- `DATN/db/README.md` — mục "Trigger có sẵn" (6 trigger, ranh giới quyền sở hữu cột), quy tắc tạo migration mới

### Bối cảnh Phase 1
- `DATN/source/server/.planning/phases/01-nen-tang-xac-thuc/01-CONTEXT.md` — D-01…D-24 còn ràng buộc: D-10/D-11 (`@Idempotent` chỉ cho POST-tạo-mới), D-23 (Testcontainers), D-24 (chi tiết kỹ thuật theo chuẩn)
- `DATN/source/server/.planning/phases/01-nen-tang-xac-thuc/deferred-items.md` — nợ kỹ thuật đã ghi nhận (giải IP client hoãn sang phase deployment)
- `DATN/source/server/.planning/notes/2026-08-23-kiem-ke-so-du-vi.md` — bối cảnh đầy đủ của WALLET-08

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (Phase 1 đã xây, dùng lại nguyên vẹn — KHÔNG viết lại)
- `common/response/ApiResponse`, `ErrorResponse`, `PageMeta`, `PageRequestParams` — khung response và phân trang
- `common/exception/BusinessException` + `GlobalExceptionHandler` — **mọi lỗi nghiệp vụ ném qua đường này** để giữ envelope nhất quán
- `common/security/SecurityContextUtil` — lấy `userId` của người gọi hiện tại, dùng cho mọi điều kiện quyền ở D-27
- `common/security/SecurityConfig` — chain STATELESS đã dựng; endpoint mới mặc định yêu cầu `Authorization: Bearer`
- `common/idempotency/@Idempotent` — gắn lên `POST /wallets`, `POST /wallets/transfer`, `POST /categories`, `POST /wallets/{id}/adjust-balance`. **Không** gắn lên PATCH/DELETE (D-11: chỉ POST-tạo-mới)
- `common/ratelimit/RateLimitFilter` — đã hoạt động, endpoint mới tự động được bảo vệ, không cần làm gì

### Established Patterns (theo Phase 1)
- `@Transactional` đặt **trên từng public method**, không ở mức class
- **Spring AOP self-invocation làm mất `@Transactional`** — Phase 1 đã vấp và giải bằng cách tách bean riêng (`IdempotencyTransactionHelper`). Cảnh giác khi service gọi method của chính nó
- Cập nhật số dư ví: **`UPDATE ... SET current_balance = current_balance + :delta`** (atomic), tuyệt đối không load-modify-save
- Khoá bi quan khi cần đọc-rồi-ghi: `@Lock(LockModeType.PESSIMISTIC_WRITE)` — mẫu có sẵn ở `RefreshTokenRepository.findActiveByTokenHashForUpdate`
- Test slice `@WebMvcTest` cần `excludeFilters` cho `SecurityConfig`/`JwtAuthFilter`
- `pom.xml` đang ép `-Duser.timezone=UTC` và `project.build.sourceEncoding=UTF-8` — **giữ nguyên**, cần cho Windows

### Integration Points
- `AuthService.register` tạo ví "Tiền mặt" trong **cùng transaction** với insert `User` — điểm chạm chính khi thay entity theo D-26
- `GET /auth/me` trả `stats.wallet_count` — đang gọi `WalletMinimalRepository.countByUserIdAndIsDeletedFalse`, phải chuyển sang repository mới
- `fn_category_tree` sẽ được bọc thành repository method ở phase này và **dùng lại ở Phase 3 (cộng gộp danh mục con), Phase 4 (ngân sách, báo cáo)** — làm sai ở đây thì sai lan ba phase

</code_context>

<deferred>
## Deferred Ideas

- **Job đối chiếu số dư tự động hằng ngày** — loại khỏi phạm vi theo D-25. Nếu sau này deploy chạy 24/7 thì thêm `@Scheduled` gọi lại chính service của endpoint `reconcile`, không phải viết mới.
- **Ba câu về giao diện của tính năng điều chỉnh số dư** — thuộc phase làm UI phía app, không chặn backend: đặt ở màn nào (Ví hay Sổ giao dịch), người dùng nhập số dư mới hay phần chênh, toggle "tính vào báo cáo" mặc định bật hay tắt. Chi tiết ở `DATN/source/server/.planning/notes/2026-08-23-kiem-ke-so-du-vi.md`.
- **Giải IP client không nhất quán** (`ClientIpResolver` tin `X-Forwarded-For` vô điều kiện; `RateLimitFilter` không dùng resolver đó) — đã hoãn sang phase deployment, ghi ở `01-nen-tang-xac-thuc/deferred-items.md`.
- **`messages_vi.properties` là tài nguyên chết** — `GlobalExceptionHandler` không dùng tới. Dọn hoặc nối vào i18n ở phase sau.

</deferred>

---

*Phase: 02-vi-danh-muc*
*Context gathered: 2026-08-23*
