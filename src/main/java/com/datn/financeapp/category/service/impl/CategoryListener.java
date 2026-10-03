package com.datn.financeapp.category.service.impl;

import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.events.ValidCategorySystemEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryListener {

    private final CategoryRepository categoryRepository;

    @EventListener
    public void validCategoryLisener(ValidCategorySystemEvent event) {
        Category category = categoryRepository
                .findById(event.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.getUserId() != null || !"expense".equalsIgnoreCase(category.getType())) {
            throw new BusinessException(ErrorCode.GROUP_TXN_SYSTEM_CATEGORY_REQUIRED);
        }
    }
}
