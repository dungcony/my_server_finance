# Phase 3: Giao dịch — Nhật ký thảo luận

**Ngày:** 2026-08-23
**Mục đích:** Lưu vết đầy đủ hỏi–đáp của `/gsd-discuss-phase 3`. File này chỉ dành cho người đọc
(rà soát, đối chiếu về sau), **không** được agent hạ nguồn (researcher, planner, executor) đọc.
Quyết định chính thức nằm ở `03-CONTEXT.md`.

---

## Bối cảnh trước lượt discuss

Ngay trước lượt này, trong cùng phiên làm việc, người dùng đã chốt ba việc về **giao dịch ngày
tương lai** (thành D-36, D-37 trong CONTEXT.md):

1. Bỏ hẳn luật chặn `date > hôm nay` — "người dùng chủ động ghi nên sẽ phải chấp nhận việc đó"
2. Cách tính số dư: giữ cột lưu sẵn, **trừ ngược** phần sau mốc thời gian (phương án C trong 3 phương án A/B/C được trình bày)
3. Tên trường API: `current_balance` giữ nghĩa "tiền thật đang có", thêm `projected_balance`

Người dùng ban đầu đề xuất "đổi logic tính `current_balance` có filter theo ngày xem" (tương đương
phương án B — bỏ cột, tính động). Sau khi được giải thích rằng bỏ cột đồng nghĩa mất khả năng phát
hiện sai lệch (phép đối chiếu chỉ có ý nghĩa khi có **hai nguồn độc lập** để so), người dùng chọn
phương án C — cho cùng kết quả linh hoạt nhưng giữ được cột làm điểm neo.

Tài liệu đã cập nhật trong lượt đó: `api/04-GIAO-DICH.md` (mục 7 mới), `api/02-VI.md`,
`db/README.md` (§3 mới), `THIET-KE-CSDL.md` §2.1, `CLAUDE.md` §5, `ROADMAP.md`, `REQUIREMENTS.md`
(thêm TXN-09), model `wallet.dart` + `transaction_request.dart` của app Flutter.

---

## Chuẩn bị (không hỏi người dùng)

**Prior context đã nạp:** `PROJECT.md`, `REQUIREMENTS.md`, `STATE.md`, `01-CONTEXT.md`,
`02-CONTEXT.md`. Todos khớp phase: 0.

**Quyết định phase trước được mang sang, không hỏi lại:** D-10 (`@Idempotent` AOP),
D-23 (Testcontainers thật), D-26 (khuôn thay thế di sản), D-27 (quyền cả hai vế trong SQL),
D-28 (test theo luồng rủi ro), D-29 (tách plan theo ranh giới nghiệp vụ).

**Scout codebase phát hiện:**
- `WalletTransferService` tự `INSERT INTO transactions` bằng JdbcTemplate ở 2 chỗ — chưa có entity
- `CategoryRepository.findCategoryTree()` đã bọc `fn_category_tree`, Javadoc ghi "Phase 3"
- 4 index trên `transactions` đã có sẵn trong V2
- `transactions` **không có cột `debt_id`**; liên kết một chiều từ `debt_payments`
- Trigger `trg_debt_payments_sync` chỉ bắn trên `debt_payments`, không bắn khi `transactions.is_deleted` đổi
- 77 test xanh, pattern `*IntegrationTest` theo feature

**Gray area được đề xuất:** 4 vùng. Người dùng chọn **cả 4**.

---

## Vùng 1 — Gộp hai đường ghi giao dịch

**Vấn đề trình bày:** Phase 2 ghi giao dịch bằng SQL trực tiếp vì chưa có entity. Phase 3 tạo
service thật → hai đường song song. Điểm gãy cụ thể: logic trừ ngược của TXN-09 sẽ phải viết hai
lần, hoặc chỉ viết một chỗ và chỗ kia âm thầm sai.

**Lựa chọn đưa ra:** gộp ngay ở plan đầu / gộp cuối phase / không gộp.

**Người dùng chọn:** gộp ngay ở plan đầu tiên → **D-30**.

**Hệ quả kỹ thuật không hỏi lại (→ D-31):** tách `TransactionWriter` thành bean riêng — lớp mỏng
chỉ lo ghi bản ghi + cập nhật số dư, không biết nghiệp vụ gọi nó. Lý do: `adjustBalance` có luật
riêng (`source='adjustment'`, danh mục hệ thống V8) sẽ làm service chung phình ra; và bean riêng
giúp `@Transactional` đi qua proxy thật (bài học `IdempotencyTransactionHelper` Phase 1).

---

## Vùng 2 — Xoá giao dịch gắn sổ nợ (TXN-06)

**Người dùng hỏi lại 2 lần** trước khi quyết. Lần đầu: "tôi chưa hiểu lắm, cần bạn cung cấp thêm
bối cảnh". Lần hai: "bạn giải thích đơn giản hơn giúp tôi qua ví dụ".

**Cách giải thích cuối cùng có hiệu quả** — kể chuyện cụ thể, không thuật ngữ:
> Thứ Hai cho Nam vay 2 triệu. Thứ Sáu Nam trả 500k → app ghi 2 chỗ: Sổ nợ ("đã trả 500k/2tr") và
> Sổ giao dịch ("Thu +500.000"). Thứ Bảy phát hiện nhầm, vào **Sổ giao dịch** bấm xoá.
> Dòng ở Sổ giao dịch biến mất, ví trừ lại 500k — nhưng Sổ nợ vẫn ghi "Nam đã trả 500k".
> **Hai màn hình nói hai chuyện khác nhau**, và chỉ phát hiện ra lúc đòi nợ Nam.

**Lý do kỹ thuật:** `paid_amount` do trigger sở hữu; trigger chỉ bắn khi `debt_payments` đổi, không
bắn khi `transactions.is_deleted` đổi. Mà Phase 3 chưa có module sổ nợ (Phase 4 mới làm).

**Người dùng chọn:** báo "hãy huỷ ở Sổ nợ" → **D-32**.

**Xác minh sau khi chốt:** `api/08-SO-NO.md` §296-309 **đã đặc tả sẵn**
`DELETE /debts/{id}/payments/{payment_id}` và mô tả đúng cơ chế trigger — D-32 khớp hoàn toàn với
tài liệu có sẵn, không hứa một API không tồn tại.

---

## Vùng 3 — Khoá ví khi sửa giao dịch (TXN-05)

**Người dùng hỏi lại 1 lần:** "tôi chưa hiểu bối cảnh phần này".

**Cách giải thích có hiệu quả** — dựng từ vấn đề gốc lên:
1. Hai người cùng tiêu một ví lúc — nếu "đọc rồi ghi" thì người sau đè người trước (*lost update*)
2. Lớp bảo vệ 1: để CSDL tự cộng trừ (`balance = balance + delta`) — Phase 2 đã làm
3. Lớp bảo vệ 2: khoá ví — cần khi phải **đọc số dư rồi mới quyết định**
4. Cái bẫy: hai người chuyển tiền ngược chiều A→B và B→A, mỗi người giữ một ví đợi ví kia → **kẹt cứng**
5. Cách Phase 2 giải: khoá theo **thứ tự UUID cố định**, không theo vai trò nguồn/đích
6. Câu hỏi thật của Phase 3: sửa `transfer A→B` thành `transfer C→D` chạm **4 ví** — khoá cả 4 hay bỏ khoá?

**Người dùng chọn:** khoá tất cả ví liên quan, sắp theo UUID → **D-33**.

---

## Vùng 4 — Bulk và idempotency (TXN-04)

Hai câu hỏi tách riêng, người dùng trả lời thẳng cả hai không cần hỏi lại.

**Câu 1 — phạm vi transaction.** Bối cảnh: 12 dòng OCR, dòng 7 lỗi dữ liệu, dòng 10 lỗi hệ thống.
Mỗi dòng riêng → 10 dòng vào được. Cả lô chung → 0 dòng, user chụp lại hoá đơn từ đầu.
**Chọn:** mỗi dòng một transaction riêng → **D-34**.

**Câu 2 — Idempotency-Key.** Một key cả lô (dùng lại `@Idempotent` Phase 1) hay mỗi dòng một key
(mịn hơn nhưng phải viết cơ chế mới, `api/00` không đặc tả, app quản 50 key).
**Chọn:** một key cho cả lô → **D-35**.

**Hệ quả kỹ thuật không hỏi lại (→ D-35a):** `@Idempotent` chạy trong `@Transactional`; để nguyên
thì cả lô lại thành một transaction, **triệt tiêu D-34**. Plan phải để mỗi dòng chạy transaction
riêng, `@Idempotent` chỉ cache kết quả cuối. Phải có test chứng minh.

---

## Việc sửa tài liệu phát sinh trong lượt discuss

Scout phát hiện **3 lỗi** trong bảng mã lỗi cuối `api/04-GIAO-DICH.md`. Người dùng chọn sửa ngay
thay vì để planner làm.

| Lỗi | Xử lý |
|---|---|
| Còn mã `FUTURE_DATE_NOT_ALLOWED` (400) | Gỡ bỏ — luật ngày tương lai đã huỷ, đây là chỗ bỏ sót của lượt sửa trước |
| `FORBIDDEN` (403) cho "giao dịch của người khác" | Đổi thành `NOT_FOUND` (404) — mâu thuẫn CORE-05 (thiếu quyền trả 404 để không lộ tồn tại bản ghi) |
| Thiếu mã cho D-32 | Thêm `TRANSACTION_LINKED_TO_DEBT` (409) vào cả `api/04` và bảng chung `api/00` mục 6 |

Đồng thời viết lại **mục 9 (Xoá giao dịch)** của `api/04`: bước 3 cũ ghi "cập nhật lại `paid_amount`"
— trái quy tắc trigger sở hữu cột. Thay bằng bước chặn + đoạn giải thích vì sao chặn thay vì tự dọn.

Một lỗi tự phát hiện và sửa: link `[API sổ nợ](07-SO-NO.md)` sai — file thật là `08-SO-NO.md`
(`07-AI.md` mới là số 7).

---

## Tổng kết

**8 quyết định:** D-30 → D-37 (kèm D-35a là hệ quả kỹ thuật của D-34+D-35).
**Người dùng hỏi lại 3 lần** (vùng 2 hai lần, vùng 3 một lần) — đều ở các vùng dính cơ chế CSDL
(trigger sở hữu cột, pessimistic lock, deadlock). Cách giải thích hiệu quả nhất là **kể một câu
chuyện cụ thể có nhân vật và mốc thời gian**, rồi mới nêu lý do kỹ thuật.
**Không có scope creep** — thảo luận nằm trọn trong ranh giới phase.

---

*Phase: 03-giao-dich*
*Ghi lại: 2026-08-23*
