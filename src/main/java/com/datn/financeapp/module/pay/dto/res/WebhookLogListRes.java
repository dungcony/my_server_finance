package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

/**
 * Nhật ký webhook có phân trang theo chuẩn Rule 2.4.
 */
public record WebhookLogListRes(List<WebhookLogRes> items, PageMeta meta) {
}
