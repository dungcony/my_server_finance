# BẢNG THỐNG KÊ SỐ LƯỢNG SQL QUERY CỦA TỪNG HÀM (PERFORMANCE BENCHMARK)

> **Tài liệu tra cứu hiệu năng hệ thống**  
> Dữ liệu được đo đạc trực tiếp từ bộ kiểm thử hiệu năng tự động (`ServicePerfSupport`) trên môi trường PostgreSQL 16 và Redis.

---

## 1. Ý nghĩa các chỉ số trong bảng
- **SQL (Tốt nhất):** Số câu query khi dữ liệu tối thiểu (1 người dùng / 1 vai trò / 0 giao dịch).
- **SQL (Tệ nhất):** Số câu query khi dữ liệu lớn (hệ thống có nhiều người dùng / tài khoản Admin giữ đầy đủ vai trò & quyền hạn / nhóm có nhiều giao dịch).
- **Tăng:** Mức độ thay đổi số lượng câu SQL giữa 2 môi trường. (Nếu `+0` nghĩa là số query hoàn toàn độc lập với kích thước dữ liệu, loại trừ rủi ro N+1).
- **Thời gian (ms):** Thời gian thực thi trung bình (ms_tot -> ms_te).

---

## 2. Module User (Quản lý người dùng, vai trò & phân quyền)

> **Hồ sơ dữ liệu:** Tốt nhất: Admin + 1 người dùng thường -> Tệ nhất: Admin + 101 người dùng thường.

| Tên Hàm / Nghiệp vụ | SQL (Tốt nhất) | SQL (Tệ nhất) | Tăng | Thời gian (ms) | Nhận xét / Nguyên nhân |
|---|:---:|:---:|:---:|:---:|---|
| `UserService.resolveGoogleUser` (người dùng mới) | **4** | **4** | +0 | 3 - 4ms | Insert user mới, gán `ROLE_USER` mặc định, phát sinh event và map DTO. |
| `ManagerUserService.lockUser` | **4** | **4** | +0 | 11 - 15ms | Khóa user, thu hồi refresh token, kiểm tra cấp bậc quản trị. |
| `ManagerUserService.addRoleToUser` | **4** | **4** | +0 | 6 - 8ms | Kiểm tra cấp bậc người thao tác và người nhận, nạp vai trò và lưu liên kết. |
| `ManagerUserService.deleteByUserId` | **4** | **4** | +0 | 9 - 12ms | Xóa mềm user, kiểm tra cấp bậc người xóa và người bị xóa. |
| `ManagerRoleService.addPermissionToRole` | **4** | **4** | +0 | 4 - 7ms | Kiểm tra cấp bậc quản trị, nạp vai trò và gán quyền hạn. |
| `UserService.create` | 3 | 3 | +0 | 2 - 3ms | Tạo user mới, gán `ROLE_USER`, lưu bảng liên kết `user_roles`. |
| `UserBehavierService.changePassword` | 3 | 3 | +0 | 184 - 190ms | Xác thực mật khẩu cũ và băm mật khẩu mới (thời gian chủ yếu do BCrypt). |
| `UserBehavierService.deleteMe` | 3 | 3 | +0 | 61 - 65ms | Tự xóa tài khoản cá nhân (xóa mềm). |
| `UserService.updatePass` | 2 | 2 | +0 | 121 - 123ms | Cập nhật mật khẩu mới (thời gian tốn cho BCrypt). |
| `UserService.resolveGoogleUser` (người dùng đã có) | 2 | 2 | +0 | 2ms | Cập nhật thông tin Google ID và map response. |
| `UserBehavierService.createPassword` | 2 | 2 | +0 | 61 - 63ms | Tạo mật khẩu lần đầu cho tài khoản đăng ký bằng Google (BCrypt). |
| `UserService.get` (theo id) | 1 | 1 | +0 | 3ms | Truy vấn chi tiết người dùng theo ID kèm roles. |
| `UserService.get` (theo email) | 1 | 1 | +0 | 2 - 3ms | Truy vấn chi tiết người dùng theo Email. |
| `UserService.getNames` (danh sách id) | 1 | 1 | +0 | 1 - 2ms | Truy vấn tên hiển thị hàng loạt bằng `WHERE id IN (...)`. |
| `UserService.updateStatus` | 1 | 1 | +0 | 3ms | Cập nhật trạng thái tài khoản (`ACTIVE`, `LOCKED`, ...). |
| `UserService.existByEmail` | 1 | 1 | +0 | 0 - 1ms | Kiểm tra trùng email (`COUNT(u) > 0`). |
| `UserService.validUser` | 1 | 1 | +0 | 1 - 2ms | Kiểm tra trạng thái hoạt động của tài khoản. |
| `UserBehavierService.getMe` | 1 | 1 | +0 | 1 - 2ms | Lấy thông tin cá nhân kèm roles và permissions. |
| `UserBehavierService.updateMe` | 1 | 1 | +0 | 2 - 3ms | Cập nhật họ tên, ảnh đại diện người dùng. |
| `ManagerUserService.findByUserId` | 1 | 1 | +0 | 3 - 4ms | Admin xem chi tiết 1 người dùng khác. |
| `ManagerUserService.findAllUser` | 1 | 1 | +0 | 3 - 5ms | Lấy danh sách người dùng cấp dưới. |
| `ManagerRoleService.findRoles` | 0 | 0 | +0 | 0ms | Lấy toàn bộ danh mục vai trò (đã cache Redis/bộ nhớ). |
| `ManagerRoleService.findByRole` (`ROLE_ADMIN`) | 0 | 0 | +0 | 0ms | Lấy vai trò và quyền hạn Admin (đã cache). |
| `ManagerRoleService.findByRole` (`ROLE_USER`) | 0 | 0 | +0 | 0ms | Lấy vai trò và quyền hạn User (đã cache). |
| `ManagerUserService.removeRoleToUser` | - | - | - | - | *Bỏ qua trong benchmark: chưa có vai trò nào gỡ được.* |

---

## 3. Module Auth (Xác thực & Phiên đăng nhập)

> **Hồ sơ dữ liệu:** Tốt nhất: Người dùng thường, 1 refresh token -> Tệ nhất: Admin đủ quyền, 20 refresh token.

| Tên Hàm / Nghiệp vụ | SQL (Tốt nhất) | SQL (Tệ nhất) | Tăng | Thời gian (ms) | Nhận xét / Nguyên nhân |
|---|:---:|:---:|:---:|:---:|---|
| `LoginService<Email>.login` | **6** | **6** | +0 | 69 - 261ms | Kiểm tra tài khoản, băm BCrypt, ghi lịch sử `login_attempts`, tạo session và nạp roles/permissions. |
| `AuthService.refresh` | **5** | **5** | +0 | 8 - 11ms | Xác thực refresh token, xoay vòng token trong Redis/DB, nạp user permissions để cấp access token. |
| `AuthService.register` | 4 | 4 | +0 | 73ms | Kiểm tra trùng email, băm mật khẩu, tạo user `PENDING_VERIFY`, gán `ROLE_USER`, lưu OTP Redis. |
| `AuthService.verifyEmail` | 3 | 3 | +0 | 12 - 17ms | Xác thực OTP, kích hoạt user thành `ACTIVE`, cấp token lần đầu. |
| `AuthService.resetPassword` | 3 | 3 | +0 | 131 - 134ms | Xác thực OTP đặt lại mật khẩu, mã hóa BCrypt mật khẩu mới. |
| `LoginService<Google>.login` (mock xác thực Google) | 3 | 3 | +0 | 6 - 8ms | Đăng nhập Google, đồng bộ user và cấp token. |
| `AuthService.logout` (1 thiết bị) | 2 | 2 | +0 | 3ms | Thu hồi 1 refresh token cụ thể. |
| `TokenService.create` | 2 | 2 | +0 | 2 - 3ms | Tạo cặp access token và refresh token. |
| `TokenService.checkRefreshAndGetUserId` | 2 | 2 | +0 | 3 - 4ms | Kiểm tra tính hợp lệ của refresh token. |
| `TokenService.revokeRefresh` | 2 | 2 | +0 | 2 - 4ms | Hủy bỏ refresh token. |
| `AuthService.resendVerification` | 1 | 1 | +0 | 9ms | Kiểm tra rate limit và sinh OTP mới gửi email. |
| `AuthService.logout` (tất cả thiết bị) | 1 | 1 | +0 | 1 - 2ms | Thu hồi toàn bộ refresh token theo user ID. |
| `AuthService.forgotPassword` | 1 | 1 | +0 | 7 - 9ms | Kiểm tra email tồn tại và gửi OTP quên mật khẩu. |
| `TokenService.revokeAllByUserId` | 1 | 1 | +0 | 1ms | Xóa session theo user ID. |
| `TokenService.revokeAllByEmail` | 1 | 1 | +0 | 1ms | Xóa session theo email. |
| `GoogleService.verifyIdToken` | - | - | - | - | *Bỏ qua trong benchmark: gọi máy chủ Google thật qua mạng.* |

---

## 4. Module Group (Nhóm tài chính, Quỹ & Giao dịch chung)

> **Hồ sơ dữ liệu:**  
> - **Tốt nhất:** 2 thành viên, 0 giao dịch.  
> - **Tệ nhất:** 30 thành viên hoạt động + 5 chờ duyệt, 300 giao dịch đã duyệt + 30 chờ duyệt, 20 nhóm của chủ sở hữu.

| Tên Hàm / Nghiệp vụ | SQL (Tốt nhất) | SQL (Tệ nhất) | Tăng | Thời gian (ms) | Nhận xét / Nguyên nhân |
|---|:---:|:---:|:---:|:---:|---|
| `FundService.reconcileFund` | **9** | **9** | +0 | 11 - 17ms | Kiểm kê quỹ: kiểm tra quyền, load quỹ, đếm giao dịch chờ duyệt, lấy thành viên hợp lệ và gọi `GTransactionService.create` bù trừ chênh lệch. |
| `GTransactionService.create` (chi tiêu chia cả nhóm) | **7** | **7** | +0 | 14 - 28ms | Kiểm tra quyền thành viên, insert giao dịch, chia tiền đều cho các thành viên, insert `participants`, cập nhật số dư quỹ. |
| `GroupService.create` | 4 | 4 | +0 | 4 - 6ms | Tạo nhóm mới, tạo quỹ nhóm mặc định, thêm người tạo làm Trưởng nhóm (`OWNER`). |
| `GroupService.update` | 4 | 4 | +0 | 9 - 19ms | Kiểm tra quyền Trưởng nhóm, cập nhật thông tin nhóm và cấu hình quỹ. |
| `GroupService.archive` | 4 | 4 | +0 | 8 - 9ms | Lưu trữ nhóm (đổi trạng thái lưu trữ). |
| `GroupService.delete` | 4 | 4 | +0 | 9 - 10ms | Xóa mềm nhóm. |
| `GroupService.joinByCode` | 4 | 4 | +0 | 4 - 6ms | Tìm nhóm theo mã mời, kiểm tra chưa tham gia, tạo bản ghi thành viên. |
| `MemberBehavierService.leave` | 4 | 4 | +0 | 8ms | Thành viên rời nhóm: kiểm tra công nợ và đổi trạng thái. |
| `MemberBehavierService.removeMember` | 4 | 4 | +0 | 8 - 11ms | Trưởng nhóm xóa thành viên: kiểm tra công nợ và đổi trạng thái. |
| `GTransactionService.create` (nộp quỹ) | 4 | 4 | +0 | 11 - 12ms | Nộp tiền vào quỹ: kiểm tra thành viên, lưu giao dịch và cộng tiền quỹ. |
| `GTransactionService.bulkConfirm` | 4 | 4 | +0 | 8 - 11ms | Duyệt hàng loạt giao dịch chờ duyệt. |
| `FundService.updateFundKeepper` | 4 | 4 | +0 | 5 - 8ms | Chuyển giao vai trò Người giữ quỹ (thủ quỹ). |
| `ReportService.getBalances` | 4 | 4 | +0 | 5 - 7ms | Tính số dư công nợ của từng thành viên trong nhóm. |
| `GroupService.detail` | 3 | 3 | +0 | 3 - 5ms | Xem chi tiết nhóm kèm thông tin quỹ và phân quyền. |
| `GroupService.unarchive` | 3 | 3 | +0 | 8 - 9ms | Mở lại nhóm đã lưu trữ. |
| `MemberBehavierService.ownerAddMembers` | 3 | 3 | +0 | 6 - 8ms | Trưởng nhóm thêm danh sách thành viên vào nhóm. |
| `GTransactionService.delete` | 3 | 3 | +0 | 7 - 9ms | Xóa mềm giao dịch kèm hoàn lại số dư quỹ. |
| `GTransactionService.confirm` | 3 | 3 | +0 | 6ms | Duyệt 1 giao dịch chờ duyệt kèm cập nhật quỹ. |
| `ReportService.getSummary` (toàn thời gian) | 3 | 3 | +0 | 3 - 5ms | Báo cáo tổng quan thu chi quỹ nhóm toàn thời gian. |
| `ReportService.getSummary` (theo tháng) | 3 | 3 | +0 | 3 - 4ms | Báo cáo tổng quan thu chi quỹ nhóm theo tháng chỉ định. |
| `GroupService.pendingCount` | 2 | 2 | +0 | 3 - 5ms | Đếm số lượng yêu cầu xin vào nhóm đang chờ duyệt. |
| `MemberBehavierService.listMembers` (mọi trạng thái) | 2 | 2 | +0 | 6 - 7ms | Danh sách toàn bộ thành viên trong nhóm. |
| `MemberBehavierService.listMembers` (người chờ duyệt) | 1 | 2 | +1 | 4 - 5ms | *Nghi ngờ N+1*: phát sinh thêm 1 query nạp thông tin người dùng khi có người chờ duyệt. |
| `MemberBehavierService.ownerAddMember` | 2 | 2 | +0 | 2 - 3ms | Thêm 1 thành viên trực tiếp vào nhóm. |
| `MemberBehavierService.transferOwnership` | 2 | 2 | +0 | 6 - 8ms | Chuyển quyền Trưởng nhóm cho thành viên khác. |
| `MemberBehavierService.approve` | 2 | 2 | +0 | 8 - 9ms | Duyệt 1 thành viên xin vào nhóm. |
| `MemberBehavierService.approveAll` | 2 | 2 | +0 | 7 - 8ms | Duyệt tất cả thành viên đang chờ duyệt. |
| `MemberBehavierService.reject` | 2 | 2 | +0 | 6 - 7ms | Từ chối 1 thành viên xin vào nhóm. |
| `MemberBehavierService.rejectAll` | 2 | 2 | +0 | 6 - 8ms | Từ chối toàn bộ thành viên đang chờ duyệt. |
| `GTransactionService.myList` | 2 | 2 | +0 | 7 - 9ms | Danh sách giao dịch cá nhân tham gia. |
| `GTransactionService.listPending` | 2 | 2 | +0 | 6 - 7ms | Danh sách các giao dịch đang chờ duyệt. |
| `GTransactionService.update` | 2 | 2 | +0 | 4 - 6ms | Cập nhật thông tin giao dịch và điều chỉnh quỹ. |
| `GTransactionService.reject` | 2 | 2 | +0 | 4 - 5ms | Từ chối giao dịch chờ duyệt. |
| `GTransactionService.bulkReject` | 2 | 2 | +0 | 5 - 6ms | Từ chối hàng loạt giao dịch chờ duyệt. |
| `GroupService.findNotDeletedById` | 1 | 1 | +0 | 0 - 2ms | Tìm nhóm theo ID. |
| `GroupService.list` | 1 | 1 | +0 | 2ms | Danh sách các nhóm của người dùng. |
| `MemberService.create` / `creates` | 1 | 1 | +0 | 0 - 2ms | Tạo bản ghi thành viên nhóm. |
| `MemberService.countActiveMembers` / `countPendingMembers` | 1 | 1 | +0 | 1 - 2ms | Đếm số lượng thành viên đang hoạt động hoặc chờ duyệt. |
| `MemberService.getMember` / `getActivateMembers` | 1 | 1 | +0 | 1 - 2ms | Truy vấn thông tin thành viên nhóm. |
| `MemberService.getMembersWithStatusIn` / `findIdAllMember` | 1 | 1 | +0 | 1 - 3ms | Truy vấn ID hoặc danh sách thành viên theo trạng thái. |
| `MemberService.allMemberInGroup` / `assertNotInGroup` | 1 | 1 | +0 | 1 - 4ms | Kiểm tra sự tồn tại của thành viên trong nhóm. |
| `GTransactionService.list` (trang 20 bản ghi) | 1 | 1 | +0 | 5 - 7ms | Phân trang danh sách giao dịch (đã tối ưu `@EntityGraph`, 1 query duy nhất). |
| `GTransactionService.list` (trang 100 bản ghi) | 1 | 1 | +0 | 6 - 7ms | Phân trang 100 giao dịch (1 query duy nhất). |
| `GTransactionService.detail` | 1 | 1 | +0 | 4ms | Chi tiết 1 giao dịch nhóm kèm người tham gia. |
| `GTransactionService.countPendingForGroup` | 1 | 1 | +0 | 1ms | Đếm số giao dịch chờ duyệt (`COUNT`). |
| `GTransactionService.sumConfirmedAmount` (toàn thời gian / kỳ) | 1 | 1 | +0 | 0 - 1ms | Tính tổng tiền thu hoặc chi bằng `SUM(...)`. |
| `FundService.adjustBalance` | 1 | 1 | +0 | 0 - 1ms | Điều chỉnh cộng/trừ số dư quỹ. |
| `FundService.create` | - | - | - | - | *Bỏ qua: chỉ `GroupService.create` gọi nội bộ, đã nằm trong số đo của hàm đó.* |

---

## 5. Danh sách toàn bộ các hàm có số câu SQL > 5 (Cần ưu tiên tối ưu)

Sau các đợt tối ưu hoá hệ thống, danh sách các hàm có số query lớn hơn 5 đã giảm mạnh từ **13 hàm** xuống chỉ còn **3 hàm**:

| STT | Module | Tên Hàm / Nghiệp vụ | Số Query Hiện Tại | Số Query Mục Tiêu | Nguyên Nhân & Giải Pháp Rút Gọn |
|:---:|:---:|---|:---:|:---:|---|
| 1 | **Group** | `FundService.reconcileFund` | **9** | **3 - 4** | **Nguyên nhân:** Kiểm tra quyền (1), nạp quỹ (1), đếm pending txns (1), nạp danh sách thành viên loại trừ (1), và gọi `GTransactionService.create` tạo giao dịch bù trừ (chiếm 5 query).<br>**Giải pháp:** Tối ưu hóa bên trong `GTransactionService.create` và gộp các bước kiểm tra điều kiện kiểm kê. |
| 2 | **Group** | `GTransactionService.create` (chi tiêu cả nhóm) | **7** | **3** | **Nguyên nhân:** Kiểm tra quyền thành viên, kiểm tra danh mục/nguồn tiền, nạp số dư quỹ, insert giao dịch, chia tiền và lưu từng người tham gia (`participants`), cập nhật số dư quỹ.<br>**Giải pháp:** Batch insert danh sách participants bằng 1 statement thay vì lưu rời rạc; cập nhật số dư quỹ nguyên tử trực tiếp bằng lệnh UPDATE (`UPDATE funds SET current_balance = current_balance - ?`). |
| 3 | **Auth** | `LoginService<Email>.login` | **6** | **3 - 4** | **Nguyên nhân:** Load User, kiểm tra khóa, ghi lịch sử `login_attempts`, nạp quan hệ roles và permissions để cấp JWT token.<br>**Giải pháp:** Tối ưu truy vấn quyền hạn tối thiểu khi đăng nhập; tận dụng cache Redis cho danh mục roles/permissions của user. |

---

## 6. Khả năng chịu tải quy mô lớn (Scale Test: 1.000 - 1.000.000 giao dịch)

### Kịch bản A: Một nhóm lớn (30 thành viên, mỗi giao dịch chia 5 người, 100 giao dịch chờ duyệt)

| Hàm nghiệp vụ (Số SQL / mili giây) | 1.000 giao dịch | 10.000 giao dịch | 100.000 giao dịch | 1.000.000 giao dịch | Nhận xét |
|---|:---:|:---:|:---:|:---:|---|
| `GTransactionService.countPendingForGroup` | 1 / 4ms | 1 / 3ms | 1 / 3ms | 1 / 6ms | Ổn định tuyệt đối nhờ index |
| `GTransactionService.sumConfirmedAmount` | 1 / 3ms | 1 / 4ms | 1 / 18ms | 1 / 106ms | Quét aggregate tăng tuyến tính nhẹ |
| `GTransactionService.list` (trang 1, 20 bản ghi) | 1 / 46ms | 1 / 9ms | 1 / 9ms | 1 / 12ms | Phân trang đầu trang cực nhanh |
| `GTransactionService.list` (trang 1, 100 bản ghi) | 1 / 28ms | 1 / 11ms | 1 / 9ms | 1 / 8ms | Ổn định ở ~8-11ms |
| `GTransactionService.list` (trang sâu: trang 1000 x 100) | 1 / 9ms | 1 / 13ms | 1 / 86ms | 1 / 179ms | Offset sâu tốn thời gian scan index |
| `GTransactionService.list` (lọc theo tháng, 100 bản ghi) | 2 / 19ms | 2 / 12ms | 2 / 14ms | 2 / 16ms | Ổn định nhờ index theo ngày |
| `GTransactionService.listPending` | 2 / 19ms | 2 / 12ms | 2 / 12ms | 2 / 9ms | Tốc độ cao và không đổi |
| `ReportService.getSummary` (toàn thời gian) | 3 / 10ms | 3 / 10ms | 3 / 40ms | 3 / 192ms | Aggregate trên 1 triệu dòng |
| `ReportService.getSummary` (theo tháng) | 3 / 11ms | 3 / 7ms | 3 / 14ms | 3 / 25ms | Rất nhanh khi giới hạn theo kỳ |
| `ReportService.getBalances` | 4 / 17ms | 4 / 16ms | 4 / 16ms | 4 / 12ms | Độc lập với số lượng giao dịch |
| `GTransactionService.create` (giao dịch mới) | 5 / 28ms | 5 / 18ms | 5 / 23ms | 5 / 15ms | Ghi mới luôn duy trì ~15-20ms |

### Kịch bản B: Nhiều nhóm nhỏ (Mỗi nhóm 200 giao dịch, 6 thành viên; số liệu tổng toàn hệ thống)

| Hàm nghiệp vụ (Số SQL / mili giây) | 10.000 giao dịch | 100.000 giao dịch | 1.000.000 giao dịch | Nhận xét |
|---|:---:|:---:|:---:|---|
| `GTransactionService.countPendingForGroup` | 1 / 2ms | 1 / 2ms | 1 / 3ms | Không đổi |
| `GTransactionService.sumConfirmedAmount` | 1 / 1ms | 1 / 2ms | 1 / 2ms | Tính trên phạm vi 1 nhóm cực nhanh (~2ms) |
| `GTransactionService.list` (trang 1, 20 bản ghi) | 1 / 7ms | 1 / 6ms | 1 / 7ms | Tốc độ đồng đều ~7ms |
| `GTransactionService.list` (trang 1, 100 bản ghi) | 1 / 8ms | 1 / 7ms | 1 / 7ms | Ổn định |
| `GTransactionService.list` (lọc theo tháng, 100 bản ghi) | 2 / 8ms | 2 / 7ms | 2 / 6ms | Ổn định |
| `ReportService.getSummary` (toàn thời gian) | 3 / 6ms | 3 / 5ms | 3 / 7ms | Nhanh hơn kịch bản A nhiều vì dữ liệu phân tán theo nhóm |
| `ReportService.getSummary` (theo tháng) | 3 / 8ms | 3 / 6ms | 3 / 6ms | ~6ms |
| `ReportService.getBalances` | 4 / 9ms | 4 / 9ms | 4 / 10ms | ~9-10ms |
| `GTransactionService.create` (giao dịch mới) | 5 / 15ms | 5 / 13ms | 5 / 13ms | Duy trì ~13ms |
