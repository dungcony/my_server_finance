# Phase 3: Giao dịch - Context

**Gathered:** 2026-08-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Ghi nhận, sửa, xoá (mềm), nhân bản và tạo hàng loạt giao dịch `expense`/`income`/`transfer`,
với số dư ví chính xác tuyệt đối kể cả trong thao tác sửa/xoá phức tạp. Đây là module lõi —
Phase 4 (ngân sách, sổ nợ, định kỳ, mục tiêu, báo cáo) và Phase 5 (duyệt AI drafts) đều gọi
lại logic ghi giao dịch xây ở đây, không viết lại.

Bao gồm: TXN-01 → TXN-09 (TXN-09 mới thêm 23/08/2026 — xem D-36).

**Không thuộc phase này:** module sổ nợ / định kỳ / mục tiêu / ngân sách / báo cáo (Phase 4),
tầng quyền nhóm gia đình và AI drafts (Phase 5).

</domain>

<decisions>
## Implementation Decisions

### Gộp hai đường ghi giao dịch — di sản Phase 2

- **D-30:** **Gộp ngay ở plan ĐẦU TIÊN của phase, không để cuối.**
  `WalletTransferService` (Phase 2) đang tự `INSERT INTO transactions` bằng `JdbcTemplate` ở
  **2 chỗ**: `transfer()` (dòng ~81) và `adjustBalance()` (dòng ~146) — lúc đó chưa có entity
  Transaction nào tồn tại.
  Phase 3 tạo entity + service thật → nếu để nguyên sẽ có **hai đường ghi giao dịch song song**.
  Đây đúng kiểu lỗi `db/README.md` cảnh báo: chạy đúng một cách tình cờ, sai thật khi ai đó
  đổi một bên.
  Cụ thể nó gãy ở TXN-09: logic "trừ ngược phần tương lai" sẽ phải viết hai lần, hoặc chỉ viết
  một chỗ và chỗ kia âm thầm sai.
  **Trình tự bắt buộc:** tạo entity + writer → sửa `WalletTransferService` gọi qua writer →
  **chạy lại toàn bộ 77 test Phase 1+2, xanh hết mới đi tiếp**. Cùng khuôn với D-26 (Phase 2
  thay `WalletMinimal`).

- **D-31:** **Tách `TransactionWriter` thành bean riêng — một lớp mỏng chỉ lo "ghi bản ghi +
  cập nhật số dư ví cho đúng".**
  Nhận đủ tham số (`source`, `countsInReport`, `categoryId`, `date`…) và **không biết gì** về
  nghiệp vụ gọi nó. Ba nơi cùng gọi: `TransactionService` (CRUD người dùng),
  `WalletTransferService` (chuyển tiền + kiểm kê), và Phase 4 (debt/goal/recurring).
  Lý do tách thay vì nhét hết vào một service chung: `adjustBalance` có luật riêng
  (`source='adjustment'`, `counts_in_report` do user chọn, danh mục hệ thống V8) — nhét vào
  service chung sẽ làm nó phình ra để phục vụ ca đặc biệt.
  **Lợi ích thứ hai — tránh self-invocation:** writer là bean riêng nên `@Transactional` của nó
  đi qua Spring AOP proxy thật. Đúng bài học từ `IdempotencyTransactionHelper` ở Phase 1
  (self-invocation trong cùng class làm mất `@Transactional` âm thầm).

### Xoá giao dịch gắn sổ nợ (TXN-06)

- **D-32:** **CHẶN xoá — trả `TRANSACTION_LINKED_TO_DEBT` (409), không tự dọn.**
  Phase 3 chỉ thêm **một câu kiểm tra `EXISTS`** trên `debt_payments` theo `transaction_id`.
  Có liên kết → từ chối, thông báo người dùng huỷ ở màn Sổ nợ.

  **Vì sao không tự dọn (TXN-06 nguyên văn ghi "cập nhật lại `paid_amount`"):**
  `debts.paid_amount` và `debts.status` **do trigger `trg_debt_payments_sync` sở hữu** —
  backend tuyệt đối không ghi (quy tắc ở `CLAUDE.md` + `db/README.md`). Trigger chỉ bắn
  `AFTER INSERT OR UPDATE OR DELETE ON debt_payments`, **không** bắn khi `transactions.is_deleted`
  đổi. Nên xoá mềm giao dịch để nguyên `paid_amount` → sổ nợ báo "đã trả 500k" trong khi khoản
  thu tương ứng không còn. Hai màn hình nói hai chuyện khác nhau.

  Cách đúng duy nhất là xoá bản ghi `debt_payments` để trigger tự tính lại — mà việc đó thuộc
  `DELETE /debts/{id}/payments/{payment_id}` (**đã đặc tả sẵn** ở `api/08-SO-NO.md` mục 296-309,
  mô tả đúng cơ chế trigger). Phase 4 xây API đó. Phase 3 không đụng bảng của Phase 4.

  **Lưu ý phạm vi:** chỉ áp dụng cho giao dịch sinh ra từ sổ nợ. Giao dịch người dùng tự ghi
  xoá bình thường, không vướng gì.

  **Đã sửa tài liệu trong lượt discuss này** (`api/04-GIAO-DICH.md` mục 9 + bảng mã lỗi,
  `api/00-QUY-UOC-CHUNG.md` mục 6) — planner không cần sửa lại.

### Khoá ví khi sửa giao dịch (TXN-05)

- **D-33:** **Khoá HỢP tất cả ví liên quan (cũ + mới), sắp theo `UUID.compareTo()` rồi khoá lần lượt.**
  Phase 2 chỉ gặp ca đúng 2 ví (transfer). Sửa giao dịch có thể chạm **tối đa 4 ví**: sửa
  `transfer A→B` thành `transfer C→D` phải hoàn tác trên A,B rồi áp dụng lên C,D.
  Mở rộng đúng cách Phase 2 đã chứng minh chạy được (`lockAndCheckOwnership` +
  `UUID.compareTo()` chống deadlock, `WalletTransferConcurrencyTest` đã xanh) — từ 2 ví lên 4.
  Thứ tự khoá cố định theo UUID nên không bao giờ deadlock kể cả khi hai người sửa chéo nhau.
  **Không bỏ khoá** dù `adjustBalance` đã atomic ở tầng Postgres: cần đọc số dư đáng tin để trả
  `new_balance` cho app, và giữ một kiểu duy nhất trong dự án.

### Bulk và idempotency (TXN-04)

- **D-34:** **Mỗi dòng một DB transaction RIÊNG**, không phải cả lô chung một.
  Dòng nào ghi xong là chắc chắn xong, không bị dòng sau kéo đổ. Đúng tinh thần "dòng lỗi bị bỏ
  qua và báo trong `row_errors`, không từ chối toàn bộ lô", và đúng cho luồng duyệt AI drafts ở
  Phase 5 (user duyệt 12 bản nháp OCR, 11 cái đúng phải vào được).
  Áp dụng cho **cả lỗi dữ liệu lẫn lỗi hệ thống**: dòng 10 mất kết nối DB thì 9 dòng trước vẫn giữ.

- **D-35:** **Một `Idempotency-Key` cho CẢ LÔ**, không phải mỗi dòng một key.
  Dùng lại `@Idempotent` của Phase 1 (D-10) — không viết cơ chế mới. Gửi lại cùng key trả nguyên
  kết quả lần đầu (gồm cả `row_errors`), không ghi thêm gì.
  Không chọn key-mỗi-dòng vì `api/00-QUY-UOC-CHUNG.md` không đặc tả kiểu đó, và app phải quản lý
  tới 50 key một lúc.

- **D-35a (hệ quả kỹ thuật bắt buộc của D-34 + D-35):** `@Idempotent` của Phase 1 chạy **trong**
  `@Transactional`. Để nguyên thì cả lô lại thành một transaction — **triệt tiêu D-34**.
  Plan phải để mỗi dòng chạy transaction riêng (`REQUIRES_NEW`, hoặc gọi qua bean riêng như
  `TransactionWriter` ở D-31), còn `@Idempotent` chỉ lo cache kết quả cuối cùng.
  **Phải có test chứng minh:** lô 3 dòng, dòng 2 lỗi hệ thống → dòng 1 và 3 vẫn nằm trong DB.

### Ngày tương lai và hai trường số dư (quyết định 23/08/2026, trước lượt discuss này)

- **D-36:** **Bỏ hẳn luật chặn `date > hôm nay`.** Giao dịch tương lai ghi thật vào `transactions`
  ngay và cộng trừ `wallets.current_balance` ngay — không trạng thái chờ, không job kích hoạt.
  Hệ quả: **cột** `current_balance` mang nghĩa "số dư đã tính hết mọi giao dịch đã ghi".
  Số dư tại mốc T tính bằng **trừ ngược**: `current_balance − SUM(ảnh hưởng của giao dịch date > T)`.
  Không cộng xuôi từ `initial_balance` — trừ ngược quét ít dòng hơn hẳn (phần lớn ví không có
  giao dịch tương lai) và **giữ được hai nguồn độc lập cho phép đối chiếu WALLET-08**
  (WALLET-07/08 của Phase 2 **không phải sửa** — chúng so trên toàn bộ giao dịch kể cả tương lai).

- **D-37 — điểm dễ sai nhất của phase:** **cột CSDL ≠ trường API.**

  | | Nghĩa |
  |---|---|
  | Cột `wallets.current_balance` | đã gồm giao dịch tương lai |
  | Trường API `current_balance` | **đã trừ ngược** — tiền thật đến hết hôm nay |
  | Trường API `projected_balance` | bằng đúng cột CSDL; **chỉ trả khi ví CÓ giao dịch tương lai**, không có thì bỏ hẳn trường |

  **Đừng map thẳng entity sang DTO.** `WalletResponse`/`WalletDetailResponse` của Phase 2 hiện
  đang map thẳng → **phải sửa** (đây chính là TXN-09).
  Chọn giữ nghĩa "tiền thật" cho `current_balance` để app Flutter không phải sửa màn hình nào.

### Claude's Discretion

Không cần hỏi lại, làm theo chuẩn dự án, `api/*.md` và pattern Phase 1+2:
- Cấu trúc package con trong `transaction/` (feature-first như `auth/`, `wallet/`, `category/`)
- Tổ chức DTO, MapStruct hay mapper thủ công
- Tên class/method cụ thể — **tiếng Anh, không ngoại lệ kể cả tên method test**
- Cách chống N+1 khi list giao dịch kèm wallet/category/icon (`@EntityGraph` / `JOIN FETCH` /
  DTO projection — chọn cái hợp nhất)
- Thứ tự task trong từng plan, cách chia plan chi tiết (nhưng D-30 phải ở plan đầu)
- Cách bọc query "trừ ngược" của D-36 thành method dùng chung

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Hợp đồng API — nguồn sự thật
- `api/00-QUY-UOC-CHUNG.md` — khung `{success,data}`/`{success,error}`, phân trang, `Idempotency-Key`,
  bảng mã lỗi chung (đã thêm `TRANSACTION_LINKED_TO_DEBT` trong lượt discuss này)
- `api/04-GIAO-DICH.md` — **đặc tả chính của phase**. Mục 6 (cộng gộp danh mục con),
  **mục 7 (Giao dịch tương lai — mới thêm 23/08)**, mục 8 (sửa 3 bước), mục 9 (xoá + chặn debt),
  mục 10 (nhân bản), bảng mã lỗi cuối file
- `api/02-VI.md` — mục "Hai trường số dư" (`current_balance` vs `projected_balance`),
  mục 10 (đối chiếu — **không phải sửa**, so trên toàn bộ giao dịch)
- `api/08-SO-NO.md` §296-309 — `DELETE /debts/{id}/payments/{payment_id}`, API mà D-32 trỏ người
  dùng sang. Đã mô tả đúng cơ chế trigger

### Schema thật — đối chiếu TRƯỚC khi tin api/*.md (CORE-11)
- `db/migration/V2__giao_dich.sql` — bảng `transactions`: `ck_txn_shape`, `ck_txn_amount`,
  `ck_txn_source` (`manual|text|ocr|auto`), 4 index đã có sẵn (`idx_txn_user_date`,
  `idx_txn_wallet_date`, `idx_txn_category_date`, `idx_txn_dest_wallet`),
  `uq_txn_recurring_date`. **Không có cột `debt_id`** — liên kết một chiều từ `debt_payments`
- `db/migration/V4__so_no_muc_tieu.sql` §75-110 — `fn_debt_payments_sync` + trigger; đọc để hiểu
  vì sao D-32 chặn thay vì tự dọn. `debt_payments.transaction_id` có `ON DELETE RESTRICT` + `UNIQUE`
- `db/migration/V8__dieu_chinh_so_du.sql` — `counts_in_report`, giá trị `adjustment` cho `source`,
  2 danh mục hệ thống "Cập nhật số dư"
- `db/migration/V6__va_loi_bao_mat.sql` — `fn_category_tree` (đã lọc `is_deleted`)
- `db/README.md` mục "Việc phải làm ở tầng ứng dụng" §1-3 — cập nhật số dư trong transaction,
  đối chiếu, **§3 "Suy ra số dư ví theo mốc thời gian" (mới thêm 23/08, kèm SQL mẫu cho D-36)**
- `db/README.md` mục "Trigger có sẵn" — ranh giới cột nào do CSDL sở hữu

### Quy tắc bất biến của dự án
- `CLAUDE.md` mục "Quy tắc nghiệp vụ bất biến" §1-8 — đặc biệt §1 (cộng gộp danh mục con),
  §2 (loại `transfer` khỏi báo cáo), §3 (`amount` luôn dương), §4 (sửa/xoá đúng 3 bước),
  §5 (số dư ví + **đoạn mới về ngày tương lai và lệch nghĩa cột/trường**), §7 (quyền trong SQL)
- `THIET-KE-CSDL.md` §2.1 — số dư ví lưu sẵn + đoạn mới về giao dịch tương lai

### Quyết định phase trước còn hiệu lực
- `.planning/phases/01-nen-tang-xac-thuc/01-CONTEXT.md` — D-10 (`@Idempotent` AOP tường minh),
  D-23 (Testcontainers thật)
- `.planning/phases/02-vi-danh-muc/02-CONTEXT.md` — D-26 (khuôn thay thế di sản, D-30 lặp lại),
  D-27 (điều kiện quyền cả hai vế trong SQL, `group_members.is_active` **BOOLEAN** không phải
  `status='active'`), D-28 (test theo luồng rủi ro), D-29 (tách plan theo ranh giới nghiệp vụ)

</canonical_refs>

<code_context>
## Existing Code Insights

### Tài sản dùng lại được
- `CategoryRepository.findCategoryTree()` — **đã bọc sẵn `fn_category_tree`**, Javadoc ghi thẳng
  "Phase 3". Dùng cho TXN-08, không viết lại điều kiện lọc
- `WalletRepository.adjustBalance(id, delta)` — atomic `UPDATE ... SET balance = balance + :delta`,
  đã có sẵn, không load-modify-save
- `WalletTransferService.lockAndCheckOwnership()` — pessimistic lock + kiểm tra quyền,
  D-33 mở rộng từ đây
- `WalletTransferService` khoá 2 ví theo `UUID.compareTo()` chống deadlock — pattern D-33 kế thừa
- `@Idempotent` (Phase 1) — D-35 dùng lại nguyên
- `IdempotencyTransactionHelper` — **mẫu tham chiếu cho D-31**: bean riêng để `@Transactional`
  đi qua proxy thật

### Pattern đã thiết lập
- Package feature-first: `auth/`, `wallet/`, `category/` + `common/`, `scheduler/`.
  Phase 3 thêm `transaction/{controller,dto,entity,repository,service}`
- Test đặt theo feature, đặt tên `*IntegrationTest`, Testcontainers PostgreSQL thật.
  Hiện **77 test xanh** — D-30 phải giữ nguyên con số này
- `@WebMvcTest` slice test (`GlobalExceptionHandlerTest`) **phải liệt kê tường minh mọi Controller
  mới vào `excludeFilters`** — quên là Spring component-scan kéo controller vào context thiếu bean.
  Đã dính 2 lần (Phase 1 với `RateLimitFilter`, Phase 2 với controller nghiệp vụ).
  **Thêm `TransactionController` vào danh sách đó**
- `spring.jackson.property-naming-strategy=SNAKE_CASE` toàn cục — DTO mới tự động đúng `snake_case`
- Số tiền `Long`, ngày `LocalDate` (cột `DATE`, **không múi giờ, không chuyển đổi gì**)

### Điểm nối
- `WalletTransferService.transfer()` và `.adjustBalance()` — **hai chỗ D-30 phải sửa**
- `WalletResponse` / `WalletDetailResponse` — **hai DTO D-37/TXN-09 phải sửa** (đang map thẳng cột)
- `CategoryRepository.findCategoryTree()` — điểm nối cho TXN-08
- Phase 4 và Phase 5 sẽ gọi `TransactionWriter` (D-31) — thiết kế API của nó cho ổn định ngay

</code_context>

<specifics>
## Specific Ideas

- **Trình tự 3 bước là bất di bất dịch** (`CLAUDE.md` §4): hoàn tác ảnh hưởng CŨ lên ví CŨ →
  ghi giá trị MỚI → áp dụng ảnh hưởng MỚI lên ví MỚI. Tất cả trong **một** DB transaction.
  Ví dụ sai kinh điển phải có test chặn: đổi khoản chi 100k ở ví A thành 80k ở ví B — nếu chỉ trừ
  chênh 20k vào ví B thì ví A vẫn bị trừ oan 100k.
- **Ưu tiên test cao nhất** (theo D-28, tập trung luồng rủi ro không phủ đều CRUD):
  1. Atomicity 3 bước sửa giao dịch đổi cả amount lẫn ví — số dư ví cũ và mới đều đúng tuyệt đối
  2. Bulk có dòng lỗi hệ thống giữa chừng — các dòng khác vẫn giữ (chứng minh D-34/D-35a)
  3. Xoá giao dịch gắn `debt_payments` → 409 (D-32)
  4. Ví có giao dịch tương lai → `current_balance` và `projected_balance` đúng, ví không có →
     **không** trả `projected_balance` (D-37)
  5. Lọc theo danh mục cha cộng gộp con và loại `transfer` (TXN-08)
  6. 77 test Phase 1+2 vẫn xanh sau D-30
- **Quy ước ngôn ngữ:** giao tiếp/commit tiếng Việt; **mọi định danh code tiếng Anh, không ngoại
  lệ kể cả tên method test**.

</specifics>

<deferred>
## Deferred Ideas

- **`DELETE /debts/{id}/payments/{payment_id}`** — API huỷ trả nợ mà D-32 trỏ người dùng sang.
  Đã đặc tả sẵn ở `api/08-SO-NO.md`, **thuộc Phase 4**. Khi xây, phải xoá `debt_payments` để
  trigger tự tính lại rồi mới xoá mềm giao dịch — không tự ghi `paid_amount`.
- **`counts_in_report` trong báo cáo** — Phase 3 lưu và trả đúng cột này, nhưng việc *lọc* theo nó
  khi thống kê thuộc Phase 4 (REPORT-01..05).
- **Hai chỗ tài liệu còn sai về múi giờ báo cáo** (`api/00` mục 13 và `source/server/CLAUDE.md`
  quy tắc 7 vẫn ghi `AT TIME ZONE`) — mâu thuẫn CORE-07. Thuộc Phase 4, đã ghi ở `<deferred>`
  của `01-CONTEXT.md`, nhắc lại để không trôi.
- **Job đối chiếu số dư tự động hằng ngày** — loại khỏi phạm vi theo D-25 (Phase 2). Không hồi sinh
  ở phase này.
- **Mock data của app Flutter chưa tách hai con số số dư** (`lib/core/network/mock/db/mock_db.dart`
  cộng dồn theo nghĩa cột). Không chặn backend — xử lý khi app bật `USE_MOCK=false`.

</deferred>

---

*Phase: 03-giao-dich*
*Context gathered: 2026-08-23*
