package com.datn.financeapp.group.dto.response.transaction;

import com.datn.financeapp.common.response.PageMeta;
import java.util.List;

public record GroupTransactionListRes(
        List<GroupTransactionDetailRes> items,
        PageMeta meta
) {
    public static GroupTransactionListRes of(List<GroupTransactionDetailRes> items, PageMeta meta) {
        return new GroupTransactionListRes(items, meta);
    }
}
