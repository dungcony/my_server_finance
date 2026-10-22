package com.datn.financeapp.module.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class PasswordSameAsOldException extends BusinessException {

    public PasswordSameAsOldException() {
        super(ErrorCode.AUTH_PASSWORD_SAME_AS_OLD);
    }

    public PasswordSameAsOldException(String message) {
        super(ErrorCode.AUTH_PASSWORD_SAME_AS_OLD, message);
    }
}
