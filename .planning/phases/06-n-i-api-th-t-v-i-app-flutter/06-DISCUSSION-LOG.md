# Phase 6: Nối API thật với app Flutter - Discussion Log

> **Chỉ để tra cứu.** Không dùng làm đầu vào cho agent nghiên cứu, lập kế hoạch hay thực thi.
> Quyết định nằm ở CONTEXT.md — log này giữ lại các phương án đã cân nhắc.

**Date:** 2026-08-29
**Phase:** 06-n-i-api-th-t-v-i-app-flutter
**Areas discussed:** Phụ thuộc Phase 5, Base URL, Xử lý lệch hợp đồng, Mức kiểm chứng, Màn AI, Dữ liệu thử, Gap Phase 4, Cờ mock, Tổ chức plan, Thử biên, Thiếu API

---

## Phụ thuộc Phase 5 (nhóm `ai`)

| Option | Description | Selected |
|--------|-------------|----------|
| Nối 6 nhóm, hoãn ai | Đúng như roadmap dự liệu; tiêu chí #6 dời sang sau | ✓ |
| Làm AI tối thiểu trong Phase 6 | Mở rộng phạm vi, code AiController rule-based ngay | |
| Dừng, làm Phase 5 trước | Quay lại chạy Phase 5 cho đủ Group + AI | |

**User's choice:** Nối 6 nhóm, hoãn ai
**Notes:** Xác nhận bằng quét codebase — backend không có `GroupController`/`AiController`.

---

## Base URL

| Option | Description | Selected |
|--------|-------------|----------|
| /v1 (khớp app hiện tại) | Backend thêm context-path /v1, app không sửa mã | ✓ |
| /api/v1 | Backend dùng /api/v1, phải sửa app_config.dart | (chọn đầu, đã đổi) |
| Để tôi kiểm api/00-QUY-UOC-CHUNG.md | Đọc đặc tả gốc rồi theo | |

**User's choice:** ban đầu chọn `/api/v1`, sau khi đối chiếu `api/00-QUY-UOC-CHUNG.md` dòng 37 (`https://api.<tên-miền>/v1` — `api.` là subdomain, không phải path segment) thì **đổi sang `/v1`**.
**Notes:** Lựa chọn `/api/v1` mâu thuẫn với quyết định "sửa backend cho khớp api/*.md" ở câu sau, nên đã hỏi lại và chốt `/v1`.

---

## Xử lý khi backend lệch hợp đồng

| Option | Description | Selected |
|--------|-------------|----------|
| Sửa backend cho khớp api/*.md | api/ là nguồn sự thật; app và mock đã dựng theo đó | ✓ |
| Sửa app cho khớp backend | Ưu tiên không đụng backend đã có test | |
| Tuỳ từng chỗ, ghi lại quyết định | Đánh giá từng ca — rủi ro mất nhất quán | |

**User's choice:** Sửa backend cho khớp api/*.md

---

## Mức tự động hoá kiểm chứng

| Option | Description | Selected |
|--------|-------------|----------|
| Thử tay + truy vấn CSDL | Theo đúng roadmap, không viết test mới | |
| Thêm integration_test Flutter | Test chạy trên máy ảo với backend thật, lặp lại được | ✓ |
| Thêm test HTTP phía backend | MockMvc/RestAssured mô phỏng chuỗi call app gửi | |

**User's choice:** Thêm integration_test Flutter

---

## Màn AI khi USE_MOCK=false

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ mock riêng cho /ai | MockInterceptor vẫn chặn /ai/* kể cả khi cờ tắt | ✓ |
| Để lỗi thật hiện ra | Không ngoại lệ, gọi /ai/* → 404 | |
| Chặn lối vào màn AI | Ẩn/vô hiệu hoá nút AI khi USE_MOCK=false | |

**User's choice:** Giữ mock riêng cho /ai

---

## Dữ liệu thử

| Option | Description | Selected |
|--------|-------------|----------|
| Script seed SQL/HTTP riêng | Dựng sẵn user/ví/danh mục/giao dịch, chạy lại được | |
| Tạo tay qua UI app | Kiểm luôn được luồng ghi, nhưng không lặp lại tự động | |
| Cả hai | Seed dựng nền, giao dịch tạo qua UI | ✓ |

**User's choice:** Cả hai

---

## Gap Phase 4 (DebtReminderWorker, ExportAsyncRunner)

| Option | Description | Selected |
|--------|-------------|----------|
| Bỏ qua — ngoài phạm vi | Cả hai thuộc /debts và export, app chưa dùng | |
| Đóng trước khi nối | Backend hoàn chỉnh rồi mới nối app | ✓ |

**User's choice:** Đóng trước khi nối
**Notes:** Mở rộng phạm vi Phase 6 — trở thành điều kiện tiên quyết (D-17).

---

## Cờ USE_MOCK mặc định

| Option | Description | Selected |
|--------|-------------|----------|
| Đổi mặc định thành false | Theo §9 CHUYEN-SANG-API-THAT.md, giữ nguyên core/network/mock/ | ✓ |
| Giữ mặc định true | Tiện phát triển giao diện, đổi ở phase cuối | |

**User's choice:** Đổi mặc định thành false

---

## Tổ chức plan trên hai repo

| Option | Description | Selected |
|--------|-------------|----------|
| Plan chia theo nhóm API | Bám trình tự 8 bước, mỗi plan có thể đụng cả hai repo | ✓ |
| Plan chia theo repo | Tách 'sửa backend' và 'sửa app' — phá vỡ trình tự nối-rồi-thử | |

**User's choice:** Plan chia theo nhóm API

---

## Kiểm chứng idempotency và refresh-gom

| Option | Description | Selected |
|--------|-------------|----------|
| Tắt mạng máy ảo + log backend | Thực tế nhất, khó tái lập đều | |
| integration_test mô phỏng | Lặp lại được, đúng tinh thần đã chọn | |
| Cả hai | integration_test + một lần thử tay cắt mạng thật | ✓ |

**User's choice:** Cả hai

---

## Backend thiếu hẳn endpoint app đang gọi

| Option | Description | Selected |
|--------|-------------|----------|
| Bổ sung ngay trong Phase 6 | Thuộc 7 nhóm phạm vi thì code luôn | ✓ |
| Dừng, hỏi trước | Mỗi endpoint thiếu là một điểm dừng | |

**User's choice:** Bổ sung ngay trong Phase 6

---

## Claude's Discretion

- Cơ chế danh sách ngoại lệ `/ai/*` trong `MockInterceptor`
- Hình thức script seed (SQL thuần / Flyway callback dev / chuỗi HTTP)
- Cách đo số lượt `/auth/refresh`
- Chia bao nhiêu plan cho 8 bước

## Deferred Ideas

- Nối nhóm `ai` sau khi Phase 5 xong
- Nối `/debts`, `/goals`, `/recurring`, `/notifications`, `/groups` — Phase 7 của app
- Code Phase 5 backend (Group + AI)
