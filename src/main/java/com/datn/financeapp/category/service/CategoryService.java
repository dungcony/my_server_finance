package com.datn.financeapp.category.service;

import com.datn.financeapp.category.dto.CategoryDetailResponse;
import com.datn.financeapp.category.dto.CategoryGroupResponse;
import com.datn.financeapp.category.dto.CategoryResponse;
import com.datn.financeapp.category.dto.CreateCategoryRequest;
import com.datn.financeapp.category.dto.IconGroupResponse;
import com.datn.financeapp.category.dto.IconResponse;
import com.datn.financeapp.category.dto.ReorderCategoriesRequest;
import com.datn.financeapp.category.dto.UpdateCategoryRequest;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.entity.CategoryGroup;
import com.datn.financeapp.category.entity.Icon;
import com.datn.financeapp.category.repository.CategoryGroupRepository;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.category.repository.IconRepository;
import com.datn.financeapp.common.exception.BusinessException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic CRUD danh mục hai cấp (CAT-01..06, api/03-DANH-MUC.md mục 1-8). Validate 5 ràng
 * buộc ở mục 3 làm lớp phòng thủ đầu; trigger DB {@code fn_categories_validate}/
 * {@code fn_categories_block_demote} là lớp phòng thủ cuối cùng nếu Service bị lách (race
 * condition hiếm) — Service vẫn catch fallback exception CSDL để không lộ lỗi CHECK thô.
 *
 * {@code @Transactional} đặt TRÊN TỪNG PUBLIC METHOD theo mẫu {@code WalletService}.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    /** 8 nhóm biểu tượng cố định — ck_icons_group ở db/migration/V1__nen_tang.sql. */
    private static final Map<String, String> ICON_GROUP_NAMES = Map.ofEntries(
            Map.entry("an_uong", "Ăn uống"),
            Map.entry("di_lai", "Đi lại"),
            Map.entry("mua_sam", "Mua sắm"),
            Map.entry("giai_tri", "Giải trí"),
            Map.entry("suc_khoe", "Sức khoẻ"),
            Map.entry("hoc_tap", "Học tập"),
            Map.entry("tai_chinh", "Tài chính"),
            Map.entry("khac", "Khác"));

    private final CategoryRepository categoryRepository;
    private final CategoryGroupRepository categoryGroupRepository;
    private final IconRepository iconRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, String type, boolean asTree, boolean rootsOnly) {
        List<Category> categories = categoryRepository.findAllForUser(userId, type, rootsOnly);

        Map<UUID, CategoryGroup> groupsById = loadGroupsById();
        Map<UUID, Icon> iconsById = loadIconsById();

        List<CategoryResponse> flat = categories.stream()
                .map(c -> toResponse(c, groupsById.get(c.getCategoryGroupId()), iconsById.get(c.getIconId()), null))
                .toList();

        if (!asTree) {
            return flat;
        }

        Map<UUID, List<CategoryResponse>> childrenByParent = flat.stream()
                .filter(c -> c.parentCategoryId() != null)
                .collect(Collectors.groupingBy(CategoryResponse::parentCategoryId, LinkedHashMap::new, Collectors.toList()));

        return flat.stream()
                .filter(c -> c.parentCategoryId() == null)
                .map(c -> withChildren(c, childrenByParent.getOrDefault(c.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryDetailResponse detail(UUID userId, UUID categoryId) {
        Category category = categoryRepository
                .findByIdAndVisibleToUser(categoryId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục."));

        Map<UUID, CategoryGroup> groupsById = loadGroupsById();
        Map<UUID, Icon> iconsById = loadIconsById();
        CategoryResponse base =
                toResponse(category, groupsById.get(category.getCategoryGroupId()), iconsById.get(category.getIconId()), null);

        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE category_id = ? AND NOT is_deleted",
                Long.class, categoryId);
        Long expenseThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE category_id = ? AND type = 'expense' "
                        + "AND NOT is_deleted AND date_trunc('month', date) = date_trunc('month', CURRENT_DATE)",
                Long.class, categoryId);

        // has_budget: Phase 2 chưa có service budgets, luôn false — TODO Phase 4 nối thật.
        CategoryDetailResponse.Stats stats = new CategoryDetailResponse.Stats(
                transactionCount == null ? 0 : transactionCount,
                expenseThisMonth == null ? 0 : expenseThisMonth,
                false);

        return new CategoryDetailResponse(
                base.id(), base.name(), base.type(), base.isSystem(), base.parentCategoryId(),
                base.categoryGroup(), base.icon(), base.color(), base.sortOrder(), base.createdAt(), stats);
    }

    @Transactional
    public CategoryResponse create(UUID userId, CreateCategoryRequest req) {
        Category parent = null;
        UUID categoryGroupId;

        if (req.parentCategoryId() != null) {
            parent = categoryRepository
                    .findByIdAndVisibleToUser(req.parentCategoryId(), userId)
                    .orElseThrow(() -> new BusinessException(
                            "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục cha."));

            // Ràng buộc 1: cha được chọn phải có parent_category_id rỗng (chỉ hai tầng).
            if (parent.getParentCategoryId() != null) {
                throw new BusinessException(
                        "MAX_DEPTH_EXCEEDED", HttpStatus.BAD_REQUEST.value(), "Danh mục chỉ được tối đa hai cấp.");
            }
            // Ràng buộc 2: con phải cùng type với cha.
            if (!parent.getType().equals(req.type())) {
                throw new BusinessException(
                        "TYPE_MISMATCH_WITH_PARENT",
                        HttpStatus.BAD_REQUEST.value(),
                        "Danh mục con phải cùng loại thu/chi với cha.");
            }
            // Con kế thừa category_group_id từ cha, dù trigger DB cũng tự làm — set tường minh
            // để response trả đúng ngay không cần load lại.
            categoryGroupId = parent.getCategoryGroupId();
        } else {
            // Ràng buộc 3: danh mục cha bắt buộc có category_group_id.
            if (req.categoryGroupId() == null) {
                throw new BusinessException(
                        "CATEGORY_GROUP_REQUIRED",
                        HttpStatus.BAD_REQUEST.value(),
                        "Danh mục cha bắt buộc có nhóm lớn.");
            }
            categoryGroupId = req.categoryGroupId();
        }

        // Ràng buộc 4: tên không trùng trong cùng cấp, cùng loại, cùng chủ sở hữu.
        if (categoryRepository.existsSiblingWithName(userId, req.parentCategoryId(), req.type(), req.name(), null)) {
            throw new BusinessException(
                    "CATEGORY_NAME_EXISTS", HttpStatus.CONFLICT.value(), "Đã có danh mục cùng tên ở cấp này.");
        }

        // Ràng buộc 5: icon_id phải tồn tại và is_active = true.
        Icon icon = iconRepository
                .findById(req.iconId())
                .filter(i -> Boolean.TRUE.equals(i.getIsActive()))
                .orElseThrow(() -> new BusinessException(
                        "INVALID_ICON", HttpStatus.BAD_REQUEST.value(), "Biểu tượng không tồn tại hoặc đã ngừng dùng."));

        Integer maxSortOrder = categoryRepository.findMaxSortOrderInLevel(userId, req.parentCategoryId());
        Instant now = Instant.now();

        Category category = Category.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .parentCategoryId(req.parentCategoryId())
                .categoryGroupId(categoryGroupId)
                .name(req.name())
                .type(req.type())
                .iconId(req.iconId())
                .color(req.color())
                .sortOrder((maxSortOrder == null ? -1 : maxSortOrder) + 1)
                .isDeleted(false)
                .createdAt(now)
                .build();

        try {
            categoryRepository.save(category);
        } catch (DataIntegrityViolationException ex) {
            // Fallback nếu Service check bị lách (race condition hiếm) — trigger DB ném
            // check_violation, map lại thành lỗi nghiệp vụ chung thay vì lộ lỗi CSDL thô.
            throw mapDatabaseCheckViolation(ex);
        }

        CategoryGroup group = parent != null
                ? loadGroupsById().get(categoryGroupId)
                : categoryGroupRepository.findById(categoryGroupId).orElse(null);

        return toResponse(category, group, icon, null);
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID categoryId, UpdateCategoryRequest req) {
        if (req.extraFields().containsKey("type")) {
            throw new BusinessException(
                    "TYPE_NOT_EDITABLE", HttpStatus.BAD_REQUEST.value(), "Không đổi được loại thu/chi.");
        }

        Category category = categoryRepository
                .findById(categoryId)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục."));

        if (category.getUserId() == null) {
            throw new BusinessException(
                    "SYSTEM_CATEGORY_NOT_EDITABLE",
                    HttpStatus.FORBIDDEN.value(),
                    "Danh mục hệ thống không sửa được.");
        }
        if (!category.getUserId().equals(userId)) {
            // Không lộ tồn tại — trả 404 như bản ghi không có (T-02-09).
            throw new BusinessException(
                    "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục.");
        }

        if (req.isParentCategoryIdSet()) {
            UUID newParentId = req.parentCategoryId();
            // Danh mục đang có con thì không được chuyển thành con của danh mục khác.
            if (categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(categoryId)) {
                throw new BusinessException(
                        "CATEGORY_HAS_CHILDREN",
                        HttpStatus.CONFLICT.value(),
                        "Danh mục đang có con, không thể chuyển thành cấp con.");
            }
            if (newParentId != null) {
                Category newParent = categoryRepository
                        .findById(newParentId)
                        .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                        .orElseThrow(() -> new BusinessException(
                                "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục cha."));
                if (newParent.getParentCategoryId() != null) {
                    throw new BusinessException(
                            "MAX_DEPTH_EXCEEDED",
                            HttpStatus.BAD_REQUEST.value(),
                            "Danh mục chỉ được tối đa hai cấp.");
                }
                if (!newParent.getType().equals(category.getType())) {
                    throw new BusinessException(
                            "TYPE_MISMATCH_WITH_PARENT",
                            HttpStatus.BAD_REQUEST.value(),
                            "Danh mục con phải cùng loại thu/chi với cha.");
                }
                category.setParentCategoryId(newParentId);
                category.setCategoryGroupId(newParent.getCategoryGroupId());
            } else {
                category.setParentCategoryId(null);
                if (req.categoryGroupId() != null) {
                    category.setCategoryGroupId(req.categoryGroupId());
                } else if (category.getCategoryGroupId() == null) {
                    throw new BusinessException(
                            "CATEGORY_GROUP_REQUIRED",
                            HttpStatus.BAD_REQUEST.value(),
                            "Danh mục cha bắt buộc có nhóm lớn.");
                }
            }
        } else if (req.categoryGroupId() != null && category.getParentCategoryId() == null) {
            category.setCategoryGroupId(req.categoryGroupId());
        }

        if (req.name() != null) {
            UUID excludeId = category.getId();
            if (categoryRepository.existsSiblingWithName(
                    userId, category.getParentCategoryId(), category.getType(), req.name(), excludeId)) {
                throw new BusinessException(
                        "CATEGORY_NAME_EXISTS", HttpStatus.CONFLICT.value(), "Đã có danh mục cùng tên ở cấp này.");
            }
            category.setName(req.name());
        }
        if (req.iconId() != null) {
            Icon icon = iconRepository
                    .findById(req.iconId())
                    .filter(i -> Boolean.TRUE.equals(i.getIsActive()))
                    .orElseThrow(() -> new BusinessException(
                            "INVALID_ICON", HttpStatus.BAD_REQUEST.value(), "Biểu tượng không tồn tại hoặc đã ngừng dùng."));
            category.setIconId(icon.getId());
        }
        if (req.color() != null) {
            category.setColor(req.color());
        }

        try {
            categoryRepository.save(category);
        } catch (DataIntegrityViolationException ex) {
            throw mapDatabaseCheckViolation(ex);
        }

        Map<UUID, CategoryGroup> groupsById = loadGroupsById();
        Map<UUID, Icon> iconsById = loadIconsById();
        return toResponse(category, groupsById.get(category.getCategoryGroupId()), iconsById.get(category.getIconId()), null);
    }

    @Transactional
    public void delete(UUID userId, UUID categoryId, UUID replacementCategoryId) {
        Category category = categoryRepository
                .findById(categoryId)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục."));

        if (category.getUserId() == null) {
            throw new BusinessException(
                    "SYSTEM_CATEGORY_NOT_DELETABLE",
                    HttpStatus.FORBIDDEN.value(),
                    "Danh mục hệ thống không xoá được.");
        }
        if (!category.getUserId().equals(userId)) {
            throw new BusinessException(
                    "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục.");
        }

        if (categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(categoryId)) {
            throw new BusinessException(
                    "CHILD_CATEGORIES_EXIST",
                    HttpStatus.CONFLICT.value(),
                    "Còn danh mục con, phải xoá con trước.");
        }

        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE category_id = ? AND NOT is_deleted",
                Long.class, categoryId);
        boolean hasTransactions = transactionCount != null && transactionCount > 0;

        if (hasTransactions) {
            if (replacementCategoryId == null) {
                throw new BusinessException(
                        "CATEGORY_HAS_TRANSACTIONS",
                        HttpStatus.CONFLICT.value(),
                        "Còn giao dịch, cần chỉ định danh mục thay thế.");
            }
            Category replacement = categoryRepository
                    .findByIdAndVisibleToUser(replacementCategoryId, userId)
                    .orElseThrow(() -> new BusinessException(
                            "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục thay thế."));
            if (!replacement.getType().equals(category.getType())) {
                throw new BusinessException(
                        "TYPE_MISMATCH_WITH_PARENT",
                        HttpStatus.BAD_REQUEST.value(),
                        "Danh mục thay thế phải cùng loại thu/chi.");
            }
            jdbcTemplate.update(
                    "UPDATE transactions SET category_id = ? WHERE category_id = ?",
                    replacementCategoryId, categoryId);
        }

        // CATEGORY_HAS_BUDGET: Phase 2 bỏ qua vì bảng budgets chưa có service — TODO Phase 4.

        category.setIsDeleted(true);
        categoryRepository.save(category);
    }

    @Transactional
    public void reorder(UUID userId, ReorderCategoriesRequest req) {
        List<UUID> ids = req.sortOrder();
        for (int i = 0; i < ids.size(); i++) {
            categoryRepository.updateSortOrder(ids.get(i), i, userId, req.parentCategoryId());
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryGroupResponse> listCategoryGroups() {
        Map<UUID, Icon> iconsById = loadIconsById();
        return categoryGroupRepository.findAllByOrderBySortOrderAsc().stream()
                .map(g -> new CategoryGroupResponse(
                        g.getId(), g.getName(), g.getColor(), toIconSummary(iconsById.get(g.getIconId())), g.getSortOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public IconGroupResponseWrapper listIcons(String iconGroup, String search) {
        String searchPattern = search == null || search.isBlank() ? null : "%" + search.toLowerCase() + "%";
        List<Icon> icons = iconRepository.search(iconGroup, searchPattern);

        Map<String, List<Icon>> byGroup = icons.stream()
                .collect(Collectors.groupingBy(Icon::getIconGroup, LinkedHashMap::new, Collectors.toList()));

        List<IconGroupResponse> groups = byGroup.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> new IconGroupResponse(
                        entry.getKey(),
                        ICON_GROUP_NAMES.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue().stream()
                                .map(i -> new IconResponse(i.getId(), i.getCode(), i.getDisplayName(), i.getPathData()))
                                .toList()))
                .toList();

        return new IconGroupResponseWrapper(groups, icons.size());
    }

    /** Wrapper cho phản hồi {@code {group: [...], total: N}} của GET /icons. */
    public record IconGroupResponseWrapper(List<IconGroupResponse> group, int total) {}

    private CategoryResponse withChildren(CategoryResponse parent, List<CategoryResponse> children) {
        List<CategoryResponse> sortedChildren =
                children.stream().sorted(Comparator.comparingInt(CategoryResponse::sortOrder)).toList();
        return new CategoryResponse(
                parent.id(), parent.name(), parent.type(), parent.isSystem(), parent.parentCategoryId(),
                parent.categoryGroup(), parent.icon(), parent.color(), parent.sortOrder(), parent.createdAt(),
                sortedChildren);
    }

    private CategoryResponse toResponse(Category c, CategoryGroup group, Icon icon, List<CategoryResponse> children) {
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.getType(),
                c.getUserId() == null,
                c.getParentCategoryId(),
                group == null ? null : new CategoryResponse.CategoryGroupSummary(group.getId(), group.getName(), group.getColor()),
                toIconSummary(icon),
                c.getColor(),
                c.getSortOrder(),
                c.getCreatedAt(),
                children);
    }

    private CategoryResponse.IconSummary toIconSummary(Icon icon) {
        return icon == null ? null : new CategoryResponse.IconSummary(icon.getId(), icon.getCode(), icon.getPathData());
    }

    private Map<UUID, CategoryGroup> loadGroupsById() {
        return categoryGroupRepository.findAll().stream()
                .collect(Collectors.toMap(CategoryGroup::getId, g -> g));
    }

    private Map<UUID, Icon> loadIconsById() {
        return iconRepository.findAll().stream().collect(Collectors.toMap(Icon::getId, i -> i));
    }

    private BusinessException mapDatabaseCheckViolation(DataIntegrityViolationException ex) {
        String message = Optional.ofNullable(ex.getMostSpecificCause())
                .map(Throwable::getMessage)
                .orElse("");
        if (message.contains("MAX_DEPTH_EXCEEDED")) {
            return new BusinessException(
                    "MAX_DEPTH_EXCEEDED", HttpStatus.BAD_REQUEST.value(), "Danh mục chỉ được tối đa hai cấp.");
        }
        if (message.contains("TYPE_MISMATCH_WITH_PARENT")) {
            return new BusinessException(
                    "TYPE_MISMATCH_WITH_PARENT",
                    HttpStatus.BAD_REQUEST.value(),
                    "Danh mục con phải cùng loại thu/chi với cha.");
        }
        if (message.contains("CATEGORY_HAS_CHILDREN")) {
            return new BusinessException(
                    "CATEGORY_HAS_CHILDREN",
                    HttpStatus.CONFLICT.value(),
                    "Danh mục đang có con, không thể chuyển thành cấp con.");
        }
        return new BusinessException(
                "VALIDATION_FAILED", HttpStatus.BAD_REQUEST.value(), "Dữ liệu danh mục không hợp lệ.");
    }
}
