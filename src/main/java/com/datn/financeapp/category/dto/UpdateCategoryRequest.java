package com.datn.financeapp.category.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Body của PATCH /categories/{id} (api/03-DANH-MUC.md mục 4). Cố tình KHÔNG khai báo field
 * {@code type} — chặn ngay ở tầng hợp đồng (T-02-08), tương tự cách {@code UpdateWalletRequest}
 * chặn {@code current_balance}. {@code @JsonAnySetter} gom field lạ để phát hiện tường minh nếu
 * client cố gửi {@code type}, thay vì Jackson âm thầm bỏ qua.
 */
public class UpdateCategoryRequest {

    @Size(min = 1, max = 50)
    private String name;

    private UUID iconId;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.")
    private String color;

    private UUID categoryGroupId;

    private UUID parentCategoryId;
    private boolean parentCategoryIdSet;

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

    public UUID iconId() {
        return iconId;
    }

    public void setIconId(UUID iconId) {
        this.iconId = iconId;
    }

    public String color() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public UUID categoryGroupId() {
        return categoryGroupId;
    }

    public void setCategoryGroupId(UUID categoryGroupId) {
        this.categoryGroupId = categoryGroupId;
    }

    public UUID parentCategoryId() {
        return parentCategoryId;
    }

    public void setParentCategoryId(UUID parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
        this.parentCategoryIdSet = true;
    }

    /** true khi client thực sự gửi field {@code parent_category_id} trong JSON (kể cả null). */
    public boolean isParentCategoryIdSet() {
        return parentCategoryIdSet;
    }

    public Map<String, Object> extraFields() {
        return extraFields;
    }
}
