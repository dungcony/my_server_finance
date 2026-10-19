package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

/**
 * Lịch sử thanh toán của user có phân trang theo chuẩn Rule 2.4.
 */
public record PaymentListRes(List<PaymentStatusRes> items, PageMeta meta) {
}
