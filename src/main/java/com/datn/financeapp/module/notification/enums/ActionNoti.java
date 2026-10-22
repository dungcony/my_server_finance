package com.datn.financeapp.module.notification.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ActionNoti {
    REVIEW("REVIEW"),
    RECORD("RECORD"),
    NOTIFY("NOTIFY");

    private final String code;

    ActionNoti(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

}
