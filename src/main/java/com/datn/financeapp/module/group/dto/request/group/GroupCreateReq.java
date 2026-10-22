package com.datn.financeapp.module.group.dto.request.group;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record GroupCreateReq(
        @NotBlank(message = "Tên nhóm không được để trống.") @Size(max = 100, message = "Tên nhóm không được vượt quá 100 ký tự.") String name,
        @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự.") String description,
        @Positive(message = "Mục tiêu quỹ phải lớn hơn 0.") Long target,
        @NotNull(message = "Chế độ quyết toán không được để trống.") Boolean isSettlementEnabled,
        @NotNull(message = "Chế độ tham gia không cần duyệt không được để trống.") Boolean isJoinWithoutConfirm,
        @NotNull(message = "Danh sách thành viên không được để trống.") List<UUID> members
) {
}
