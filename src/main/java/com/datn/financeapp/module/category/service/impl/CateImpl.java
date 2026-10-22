package com.datn.financeapp.module.category.service.impl;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CateImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final IconService iconService;
    private final CateMapper cateMapper;


    @Override
    public Optional<CateSumaryRes> findByCodeAndIsDeletedAndIsLeaf(String code, boolean includeDeleted, boolean isLeaf) {

        if (code == null || code.isEmpty())
            return Optional.empty();

        var cate = categoryRepository.findByCode(code)
                .orElse(null);

        if (cate == null)
            return Optional.empty();

        if (!includeDeleted && cate.getIsDeleted())
            return Optional.empty();

        if (isLeaf && !cate.getIsLeaf())
            return Optional.empty();

        return Optional.of(cateMapper.toSumary(cate, iconService.findIconByCode(cate.getIconCode())));
    }


    //-------------------------- PRIVATE -------------------------//

}
