package com.datn.financeapp.module.pay.exceptions;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

/**
 * Không có đơn thanh toán, hoặc đơn thuộc người khác. Dùng chung mã {@code NOT_FOUND} để không lộ việc đơn có tồn tại.
 */
public class PaymentNotFoundException extends BusinessException {

    public PaymentNotFoundException() {
        super(ErrorCode.NOT_FOUND, "Không tìm thấy đơn thanh toán.");
    }
}
