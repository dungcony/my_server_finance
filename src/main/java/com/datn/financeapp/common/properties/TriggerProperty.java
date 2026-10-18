package com.datn.financeapp.common.properties;

import com.datn.financeapp.common.exception.ErrorCode;

import java.util.Map;

public class TriggerProperty {
    public static final Map<String, ErrorCode> CONSTRAINT_MAP = Map.of(
            "uq_wallets_user_name", ErrorCode.WALLET_NAME_EXISTS,
            "uq_wallets_user_name_type", ErrorCode.WALLET_NAME_EXISTS,
            "uq_users_email", ErrorCode.AUTH_EMAIL_ALREADY_EXISTS,
            "uq_roles_name", ErrorCode.USER_ROLE_PERMISSION_ALREADY_ASSIGNED,
            "TRANSACTION_CATEGORY_NOT_LEAF", ErrorCode.TRANSACTION_CATEGORY_NOT_LEAF,
            "TRANSACTION_CATEGORY_DELETED", ErrorCode.CATEGORY_NOT_FOUND
    );
}
