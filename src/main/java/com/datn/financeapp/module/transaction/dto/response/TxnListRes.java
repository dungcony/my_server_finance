package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

public record TxnListRes(
        List<TxnSummaryRes> txns,
        PageMeta page
) {
}
