package com.datn.financeapp.module.debt.dto.response;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

public record DebtPaymentListRes(
        List<DebtPaymentRes> list,
        PageMeta pageMeta
) {
}
