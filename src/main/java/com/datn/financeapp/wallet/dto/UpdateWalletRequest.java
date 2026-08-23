package com.datn.financeapp.wallet.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;

/**
 * Body của PATCH /wallets/{id} (api/02-VI.md mục 5). Cố tình KHÔNG khai báo hai field số dư và
 * loại ví — chặn ngay ở tầng hợp đồng (T-02-04). {@code @JsonAnySetter} gom mọi field lạ trong
 * JSON gửi lên vào {@code extraFields} để WalletService phát hiện và trả
 * {@code BALANCE_NOT_EDITABLE} thay vì Jackson âm thầm bỏ qua.
 */
public class UpdateWalletRequest {

    @Size(min = 1, max = 50)
    private String name;

    private Boolean includeInTotal;

    private String icon;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String color;

    private final Map<String, Object> extraFields = new HashMap<>();

    @JsonAnySetter
    public void addExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean includeInTotal() {
        return includeInTotal;
    }

    public void setIncludeInTotal(Boolean includeInTotal) {
        this.includeInTotal = includeInTotal;
    }

    public String icon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String color() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Map<String, Object> extraFields() {
        return extraFields;
    }
}
