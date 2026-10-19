# Bỏ toàn bộ JPA converter của enum, lưu và trả về đúng tên enum viết hoa

## Bối cảnh

- Module `user` đang có 2 kiểu converter khiến giá trị trong DB khác tên enum:
    - `LowercaseEnumConverter` (đang được dời sang `common/configs/` trong working tree, chưa commit): `UserPlan`, `UserStatus` lưu `free`, `pending_verify`...
    - `PermissionName.PermissionNameConverter` (`PermissionName.java:139-151`): lưu `users:read` thay vì `USERS_READ`.
- Các module khác (`Budget`, `Group`, `Wallet`...) đã dùng `@Enumerated(EnumType.STRING)` và lưu viết hoa → đang không đồng nhất.
- Plan premium (`plan-premium-upgrade-payment.md`) cần module `pay` lưu enum mà không phụ thuộc converter của `user`.
- Yêu cầu: bỏ hết converter, mọi enum lưu và trả JSON bằng đúng tên viết hoa.

## Quyết định đã chốt

| # | Nội dung |
|---|---|
| 1 | Bỏ cả `LowercaseEnumConverter` lẫn `PermissionNameConverter`. |
| 2 | Sửa thẳng `migration_v2/V1__auth_user_role_permission.sql`, không thêm migration mới. ⚠️ DB đã chạy V1 sẽ vỡ checksum Flyway → phải xoá DB tạo lại. |
| 3 | JSON API đổi sang viết hoa: bỏ `@JsonValue`/`@JsonCreator` ở `UserPlan`, `PermissionName`. |
| 4 | Làm plan này trước plan premium. |
| 5 | Có viết test mới (repository test, Testcontainers) ngoài việc sửa test bị vỡ. |
| 6 | Bổ sung luôn vào seed V1 các quyền có trong enum nhưng chưa seed (11 quyền, xem mục 3). |

## 1. Các file sẽ thay đổi

Đường dẫn gốc `src/main/java/com/datn/financeapp/`.

**Code — enum & entity**

| File | Thay đổi |
|---|---|
| `common/configs/LowercaseEnumConverter.java` | **Xoá** (working tree đang là bản dời từ `module/user/enums/`). |
| `module/user/enums/UserPlan.java` | Bỏ `UserPlanConverter`, `@JsonValue toValue()`, `@JsonCreator fromValue()`. Còn enum trần `PREMIUM, FREE`. |
| `module/user/enums/UserStatus.java` | Bỏ `UserStatusConverter`. |
| `module/user/enums/PermissionName.java` | Bỏ `PermissionNameConverter` (`:139-151`), `@JsonValue getValue()` (`:69-72`), `VALUE_MAP` + `@JsonCreator fromValue()` (`:74-87`). Giữ `resource`, `action`, `description`, `byResource()` (xem mục 3). |
| `module/user/entity/User.java:63,67` | Thêm `@Enumerated(EnumType.STRING)` cho `plan`, `status`. |
| `module/user/entity/Permission.java:30` | Thêm `@Enumerated(EnumType.STRING)` cho `name`. |

**Code — chỗ dùng `getValue()`** (đổi `.getValue()` → `.name()`)

| File | Dòng |
|---|---|
| `module/budget/controller/BudgetAdminController.java` | 38, 39 |
| `module/category/controller/CategoryController.java` | 37, 66 |
| `module/debt/controller/DebtController.java` | 112, 122, 129, 135, 145, 152, 161, 168, 175 |
| `module/goal/controller/GoalController.java` | 54, 56 |
| `module/icon/controller/IconController.java` | 23, 25 |
| `module/transaction/controller/RecController.java` | 112, 120, 126, 132 |
| `module/transaction/controller/TxnController.java` | 65, 73, 81, 87, 97 |
| `module/user/controller/ManagerRoleController.java` | 24, 30, 36 |
| `module/user/controller/ManagerUserController.java` | 24, 32, 38, 46, 54, 62, 70 |
| `module/wallet/controller/ReconcilHistoryController.java` | 25 |
| `module/wallet/controller/WalletController.java` | 31, 32, 33 |
| `module/user/service/impl/ManagerRoleServiceImpl.java` | 68 (comment), 69, 85 |
| `module/user/dto/response/UserRes.java` | 83: **xoá dòng** `result.add(p.name().getValue())` (dòng 84 đã add `name()`) |

**CSDL**

| File | Thay đổi |
|---|---|
| `src/main/resources/db/migration_v2/V1__auth_user_role_permission.sql` | ⚠️ Sửa migration đã chạy. Dòng 16-17 `DEFAULT 'FREE'`, `'PENDING_VERIFY'`; dòng 25-26 `CHECK` viết hoa; dòng 31-32 sửa comment (bỏ nhắc `LowercaseEnumConverter`); dòng 147-149 sửa comment seed; dòng 157-181 `permissions.name` đổi `'users:read'` → `'USERS_READ'`... và **thêm 11 quyền còn thiếu**; dòng 200-201 admin `'PREMIUM'`, `'ACTIVE'`. |
| `src/main/resources/db/seed/01_seed_users_and_auth.sql` | Dòng 35, 37-39, 93: giá trị `plan`/`status` viết hoa. |

**Test**

| File | Thay đổi |
|---|---|
| `src/test/.../integrationTest/user/repository/UserRepositoryTest.java` | **Thêm 3 ca kiểm thử mới** (xem mục 3). File đã có sẵn Testcontainers + Flyway `migration_v2` (`application-test.yml:4-7`). |
| `src/test/.../common/idempotency/IdempotencyAspectIntegrationTest.java:91-92` | `"free"`, `"active"` → `"FREE"`, `"ACTIVE"` (insert SQL thô, sẽ vi phạm `CHECK` mới). |
| `src/test/.../unitTest/user/service/AdminRoleServiceTest.java:119,181,200` | `.getValue()` → `.name()` (không sửa thì lỗi biên dịch). |
| `src/test/.../unitTest/user/helper/UserAuthCacheHelperTest.java:98-129` | Chuỗi mock `"users:read"` → `"USERS_READ"` cho đúng dữ liệu thật (test vẫn qua nếu không đổi). |

**Tài liệu**

| File | Thay đổi |
|---|---|
| `docs/api/user.md:51,52,158,166,280` | `users:read` → `USERS_READ`. |
| `docs/design/user/api.md:51,52,158,166,280` | Như trên. |
| `docs/design/user/lop-thuc-the.md:108,149` | Bỏ quy ước `resource:action`, ghi lưu tên enum. |
| `docs/design/auth/lop-thuc-the.md:128` | Ví dụ `authorities` viết hoa. |
| `src/main/resources/db/seed/README.md:46` | `status` viết hoa. |

**Không đổi**: `JwtService.java`, `TokenServiceImpl.java`, `AuthServiceImpl.java` (claim `plan` đã dùng `user.plan().name()` → vốn viết hoa). `UserRepository.findAuthoritiesByUserId` (native query, trả nguyên chuỗi DB → tự thành `USERS_READ`). `RoleName` (không có converter).

## 2. Lý do thay đổi

- Không đồng nhất: `user` lưu viết thường / `resource:action`, các module khác lưu tên enum (`Budget.java:44`, `Group.java:42`...).
- Rule 2.3 `spring-boot_struct.md`: enum trong entity phải `@Enumerated(EnumType.STRING)`.
- Converter đặt trong một module làm module khác (như `pay`) muốn dùng thì phải phụ thuộc vào module đó.
- Chuỗi quyền phải khớp ở 3 nơi (DB → JWT → `@PreAuthorize`). Hiện cả 3 cùng dùng `getValue()`; bỏ converter mà không đổi `@PreAuthorize` thì DB trả `USERS_READ` còn `@PreAuthorize` so `users:read` → mọi endpoint phân quyền trả 403.

## 3. Hướng giải quyết / code xem trước

**Thứ tự**: enum → entity → `getValue()` → V1 + seed → test → tài liệu → biên dịch.

**Enum sau khi bỏ converter**

```java
public enum UserPlan {
    PREMIUM,
    FREE
}
```

**Entity**

```java
@Builder.Default
@Enumerated(EnumType.STRING)
@Column(name = "plan", nullable = false)
private UserPlan plan = UserPlan.FREE;
```

**`@PreAuthorize`** — giữ kiểu tham chiếu enum để đổi tên enum thì biên dịch/khởi động báo lỗi:

```java
@PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_READ.name())")
```

**`PermissionName`**: `resource`/`action` hiện chỉ dùng để sinh `getValue()` và cho `byResource()`. Bỏ `getValue()` thì vẫn giữ `byResource()` + `description` vì không ảnh hưởng gì. Không xoá thêm để giữ thay đổi nhỏ.

**V1 seed permissions**

```sql
INSERT INTO permissions (name, description)
VALUES ('USERS_READ', 'Xem danh sách và chi tiết người dùng'),
       ('USERS_UPDATE', 'Khóa, mở khóa hoặc cập nhật thông tin người dùng'),
       ...
```

Tên enum ↔ chuỗi cũ không phải lúc nào cũng suy ra được từ nhau (VD `TXN_READ` ↔ `transactions:read`, `BUDGET_READ` ↔ `budgets:read`) → map từng dòng theo `PermissionName.java:22-56`.

**11 quyền bổ sung vào seed** (có trong `PermissionName.java:22-56` nhưng không có ở `V1:157-181` hay migration nào khác). `ROLE_ADMIN` tự nhận hết nhờ `CROSS JOIN` ở `V1:185-190`, không phải sửa đoạn gán quyền.

| Quyền | Mô tả seed |
|---|---|
| `TXN_UPDATE` | Tạo và cập nhật giao dịch của người dùng (enum để trống mô tả) |
| `GOAL_READ`, `GOAL_UPDATE`, `GOAL_DELETE` | Lấy theo `description` trong enum |
| `BUDGET_READ`, `BUDGET_DELETE` | Lấy theo `description` trong enum |
| `DEBT_READ`, `DEBT_UPDATE`, `DEBT_DELETE` | Lấy theo `description` trong enum |
| `REPORT_READ`, `REPORT_DELETE` | Lấy theo `description` trong enum |

⚠️ Đổi hành vi: admin sẽ gọi được các endpoint `GoalController`, `DebtController`, `TxnController` (cập nhật) mà trước đây có thể bị 403 do thiếu quyền trong DB.

**Test mới trong `UserRepositoryTest`**

| Ca kiểm thử | Khẳng định | Đỏ trước khi sửa vì |
|---|---|---|
| `savedUser_PersistsPlanAndStatusAsEnumName` | Lưu user `FREE`/`ACTIVE`, đọc cột thô bằng `JdbcTemplate` ra đúng `"FREE"`, `"ACTIVE"` | converter đang ghi `"free"`, `"active"` |
| `findAuthoritiesByUserId_ReturnsPermissionEnumName` | User có `ROLE_ADMIN` → authorities chứa `PermissionName.USERS_READ.name()` | DB đang trả `"users:read"` |
| `seededPermissions_CoverAllPermissionNames` | Tập `permissions.name` trong DB chứa đủ mọi `PermissionName.values()` | V1 đang thiếu 11 quyền, và tên đang là `resource:action` |

Ca thứ ba giữ cho lần sau thêm quyền vào enum mà quên seed thì test báo ngay.

**⚠️ Thay đổi nhạy cảm**

- **Sửa migration đã chạy**: mọi DB đã chạy V1 (dev, máy khác, server) sẽ báo lỗi checksum khi khởi động → phải xoá DB và chạy lại từ đầu (`db/seed/reset_db.sql` hoặc drop schema).
- **API contract — ảnh hưởng app Flutter**:
    - `UserRes.plan`: `"free"/"premium"` → `"FREE"/"PREMIUM"`. `UserRes.status` vốn đã viết hoa (không có `@JsonValue`).
    - `PermissionResponse.name` và danh sách quyền trong `UserRes` (`:83`): `"users:read"` → `"USERS_READ"`.
    - `AddPermissionRoleRequest.permissionName`: trước nhận cả `"users:read"` lẫn `"USERS_READ"` (`fromValue`), sau chỉ nhận `"USERS_READ"`.
    - Claim `authorities` trong JWT đổi theo; access token cũ bị 403 tới khi refresh.
- **Xoá code**: `LowercaseEnumConverter`, 3 class converter lồng, `getValue()`/`fromValue()`.

**TDD**: viết 3 ca mới trong `UserRepositoryTest` trước khi sửa code, chạy đỏ theo bảng trên rồi mới sửa. Phần bỏ `getValue()` thì lỗi biên dịch xuất hiện trước khi test kịp đỏ → dựa vào biên dịch + test hiện có.

## 4. Danh sách công việc chi tiết

1. Viết 3 ca mới trong `UserRepositoryTest` (đỏ).
2. Sửa `UserPlan`, `UserStatus`, `PermissionName`; xoá `LowercaseEnumConverter`.
3. Thêm `@Enumerated(EnumType.STRING)` ở `User`, `Permission`.
4. Đổi `.getValue()` → `.name()` ở 11 controller, `ManagerRoleServiceImpl`; xoá dòng `UserRes.java:83`.
5. Sửa `V1__auth_user_role_permission.sql` (viết hoa + 11 quyền thiếu) và `seed/01_seed_users_and_auth.sql`.
6. Sửa 3 file test bị ảnh hưởng.
7. Cập nhật tài liệu.
8. Biên dịch, `grep` kiểm tra sót, `git diff` đối chiếu mục 1, báo cáo.

## Ngoài phạm vi

- `ActionNoti` (`notification/enums`) có `@JsonValue` nhưng không phải converter JPA, không đụng.
- `JwtServiceTest` dùng chuỗi `"free"` cho claim (chỉ là chuỗi thử, không qua DB) → không đổi.

## Kiểm tra

- Hỏi bạn trước khi chạy test; không chạy full test.
- Lệnh (bạn tự chạy, cần Docker cho integration test):

```powershell
mvn test -Dtest=UserRepositoryTest,AdminRoleServiceTest,UserAuthCacheHelperTest,IdempotencyAspectIntegrationTest,UserResTest
```

- `grep` không còn sót:

```powershell
rg -n "LowercaseEnumConverter|PermissionNameConverter|UserPlanConverter|UserStatusConverter|PermissionName\).*getValue|fromValue" src
rg -n "'free'|'premium'|'pending_verify'|'active'|'blocked'|[a-z_]+:(read|update|delete)'" src/main/resources/db/migration_v2/V1__auth_user_role_permission.sql src/main/resources/db/seed
```

- Xoá DB dev, khởi động app để Flyway chạy lại V1, đăng nhập admin và gọi 1 endpoint có `@PreAuthorize` (VD `GET /admin/user/all`).
- Bạn tự chạy full test nếu cần kết quả tổng.

## Câu hỏi còn mở

Không còn.

## Bảng công việc

| # | Công việc | Trạng thái |
|---|---|---|
| 1 | 3 ca test mới trong `UserRepositoryTest` | Đã xong |
| 2 | Sửa enum, xoá `LowercaseEnumConverter` | Đã xong |
| 3 | `@Enumerated` ở `User`, `Permission` | Đã xong |
| 4 | `.getValue()` → `.name()`, sửa `UserRes` | Đã xong |
| 5 | Sửa V1 (viết hoa + 11 quyền) + seed | Đã xong |
| 6 | Sửa test bị ảnh hưởng | Đã xong |
| 7 | Cập nhật tài liệu | Đã xong |
| 8 | Biên dịch, đối chiếu diff, báo cáo | Đã xong |
