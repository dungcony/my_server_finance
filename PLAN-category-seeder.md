# Plan: Tạo CategorySeeder trong package category

## 1. File sẽ thay đổi

| File | Hành động |
|---|---|
| `src/main/java/com/datn/financeapp/category/CategorySeeder.java` | **TẠO MỚI** |

## 2. Lý do thay đổi

- Cần seed dữ liệu category (icons, category_groups, categories) bằng Java/JPA, theo pattern `AppRunner` trong package `user/`.
- Hiện tại V5 migration đã seed bằng SQL — seeder này sẽ chạy bổ sung ở profile `dev`, dùng `ON CONFLICT DO NOTHING` / check tồn tại để chạy lại được.

## 3. Hướng giải quyết

- Tạo class `CategorySeeder` implements `ApplicationRunner`, `@Profile("dev")`, `@Component`
- Dùng `JdbcTemplate` (giống `DevDataSeeder`) vì logic seed cần `ON CONFLICT DO NOTHING` mà JPA không hỗ trợ native
- Seed 3 lớp dữ liệu theo đúng thứ tự V5:
  1. **Icons** — 32 biểu tượng
  2. **Category Groups** — 5 nhóm lớn
  3. **Categories cấp cha** — 16 danh mục (10 chi + 6 thu)
  4. **Categories cấp con** — 25 danh mục con mẫu

### Câu hỏi cần xác nhận:

1. **Dùng JdbcTemplate hay JPA Repository?** 
   - JdbcTemplate: gọn, dùng được `ON CONFLICT DO NOTHING` giống V5, chạy lại không lỗi
   - JPA Repository: phải check `existsBy...` từng dòng, code dài hơn nhiều
   - **Đề xuất:** JdbcTemplate (giống DevDataSeeder đang làm)

2. **Dữ liệu seed giống hệt V5 hay khác?** V5 đã chạy rồi ở migration, seeder này có cần thêm dữ liệu mới không hay chỉ đảm bảo dữ liệu V5 luôn tồn tại?

3. **Có cần tách riêng hay gộp vào DevDataSeeder hiện có?** AppRunner nằm trong package `user/`, nhưng DevDataSeeder nằm ở `common/seed/`. Đặt ở đâu?
