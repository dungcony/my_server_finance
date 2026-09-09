package com.datn.financeapp.user.exception;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản.");
    }

    public UserNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
