package com.datn.financeapp.module.category.mapper;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CateMapper {

    @Mapping(target = "id", source = "category.id")
    @Mapping(target = "name", source = "category.name")
    @Mapping(target = "code", source = "category.code")
    @Mapping(target = "description", source = "category.description")
    @Mapping(target = "parentCode", source = "category.parent.code")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "color", source = "category.color")
    @Mapping(target = "level", source = "category.level")
    @Mapping(target = "isLeaf", source = "category.isLeaf")
    @Mapping(target = "createdAt", source = "category.createdAt")
    @Mapping(target = "isDeleted", source = "category.isDeleted")
    CategoryRes toResponse(Category category, String icon);

    CateSumaryRes toSumary(Category category, String icon);
}
