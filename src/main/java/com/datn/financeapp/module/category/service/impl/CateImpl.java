package com.datn.financeapp.module.category.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryResponse;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.entity.Icon;
import com.datn.financeapp.module.category.helper.CateCache;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.category.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Triển khai {@link CategoryService} trên cây danh mục nhiều cấp.
 * <p>
 * Mỗi nút giữ {@code level} (độ sâu, gốc = 0), {@code path} ({@code code} từ gốc tới nó nối bằng
 * {@code /}) và {@code isLeaf} (còn con hay không); ba giá trị này do service duy trì.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CateImpl implements CategoryService {

    private static final String PATH_SEPARATOR = "/";

    private final CategoryRepository categoryRepository;
    private final IconService iconService;
    private final CateMapper cateMapper;
    private final CateCache cateCache;

    @Override
    @Transactional
    public CategoryResponse create(CategoryCreateReq req) {
        if (cateCache.existsCode(req.code()))
            throw new BusinessException(ErrorCode.CATEGORY_CODE_EXISTS);

        Icon icon = iconService.getActive(req.iconCode());
        Category parent = req.parentCode() != null ? findByCode(req.parentCode()) : null;
        UUID id = UUID.randomUUID();

        if (categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(req.name(), id))
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);

        Category category = Category.builder()
                .id(id)
                .code(req.code())
                .name(req.name())
                .description(req.description())
                .parent(parent)
                .icon(icon)
                .color(req.color())
                .level(levelUnder(parent))
                .path(pathUnder(parent, req.code()))
                .createdAt(Instant.now())
                .build();

        if (parent != null)
            parent.setIsLeaf(false);

        categoryRepository.save(category);
        registerCodeAfterCommit(id, req.code());
        return cateMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getMasters() {
        return categoryRepository.findByParentIsNullAndDeletedFalseOrderByNameAsc().stream()
                .map(cateMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getOfParent(String parentCode) {
        Category parent = findByCode(parentCode);
        return categoryRepository.findByParent_IdAndDeletedFalseOrderByNameAsc(parent.getId()).stream()
                .map(cateMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse update(String cateCode, CategoryUpdateReq req) {
        Category category = findByCode(cateCode);
        Category parent = findByCode(req.parentCode());

        boolean isSameParent = category.getParent() != null
                && Objects.equals(category.getParent().getId(), parent.getId());

        if (!isSameParent) {
            if (Objects.equals(category.getId(), parent.getId()) || parent.getPath().startsWith(category.getPath() + PATH_SEPARATOR))
                throw new BusinessException(ErrorCode.CATEGORY_PARENT_INVALID);
            moveUnder(category, parent);
        }


        if (req.name() != null
                && categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(req.name(), category.getId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }
        if (req.iconCode() != null) {
            category.setIcon(iconService.getActive(req.iconCode()));
        }
        if (req.name() != null) {
            category.setName(req.name());
        }
        if (req.description() != null) {
            category.setDescription(req.description());
        }
        if (req.color() != null) {
            category.setColor(req.color());
        }
        return cateMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void delete(String cateCode) {
        Category category = findByCode(cateCode);
        category.setDeleted(true);
        // xóa các cate con
        categoryRepository.findDescendants(category.getPath()).forEach(descendant -> descendant.setDeleted(true));
        Category parent = category.getParent();
        
        if (parent != null)
            parent.setIsLeaf(!categoryRepository.existsByParent_IdAndDeletedFalse(parent.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findActiveById(UUID id) {
        return categoryRepository
                .findByIdAndDeletedFalse(id)
                .map(cateMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CategoryResponse> findAllByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Category::getId, cateMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findSelfAndAncestorIds(UUID id) {
        // Đọc cả danh mục đã xóa mềm: giao dịch cũ vẫn trỏ vào danh mục đó và ngân sách ở tổ tiên vẫn phải cộng gộp.
        return categoryRepository.findById(id)
                .map(category -> {
                    List<String> codes = List.of(category.getPath().split(PATH_SEPARATOR));
                    return categoryRepository.findByCodeIn(codes).stream()
                            .map(Category::getId)
                            .toList();
                })
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findSelfAndDescendantIds(UUID id) {
        return categoryRepository.findById(id)
                .map(category -> Stream.concat(
                                Stream.of(category),
                                categoryRepository.findDescendants(category.getPath()).stream())
                        .map(Category::getId)
                        .toList())
                .orElse(List.of());
    }

    //-------------------------------------------PRIVATE---------------------------------//

    private Category findByCode(String code) {
        return categoryRepository
                .findByCodeAndDeletedFalse(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /**
     * Chuyển {@code category} sang cha mới: tính lại level/path của nó và của cả cây con, rồi cập
     * nhật cờ lá của cha cũ và cha mới. Cây con phải nạp theo path cũ trước khi path bị đổi.
     */
    private void moveUnder(Category category, Category newParent) {
        Category oldParent = category.getParent();
        String oldPath = category.getPath();
        int oldLevel = category.getLevel();
        List<Category> descendants = categoryRepository.findDescendants(oldPath);

        category.setParent(newParent);
        category.setLevel(levelUnder(newParent));
        category.setPath(pathUnder(newParent, category.getCode()));

        int levelShift = category.getLevel() - oldLevel;
        for (Category descendant : descendants) {
            descendant.setPath(category.getPath() + descendant.getPath().substring(oldPath.length()));
            descendant.setLevel(descendant.getLevel() + levelShift);
        }
        newParent.setIsLeaf(false);
        if (oldParent != null)
            oldParent.setIsLeaf(!categoryRepository.existsByParent_IdAndDeletedFalse(oldParent.getId()));
    }

    private int levelUnder(Category parent) {
        return parent == null ? 0 : parent.getLevel() + 1;
    }

    private String pathUnder(Category parent, String code) {
        return parent == null ? code : parent.getPath() + PATH_SEPARATOR + code;
    }

    /**
     * Ghi code vào cache sau khi transaction commit, để rollback không để lại code ma trong cache.
     */
    private void registerCodeAfterCommit(UUID id, String code) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cateCache.register(id, code);
            }
        });
    }
}
