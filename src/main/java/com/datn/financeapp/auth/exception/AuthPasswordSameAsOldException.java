package com.datn.financeapp.auth.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class AuthPasswordSameAsOldException extends BusinessException {

    public AuthPasswordSameAsOldException() {
        super(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
    }

    public AuthPasswordSameAsOldException(String message) {
        super(ErrorCode.NEW_PASSWORD_SAME_AS_OLD, message);
    }
}
