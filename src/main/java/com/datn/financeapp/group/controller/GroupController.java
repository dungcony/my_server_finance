package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupInviteCodeRes;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.response.group.GroupMemberRes;
import com.datn.financeapp.group.dto.request.group.GroupMemberRoleReq;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.service.GroupService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupDetailRes> create(@Valid @RequestBody GroupCreateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupService.create(userId, req));
    }

    @GetMapping
    public ApiResponse<List<GroupSummaryRes>> list() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupService.list(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<GroupDetailRes> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupService.detail(userId, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<GroupDetailRes> update(
            @PathVariable UUID id,
            @Valid @RequestBody GroupUpdateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.delete(userId, id);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/invite-code")
    public ApiResponse<GroupInviteCodeRes> getOrRegenerateInviteCode(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        UUID userId = SecurityContextUtil.currentUserId();
        if (regenerate) {
            return ApiResponse.of(groupService.regenerateInviteCode(userId, id));
        }
        return ApiResponse.of(groupService.getInviteCode(userId, id));
    }

    @PostMapping("/join")
    public ApiResponse<GroupDetailRes> join(@Valid @RequestBody GroupJoinReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupService.join(userId, req));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<GroupMemberRes>> listMembers(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupService.listMembers(userId, id));
    }

    @PatchMapping("/{id}/members/{memberUserId}/role")
    public ApiResponse<Void> updateMemberRole(
            @PathVariable UUID id,
            @PathVariable UUID memberUserId,
            @Valid @RequestBody GroupMemberRoleReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.updateMemberRole(userId, id, memberUserId, req.role());
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/members/{memberUserId}/approve")
    public ApiResponse<Void> approveMember(
            @PathVariable UUID id,
            @PathVariable UUID memberUserId) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.approveMember(userId, id, memberUserId);
        return ApiResponse.of(null);
    }

    @DeleteMapping("/{id}/members/{memberUserId}")
    public ApiResponse<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID memberUserId) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.removeMember(userId, id, memberUserId);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.archive(userId, id);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/unarchive")
    public ApiResponse<Void> unarchive(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.unarchive(userId, id);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/transfer-ownership")
    public ApiResponse<Void> transferOwnership(
            @PathVariable UUID id,
            @Valid @RequestBody com.datn.financeapp.group.dto.request.group.GroupTransferOwnershipReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.transferOwnership(userId, id, req.newOwnerId());
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<Void> leave(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.leave(userId, id);
        return ApiResponse.of(null);
    }
}
