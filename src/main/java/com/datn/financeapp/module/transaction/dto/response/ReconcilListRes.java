package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.wallet.dto.response.ReconcileRes;

import java.util.List;

public record ReconcilListRes(
        List<ReconcileRes> reconcils,
        PageMeta pageMeta
) {
}
