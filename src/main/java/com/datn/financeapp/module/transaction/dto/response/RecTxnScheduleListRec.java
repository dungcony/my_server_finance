package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

/**
 * Phản hồi danh sách các buổi lịch phát sinh có hỗ trợ phân trang.
 *
 * @param items Danh sách các buổi lịch phát sinh
 * @param meta  Metadata thông tin phân trang chuẩn hệ thống
 */
public record RecTxnScheduleListRec(
        List<RecTxnScheduleRes> items,
        PageMeta meta
) {
}
