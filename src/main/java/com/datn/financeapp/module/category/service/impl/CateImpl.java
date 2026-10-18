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
import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CateImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final IconService iconService;
    private final CateMapper cateMapper;
    private final CateCache cateCache;


    @Override
    public Optional<CateSumaryRes> findByIdAndIsDeletedAndIsLeaf(UUID id, boolean includeDeleted, boolean isLeaf) {

        var cate = categoryRepository.findById(id)
                .orElse(null);

        if (cate == null)
            return Optional.empty();

        if (!includeDeleted && cate.getIsDeleted())
            return Optional.empty();

        if (isLeaf && !cate.getIsLeaf())
            return Optional.empty();

        return Optional.of(cateMapper.toSumary(cate, iconService.findIconByCode(cate.getIconCode())));
    }

    @Override
    public CategoryRes findActiveById(UUID id) {
        return null;
    }

    @Override
    public CateSumaryRes findSumaryActiveById(UUID id) {
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public CateSumaryRes findIfIsleaf(UUID id) {
        // lấy entity trực tiếp từ repository
        Category category = categoryRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // kiểm tra điều kiện danh mục lá trên entity trước khi map
        if (!Boolean.TRUE.equals(category.getIsLeaf()))
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_NOT_LEAF);

        return cateMapper.toSumary(category, iconOf(category.getIconCode()));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryRes findByIdForRead(UUID id) {
        // lấy entity trực tiếp từ repository
        Category category = categoryRepository.findByIdAndIsDeletedFalse(id)
                .orElse(null);

        if (category == null)
            return null;

        return cateMapper.toResponse(category, iconOf(category.getIconId()));
    }

    @Override
    @Transactional(readOnly = true)
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
    public List<UUID> findSelfAndAncestorIds(UUID id) {
        // Đọc cả danh mục đã xóa mềm: giao dịch cũ vẫn trỏ vào danh mục đó và ngân sách ở tổ tiên vẫn phải cộng gộp.
        return categoryRepository.findById(id)
                .map(category -> {
                    List<String> codes = List.of(category.getPath().split("/"));
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

    //-------------------------- PRIVATE -------------------------//
    // icon danh mục chỉ để hiển thị: icon đã bị tắt thì trả null (response để trống icon),
    // không được chặn việc kiểm danh mục khi ghi giao dịch
    private IconRes iconOf(UUID iconId) {
        if (iconId == null)
            return null;

        return iconService.findActiveByIdForRead(iconId);
    }

}
