package com.datn.financeapp.module.icon.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.icon.dto.request.IconCreateReq;
import com.datn.financeapp.module.icon.dto.request.IconUpdateReq;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.entity.Icon;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import com.datn.financeapp.module.icon.service.IconBehavierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Triển khai các thao tác nghiệp vụ biểu tượng với cơ sở dữ liệu.
 */
@Service
@RequiredArgsConstructor
public class IconBehavierImpl implements IconBehavierService {

    private final IconRepository iconRepository;
    private final IconMapper iconMapper;

    @Override
    @Transactional
    public IconResponse create(IconCreateReq req) {
        if (iconRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.ICON_CODE_EXISTS);
        }
        Icon icon = Icon.builder()
                .id(UUID.randomUUID())
                .code(req.code())
                .displayName(req.displayName())
                .pathData(req.pathData())
                .isActive(true)
                .build();

        iconRepository.save(icon);
        return iconMapper.toIconResponse(icon);
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<IconResponse> getAll(Pageable pageable) {
        return iconRepository.findByIsActiveTrue(pageable)
                .map(iconMapper::toIconResponse);
    }

    @Override
    @Transactional
    public IconResponse update(UUID id, IconUpdateReq req) {
        Icon icon = iconRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ICON_NOT_FOUND));

        if (req.displayName() != null) {
            icon.setDisplayName(req.displayName());
        }
        if (req.pathData() != null) {
            icon.setPathData(req.pathData());
        }
        if (req.isActive() != null) {
            icon.setIsActive(req.isActive());
        }
        return iconMapper.toIconResponse(icon);
    }

    @Override
    @Transactional
    public IconResponse delete(UUID id) {
        Icon icon = iconRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ICON_NOT_FOUND));
        icon.setIsActive(false);
        return iconMapper.toIconResponse(icon);
    }
}
