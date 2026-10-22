package com.datn.financeapp.module.group.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.module.group.dto.response.member.MemberRes;
import com.datn.financeapp.module.group.enums.MemberStatus;
import com.datn.financeapp.module.group.service.MemberBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API quản lý thành viên trong nhóm, giữ nguyên tiền tố {@code /groups/{groupId}}.
 */
@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class MemberController {

    private final MemberBehavierService memberBehavierService;


    @GetMapping("/members")
    public ApiResponse<List<MemberRes>> listMembers(
            @PathVariable UUID groupId,
            @RequestParam(required = false) MemberStatus status) {
        return ApiResponse.of(memberBehavierService.listMembers(groupId, status));
    }

    @PostMapping("/members")
    public ApiResponse<?> addMembers(
            @PathVariable UUID groupId,
            @RequestBody @Valid MemberAddReq req
    ) {
        return ApiResponse.of(
                memberBehavierService.ownerAddMembers(groupId, req)
        );
    }

    @PutMapping("/owner-role/{memberUserId}")
    public ApiResponse<Void> updateMemberRole(
            @PathVariable UUID groupId,
            @PathVariable UUID memberUserId) {
        memberBehavierService.transferOwnership(groupId, memberUserId);
        return ApiResponse.of(null);
    }

    @PostMapping("/members/{memberUserId}/approve")
    public ApiResponse<Void> approveMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberUserId) {
        memberBehavierService.approve(groupId, memberUserId);
        return ApiResponse.of(null);
    }

    @PostMapping("/approves")
    public ApiResponse<Void> approveMembers(
            @PathVariable UUID groupId) {
        memberBehavierService.approveAll(groupId);
        return ApiResponse.of(null);
    }


    @PostMapping("/members/{memberUserId}/reject")
    public ApiResponse<Void> rejectMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberUserId) {
        memberBehavierService.reject(groupId, memberUserId);
        return ApiResponse.of(null);
    }


    @PostMapping("/rejects")
    public ApiResponse<Void> rejectMembers(
            @PathVariable UUID groupId) {
        memberBehavierService.rejectAll(groupId);
        return ApiResponse.of(null);
    }

    @DeleteMapping("/members/{memberUserId}")
    public ApiResponse<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberUserId) {
        memberBehavierService.removeMember(groupId, memberUserId);
        return ApiResponse.of(null);
    }

    @PostMapping("/leave")
    public ApiResponse<Void> leave(@PathVariable UUID groupId) {
        memberBehavierService.leave(groupId);
        return ApiResponse.of(null);
    }
}
