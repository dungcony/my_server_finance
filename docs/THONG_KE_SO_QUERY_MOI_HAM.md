# BẢNG THỐNG KÊ SỐ LƯỢNG SQL QUERY CỦA TỪNG HÀM (PERFORMANCE BENCHMARK)

> **Tài liệu tra cứu hiệu năng hệ thống**  
> Dữ liệu được đo đạc trực tiếp từ bộ kiểm thử hiệu năng tự động (`ServicePerfSupport`) trên môi trường PostgreSQL 16 và Redis.

---

## 1. Ý nghĩa các chỉ số trong bảng
- **SQL (Tốt nhất):** Số câu query khi dữ liệu tối thiểu (1 người dùng / 1 vai trò / 0 giao dịch).
- **SQL (Tệ nhất):** Số câu query khi dữ liệu lớn (hệ thống có nhiều người dùng / tài khoản Admin giữ đầy đủ vai trò & quyền hạn / nhóm có nhiều giao dịch).
- **Tăng:** Mức độ thay đổi số lượng câu SQL giữa 2 môi trường. (Nếu đứng yên `+0` nghĩa là số query hoàn toàn độc lập với kích thước dữ liệu).
- **Thời gian (ms):** Thời gian thực thi trung bình.

---

## 2. Module User (Quản lý người dùng, vai trò & phân quyền)

| Tên Hàm / Nghiệp vụ | SQL (Tốt nhất) | SQL (Tệ nhất) | Tăng | Thời gian (ms) | Nhận xét / Nguyên nhân |
|---|:---:|:---:|:---:|:---:|---|
| `ManagerUserService.addRoleToUser` | **8** | **8** | +0 | 19 - 29ms | Tốn 3 query load `User` + `Role` + kiểm tra trùng; tốn thêm 3-4 query do hàm `getLevel` duyệt Lazy các role của người thao tác. |
| `ManagerRoleService.addPermissionToRole` | **8** | **8** | +0 | 25 - 35ms | Tương tự, kiểm tra phân cấp quản trị và nạp vai trò kèm danh sách quyền hạn. |
| `UserService.resolveGoogleUser` (tài khoản mới) | **7** | **7** | +0 | 24 - 28ms | Insert user mới, query role mặc định `ROLE_USER`, gán role, phát sinh event và nạp lại DTO response. |
| `ManagerUserService.deleteByUserId` | **7** | **7** | +0 | 17 - 19ms | Xóa mềm user, kiểm tra cấp bậc người xóa và người bị xóa qua `getLevel`. |
| `UserService.create` | **6** | **6** | +0 | 15 - 30ms | Tạo user mới, gán `ROLE_USER`, lưu bảng liên kết `user_roles`, map DTO response. |
| `ManagerUserService.lockUser` | **6** | **6** | +0 | 22 - 32ms | Khóa user, thu hồi refresh token, kiểm tra cấp bậc quản trị. |
| `UserService.get` (theo id) | 3 | 4 | +1 | 9 - 13ms | Khi đo trên Admin (2 roles), Hibernate bắn thêm 1 query lấy quyền của role thứ hai. |
| `UserService.get` (theo email) | 3 | 4 | +1 | 12 - 15ms | Tương tự như trên. |
| `UserBehavierService.getMe` | 3 | 4 | +1 | 5 - 12ms | Lấy thông tin cá nhân kèm roles và permissions. |
| `UserService.updatePass` | 4 | 4 | +0 | 175 - 245ms | Tốn thời gian mã hóa BCrypt password (~180ms), phần SQL chỉ tốn 4 câu. |
| `UserService.resolveGoogleUser` (đã có tài khoản) | 4 | 4 | +0 | 13 - 42ms | Cập nhật thông tin Google ID và map response. |
| `UserService.updateStatus` | 3 | 3 | +0 | 12 - 20ms | Cập nhật trạng thái tài khoản (`ACTIVE`, `LOCKED`, ...). |
| `UserBehavierService.updateMe` | 3 | 3 | +0 | 6 - 9ms | Cập nhật họ tên, ảnh đại diện người dùng. |
| `UserBehavierService.changePassword` | 3 | 3 | +0 | 251 - 272ms | Xác thực mật khẩu cũ và băm mật khẩu mới (BCrypt). |
| `UserBehavierService.deleteMe` | 3 | 3 | +0 | 88 - 100ms | Tự xóa tài khoản (xóa mềm). |
| `ManagerUserService.findByUserId` | 3 | 3 | +0 | 9 - 12ms | Admin xem chi tiết 1 người dùng khác. |
| `UserBehavierService.createPassword` | 2 | 2 | +0 | 92 - 93ms | Tạo mật khẩu cho tài khoản Google lần đầu (BCrypt). |
| `ManagerUserService.findAllUser` | 1 | 2 | +1 | 13 - 27ms | Danh sách người dùng cấp dưới. Tăng +1 do nạp permissions khi có người dùng có role. |
| `ManagerRoleService.findByRole` (`ROLE_ADMIN`) | 2 | 2 | +0 | 8 - 14ms | Lấy role và danh sách permissions của admin. |
| `ManagerRoleService.findByRole` (`ROLE_USER`) | 2 | 2 | +0 | 6ms | Lấy role và danh sách permissions của user. |
| `UserService.getNames` (danh sách id) | 1 | 1 | +0 | 9 - 10ms | Truy vấn tên hiển thị hàng loạt bằng `WHERE id IN (...)`. |
| `UserService.existByEmail` | 1 | 1 | +0 | 4ms | Kiểm tra trùng email (`COUNT(u) > 0`). |
| `UserService.validUser` | 1 | 1 | +0 | 5ms | Kiểm tra trạng thái hoạt động của tài khoản. |
| `ManagerRoleService.findRoles` | 1 | 1 | +0 | 4 - 10ms | Lấy toàn bộ danh mục vai trò. |

---

## 3. Module Auth (Xác thực & Phiên đăng nhập)

| Tên Hàm / Nghiệp vụ | SQL (Tốt nhất) | SQL (Tệ nhất) | Tăng | Thời gian (ms) | Nhận xét / Nguyên nhân |
|---|:---:|:---:|:---:|:---:|---|
| `LoginService<Email>.login` | **8** | **9** | +1 | 129 - 432ms | Kiểm tra khóa tài khoản, băm BCrypt, lưu lịch sử đăng nhập (`login_attempts`), tạo session và nạp cây roles/permissions. |
| `AuthService.refresh` | **7** | **8** | +1 | 28 - 37ms | Xác thực refresh token, xoay vòng token trong Redis/DB, nạp lại user permissions để cấp access token mới. |
| `AuthService.register` | **7** | **7** | +0 | 120 - 143ms | Kiểm tra trùng lặp, băm mật khẩu, tạo user `PENDING_VERIFY`, gán `ROLE_USER`, lưu OTP Redis. |
| `AuthService.verifyEmail` | **5** | **6** | +1 | 47 - 66ms | Xác thực OTP, kích hoạt user thành `ACTIVE`, cấp token lần đầu. |
| `LoginService<Google>.login` | **5** | **6** | +1 | 16 - 17ms | Đăng nhập Google, đồng bộ user và cấp token. |
| `AuthService.resetPassword` | 5 | 5 | +0 | 198 - 208ms | Xác thực OTP đặt lại mật khẩu, mã hóa BCrypt mật khẩu mới. |
| `AuthService.resendVerification` | 3 | 3 | +0 | 39 - 45ms | Kiểm tra rate limit và sinh OTP mới gửi email. |
| `AuthService.logout` (1 thiết bị) | 2 | 2 | +0 | 12 - 13ms | Thu hồi 1 refresh token cụ thể. |
| `TokenService.create` | 2 | 2 | +0 | 8ms | Tạo cặp access token và refresh token. |
| `TokenService.checkRefreshAndGetUserId` | 2 | 2 | +0 | 10ms | Kiểm tra tính hợp lệ của refresh token. |
| `TokenService.revokeRefresh` | 2 | 2 | +0 | 8 - 11ms | Hủy bỏ refresh token. |
| `AuthService.logout` (tất cả thiết bị) | 1 | 1 | +0 | 5 - 8ms | Thu hồi toàn bộ refresh token theo user ID. |
| `AuthService.forgotPassword` | 1 | 1 | +0 | 15 - 18ms | Kiểm tra email tồn tại và gửi OTP quên mật khẩu. |
| `TokenService.revokeAllByUserId` | 1 | 1 | +0 | 5 - 7ms | Xóa session theo user ID. |
| `TokenService.revokeAllByEmail` | 1 | 1 | +0 | 5 - 6ms | Xóa session theo email. |

---

## 4. Module Group (Nhóm tài chính, Quỹ & Giao dịch chung)

| Tên Hàm / Nghiệp vụ | SQL (Tốt nhất) | SQL (Tệ nhất) | Tăng | Thời gian (ms) | Nhận xét / Nguyên nhân |
|---|:---:|:---:|:---:|:---:|---|
| `FundService.reconcileFund` | **9** | **9** | +0 | 41 - 82ms | Đối soát quỹ: tính tổng tiền nộp, rút, chi tiêu, so khớp số dư thực tế và cập nhật trạng thái quỹ. |
| `GTransactionService.create` (chi tiêu chia cả nhóm) | **7** | **7** | +0 | 49 - 104ms | Kiểm tra quyền thành viên, insert giao dịch, chia tiền cho từng người, insert bảng phụ `participants`, cập nhật số dư quỹ. |
| `GroupService.update` | 5 | 5 | +0 | 29 - 33ms | Kiểm tra quyền Trưởng nhóm, cập nhật thông tin nhóm, cập nhật quỹ. |
| `ReportService.getBalances` | 5 | 5 | +0 | 34 - 154ms | Tính số dư thu chi từng thành viên: lấy danh sách thành viên, stream giao dịch xác nhận, tính toán công nợ và lấy tên hiển thị. |
| `GroupService.create` | 4 | 4 | +0 | 13 - 19ms | Tạo nhóm mới, tạo quỹ nhóm mặc định, thêm người tạo làm Trưởng nhóm (`OWNER`). |
| `GroupService.archive` / `delete` / `unarchive` | 4 | 4 | +0 | 23 - 30ms | Thao tác trạng thái nhóm (lưu trữ, xóa mềm, mở khóa). |
| `GroupService.joinByCode` | 4 | 4 | +0 | 22 - 26ms | Tìm nhóm theo mã mời, kiểm tra chưa tham gia, tạo bản ghi thành viên. |
| `MemberBehavierService.leave` / `removeMember` | 4 | 4 | +0 | 21 - 37ms | Rời nhóm hoặc Trưởng nhóm xóa thành viên: kiểm tra công nợ và đổi trạng thái. |
| `GTransactionService.create` (nộp quỹ) | 4 | 4 | +0 | 35 - 72ms | Nộp tiền vào quỹ: kiểm tra thành viên, lưu giao dịch và cộng tiền quỹ. |
| `GTransactionService.list` (phân trang 20 - 100 bản ghi) | 3 | 4 | +1 | 15 - 61ms | Lấy danh sách giao dịch có phân trang kèm `participants` (sử dụng `@EntityGraph`). |
| `GTransactionService.myList` | 3 | 4 | +1 | 22 - 81ms | Lấy danh sách giao dịch cá nhân tham gia. |
| `ReportService.getSummary` (toàn thời gian / theo tháng) | 4 | 4 | +0 | 16 - 28ms | Báo cáo tổng quan thu chi quỹ nhóm. |
| `FundService.updateFundKeepper` | 4 | 4 | +0 | 19ms | Chuyển giao vai trò Người giữ quỹ (thủ quỹ). |
| `GroupService.detail` / `pendingCount` | 3 | 3 | +0 | 16 - 28ms | Xem chi tiết nhóm hoặc đếm số lượng yêu cầu chờ duyệt. |
| `GTransactionService.update` / `delete` | 3 | 3 | +0 | 14 - 22ms | Cập nhật hoặc xóa mềm giao dịch kèm điều chỉnh số dư quỹ. |
| `GTransactionReviewService.confirm` / `reject` | 3 | 3 | +0 | 13 - 35ms | Duyệt hoặc từ chối giao dịch đang chờ duyệt. |
| `GTransactionReviewService.bulkConfirm` / `bulkReject` | 3 | 3 | +0 | 14 - 23ms | Duyệt hàng loạt giao dịch chờ duyệt. |
| `MemberBehavierService.listMembers` | 3 | 3 | +0 | 25 - 26ms | Danh sách thành viên trong nhóm. |
| `MemberBehavierService.approve` / `reject` (đơn lẻ / tất cả) | 2 | 2 | +0 | 8 - 22ms | Duyệt hoặc từ chối thành viên xin vào nhóm. |
| `MemberBehavierService.transferOwnership` | 2 | 2 | +0 | 11 - 12ms | Chuyển quyền Trưởng nhóm cho thành viên khác. |
| `GTransactionService.detail` | 2 | 2 | +0 | 12 - 15ms | Xem chi tiết 1 giao dịch nhóm. |
| `GroupService.findNotDeletedById` / `list` | 1 | 1 | +0 | 3 - 8ms | Tìm nhóm theo ID hoặc danh sách nhóm của user. |
| `MemberService.create` / `creates` | 1 | 1 | +0 | 3 - 8ms | Thêm thành viên vào nhóm. |
| `MemberService.countActiveMembers` / `countPendingMembers` | 1 | 1 | +0 | 6 - 11ms | Đếm số lượng thành viên đang hoạt động hoặc chờ duyệt. |
| `GTransactionService.countPendingForGroup` | 1 | 1 | +0 | 7 - 10ms | Đếm số lượng giao dịch chờ duyệt. |
| `GTransactionService.sumConfirmedAmount` | 1 | 1 | +0 | 4 - 26ms | Tính tổng tiền thu hoặc chi bằng `SUM(...)`. |
| `GTransactionService.streamConfirmedTransactions` | 1 | 1 | +0 | 12 - 101ms | Stream toàn bộ giao dịch xác nhận (đã tối ưu cursor 1.000 batch). |
| `FundService.adjustBalance` | 1 | 1 | +0 | 6 - 11ms | Điều chỉnh cộng/trừ số dư quỹ. |

---

## 5. Danh sách toàn bộ các hàm có số câu SQL > 5 (Cần ưu tiên tối ưu)

| STT | Module | Tên Hàm / Nghiệp vụ | Số Query Hiện Tại | Số Query Mục Tiêu | Nguyên Nhân & Giải Pháp Rút Gọn |
|:---:|:---:|---|:---:|:---:|---|
| 1 | **Group** | `FundService.reconcileFund` | **9** | **3 - 4** | **Nguyên nhân:** Đang gọi các hàm `sumConfirmedAmount` và query số dư quỹ tách biệt 9 lần.<br>**Giải pháp:** Gom các câu tính tổng (thu, chi, hoàn tiền) vào 1 câu SQL thống kê dùng `SUM(CASE WHEN ...)`. |
| 2 | **Auth** | `LoginService<Email>.login` | **8 - 9** | **3 - 4** | **Nguyên nhân:** Load User, kiểm tra khóa, lưu `login_attempts`, nạp cây role và permissions.<br>**Giải pháp:** Bỏ lazy load các quan hệ quyền hạn thừa khi login; chỉ load thông tin tối thiểu cần thiết để tạo JWT token. |
| 3 | **User** | `ManagerUserService.addRoleToUser` | **8** | **2 - 3** | **Nguyên nhân:** `getLevel` duyệt Lazy các roles của người thao tác (mất 3-4 query); gọi `existsBy...` trước khi `save()`.<br>**Giải pháp:** Lấy cấp bậc từ token (`SecurityContextUtil.currentLevel()`); dùng `findTopRoleLevelByUserId` cho user bị tác động; insert trực tiếp dựa vào Unique Constraint. |
| 4 | **User** | `ManagerRoleService.addPermissionToRole` | **8** | **2 - 3** | **Nguyên nhân:** Kiểm tra cấp bậc quản trị và nạp vai trò kèm danh sách quyền hạn bằng nhiều câu đơn lẻ.<br>**Giải pháp:** Tương tự `addRoleToUser`. |
| 5 | **Auth** | `AuthService.refresh` | **7 - 8** | **2 - 3** | **Nguyên nhân:** Nạp lại cả thực thể User, nạp toàn bộ danh sách vai trò và quyền hạn để sinh access token.<br>**Giải pháp:** Cache thông tin roles/permissions của user vào Redis hoặc nạp trực tiếp qua 1 query projection duy nhất. |
| 6 | **User** | `UserService.resolveGoogleUser` (mới) | **7** | **3** | **Nguyên nhân:** Insert user, query role mặc định, insert bảng liên kết, phát sinh event và nạp lại DTO response.<br>**Giải pháp:** Gán role mặc định ngay trong transaction tạo user; không nạp lại toàn bộ cây permissions khi trả về DTO. |
| 7 | **User** | `ManagerUserService.deleteByUserId` | **7** | **2 - 3** | **Nguyên nhân:** Kiểm tra quyền hạn của cả người xóa và người bị xóa qua hàm `getLevel` tốn 3-4 query phụ.<br>**Giải pháp:** Lấy `currentLevel` từ token; dùng query native `findTopRoleLevelByUserId` cho người bị xóa; cập nhật cờ `is_deleted = true` trực tiếp. |
| 8 | **Group** | `GTransactionService.create` (chi tiêu cả nhóm) | **7** | **3** | **Nguyên nhân:** Kiểm tra quyền, insert giao dịch, chia tiền và insert từng người tham gia (`participants`), cập nhật số dư quỹ.<br>**Giải pháp:** Batch insert danh sách participants bằng 1 lệnh; cập nhật số dư quỹ nguyên tử (`UPDATE funds SET current_balance = current_balance - ?`). |
| 9 | **Auth** | `AuthService.register` | **7** | **3** | **Nguyên nhân:** Kiểm tra email tồn tại, insert user, gán role mặc định, map DTO nạp lại roles/permissions.<br>**Giải pháp:** Dựa vào Unique Constraint để bắt lỗi trùng email; chỉ trả DTO tóm tắt (`id`, `email`) thay vì nạp lại toàn bộ quyền. |
| 10 | **User** | `UserService.create` | **6** | **2 - 3** | **Nguyên nhân:** Tạo user mới, gán `ROLE_USER`, lưu `user_roles`, map DTO response nạp lại permissions.<br>**Giải pháp:** Tương tự `AuthService.register`. |
| 11 | **User** | `ManagerUserService.lockUser` | **6** | **2 - 3** | **Nguyên nhân:** Kiểm tra cấp bậc người khóa và người bị khóa qua `getLevel` (mất 3-4 query); cập nhật trạng thái.<br>**Giải pháp:** Dùng `currentLevel` từ token và native query cấp bậc cho người bị khóa. |
| 12 | **Auth** | `AuthService.verifyEmail` | **5 - 6** | **2 - 3** | **Nguyên nhân:** Kích hoạt user, tạo session, nạp cây permissions để sinh token.<br>**Giải pháp:** Tối ưu câu query nạp quyền hạn bằng 1 native query thay vì duyệt JPA quan hệ. |
| 13 | **Auth** | `LoginService<Google>.login` | **5 - 6** | **2 - 3** | **Nguyên nhân:** Đồng bộ user Google và nạp cây permissions để sinh token.<br>**Giải pháp:** Tương tự `verifyEmail`. |
