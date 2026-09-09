package com.datn.financeapp.category.mapper;

import com.datn.financeapp.category.dto.response.CategoryGroupResponse;
import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.category.dto.response.CategoryResponse;
import com.datn.financeapp.category.dto.response.IconRefResponse;
import com.datn.financeapp.category.dto.response.IconResponse;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.entity.CategoryGroup;
import com.datn.financeapp.category.entity.Icon;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse.IconSummary toIconSummary(Icon icon);

    IconRefResponse toIconRef(Icon icon);

    IconResponse toIconResponse(Icon icon);

    @Mapping(target = "id", source = "group.id")
    @Mapping(target = "name", source = "group.name")
    @Mapping(target = "color", source = "group.color")
    @Mapping(target = "sortOrder", source = "group.sortOrder")
    @Mapping(target = "icon", source = "iconSummary")
    CategoryGroupResponse toCategoryGroupResponse(CategoryGroup group, CategoryResponse.IconSummary iconSummary);

    @Mapping(target = "id", source = "c.id")
    @Mapping(target = "name", source = "c.name")
    @Mapping(target = "type", source = "c.type")
    @Mapping(target = "color", source = "c.color")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "parentCategoryId", source = "c.parentCategoryId")
    CategoryRefResponse toRef(Category c, IconRefResponse icon);

    @Mapping(target = "id", source = "c.id")
    @Mapping(target = "name", source = "c.name")
    @Mapping(target = "type", source = "c.type")
    @Mapping(target = "isSystem", expression = "java(c.getUserId() == null)")
    @Mapping(target = "parentCategoryId", source = "c.parentCategoryId")
    @Mapping(target = "categoryGroup", expression = "java(toGroupSummary(group))")
    @Mapping(target = "icon", expression = "java(toIconSummary(icon))")
    @Mapping(target = "color", source = "c.color")
    @Mapping(target = "sortOrder", source = "c.sortOrder")
    @Mapping(target = "createdAt", source = "c.createdAt")
    @Mapping(target = "children", source = "children")
    CategoryResponse toResponse(Category c, CategoryGroup group, Icon icon, List<CategoryResponse> children);

    default CategoryResponse.CategoryGroupSummary toGroupSummary(CategoryGroup group) {
        return group == null ? null : new CategoryResponse.CategoryGroupSummary(group.getId(), group.getName(), group.getColor());
    }
}
