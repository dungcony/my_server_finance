package com.datn.financeapp.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class PasswordAlreadySetException extends BusinessException {

    public PasswordAlreadySetException() {
        super(ErrorCode.AUTH_PASSWORD_ALREADY_SET);
    }
}
