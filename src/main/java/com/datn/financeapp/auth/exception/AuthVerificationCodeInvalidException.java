package com.datn.financeapp.auth.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class AuthVerificationCodeInvalidException extends BusinessException {

    public AuthVerificationCodeInvalidException() {
        super(ErrorCode.VERIFICATION_CODE_INVALID);
    }
}
