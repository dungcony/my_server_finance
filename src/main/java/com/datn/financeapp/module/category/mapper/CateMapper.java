package com.datn.financeapp.module.category.mapper;

import com.datn.financeapp.module.category.dto.response.CategoryResponse;
import com.datn.financeapp.module.category.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CateMapper {
    CategoryResponse toRespone(Category category);
}
