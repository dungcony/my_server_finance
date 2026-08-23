---
phase: 02-vi-danh-muc
plan: 03
subsystem: category
tags: [spring-boot, jpa, postgresql, testcontainers, rest-api]

# Dependency graph
requires:
  - phase: 02-vi-danh-muc (plan 02-01)
    provides: "Không phụ thuộc trực tiếp code — chỉ dùng chung pattern entity/repository đã xác lập"
provides:
  - "category/entity/Category.java, CategoryGroup.java, Icon.java — khớp schema V1 (categories, category_groups, icons)"
  - "category/repository/CategoryRepository.java — bọc fn_category_tree thành 1 method dùng chung, tái sử dụng ở Phase 3/4"
  - "category/service/CategoryService.java — CRUD hai cấp + validate 5 ràng buộc api/03-DANH-MUC.md mục 3"
  - "category/controller/CategoryController.java — 8 endpoint REST danh mục/nhóm lớn/kho icon"
affects: [02-04-chuyen-tien-doi-chieu, 03-giao-dich, 04-ngan-sach-bao-cao]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "fn_category_tree (V6__va_loi_bao_mat.sql) bọc thành CategoryRepository.findCategoryTree — Phase 3/4 dùng lại nguyên vẹn, không tự viết điều kiện lọc cây khác"
    - "Danh mục KHÔNG áp dụng vế nhóm của D-27 — categories.user_id không liên kết group_id theo schema V1; danh mục hệ thống (user_id IS NULL) luôn hiển thị cho mọi người theo api/03-DANH-MUC.md mục 9"
    - "UpdateCategoryRequest dùng @JsonAnySetter gom field lạ (kể cả type) vào extraFields để Service phát hiện tường minh, cùng pattern UpdateWalletRequest"
    - "Validate Service làm lớp phòng thủ đầu (5 ràng buộc mục 3 + quy tắc sửa/xoá mục 4-5), trigger DB fn_categories_validate/fn_categories_block_demote làm lớp phòng thủ cuối — Service có catch fallback DataIntegrityViolationException map sang BusinessException"

key-files:
  created:
    - src/main/java/com/datn/financeapp/category/entity/Category.java
    - src/main/java/com/datn/financeapp/category/entity/CategoryGroup.java
    - src/main/java/com/datn/financeapp/category/entity/Icon.java
    - src/main/java/com/datn/financeapp/category/repository/CategoryRepository.java
    - src/main/java/com/datn/financeapp/category/repository/CategoryGroupRepository.java
    - src/main/java/com/datn/financeapp/category/repository/IconRepository.java
    - src/main/java/com/datn/financeapp/category/service/CategoryService.java
    - src/main/java/com/datn/financeapp/category/controller/CategoryController.java
    - src/main/java/com/datn/financeapp/category/dto/CreateCategoryRequest.java
    - src/main/java/com/datn/financeapp/category/dto/UpdateCategoryRequest.java
    - src/main/java/com/datn/financeapp/category/dto/CategoryResponse.java
    - src/main/java/com/datn/financeapp/category/dto/CategoryDetailResponse.java
    - src/main/java/com/datn/financeapp/category/dto/CategoryGroupResponse.java
    - src/main/java/com/datn/financeapp/category/dto/IconResponse.java
    - src/main/java/com/datn/financeapp/category/dto/IconGroupResponse.java
    - src/main/java/com/datn/financeapp/category/dto/ReorderCategoriesRequest.java
    - src/test/java/com/datn/financeapp/category/CategoryTreeIntegrationTest.java
    - src/test/java/com/datn/financeapp/category/CategoryAccessControlIntegrationTest.java
  modified:
    - src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java

key-decisions:
  - "Danh mục không áp dụng vế nhóm gia đình của D-27 — categories.user_id không liên kết group_id trong schema V1 thật, khác wallets. Danh mục hệ thống (user_id IS NULL) cố ý hiển thị cho mọi người theo api/03-DANH-MUC.md mục 9, không phải lỗ hổng quyền"
  - "has_budget trong stats luôn false ở Phase 2 — bảng budgets chưa có service, ghi rõ TODO Phase 4 thay vì tự bịa logic kiểm tra"
  - "CATEGORY_HAS_BUDGET (mục 5 quy tắc 4) bỏ qua ở Phase 2 cùng lý do trên — chỉ 3/4 quy tắc xoá được kiểm tra thật"

requirements-completed: [CAT-01, CAT-02, CAT-03, CAT-04, CAT-05, CAT-06]

# Metrics
duration: 35min
completed: 2026-08-23
---

# Phase 2 Plan 3: CRUD danh mục hai cấp (CAT-01..06) Summary

**CRUD danh mục hai cấp gắn biểu tượng với 5 ràng buộc tạo + quy tắc sửa/xoá theo api/03-DANH-MUC.md, fn_category_tree bọc thành 1 repository method dùng chung cho Phase 3/4, 12 test tích hợp qua Testcontainers PostgreSQL thật (cây 2 cấp D-28 mục 4 + quyền truy cập 404).**

## Performance

- **Duration:** 35 min
- **Started:** 2026-08-23T10:13:00Z
- **Completed:** 2026-08-23T10:21:39Z
- **Tasks:** 2
- **Files modified:** 19 (18 tạo mới, 1 sửa)

## Accomplishments
- `Category`/`CategoryGroup`/`Icon` entity khớp đúng schema `categories`/`category_groups`/`icons` (V1__nen_tang.sql)
- `CategoryRepository.findCategoryTree` bọc `fn_category_tree` (V6) thành 1 method — sẵn sàng cho Phase 3 (cộng gộp danh mục con) và Phase 4 (ngân sách/báo cáo) dùng lại, không lặp điều kiện lọc cây
- `CategoryService` triển khai đủ list (cây/phẳng)/detail/create/update/delete/reorder/listCategoryGroups/listIcons — validate đúng thứ tự 5 ràng buộc tạo (MAX_DEPTH_EXCEEDED, TYPE_MISMATCH_WITH_PARENT, CATEGORY_GROUP_REQUIRED, CATEGORY_NAME_EXISTS, INVALID_ICON) + quy tắc sửa/xoá (SYSTEM_CATEGORY_NOT_EDITABLE/NOT_DELETABLE, CATEGORY_HAS_CHILDREN, CHILD_CATEGORIES_EXIST, CATEGORY_HAS_TRANSACTIONS)
- `CategoryController` expose 8 endpoint, chỉ `POST /categories` gắn `@Idempotent` đúng D-11
- 12 test tích hợp PASS qua Testcontainers PostgreSQL thật: 6 test hành vi Task 1 + 2 test bổ sung (cây gồm cả hệ thống/user, kế thừa category_group_id) trong `CategoryTreeIntegrationTest`, 4 test quyền truy cập 404 trong `CategoryAccessControlIntegrationTest`
- Toàn bộ 58 test của project (46 trước đó + 12 mới) PASS — không có regression

## Task Commits

Each task was committed atomically:

1. **Task 1: Category/CategoryGroup/Icon entity + repository + CategoryService** - `bfb4883` (feat)
2. **Task 2: CategoryController + test cây 2 cấp + test quyền truy cập** - `65d84be` (feat)

**Plan metadata:** (commit tiếp theo sau summary này)

## Files Created/Modified
- `src/main/java/com/datn/financeapp/category/entity/Category.java` - Entity bảng `categories`, hai cấp
- `src/main/java/com/datn/financeapp/category/entity/CategoryGroup.java` - Entity bảng `category_groups`
- `src/main/java/com/datn/financeapp/category/entity/Icon.java` - Entity bảng `icons`
- `src/main/java/com/datn/financeapp/category/repository/CategoryRepository.java` - fn_category_tree + quyền + CRUD hỗ trợ
- `src/main/java/com/datn/financeapp/category/repository/CategoryGroupRepository.java` - Danh sách nhóm lớn cố định
- `src/main/java/com/datn/financeapp/category/repository/IconRepository.java` - Lọc icon theo nhóm/tìm kiếm
- `src/main/java/com/datn/financeapp/category/service/CategoryService.java` - Business logic 8 nghiệp vụ danh mục
- `src/main/java/com/datn/financeapp/category/controller/CategoryController.java` - 8 endpoint REST
- `src/main/java/com/datn/financeapp/category/dto/*.java` (8 file) - Request/Response DTO
- `src/test/java/com/datn/financeapp/category/CategoryTreeIntegrationTest.java` - 8 test cây 2 cấp
- `src/test/java/com/datn/financeapp/category/CategoryAccessControlIntegrationTest.java` - 4 test quyền truy cập 404
- `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java` - Loại trừ CategoryController khỏi @WebMvcTest slice

## Decisions Made
- Danh mục KHÔNG áp dụng vế nhóm gia đình của D-27 (khác `WalletRepository`) — `categories.user_id` không liên kết `group_id` trong schema V1 thật. Danh mục hệ thống (`user_id IS NULL`) cố ý hiển thị cho mọi người đã đăng nhập theo `api/03-DANH-MUC.md` mục 9, không phải lỗ hổng quyền cần vá.
- `has_budget` trong `CategoryDetailResponse.stats` luôn `false` ở Phase 2 vì bảng `budgets` chưa có service — ghi rõ comment TODO cho Phase 4 thay vì tự bịa kiểm tra rỗng.
- `CATEGORY_HAS_BUDGET` (quy tắc xoá thứ 4 của mục 5) bỏ qua ở Phase 2 cùng lý do trên — chỉ 3/4 quy tắc xoá (hệ thống, còn con, còn giao dịch) được kiểm tra thật ở plan này.

## Deviations from Plan

Không có sai lệch nào cần user quyết định (Rule 4). Toàn bộ implement đúng theo `<action>` của PLAN và đối chiếu thành công với schema thật (`db/migration/V1__nen_tang.sql`, `V6__va_loi_bao_mat.sql`) — không lặp lại lỗi `group_members.status` đã phát hiện ở plan 02-02 vì Category không dùng vế nhóm đó.

Không có auto-fix Rule 1-3 phát sinh trong quá trình chạy test — build và test PASS ngay ở lần chạy đầu tiên sau khi biên dịch sạch.

## Issues Encountered

Không có vấn đề nào. Toàn bộ 12 test tích hợp mới PASS ngay lần chạy đầu, cùng với 46 test cũ (Phase 1 + plan 02-01/02-02) không bị regression.

## User Setup Required

None - không có cấu hình dịch vụ ngoài nào cần thiết lập thủ công.

## Next Phase Readiness

- `CategoryService`/`CategoryController` sẵn sàng cho plan 02-04 (không phụ thuộc trực tiếp — plan Ví) và Phase 3 (giao dịch) gán `category_id` vào transaction.
- `CategoryRepository.findCategoryTree` sẵn sàng dùng lại nguyên vẹn ở Phase 3 (cộng gộp danh mục con vào cha khi lọc/thống kê giao dịch) và Phase 4 (ngân sách theo danh mục cha, báo cáo theo nhóm lớn) — không cần viết lại điều kiện lọc cây.
- **Lưu ý cho Phase 4:** `has_budget`/`CATEGORY_HAS_BUDGET` chưa nối thật ở Phase 2, cần cập nhật `CategoryService.detail()` và `CategoryService.delete()` khi module `budgets` có service.
- Không có blocker.

---
*Phase: 02-vi-danh-muc*
*Completed: 2026-08-23*

## Self-Check: PASSED

- FOUND: src/main/java/com/datn/financeapp/category/entity/Category.java
- FOUND: src/main/java/com/datn/financeapp/category/entity/CategoryGroup.java
- FOUND: src/main/java/com/datn/financeapp/category/entity/Icon.java
- FOUND: src/main/java/com/datn/financeapp/category/repository/CategoryRepository.java
- FOUND: src/main/java/com/datn/financeapp/category/repository/CategoryGroupRepository.java
- FOUND: src/main/java/com/datn/financeapp/category/repository/IconRepository.java
- FOUND: src/main/java/com/datn/financeapp/category/service/CategoryService.java
- FOUND: src/main/java/com/datn/financeapp/category/controller/CategoryController.java
- FOUND: src/main/java/com/datn/financeapp/category/dto/CreateCategoryRequest.java
- FOUND: src/main/java/com/datn/financeapp/category/dto/UpdateCategoryRequest.java
- FOUND: src/test/java/com/datn/financeapp/category/CategoryTreeIntegrationTest.java
- FOUND: src/test/java/com/datn/financeapp/category/CategoryAccessControlIntegrationTest.java
- FOUND: commit bfb4883
- FOUND: commit 65d84be
