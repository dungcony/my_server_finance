package com.datn.financeapp.module.category.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.dto.response.IconGroupListResponse;
import com.datn.financeapp.module.category.dto.response.IconGroupResponse;
import com.datn.financeapp.module.category.dto.response.IconResponse;
import com.datn.financeapp.module.category.entity.Icon;
import com.datn.financeapp.module.category.mapper.IconMapper;
import com.datn.financeapp.module.category.repository.IconRepository;
import com.datn.financeapp.module.category.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Triển khai {@link IconService}.
 * <p>
 * <b>Các hàm:</b> {@link #listIcons(String, String)} đọc biểu tượng đang dùng rồi gom theo nhóm.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class IconImpl implements IconService {

    // 8 nhóm biểu tượng cố định — ck_icons_group ở db/migration/V1__tables.sql.
    private static final Map<String, String> ICON_GROUP_NAMES = Map.of(
            "an_uong", "Ăn uống",
            "di_lai", "Đi lại",
            "mua_sam", "Mua sắm",
            "giai_tri", "Giải trí",
            "suc_khoe", "Sức khoẻ",
            "hoc_tap", "Học tập",
            "tai_chinh", "Tài chính",
            "khac", "Khác");

    private final IconRepository iconRepository;
    private final IconMapper iconMapper;

    @Override
    @Transactional(readOnly = true)
    public IconGroupListResponse listIcons(String iconGroup, String search) {
        List<Icon> icons = iconRepository.search(iconGroup, toLikePattern(search));

        Map<String, List<IconResponse>> itemsByGroup = new LinkedHashMap<>();
        for (Icon icon : icons) {
            itemsByGroup
                    .computeIfAbsent(icon.getIconGroup(), group -> new ArrayList<>())
                    .add(iconMapper.toIconResponse(icon));
        }
        List<IconGroupResponse> groups = itemsByGroup.entrySet().stream()
                .map(entry -> new IconGroupResponse(
                        entry.getKey(), ICON_GROUP_NAMES.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()))
                .toList();
        return new IconGroupListResponse(groups, icons.size());
    }

    @Override
    public Icon getActive(String code) {
        return iconRepository
                .findByCodeAndIsActiveTrue(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.ICON_NOT_FOUND));
    }

    private String toLikePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
