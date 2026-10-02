# Kế Hoạch Tích Hợp API Xem Chi Tiết Giao Dịch (GET /v1/groups/{id}/transactions/{txnId})

## 1. Mục tiêu
- Tích hợp endpoint duy nhất còn thiếu của module Group: `GET /v1/groups/{groupId}/transactions/{txnId}` (lấy chi tiết giao dịch kèm danh mục và danh sách thành viên tham gia chia tiền `participants`).
- Hoàn thiện trọn vẹn **25/25 API** của module Group trên trang static kiểm thử.

---

## 2. Các file sẽ chỉnh sửa
1. `src/main/resources/static/css/group-test.css`:
   - Thêm style cho `modal-overlay` và `modal-content` (responsive, tối ưu hiển thị danh sách chia tiền).
2. `src/main/resources/static/pages/group-test.html`:
   - Thêm cột "Thao Tác" vào bảng Tất cả giao dịch nhóm (`#allTxnTableBody`).
   - Thêm cấu trúc HTML Popup Modal `#txnDetailModal` để hiển thị thông tin chi tiết giao dịch.
3. `src/main/resources/static/js/group-test.js`:
   - Thêm hàm `showTransactionDetail(txnId)`: Gọi API `GET /v1/groups/{groupId}/transactions/{txnId}` và render dữ liệu lên modal.
   - Thêm hàm `closeTransactionDetailModal()`: Đóng modal (hỗ trợ nút Đóng và click ra ngoài backdrop).
   - Thêm nút `🔍 Chi tiết` vào các bảng:
     + Bảng Giao dịch chờ duyệt (`pendingTxnTableBody`)
     + Bảng Giao dịch của tôi (`myTxnTableBody`)
     + Bảng Tất cả giao dịch nhóm (`allTxnTableBody`)

---

## 3. Thiết kế giao diện Modal Chi Tiết Giao Dịch

Modal sẽ hiển thị đầy đủ các thông tin:
- **Tiêu đề:** `🔍 Chi Tiết Giao Dịch` kèm mã giao dịch (UUID) và nút `✕`.
- **Thông số chính (Grid):**
  - Loại giao dịch (`type`): EXPENSE / CONTRIBUTION / ...
  - Trạng thái (`status`): CONFIRMED / PENDING / REJECTED (badge màu).
  - Số tiền (`amount`): Định dạng VNĐ nổi bật.
  - Nguồn tiền (`money_source`): FUND / PERSONAL.
  - Danh mục chi tiêu (`category_name` hoặc `category_id`).
  - Ngày phát sinh (`occurred_at`).
  - Người thực hiện (`transactor_name` / `transactor_id`).
  - Người tạo giao dịch (`created_by`).
- **Ghi chú (`note`):** Hiển thị văn bản ghi chú hoặc "Không có ghi chú".
- **Danh sách người tham gia chia tiền (`participants`):**
  - Hiển thị bảng danh sách các thành viên tham gia chia khoản chi này:
    + Cột: *Thành viên (Tên / User ID)*, *Số tiền phải chịu (share_amount)*.
    + Nếu không có người tham gia riêng: Ghi nhận chia đều cho toàn bộ nhóm hoặc tự thanh toán.

---

## 4. Quy tắc tuân thủ
- Tuyệt đối không đánh số thứ tự trong comment (`// 1.`, `// 2.`...).
- Giữ nguyên cấu trúc code hiện tại, không gây ảnh hưởng tới các chức năng khác.
