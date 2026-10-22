package com.datn.financeapp.module.icon.service.impl;

import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import com.datn.financeapp.module.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IconImpl implements IconService {

    private final IconRepository iconRepository;
    private final IconMapper iconMapper;


    @Override
    public Optional<IconRes> findByCode(String code, boolean includeDeleted) {
        if (!includeDeleted)
            return iconRepository.findByCodeAndIsDeletedFalse(code)
                    .map(iconMapper::toIconResponse);

        return iconRepository.findByCode(code)
                .map(iconMapper::toIconResponse);
    }

    @Override
    public String findIconByCode(String code) {

        if (code == null)
            return "";

        var icon = iconRepository.findByCodeAndIsDeletedFalse(code)
                .orElse(null);

        if (icon == null)
            return "";

        return icon.getPathData();
    }

    @Override
    public Map<String, String> findIconsByCodes(List<String> codes) {
        Map<String, String> res = new HashMap<>();

        if (codes == null)
            return res;

        List<String> remaining = new ArrayList<>(new LinkedHashSet<>(codes));

        iconRepository.findAllByCodeInAndIsDeleted(remaining, false)
                .forEach(i -> {
                    res.put(i.getCode(), i.getPathData());
                    remaining.remove(i.getCode());
                });

        remaining.forEach(code -> res.put(code, ""));


        return res;
    }
}
