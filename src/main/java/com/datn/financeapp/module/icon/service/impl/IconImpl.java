package com.datn.financeapp.module.icon.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.entity.Icon;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import com.datn.financeapp.module.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
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
                .orElse(new IconResponse());
    }

    @Override
    @Transactional(readOnly = true)
    public IconResponse findActiveByCode(String code) {
        return iconRepository
                .findByCodeAndIsActiveTrue(code)
                .map(iconMapper::toIconResponse)
                .orElse(new IconResponse());
    }

    @Override
    @Transactional(readOnly = true)
    public IconResponse findActiveById(@NonNull UUID id) {

        return iconRepository.findByIdAndIsActiveTrue(id)
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
