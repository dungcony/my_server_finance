package com.datn.financeapp.module.icon.mapper;

import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.entity.Icon;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper chuyển đổi giữa entity {@link Icon} và DTO {@link IconRes}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IconMapper {

    IconRes toIconResponse(Icon icon);
}
