# Plan: Test đo hiệu năng từng hàm service (user / auth / group)

## 0. Mục tiêu

Mỗi module có **một class test** gọi trực tiếp từng hàm service công khai và ghi lại **số câu SQL + thời gian**
của hàm đó, để chỉ ra đúng hàm nào chậm hoặc bị N+1. Quét theo endpoint (`EndpointSqlCountIntegrationTest`)
chỉ cho biết endpoint nào tệ, không chỉ ra được hàm gây ra — vì một endpoint gọi nhiều hàm service.

Cách phát hiện N+1: đo mỗi hàm ở **hai hồ sơ dữ liệu** — **tốt nhất** (dữ liệu tối thiểu) và **tệ nhất** (dữ liệu lớn nhất
có thể gặp thật). Số câu SQL **tăng từ tốt nhất sang tệ nhất** thì nghi N+1; số tuyệt đối ở hồ sơ tệ nhất cho biết chặn trên
của hàm đó. Hàm đọc cũng phải dựng sẵn dữ liệu cho từng hồ sơ — đo trên bảng rỗng không nói lên điều gì.

## 1. Các file sẽ thay đổi

| File | Trạng thái | Vai trò |
|---|---|---|
| `src/test/java/.../performance/ServicePerfSupport.java` | mới | Khung dùng chung: `measure(nhãn, hành động)` đo SQL + ms, chạy khởi động nguội, gom kết quả hai mức dữ liệu, dựng bảng báo cáo, ghi file `target/perf-<module>.txt` |
| `src/test/java/.../user/service/UserServicePerfIntegrationTest.java` | mới | Đo các hàm của `UserService`, `UserBehavierService`, `ManagerUserService`, `ManagerRoleService` |
| `src/test/java/.../auth/service/AuthServicePerfIntegrationTest.java` | mới | Đo các hàm của `AuthService`, `TokenService`, `LoginService` |
| `src/test/java/.../group/service/GroupServicePerfIntegrationTest.java` | mới | Đo các hàm của `GroupService`, `MemberService`, `MemberBehavierService`, `GTransactionService`, `GTransactionReviewService`, `FundService`, `ReportService` |
| `src/test/java/.../performance/SqlCountingConfig.java` | giữ nguyên | Bộ đếm SQL tầng JDBC đã có, dùng lại |
| `src/test/java/.../performance/EndpointSqlCountIntegrationTest.java` | giữ nguyên | Bổ sung góc nhìn endpoint, không xoá |
| `src/test/java/.../user/service/UserGetQueryCountIntegrationTest.java` | **đề xuất xoá** | Chỉ đo một hàm, đã bị class mới bao |

Không sửa code `src/main`.

## 2. Lý do thiết kế

- **Gọi service trực tiếp, không qua MockMvc:** loại bỏ nhiễu của filter, JWT, serialize; con số là của đúng hàm.
- **Một khung dùng chung** để ba class không lặp lại logic đo/báo cáo (quy ước: tách đoạn lặp thành private method).
- **Bộ đếm SQL tầng JDBC** (đã có) để tính cả `JdbcTemplate`, mà bộ đếm Hibernate không thấy.
- **Hai mức dữ liệu** để phân biệt "nhiều câu SQL nhưng cố định" với "N+1".
- **Không assert hiệu năng, chỉ báo cáo**: lỗi một hàm (dữ liệu mẫu sai, thiếu quyền) ghi vào cột `ghi_chu`, không làm đỏ test, để không che số liệu các hàm còn lại.

## 3. Phân loại hàm và cách đo

| Loại | Cách đo | Ví dụ |
|---|---|---|
| **Đọc thuần** | gọi trực tiếp: khởi động nguội một lần rồi đo lần hai | `UserService.get`, `GroupService.list`, `MemberService.getActivateMembers`, `ReportService.getBalances` |
| **Ghi, lặp lại được** | mỗi lần gọi dùng dữ liệu mới tạo sẵn (không tính vào số đo) | `GTransactionService.create`, `MemberBehavierService.ownerAddMembers`, `UserBehavierService.updateMe` |
| **Ghi, một lần / phá huỷ** | tạo thực thể dùng một lần ngay trước khi gọi, đo đúng một lần (không khởi động nguội) | `GroupService.delete`, `MemberBehavierService.leave`, `UserBehavierService.deleteMe`, `AuthService.logout` |
| **Phụ thuộc ngoài** | thay bằng mock, ghi rõ "đã mock" trong báo cáo | gửi email (`forgotPassword`, `resendVerification`), `GoogleService.verifyIdToken` |

Hàm có `SecurityContextUtil.currentUserId()` (vd `ManagerRoleService`, `ManagerUserService`): đặt `SecurityContext` giả là admin trước khi gọi.

## 3b. Hồ sơ dữ liệu tốt nhất / tệ nhất (khung dùng chung dựng sẵn trước khi đo)

Mỗi class dựng hồ sơ **tốt nhất** rồi đo toàn bộ hàm, sau đó **nới dữ liệu tăng dần** lên hồ sơ **tệ nhất** và đo lại
đúng các hàm đó. Làm tăng dần nên chỉ dựng dữ liệu một lần, không dựng hai bộ riêng.

| Module | Tốt nhất | Tệ nhất |
|---|---|---|
| user | 1 người dùng, 1 vai trò `ROLE_USER` | tài khoản admin giữ cả `ROLE_ADMIN` và `ROLE_USER`, đủ mọi `PermissionName`; 100 người dùng để duyệt danh sách |
| auth | 1 tài khoản, 1 refresh token đang sống | tài khoản admin (vai trò + quyền nhiều nhất), 20 refresh token đang sống (nhiều thiết bị) |
| group | nhóm 2 thành viên, 0 giao dịch | nhóm 30 thành viên (5 đang chờ duyệt), 300 giao dịch, mỗi giao dịch chia cho toàn bộ thành viên, có cả nộp quỹ lẫn chi, nhiều giao dịch chờ duyệt |

Hàm có tham số (phân trang, lọc) đo thêm ở **tệ nhất theo tham số**: kích thước trang lớn nhất cho phép, bộ lọc rộng nhất.
Hai hồ sơ này là mặc định đề xuất; chỉnh con số trong bảng nếu muốn đo mức khác (số lớn hơn thì test chạy lâu hơn).

## 4. Số hàm cần phủ (đếm từ interface)

| Module | Service | Số hàm |
|---|---|---|
| user | `UserService` 8, `UserBehavierService` 5, `ManagerUserService` 6, `ManagerRoleService` 4 | 23 |
| auth | `AuthService` 7, `TokenService` 5, `LoginService` 1 (+ `GoogleService` 1, mock) | 14 |
| group | `GroupService` 10, `MemberService` ≥10, `MemberBehavierService` 10, `GTransactionService` 11, `GTransactionReviewService` 4, `FundService` 4, `ReportService` 2 | ≈51 |

## 5. Báo cáo mẫu (số minh hoạ, không phải kết quả đo)

```
[PERF] group — TỐT NHẤT (2 thành viên, 0 giao dịch) -> TỆ NHẤT (30 thành viên, 300 giao dịch)
hàm                                  sql_tot  sql_te  tang  ms_tot  ms_te   ghi_chu
GTransactionServiceImpl.list               3     303  +300       8    540   NGHI N+1
ReportServiceImpl.getBalances              9       9    +0      12     45
MemberServiceImpl.getActivateMembers       2      32   +30       4     60   NGHI N+1
```

## 6. Cần anh/chị quyết

1. **Phủ cả hàm ghi và hàm phá huỷ ngay bây giờ, hay làm theo hai bước?**
   Đề xuất hai bước: bước 1 là khung dùng chung + mọi hàm đọc + các hàm ghi lặp lại được (N+1 thường nằm ở hàm đọc);
   bước 2 là hàm ghi một lần/phá huỷ. Lý do: bước 1 nhỏ hơn nhiều và cho kết quả có giá trị sớm, phần phá huỷ cần nhiều
   dữ liệu dựng sẵn nhất và dễ sai nhất khi viết mà chưa chạy được.
2. **Xoá `UserGetQueryCountIntegrationTest`?** Đề xuất xoá.
3. **Đặt các class ở đâu?** Đề xuất mirror package module (`user/service`, `auth/service`, `group/service`) đúng quy ước 2.13,
   riêng khung dùng chung ở `performance/`.

## 7. Giới hạn đã biết

- Máy phát triển của Claude không có Docker nên các test này **chỉ compile-check được**, chưa chạy thử; anh/chị chạy và gửi lại
  báo cáo để chỉnh dữ liệu mẫu.
- Bộ test cũ đang lỗi compile (khoảng 10 file sau refactor) nên chạy qua thư mục tạm chứa riêng các file này, như đã làm ở
  `EndpointSqlCountIntegrationTest`.
- Bộ đếm SQL là toàn cục, tiến trình nền có thể làm số liệu cao hơn thực tế một chút.
