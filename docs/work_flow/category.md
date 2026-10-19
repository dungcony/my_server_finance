# Workflow — Danh mục thu chi (category)

Code chính: [CategoryController](../../src/main/java/com/datn/financeapp/module/category/controller/CategoryController.java) (API danh mục),
[CateBehavierImpl](../../src/main/java/com/datn/financeapp/module/category/service/impl/CateBehavierImpl.java) (nghiệp vụ quản trị cây danh mục),
[CateImpl](../../src/main/java/com/datn/financeapp/module/category/service/impl/CateImpl.java) (hàm tra cứu cho các module khác gọi),
[CategoryRepository](../../src/main/java/com/datn/financeapp/module/category/repository/CategoryRepository.java) (truy vấn CSDL & tiền tố cây),
[CateMapper](../../src/main/java/com/datn/financeapp/module/category/mapper/CateMapper.java) (chuyển đổi Entity sang DTO).

Đặc tả RESTful API chi tiết: xem [docs/api/category.md](../api/category.md).

---

## Quy tắc cây danh mục và Ràng buộc CSDL

Danh mục trong hệ thống là **cây phân cấp nhiều cấp dùng chung cho toàn bộ người dùng** (không thuộc quyền sở hữu riêng của cá nhân nào). Danh mục do quản trị viên quản lý; người dùng thông thường chỉ có quyền đọc để phân loại giao dịch thu/chi, lập ngân sách và thống kê.

- `level`: độ sâu phân cấp trong cây. Danh mục gốc có `level = 0`. Danh mục con có `level = level_cha + 1`.
- `path`: chuỗi các mã `code` nối bằng dấu `/` từ gốc tới danh mục hiện tại (ví dụ: `an_uong/an_sang`). Dùng để truy vấn mọi danh mục con cháu hậu duệ nhanh chóng bằng toán tử tiền tố `left(c.path, length(:path) + 1) = concat(:path, '/')`.
- `is_leaf`: cờ đánh dấu nút lá.
  - `true`: nút lá, không có danh mục con nào đang hoạt động.
  - `false`: nút cha, có ít nhất một danh mục con chưa bị xóa mềm.
- `is_deleted`: cờ xóa mềm. Danh mục bị xóa mềm vẫn giữ nguyên trong bảng để các giao dịch cũ trong lịch sử không bị mồ côi. Mã `code` của danh mục đã xóa **vẫn giữ chỗ vĩnh viễn**, không thể tái sử dụng cho danh mục mới (`uq_cat_code`).

### Bảng ràng buộc & Trigger CSDL liên quan (migration_v2)

| Tên Ràng buộc / Trigger | File Migration | Khi nào chạy / Mục đích |
|---|---|---|
| `uq_cat_code` | [V2](../../src/main/resources/db/migration_v2/V2__categories_icon.sql) | `UNIQUE (code)` — Mã duy nhất toàn hệ thống, kể cả danh mục đã xóa mềm. |
| `uq_cat_name` | [V2](../../src/main/resources/db/migration_v2/V2__categories_icon.sql) | `UNIQUE (lower(name)) WHERE NOT is_deleted` — Tên danh mục không trùng trên toàn cây (không phân biệt hoa/thường) trong số các danh mục chưa xóa. |
| `fk_cat_parent` | [V2](../../src/main/resources/db/migration_v2/V2__categories_icon.sql) | `FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT` — Khóa ngoại trỏ đến danh mục cha. |
| `fk_cat_icon` | [V2](../../src/main/resources/db/migration_v2/V2__categories_icon.sql) | `FOREIGN KEY (icon_code) REFERENCES icons(code) ON UPDATE CASCADE ON DELETE RESTRICT` — Khóa ngoại trỏ đến kho biểu tượng SVG. |
| `ck_cat_color` | [V2](../../src/main/resources/db/migration_v2/V2__categories_icon.sql) | `CHECK (color ~ '^#[0-9A-Fa-f]{6}$')` — Định dạng mã màu Hex chuẩn 7 ký tự `#RRGGBB`. |
| `ck_cat_level` | [V2](../../src/main/resources/db/migration_v2/V2__categories_icon.sql) | `CHECK (level >= 0)` — Cấp độ không âm. |
| `ck_cat_parent` | [V2](../../src/main/resources/db/migration_v2/V2__categories_icon.sql) | `CHECK (parent_id IS DISTINCT FROM id)` — Ngăn danh mục tự trỏ cha vào chính mình ở tầng DB. |
| `ck_cat_root` | [V2](../../src/main/resources/db/migration_v2/V2__categories_icon.sql) | `CHECK ((parent_id IS NULL) = (level = 0))` — Danh mục gốc bắt buộc `level = 0` và `parent_id = NULL`. |
| `trg_transactions_category_leaf` | [V11](../../src/main/resources/db/migration_v2/V11__trigger_transaction_category_leaf.sql) | Chạy khi thêm hoặc sửa `category_code` của giao dịch. Khóa dòng danh mục `FOR SHARE`: bắt buộc danh mục phải tồn tại, chưa xóa (`is_deleted = false`) và **phải là nút lá** (`is_leaf = true`). Tránh race condition khi admin sửa cây danh mục cùng lúc với user ghi giao dịch. |

---

## Danh sách danh mục gốc — `GET /categories`

Endpoint công khai cho mọi người dùng đã xác thực (Bearer Token).

```text
getMasters
-> categoryRepository.findByParentIsNullAndIsDeletedFalseOrderByNameAsc()
   (lấy các danh mục có parent_id IS NULL và is_deleted = false, sắp xếp name A-Z)
-> gom danh sách iconCode của các danh mục lấy được
-> iconService.findIconsByCodes(...)
   (lấy đường dẫn SVG path_data của toàn bộ biểu tượng trong một lần truy vấn)
-> cateMapper.toResponse(c, pathData)
   (ánh xạ sang CategoryRes, ẩn id nội bộ, parent_code = null)
-> trả về danh sách danh mục gốc
```

---

## Danh sách danh mục con — `GET /categories/{code}/children`

Endpoint công khai cho mọi người dùng đã xác thực (Bearer Token).

```text
getOfParent(code)
-> categoryRepository.findByCodeAndIsDeletedFalse(code)
   (nếu không tìm thấy hoặc đã bị xóa mềm -> ném CATEGORY_NOT_FOUND, HTTP 404)
-> categoryRepository.findByParent_IdAndIsDeletedFalseOrderByNameAsc(parent.id)
   (lấy các danh mục con trực tiếp chưa xóa, sắp xếp theo tên A-Z)
-> iconService.findIconsByCodes(...)
-> cateMapper.toResponse(...)
-> trả về danh sách danh mục con
```

---

## Tạo danh mục — `POST /categories` (`@Idempotent`)

Yêu cầu quyền hạn quản trị: `categories:update` (`PermissionName.CATEGORIES_UPDATE`).

```text
create
-> @Idempotent kiểm tra header Idempotency-Key
-> categoryRepository.existsByCode(req.code)
   -> đã tồn tại (kể cả đã xóa mềm) -> CATEGORY_CODE_EXISTS (HTTP 409)
-> iconService.findByCode(req.iconCode, false)
   -> không tồn tại hoặc đã bị tắt -> VALIDATION_ERROR ("Icon gửi lên không hợp lệ", HTTP 400)
-> req.parentCode có truyền hay không?
   -> có: findByCode(req.parentCode) (không thấy -> CATEGORY_NOT_FOUND, HTTP 404)
          parent = tìm thấy
          level = parent.level + 1
          path = parent.path + "/" + req.code
   -> không (danh mục gốc):
          parent = null
          level = 0
          path = req.code
-> categoryRepository.existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(req.name, tempId)
   -> trùng tên với danh mục khác đang hoạt động -> CATEGORY_NAME_EXISTS (HTTP 409)
-> nếu parent != null:
   -> parent.setIsLeaf(false) (cha có thêm con nên không còn là lá)
-> categoryRepository.save(category)
-> cateMapper.toResponse(category, icon.pathData)
-> trả về thông tin danh mục vừa tạo (HTTP 201 Created)
```

---

## Cập nhật danh mục — `PATCH /categories/{code}`

Yêu cầu quyền hạn quản trị: `categories:update` (`PermissionName.CATEGORIES_UPDATE`).  
Cho phép sửa thông tin hiển thị (tên, mô tả, màu, biểu tượng) và **di chuyển vị trí nhánh danh mục trong cây phân cấp**.

```text
update
-> category = findByCode(code) (không thấy -> CATEGORY_NOT_FOUND, HTTP 404)
-> req.parentCode != null:
   -> newParent = findByCode(req.parentCode) (không thấy -> CATEGORY_NOT_FOUND, HTTP 404)
   -> kiểm tra xem có đổi cha hay giữ nguyên cha:
      -> nếu đổi cha:
         -> kiểm tra vòng lặp:
            (newParent.id == category.id HOẶC newParent.path bắt đầu bằng category.path + "/")
            -> vi phạm chu trình -> CATEGORY_PARENT_INVALID (HTTP 400)
         -> moveUnder(category, newParent):
            -> oldParent = category.parent
            -> oldPath = category.path
            -> oldLevel = category.level
            -> descendants = categoryRepository.findDescendants(oldPath)
               (lấy toàn bộ cây con cháu bằng SQL: left(c.path, length(oldPath)+1) = concat(oldPath, '/'))
            -> cập nhật category:
               category.parent = newParent
               category.level = newParent.level + 1
               category.path = newParent.path + "/" + category.code
            -> levelShift = category.level - oldLevel
            -> lặp qua từng descendant trong descendants:
               descendant.path = category.path + descendant.path.substring(oldPath.length)
               descendant.level = descendant.level + levelShift
            -> newParent.setIsLeaf(false)
            -> nếu oldParent != null:
               kiểm tra oldParent còn con nào chưa xóa không:
               oldParent.setIsLeaf(!categoryRepository.existsByParent_IdAndIsDeletedFalse(oldParent.id))
-> req.iconCode != null:
   -> kiểm tra iconService.findByCode(req.iconCode, false) -> không hợp lệ -> VALIDATION_ERROR (HTTP 400)
   -> category.setIconCode(req.iconCode)
-> req.name != null:
   -> kiểm tra trùng tên: existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(req.name, category.id)
      -> trùng -> CATEGORY_NAME_EXISTS (HTTP 409)
   -> category.setName(req.name)
-> req.description != null -> category.setDescription(req.description)
-> req.color != null -> category.setColor(req.color)
-> trả về CategoryRes sau khi cập nhật (HTTP 200 OK)
```

---

## Xóa danh mục — `DELETE /categories/{code}`

Yêu cầu quyền hạn quản trị: `categories:delete` (`PermissionName.CATEGORIES_DELETE`).  
Áp dụng cơ chế **xóa mềm cascade (soft delete cascade)** cho toàn bộ cây con cháu hậu duệ.

```text
delete
-> category = findByCode(code) (không thấy hoặc đã bị xóa -> CATEGORY_NOT_FOUND, HTTP 404)
-> category.setIsDeleted(true)
-> categoryRepository.findDescendants(category.path)
   (tìm tất cả danh mục con, cháu, chắt... theo tiền tố path)
-> duyệt từng descendant -> descendant.setIsDeleted(true)
-> parent = category.parent
-> nếu parent != null:
   -> kiểm tra parent còn con trực tiếp nào chưa xóa không:
      hasChildren = categoryRepository.existsByParent_IdAndIsDeletedFalse(parent.id)
   -> parent.setIsLeaf(!hasChildren)
      (nếu tất cả các con của parent đều đã bị xóa -> parent tự động thành nút lá trở lại)
-> trả về ApiResponse(null) (HTTP 200 OK)
```

---

## Hàm cho module khác gọi (`CategoryService`)

Module giao dịch cá nhân (`transaction`), giao dịch định kỳ (`recurring`), ngân sách (`budget`), nhóm (`group`)... khi cần xác thực hoặc lấy thông tin danh mục sẽ gọi qua interface `CategoryService` ([CateImpl](../../src/main/java/com/datn/financeapp/module/category/service/impl/CateImpl.java)):

| Hàm | Ai gọi | Mục đích & Quy tắc |
|---|---|---|
| `findByCodeAndIsDeletedAndIsLeaf(code, includeDeleted, isLeaf)` | `TxnBehavierImpl`, `RecTxnBehavierImpl`, `BudgetBehavierImpl` | Kiểm tra tính hợp lệ và lấy tóm tắt danh mục (`CateSumaryRes`):<br>- `code` rỗng/null: trả về `Optional.empty()`<br>- Không tìm thấy mã: trả về `Optional.empty()`<br>- `includeDeleted = false` mà bản ghi có `is_deleted = true`: trả về `Optional.empty()`<br>- `isLeaf = true` mà bản ghi có `is_leaf = false`: trả về `Optional.empty()` (chỉ cho phép gắn vào danh mục lá)<br>- Hợp lệ: trả về `CateSumaryRes` kèm icon SVG data. |

---

## Lưu ý tích hợp & Quy tắc bất biến

1. **Không xóa cứng trong CSDL:** Bảng `categories` không bao giờ chạy lệnh `DELETE`. Việc xóa chỉ cập nhật cờ `is_deleted = true`. Điều này đảm bảo toàn vẹn dữ liệu cho mọi giao dịch lịch sử đã gắn mã `category_code`.
2. **Mã `code` giữ chỗ vĩnh viễn:** Ràng buộc `UNIQUE (code)` trên toàn bảng ngăn việc tái sử dụng `code` đã xóa, tránh việc dữ liệu giao dịch cũ bị hiểu nhầm sang danh mục mới.
3. **Giao dịch chỉ gắn vào danh mục lá:** Cả Java (`findByCodeAndIsDeletedAndIsLeaf(..., isLeaf=true)`) lẫn Trigger CSDL (`trg_transactions_category_leaf`) đều kiểm tra nghiêm ngặt `is_leaf = true`. Nếu danh mục cha có con, người dùng không thể tạo giao dịch gắn trực tiếp vào danh mục cha đó.
