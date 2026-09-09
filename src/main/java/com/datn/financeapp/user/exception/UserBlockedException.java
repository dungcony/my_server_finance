package com.datn.financeapp.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class UserBlockedException extends BusinessException {

    public UserBlockedException() {
        super(ErrorCode.ACCOUNT_BLOCKED);
    }
}
