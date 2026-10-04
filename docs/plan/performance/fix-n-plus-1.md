# Plan: sửa hai điểm N+1 tìm thấy bằng test hiệu năng

- **Nguồn số liệu:** `target/perf-user.txt`, `target/perf-group.txt` (chạy 04/10/2026)
- **Trạng thái:** điểm A ✅ đã làm · điểm B ⏳ CHỜ XÁC NHẬN
- **Xác nhận điểm B:** `[ ] PROCESS`

## 0. Hai điểm

| # | Điểm N+1 | Số đo (tốt nhất → tệ nhất) | Trạng thái |
|---|---|---|---|
| A | `UserService.getNames` nạp vai trò + quyền từng người dù chỉ cần tên | 5 → 119 SQL (102 người) | ✅ đã sửa (xem mục 1) |
| B | Danh sách giao dịch nhóm nạp người chia tiền từng giao dịch | 6 → 103 SQL (100 giao dịch) | ⏳ làm ở mục 2 |

Mục tiêu: số SQL **không còn tăng theo số thành viên / số giao dịch**.

## 1. Điểm A — đã làm (ghi lại để đối chiếu)

`getNames` đổi sang projection `UserRepository.findDisplay` trả `UserNameDisplayRes` (id, họ, tên, trạng thái, cờ xoá), một câu SQL,
không nạp entity. Việc che tên tài khoản xoá/khoá nằm trong `UserNameDisplayRes.fullName()`. Đã chặn `ids` null/rỗng, có test.
Hướng này tốt hơn đề xuất ban đầu của plan (thêm `toNameResponse` ở mapper).

**Việc còn lại của A:** chạy lại `UserServicePerfIntegrationTest` và `GroupServicePerfIntegrationTest` để xác nhận `getNames`,
`GroupService.detail/update`, `listMembers`, `getBalances` giảm về mức hằng số. Chưa kiểm chứng (cần Docker).

## 2. Điểm B — giao dịch nhóm

### 2.1 Các file sẽ thay đổi

| File | Thay đổi |
|---|---|
| `group/entity/GTransaction.java` | thêm `@BatchSize(size = 100)` trên `participants` |
| `src/test/.../group/service/GroupTransactionListQueryCountIntegrationTest.java` | **mới**, test đếm SQL của `list`, `myList`, `listPending` |

Không đổi API, không đổi CSDL, không đổi logic nghiệp vụ.

### 2.2 Lý do

- `participants` là `@ElementCollection` mặc định lazy. Các truy vấn một giao dịch và "toàn bộ giao dịch đã duyệt" đã có
  `@EntityGraph(attributePaths = "participants")` nên chỉ 1 SQL. Riêng `findAll(spec, pageable)` (dùng cho `list`, `myList`,
  `listPending`) không có, nên `buildDetailRes` nạp người chia tiền từng giao dịch một.
- **Không dùng `JOIN FETCH`/`@EntityGraph` cho truy vấn phân trang:** fetch collection kèm phân trang khiến Hibernate phân trang
  **trong bộ nhớ** sau khi nạp toàn bộ kết quả (cảnh báo HHH90003004) — tệ hơn với nhóm lớn.
- **Hướng sửa:** `@BatchSize` — Hibernate nạp người chia tiền của tối đa 100 giao dịch trong một câu.

```java
@ElementCollection
@BatchSize(size = 100)
@CollectionTable(name = "group_transaction_participants", joinColumns = @JoinColumn(name = "group_transaction_id"))
@Builder.Default
private List<TransactionParticipant> participants = new ArrayList<>();
```

Kích thước 100 khớp với kích thước trang lớn nhất cho phép (`GroupTransactionFilterReq.getPageSize()` chặn ở 100), nên mỗi trang
chỉ cần đúng 1 câu nạp người chia tiền.

### 2.3 Cách kiểm chứng (TDD)

1. **Viết test trước, chạy ra đỏ.** Test dựng một nhóm, tạo 10 giao dịch rồi đo số SQL của `list` (trang 100); tạo thêm tới 100 giao dịch
   rồi đo lại. Khẳng định **số SQL ở 100 giao dịch không lớn hơn số SQL ở 10 giao dịch quá 1**. Trước khi sửa phải đỏ đúng lý do
   (SQL tăng theo số giao dịch). Làm tương tự cho `myList` và `listPending` (giao dịch chờ duyệt do thành viên thường tạo).
2. Thêm `@BatchSize`, chạy lại cho xanh.
3. Chạy lại `GroupServicePerfIntegrationTest`, so sánh báo cáo trước/sau (kỳ vọng `list` 100 bản ghi: 103 → khoảng 7 SQL).

Dùng lại `SqlCountingConfig` (`src/test/.../performance/`). Dựng dữ liệu bằng chính service như `GroupServicePerfIntegrationTest`.

### 2.4 Rủi ro

- Test này cần Docker; Claude chỉ compile-check được, người chạy gửi lại kết quả.
- `@BatchSize` chỉ giảm số câu SQL, không đổi dữ liệu trả về; mỗi giao dịch vẫn nhận đủ người chia tiền như trước.
- Nếu sau này `getPageSize()` nâng trên 100 thì cần nâng `size` tương ứng (không sai, chỉ thêm một câu nạp).
