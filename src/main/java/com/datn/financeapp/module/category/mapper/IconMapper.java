package com.datn.financeapp.module.category.mapper;

import com.datn.financeapp.module.category.dto.response.IconResponse;
import com.datn.financeapp.module.category.entity.Icon;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IconMapper {

    IconResponse toIconResponse(Icon icon);
}
