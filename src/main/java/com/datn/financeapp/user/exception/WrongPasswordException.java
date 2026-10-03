package com.datn.financeapp.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class WrongPasswordException extends BusinessException {

    public WrongPasswordException() {
        super(ErrorCode.AUTH_PASSWORD_INCORRECT);
    }

    public WrongPasswordException(ErrorCode code) {
        super(code);
    }
}
