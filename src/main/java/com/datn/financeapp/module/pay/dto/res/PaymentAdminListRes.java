package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

/**
 * Danh sách thanh toán toàn hệ thống có phân trang, dành cho quản trị viên.
 */
public record PaymentAdminListRes(List<PaymentAdminRes> items, PageMeta meta) {
}
