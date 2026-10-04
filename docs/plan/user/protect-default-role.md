# Plan: không cho thu hồi vai trò mặc định của người dùng

- **Nguồn:** `docs/design/auth/problem.md` — "đang xóa vai trò mặc định cái này không nên"
- **Phạm vi:** `ManagerUserServiceImpl.removeRoleToUser` (endpoint `DELETE /admin/user/role`)
- **Trạng thái:** ✅ ĐÃ LÀM (05/10/2026) — mã lỗi `403`, mục 3 của `problem.md` chưa làm
- **Xác nhận:** `[x] PROCESS`

## 0. Vấn đề

Mọi tài khoản mới được gán `ROLE_USER` lúc tạo (`UserServiceImpl.assignDefaultRole`). Hiện `removeRoleToUser` cho admin gỡ `ROLE_USER`
khỏi một người dùng (level 10 lớn hơn level admin nên qua được chốt cấp bậc). Người đó thành tài khoản không có vai trò nào, mất
toàn bộ quyền cơ bản: token cấp sau đó có `authorities` rỗng, và `findTopRoleLevelByUserId` trả về `Integer.MAX_VALUE`.

## 1. Các file sẽ thay đổi

| File | Thay đổi |
|---|---|
| `common/exception/ErrorCode.java` | thêm mã lỗi `USER_DEFAULT_ROLE_NOT_REMOVABLE` (403, "Không thể thu hồi vai trò mặc định của người dùng.") |
| `user/service/impl/ManagerUserServiceImpl.java` | `removeRoleToUser` ném lỗi trên khi vai trò là `ROLE_USER` |
| `src/test/.../user/service/AdminUserServiceTest.java` | thêm test cho nhánh mới và nhánh gỡ vai trò thường |
| `src/test/.../user/service/UserServicePerfIntegrationTest.java` | đổi phép đo `removeRoleToUser` thành `perf.skip` (xem mục 3) |

Không đổi API (đường dẫn, body), không đổi CSDL. `ErrorCode` là hợp đồng ra JSON nên mã mới cần ghi vào `api/` và tài liệu phân quyền.

## 2. Hướng giải quyết (code xem trước)

```java
// ManagerUserServiceImpl.removeRoleToUser — đặt sau chốt cấp bậc để người yếu hơn vẫn nhận lỗi cấp bậc như cũ
if (role.getName() == RoleName.ROLE_USER) {
    throw new BusinessException(ErrorCode.USER_DEFAULT_ROLE_NOT_REMOVABLE);
}
```

Vai trò mặc định đang được xác định bằng `RoleName.ROLE_USER` (cùng giá trị `assignDefaultRole` dùng khi tạo tài khoản).

## 3. Hệ quả cần biết

`RoleName` hiện chỉ có `ROLE_ADMIN` và `ROLE_USER`. Sau thay đổi này `removeRoleToUser` **không còn gỡ được vai trò nào**:
- `ROLE_USER`: bị chặn bởi quy tắc mới;
- `ROLE_ADMIN` (level 1): admin cũng level 1 nên bị chốt cấp bậc `role.getLevel() <= currentUserLevel` chặn.

Endpoint vẫn giữ nguyên để dùng khi sau này thêm vai trò trung gian. Vì không còn đường nào thành công, phép đo
`ManagerUserService.removeRoleToUser` trong `UserServicePerfIntegrationTest` sẽ chuyển sang `perf.skip` kèm lý do.

## 4. Kiểm chứng (TDD)

Test đơn vị (Mockito, theo mẫu `AdminUserServiceTest` hiện có), viết trước và chạy ra đỏ:
1. Gỡ `ROLE_USER` khỏi người dùng thường → ném `USER_DEFAULT_ROLE_NOT_REMOVABLE`, `deleteByUserIdAndRoleId` không được gọi.
2. Gỡ vai trò không phải mặc định (dựng `Role` giả cấp thấp hơn người gọi) → xoá bản ghi như cũ (giữ hành vi hiện tại).
3. Người gọi cấp thấp hơn hoặc bằng role đích → vẫn nhận `FORBIDDEN` cấp bậc như cũ (thứ tự chốt chặn không đổi).

## 5. Cần anh/chị quyết

1. **Mã lỗi và HTTP status:** đề xuất `USER_DEFAULT_ROLE_NOT_REMOVABLE` với `403`, giống tiền lệ `SYSTEM_CATEGORY_NOT_DELETABLE`. Phương án khác: `409`.
2. **Mục 3 của `problem.md`** ("gán role… ROLE_USER cho user vẫn được"): hiện `addRoleToUser` gán trùng thì **im lặng bỏ qua**
   (không lỗi). Đề xuất **chưa làm** vì ghi "chưa vội" và ý chưa rõ: muốn trả `409` khi vai trò đã được gán, hay là chuyện khác?
3. **File `ErrorCode.java` đang có thay đổi chưa commit** ở working tree (liên quan `chuan-hoa-error-code.md`). Thêm một mã mới ở cuối nhóm
   `AUTH_`/user là đủ, không đổi tên mã cũ.
