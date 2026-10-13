package com.datn.financeapp.module.group.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.module.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.module.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.module.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.module.group.service.GroupService;
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
        return ApiResponse.of(groupService.create(req));
    }

    @GetMapping
    public ApiResponse<List<GroupSummaryRes>> list() {
        return ApiResponse.of(groupService.list());
    }


    @GetMapping("/{id}")
    public ApiResponse<GroupDetailRes> detail(@PathVariable UUID id) {
        return ApiResponse.of(groupService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<GroupDetailRes> update(
            @PathVariable UUID id,
            @Valid @RequestBody GroupUpdateReq req) {
        return ApiResponse.of(groupService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        groupService.delete(id);
        return ApiResponse.of(null);
    }

    @Idempotent
    @PostMapping("/join")
    public ApiResponse<Void> join(@RequestParam("code") String code) {
        groupService.joinByCode(code);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable UUID id) {
        groupService.archive(id);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/unarchive")
    public ApiResponse<Void> unarchive(@PathVariable UUID id) {
        groupService.unarchive(id);
        return ApiResponse.of(null);
    }

}
