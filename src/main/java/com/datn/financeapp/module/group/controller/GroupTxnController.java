package com.datn.financeapp.module.group.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.group.dto.request.transaction.*;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnListRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnParticipantRes;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.service.GroupTxnCreateService;
import com.datn.financeapp.module.group.service.GroupTxnDeleteService;
import com.datn.financeapp.module.group.service.GroupTxnGetService;
import com.datn.financeapp.module.group.service.GroupTxnUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class GroupTxnController {

    private final GroupTxnGetService groupTxnGetService;
    private final GroupTxnCreateService groupTxnCreateService;
    private final GroupTxnUpdateService groupTxnUpdateService;
    private final GroupTxnDeleteService groupTxnDeleteService;

    @PostMapping("/transactions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupTxnDetailRes> create(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTxnCreateReq req) {
        return ApiResponse.of(groupTxnCreateService.create(groupId, req));
    }

    @GetMapping("/transactions/all")
    public ApiResponse<GroupTxnListRes> all(
            @PathVariable UUID groupId,
            @RequestParam(name = "statuses", required = false) List<GroupTxnStatus> statuses,
            @ModelAttribute GroupTxnFilterReq filter,
            Pageable pageable
    ) {
        return ApiResponse.of(groupTxnGetService.all(groupId, statuses, filter, pageable));
    }

    @GetMapping("/transactions")
    public ApiResponse<GroupTxnListRes> genera(
            @PathVariable UUID groupId,
            @ModelAttribute GroupTxnFilterReq filter,
            Pageable pageable
    ) {
        return ApiResponse.of(groupTxnGetService.generalList(groupId, filter, pageable));
    }

    @GetMapping("/transactions/mine")
    public ApiResponse<GroupTxnListRes> myList(
            @PathVariable UUID groupId,
            @RequestParam(name = "statuses", required = false) List<GroupTxnStatus> statuses,
            @ModelAttribute GroupTxnFilterReq filter,
            Pageable pageable
    ) {
        return ApiResponse.of(groupTxnGetService.myList(groupId, statuses, filter, pageable));
    }

    @GetMapping("/transactions/{txnId}")
    public ApiResponse<GroupTxnDetailRes> detail(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        return ApiResponse.of(groupTxnGetService.detail(groupId, txnId));
    }

    @GetMapping("/transactions/{txnId}/participants")
    public ApiResponse<List<GroupTxnParticipantRes>> getParticipants(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        return ApiResponse.of(groupTxnGetService.getParticipants(groupId, txnId));
    }

    @PutMapping("/transactions/{txnId}")
    public ApiResponse<GroupTxnDetailRes> update(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTxnUpdateReq req) {
        return ApiResponse.of(groupTxnUpdateService.update(groupId, req));
    }

    @DeleteMapping("/transactions/{txnId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        groupTxnDeleteService.softDelete(groupId, txnId);
        return ApiResponse.of(null);
    }

    @PostMapping("/transactions/status")
    public ApiResponse<GroupTxnDetailRes> confirm(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTxnUpdateStatusReq req) {
        return ApiResponse.of(groupTxnUpdateService.status(groupId, req));
    }

    @PostMapping("/transactions/statuses")
    public ApiResponse<?> bulkConfirm(
            @PathVariable UUID groupId,
            @Valid @RequestBody BatchGroupTxnUpdateStatusReq req) {
        return ApiResponse.of(groupTxnUpdateService.statuses(groupId, req));
    }

}
