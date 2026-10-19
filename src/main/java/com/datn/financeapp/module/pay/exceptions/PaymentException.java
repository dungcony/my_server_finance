package com.datn.financeapp.module.pay.exceptions;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

/**
 * Lỗi nghiệp vụ của module thanh toán; mã lỗi cụ thể truyền qua {@link ErrorCode}.
 */
public class PaymentException extends BusinessException {

    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PaymentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
