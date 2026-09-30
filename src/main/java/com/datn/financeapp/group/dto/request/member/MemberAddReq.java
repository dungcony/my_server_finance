package com.datn.financeapp.group.dto.request.member;

import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 *
 * @param memberIds danh sách member đc thêm
 * @param status    trạng thái
 * @param role      quyền
 * @param addAt     thời gian thêm
 */
public record MemberAddReq(
        @NotNull(message = "phải có id người được thêm vào") List<UUID> memberIds,
        @NotNull(message = "cần phải có trạng thái member") MemberStatus status,
        MemberRole role,
        Instant addAt
) {

}
