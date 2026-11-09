package com.datn.financeapp.module.debt.mapper;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.debt.dto.response.DebtPaymentListRes;
import com.datn.financeapp.module.debt.dto.response.DebtPaymentRes;
import com.datn.financeapp.module.debt.entity.DebtPayment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DebtPaymentMapper {

    DebtPaymentRes toRes(DebtPayment dp);

    default DebtPaymentListRes toDebtPaymentListRes(List<DebtPayment> list, PageMeta pageMeta) {
        return new DebtPaymentListRes(list.stream().map(this::toRes).toList(), pageMeta);
    }
}
