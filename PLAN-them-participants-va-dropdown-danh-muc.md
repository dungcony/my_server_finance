# Kế hoạch Thêm Người Tham Gia (Participants) & Chuyển Danh Mục, Người Thực Hiện sang Danh Sách Chọn

## 1. Yêu cầu & Mục tiêu
- **Chuyển Danh mục (`categoryId`) sang danh sách chọn (Dropdown Select):** Thay thế ô nhập UUID bằng thẻ `<select>`, tự động tải danh sách danh mục chi tiêu từ API `GET /v1/categories?type=EXPENSE&as_tree=false` để người dùng chọn tên danh mục trực quan.
- **Chuyển Người thực hiện (`transactorId`) sang danh sách chọn (Dropdown Select):** Thay thế ô nhập UUID bằng thẻ `<select>` chứa danh sách thành viên trong nhóm (`displayName` / `email`), mặc định chọn người đang đăng nhập.
- **Thêm phần nhập Người tham gia chia tiền (`participants`):**
  - Hiển thị danh sách các thành viên trong nhóm dạng danh sách chọn (Checkbox List).
  - Có tùy chọn **"Chia đều cho tất cả thành viên"** (mặc định) hoặc chọn riêng từng thành viên tham gia chia tiền.
  - Mỗi thành viên có ô nhập số tiền tùy chọn (để trống = tự động chia đều, hoặc điền số tiền cụ thể).
  - **Tự động ẩn/hiện theo loại giao dịch:** Khi chọn loại giao dịch là **Đóng góp (CONTRIBUTION)**, tự động ẩn phần Danh mục và Người tham gia (vì nộp quỹ không có danh mục và không có chia tiền).

---

## 2. Danh sách file sẽ thay đổi
- `src/main/resources/static/pages/group-test.html`
- `src/main/resources/static/js/group-test.js`

---

## 3. Thiết kế chi tiết

### 3.1. Giao diện Form Tạo Giao Dịch (`group-test.html`)
1. **Dropdown Danh Mục (`#txnCategoryId`):**
   - Chuyển từ `<input type="text">` sang `<select id="txnCategoryId">`.
   - Option đầu: `-- Chọn danh mục chi tiêu (*) --`.
2. **Dropdown Người Thực Hiện (`#txnTransactorId`):**
   - Chuyển từ `<input type="text">` sang `<select id="txnTransactorId">`.
   - Option đầu: `-- Chọn thành viên chi trả / nộp quỹ (*) --`.
3. **Khu vực chọn Người tham gia (`#txnParticipantsSection`):**
   - Thêm khối chọn thành viên chia tiền bên dưới trường ghi chú:
     - Checkbox toggle: `☑ Chia đều cho tất cả thành viên trong nhóm` (mặc định bật).
     - Danh sách thành viên tùy chỉnh (hiển thị khi bỏ chọn chia đều tất cả): gồm checkbox chọn từng thành viên + ô nhập số tiền (VNĐ).
   - Khối này có class `expense-only-field` để tự động ẩn khi loại giao dịch là `CONTRIBUTION`.

### 3.2. Logic JavaScript (`group-test.js`)
1. **Hàm nạp danh mục `loadExpenseCategories()`:**
   - Gọi `GET /v1/categories?type=EXPENSE&as_tree=false`.
   - Đổ danh sách vào `<select id="txnCategoryId">`.
   - Được gọi khi khởi tạo hoặc khi chuyển sang tab Giao dịch.
2. **Hàm nạp thành viên vào form `populateTransactionMemberSelects(members)`:**
   - Nạp danh sách thành viên vào dropdown `#txnTransactorId`.
   - Nạp danh sách thành viên vào bảng chọn `#txnParticipantsContainer`.
   - Được gọi tự động mỗi khi lấy danh sách thành viên nhóm (`renderMemberList`).
3. **Xử lý sự kiện đổi Loại giao dịch (`onTxnTypeChange`):**
   - Khi chọn `CONTRIBUTION`:
     - Nguồn tiền mặc định: `PERSONAL` (Tự bỏ túi).
     - Ẩn ô chọn Danh mục (hoặc disabled).
     - Ẩn khối chọn Người tham gia (`#txnParticipantsSection`).
   - Khi chọn `EXPENSE`:
     - Hiện ô chọn Danh mục và khối Người tham gia.
4. **Hàm `createGroupTransaction()`:**
   - Đọc giá trị `categoryId` từ dropdown (bắt buộc nếu là `EXPENSE`).
   - Đọc giá trị `transactorId` từ dropdown.
   - Xử lý `participants`:
     - Nếu là `CONTRIBUTION`: gửi `participants: []`.
     - Nếu là `EXPENSE` và bật "Chia đều tất cả": gửi `participants: []` (backend sẽ tự động chia đều cho mọi thành viên).
     - Nếu là `EXPENSE` và chọn thành viên cụ thể: gom danh sách các thành viên được tick kèm `share_amount` (nếu có nhập).

---

## 4. Kịch bản kiểm thử sau khi hoàn thành
1. Mở tab **Giao Dịch**:
   - Ô "Người thực hiện" là dropdown hiển thị tên thành viên trong nhóm, mặc định là bạn.
   - Ô "Danh mục" là dropdown hiển thị các danh mục chi tiêu (Ăn uống, Đi lại, Mua sắm...).
   - Hiển thị danh sách Người tham gia chia tiền.
2. Tạo giao dịch `EXPENSE` với danh mục chọn từ dropdown và chia tiền cho thành viên:
   - Giao dịch tạo thành công với đúng `category_id` và phân bổ `participants`.
3. Đổi sang `CONTRIBUTION`:
   - Danh mục và phần người tham gia tự động ẩn đi, tạo giao dịch nộp quỹ thành công.
