# Phase 4: Nghiệp vụ phái sinh & Báo cáo - Context

**Gathered:** 2026-08-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Ngân sách (BUDGET-01..08), sổ nợ (DEBT-01..07), giao dịch định kỳ (RECUR-01..03),
mục tiêu tiết kiệm (GOAL-01..04), báo cáo thống kê (REPORT-01..05) và bốn tác vụ nền
hằng ngày (JOB-01..04). Tất cả xây trên `TransactionWriter` (D-31) đã ổn định ở Phase 3 —
**không viết lại logic cập nhật số dư ví ở bất kỳ module nào**.

**Không thuộc phase này:** tầng quyền nhóm gia đình và AI drafts (Phase 5), JOB-05 dọn
`ai_drafts` (Phase 5).

**Hai hạ tầng mới phải xây trong phase này** (schema V1–V8 chưa có, xem D-38 và D-42):
bảng `export_jobs` (REPORT-05) và bảng `notifications` (JOB-04 + BUDGET-06).

</domain>

<decisions>
## Implementation Decisions

### Hạ tầng thông báo — hợp đồng API MỚI (D-38..D-41)

- **D-38: Tạo bảng `notifications` bằng migration mới.**
  `api/08-SO-NO.md` mục 9 ghi JOB-04 "gửi thông báo" nhưng **schema V1–V8 không có bảng nào lưu
  thông báo, và dự án chưa có FCM/email**. Không có nơi lưu thì JOB-04 không kiểm chứng được —
  chỉ log ra console rồi biến mất.
  Bảng thiết kế tổng quát (có cột `type` phân loại), phase này sinh hai loại: nhắc nợ và cảnh báo
  ngân sách. Viết thẳng vào `db/migration/` ở thư mục gốc DATN theo constraint dự án
  (một nguồn sự thật duy nhất, không tạo thư mục migration riêng).

- **D-39: Hai endpoint đọc — `GET /notifications` (phân trang theo `api/00`) và
  `PATCH /notifications/{id}/read`.**
  Đủ để app dựng hộp thư có chấm đỏ. **Không** làm `DELETE` và `unread-count` ở phase này —
  phình phạm vi cho một requirement vốn chỉ yêu cầu "gửi thông báo".

- **D-40: Viết hợp đồng mới vào file `api/11-THONG-BAO.md`.**
  Đây là **API chưa từng có trong `api/*.md`** — theo quy tắc "không tự bịa API", phải đặc tả
  trước khi code. Tạo file mới thay vì nhét vào `api/08` để dễ mở rộng khi có thêm loại thông báo.
  **Hệ quả bắt buộc:** cập nhật bảng "Bản đồ tài liệu" trong `CLAUDE.md` gốc (đang ghi
  "api/01 đến api/10") — trong **cùng** lần thay đổi.

- **D-41: Cảnh báo ngân sách sinh NGAY khi ghi giao dịch, qua Spring event xử lý SAU KHI COMMIT.**
  `TransactionWriter` chỉ bắn một event (`TransactionRecordedEvent`) và **không biết ai nghe nó**.
  Module `budget/` đăng ký `@TransactionalEventListener(phase = AFTER_COMMIT)`.

  **Ba lý do bắt buộc phải làm theo cách này, không gọi trực tiếp:**
  1. **Không đảo chiều phụ thuộc** — `budget/` phụ thuộc `transaction/`; gọi ngược lại tạo vòng tròn.
  2. **Không làm chậm luồng ghi** — listener chạy ngoài transaction ghi, giữ nguyên tắc dự án
     "ghi nhanh hơn quên, dưới 5 giây".
  3. **Bulk 50 dòng (D-34, mỗi dòng một transaction riêng)** sẽ bắn 50 event — chống trùng bằng
     **UNIQUE ở tầng CSDL** trên bảng `notifications` (khoá theo ngân sách + ngày + mức độ),
     không dựa vào kiểm tra ở tầng Java.

  `GET /budgets/alerts` (`api/05` mục 7) **vẫn là nguồn sự thật cho trạng thái hiện tại** —
  tính tại chỗ từ `v_budget_progress`. Bản ghi trong `notifications` là **lịch sử tại thời điểm
  vượt ngưỡng**, giống tin nhắn ngân hàng: không tự sửa lại khi người dùng xoá giao dịch sau đó.
  Chấp nhận có chủ đích, phải ghi rõ trong `api/11-THONG-BAO.md`.

### Xuất báo cáo REPORT-05 (D-42..D-45)

- **D-42: Migration tạo bảng `export_jobs`.**
  Đúng ý định đã ghi ở `REQUIREMENTS.md` dòng 31 ("hoãn tới Phase 4 để thiết kế sát nhu cầu thật").
  Không dùng bộ nhớ (mất khi restart, người dùng đang poll nhận 404 không hiểu vì sao) và
  **không dùng lại `idempotency_keys`** — bảng đó có ngữ nghĩa khác hẳn (cache response theo key),
  nhét export job vào làm rối cả hai.

- **D-43: Tệp lưu trên đĩa máy chủ, thư mục cấu hình được; `download_url` là đường dẫn TƯƠNG ĐỐI
  của chính backend, xác thực bằng JWT sẵn có.**
  `api/06` ghi `https://cdn.example.com/...` — **placeholder, không có CDN thật**.
  `download_url` trả về dạng `/reports/export/{job_id}/download`; app gọi kèm `Bearer` token như
  mọi endpoint khác, backend kiểm tra quyền sở hữu job trước khi trả tệp.
  Hết hạn kiểm tra bằng cột `expires_at` → **410 GONE**. Không sinh token tải riêng trong URL —
  link rò rỉ là lộ dữ liệu tài chính, trái nguyên tắc "riêng tư mặc định".

- **D-44: Phase này CHỈ hỗ trợ `csv`. `pdf`/`excel` trả 501 NOT_IMPLEMENTED.**
  Phần đáng làm về mặt kỹ thuật là **cơ chế bất đồng bộ** (202 + `job_id` + poll trạng thái) —
  làm đầy đủ. pdf/excel chỉ là thêm thư viện định dạng, không thêm giá trị.
  **Bắt buộc sửa `api/06-BAO-CAO.md` mục 7** ghi rõ "giai đoạn này chỉ hỗ trợ csv, pdf/excel trả 501"
  trong cùng lần thay đổi — nếu không, code và tài liệu trôi dạt.

- **D-45: Thêm job dọn tệp/bản ghi export quá hạn hằng ngày** (ngoài JOB-01..04).
  Không dọn thì đĩa đầy dần và `export_jobs` tích luỹ vô hạn. Dùng lại khuôn `@Scheduled` của
  `IdempotencyCleanupJob` (Phase 1) — vài dòng code, không rủi ro.

### Sổ nợ & Mục tiêu — ranh giới trigger (D-46..D-49)

**Nhắc lại ranh giới, đọc trước khi code:** `debts.paid_amount`, `debts.status`,
`savings_goals.saved_amount`, `savings_goals.status` **do trigger V4 sở hữu**. Backend chỉ
chèn/xoá bản ghi `debt_payments`/`goal_contributions`. Muốn biết trạng thái sau khi chèn thì
**đọc lại**, đừng tự suy ra rồi ghi đè.

- **D-46: DEBT-04 huỷ một lần trả nợ — xoá `debt_payments` TRƯỚC, rồi mới xoá mềm giao dịch.**
  Cả hai trong **một** `@Transactional`.
  Trình tự: xoá bản ghi `debt_payments` → trigger tự tính lại `paid_amount` và **tự mở lại**
  `status` từ `settled` về `outstanding` → xoá mềm giao dịch qua đúng luồng 3 bước của Phase 3
  để hoàn tác số dư ví.
  **Lý do thứ tự này:** `debt_payments.transaction_id` có `ON DELETE RESTRICT` + `UNIQUE`
  (`V4__so_no_muc_tieu.sql` §53-67). Xoá mềm (`is_deleted`) không vi phạm RESTRICT, nhưng thứ tự
  này vẫn đúng hơn — không có khoảnh khắc nào `paid_amount` tính cả khoản đã bỏ.
  **Đây chính là API mà D-32 (Phase 3) trỏ người dùng sang** khi chặn xoá giao dịch gắn sổ nợ:
  `DELETE /debts/{id}/payments/{payment_id}`, đã đặc tả ở `api/08-SO-NO.md` §296-309.

- **D-47: Trả vượt số còn nợ — CHẶN, nhưng thông điệp lỗi phải gợi ý cách xử lý.**
  Giữ nguyên luật chặn (trigger `fn_debt_payments_sync` tự `RAISE EXCEPTION`, và `api/08` đã đặc tả
  mã `EXCEEDS_REMAINING_AMOUNT`) — không sửa CSDL, vì cho phép vượt sẽ làm "còn nợ bao nhiêu"
  thành số âm và lan ra mọi màn hình của app Flutter.
  **Validate trước ở tầng service** để trả lỗi 400 sạch sẽ với thông điệp tiếng Việt dạng:
  *"A chỉ còn nợ 3.000.000đ. Ghi trả 3.000.000đ, phần dư ghi thành khoản thu riêng."*
  Trigger vẫn giữ vai trò lưới an toàn ở tầng CSDL nếu có đường ghi nào lọt — hai tầng phòng thủ.

- **D-48: DEBT-06 xoá khoản nợ — xoá CỨNG bản ghi `debts`, giao dịch liên quan xoá MỀM.**
  Bảng `debts` **không có cột `is_deleted`** trong schema V4 (đã kiểm tra) — thiết kế cố ý.
  `debt_payments` có `ON DELETE CASCADE` nên tự biến mất.
  Trình tự: xoá `debts` (cascade dọn `debt_payments`) → xoá mềm giao dịch gốc **và mọi giao dịch
  trả nợ** qua luồng 3 bước → số dư ví hoàn tác đúng.
  Không thêm cột `is_deleted` cho `debts`: nhu cầu "giữ lại để xem" đã có chức năng riêng là
  `write-off` (DEBT-05, chỉ đổi status, không sinh giao dịch).

- **D-49: GOAL-03 hai chế độ nạp — dùng đúng trường `create_transaction` đã đặc tả ở
  `api/09-DINH-KY-MUC-TIEU.md` §285-296.** Không bịa trường mới.
  `create_transaction = true` (mặc định) → yêu cầu goal có `wallet_id`, sinh giao dịch `transfer`
  từ `source_wallet_id` qua `TransactionWriter`, lưu `transaction_id` vào `goal_contributions`.
  `create_transaction = false` → `goal_contributions.transaction_id = NULL`
  (cột đã nullable + `ON DELETE SET NULL`, đúng cho ca này), không ví nào đổi.
  **Không suy đoán từ việc goal có `wallet_id` hay không** — goal có ví vẫn có thể muốn chỉ ghi nhận.

### Giao dịch định kỳ (D-50..D-52)

Ba luật đã đặc tả rõ ở `api/09` §119-125, §181-183, **không phải quyết định lại**:
ngày 29/30/31 dùng ngày cuối tháng đích nhưng **giữ nguyên ngày gốc cho kỳ sau**;
sinh **đủ** giao dịch cho từng kỳ đã qua; chống trùng bằng `uq_txn_recurring_date`.

- **D-50: Kỳ bỏ lỡ ghi ĐÚNG ngày đáng lẽ phải chạy, không dồn vào hôm nay.**
  Vắng 8 tháng → 8 giao dịch với `date` lần lượt 05/01, 05/02… Báo cáo theo tháng đúng thực tế,
  không vọt bất thường ở một tháng.
  **Đây cũng là điều kiện để `uq_txn_recurring_date` hoạt động** — dồn vào một ngày thì ràng buộc
  UNIQUE `(recurring_id, date)` sẽ chặn mất 7 giao dịch.
  Ghi được ngày quá khứ lẫn tương lai vì D-36 đã bỏ luật chặn ngày.

- **D-51: Sinh nhiều kỳ — MỖI KỲ MỘT DB TRANSACTION RIÊNG.**
  Kỳ 5 lỗi thì 4 kỳ trước vẫn giữ; tác vụ ngày mai tự bắt tiếp từ kỳ 5.
  Cùng nguyên tắc D-34 (bulk Phase 3), và tránh ca "kỳ 5 lỗi vĩnh viễn → không kỳ nào vào được".

- **D-52: Khoản định kỳ lỗi kéo dài (ví đã xoá, danh mục đã xoá) — ghi log, bỏ qua, chạy tiếp
  khoản khác. KHÔNG tự tắt `is_enabled`.**
  Một khoản hỏng không được làm chết toàn bộ tác vụ (đúng ghi chú "mỗi job bọc try/catch" trong
  ROADMAP). Không tự tắt vì người dùng không biết → tiền nhà im lặng ngừng ghi.

### Báo cáo (D-53..D-55)

Hai nguyên tắc bắt buộc đã chốt từ `CLAUDE.md` §1-2, **không hỏi lại**: loại `type = 'transfer'`
và cộng gộp danh mục con qua `fn_category_tree`. Nên bọc thành query fragment dùng chung
(`excludeTransfer()`) thay vì rải điều kiện mỗi nơi.

- **D-53: `GET /reports/home` là MỘT endpoint gộp trả đủ dữ liệu màn Tổng quan** — đúng đặc tả
  `api/06` (REPORT-02). App mở lên gọi một lần, không gọi 5 endpoint rời rồi đợi cả 5.

- **D-54: KHÔNG cache kết quả báo cáo — luôn tính lại.**
  Cùng tinh thần với ngân sách ("tính tại chỗ, không lưu `spent_amount`"). Dữ liệu một người dùng
  chỉ vài nghìn dòng. Cache sẽ khiến ghi giao dịch xong mở báo cáo vẫn thấy số cũ — người dùng
  tưởng app hỏng — và thêm cơ chế xoá cache là phình phạm vi.

- **D-55: Sửa `source/server/CLAUDE.md` dòng 75 trong phase này.**
  Dòng đó ghi `AT TIME ZONE 'Asia/Ho_Chi_Minh'` khi group theo ngày/tháng — **sai**, vì cột
  `transactions.date` kiểu `DATE` thuần, không có múi giờ để chuyển đổi. Đây là thiết kế cố ý
  tránh hẳn lớp bug múi giờ (CORE-07, REPORT-01).
  Đã ghi trong `<deferred>` từ 01-CONTEXT.md, trôi qua 3 phase — sửa dứt điểm ở đây.
  (`api/00` mục 13 đã được sửa ở phase trước, chỉ còn một chỗ này.)

### Chia plan và test (D-56..D-58)

- **D-56: 6–7 plan theo ranh giới nghiệp vụ**, mỗi plan một module hoàn chỉnh kiểm thử độc lập
  được. Cùng khuôn D-29 (Phase 2).
  **Thứ tự đề xuất theo phụ thuộc thật:**
  1. Hạ tầng thông báo (bảng `notifications` + 2 endpoint + `api/11-THONG-BAO.md`) — **phải đi đầu**,
     vì cả nhắc nợ (JOB-04) lẫn cảnh báo ngân sách (D-41) đều cần
  2. Ngân sách (BUDGET-01..08, gồm test riêng tư BUDGET-08) — chỉ cần `fn_category_tree` đã có
  3. Sổ nợ (DEBT-01..06)
  4. Mục tiêu tiết kiệm (GOAL-01..04) — cùng kiểu trigger với sổ nợ
  5. Định kỳ (RECUR-01..02)
  6. Báo cáo (REPORT-01..05, gồm `export_jobs`) — đọc dữ liệu mọi module trên, để sau
  7. Tác vụ nền (JOB-01..04 + dọn export) — xem D-57

- **D-57: Gom cả bốn tác vụ nền + job dọn export vào MỘT plan riêng cuối phase.**
  Cả năm dùng chung một khuôn (`@Scheduled` với `zone = "Asia/Ho_Chi_Minh"` đặt thẳng trong
  annotation, bọc try/catch để không crash scheduler pool, ghi log). Làm một lần thống nhất thay vì
  bốn lần mỗi nơi một kiểu. Cần mọi module xong trước — hợp lý để cuối.

- **D-58: Test theo luồng rủi ro, không phủ đều mọi endpoint** (tiếp D-28).
  **Ưu tiên cao nhất:**
  1. **BUDGET-08 riêng tư ngân sách** — B chi 1 triệu vào danh mục X, ngân sách **cá nhân** của A
     cùng danh mục hiện `spent_amount = 0`. Chốt vĩnh viễn lỗ hổng `v_budget_progress` đã vá ở V6
  2. Trigger sổ nợ/mục tiêu tự tính đúng — gồm ca **mở lại** `settled → outstanding` khi huỷ trả nợ
     và `completed → in_progress` khi rút nạp (D-46, GOAL-04)
  3. Định kỳ ngày 31 qua tháng 2 (giữ ngày gốc cho kỳ sau) và bắt kịp nhiều kỳ bỏ lỡ (D-50)
  4. Kỳ lỗi giữa chừng — các kỳ trước vẫn giữ (D-51)
  5. Báo cáo loại `transfer` + cộng gộp danh mục con
  6. Toàn bộ test Phase 1+2+3 vẫn xanh sau khi thêm event bắn từ `TransactionWriter` (D-41)

### Bổ sung sau research (D-59..D-61)

Ba quyết định chốt ngày 25/08/2026 sau khi `04-RESEARCH.md` đối chiếu tài liệu với schema thật.

- **D-59: `POST /reports/export` thay vì `GET`, và sửa `api/06-BAO-CAO.md` trong cùng phase.**
  `api/06` hiện ghi `GET /reports/export`, nhưng endpoint này **tạo bản ghi `export_jobs`** — có
  side-effect, trái quy ước REST của chính dự án (`api/00` mục 14: tạo mới → `POST`). `GET` cũng
  không dùng được header `Idempotency-Key` và có thể bị proxy/cache lặp lại.
  Hợp đồng chốt:
  - `POST /reports/export` → `202` + `{ job_id, status }`, hỗ trợ `Idempotency-Key`
  - `GET /reports/export/{job_id}` → poll trạng thái
  - `GET /reports/export/{job_id}/download` → tải tệp (giữ nguyên D-43)
  Cập nhật `api/06-BAO-CAO.md` mục 7 trong cùng lần thay đổi.

- **D-60: Xoá `AT TIME ZONE 'Asia/Ho_Chi_Minh'` khỏi CẢ HAI file `api/*.md`, không chỉ
  `source/server/CLAUDE.md`.**
  D-55 giả định `api/00` mục 13 "đã được sửa ở phase trước" — **giả định này sai**. Research đối
  chiếu `git log` xác nhận cả `api/00-QUY-UOC-CHUNG.md` mục 13 lẫn `api/06-BAO-CAO.md`
  (mục "Ba nguyên tắc chung #3") **vẫn còn nguyên câu sai**, chưa từng được sửa.
  Vậy có **ba** chỗ phải sửa dứt điểm ở phase này: `source/server/CLAUDE.md` dòng 75,
  `api/00-QUY-UOC-CHUNG.md` mục 13, `api/06-BAO-CAO.md`. Lý do vẫn như D-55: `transactions.date`
  kiểu `DATE` thuần không có múi giờ, chuyển đổi sẽ ra kết quả sai.
  Lưu ý repo: hai file `api/*.md` nằm ở **repo gốc `DATN/`**, không phải repo `source/server/` —
  phải commit riêng ở repo gốc.

- **D-61: Mọi query báo cáo của Phase 4 hard-code `counts_in_report = TRUE`.**
  Cột này thêm ở `V8`. Khác với `TransactionRepository` của Phase 3, nơi nó là **tham số lọc**
  người dùng tự chọn khi xem sổ giao dịch — ở báo cáo nó là **hằng số**, không nhận từ query param.
  Không nhắc trong CONTEXT.md ban đầu, nhưng là nguyên tắc thứ ba của báo cáo, ngang hàng với
  "loại `transfer`" và "cộng gộp danh mục con".

### Claude's Discretion

Không cần hỏi lại, làm theo chuẩn dự án, `api/*.md` và pattern Phase 1–3:
- Tên bảng/cột cụ thể trong migration mới (`notifications`, `export_jobs`) và số hiệu V9/V10
- Cấu trúc package con trong `budget/`, `debt/`, `goal/`, `recurring/`, `report/`, `notification/`
  (feature-first như các module hiện có)
- Tên class/method — **tiếng Anh, không ngoại lệ kể cả tên method test**
- Tổ chức DTO, cách chống N+1 trong truy vấn báo cáo
- Cách bọc `excludeTransfer()` và `fn_category_tree` thành fragment dùng chung
- Ánh xạ view `v_budget_progress` (native query hay DTO projection)
- Thư viện/cách viết csv (viết tay hay dùng thư viện nhỏ)
- Thứ tự task trong từng plan (nhưng plan hạ tầng thông báo phải đi đầu theo D-56)
- BUDGET-05 gợi ý hạn mức: công thức đã rõ ở `api/05` §273-279, không cần bàn thêm

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Hợp đồng API — nguồn sự thật
- `api/00-QUY-UOC-CHUNG.md` — khung `{success,data}`/`{success,error}`, phân trang,
  `Idempotency-Key`, bảng mã lỗi chung. **Phải bổ sung mã cho 501 pdf/excel (D-44) và
  410 link tải hết hạn (D-43)**
- `api/05-NGAN-SACH.md` — **đặc tả chính module ngân sách**. Mục 7 (`GET /budgets/alerts`,
  bảng điều kiện `severity` §329-334), §132-141 (`projected_depletion_date` — chỉ trả khi ngày
  dự báo rơi TRƯỚC `end_date`), §246-279 (`suggested_limit` = TB 3 kỳ × 1.05, `null` nếu <1 tháng)
- `api/06-BAO-CAO.md` — REPORT-01..05. Mục 7 (xuất báo cáo — **phải sửa theo D-44**),
  §358 (tháng chưa kết thúc vẫn trả nhưng app hiển thị khác)
- `api/08-SO-NO.md` — **đặc tả chính module sổ nợ**. §296-309 (`DELETE /debts/{id}/payments/{payment_id}`,
  API mà D-32 Phase 3 trỏ sang), §340-348 (ba trường không sửa được: `principal_amount`/`type`/`wallet_id`;
  xoá nợ hoàn tác TOÀN BỘ), mục 9 §350-365 (nhắc nợ: 7 ngày / 1 ngày / quá hạn mỗi 7 ngày),
  bảng mã lỗi cuối file
- `api/09-DINH-KY-MUC-TIEU.md` — **đặc tả chính định kỳ + mục tiêu**. §113-125 (tạo định kỳ,
  **ngày 29/30/31 giữ nguyên ngày gốc**), §139-145 (tạm dừng, `run-now` KHÔNG đổi `next_run_date`),
  §158-183 (tác vụ nền + **bắt kịp kỳ bỏ lỡ sinh đủ từng kỳ**), §250-251 (ngưỡng
  `feasible`/`challenging` theo % thu nhập), **§285-325 (`create_transaction` hai chế độ nạp — D-49,
  và cảnh báo trigger sở hữu `saved_amount`/`status`)**
- `api/02-VI.md` — hai trường số dư (`current_balance` vs `projected_balance`), cần khi báo cáo
  và màn Tổng quan hiển thị số dư
- **`api/11-THONG-BAO.md` — CHƯA TỒN TẠI, phải tạo trong phase này (D-40)**

### Schema thật — đối chiếu TRƯỚC khi tin api/*.md (CORE-11)
- `db/migration/V3__ngan_sach.sql` — bảng `budgets`: **KHÔNG có cột `spent_amount`** (cố ý, tính
  tại chỗ), `ck_bud_period` (`week|month|quarter|year`), `ck_bud_owner` (cá nhân HOẶC nhóm),
  **`ex_bud_no_overlap`** (EXCLUDE gist chặn ngân sách trùng — BUDGET-03 dựa vào đây),
  `idx_bud_renew` (index cho JOB-02), trigger `trg_budgets_validate` (chỉ danh mục chi)
- `db/migration/V6__va_loi_bao_mat.sql` §45-90 — **`v_budget_progress` bản ĐÃ VÁ**: có sẵn
  `spent_amount`, `remaining`, `ratio`, `status` (3 mức, ngưỡng 0.8), `days_remaining`,
  đã cộng gộp danh mục con VÀ đã có điều kiện phạm vi quyền. **BUDGET-08 test chốt chính chỗ này**
- `db/migration/V4__so_no_muc_tieu.sql` — **đọc trước khi code debt/goal**:
  §53-67 `debt_payments` (`transaction_id` **ON DELETE RESTRICT + UNIQUE**),
  §78-110 `fn_debt_payments_sync` (**tự RAISE EXCEPTION khi vượt nợ gốc** — D-47;
  giữ nguyên `written_off`; tự mở lại `outstanding`),
  §143-158 `goal_contributions` (`transaction_id` **nullable + ON DELETE SET NULL** — D-49),
  §163-188 `fn_goal_contributions_sync` (giữ nguyên `cancelled`; tự mở lại `in_progress`)
- `db/migration/V2__giao_dich.sql` §50-84 — `recurring_transactions`: `next_run_date` là cột then
  chốt tác vụ nền quét, `last_run_date` chống ghi trùng khi chạy lại, `idx_rec_due`,
  `ck_rec_type` (**chỉ `expense|income`, không có `transfer`**)
- `db/migration/V2__giao_dich.sql` — `uq_txn_recurring_date` **UNIQUE (recurring_id, date)**,
  ràng buộc chống trùng mà D-50 dựa vào
- `db/migration/V6__va_loi_bao_mat.sql` §105+ — `fn_category_tree` (đã lọc `is_deleted`)
- `db/README.md` mục **"Trigger có sẵn"** — ranh giới cột nào do CSDL sở hữu
- `db/README.md` mục "Việc phải làm ở tầng ứng dụng" §1-3 — gồm §3 "Suy ra số dư ví theo mốc
  thời gian" (cần cho báo cáo)

### Quy tắc bất biến của dự án
- `CLAUDE.md` mục "Quy tắc nghiệp vụ bất biến" — **§1 (cộng gộp danh mục con) và §2 (loại
  `transfer` khỏi báo cáo) là hai lỗi dễ mắc nhất của phase này**; §4 (sửa/xoá 3 bước — debt/goal
  gọi lại, không viết lại); §6 (ngân sách tính tại chỗ); §7 (quyền trong SQL)
- `source/server/CLAUDE.md` — quy tắc riêng backend. **Dòng 75 SAI về múi giờ, phải sửa (D-55)**;
  quy tắc "không viết trigger" chỉ áp dụng cho `wallets.current_balance`
- `THIET-KE-CSDL.md` — thiết kế logic, lý do từng bảng

### Quyết định phase trước còn hiệu lực
- `.planning/phases/03-giao-dich/03-CONTEXT.md` — **D-31 (`TransactionWriter` là điểm nối bắt buộc
  cho debt/goal/recurring — thiết kế đã tính sẵn cho Phase 4)**, D-32 (chặn xoá giao dịch gắn sổ nợ
  → API huỷ trả nợ thuộc phase này), D-34/D-35a (mỗi dòng một transaction riêng — khuôn cho D-51),
  D-36/D-37 (ngày tương lai, hai trường số dư)
- `.planning/phases/02-vi-danh-muc/02-CONTEXT.md` — D-27 (điều kiện quyền cả hai vế trong SQL,
  **`group_members.is_active` BOOLEAN** không phải `status='active'`), D-28 (test theo luồng rủi ro),
  D-29 (tách plan theo ranh giới nghiệp vụ)
- `.planning/phases/01-nen-tang-xac-thuc/01-CONTEXT.md` — D-10 (`@Idempotent` AOP), D-23 (Testcontainers thật)

</canonical_refs>

<code_context>
## Existing Code Insights

### Tài sản dùng lại được
- **`TransactionWriter`** (`transaction/`, D-31) — bean hạ tầng `@Component`, một method
  `@Transactional` duy nhất, ghi bản ghi + cập nhật số dư ví. **Debt/Goal/Recurring gọi lại nó,
  tuyệt đối không viết lại logic cập nhật ví.** Đây là điểm mở rộng cho D-41 (bắn event)
- `CategoryRepository.findCategoryTree()` — đã bọc sẵn `fn_category_tree`. Dùng cho ngân sách và
  mọi truy vấn báo cáo theo danh mục
- `WalletRepository.adjustBalance(id, delta)` — atomic UPDATE, không load-modify-save
- `WalletTransferService.lockAndCheckOwnership()` + khoá theo `UUID.compareTo()` chống deadlock —
  cần khi mục tiêu nạp tiền thật (2 ví)
- `@Idempotent` (Phase 1, D-10) — gắn cho mọi `POST` tạo mới của phase này
- `IdempotencyCleanupJob` (`scheduler/`) — **khuôn tham chiếu duy nhất cho `@Scheduled` hiện có**;
  D-57 và D-45 dùng lại khuôn này
- `GlobalExceptionHandler` + `BusinessException` — thêm mã lỗi mới của phase vào đây

### Pattern đã thiết lập
- Package feature-first: `auth/`, `wallet/`, `category/`, `transaction/` + `common/`, `scheduler/`.
  Phase 4 thêm `budget/`, `debt/`, `goal/`, `recurring/`, `report/`, `notification/`
- Test theo feature, tên `*IntegrationTest`, **Testcontainers PostgreSQL thật** (không H2)
- **`@WebMvcTest` slice test (`GlobalExceptionHandlerTest`) phải liệt kê TƯỜNG MINH mọi Controller
  mới vào `excludeFilters`** — quên là Spring component-scan kéo controller vào context thiếu bean.
  **Đã dính 3 lần (Phase 1 `RateLimitFilter`, Phase 2 controller nghiệp vụ, Phase 3
  `TransactionController`). Phase này thêm ~6 controller — dễ dính lần thứ tư**
- `spring.jackson.property-naming-strategy=SNAKE_CASE` toàn cục — DTO mới tự đúng `snake_case`
- Số tiền `Long`; ngày `LocalDate` (cột `DATE`, **không múi giờ, không chuyển đổi gì**)
- Tách service riêng khi cần tránh transaction self-invocation (bài học
  `IdempotencyTransactionHelper`, `WalletTransferService`)

### Điểm nối
- `TransactionWriter` — nơi bắn `TransactionRecordedEvent` cho D-41; **sửa ở đây động tới mọi
  test Phase 1–3, phải chạy lại toàn bộ**
- `v_budget_progress` — ánh xạ qua native query, không viết lại logic tính trong Java
- `GET /budgets/alerts` (tính tại chỗ) và bảng `notifications` (lịch sử) — **hai nguồn cố ý khác
  nhau**, đừng cố đồng bộ chúng (D-41)
- `scheduler/` — nơi đặt 5 job mới (D-57)
- `db/migration/` ở thư mục gốc DATN — nơi viết V9/V10, **không tạo thư mục migration riêng**

</code_context>

<specifics>
## Specific Ideas

- **Bốn tài liệu phải sửa/tạo trong phase này** (theo quy tắc "cập nhật tài liệu trong cùng lần
  thay đổi"):
  1. **Tạo** `api/11-THONG-BAO.md` (D-40) — hợp đồng mới, viết TRƯỚC khi code
  2. **Sửa** `api/06-BAO-CAO.md` mục 7 — chỉ csv, pdf/excel trả 501 (D-44)
  3. **Sửa** `source/server/CLAUDE.md` dòng 75 — bỏ `AT TIME ZONE` (D-55)
  4. **Sửa** `CLAUDE.md` gốc bảng "Bản đồ tài liệu" — đang ghi "api/01 đến api/10" (D-40)
  Thêm mã lỗi mới vào `api/00-QUY-UOC-CHUNG.md` mục 6 (501, 410).

- **Ranh giới trigger là điểm dễ sai nhất của phase.** Bốn cột `debts.paid_amount`,
  `debts.status`, `savings_goals.saved_amount`, `savings_goals.status` **backend không bao giờ ghi**.
  Nếu backend cũng ghi thì trigger `SUM()` ghi đè — "chạy đúng một cách tình cờ", cực khó debug.
  Muốn biết trạng thái sau khi chèn thì **đọc lại bản ghi**.

- **Đừng nhầm với quy tắc số dư ví.** "Không viết trigger CSDL" chỉ áp dụng cho
  `wallets.current_balance` — backend chủ động cập nhật trong transaction. Debt/goal thì ngược lại.

- **Hai lỗi kinh điển phải có test chặn** (`CLAUDE.md` §1-2):
  quên cộng gộp danh mục con (báo cáo thiếu tiền nhưng số vẫn "trông hợp lý"), và
  quên loại `type = 'transfer'` (nạp mục tiêu/chuyển ví bị đếm thành thu chi).

- **Quy ước ngôn ngữ:** giao tiếp/commit tiếng Việt; **mọi định danh code tiếng Anh, không ngoại lệ
  kể cả tên method test**.

</specifics>

<deferred>
## Deferred Ideas

- **Xuất pdf/excel** (D-44) — chỉ csv ở phase này. Khi làm: Apache POI cho excel, OpenPDF cho pdf,
  và gỡ ghi chú 501 khỏi `api/06`.
- **`DELETE /notifications` và `GET /notifications/unread-count`** (D-39) — chưa cần cho JOB-04.
- **Gửi thông báo đẩy thật (FCM/email)** — bảng `notifications` là hộp thư trong app; kênh đẩy ra
  ngoài thuộc milestone sau.
- **Bản ghi cảnh báo ngân sách không tự sửa lại khi người dùng xoá giao dịch** (D-41) — chấp nhận
  có chủ đích. Nếu sau này thấy phiền, cân nhắc job dọn cảnh báo đã hết hiệu lực.
- **JOB-05 dọn `ai_drafts` discarded 90 ngày** — thuộc Phase 5, dùng lại khuôn `@Scheduled` của D-57.
- **Tầng quyền nhóm gia đình phủ lên ngân sách/báo cáo** — Phase 5. Phase này BUDGET-08 đã test
  chốt phần riêng tư cá nhân, Phase 5 thêm vế `group_id`.
- **Mock data của app Flutter chưa có module nào của Phase 4** — xử lý khi app bật `USE_MOCK=false`.

</deferred>

---

*Phase: 04-nghi-p-v-ph-i-sinh-b-o-c-o*
*Context gathered: 2026-08-25*
