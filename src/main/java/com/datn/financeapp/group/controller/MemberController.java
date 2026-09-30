package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API quản lý thành viên trong nhóm, giữ nguyên tiền tố {@code /groups/{groupId}}.
 * <ul>
 * <li>{@link #listMembers}: danh sách thành viên (trừ người đã bị xoá).</li>
 * quyền OWNER).</li>
 * <li>{@link #approveMember}: duyệt thành viên đang chờ.</li>
 * <li>{@link #removeMember}: mời thành viên ra khỏi nhóm.</li>
 * <li>{@link #leave}: người dùng hiện tại rời nhóm.</li>
 * </ul>
 */
@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/members")
    public ApiResponse<List<MemberRes>> listMembers(@PathVariable UUID groupId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(memberService.findMembers(groupId));
    }


    @PostMapping("/members/{memberUserId}/approve")
    public ApiResponse<Void> approveMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberUserId) {
        UUID userId = SecurityContextUtil.currentUserId();
        memberService.approve(userId, groupId, memberUserId);
        return ApiResponse.of(null);
    }

    @DeleteMapping("/members/{memberUserId}")
    public ApiResponse<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberUserId) {
        UUID userId = SecurityContextUtil.currentUserId();
        memberService.removeMember(userId, groupId, memberUserId);
        return ApiResponse.of(null);
    }

    @PostMapping("/leave")
    public ApiResponse<Void> leave(@PathVariable UUID groupId) {
        UUID userId = SecurityContextUtil.currentUserId();
        memberService.leave(userId, groupId);
        return ApiResponse.of(null);
    }
}
