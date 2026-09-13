package com.datn.financeapp.auth.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class AuthResetCodeInvalidException extends BusinessException {

    public AuthResetCodeInvalidException() {
        super(ErrorCode.RESET_CODE_INVALID);
    }
}
