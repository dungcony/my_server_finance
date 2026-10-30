package com.datn.financeapp.module.debt.dto.response;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.debt.enums.DebtStatus;

import java.util.List;
import java.util.UUID;

public record DebtListRes(
        List<DebtSumaryRes> list,
        PageMeta pageMeta
) {
    public record DebtSumaryRes(
            UUID id,
            String name,
            DebtStatus status,
            Long totalAmount,
            Long paidAmount
    ) {
    }
}
