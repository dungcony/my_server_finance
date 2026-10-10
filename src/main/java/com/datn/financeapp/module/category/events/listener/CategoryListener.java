package com.datn.financeapp.module.category.events.listener;

import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.events.ValidCategorySystemEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryListener {

    private final CategoryRepository categoryRepository;

    /**
     * Danh mục hiện chỉ có loại hệ thống và không phân biệt thu/chi, nên chỉ cần danh mục tồn
     * tại và chưa bị xoá.
     */
    @EventListener
    public void validCategoryLisener(ValidCategorySystemEvent event) {
        categoryRepository
                .findByIdAndDeletedFalse(event.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
