package com.datn.financeapp.module.group.dto.request.group;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GroupUpdateReq(
        @Size(max = 100, message = "Tên nhóm không được vượt quá 100 ký tự.") String name,
        @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự.") String description,
        @Positive(message = "Mục tiêu quỹ phải lớn hơn 0.") Long target,
        @JsonAlias({"is_split_equally", "isSplitEqually"}) Boolean isSettlementEnabled,
        Boolean isJoinWithoutConfirm) {
}
