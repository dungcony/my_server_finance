# ĐẶC TẢ RESTFUL API ENDPOINTS — BÁO CÁO & XUẤT DỮ LIỆU (REPORT)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Báo Cáo Thống Kê & Xuất Dữ Liệu CSV (`/reports`), cơ chế xử lý background job bất đồng bộ cho tiến trình xuất tệp, và kịch bản kiểm thử API.  
> Code chính → [`ReportController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/report/controller/ReportController.java) · [`ReportAdminController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/report/controller/ReportAdminController.java)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Đặc tả Chi tiết Endpoints Thống kê](#3-dac-ta-chi-tiet-endpoints-thong-ke)
  - [3.1 Báo cáo tổng quan thu chi (`GET /reports/summary`)](#31-tong-quan-thu-chi)
  - [3.2 Phân bổ chi tiêu theo danh mục (`GET /reports/category-breakdown`)](#32-phan-bo-danh-muc)
  - [3.3 Xu hướng chi tiêu hàng ngày (`GET /reports/daily-trend`)](#33-xu-huong-ngay)
  - [3.4 Xu hướng chi tiêu hàng tháng (`GET /reports/monthly-trend`)](#34-xu-huong-thang)
- [4. Đặc tả Chi tiết Tiến trình Xuất dữ liệu CSV](#4-dac-ta-chi-tiet-xuat-csv)
  - [4.1 Khởi tạo tác vụ xuất CSV (`POST /reports/export`)](#41-khoi-tao-xuat)
  - [4.2 Kiểm tra trạng thái tác vụ (`GET /reports/export/{jobId}`)](#42-trang-thai-xuat)
  - [4.3 Tải tệp báo cáo CSV (`GET /reports/export/{jobId}/download`)](#43-tai-tep-csv)
- [5. Đặc tả Chi tiết Endpoints Quản trị viên (`/reports/admin`)](#5-dac-ta-chi-tiet-admin)
- [6. Bảng Ma trận Mã lỗi (Error Code Matrix)](#6-bang-ma-tran-ma-loi)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:** `/reports`
- **Định dạng dữ liệu:** `Content-Type: application/json` (riêng download tệp trả về `text/csv; charset=UTF-8`).
- **Đóng gói chuẩn:** Bọc trong `ApiResponse<T>`.
- **Cơ chế tải tệp:**
  - `POST /reports/export`: Khởi tạo tác vụ nền (`ExportJob`) lưu vào bảng `export_jobs` với trạng thái `PENDING` ➔ `PROCESSING` ➔ `COMPLETED` / `FAILED`.
  - Khi hoàn thành, người dùng gọi `GET /reports/export/{jobId}/download` để tải tệp. Link tải có thời hạn hiệu lực (`EXPORT_LINK_EXPIRED` nếu quá hạn).

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

| Phương thức | Endpoint | Quyền hạn | Idempotent | Mô tả |
| :--- | :--- | :--- | :---: | :--- |
| **`GET`** | `/reports/summary` | Đã đăng nhập | Không | Báo cáo tổng hợp: tổng thu, tổng chi, số dư ròng, biến động so với kỳ trước |
| **`GET`** | `/reports/category-breakdown` | Đã đăng nhập | Không | Cơ cấu chi tiêu theo từng danh mục (tỷ trọng %, số tiền) |
| **`GET`** | `/reports/daily-trend` | Đã đăng nhập | Không | Dòng tiền biến thiên theo từng ngày trong tháng |
| **`GET`** | `/reports/monthly-trend`| Đã đăng nhập | Không | Xu hướng dòng tiền qua các tháng trong năm |
| **`POST`** | `/reports/export` | Đã đăng nhập | **Có** | Khởi tạo tiến trình xuất báo cáo giao dịch ra tệp CSV |
| **`GET`** | `/reports/export/{jobId}` | Đã đăng nhập | Không | Tra cứu trạng thái tiến độ (`PENDING`, `PROCESSING`, `COMPLETED`) |
| **`GET`** | `/reports/export/{jobId}/download` | Đã đăng nhập | Không | Tải về tệp CSV kết quả |
| **`GET`** | `/reports/admin` | `reports:read` | Không | Admin tra cứu danh sách toàn bộ tác vụ xuất dữ liệu |
| **`DELETE`**| `/reports/admin/{id}` | `reports:delete` | Không | Admin hủy hoặc xóa tiến trình xuất |

---

## 3. Bảng Ma trận Mã lỗi (Error Code Matrix) <a id="6-bang-ma-tran-ma-loi"></a>

| Mã lỗi | HTTP Status | Mô tả |
| :--- | :--- | :--- |
| `EXPORT_LINK_EXPIRED` | `410 Gone` | Đường dẫn tải tệp báo cáo đã hết hạn |
| `NOT_FOUND` | `404 Not Found` | Không tìm thấy tiến trình xuất dữ liệu hoặc dữ liệu không thuộc người dùng |
| `VALIDATION_ERROR` | `400 Bad Request` | Khoảng thời gian lọc không hợp lệ |
