package com.datn.financeapp.module.category.mapper;

import com.datn.financeapp.module.category.dto.response.CategoryResponse;
import com.datn.financeapp.module.category.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = IconMapper.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CateMapper {

    @Mapping(target = "parentCode", source = "parent.code")
    @Mapping(target = "isDeleted", source = "deleted")
    CategoryResponse toResponse(Category category);

}
