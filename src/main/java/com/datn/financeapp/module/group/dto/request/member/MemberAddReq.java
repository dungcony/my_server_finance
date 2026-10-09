package com.datn.financeapp.module.group.dto.request.member;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 *
 * @param memberIds danh sách member đc thêm  thời gian thêm
 */
public record MemberAddReq(
        @NotNull(message = "phải có id người được thêm vào") List<UUID> memberIds
) {

}
