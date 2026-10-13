# Vấn đề: tra id ↔ code danh mục chưa dùng `CateCache`

> **Đã xử lý (phương án C):** `CateCache` lưu thêm tập id đã xóa mềm; `CateImpl.delete` gọi `markDeleted` sau commit;
> `findIdByCode` dùng `findActiveId`, `findCodesByIds` dùng cache. Mục 1-2 dưới đây là hiện trạng trước khi sửa.

## 1. Hiện trạng

- `CateCache` (`module/category/helper/CateCache.java`) giữ cache hai chiều id ↔ `code`, nạp toàn bộ một lần khi
  `ApplicationReadyEvent`.
- Hiện chỉ `CateImpl.create` dùng cache (`cateCache.existsCode`, `cateCache.register` sau commit).
- `CategoryRepository.findAllCodes` **không lọc `deleted`** (`CategoryRepository.java:22-26`), nên cache giữ cả danh mục
  đã xoá mềm. Đây là chủ ý, ghi ở `CateCache.java:20-22`: giữ chỗ `code` để không tạo trùng và vẫn tra ngược được id cũ
  của giao dịch đã phát sinh.
- `CateImpl.delete` (`CateImpl.java:139-141`) chỉ `setDeleted(true)`, không gọi gì vào cache.

## 2. Vấn đề

Sau khi giao dịch nhóm đổi sang `category_code`, hai chỗ sau vẫn query DB dù đã có cache:

| Hàm (`CategoryService`)                                | Nơi gọi                                                   | Tần suất                                              |
|:-------------------------------------------------------|:----------------------------------------------------------|:------------------------------------------------------|
| `findIdByCode(code)` (code → id, loại danh mục đã xoá) | `ExpenseTransaction.build`, `ExpenseUpdate.update`        | mỗi lần tạo/sửa giao dịch chi tiêu                    |
| `findAllByIds(ids)` (id → code, gồm cả đã xoá)         | `TransactionHelper.buildDetailResList` / `buildDetailRes` | mỗi trang danh sách và mỗi lần trả chi tiết giao dịch |

`CategoryService` chưa có hàm nào dựa trên `CateCache`; theo quy tắc 2.2 module khác không được dùng `CateCache`
trực tiếp mà phải đi qua `CategoryService`.

## 3. Ràng buộc cần giữ khi sửa

- Code của danh mục đã xoá vẫn bị giữ chỗ (không tạo trùng).
- Giao dịch cũ có danh mục đã xoá vẫn tra được `code`.
- Tạo/sửa giao dịch phải loại danh mục đã xoá (`CATEGORY_NOT_FOUND`).
- Cache chỉ đúng khi chạy một instance; cập nhật cache chỉ sau khi transaction commit.

## 4. Phương án đã cân nhắc

|   | Mô tả                                                          | Đánh đổi                                                                                                          |
|:--|:---------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------|
| A | Giữ cache như cũ. id → code dùng cache, code → id vẫn query DB | Ít sửa nhất, bỏ được phần query nhiều nhất (danh sách). Vẫn còn một query khi tạo/sửa                             |
| B | Loại danh mục xoá mềm khỏi cache                               | Mất khả năng tra code cho giao dịch cũ, và code đã xoá có thể bị tạo lại (cần kiểm tra ràng buộc UNIQUE trong DB) |
| C | Cache lưu thêm cờ xoá (id, code, deleted)                      | Cả hai chiều không query DB và vẫn tra được danh mục đã xoá. Phải sửa `CateCache` và `CateImpl.delete`            |

## 5. Việc cần làm khi xử lý

- Thêm hàm vào `CategoryService` / `CateImpl` dựa trên `CateCache` (ví dụ tra code theo id).
- Sửa `GroupTxnHelper`, và `ExpenseTransaction` / `ExpenseUpdate` nếu chọn phương án C.
- Cập nhật `CateCacheTest`, `CateImplTest` (và test group liên quan).
- Lập plan riêng trước khi sửa, theo `.agents/plan-rule.md`.
