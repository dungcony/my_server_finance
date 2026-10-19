package com.datn.financeapp.module.budget.helper;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tiện ích truy vấn danh mục, duyệt cây danh mục cha - con phục vụ tính toán ngân sách.
 */
@Component
@RequiredArgsConstructor
public class BudgetCategoryHelper {

    private final CategoryRepository categoryRepository;
    private final CateMapper cateMapper;
    private final IconService iconService;

    public Optional<CateSumaryRes> findSummary(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return categoryRepository.findByCodeAndIsDeletedFalse(code)
                .map(this::toSummary);
    }

    /**
     * Tìm mã của chính danh mục và toàn bộ danh mục tổ tiên (dựa vào cột path: "an_uong/ca_phe").
     */
    public List<String> findAncestorCodes(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        return categoryRepository.findByCode(code)
                .map(Category::getPath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> Arrays.asList(path.split("/")))
                .orElseGet(() -> List.of(code));
    }

    /**
     * Tìm mã của chính danh mục và toàn bộ danh mục con cháu.
     */
    public List<String> findDescendantCodes(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        Category category = categoryRepository.findByCode(code).orElse(null);
        if (category == null) {
            return List.of();
        }
        List<Category> descendants = categoryRepository.findDescendants(category.getPath());
        List<String> codes = new ArrayList<>();
        codes.add(category.getCode());
        for (Category d : descendants) {
            codes.add(d.getCode());
        }
        return codes;
    }

    /**
     * Lấy tóm tắt danh mục theo danh sách mã.
     */
    public Map<String, CateSumaryRes> findSummaries(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, CateSumaryRes> result = new HashMap<>();
        for (String code : codes) {
            findSummary(code).ifPresent(summary -> result.put(code, summary));
        }
        return result;
    }

    private CateSumaryRes toSummary(Category category) {
        String iconPath = iconService.findIconByCode(category.getIconCode());
        return cateMapper.toSumary(category, iconPath);
    }
}
