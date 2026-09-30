package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @PostMapping("/join")
    public ApiResponse<Void> join(@Valid @RequestBody GroupJoinReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupService.joinByCode(userId, req);
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

}
