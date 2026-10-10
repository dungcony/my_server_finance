package com.datn.financeapp.module.category.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.category.dto.response.IconGroupListResponse;
import com.datn.financeapp.module.category.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller kho biểu tượng hệ thống, mở cho mọi người dùng đã đăng nhập.
 * <p>
 * <b>Endpoint:</b> {@link #icons(String, String)}: {@code GET /icons} - Biểu tượng gom theo nhóm.
 * </p>
 *
 * @see IconService
 */
@RestController
@RequiredArgsConstructor
public class IconController {

    private final IconService iconService;

    @GetMapping("/icons")
    public ApiResponse<IconGroupListResponse> icons(
            @RequestParam(name = "icon_group", required = false) String iconGroup,
            @RequestParam(required = false) String search) {
        return ApiResponse.of(iconService.listIcons(iconGroup, search));
    }
}
