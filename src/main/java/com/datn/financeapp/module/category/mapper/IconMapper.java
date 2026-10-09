package com.datn.financeapp.module.category.mapper;

import com.datn.financeapp.module.category.dto.response.IconResponse;
import com.datn.financeapp.module.category.entity.Icon;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface IconMapper {

    IconResponse toIconResponse(Icon icon);
}
