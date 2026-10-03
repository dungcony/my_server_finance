# Plan: sửa hai điểm N+1 tìm thấy bằng test hiệu năng

Nguồn số liệu: `target/perf-user.txt`, `target/perf-group.txt` (chạy 04/10/2026).

## 0. Hai điểm cần sửa

| # | Điểm N+1 | Số đo (tốt nhất → tệ nhất) | Hàm bị ảnh hưởng |
|---|---|---|---|
| A | `UserService.getNames` nạp vai trò + quyền của từng người dù chỉ cần tên | 5 → 119 SQL (102 người) | `GroupService.detail` 6→34, `GroupService.update` 8→36, `MemberBehavierService.listMembers` 6→39, `ReportService.getBalances` 8→36 |
| B | Danh sách giao dịch nhóm nạp người chia tiền của từng giao dịch | 6 → 103 SQL (100 giao dịch) | `GTransactionService.list`, `myList`, `listPending` |

Mục tiêu sau khi sửa: số SQL **không còn tăng theo số thành viên / số giao dịch** (hằng số hoặc tăng rất chậm).

## 1. Các file sẽ thay đổi

| File | Điểm | Thay đổi |
|---|---|---|
| `user/mapper/UserMapper.java` | A | Thêm `toNameResponse(User)`: map các trường hiển thị, **bỏ qua `roles`** |
| `user/service/impl/UserServiceImpl.java` | A | `getNames` dùng `toNameResponse` thay `toResponse` |
| `group/entity/GTransaction.java` | B | Thêm `@BatchSize(size = 100)` trên `participants` |
| `src/test/.../user/service/UserNamesQueryCountIntegrationTest.java` | A | Test mới: số SQL của `getNames` không đổi khi tăng số người |
| `src/test/.../group/service/GroupNPlusOneRegressionIntegrationTest.java` | A + B | Test mới: số SQL của `list`, `detail`, `listMembers` không đổi khi tăng thành viên / giao dịch |

Không đổi API, không đổi CSDL.

## 2. Lý do và hướng giải quyết

### A. `getNames`

- **Nguyên nhân gốc:** `getNames` gọi `userMapper.toResponse(u)`, mà `toResponse` luôn gọi `mapRoles(user)` — duyệt `userRoles` (lazy)
  rồi `role.rolePermissions` (lazy) của **từng** người. Hai nơi gọi `getNames` (`MemberViewEnricher`, `GReportServiceImpl.getBalances`)
  chỉ dùng `fullName()`.
- **Hướng sửa:** thêm một phương thức mapper riêng không đụng tới quan hệ lazy.

```java
// UserMapper — chỉ lấy trường hiển thị, không nạp vai trò nên không phát sinh truy vấn phụ
@Mapping(target = "roles", ignore = true)
@Mapping(target = "password", ignore = true)
@Mapping(target = "googleId", ignore = true)
@Mapping(target = "isDeleted", expression = "java(user.isDeleted())")
UserRes toNameResponse(User user);
```

```java
// UserServiceImpl.getNames
} else user = userMapper.toNameResponse(u);
```

- **Hệ quả cần biết:** `UserRes` trả về từ `getNames` sẽ có `roles = null`, `password = null`, `googleId = null`. Hai nơi gọi hiện tại
  không đọc các trường này nên an toàn; ai gọi `getNames` sau này mà cần vai trò phải dùng `get`. Ghi chú này đưa vào javadoc của `getNames`.
- **Kỳ vọng:** `getNames` còn đúng 1 SQL (`findAllById`) bất kể số người → mọi hàm group ở bảng 0 mất khoảng 28 đến 33 SQL.

### B. Danh sách giao dịch nhóm

- **Nguyên nhân gốc:** `GTransaction.participants` là `@ElementCollection` mặc định lazy. Các hàm tìm một giao dịch hoặc toàn bộ giao dịch đã
  duyệt đã có `@EntityGraph(attributePaths = "participants")` nên chỉ 1 SQL. Riêng `findAll(spec, pageable)` (dùng cho `list`/`myList`/`listPending`)
  không có, nên `buildDetailRes` nạp người chia tiền từng giao dịch một.
- **Vì sao không dùng `@EntityGraph`/`JOIN FETCH` cho truy vấn phân trang:** fetch một collection kèm phân trang khiến Hibernate phân trang
  **trong bộ nhớ** sau khi nạp toàn bộ kết quả (cảnh báo HHH90003004) — vô tình tạo ra vấn đề hiệu năng tệ hơn với nhóm lớn.
- **Hướng sửa:** `@BatchSize(size = 100)` — Hibernate nạp người chia tiền của tối đa 100 giao dịch trong một câu SQL.

```java
// GTransaction
@ElementCollection
@BatchSize(size = 100)
@CollectionTable(name = "group_transaction_participants", joinColumns = @JoinColumn(name = "group_transaction_id"))
@Builder.Default
private List<TransactionParticipant> participants = new ArrayList<>();
```

- **Kỳ vọng:** `list` (100 bản ghi) từ 103 SQL xuống khoảng 7 (6 SQL cố định + 1 SQL gom người chia tiền); trang 20 bản ghi khoảng 7.

## 3. Cách kiểm chứng (TDD)

1. **Viết test trước và chạy ra đỏ.** Hai test mới dựng hai mức dữ liệu (ví dụ 3 rồi 30 thành viên; 10 rồi 100 giao dịch) và khẳng định
   **số SQL của mức lớn không vượt số SQL của mức nhỏ quá một ngưỡng nhỏ cố định**. Trước khi sửa phải đỏ đúng lý do (SQL tăng theo dữ liệu).
2. Sửa code, chạy lại cho xanh.
3. Chạy lại `UserServicePerfIntegrationTest` và `GroupServicePerfIntegrationTest`, so sánh báo cáo trước / sau.

Dùng lại `SqlCountingConfig` (đếm SQL tầng JDBC) đã có ở `src/test/.../performance/`.

## 4. Cần anh/chị quyết

1. **`@BatchSize` trên một collection, hay đặt toàn cục** `hibernate.default_batch_fetch_size=100`? Đề xuất **trên một collection**: phạm vi thay đổi nhỏ, kiểm soát được.
   Toàn cục sẽ giảm N+1 ở mọi nơi nhưng đổi hành vi truy vấn của toàn hệ thống cùng lúc, khó truy nguyên nếu có gì lệch.
2. **Có chấp nhận `getNames` trả `UserRes` thiếu `roles`/`password`/`googleId` không?** Đề xuất chấp nhận, kèm ghi chú javadoc như mục A.

## 5. Giới hạn

- Máy phát triển của Claude không có Docker nên mọi test ở đây chỉ compile-check được; anh/chị chạy và gửi lại số liệu.
- `findConfirmedTransactions` / `getBalances` nạp toàn bộ giao dịch đã duyệt (đã 1 SQL nhờ `@EntityGraph`) — chi phí tăng theo số giao dịch,
  không phải N+1; chờ kết quả `GroupScalePerfIntegrationTest` mới quyết có làm hay không.
- `UserService.get` chỉ 3 đến 4 SQL, không N+1 (số liệu cũ đã loại bỏ nghi ngờ này).
