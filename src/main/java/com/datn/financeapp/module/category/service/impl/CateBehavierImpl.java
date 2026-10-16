package com.datn.financeapp.module.category.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.helper.CateCache;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.category.service.CategoryBehavierService;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CateBehavierImpl implements CategoryBehavierService {

    private static final String PATH_SEPARATOR = "/";

    private final CategoryRepository categoryRepository;
    private final IconService iconService;
    private final CateMapper cateMapper;
    private final CateCache cateCache;

    @Override
    @Transactional
    public CategoryRes create(CategoryCreateReq req) {
        if (cateCache.existsCode(req.code()))
            throw new BusinessException(ErrorCode.CATEGORY_CODE_EXISTS);

        IconResponse icon = iconService.findActiveById(req.iconId());
        Category parent = req.parentCode() != null ? findByCode(req.parentCode()) : null;
        UUID id = UUID.randomUUID();

        if (categoryRepository.existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(req.name(), id))
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);

        Category category = Category.builder()
                .id(id)
                .code(req.code())
                .name(req.name())
                .description(req.description())
                .parent(parent)
                .iconId(icon.id())
                .color(req.color())
                .level(levelUnder(parent))
                .path(pathUnder(parent, req.code()))
                .build();

        if (parent != null)
            parent.setIsLeaf(false);

        categoryRepository.save(category);
        registerCodeAfterCommit(id, req.code());
        return cateMapper.toResponse(category, icon);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryRes> getMasters() {
        List<Category> categories = categoryRepository.findByParentIsNullAndIsDeletedFalseOrderByNameAsc();
        Map<UUID, IconResponse> iconMap = iconService.findActiveByIds(
                categories.stream().map(Category::getIconId).toList());
        return categories.stream()
                .map(c -> cateMapper.toResponse(c, iconMap.get(c.getIconId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryRes> getOfParent(String parentCode) {
        Category parent = findByCode(parentCode);
        List<Category> categories = categoryRepository.findByParent_IdAndIsDeletedFalseOrderByNameAsc(parent.getId());
        Map<UUID, IconResponse> iconMap = iconService.findActiveByIds(
                categories.stream().map(Category::getIconId).toList());
        return categories.stream()
                .map(c -> cateMapper.toResponse(c, iconMap.get(c.getIconId())))
                .toList();
    }

    @Override
    @Transactional
    public CategoryRes update(String cateCode, CategoryUpdateReq req) {
        Category category = findByCode(cateCode);
        Category parent = req.parentCode() != null ? findByCode(req.parentCode()) : null;

        if (parent != null) {
            boolean isSameParent = category.getParent() != null
                    && Objects.equals(category.getParent().getId(), parent.getId());

            if (!isSameParent) {
                if (Objects.equals(category.getId(), parent.getId()) || parent.getPath().startsWith(category.getPath() + PATH_SEPARATOR))
                    throw new BusinessException(ErrorCode.CATEGORY_PARENT_INVALID);
                moveUnder(category, parent);
            }
        }

        if (req.name() != null
                && categoryRepository.existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(req.name(), category.getId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }

        // chọn icon mới thì phải còn bật (findActiveById báo ICON_NOT_FOUND), giữ icon cũ thì chỉ hiển thị
        IconResponse icon;
        if (req.iconId() != null) {
            icon = iconService.findActiveById(req.iconId());
            category.setIconId(req.iconId());
        } else
            icon = iconService.findActiveByIds(List.of(category.getIconId())).get(category.getIconId());

        if (req.name() != null) {
            category.setName(req.name());
        }
        if (req.description() != null) {
            category.setDescription(req.description());
        }
        if (req.color() != null) {
            category.setColor(req.color());
        }
        return cateMapper.toResponse(category, icon);
    }

    @Override
    @Transactional
    public void delete(String cateCode) {

        Category category = findByCode(cateCode);
        category.setIsDeleted(true);

        List<UUID> deletedIds = new ArrayList<>(List.of(category.getId()));
        // xóa các danh mục con
        categoryRepository.findDescendants(category.getPath()).forEach(descendant -> {
            descendant.setIsDeleted(true);
            deletedIds.add(descendant.getId());
        });
        markDeletedAfterCommit(deletedIds);
        Category parent = category.getParent();

        if (parent != null)
            parent.setIsLeaf(!categoryRepository.existsByParent_IdAndIsDeletedFalse(parent.getId()));
    }

    // ------------------------------PRIVATE METHOD-------------------------------//
    private Category findByCode(String code) {
        return categoryRepository
                .findByCodeAndIsDeletedFalse(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /**
     * Chuyển danh mục sang cha mới, tịnh tiến lại toàn bộ đường dẫn (path) và cấp bậc (level)
     * của cây con hậu duệ, đồng thời cập nhật trạng thái nút lá (isLeaf) cho cả cha cũ lẫn cha mới.
     */
    private void moveUnder(Category category, Category newParent) {
        Category oldParent = category.getParent();
        String oldPath = category.getPath();
        int oldLevel = category.getLevel();
        List<Category> descendants = categoryRepository.findDescendants(oldPath);

        // Gán cha mới và cập nhật lại tọa độ (cấp bậc, đường dẫn) của danh mục hiện tại
        category.setParent(newParent);
        category.setLevel(levelUnder(newParent));
        category.setPath(pathUnder(newParent, category.getCode()));

        // Tịnh tiến độ lệch cấp bậc và thay thế tiền tố đường dẫn cho toàn bộ cây con hậu duệ
        int levelShift = category.getLevel() - oldLevel;
        for (Category descendant : descendants) {
            descendant.setPath(category.getPath() + descendant.getPath().substring(oldPath.length()));
            descendant.setLevel(descendant.getLevel() + levelShift);
        }
        // Cha mới được nhận thêm con nên chuyển trạng thái không còn là nút lá
        newParent.setIsLeaf(false);
        // Kiểm tra cha cũ: nếu không còn danh mục con nào khác đang hoạt động thì chuyển thành nút lá
        if (oldParent != null)
            oldParent.setIsLeaf(!categoryRepository.existsByParent_IdAndIsDeletedFalse(oldParent.getId()));
    }

    // Xác định cấp bậc theo nút cha: danh mục gốc là cấp 0, danh mục con kế thừa và tăng thêm 1 cấp
    private int levelUnder(Category parent) {
        return parent == null ? 0 : parent.getLevel() + 1;
    }

    // Xây dựng chuỗi path: danh mục gốc dùng mã code, danh mục con ghép nối tiếp sau path của cha
    private String pathUnder(Category parent, String code) {
        return parent == null ? code : parent.getPath() + PATH_SEPARATOR + code;
    }

    // Đồng bộ mã danh mục vào cache chỉ sau khi transaction ghi DB đã commit thành công
    private void registerCodeAfterCommit(UUID id, String code) {
        runAfterCommit(() -> cateCache.register(id, code));
    }

    // Xóa mềm danh mục khỏi cache sau khi transaction DB đã commit xong để tránh lỗi bất đồng bộ
    private void markDeletedAfterCommit(List<UUID> ids) {
        runAfterCommit(() -> cateCache.markDeleted(ids));
    }

    // Đăng ký hook thực thi sau khi transaction commit thành công để đảm bảo tính nhất quán với cache
    private void runAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
