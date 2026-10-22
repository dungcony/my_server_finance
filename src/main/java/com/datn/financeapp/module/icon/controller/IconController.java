package com.datn.financeapp.module.icon.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.icon.dto.request.IconCreateReq;
import com.datn.financeapp.module.icon.dto.request.IconUpdateReq;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.service.IconBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/icons")
@RequiredArgsConstructor
public class IconController {

    private static final String PERM_UPDATE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).ICONS_UPDATE.name())";

    private static final String PERM_DELETE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).ICONS_DELETE.name())";

    private final IconBehavierService iconBehavierService;

    @GetMapping
    public ApiResponse<Slice<IconRes>> getAll(Pageable pageable) {
        return ApiResponse.of(iconBehavierService.getAll(pageable));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PERM_UPDATE)
    public ApiResponse<IconRes> create(@Valid @RequestBody IconCreateReq req) {
        return ApiResponse.of(iconBehavierService.create(req));
    }

    @PatchMapping("/{code}")
    @PreAuthorize(PERM_UPDATE)
    public ApiResponse<IconRes> update(
            @PathVariable String code,
            @Valid @RequestBody IconUpdateReq req) {
        return ApiResponse.of(iconBehavierService.update(code, req));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize(PERM_DELETE)
    public ApiResponse<IconRes> delete(@PathVariable String code) {
        return ApiResponse.of(iconBehavierService.delete(code));
    }
}
