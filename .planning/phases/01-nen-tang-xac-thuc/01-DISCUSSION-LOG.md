# Phase 1 — Nhật ký thảo luận

**Ngày:** 2026-08-22
**Chế độ:** discuss (tương tác)

File này là bản ghi đầy đủ hỏi–đáp để tra cứu về sau. **Downstream agent không đọc file này** — quyết định đã chốt nằm ở `01-CONTEXT.md`.

---

## Chọn vùng thảo luận

**Hỏi:** Đặc tả `api/*.md` đã khoá gần hết quyết định nghiệp vụ của Phase 1. Chọn vùng xám kỹ thuật muốn bàn.

**Lựa chọn đưa ra:**
1. Gửi email reset password (AUTH-06 không có tài liệu nói dùng gì)
2. Cấu hình môi trường & bí mật
3. Phạm vi test Phase 1
4. Idempotency & rate limit — mức triển khai

**Người dùng chọn:** (2) và (4).
Vùng (1) và (3) chuyển sang mục "Claude's Discretion" trong CONTEXT với đề xuất mặc định (D-22, D-23) thay vì bỏ trống.

---

## Vùng 1 — Cấu hình môi trường & bí mật

| Hỏi | Lựa chọn đưa ra | Người dùng chọn |
|---|---|---|
| JWT secret + DB creds lưu ở đâu? | (a) Biến môi trường có default dev *(khuyến nghị lúc đó)* · (b) Biến môi trường bắt buộc, không default · (c) `application-local.yml` gitignore | **(b)** — fail-fast khi thiếu |
| Chia profile Spring? | (a) `dev` + `test` *(khuyến nghị)* · (b) `dev` + `test` + `prod` · (c) một file duy nhất | **(a)** |
| Postgres dev lấy từ đâu? | (a) Postgres cài sẵn trên máy · (b) Docker Compose · (c) cả hai | **(b)** |
| Nạp biến môi trường thế nào? | (a) `.env` + docker-compose đọc chung *(khuyến nghị)* · (b) IDE run configuration · (c) export thủ công ở shell | **(a)** |
| Migration cho Testcontainers? | (a) Cùng `db/migration/` gốc chạy V1→V7 *(khuyến nghị)* · (b) schema rút gọn riêng cho test | **(a)** |
| V6/V7 đã viết sẵn — task đầu là gì? | (a) Verify bằng cách chạy thật *(khuyến nghị)* · (b) đọc kỹ lại trước khi chạy · (c) coi như xong, bỏ qua | **(a)** |

**Ghi chú trong lúc bàn:** chọn (b) cho câu 1 kết hợp với Docker Compose kéo theo hệ quả — lần chạy đầu sẽ fail-fast nếu chưa chuẩn bị `.env` và container. Vì thế thêm D-05: Phase 1 bắt buộc giao kèm `README.md` mô tả trình tự khởi động.

---

## Vùng 2 — Idempotency & rate limit

**Diễn biến:** người dùng dừng lại trước khi trả lời, cho biết chưa đủ kiến thức nền về hai khái niệm này. Claude giải thích trước:

- **Idempotency-Key** giải quyết bài toán mạng rớt sau khi server đã ghi DB nhưng trước khi response về tới app → app retry → ghi trùng giao dịch. Cách sửa: app sinh key trước khi gửi, retry dùng lại đúng key cũ; server tra bảng `idempotency_keys`, gặp key đã `completed` thì trả lại `response_body` cũ thay vì chạy lại nghiệp vụ.
- **Ba chỗ chen ngang trong Spring** theo thứ tự request đi qua: Filter → Interceptor → AOP → Controller. So sánh đánh đổi từng chỗ.
- **Rate limit** chống dò mật khẩu và spam; thuật toán token bucket của Bucket4j; phân biệt rõ CORE-04 (đếm request theo IP/user) với AUTH-07 (đếm lần sai mật khẩu theo tài khoản) — hai cơ chế độc lập, đều cần.

Sau đó người dùng yêu cầu Claude đưa đề xuất kèm lý do để duyệt cả gói thay vì trả lời từng câu.

**Đề xuất đưa ra:**

| Câu | Đề xuất | Lý do tóm tắt |
|---|---|---|
| Cơ chế gắn Idempotency | AOP `@Idempotent` trên method | Rủi ro "quên gắn annotation" nhẹ hơn "quên loại trừ endpoint" — quên loại trừ `/auth/login` khiến lần đăng nhập thứ hai nhận lại token cũ đã cache. Ngoài ra `api/00` viết "POST **tạo mới**", tức đặc tả đã phân biệt POST-tạo-mới với POST-hành-động |
| Key đang `processing` | Trả 409 ngay | Trường hợp hiếm (hai request chồng nhau vài trăm ms); "chờ rồi trả kết quả chung" phải giải quyết timeout + cạn thread pool, không đáng |
| Rate limit Phase 1 | Đủ cả 3 tầng ngay | Mục tiêu phase là "hạ tầng dùng lại cho mọi phase sau"; chi phí chênh lệch chỉ là bảng cấu hình thay hằng số cứng |
| Header `X-RateLimit-*` | Luôn trả mọi response | `api/00` in ví dụ `Remaining: 117` — con số đó chỉ có nghĩa trên response thành công |

**Tra cứu giữa chừng** (`api/00-QUY-UOC-CHUNG.md` mục 6 và 13), phát hiện hai điều:

1. **Không có mã lỗi cho "key đang processing".** Bảng mã chung chỉ có `DUPLICATE` (409). Đây là chỗ đặc tả thiếu → theo quy tắc dự án phải hỏi thay vì bịa. Đề xuất thêm mã mới `REQUEST_IN_PROGRESS` (409) vào `api/00`, vì app cần phân biệt: `DUPLICATE` thì báo lỗi cho người dùng, `REQUEST_IN_PROGRESS` thì im lặng retry.
2. **Mâu thuẫn tài liệu ngoài phạm vi phase:** `api/00` mục 13 và `source/server/CLAUDE.md` quy tắc 7 vẫn ghi gom nhóm báo cáo theo giờ Việt Nam bằng `AT TIME ZONE`, mâu thuẫn với CORE-07/REPORT-01 đã sửa. Ghi vào `<deferred>` cho Phase 4.

**Người dùng chọn:** duyệt cả 4 đề xuất, gồm cả việc thêm mã mới `REQUEST_IN_PROGRESS` vào `api/00`.

---

## Scope creep

Không có. Thảo luận giữ nguyên trong phạm vi Phase 1.

---

*Nhật ký ghi ngày 2026-08-22*
