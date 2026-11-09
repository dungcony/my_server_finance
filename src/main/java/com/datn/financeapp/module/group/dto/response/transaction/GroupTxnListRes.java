package com.datn.financeapp.module.group.dto.response.transaction;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

public record GroupTxnListRes(
        List<GroupTxnSumaryRes> items,
        PageMeta meta
) {
    public static GroupTxnListRes of(List<GroupTxnSumaryRes> items, PageMeta meta) {
        return new GroupTxnListRes(items, meta);
    }
}
