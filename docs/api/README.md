# TÀI LIỆU ĐẶC TẢ RESTFUL API — TOÀN BỘ PHÂN HỆ HỆ THỐNG

> Thư mục này chứa đặc tả kỹ thuật chi tiết của toàn bộ các API Endpoints trong hệ thống Quản lý Tài chính, được tổ chức theo từng module/phân hệ độc lập.  
> Chuẩn giao tiếp: RESTful API, JSON Payload (`snake_case`), Jakarta Validation, HTTP Status Code chuẩn RFC 7231, đóng gói phản hồi `ApiResponse<T>`.

---

## 📑 Danh mục Tài liệu API theo Phân hệ

| Module / Phân hệ | Đường dẫn tài liệu | Base URL | Mô tả chức năng chính |
| :--- | :--- | :--- | :--- |
| **Giao dịch cá nhân** | [`transaction.md`](transaction.md) | `/transactions` | Thêm, sửa, xóa, lọc giao dịch thu/chi/cho vay. Cân bằng số dư ví và kiểm tra danh mục lá qua Database Trigger. |
| **Giao dịch định kỳ** | [`recurring.md`](recurring.md) | `/recurring` | Lập lịch giao dịch định kỳ, quản lý các buổi lịch con, bỏ qua (`skip`) hoặc khôi phục (`unskip`) lịch. |
| **Ví tài chính & Đối chiếu** | [`wallet.md`](wallet.md) | `/wallets` | Quản lý ví cá nhân, sinh giao dịch số dư ban đầu, xóa mềm cascade, xem lịch sử đối chiếu số dư ví. |
| **Cây danh mục thu chi** | [`category.md`](category.md) | `/categories` | Cây phân cấp nhiều cấp, di chuyển nhánh cây con (`moveUnder`), xóa mềm cascade toàn bộ con cháu. |
| **Xác thực & Phiên** | [`auth.md`](auth.md) | `/auth` | Đăng ký, đăng nhập email/mật khẩu, Google OAuth2, xác thực OTP Redis, cấp mới Token Rotation, quên mật khẩu. |
| **Người dùng & Phân quyền RBAC** | [`user.md`](user.md) | `/users`, `/admin/user`, `/admin/role` | Hồ sơ cá nhân, đổi mật khẩu, quản trị khóa/mở khóa tài khoản, gán/thu hồi Role & Permission. |
| **Nhóm chung quỹ** | [`group.md`](group.md) | `/v1/groups` | Mô hình hai sổ ghi tách biệt (Two Separate Ledgers), quỹ nhóm, duyệt thành viên, giao dịch quỹ & cá nhân. |
| **Ngân sách chi tiêu** | [`budget.md`](budget.md) | `/budgets`, `/budgets/admin` | Thiết lập hạn mức ngân sách theo kỳ, theo dõi tiến độ chi tiêu %, cảnh báo sắp chạm ngưỡng / vượt ngân sách. |
| **Quản lý sổ nợ** | [`debt.md`](debt.md) | `/debts` | Quản lý nợ cho vay / đi vay, ghi nhận đợt trả nợ, xóa nợ khó đòi (`write-off`), theo dõi nợ quá hạn. |
| **Mục tiêu tiết kiệm** | [`goal.md`](goal.md) | `/goals` | Tạo mục tiêu tiết kiệm kèm ví phụ `SAVINGS`, đóng góp tích lũy, tự động hoàn tất mục tiêu. |
| **Báo cáo & Xuất dữ liệu** | [`report.md`](report.md) | `/reports` | Báo cáo cơ cấu danh mục, xu hướng dòng tiền ngày/tháng, khởi tạo tiến trình xuất báo cáo CSV bất đồng bộ. |
| **Biểu tượng hệ thống** | [`icon.md`](icon.md) | `/icons` | Kho biểu tượng SVG hệ thống dùng chung cho danh mục và ví. |
| **Thông báo** | [`notification.md`](notification.md) | `/notifications` | Danh sách thông báo cá nhân, đếm số lượng chưa đọc, đánh dấu đã đọc. |
| **Thanh toán & Premium** | [`payment.md`](payment.md) | `/payments`, `/payments/admin`, `/users/me/premium-upgrades`, `/public/payments/payos/*` | Nâng cấp FREE → PREMIUM qua payOS, trạng thái đơn, lịch sử thanh toán (user và admin), webhook payOS. |

---

## 🛠️ Quy chuẩn Kỹ thuật Chung

1. **Header Xác thực:** `Authorization: Bearer <accessToken>` (trừ các endpoint public `/auth/login`, `/auth/register`, `/auth/verify-email`...).
2. **Idempotency Header:** Các endpoint tạo mới dữ liệu quan trọng hỗ trợ `Idempotency-Key: <UUID>` để chống duplicate submit.
3. **Format Date / Time:** Chuẩn ISO-8601 UTC Instant (`YYYY-MM-DDTHH:mm:ssZ`).
4. **Phân trang chuẩn:** Tham số `page` (0-indexed), `size` (mặc định 20), `sort` (thuộc tính,hướng).
