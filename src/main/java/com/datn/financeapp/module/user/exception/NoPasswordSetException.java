package com.datn.financeapp.module.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class NoPasswordSetException extends BusinessException {

    public NoPasswordSetException() {
        super(ErrorCode.AUTH_PASSWORD_NOT_SET);
    }
}
