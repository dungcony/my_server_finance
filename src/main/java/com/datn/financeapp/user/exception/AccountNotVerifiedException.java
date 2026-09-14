package com.datn.financeapp.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class AccountNotVerifiedException extends BusinessException {
    public AccountNotVerifiedException() {
        super(ErrorCode.ACCOUNT_NOT_VERIFIED);
    }
}
