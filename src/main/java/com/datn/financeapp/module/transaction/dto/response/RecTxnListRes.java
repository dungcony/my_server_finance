package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;
import java.util.UUID;

public record RecTxnListRes(
        List<RecTxnSumaryRes> recs,
        PageMeta pageMeta
) {


    public record RecTxnSumaryRes(
            UUID id,
            String displayName,
            Boolean isEnabled
    ) {
    }
}
