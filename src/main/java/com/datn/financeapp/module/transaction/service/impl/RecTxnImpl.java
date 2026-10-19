package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.transaction.dto.response.RecTxnRes;
import com.datn.financeapp.module.transaction.mapper.RecMapper;
import com.datn.financeapp.module.transaction.repository.RecTxnRepository;
import com.datn.financeapp.module.transaction.service.RecTxnService;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecTxnImpl implements RecTxnService {

    private final RecTxnRepository recTxnRepository;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final RecMapper recMapper;

    @Override
    public RecTxnRes getById(UUID recId, boolean includeDeleted) {

        return recTxnRepository.findById(recId)
                .filter(r -> includeDeleted || r.getIsDeleted() == false)
                .map(r -> recMapper.toDetalRes(r, null, null))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch định kỳ."));
    }
}
