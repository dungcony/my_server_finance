package com.datn.financeapp.module.icon.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.icon.dto.response.IconGroupListResponse;
import com.datn.financeapp.module.icon.dto.response.IconGroupResponse;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.entity.Icon;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import com.datn.financeapp.module.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IconImpl implements IconService {

    private final IconRepository iconRepository;
    private final IconMapper iconMapper;

    @Override
    public IconResponse findDefaultWallet() {
        return iconRepository.findByCodeAndIsActiveTrue("default_wallet")
                .map(iconMapper::toIconResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.ICON_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public IconResponse findActiveByCode(String code) {
        return iconRepository
                .findByCodeAndIsActiveTrue(code)
                .map(iconMapper::toIconResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.ICON_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public IconResponse findActiveById(UUID id) {
        if (id == null) {
            return null;
        }
        return iconRepository
                .findByIdAndIsActiveTrue(id)
                .map(iconMapper::toIconResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.ICON_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, IconResponse> findActiveByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return iconRepository.findByIdInAndIsActiveTrue(ids).stream()
                .collect(Collectors.toMap(Icon::getId, iconMapper::toIconResponse));
    }
}
