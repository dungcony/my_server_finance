package com.datn.financeapp.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class PasswordAlreadySetException extends BusinessException {

    public PasswordAlreadySetException() {
        super(ErrorCode.PASSWORD_ALREADY_SET);
    }
}
