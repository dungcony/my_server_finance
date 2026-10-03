# Plan: Gọn lại module user (xóa tài khoản · khóa user · level role · "Người dùng app")

- **Phạm vi:** module `user` (`ProfileServiceImpl`, `ManagerUserServiceImpl`), `auth/AuthSessionEventListener`,
  `AuthService`. **Không đụng module `group`.**
- **Trạng thái:** ⏳ CHỜ DUYỆT (bản 3, 03/10/2026 — bỏ hướng lưu trữ nhóm/rời nhóm của bản 2 vì hiểu sai ý bạn)
- **Xác nhận:** `[ ] PROCESS`  ← đổi thành `[x] PROCESS` để mình bắt đầu code
- **Thứ tự thực hiện:** Commit 1 → 2 → 3 → 4 (bốn commit độc lập nhau, không commit nào chờ câu hỏi nào)

## Đã chốt trong chat

| #  | Quyết định                                                                                                                                                                                     |
|----|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| D1 | Không làm mục 1 (chuyển `changePassword`); không tách role/permission thành module riêng; không tạo PR                                                                                         |
| D2 | **Xóa chủ nhóm hoặc xóa một thành viên chỉ ảnh hưởng phần hiển thị.** Không đổi dòng `group_members`, không đổi chủ nhóm, không lưu trữ nhóm, không liên quan giao dịch hay bất cứ thứ gì khác |
| D3 | Người dùng đã xóa **hiển thị là "Người dùng app"** (kiểu Facebook)                                                                                                                             |

---

## 0. Plan trước đã lệch với code hiện tại ở đâu

Plan trước được viết trên một base khác. Đối chiếu với branch `group` đang checkout:

| Plan trước giả định                                                                             | Thực tế trên branch `group`                                                                                                                                                          |
|-------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ProfileServiceImpl` / `ManagerUserServiceImpl` còn `JdbcTemplate` + câu `UPDATE group_members` | Đã bỏ `JdbcTemplate`, hai câu `UPDATE` **đang nằm trong comment** (`ProfileServiceImpl:89`, `ManagerAccountServiceImpl:155`). Theo D2 không cần thay bằng gì cả, chỉ cần dọn comment |
| Câu `UPDATE ... SET is_active = FALSE` còn dùng được                                            | Không. `group_members` giờ dùng `status` + `left_at` (`V10__group.sql`), không còn `is_active`. Để comment đó lại là mồi cho người sau bật lên rồi nổ `column does not exist`        |
| Commit 3 sửa "khoảng 6 test"                                                                    | `stubAdminWithLevel` chỉ được dùng ở 3 test (`TC_UNIT_04`, `06`, `07`)                                                                                                               |

## 1. File sẽ thay đổi

### Commit 1 — dọn khối comment SQL `group_members` cũ (không đổi hành vi)

| File                                                                    | Thay đổi                                                                                                                                                       |
|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/.../user/service/impl/ProfileServiceImpl.java`           | Xóa khối comment `jdbcTemplate.update(...)` và đoạn giải thích "rời mọi nhóm" (dòng 83–90). Còn lại: kiểm mật khẩu → `setDeleted` → publish `UserDeletedEvent` |
| `src/main/java/.../user/service/impl/ManagerAccountServiceImpl.java`    | Giống trên, trong `deleteByUserId` (dòng 153–156)                                                                                                              |
| `src/test/java/.../user/service/UserDeleteAccountIntegrationTest.java`  | Thêm 1 test "khóa quyết định D2" (xem mục 5, câu hỏi QA)                                                                                                       |
| `docs/design/group/rule.md` (repo server)                               | Mục 5.1 dòng "Tài khoản bị khoá hoặc xoá…": thay "Đề xuất đang có: xử lý như rời nhóm" bằng quyết định D2                                                      |
| `D:\projects\DATN\api\01-EXTEND-USER-PROFILE.md` (**repo gốc**, xem QF) | Mục 4 bước 3 và dòng "Cơ chế liên module khi xoá tài khoản": bỏ `is_active = FALSE` và "rời `group_members`"; ghi rõ xóa tài khoản không đổi dòng thành viên   |

### Commit 2 — khóa user bằng event, không gọi thẳng auth

| File                                                                              | Thay đổi                                                                                                                                                                 |
|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/.../user/event/UserBlockedEvent.java` (**mới**)                    | `record UserBlockedEvent(UUID userId)`, cùng dạng `UserDeletedEvent`                                                                                                     |
| `src/main/java/.../user/service/impl/ManagerAccountServiceImpl.java`              | `blockUser`: thay `authService.revokeAllTokensForUser(...)` bằng `eventPublisher.publishEvent(new UserBlockedEvent(...))`. Bỏ field `authService` + import `AuthService` |
| `src/main/java/.../auth/service/impl/AuthSessionEventListener.java`               | Thêm `onUserBlocked` gọi `refreshTokenRepository.revokeAllActiveForUser(...)`                                                                                            |
| `src/main/java/.../auth/service/AuthService.java`                                 | Xóa `revokeAllTokensForUser` (dòng 48)                                                                                                                                   |
| `src/main/java/.../auth/service/impl/AuthServiceImpl.java`                        | Xóa `revokeAllTokensForUser` (dòng 347–349)                                                                                                                              |
| `src/test/java/.../user/service/AdminUserServiceTest.java`                        | Bỏ `@Mock AuthService`; bỏ `authService` khỏi 3 câu `verifyNoInteractions` (dòng 109/124/145); `TC_UNIT_04` verify event thay vì `revokeAllTokensForUser`                |
| `src/test/java/.../auth/service/impl/AuthSessionEventListenerTest.java` (**mới**) | Unit test: `onUserBlocked` thu hồi refresh token đúng user (hiện listener này chưa có test nào)                                                                          |

### Commit 3 — một nguồn tính level role

| File                                                                 | Thay đổi                                                                                                                                   |
|----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/.../user/service/impl/ManagerAccountServiceImpl.java` | `getLevel` gọi `userRepository.findTopRoleLevelByUserId(userId)`. Bỏ hằng `NO_ROLE_LEVEL` và import `java.util.Objects`                    |
| `src/test/java/.../user/service/AdminUserServiceTest.java`           | `stubAdminWithLevel` stub `findTopRoleLevelByUserId(adminId)`; `TC_UNIT_04` stub thêm level của người bị khóa; thêm 1 test mới (xem mục 5) |

### Commit 4 — hiển thị "Người dùng app" cho tài khoản đã xóa

| File                                                                        | Thay đổi                                                                                                                   |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/.../user/service/impl/ProfileServiceImpl.java`               | `getDisplayNames`: user `isDeleted()` trả hằng `DELETED_USER_NAME = "Người dùng app"`, bỏ qua cả nhánh dự phòng bằng email |
| `src/test/java/.../user/service/impl/ProfileServiceImplTest.java` (**mới**) | Unit test Mockito cho `getDisplayNames` (hiện hàm này chưa có test nào, các test khác chỉ mock nó)                         |
| `D:\projects\DATN\api\10-NHOM-GIA-DINH.md` (**repo gốc**, xem QF)           | Ghi chú: `display_name` / `full_name` của tài khoản đã xóa trả "Người dùng app". Mình sẽ mở file để chọn đúng mục khi làm  |

Chỉ đổi **giá trị** chuỗi, không thêm trường API mới (đúng quy tắc không tự bịa trường).

**Không đụng:** toàn bộ module `group`, controller, DTO, migration (kể cả dòng comment về `is_active` trong
`V12__phan_quyen_tai_khoan.sql` của repo gốc — migration đã chạy, sửa là vỡ checksum), `VerifyEmailListener` (vẫn
import `auth.events`), `findByUserId`/`findAllUser` (vẫn lấy level người gọi từ JWT), `AccountService.findTopRoleLevel`,
`UserRepositoryTest`.

## 2. Lý do thay đổi

| #  | Vấn đề hiện tại                                                                                                                                                                                                                                           | Vị trí                                                                      |
|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| B1 | Hai khối code chết (đang comment) mô tả hành vi "xóa tài khoản thì rời nhóm", trái quyết định D2 và tham chiếu cột `is_active` không còn tồn tại. Tài liệu `api/01-EXTEND-USER-PROFILE.md` mục 4 bước 3 và `rule.md` mục 5.1 cũng nói theo hướng rời nhóm | `ProfileServiceImpl:83–90`, `ManagerAccountServiceImpl:153–156`             |
| B2 | Khóa user gọi thẳng `AuthService` của module khác → `user` phụ thuộc `auth.service`, trong khi `auth` đã phụ thuộc `user.service` (vòng phụ thuộc). Xóa/đổi mật khẩu đã dùng event, chỉ khóa user còn đi đường riêng                                      | `ManagerAccountServiceImpl:41,75`                                           |
| B3 | Hai cách tính level role cùng tồn tại: `ManagerAccountServiceImpl.getLevel` duyệt entity trong bộ nhớ, còn `findTopRoleLevelByUserId` là query SQL (auth đang dùng). Hai nơi có thể lệch nhau khi sửa một bên                                             | `ManagerAccountServiceImpl:205–214`, `UserRepository:41–46`                 |
| B4 | `getDisplayNames` dùng `findAllById` nên trả **tên thật của cả user đã xóa mềm**, và khi tên rỗng thì rơi về **email**. Vì D2 giữ nguyên dòng thành viên, người đã xóa vẫn nằm trong danh sách thành viên và báo cáo cân đối với tên thật                 | `ProfileServiceImpl:97–113`, `MemberViewEnricher`, `GReportServiceImpl:130` |

## 3. Hướng giải quyết (code xem trước)

### Commit 1

```java
// ProfileServiceImpl.deleteMe / ManagerAccountServiceImpl.deleteByUserId sau khi dọn
user.setDeleted(true);
userRepository.

save(user);

// các module khác tự xử lý khi nhận sự kiện (auth thu hồi token); dòng thành viên nhóm giữ nguyên
eventPublisher.

publishEvent(new UserDeletedEvent(uid));
```

### Commit 2

```java
// UserBlockedEvent — sự kiện phát ra khi admin khóa tài khoản người dùng thành công.
public record UserBlockedEvent(UUID userId) {
}
```

```java
// ManagerAccountServiceImpl.blockUser
user.setStatus(UserStatus.BLOCKED);
userRepository.

save(user);

// chặn access token đang còn hạn
blacklistedUserRepository.

blacklist(req.userId(),req.

reason(),DEFAULT_BLACKLIST_TTL_SECONDS);

// auth tự thu hồi refresh token khi nhận sự kiện
        eventPublisher.

publishEvent(new UserBlockedEvent(req.userId()));
```

```java
// AuthSessionEventListener — thêm cạnh onUserDeleted, cập nhật javadoc "đổi mật khẩu, xoá hoặc khóa tài khoản"
@EventListener
public void onUserBlocked(UserBlockedEvent event) {
    refreshTokenRepository.revokeAllActiveForUser(event.userId());
}
```

Đã grep: `revokeAllTokensForUser` chỉ có 4 chỗ — định nghĩa trong `AuthService`/`AuthServiceImpl`, một lời gọi ở
`ManagerAccountServiceImpl:75`, một `verify` ở `AdminUserServiceTest:167`. Xóa được. Sau commit này `user` chỉ còn
import `auth.events` (ở `VerifyEmailListener`), không import `auth.service`.

### Commit 3

```java
// Level của role mạnh nhất (số nhỏ nhất) user đang giữ; user không có role trả Integer.MAX_VALUE (yếu nhất).
private int getLevel(UUID userId) {
    return userRepository.findTopRoleLevelByUserId(userId);
}
```

Tương đương bản cũ: query dùng `COALESCE(MIN(level), 2147483647)`, trả đúng `MAX_VALUE` cho user không role lẫn user
không tồn tại. Trong mọi method, `getLevel` đều được gọi trước khi ghi nên không lệch do role vừa đổi trong cùng
transaction.

⚠️ **Bẫy ở test:** Mockito trả `0` cho `int` chưa stub, mà theo quy ước "số nhỏ = quyền cao" thì `0` là **mạnh hơn cả
ADMIN (1)**. Test nào quên stub level của người bị khóa sẽ rơi vào nhánh `FORBIDDEN` vì lý do sai. Vì vậy mọi test phải
stub level cả hai phía một cách tường minh.

### Commit 4

```java
// ProfileServiceImpl.getDisplayNames — chỉ thêm nhánh cho tài khoản đã xóa, logic cũ giữ nguyên
private static final String DELETED_USER_NAME = "Người dùng app";

for(
User u :users){
        // tài khoản đã xóa không lộ tên thật hay email, kiểu Facebook
        if(u.

isDeleted()){
        result.

put(u.getId(),DELETED_USER_NAME);
        continue;
        }
        // ... phần tính tên cũ giữ nguyên
        }
```

Chỉ áp cho tài khoản **đã xóa**. Tài khoản bị khóa (`BLOCKED`) vẫn hiện tên thật. Nhánh dự phòng "tên rỗng thì hiện
email" của tài khoản thường mình **không đổi** (ngoài phạm vi), chỉ báo lại ở mục 6.

Toàn bộ tên người khác nhìn thấy trong nhóm đều đi qua hàm này (hai nơi gọi: `MemberViewEnricher` cho danh sách thành
viên, `GReportServiceImpl:130` cho báo cáo cân đối), và `group/` không trả avatar ở đâu cả, nên sửa một chỗ là đủ.

## 4. Câu hỏi cần bạn quyết

| #  | Câu hỏi                                                                                                                                                                                                        | Mình đề xuất                                                                                                                                                    |
|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| QA | Thêm 1 test tích hợp **khóa quyết định D2**: sau khi xóa tài khoản, dòng `group_members` và trạng thái nhóm vẫn nguyên? (Code cũ từng định "rời nhóm", tài liệu cũ cũng ghi vậy, sau này dễ có người thêm lại) | **Có.** Chỉ 1 test, nhưng nó biến quyết định thành thứ máy kiểm tra được. Cần Docker nên mình chưa chạy được, bạn chạy giúp. Bạn không muốn thì bỏ, plan vẫn đủ |
| QF | Sửa tài liệu ở repo gốc (git riêng): `api/01-EXTEND-USER-PROFILE.md` và `api/10-NHOM-GIA-DINH.md`. Làm cùng đợt?                                                                                               | **Có**, commit riêng ở repo gốc theo quy tắc "đổi nghiệp vụ thì cập nhật tài liệu cùng lần". Mình chưa đụng gì ở repo gốc cho tới khi bạn đồng ý                |
| QG | Làm trên branch nào? Plan trước ghi `claude/user-module-separation-kj0iim`, nhưng máy này đang ở `group` và không thấy branch đó                                                                               | Làm trên một branch mới tách từ `group` (chưa tạo). Push chỉ khi bạn bảo. File untracked `docs/plan/group/validate-member-exists.md` mình không đụng            |
| QH | Hai lỗ hổng phiên đăng nhập tìm thấy khi đọc code (mục 6): xử lý ở đâu?                                                                                                                                        | **Commit 5 riêng**, làm sau Commit 2 (cùng chỗ `blockUser`/`UserDeletedEvent`), hoặc bỏ ra ngoài plan này. Mình chưa sửa gì, chờ bạn chọn                       |

**Ý kiến / sửa của bạn ghi vào đây:**

-

## 5. Kiểm thử (TDD: viết test → thấy đỏ đúng lý do → viết code)

Mỗi commit gói cả test lẫn code. Để test đỏ vì **khẳng định sai** chứ không vì thiếu class, mình dựng khung rỗng (event
chưa được publish, nhánh `isDeleted` chưa có) trước khi chạy bước đỏ.

### Commit 1

- Chỉ xóa comment nên **không có test đổi hành vi**. Nếu chọn QA = Có: `UserDeleteAccountIntegrationTest` thêm 1 test —
  dựng user là chủ nhóm của một nhóm (kèm một thành viên thường), gọi `deleteMe`, rồi khẳng định
  `users.is_deleted = true`,
  mọi dòng `group_members` của họ vẫn nguyên `status`/`role`/`left_at`, và `groups.status` vẫn `ACTIVE`.
- `deleteByUserId` (admin xóa) hiện **chưa có test nào** (grep `UserDeletedEvent`/`deleteByUserId` trong `src/test`
  không ra gì). Mình thêm vào `AdminUserServiceTest` một test verify event được publish.

### Commit 2

- `AdminUserServiceTest.TC_UNIT_04`: thay `verify(authService).revokeAllTokensForUser(...)` bằng
  `verify(eventPublisher).publishEvent(new UserBlockedEvent(targetUserId))` (record có `equals`); đổi tên/mô tả test cho
  đúng.
- `AuthSessionEventListenerTest` (mới): `onUserBlocked` gọi `revokeAllActiveForUser` đúng `userId`.
- `AdminBlockUserIntegrationTest` **giữ nguyên** — đây là bằng chứng end-to-end rằng refresh token vẫn bị thu hồi sau
  khi đổi sang event.

### Commit 3

- Sửa `TC_UNIT_04/06/07` như mục 1.
- **Thêm 1 test:** `blockUser_TargetLevelHigherOrEqual_ThrowsForbidden` — khóa user có level ≤ level người gọi phải bị
  `FORBIDDEN`. Hiện **chưa test nào** phủ nhánh phân quyền này, mà đây đúng là logic Commit 3 đụng vào (và là cái bẫy
  `0` ở mục 3 sẽ làm hỏng nếu stub thiếu).

### Commit 4

- `ProfileServiceImplTest` (Mockito): user đã xóa → "Người dùng app" (kể cả khi có đủ họ tên và email); user thường →
  `"<họ> <tên>"` như cũ; tên rỗng → email như cũ; danh sách null/rỗng → `Map.of()` và không gọi repository; user bị khóa
  nhưng chưa xóa → vẫn hiện tên thật.

### Cách chạy (theo `test-rule.md`: chỉ chạy test liên quan, không full)

Sau mỗi commit: compile, rồi

```bash
mvn -q -DskipTests compile
mvn -Dtest='AdminUserServiceTest,AuthSessionEventListenerTest,ProfileServiceImplTest' test
```

Kèm grep kiểm cấu trúc: `user/` không còn `group_members` hay `JdbcTemplate`; chỉ `VerifyEmailListener` còn import
`auth`.

**Không chạy được ở máy này:** `docker` không có, nên các test Testcontainers (`UserDeleteAccountIntegrationTest`,
`AdminBlockUserIntegrationTest`, `UserProfileIntegrationTest`, `Auth*IntegrationTest`) mình viết/sửa nhưng chưa chạy
được. Khi báo kết quả mình sẽ ghi rõ test nào chưa chạy. Trước khi merge cần bạn chạy các test đó ở máy có Docker.
`deploy.yml` chỉ build và deploy, không chạy test nên không có lưới an toàn nào khác.

## 6. Rủi ro và phát hiện ngoài phạm vi

**Hệ quả trực tiếp của D2 (plan không xử lý, ghi lại để biết):** người đã xóa vẫn là thành viên `ACTIVE`. Cụ thể theo
code hiện tại:

- Vẫn nằm trong danh sách "chia đều cả nhóm": các strategy tạo/sửa giao dịch lấy danh sách từ
  `memberService.findIdAllMember(groupId)` (`ExpenseTransactionStrategy:73`, `AdjustmentTransactionStrategy:76`,
  `ExpenseUpdateStrategy:79`).
- Vẫn qua được `allMemberInGroup`, nên vẫn chọn được làm người chi, người tham gia hay thủ quỹ.
- Chủ nhóm đã xóa thì mọi thao tác chỉ chủ nhóm làm được (duyệt thành viên, chuyển quyền, lưu trữ, xóa nhóm) không còn
  ai
  làm được, trong khi nhóm vẫn `ACTIVE`.

| Rủi ro                                                                        | Giảm thiểu                                                                             |
|-------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| Mockito trả `0` cho level chưa stub → test pass/fail vì lý do sai             | Stub tường minh hai phía, kèm test `FORBIDDEN` mới ở Commit 3                          |
| Test tích hợp (Commit 1, `AdminBlockUserIntegrationTest`) chưa chạy ở máy này | Bạn chạy trước khi merge                                                               |
| Tài liệu và code lệch nhau về chuyện "xóa tài khoản thì làm gì với nhóm"      | Commit 1 sửa `rule.md` và (nếu QF = Có) `api/01-EXTEND-USER-PROFILE.md` trong cùng đợt |

**Phát hiện ngoài phạm vi (chưa sửa, chờ QH):**

- **Tài khoản đã xóa vẫn dùng được access token tới 10 giờ.** `JwtAuthFilter` chỉ chặn user có trong blacklist Redis, mà
  blacklist chỉ được ghi khi **khóa** (`blockUser`). `UserDeletedEvent` chỉ thu hồi refresh token. Access token sống
  `access-token-expiry-seconds: 36000` giây, nên user đã xóa vẫn gọi được API (ví, giao dịch cá nhân, và cả nhóm vì
  họ vẫn là thành viên `ACTIVE`) trong thời gian đó.
- **Blacklist của `blockUser` hết hạn sớm hơn access token.** `DEFAULT_BLACKLIST_TTL_SECONDS = 3600`, trong khi javadoc
  của `BlacklistedUserModel` nói TTL phải bằng thời hạn access token (36000). Theo code, user bị khóa dùng lại được sau
  1 giờ cho tới khi token tự hết hạn.
- Nhánh dự phòng "tên rỗng thì hiện email" trong `getDisplayNames` cho tài khoản **thường** cũng làm lộ email cho các
  thành viên khác trong nhóm. Commit 4 không đổi chỗ này.
