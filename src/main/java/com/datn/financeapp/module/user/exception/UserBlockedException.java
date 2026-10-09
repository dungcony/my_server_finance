package com.datn.financeapp.module.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class UserBlockedException extends BusinessException {

    public UserBlockedException() {
        super(ErrorCode.AUTH_ACCOUNT_BLOCKED);
    }
}
