package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;

    private final IconService iconService;


    @Override
    @Transactional(readOnly = true)
    public WalletRes findDefaultWallet(UUID userId) {
        return walletRepository.findByUserIdAndIsDefaultTrue(userId)
                .map(w -> walletMapper.toResponse(w, ""))
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_FOUND));
    }

    @Override
    public WalletSumaryRes findSumary(UUID walletId, UUID userId) {
        return walletRepository.findByUserIdAndIsDefaultTrue(userId)
                .map(w -> walletMapper.toSumary(w, ""))
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_FOUND));
    }

    @Override
    public boolean myWallet(UUID userId, UUID walletId) {
        return walletRepository.findByUserIdAndIsDefaultTrue(userId).isPresent();
    }
}
