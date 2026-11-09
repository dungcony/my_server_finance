package com.datn.financeapp.module.group.mapper;

import com.datn.financeapp.module.group.dto.response.fund.FundRes;
import com.datn.financeapp.module.group.entity.Fund;
import org.mapstruct.Mapper;

import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FundMapper {

    FundRes toResponse(Fund fund);
}
