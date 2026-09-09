package com.datn.financeapp.auth.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class AuthInvalidCredentialsException extends BusinessException {

    public AuthInvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}
