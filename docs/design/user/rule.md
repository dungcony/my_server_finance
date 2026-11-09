# QUY TẮC NGHIỆP VỤ & BẢO MẬT — PHÂN HỆ NGƯỜI DÙNG & RBAC (USER & ADMIN)

> **File này trả lời:** Hệ thống phải tuân theo những luật lệ, ràng buộc cấp bậc và bất biến bảo mật nào khi quản lý người dùng và phân quyền RBAC.  
> Cấu trúc bảng & Entity → [lop-thuc-the.md](lop-thuc-the.md) · Endpoint API → [api.md](api.md) · Luồng xử lý → [pipeline.md](pipeline.md)

---

## 📑 MỤC LỤC

- [1. Quyết định nền tảng](#1-quyet-dinh-nen-tang)
- [2. Quy tắc bất biến bảo mật & Cấp bậc quyền hạn](#2-quy-tac-bat-bien)
- [3. Quy tắc quản lý vai trò mặc định](#3-quy-tac-vai-tro-mac-dinh)
- [4. Quy tắc phân quyền động (Role-Permission Matrix)](#4-quy-tac-phan-quyen-dong)
- [5. Quy tắc tài khoản & Mật khẩu](#5-quy-tac-tai-khoan-mat-khau)

---

## 1. Quyết định nền tảng <a id="1-quyet-dinh-nen-tang"></a>

| # | Quyết định | Lý do & Ý nghĩa |
| :-: | :--- | :--- |
| **1** | **Phân quyền dựa trên vai trò có cấp bậc (Hierarchical RBAC).** Mỗi vai trò gắn một số nguyên `level`. Số càng nhỏ thì quyền hạn càng cao (ví dụ: `ROLE_ADMIN` có level = 1, `ROLE_USER` có level = 100) | Ngăn ngừa tình trạng quản trị viên cấp thấp tự ý khóa, xóa hoặc thay đổi quyền của quản trị viên cấp cao hơn hoặc đồng cấp |
| **2** | **Phân quyền chi tiết tại Controller bằng `@PreAuthorize`.** Thay vì kiểm tra theo tên vai trò (Role), các endpoint được bảo vệ bằng tên quyền hạn chi tiết (Permission) | Tăng tính linh hoạt: có thể tạo thêm vai trò mới và gán các tập quyền khác nhau mà không cần sửa code controller |
| **3** | **Hai tầng bộ nhớ đệm phân quyền trên Redis.** Cache danh sách quyền của Role và thông tin quyền hạn của User | Tối ưu hóa hiệu năng, giảm thiểu tối đa các truy vấn join nhiều bảng khi xác thực request |
| **4** | **Cơ chế Blacklist tức thì khi khóa tài khoản.** Sử dụng Redis Blacklist với TTL động cho các tài khoản bị khóa | Vô hiệu hóa ngay lập tức các Access Token còn hạn mà không cần chờ hết hạn 15 phút |

---

## 2. Quy tắc bất biến bảo mật & Cấp bậc quyền hạn <a id="2-quy-tac-bat-bien"></a>

| # | Quy tắc | Hậu quả nếu vi phạm |
| :-: | :--- | :--- |
| **1** | **Bất biến cấp bậc quản trị:** Quản trị viên chỉ được thao tác (khóa, xóa, gán vai trò) lên đối tượng có `level > level` của chính mình. Cấm tuyệt đối thao tác lên đối tượng có `level <= level` của mình | Quản trị viên tự nâng quyền hoặc lật đổ quản trị viên cấp trên |
| **2** | **Không được tự thao tác lên chính mình qua API Admin:** Không được tự khóa tài khoản (`/admin/user/lock`), không được tự xóa tài khoản (`/admin/user/{userId}`) | Quản trị viên vô tình tự khóa mình khỏi hệ thống |
| **3** | **Bất biến thừa kế quyền lực:** Người quản trị chỉ được phép gán cho vai trò khác những quyền hạn mà **chính mình đang sở hữu** | Quản trị viên cấp trung tự trao quyền tối cao cho vai trò khác |
| **4** | **Không gán trùng lặp:** Không cho phép gán vai trò mà người dùng đã sở hữu (`USER_ROLE_ALREADY_ASSIGNED`), không cho phép gán quyền mà vai trò đã có (`USER_ROLE_PERMISSION_ALREADY_ASSIGNED`) | Gây dư thừa dữ liệu và lỗi toàn vẹn liên kết |
| **5** | **Xóa mềm người dùng:** Khi xóa tài khoản (tự xóa hoặc Admin xóa), hệ thống chỉ đặt `is_deleted = true`, không xóa cứng bản ghi khỏi CSDL | Đảm bảo tính toàn vẹn dữ liệu cho các giao dịch tài chính lịch sử |

---

## 3. Quy tắc quản lý vai trò mặc định <a id="3-quy-tac-vai-tro-mac-dinh"></a>

- **Vai trò mặc định `ROLE_USER`:**
  - Được tự động gán cho mọi tài khoản ngay khi đăng ký thành công.
  - **TUYỆT ĐỐI KHÔNG ĐƯỢC PHÉP THU HỒI `ROLE_USER`:** Mọi yêu cầu gỡ vai trò `ROLE_USER` qua API `DELETE /admin/user/role` đều bị hệ thống chặn với mã lỗi `400 USER_DEFAULT_ROLE_NOT_REMOVABLE`.
  - Lý do: Gỡ vai trò mặc định sẽ khiến người dùng mất toàn bộ quyền cơ bản, dẫn đến lỗi tài khoản không thể sử dụng ứng dụng.

---

## 4. Quy tắc phân quyền động (Role-Permission Matrix) <a id="4-quy-tac-phan-quyen-dong"></a>

- **Đồng bộ Cache:**
  - Bất kỳ thao tác thêm/bớt vai trò của người dùng (`addRoleToUser`, `removeRoleToUser`, `deleteByUserId`) đều phải gọi `userAuthCacheHelper.evictUserAuth(userId)` để làm mới dữ liệu quyền trong phiên.
  - Bất kỳ thao tác gán quyền mới cho vai trò (`addPermissionToRole`) đều phải gọi `rolePermissionCacheHelper.evictPermissionsByRole(roleName)` để đồng bộ toàn hệ thống.

---

## 5. Quy tắc tài khoản & Mật khẩu <a id="5-quy-tac-tai-khoan-mat-khau"></a>

1. **Đổi mật khẩu (`PUT /users/me/password`):**
   - Phải xác thực mật khẩu cũ chính xác.
   - **Mật khẩu mới không được trùng với mật khẩu cũ** (`AUTH_PASSWORD_SAME_AS_OLD`).
   - Tài khoản đăng nhập Google thuần chưa có mật khẩu thì không được dùng API này (phải dùng API tạo mật khẩu lần đầu).
2. **Cấp mật khẩu tài khoản Google (`POST /users/me/password`):**
   - Chỉ áp dụng khi tài khoản chưa từng có mật khẩu (`password_hash IS NULL`).
   - Mật khẩu sinh ra phải đạt tiêu chuẩn bảo mật ngẫu nhiên và gửi thẳng về email chính chủ.
3. **Khóa tài khoản:**
   - Trạng thái `users.status = 'BLOCKED'`.
   - Token bị đưa vào Redis Blacklist ngay lập tức.
   - Toàn bộ Refresh Tokens của tài khoản bị thu hồi để ngăn chặn việc gia hạn phiên.
