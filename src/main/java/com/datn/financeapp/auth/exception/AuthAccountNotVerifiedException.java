package com.datn.financeapp.auth.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class AuthAccountNotVerifiedException extends BusinessException {
    public AuthAccountNotVerifiedException() {
        super(ErrorCode.ACCOUNT_NOT_VERIFIED);
    }
}
