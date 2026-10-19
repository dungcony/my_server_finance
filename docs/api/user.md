# ĐẶC TẢ API — PHÂN HỆ NGƯỜI DÙNG & QUẢN TRỊ RBAC (USER & ADMIN)

> **File này trả lời:** Danh sách và đặc tả chi tiết các endpoint quản lý hồ sơ cá nhân (`/users`) và các endpoint quản trị hệ thống (`/admin/user`, `/admin/role`).  
> Cấu trúc bảng & Entity → [lop-thuc-the.md](lop-thuc-the.md) · Luồng nghiệp vụ → [pipeline.md](pipeline.md) · Quy tắc RBAC → [rule.md](rule.md)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Chi tiết API Hồ sơ Người dùng (UserController)](#3-chi-tiet-user-controller)
  - [3.1 Lấy thông tin tài khoản hiện tại](#31-lay-thong-tin-me)
  - [3.2 Cập nhật hồ sơ cá nhân](#32-cap-nhat-ho-so)
  - [3.3 Đổi mật khẩu](#33-doi-mat-khau)
  - [3.4 Tạo mật khẩu tự động (Cho tài khoản Google)](#34-tao-mat-khau-google)
  - [3.5 Tự xóa tài khoản](#35-tu-xoa-tai-khoan)
- [4. Chi tiết API Quản trị Người dùng (ManagerUserController)](#4-chi-tiet-manager-user)
  - [4.1 Tra cứu chi tiết người dùng](#41-chi-tiet-nguoi-dung)
  - [4.2 Danh sách toàn bộ người dùng](#42-danh-sach-nguoi-dung)
  - [4.3 Khóa tài khoản người dùng](#43-khoa-tai-khoan)
  - [4.4 Gán vai trò cho người dùng](#44-gan-vai-tro)
  - [4.5 Thu hồi vai trò của người dùng](#45-thu-hoi-vai-tro)
  - [4.6 Xóa tài khoản người dùng (Admin Delete)](#46-xoa-nguoi-dung-admin)
- [5. Chi tiết API Quản trị Vai trò & Quyền hạn (ManagerRoleController)](#5-chi-tiet-manager-role)
  - [5.1 Lấy danh sách tất cả vai trò](#51-danh-sach-vai-tro)
  - [5.2 Lấy danh sách quyền hạn của vai trò](#52-danh-sach-quyen-cua-vai-tro)
  - [5.3 Gán quyền hạn cho vai trò](#53-gan-quyen-cho-vai-tro)
- [6. Bảng mã lỗi phân hệ User & RBAC](#6-bang-ma-loi)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Header bắt buộc:** Mọi endpoint trong phân hệ này đều yêu cầu xác thực phiên qua header:
  `Authorization: Bearer <accessToken>`.
- **Cơ chế phân quyền:** Các endpoint Admin được bảo vệ bằng `@PreAuthorize("hasAuthority(...)")` đối soát với Claims quyền hạn trong JWT token.
- **Đóng gói chuẩn:** Mọi response trả về đều bọc trong `ApiResponse<T>`.

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

| Phương thức | Endpoint | Quyền hạn yêu cầu | Mô tả |
| :--- | :--- | :--- | :--- |
| **`GET`** | `/users/me` | Đã đăng nhập | Xem hồ sơ tài khoản hiện tại |
| **`PATCH`** | `/users/me` | Đã đăng nhập | Cập nhật thông tin họ tên, ảnh đại diện |
| **`PUT`** | `/users/me/password` | Đã đăng nhập | Đổi mật khẩu tài khoản |
| **`POST`** | `/users/me/password` | Đã đăng nhập | Khởi tạo mật khẩu ngẫu nhiên gửi về email |
| **`DELETE`** | `/users/me` | Đã đăng nhập | Người dùng tự xóa mềm tài khoản |
| **`GET`** | `/admin/user/{userId}` | `users:read` | Admin xem chi tiết người dùng |
| **`GET`** | `/admin/user/all` | `users:read` | Admin xem danh sách tất cả người dùng |
| **`PATCH`** | `/admin/user/lock` | `users:update` | Admin khóa tài khoản người dùng |
| **`POST`** | `/admin/user/role` | `user_roles:update` | Admin gán vai trò cho người dùng |
| **`DELETE`** | `/admin/user/role` | `user_roles:delete` | Admin thu hồi vai trò của người dùng |
| **`DELETE`** | `/admin/user/{userId}` | `users:delete` | Admin xóa tài khoản người dùng |
| **`GET`** | `/admin/role/all` | `roles:read` | Admin xem tất cả các vai trò |
| **`GET`** | `/admin/role/{roleName}/permissions` | `role_permissions:read` | Xem danh sách quyền hạn của vai trò |
| **`POST`** | `/admin/role/permission` | `role_permissions:update` | Gán quyền hạn mới cho vai trò |

---

## 3. Chi tiết API Hồ sơ Người dùng (UserController) <a id="3-chi-tiet-user-controller"></a>

### 3.1 Lấy thông tin tài khoản hiện tại <a id="31-lay-thong-tin-me"></a>

- **Method / Path:** `GET /users/me`
- **Response (`200 OK` - `ApiResponse<UserRes>`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "user@example.com",
    "firstName": "Nguyễn",
    "lastName": "Văn A",
    "avatarUrl": "https://example.com/avatar.png",
    "plan": "FREE",
    "status": "ACTIVE",
    "roles": ["ROLE_USER"],
    "createdAt": "2026-09-01T08:00:00Z"
  }
}
```

---

### 3.2 Cập nhật hồ sơ cá nhân <a id="32-cap-nhat-ho-so"></a>

- **Method / Path:** `PATCH /users/me`
- **Request Body (`UpdateProfileRequest`):**
```json
{
  "firstName": "Nguyễn",
  "lastName": "Văn B",
  "avatarUrl": "https://example.com/new-avatar.png"
}
```
- **Response (`200 OK`):** Trả về `UserRes` với thông tin cập nhật.

---

### 3.3 Đổi mật khẩu <a id="33-doi-mat-khau"></a>

- **Method / Path:** `PUT /users/me/password`
- **Request Body (`UpdatePassReq`):**
```json
{
  "oldPassword": "CurrentPassword123@",
  "newPassword": "NewPassword456@"
}
```
- **Response (`200 OK`):** `data: null`
- **Mã lỗi:**
  - `400 AUTH_OLD_PASSWORD_INCORRECT`: Mật khẩu cũ không chính xác.
  - `400 AUTH_PASSWORD_SAME_AS_OLD`: Mật khẩu mới không được trùng với mật khẩu cũ.
  - `400 AUTH_NO_PASSWORD_SET`: Tài khoản Google chưa từng tạo mật khẩu, không thể đổi.

---

### 3.4 Tạo mật khẩu tự động (Cho tài khoản Google) <a id="34-tao-mat-khau-google"></a>

- **Method / Path:** `POST /users/me/password`
- **Mô tả:** Dành cho tài khoản ban đầu đăng nhập Google thuần (`password == null`). Hệ thống sinh ngẫu nhiên mật khẩu an toàn và gửi về email.
- **Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Mật khẩu đã được gửi về email của bạn.",
  "data": null
}
```
- **Mã lỗi:** `400 AUTH_PASSWORD_ALREADY_SET`: Tài khoản đã có mật khẩu, phải dùng API đổi mật khẩu.

---

### 3.5 Tự xóa tài khoản <a id="35-tu-xoa-tai-khoan"></a>

- **Method / Path:** `DELETE /users/me`
- **Request Body (`DeleteAccountRequest`):**
```json
{
  "password": "Password123@"
}
```
*(Nếu tài khoản Google thuần chưa có mật khẩu thì không yêu cầu password)*.
- **Mô tả:** Đánh dấu xóa mềm `is_deleted = true`, hủy các phiên token và gửi sự kiện `UserDeletedEvent`.
- **Response (`200 OK`):** `data: null`

---

## 4. Chi tiết API Quản trị Người dùng (ManagerUserController) <a id="4-chi-tiet-manager-user"></a>

### 4.1 Tra cứu chi tiết người dùng <a id="41-chi-tiet-nguoi-dung"></a>

- **Method / Path:** `GET /admin/user/{userId}`
- **Quyền hạn:** `users:read`
- **Response (`200 OK`):** Trả về `ApiResponse<UserRes>`.

---

### 4.2 Danh sách toàn bộ người dùng <a id="42-danh-sach-nguoi-dung"></a>

- **Method / Path:** `GET /admin/user/all`
- **Quyền hạn:** `users:read`
- **Response (`200 OK`):** Trả về danh sách `ApiResponse<List<UserRes>>`.

---

### 4.3 Khóa tài khoản người dùng <a id="43-khoa-tai-khoan"></a>

- **Method / Path:** `PATCH /admin/user/lock`
- **Quyền hạn:** `users:update`
- **Request Body (`BlockUserRequest`):**
```json
{
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "reason": "Vi phạm điều khoản chính sách thanh toán"
}
```
- **Quy tắc cấp bậc:**
  - Không được tự khóa tài khoản của chính mình.
  - Không được khóa tài khoản có `level` nhỏ hơn hoặc bằng cấp bậc của mình (số nhỏ = quyền cao).
- **Hành động hệ thống:**
  - Cập nhật `users.status = 'BLOCKED'`.
  - Đưa `userId` vào Blacklist trên Redis (TTL 3600 giây).
  - Thu hồi toàn bộ Refresh Tokens của người dùng này (`UserLockedEvent`).
- **Response (`200 OK`):** `message: "Khóa tài khoản người dùng thành công"`

---

### 4.4 Gán vai trò cho người dùng <a id="44-gan-vai-tro"></a>

- **Method / Path:** `POST /admin/user/role`
- **Quyền hạn:** `user_roles:update`
- **Request Body (`UpdateUserRoleReq`):**
```json
{
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "roleName": "ROLE_ADMIN"
}
```
- **Quy tắc cấp bậc & kiểm soát:**
  - Không được gán vai trò có `level <= level` của chính mình.
  - Không được gán vai trò mà người dùng đã sở hữu (`USER_ROLE_ALREADY_ASSIGNED`).
- **Response (`200 OK`):** `message: "Gán vai trò cho người dùng thành công"`

---

### 4.5 Thu hồi vai trò của người dùng <a id="45-thu-hoi-vai-tro"></a>

- **Method / Path:** `DELETE /admin/user/role`
- **Quyền hạn:** `user_roles:delete`
- **Request Body (`UpdateUserRoleReq`):**
```json
{
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "roleName": "ROLE_ADMIN"
}
```
- **Quy tắc cấp bậc & bảo vệ vai trò mặc định:**
  - Không được thu hồi vai trò có `level <= level` của chính mình.
  - **Tuyệt đối cấm gỡ `ROLE_USER`:** Đây là vai trò mặc định của mọi tài khoản (`400 USER_DEFAULT_ROLE_NOT_REMOVABLE`).
- **Response (`200 OK`):** `message: "Xóa vai trò của người dùng thành công"`

---

### 4.6 Xóa tài khoản người dùng (Admin Delete) <a id="46-xoa-nguoi-dung-admin"></a>

- **Method / Path:** `DELETE /admin/user/{userId}`
- **Quyền hạn:** `users:delete`
- **Quy tắc cấp bậc:** Không được tự xóa mình; không được xóa người dùng có cấp bậc cao hơn hoặc bằng mình.
- **Response (`200 OK`):** Trả về thông tin người dùng đã bị xóa mềm (`is_deleted = true`).

---

## 5. Chi tiết API Quản trị Vai trò & Quyền hạn (ManagerRoleController) <a id="5-chi-tiet-manager-role"></a>

### 5.1 Lấy danh sách tất cả vai trò <a id="51-danh-sach-vai-tro"></a>

- **Method / Path:** `GET /admin/role/all`
- **Quyền hạn:** `roles:read`
- **Response (`200 OK` - `ApiResponse<List<RoleResponse>>`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "name": "ROLE_ADMIN",
      "level": 1,
      "description": "Quản trị viên toàn hệ thống"
    },
    {
      "id": "22222222-2222-2222-2222-222222222222",
      "name": "ROLE_USER",
      "level": 100,
      "description": "Người dùng thông thường"
    }
  ]
}
```

---

### 5.2 Lấy danh sách quyền hạn của vai trò <a id="52-danh-sach-quyen-cua-vai-tro"></a>

- **Method / Path:** `GET /admin/role/{roleName}/permissions`
- **Quyền hạn:** `role_permissions:read`
- **Response (`200 OK` - `ApiResponse<List<PermissionResponse>>`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": [
    {
      "id": "33333333-3333-3333-3333-333333333333",
      "name": "users:read",
      "description": "Xem danh sách và chi tiết người dùng"
    }
  ]
}
```

---

### 5.3 Gán quyền hạn cho vai trò <a id="53-gan-quyen-cho-vai-tro"></a>

- **Method / Path:** `POST /admin/role/permission`
- **Quyền hạn:** `role_permissions:update`
- **Request Body (`AddPermissionRoleRequest`):**
```json
{
  "roleName": "ROLE_ADMIN",
  "permissionName": "USERS_READ"
}
```
- **Quy tắc bảo mật & kiểm soát:**
  - Không được gán quyền cho vai trò có `level <= level` của mình.
  - Người thực hiện **bắt buộc phải sở hữu quyền đó** trước khi có thể đem gán cho vai trò khác.
  - Không được gán quyền đã tồn tại trong vai trò (`USER_ROLE_PERMISSION_ALREADY_ASSIGNED`).
- **Response (`200 OK`):** `message: "Gán quyền hạn cho vai trò thành công"`

---

## 6. Bảng mã lỗi phân hệ User & RBAC <a id="6-bang-ma-loi"></a>

| Mã lỗi kỹ thuật | HTTP Status | Ý nghĩa & Nguyên nhân |
| :--- | :---: | :--- |
| `USER_NOT_FOUND` | 404 | Không tìm thấy người dùng |
| `AUTH_OLD_PASSWORD_INCORRECT` | 400 | Mật khẩu cũ không chính xác |
| `AUTH_PASSWORD_SAME_AS_OLD` | 400 | Mật khẩu mới không được trùng với mật khẩu cũ |
| `AUTH_PASSWORD_ALREADY_SET` | 400 | Tài khoản đã có mật khẩu |
| `AUTH_NO_PASSWORD_SET` | 400 | Tài khoản chưa từng tạo mật khẩu |
| `USER_ROLE_ALREADY_ASSIGNED` | 400 | Người dùng đã sở hữu vai trò này trước đó |
| `USER_DEFAULT_ROLE_NOT_REMOVABLE` | 400 | Không được phép gỡ vai trò mặc định (`ROLE_USER`) |
| `USER_ROLE_PERMISSION_ALREADY_ASSIGNED` | 400 | Quyền hạn đã được gán cho vai trò này trước đó |
| `FORBIDDEN` | 403 | Không đủ quyền hạn hoặc thao tác vi phạm cấp bậc phân quyền (`level`) |
