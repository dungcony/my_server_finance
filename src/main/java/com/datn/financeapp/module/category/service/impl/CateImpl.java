package com.datn.financeapp.module.category.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.helper.CateCache;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    @Transactional(readOnly = true)
    public CategoryRes findActiveById(UUID id) {
        Category category = categoryRepository
                .findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        IconResponse icon = iconOf(category);
        return cateMapper.toResponse(category, icon);
    }

    @Override
    @Transactional(readOnly = true)
    public CateSumaryRes findIfIsleaf(UUID id) {
        // lấy entity trực tiếp từ repository
        Category category = categoryRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        // kiểm tra điều kiện danh mục lá trên entity trước khi map
        if (!Boolean.TRUE.equals(category.getIsLeaf())) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_NOT_LEAF);
        }
        IconResponse icon = iconOf(category);
        return cateMapper.toSumary(category, icon);
    }

    @Override
    public CategoryRes findCateIfIsleaf(UUID id) {
        // lấy entity trực tiếp từ repository
        Category category = categoryRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        // kiểm tra điều kiện danh mục lá trên entity trước khi map
        if (!Boolean.TRUE.equals(category.getIsLeaf())) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_NOT_LEAF);
        }
        IconResponse icon = iconOf(category);
        return cateMapper.toResponse(category, icon);
    }

    @Override
    public UUID findIdByCode(String code) {
        return cateCache.findActiveId(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    @Override
    public Map<UUID, String> findCodesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> codes = new HashMap<>();
        ids.forEach(id -> cateCache.findCode(id).ifPresent(code -> codes.put(id, code)));
        return codes;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CategoryRes> findAllByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Category> categories = categoryRepository.findAllById(ids);
        Map<UUID, IconResponse> iconMap = iconService.findActiveByIds(
                categories.stream().map(Category::getIconId).toList());
        return categories.stream()
                .collect(Collectors.toMap(Category::getId, c -> cateMapper.toResponse(c, iconMap.get(c.getIconId()))));
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

    // icon danh mục chỉ để hiển thị: icon đã bị tắt thì trả null (response để trống icon),
    // không được chặn việc kiểm danh mục khi ghi giao dịch
    private IconResponse iconOf(Category category) {
        return iconService.findActiveByIds(List.of(category.getIconId())).get(category.getIconId());
    }

}
