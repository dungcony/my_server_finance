# QUY TẮC BẢO MẬT & BẤT BIẾN — PHÂN HỆ XÁC THỰC (AUTH)

> **File này trả lời:** Hệ thống bắt buộc phải tuân theo những luật lệ, chính sách bảo mật và bất biến kỹ thuật nào trong phân hệ xác thực.  
> Cấu trúc bảng & Entity → [lop-thuc-the.md](lop-thuc-the.md) · Endpoint API → [api.md](api.md) · Luồng xử lý → [pipeline.md](pipeline.md)

---

## 📑 MỤC LỤC

- [1. Quyết định nền tảng](#1-quyet-dinh-nen-tang)
- [2. Quy tắc bất biến bảo mật](#2-quy-tac-bat-bien)
- [3. Chính sách mật khẩu & OTP](#3-chinh-sach-mat-khau-otp)
- [4. Chính sách quản lý Token (JWT & Refresh Token)](#4-chinh-sach-token)
- [5. Phòng chống tấn công Brute-force & Gian lận](#5-phong-chong-tan-cong)

---

## 1. Quyết định nền tảng <a id="1-quyet-dinh-nen-tang"></a>

| # | Quyết định | Lý do & Ý nghĩa |
| :-: | :--- | :--- |
| **1** | **Tách biệt Auth và User Management.** Mọi thao tác xác thực danh tính nằm tại `/auth`; các thao tác quản lý hồ sơ cá nhân nằm tại `/users/me`; thao tác quản trị tài khoản nằm tại `/admin/user` | Rõ ràng ranh giới trách nhiệm (Separation of Concerns), giảm tải cho controller xác thực |
| **2** | **Xác thực phi trạng thái (Stateless Authentication).** Sử dụng JWT Access Token có thời hạn ngắn (15 phút) mang đầy đủ Claims quyền hạn | Giảm tải truy vấn CSDL PostgreSQL cho các service nghiệp vụ, dễ mở rộng quy mô |
| **3** | **Không lưu Refresh Token thô.** Chỉ lưu mã băm SHA-256 (64 ký tự hex) trong bảng `refresh_tokens` | Ngăn ngừa rủi ro rò rỉ token nếu cơ sở dữ liệu bị lộ lọt (Data Breach) |
| **4** | **Lưu trữ mã OTP trên Redis In-Memory với TTL trượt.** | Tự động giải phóng bộ nhớ khi hết hạn, truy vấn cực nhanh và không làm phình CSDL quan hệ |
| **5** | **Ghi nhận toàn bộ lượt đăng nhập vào `login_attempts` độc lập với kết quả giao dịch** (`noRollbackFor = BusinessException.class`) | Đảm bảo cơ chế đếm số lần sai không bị rollback khi transaction gặp exception |

---

## 2. Quy tắc bất biến bảo mật <a id="2-quy-tac-bat-bien"></a>

| # | Quy tắc | Hậu quả nếu vi phạm |
| :-: | :--- | :--- |
| **1** | Email luôn được chuyển thành **chữ thường (lowercase) và cắt khoảng trắng** trước khi truy vấn hoặc lưu trữ | Người dùng bị trùng tài khoản hoặc không đăng nhập được do ký tự hoa thường |
| **2** | Mật khẩu người dùng **bắt buộc mã hóa bằng BCrypt** (độ dài muối mặc định). Tuyệt đối không lưu mật khẩu dạng plaintext | Vi phạm nghiêm trọng tiêu chuẩn bảo mật dữ liệu |
| **3** | **Không bao giờ trả về trường `password_hash`** trong bất kỳ DTO response nào ra ngoài client | Lộ hash mật khẩu ra môi trường bên ngoài |
| **4** | **Một Refresh Token chỉ được sử dụng đúng 1 lần duy nhất.** Khi cấp Access Token mới, token cũ phải bị đánh dấu thu hồi (`revoked_at = now()`) | Mở ra nguy cơ kẻ gian dùng lại token cũ nhiều lần |
| **5** | **Phát hiện tái sử dụng token đã thu hồi (Reuse Detection):** Ngay khi một token đã có `revoked_at != NULL` được gửi lên, hệ thống phải **thu hồi toàn bộ token còn hiệu lực** của tài khoản đó | Không ngăn chặn được cuộc tấn công khi token bị đánh cắp |
| **6** | **Tài khoản Google đăng ký tự động ở trạng thái `ACTIVE`.** Tài khoản đăng ký bằng email bắt đầu ở `PENDING` và phải xác thực OTP mới sang `ACTIVE` | Tài khoản chưa xác thực email có thể thực hiện thao tác trong hệ thống |
| **7** | **Không cho phép đặt lại hoặc đổi mật khẩu trùng với mật khẩu hiện tại** | Người dùng giữ nguyên mật khẩu cũ mà tưởng đã đổi, hoặc không đảm bảo tính an toàn |
| **8** | Khi **đổi mật khẩu** hoặc **đặt lại mật khẩu** thành công, hệ thống phải **thu hồi toàn bộ Refresh Token** của người dùng trên mọi thiết bị | Kẻ gian đang nắm phiên đăng nhập cũ vẫn tiếp tục truy cập trái phép |
| **9** | **Tài khoản bị Admin khóa (`status = BLOCKED`):** Chặn ngay lập tức tại các bước đăng nhập và làm mới token | Người dùng bị cấm vẫn duy trì hoạt động trong hệ thống |

---

## 3. Chính sách mật khẩu & OTP <a id="3-chinh-sach-mat-khau-otp"></a>

- **Độ phức tạp mật khẩu:** Tối thiểu 8 ký tự, tối đa 32 ký tự, bao gồm ít nhất một chữ hoa, một chữ thường, một chữ số và một ký tự đặc biệt.
- **Quy tắc mã OTP:**
  - Gồm đúng 6 chữ số ngẫu nhiên sinh từ bộ tạo số an toàn (`SecureRandom`).
  - Thời hạn hiệu lực: **15 phút**.
  - **Giới hạn nhập sai:** Tối đa **5 lần**. Mỗi lần nhập sai, hệ thống tăng biến đếm `attempts` và tính toán lại TTL thực tế còn lại. Nếu chạm mốc 5 lần sai, mã OTP bị xóa ngay lập tức khỏi Redis.
  - **Giãn cách gửi lại (Cooldown):** Tối thiểu **60 giây** giữa 2 lần yêu cầu gửi mã mới để chống spam email.

---

## 4. Chính sách quản lý Token (JWT & Refresh Token) <a id="4-chinh-sach-token"></a>

- **Access Token:**
  - Thời hạn: 15 phút (900 giây).
  - Mang thông tin định danh: `sub` (userId), `email`, `plan`, `roleLevel`, `roles`, `authorities`.
  - Không thể thu hồi đơn lẻ giữa chừng (Stateless); hết hạn sau 15 phút.
- **Refresh Token:**
  - Chuỗi ngẫu nhiên an toàn độ dài cao, mã hóa Base64 URL-safe.
  - Thời hạn: **30 ngày** kể từ ngày tạo.
  - Được lưu trong bảng `refresh_tokens` dưới dạng mã băm SHA-256.
  - Có thể bị thu hồi trước hạn qua API `/auth/logout`, khi đổi mật khẩu, hoặc khi phát hiện xâm nhập.

---

## 5. Phòng chống tấn công Brute-force & Gian lận <a id="5-phong-chong-tan-cong"></a>

- **Khóa tạm thời đăng nhập:**
  - Theo dõi 5 lần đăng nhập gần nhất theo email trong bảng `login_attempts`.
  - Nếu cả 5 lần gần nhất đều thất bại trong khung thời gian quy định, hệ thống từ chối đăng nhập với mã lỗi `429 AUTH_ACCOUNT_LOCKED`.
- **Bảo mật phản hồi quên mật khẩu:**
  - Áp dụng `ForgotPasswordRateLimiter` trên Redis để giới hạn tần suất yêu cầu.
  - Không thông báo chi tiết email có tồn tại hay không ra ngoài giao diện công khai để tránh lộ danh sách người dùng hệ thống.
