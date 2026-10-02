# Kế hoạch Hiển Thị Trực Quan Báo Cáo Tài Chính & Bổ Sung Nút Thêm Thành Viên (Owner Only)

## 1. Yêu cầu & Mục tiêu
1. **Giao diện trực quan cho Tab Báo Cáo Tài Chính:**
   - Thay vì chỉ in JSON thô ở inspector bên dưới, trang Báo Cáo sẽ hiển thị giao diện bảng biểu và thẻ chỉ số thống kê (Cards & Tables) trực quan:
     - **Báo cáo tổng quan:** Thẻ chỉ số hiển thị Số dư quỹ, Mục tiêu, Tổng chi tiêu, Tổng đóng góp theo kỳ/toàn thời gian.
     - **Báo cáo cân đối thành viên:** Thẻ tóm tắt số dư/tổng cần nộp + **Bảng chi tiết từng thành viên** (Họ tên, Đã tự chi hộ, Đã hoàn lại, Phần chi tiêu phải chịu, Cân đối ròng, Cần đóng thêm).
2. **Phân quyền Tab Báo Cáo Tài Chính:**
   - Member thường không được phép thấy hoặc truy cập trang này (`role-reviewer-only` / ẩn với Member thường).
3. **Bổ sung nút & form Thêm Thành Viên (Chỉ Owner mới có):**
   - Đặt nút **"➕ Thêm Thành Viên"** trực tiếp tại Card Danh sách thành viên.
   - Nút và khu vực nhập User ID này được gán `role-owner-only` (chỉ Owner mới nhìn thấy và thao tác).
   - Sau khi thêm thành viên thành công: Tự động tải lại danh sách thành viên.

---

## 2. Danh sách file sẽ thay đổi
- `src/main/resources/static/pages/group-test.html`
- `src/main/resources/static/js/group-test.js`
- `src/main/resources/static/css/group-test.css`

---

## 3. Thiết kế chi tiết

### 3.1. Ẩn Tab Báo Cáo Tài Chính đối với Member
- Gán class `role-reviewer-only` vào nút tab `📊 Báo Cáo Tài Chính` trên thanh điều hướng (`.nav-tabs`).
- Trong hàm `applyRoleVisibility()`: Khi vai trò là `MEMBER` thông thường, tab này sẽ tự động bị ẩn (`display: none`). Nếu đang đứng tại tab này mà đổi nhóm, hệ thống tự chuyển về tab `tab-groups`.

### 3.2. Giao diện trực quan cho Tab Báo Cáo Tài Chính
1. **Khu vực Báo cáo tổng quan (`#summaryReportContainer`):**
   - Khi bấm **"Xem Tổng Quan"**: Render khối thẻ chỉ số:
     - Kỳ báo cáo (Tháng cụ thể hoặc Toàn thời gian)
     - Số dư quỹ hiện tại (VNĐ)
     - Mục tiêu quỹ (VNĐ)
     - Tổng chi tiêu (VNĐ)
     - Tổng đóng góp (VNĐ)
2. **Khu vực Báo cáo cân đối thành viên (`#balancesReportContainer`):**
   - Khi bấm **"Xem Đối Soát Cân Đối"**: Render:
     - Thẻ tóm tắt: Số dư quỹ, Tổng tiền cần đóng thêm, Trạng thái quyết toán tự động.
     - **Bảng cân đối thành viên**: Bảng trực quan với các cột:
       - Thành viên (Họ tên / Email / User ID)
       - Trạng thái
       - Tự chi trả (Chi hộ) (VNĐ)
       - Đã hoàn lại (VNĐ)
       - Phần phải chịu (VNĐ)
       - Số dư ròng (Net Balance) (VNĐ - màu xanh nếu thừa, màu đỏ nếu thiếu)
       - Cần đóng thêm (VNĐ - tô nổi bật nếu > 0)

### 3.3. Nút & Form Thêm Thành Viên (Owner Only)
- Thêm nút **"➕ Thêm Thành Viên"** (`class="btn btn-primary role-owner-only"`) ngay trên thanh tiêu đề của Card "Danh Sách Thành Viên Trong Nhóm".
- Khi bấm nút, hiển thị form thêm nhanh thành viên:
  - Ô nhập danh sách User ID (hỗ trợ nhập 1 hoặc nhiều UUID phân tách bằng dấu phẩy hoặc xuống dòng).
  - Nút "Xác Nhận Thêm" và nút "Đóng".
- Chỉ hiển thị cho `OWNER` (`role-owner-only`).
- Hàm `addMembers()` sau khi gọi thành công sẽ tự động gọi `filterMembersByStatus('')` để cập nhật bảng thành viên ngay lập tức.

---

## 4. Kịch bản kiểm thử sau khi sửa
1. **Kiểm tra phân quyền Member thường:**
   - Chọn nhóm mà bạn có vai trò `MEMBER`:
     - Thanh tab **không xuất hiện** tab `📊 Báo Cáo Tài Chính`.
     - Tab `👥 Thành Viên` **không xuất hiện** nút `➕ Thêm Thành Viên`.
2. **Kiểm tra phân quyền Owner:**
   - Chọn nhóm mà bạn là `OWNER`:
     - Thanh tab hiển thị đầy đủ tab `📊 Báo Cáo Tài Chính`.
     - Tab `👥 Thành Viên` có nút `➕ Thêm Thành Viên`. Bấm nút mở form thêm, nhập User ID và bấm thêm $\rightarrow$ thành viên mới xuất hiện ngay trên bảng.
3. **Kiểm tra giao diện Báo Cáo:**
   - Mở tab `📊 Báo Cáo Tài Chính`, bấm "Xem Đối Soát Cân Đối":
     - Hiển thị bảng cân đối trực quan với đầy đủ số liệu của từng thành viên thay vì chỉ có màn hình đen in JSON.
   - Bấm "Xem Tổng Quan":
     - Hiển thị các thẻ chỉ số thu chi, số dư quỹ.
