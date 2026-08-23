---
date: "2026-08-23 10:14"
promoted: false
---

Phase 2 — tính năng kiểm kê số dư ví (cập nhật số dư theo thực tế). Tài liệu hiện CHƯA có, cần bổ sung khi thảo luận Phase 2. Người dùng đếm tiền thật, nhập số thực tế, hệ thống sinh giao dịch bù phần chênh gán danh mục "Cập nhật số dư". Quyết định đã chốt: (1) seed HAI danh mục hệ thống cùng tên "Cập nhật số dư", một type=expense một type=income, giống cách Cho vay/Thu nợ đang làm; (2) người dùng tự chọn khoản điều chỉnh có tính vào báo cáo hay không, bằng checkbox trong màn xem chi tiết giao dịch — không áp quy tắc cứng. Hệ quả cần chốt thêm: bảng transactions chưa có cột lưu lựa chọn này (cần thêm kiểu counts_in_report), và mọi truy vấn báo cáo phải xét thêm điều kiện đó, tương tự cách đang loại type='transfer' — chạm cả schema lẫn Phase 4. Lưu ý phân biệt: POST /wallets/{id}/reconcile hiện có trong api/02-VI.md là công cụ phát hiện BUG hệ thống (tự tính lại từ giao dịch, chạy tự động hằng ngày), KHÁC hoàn toàn với kiểm kê của người dùng — không nhét chung một endpoint. Câu ở api/02-VI.md dòng 218 đã nói đúng hướng "muốn chỉnh số dư cho khớp thực tế thì tạo một giao dịch điều chỉnh", chỉ là chưa có endpoint và chưa có danh mục.


---

**Bổ sung 23/08 — hình dung UI của người dùng (chưa chốt):**

Người dùng hình dung: màn Sổ giao dịch → nút 3 chấm trên header → mục "Điều chỉnh số dư"
→ popup hiện số dư hiện tại, sửa được thành số thực tế, kèm toggle bật/tắt tính vào báo cáo.

Đối chiếu tài liệu: KHÔNG có gì trong số này tồn tại. Màn Sổ giao dịch theo
THIET-KE-TONG-QUAN.md mục 8 và design/README.md dòng 153 chỉ có: chọn ví · số dư · bộ lọc
kỳ · tóm tắt vào/ra/chênh lệch · danh sách gom theo ngày. Không có menu 3 chấm ở header.
Danh sách 14 màn phụ cũng không có màn nào cho việc này.

Ba câu cần chốt lúc discuss:
1. Đặt ở đâu — màn Ví (kiểm kê luôn gắn MỘT ví cụ thể + thời điểm HIỆN TẠI, còn Sổ giao dịch
   có thể đang xem tất cả ví / lọc theo kỳ cũ nên mơ hồ: chỉnh ví nào, số dư thời điểm nào?),
   hay màn Sổ giao dịch như người dùng hình dung, hay cả hai (Ví là chính, Sổ giao dịch là
   lối tắt đã chọn sẵn ví đang xem)?
2. Người dùng nhập SỐ DƯ MỚI (app tự tính chênh) hay nhập thẳng PHẦN CHÊNH? Người dùng mô tả
   theo hướng nhập số dư mới — khớp thói quen kiểm kê.
3. Toggle "tính vào báo cáo" mặc định bật hay tắt? Bật thì tổng chi đầy đủ nhưng biểu đồ có
   khoản lạ; tắt thì biểu đồ sạch nhưng tổng chi thấp hơn thực tế.


---

**Bổ sung 23/08 (2) — soi lại schema thật, đánh giá NHẸ HƠN nhiều so với ghi chú đầu:**

Đọc `db/migration/V2__giao_dich.sql` dòng 89-134. Kết luận sửa lại:

1. **Lưu số dư sau điều chỉnh: KHÔNG cần làm gì.** Kiến trúc `số dư = initial_balance +
   tổng giao dịch` khiến việc sinh giao dịch bù tự làm số dư khớp. Không ghi đè cột nào,
   không phá phép đối chiếu của `POST /wallets/{id}/reconcile`.

2. **Cột `source` đã có sẵn chỗ phân loại nguồn** — `VARCHAR(20) DEFAULT 'manual'`, ràng buộc
   `ck_txn_source CHECK (source IN ('manual','text','ocr','auto'))`. Giao dịch điều chỉnh chỉ
   cần thêm một giá trị (vd `'adjustment'`) vào CHECK này. Không phải đẻ khái niệm mới.

3. **Chênh = 0 thì KHÔNG tạo giao dịch** — ràng buộc `ck_txn_amount CHECK (amount > 0)` là cứng,
   không lách được. Backend phải xử lý nhánh này tường minh.

4. **Ràng buộc `ck_txn_shape` đã hợp lệ sẵn** cho giao dịch điều chỉnh: type expense/income thì
   bắt buộc có `category_id` và `destination_wallet_id` phải NULL — đúng hình dạng của khoản
   điều chỉnh. Không cần nới ràng buộc.

5. **Cột cho toggle là thứ DUY NHẤT thật sự cần thêm.** Rủi ro không nằm ở việc thêm cột mà ở
   chỗ mọi truy vấn báo cáo phải xét thêm điều kiện (hiện đang lọc `type != 'transfer'`), sót
   một chỗ là ra số sai kiểu "trông vẫn hợp lý" — đúng loại lỗi CLAUDE.md cảnh báo ở quy tắc
   1 và 2. **Cách né: đặt cột DEFAULT true cho mọi giao dịch**, chỉ giao dịch điều chỉnh mới
   có thể false. Truy vấn cũ chưa cập nhật vẫn ra đúng cho gần như toàn bộ dữ liệu.

Ghi chú đầu nói "chạm cả schema lẫn Phase 4" là nói quá. Thực tế: 1 cột mới + 1 giá trị thêm
vào CHECK + 2 dòng seed danh mục.


---

**CHỐT 23/08 — người dùng xác nhận:** chỉ cần thêm ĐÚNG MỘT cột "tính vào báo cáo hay không"
vào bảng `transactions`. Không có thay đổi schema nào khác.

Cách thực hiện khi tới Phase 2:
- Tạo file migration MỚI `V8__...sql` — Flyway không cho sửa V1-V7 đã chạy (lệch checksum),
  quy tắc ghi ở `db/README.md` dòng 261.
- Cột đặt `NOT NULL DEFAULT true` để mọi truy vấn báo cáo hiện có vẫn ra đúng kết quả kể cả
  khi chưa kịp cập nhật điều kiện lọc.
- Trong cùng V8 thêm luôn: giá trị mới vào `ck_txn_source` (vd `'adjustment'`) và 2 dòng seed
  danh mục hệ thống "Cập nhật số dư" (một expense, một income).

Ba câu UI ở phần trên (đặt ở màn nào, nhập số dư mới hay phần chênh, toggle mặc định) vẫn
CHƯA chốt — thuộc phase giao diện, không chặn Phase 2 backend.
