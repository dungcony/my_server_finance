# Phase 6: Nối API thật với app Flutter - Context

**Gathered:** 2026-08-29
**Status:** Ready for planning

<domain>
## Phase Boundary

App Flutter chạy `--dart-define=USE_MOCK=false` gọi backend Spring Boot thật, cho các nhóm API mà giao diện đang thực sự gọi, với ba quy tắc nghiệp vụ bất biến (cộng gộp danh mục con, loại `transfer` khỏi báo cáo, sửa/xoá hoàn tác 3 bước) được kiểm chứng bằng phép thử thật trên máy ảo Pixel 7 — không phải chỉ "gọi được", mà "trả đúng".

**Trong phạm vi — 6 nhóm nối lần này:**
`auth` · `wallets` · `categories` · `transactions` · `budgets` · `reports`

**Hoãn sang sau (quyết định D-01):** nhóm `ai` — phụ thuộc Phase 5 chưa code.

**Ngoài phạm vi:** `/debts`, `/goals`, `/recurring`, `/notifications`, `/groups` — app chưa dựng màn hình nào dùng, không kiểm chứng được qua UI.

</domain>

<decisions>
## Implementation Decisions

### Phụ thuộc Phase 5 và nhóm `ai`

- **D-01:** Nối 6 nhóm trước, **hoãn nhóm `ai`**. Backend hiện không có `GroupController`/`AiController` — Phase 5 (Nhóm gia đình & Trợ lý AI) chưa được code. Roadmap đã dự liệu tình huống này.
- **D-02:** Màn AI của app khi `USE_MOCK=false` vẫn **giữ mock riêng cho đường `/ai/*`**. `MockInterceptor` cần một danh sách đường dẫn ngoại lệ vẫn bị chặn kể cả khi cờ mock tắt; các nhóm còn lại ra mạng thật. Gỡ ngoại lệ này khi Phase 5 xong.
- **D-03:** Success Criteria #6 của roadmap (duyệt bản nháp AI có sửa → ghi `user_corrections`) **không kiểm chứng được trong phase này**. Dời sang phase nối `ai` sau khi Phase 5 hoàn thành. Ghi rõ khi đóng phase, không âm thầm đánh dấu đạt.

### Base URL

- **D-04:** Prefix chốt là **`/v1`**, không phải `/api/v1`. Căn cứ: `api/00-QUY-UOC-CHUNG.md` §2 dòng 37 ghi `https://api.<tên-miền>/v1` — `api.` là **subdomain**, không phải path segment.
- **D-05:** Backend thêm `server.servlet.context-path: /v1` trong `application.yml`. Hiện backend chưa cấu hình gì nên controller phục vụ trần ở `/auth`, `/wallets`, … (đã xác nhận: không có dòng `context-path` nào trong `src/main/resources/`).
- **D-06:** App **không sửa mã** — `app_config.dart` đã mặc định `http://10.0.2.2:8080/v1`, đúng đặc tả sẵn.
- **D-07:** Sửa dòng ví dụ sai `http://10.0.2.2:8080/api/v1` trong `source/app/CHUYEN-SANG-API-THAT.md` §1 thành `/v1`.
- **D-08:** Giữ `10.0.2.2` — đây là cách máy ảo Android gọi về `localhost` của máy phát triển, không đổi thành `localhost`.

### Xử lý khi backend lệch hợp đồng

- **D-09:** `api/*.md` là **nguồn sự thật**. Backend trả lệch (thiếu trường, sai kiểu, sai tên) → **sửa backend**. App và mock đã dựng theo đặc tả, không đổi app để chiều backend.
- **D-10:** Chỉ khi `api/*.md` thực sự sai (mâu thuẫn với `db/migration/V*.sql` hoặc nghiệp vụ) mới cập nhật tài liệu — và cập nhật trong cùng lần thay đổi, không để trôi dạt.
- **D-11:** Phát hiện backend **thiếu hẳn** một endpoint trong 6 nhóm phạm vi (ví dụ `/budgets/suggestion`, `/reports/by-category-group`) → **bổ sung ngay trong Phase 6**. Không có nó thì app không chạy end-to-end được, nên đó là việc của phase này chứ không phải điểm dừng để hỏi.

### Kiểm chứng

- **D-12:** Ngoài phép thử tay trên máy ảo, **viết `integration_test` Flutter** chạy với backend thật cho ba phép thử nghiệp vụ ở §2 của `CHUYEN-SANG-API-THAT.md`. Mục tiêu: kiểm chứng lặp lại được, không phụ thuộc trí nhớ người thử.
- **D-13:** Tiêu chí #4 (idempotency) và #5 (gom refresh) kiểm bằng **cả hai cách**: `integration_test` chủ động gửi 2 POST cùng `Idempotency-Key` và bắn nhiều request song song với access token hết hạn; **kèm** một lần thử tay cắt mạng thật trên máy ảo (airplane mode/`adb`) để xác nhận hành vi trên thiết bị.
- **D-14:** Kiểm số bản ghi bằng **truy vấn CSDL trực tiếp**, không chỉ nhìn giao diện. Đếm lượt `/auth/refresh` bằng log backend.

### Dữ liệu thử

- **D-15:** Dùng **cả script seed lẫn tạo tay**. Script seed (ở `source/server`) dựng nền cố định: user thử, 2 ví, cây danh mục "Ăn uống" → "Cà phê", một ngân sách. Giao dịch thì tạo qua UI app để kiểm luôn luồng ghi.
- **D-16:** Script seed phải **chạy lại được** (idempotent hoặc reset sạch) — `integration_test` ở D-12 phụ thuộc vào việc dựng lại đúng cùng một tình huống.

### Điều kiện tiên quyết

- **D-17:** **Đóng 2 gap Phase 4 trước khi bắt đầu nối** — `DebtReminderWorker` và `ExportAsyncRunner` (STATE.md ghi Phase 4 GAPS FOUND, 5/7 success criteria). Backend phải ở trạng thái hoàn chỉnh rồi mới nối app, tránh lẫn lộn nguyên nhân khi gỡ lỗi.

### Tổ chức công việc

- **D-18:** Plan chia **theo nhóm API**, bám đúng trình tự 8 bước ở §8 của `CHUYEN-SANG-API-THAT.md`: `/auth/me` → nhóm chỉ đọc (`/wallets`, `/categories`, `/transactions`) → nhóm ghi (`POST /transactions`) → `PUT`/`DELETE` → báo cáo & ngân sách. Mỗi plan có thể đụng cả hai repo.
- **D-19:** `source/server` và `source/app` là **hai repo git độc lập**. Commit riêng từng repo nhưng thuộc cùng một plan. Trước mỗi lệnh GSD có ghi dữ liệu phải `cd` bằng đường dẫn tuyệt đối và kiểm `phase_name` trả về.
- **D-20:** Sau mỗi bước **chạy lại phép thử tương ứng ở §2** ngay, không dồn tới cuối phase.

### Cờ mock

- **D-21:** Sau khi nối xong, đổi `AppConfig.useMock` `defaultValue` thành **`false`**. Chạy dữ liệu mẫu phải truyền `--dart-define=USE_MOCK=true`.
- **D-22:** **Không xoá `core/network/mock/`** — giữ nguyên và vẫn chạy được, làm mốc đối chiếu khi nghi backend trả sai khung (§9 của `CHUYEN-SANG-API-THAT.md`, Success Criteria #7).

### Cập nhật tài liệu khi đóng phase

- **D-23:** Cập nhật cột "Backend phải làm gì" trong `CHUYEN-SANG-API-THAT.md` thành ghi chú **chỗ thực tế đã vấp**.
- **D-24:** Mọi sai lệch tài liệu/API phát hiện trong lúc nối phải sửa lại ở `api/*.md` trong cùng phase.
- **D-25:** Bảng Progress trong `.planning/ROADMAP.md` đang lỗi thời (ghi Phase 3/4 "Not started" trong khi plan đã tick `[x]`) — sửa lại khi đóng phase.

### Claude's Discretion

- Cơ chế cụ thể của danh sách ngoại lệ `/ai/*` trong `MockInterceptor` (D-02): hằng số danh sách đường dẫn, hay một cờ `MOCK_AI_ONLY` riêng.
- Hình thức script seed (D-15): SQL thuần, Flyway callback riêng cho profile `dev`, hay một chuỗi lời gọi HTTP.
- Cách đo số lượt `/auth/refresh` (D-14): log level, bộ đếm metric, hay đọc bảng `refresh_tokens`.
- Chia bao nhiêu plan cho 8 bước — có thể gộp các bước nhỏ.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Trình tự nối và phép thử (đọc trước tiên)
- `source/app/CHUYEN-SANG-API-THAT.md` — bảng đối chiếu từng điểm cuối (mock giả lập gì ↔ backend phải làm gì), ba phép thử nghiệp vụ §2, trình tự 8 bước §8, quy tắc giữ mock §9
- `source/app/lib/core/network/mock/mock_router.dart` — **danh sách chính xác điểm cuối giao diện đang thực sự gọi**, nguồn sự thật cho phạm vi phase

### Hợp đồng API (nguồn sự thật, D-09)
- `api/00-QUY-UOC-CHUNG.md` — §2 base URL `/v1`, §4 khung `{success, data}`/`{success, error}`, mã lỗi, phân trang, `Idempotency-Key`
- `api/01-XAC-THUC.md` — login/register/refresh/logout/me
- `api/02-VI-TIEN.md` — wallets + summary + transfer, phân biệt `current_balance` vs `projected_balance`
- `api/03-DANH-MUC.md` — cây danh mục 2 cấp
- `api/04-GIAO-DICH.md` — list, by-date, CRUD
- `api/05-NGAN-SACH.md` — budgets + summary + suggestion
- `api/06-BAO-CAO.md` — home, daily-trend, by-category-group, by-category

*(Kiểm lại tên tệp thực tế trong `api/` khi lập kế hoạch — đánh số 01–11, tên tệp tự giải thích nghiệp vụ.)*

### Quy tắc nghiệp vụ bất biến
- `CLAUDE.md` §"Quy tắc nghiệp vụ bất biến" — 8 quy tắc, đặc biệt #1 cộng gộp danh mục con, #2 loại `transfer`, #4 sửa/xoá 3 bước
- `db/README.md` §"Suy ra số dư ví theo mốc thời gian" — cột CSDL `current_balance` ≠ trường API `current_balance`, giải thích phép trừ ngược và `projected_balance`
- `db/migration/V*.sql` — **đối chiếu trước khi tin `api/*.md`** (đã có 4 lỗi tìm ra theo cách này)

### Cấu hình app
- `source/app/lib/core/config/app_config.dart` — `baseUrl` mặc định `/v1` (đúng, không sửa), `useMock` `defaultValue` cần đổi thành `false` (D-21)
- `source/app/README.md`, `source/app/STATE.md` — cấu trúc app, đã dựng tới đâu

### Trạng thái backend
- `source/server/.planning/STATE.md` — Phase 4 GAPS FOUND, 2 gap phải đóng trước (D-17)
- `source/server/.planning/ROADMAP.md` §"Phase 6" — bối cảnh, phạm vi, 8 success criteria
- `source/server/CLAUDE.md` — stack, package layout, quy tắc riêng backend

</canonical_refs>

<code_context>
## Existing Code Insights

### Tài sản dùng lại được

**Phía app — đã dựng sẵn để nối dễ:**
- `MockInterceptor` chặn ở tầng Dio, **không có nhánh `if (useMock)` nào trong nghiệp vụ** — repository/provider/UI chạy đúng một đường dẫn mã
- `AuthInterceptor` đã gộp nhiều yêu cầu cùng hết hạn thành **một** lần refresh
- `ApiClient` đã tự gắn `Idempotency-Key` cho mọi POST; `TransactionWriter` giữ nguyên khoá qua các lần thử lại
- `TokenStorage` dùng EncryptedSharedPreferences
- App đã xong 5/7 phase: 4 màn chính + màn phụ, 8 feature (`account`, `ai`, `auth`, `budget`, `category`, `report`, `transaction`, `wallet`)

**Phía backend — đã có:**
- 10 controller: `Auth`, `Budget`, `Category`, `Debt`, `Goal`, `Notification`, `Recurring`, `Report`, `Transaction`, `Wallet`
- Idempotency AOP + bảng `idempotency_keys` (Phase 1)
- JWT rotation + reuse detection trên `refresh_tokens` (Phase 1)
- `fn_category_tree` gọi qua repository dùng chung (Phase 2)
- Response wrapper `{success, data}` + `@RestControllerAdvice` (Phase 1)

### Điểm lệch đã phát hiện — phải xử lý

| Điểm | Hiện trạng | Xử lý |
|---|---|---|
| Prefix URL | Backend **không có** `server.servlet.context-path` → phục vụ trần `/auth`, `/wallets`. App gọi `/v1/auth` | D-05: thêm `context-path: /v1` |
| Ví dụ trong doc app | `CHUYEN-SANG-API-THAT.md` §1 ghi `/api/v1` — sai so với `api/00-QUY-UOC-CHUNG.md` | D-07: sửa thành `/v1` |
| `GroupController`, `AiController` | **Không tồn tại** — Phase 5 chưa code | D-01/D-02: hoãn `ai`, giữ mock riêng |
| Bảng Progress ROADMAP | Ghi Phase 3/4 "Not started" nhưng plan đã tick `[x]` | D-25: sửa khi đóng phase |

### Giả định app đang đặt vào backend — sai thì app vẫn chạy mà mất an toàn

1. **Refresh token dùng một lần** — backend phải rotation + reuse detection. Đã làm ở Phase 1, cần kiểm chứng thật qua app.
2. **Idempotency-Key trả lại phản hồi cũ trong 24h**, không xử lý lần nữa.
3. **`user_corrections`** — backend bỏ qua thì **không có lỗi nào hiện ra**, dữ liệu lặng lẽ mất. (Hoãn kiểm cùng nhóm `ai`, D-03.)
4. **Cột CSDL ≠ trường API** — `current_balance` (tiền thật đến hết hôm nay) vs `projected_balance` (chỉ trả khi ví có giao dịch tương lai). App đã có mô hình riêng cho hai trường.

### Điểm nối

- `application.yml` (backend) — thêm `context-path`
- `app_config.dart` (app) — đổi `useMock` `defaultValue` cuối phase
- `mock_interceptor.dart` / `mock_router.dart` (app) — thêm danh sách ngoại lệ `/ai/*`
- `source/app/integration_test/` — thư mục đã tồn tại, thêm test nối backend thật

</code_context>

<specifics>
## Specific Ideas

**Ba phép thử nghiệp vụ (§2 của `CHUYEN-SANG-API-THAT.md`) — kiểm chứng qua UI app trên backend thật:**

1. **Cộng gộp danh mục con:** tạo cha "Ăn uống" có con "Cà phê" → ghi khoản chi vào "Cà phê" → lọc theo "Ăn uống" → khoản đó **phải** xuất hiện.
2. **Chuyển tiền không vào báo cáo:** ghi nhớ tổng chi tháng → chuyển 500k ví A sang ví B → tổng chi **giữ nguyên**, chỉ số dư hai ví đổi.
3. **Sửa giao dịch hoàn tác 3 bước:** ghi chi 100k ở ví A → sửa thành 80k ở ví B → ví A **hoàn đủ 100k**, ví B trừ 80k. Sai kinh điển: chỉ trừ chênh 20k vào ví B, để ví A bị trừ oan 100k.

Cả ba nếu backend làm sai thì **app vẫn chạy bình thường và số vẫn trông hợp lý** — không có lỗi nào hiện ra. Đây là kiểu sai nguy hiểm nhất, nên phải thử chứ không được suy luận.

**Máy ảo:** Pixel 7 API 36. Backend chạy trên máy thật, app gọi qua `10.0.2.2`.

</specifics>

<deferred>
## Deferred Ideas

- **Nối nhóm `ai`** (`/ai/parse-text`, `/ai/drafts`, duyệt bản nháp + `user_corrections`) — phụ thuộc Phase 5. Làm ngay sau khi Phase 5 xong; lúc đó gỡ ngoại lệ mock `/ai/*` (D-02) và kiểm chứng Success Criteria #6 (D-03).
- **Nối `/debts`, `/goals`, `/recurring`, `/notifications`, `/groups`** — backend đã có (trừ groups) nhưng app chưa dựng màn hình. Thuộc Phase 7 của app.
- **Code Phase 5 backend** (`GroupController`, `AiController`, test riêng tư nhóm bằng Testcontainers) — phase riêng, không gộp vào đây.

</deferred>

---

*Phase: 06-n-i-api-th-t-v-i-app-flutter*
*Context gathered: 2026-08-29*
