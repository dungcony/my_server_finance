package com.datn.financeapp.auth.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class AuthAccountLockedException extends BusinessException {

    public AuthAccountLockedException() {
        super(ErrorCode.ACCOUNT_LOCKED);
    }
}
