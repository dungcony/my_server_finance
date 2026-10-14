package com.datn.financeapp.module.icon.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.icon.dto.request.IconCreateReq;
import com.datn.financeapp.module.icon.dto.request.IconUpdateReq;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.service.IconBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/icons")
@RequiredArgsConstructor
public class IconController {

    private static final String PERM_UPDATE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).ICONS_UPDATE.getValue())";

    private static final String PERM_DELETE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).ICONS_DELETE.getValue())";

    private final IconBehavierService iconBehavierService;

    @GetMapping
    public ApiResponse<Slice<IconResponse>> getAll(
            @PageableDefault(size = 20, sort = "sortOrder") Pageable pageable) {
        return ApiResponse.of(iconBehavierService.getAll(pageable));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PERM_UPDATE)
    public ApiResponse<IconResponse> create(@Valid @RequestBody IconCreateReq req) {
        return ApiResponse.of(iconBehavierService.create(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(PERM_UPDATE)
    public ApiResponse<IconResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody IconUpdateReq req) {
        return ApiResponse.of(iconBehavierService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PERM_DELETE)
    public ApiResponse<IconResponse> delete(@PathVariable UUID id) {
        return ApiResponse.of(iconBehavierService.delete(id));
    }
}
