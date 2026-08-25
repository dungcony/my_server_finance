# Phase 4: Nghiệp vụ phái sinh & Báo cáo - Discussion Log

> **Chỉ để tra cứu.** Không dùng làm đầu vào cho agent nghiên cứu/lập kế hoạch/thực thi.
> Quyết định nằm ở CONTEXT.md — log này giữ lại các phương án đã cân nhắc.

**Date:** 2026-08-25
**Phase:** 04-nghi-p-v-ph-i-sinh-b-o-c-o
**Areas discussed:** Xuất báo cáo async (REPORT-05), Nhắc nợ & cảnh báo (JOB-04, BUDGET-06),
Debt/Goal + ranh giới trigger, Định kỳ RECUR-03, Báo cáo & hiệu năng, Chia plan cho 6 module

---

## Xuất báo cáo async (REPORT-05)

### Lưu trạng thái export job ở đâu

| Option | Description | Selected |
|--------|-------------|----------|
| Migration V9 tạo bảng `export_jobs` | Đúng ý định REQUIREMENTS.md dòng 31; job sống qua restart | ✓ |
| Lưu trong bộ nhớ (ConcurrentHashMap) | Không cần migration nhưng mất khi restart | |
| Dùng lại bảng `idempotency_keys` | Tái dùng hạ tầng nhưng ngữ nghĩa khác hẳn | |

**Notes:** Bảng `export_jobs` không tồn tại trong V1–V8 — phát hiện khi đối chiếu schema thật (CORE-11).

### Tệp xuất lưu ở đâu, `download_url` trỏ tới đâu

| Option | Description | Selected |
|--------|-------------|----------|
| Đĩa local + endpoint tải có xác thực | Không phụ thuộc dịch vụ ngoài, hợp đồ án | ✓ |
| Lưu BYTEA trong CSDL | Phình CSDL, tốn RAM khi đọc | |
| S3/MinIO | Đúng chuẩn production nhưng thêm dịch vụ phải cài | |

**Notes:** `api/06` ghi `https://cdn.example.com/...` là placeholder — dự án không có CDN thật.

### Dạng `download_url` và cơ chế hết hạn 24h

| Option | Description | Selected |
|--------|-------------|----------|
| Đường dẫn tương đối + JWT sẵn có | Không thêm cơ chế token mới; `expires_at` → 410 GONE | ✓ |
| Token tải riêng trong URL | Chia sẻ link được nhưng lộ dữ liệu tài chính nếu rò rỉ | |

### Sinh tệp bằng gì

| Option | Description | Selected |
|--------|-------------|----------|
| @Async + Apache POI + OpenPDF | Đủ 3 định dạng | |
| Chỉ csv ở phase này | csv không cần thư viện; cơ chế async vẫn làm đầy đủ | ✓ |
| Đồng bộ luôn, bỏ 202 | Sai đặc tả api/06 | |

**Notes:** Người dùng tạm gác câu hỏi này giữa chừng để hỏi lại bối cảnh REPORT-05 là gì,
sau đó quay lại xác nhận "chỉ csv, sửa tài liệu cho khớp".

### Xử lý pdf/excel để không lệch tài liệu

| Option | Description | Selected |
|--------|-------------|----------|
| Trả 501 NOT_IMPLEMENTED + ghi vào api/06 | Code và tài liệu khớp nhau | ✓ |
| Trả 400 UNSUPPORTED_FORMAT | 400 hàm ý "bạn gửi sai", thực tế là "server chưa làm" | |
| Bỏ hẳn tham số `format` | Phá hợp đồng, app nhận csv mà không biết | |

### Job dọn tệp/job quá hạn

| Option | Description | Selected |
|--------|-------------|----------|
| Có — thêm job dọn hằng ngày | Dùng lại khuôn `IdempotencyCleanupJob` | ✓ |
| Không — hoãn sang sau | Giữ phạm vi đúng JOB-01..04 nhưng tệp để lại mãi | |

---

## Nhắc nợ & cảnh báo (JOB-04, BUDGET-06)

### JOB-04 gửi thông báo đi đâu

| Option | Description | Selected |
|--------|-------------|----------|
| Migration tạo bảng `notifications` + endpoint đọc | Kiểm chứng được JOB-04 bằng test thật | ✓ |
| Chỉ ghi log, không lưu DB | Không thêm bảng/API nhưng không kiểm chứng được | |
| Hoãn JOB-04 sang v2 | Mất requirement đã hứa | |

**Notes:** Schema V1–V8 không có bảng nào lưu thông báo, dự án chưa có FCM/email — phát hiện
khi đối chiếu schema thật.

### Phạm vi bảng notifications

| Option | Description | Selected |
|--------|-------------|----------|
| Chỉ nhắc nợ (JOB-04) | Cảnh báo ngân sách giữ đường `GET /budgets/alerts` tính tại chỗ | |
| Cả nhắc nợ lẫn cảnh báo ngân sách | Một hộp thư duy nhất | ✓ |

**Notes:** Claude nêu rủi ro hai nguồn lệch nhau (bản ghi lưu "85% sắp vượt" nhưng người dùng
xoá giao dịch còn 40%). Giải quyết ở câu sau.

### Endpoint đọc thông báo

| Option | Description | Selected |
|--------|-------------|----------|
| GET danh sách + PATCH đánh dấu đã đọc | Đủ để app dựng hộp thư có chấm đỏ | ✓ |
| Chỉ GET danh sách | Không đánh dấu đọc được thì hộp thư vô dụng sau vài ngày | |
| Thêm cả DELETE và unread-count | Phình phạm vi | |

### Tài liệu ghi ở đâu

| Option | Description | Selected |
|--------|-------------|----------|
| Mục mới trong api/08-SO-NO.md | Đọc liền mạch, không thêm file | |
| Tạo file api/11-THONG-BAO.md | Tách bạch, dễ mở rộng | ✓ |

**Notes:** Kéo theo phải sửa bảng "Bản đồ tài liệu" trong CLAUDE.md gốc (đang ghi "api/01 đến api/10").

### Cảnh báo ngân sách sinh lúc nào

| Option | Description | Selected |
|--------|-------------|----------|
| Job hằng ngày sinh, coi là "lịch sử" | Đơn giản nhưng vượt hôm nay mai mới báo | |
| Sinh khi ghi giao dịch, không đợi job | Báo ngay | ✓ |
| Quay lại: chỉ nhắc nợ vào notifications | Không có rủi ro lệch | |

### Đặt ở đâu để không phá TransactionWriter

| Option | Description | Selected |
|--------|-------------|----------|
| Spring event, xử lý sau khi commit | Không đảo chiều phụ thuộc, không chậm luồng ghi, chống trùng bulk ở DB | ✓ |
| Gọi thẳng trong TransactionService | Vẫn trong transaction ghi, bulk gọi 50 lần | |
| Quay lại job hằng ngày | Không đụng đường ghi nhưng chậm 1 ngày | |

**Notes:** Claude nêu quan ngại rõ trước khi khoá — chèn kiểm tra ngân sách vào `TransactionWriter`
sẽ tạo vòng tròn phụ thuộc, chậm luồng ghi (trái nguyên tắc "ghi nhanh hơn quên"), và sinh 50
thông báo trùng khi bulk. Người dùng chọn phương án event giữ được cả ý muốn "báo ngay".

---

## Debt/Goal + ranh giới trigger

**Notes:** Vòng hỏi đầu dùng ngôn ngữ quá kỹ thuật, người dùng yêu cầu giải thích bối cảnh trước.
Claude giải thích lại bằng ví dụ đời thường (cho bạn vay 5 triệu, trả dần 2 lần) rồi hỏi lại.

### Thứ tự huỷ một lần trả nợ

| Option | Description | Selected |
|--------|-------------|----------|
| Xoá dòng trả nợ trước, rồi mới đụng giao dịch | Tránh luật chặn của CSDL, không có khoảnh khắc số liệu sai | ✓ |
| Đụng giao dịch trước | Không được lợi ích gì, dễ vướng RESTRICT | |
| Bạn quyết định | | |

### Trả vượt số còn nợ

| Option | Description | Selected |
|--------|-------------|----------|
| Từ chối ngay, báo lỗi | | |
| Cho phép, coi phần thừa là trả dư | (chọn ở vòng đầu) | |
| Chặn, nhưng gợi ý cách xử lý | Giữ luật CSDL + api/08, thông điệp lỗi hướng dẫn người dùng | ✓ |

**Notes:** Người dùng ban đầu chọn "cho phép trả dư". Claude nêu trở ngại thật: trigger
`fn_debt_payments_sync` (V4) tự `RAISE EXCEPTION`, `api/08` đã đặc tả mã `EXCEEDS_REMAINING_AMOUNT`,
và cho phép vượt sẽ làm "còn nợ bao nhiêu" thành số âm lan ra cả app Flutter. Người dùng chọn
lại phương án chặn kèm thông điệp gợi ý.

### Xoá khoản nợ có giữ lịch sử không

| Option | Description | Selected |
|--------|-------------|----------|
| Xoá hẳn, số dư ví trả về như chưa cho vay | Bảng `debts` không có cột `is_deleted` — thiết kế cố ý | ✓ |
| Thêm cột is_deleted (migration mới) | Thêm cột cho nhu cầu tài liệu chưa nêu; đã có `write-off` | |

### Hai chế độ nạp mục tiêu

| Option | Description | Selected |
|--------|-------------|----------|
| Người dùng chọn rõ trên màn hình | Khớp trường `create_transaction` đã đặc tả ở api/09 §288 | ✓ |
| App tự đoán theo cấu hình mục tiêu | Đoán sai thì tiền bị trừ khỏi ví ngoài ý muốn | |

---

## Định kỳ RECUR-03

### Ngày ghi cho các kỳ bỏ lỡ

| Option | Description | Selected |
|--------|-------------|----------|
| Ghi đúng ngày đáng lẽ phải chạy | Báo cáo theo tháng đúng thực tế; hợp `uq_txn_recurring_date` | ✓ |
| Ghi hết vào ngày hôm nay | Dồn cục làm báo cáo vọt, và UNIQUE chặn mất 7 giao dịch | |

### Lỗi giữa chừng khi sinh nhiều kỳ

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ các kỳ đã sinh, lần sau chạy tiếp | Cùng nguyên tắc D-34 (bulk Phase 3) | ✓ |
| Lỗi một kỳ thì huỷ hết | Kỳ lỗi liên tục thì không kỳ nào vào được, mãi mãi | |

### Khoản định kỳ lỗi kéo dài

| Option | Description | Selected |
|--------|-------------|----------|
| Ghi log, bỏ qua, tiếp tục khoản khác | Một khoản hỏng không làm chết toàn bộ tác vụ | ✓ |
| Tự tắt sau vài lần lỗi | Người dùng không biết → tiền nhà im lặng ngừng ghi | |

---

## Báo cáo & hiệu năng

### Màn Tổng quan lấy dữ liệu thế nào

| Option | Description | Selected |
|--------|-------------|----------|
| Một lần gọi trả hết (đúng api/06) | App mở lên gọi 1 lần, nhanh hơn trên mạng di động | ✓ |
| App tự gọi nhiều endpoint rời | Backend đơn giản hơn nhưng trái đặc tả | |

### Cache kết quả báo cáo

| Option | Description | Selected |
|--------|-------------|----------|
| Không — luôn tính lại | Cùng tinh thần ngân sách "tính tại chỗ" | ✓ |
| Có — lưu tạm vài phút | Ghi giao dịch xong vẫn thấy số cũ, người dùng tưởng app hỏng | |

### Chỗ tài liệu sai múi giờ

| Option | Description | Selected |
|--------|-------------|----------|
| Sửa ngay trong phase này | Đã trôi qua 3 phase; để tiếp thì agent sau đọc vào làm sai | ✓ |
| Để sau | | |

**Notes:** Claude verify bằng grep — chỉ còn `source/server/CLAUDE.md:75`, `api/00` mục 13
đã được sửa ở phase trước.

---

## Chia plan cho 6 module

### Số plan

| Option | Description | Selected |
|--------|-------------|----------|
| 6–7 plan theo ranh giới nghiệp vụ | Mỗi plan một module, kiểm thử độc lập, cùng khuôn D-29 | ✓ |
| 4–5 plan, gộp module nhỏ | Mỗi plan nặng, lỗi ở giữa khó cô lập | |
| 8–10 plan, chia nhỏ hơn | Vụn vặt, trái granularity "coarse" của roadmap | |

### Tác vụ nền gom hay rải

| Option | Description | Selected |
|--------|-------------|----------|
| Gom vào một plan riêng cuối phase | Cả 4 dùng chung một khuôn, làm một lần thống nhất | ✓ |
| Mỗi tác vụ đi kèm module của nó | Dễ thành 4 kiểu đặt lịch khác nhau | |

### Trọng tâm test

| Option | Description | Selected |
|--------|-------------|----------|
| Theo luồng rủi ro (như Phase 2–3) | Ưu tiên BUDGET-08, trigger debt/goal, định kỳ ngày 31 | ✓ |
| Phủ đều mọi endpoint | Tốn công cho CRUD ít rủi ro | |

---

## Claude's Discretion

Người dùng để Claude tự quyết: tên bảng/cột migration mới và số hiệu V9/V10, cấu trúc package con,
tên class/method, tổ chức DTO và cách chống N+1, cách bọc `excludeTransfer()`/`fn_category_tree`,
ánh xạ `v_budget_progress`, cách viết csv, thứ tự task trong plan.

## Deferred Ideas

Xuất pdf/excel; `DELETE /notifications` và `unread-count`; gửi thông báo đẩy thật (FCM/email);
job dọn cảnh báo ngân sách đã hết hiệu lực; JOB-05 (Phase 5); tầng quyền nhóm phủ lên
ngân sách/báo cáo (Phase 5); mock data app Flutter cho module Phase 4.

## Sự cố trong lượt discuss

File `04-DISCUSS-CHECKPOINT.json` ban đầu bị ghi nhầm vào repo gốc `DATN/.planning/` do lệnh dùng
đường dẫn tương đối trong khi shell đang ở thư mục gốc. Người dùng phát hiện, đã xoá sạch
`DATN/.planning/`. Đúng sự cố mà `CLAUDE.md` mục "Ba repo git độc lập" cảnh báo — luôn dùng
đường dẫn tuyệt đối trong cùng một lệnh.
