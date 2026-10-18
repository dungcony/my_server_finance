package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
    private final IconService iconService;


    @Override
    public Optional<WalletSumaryRes> findSumaryByIdAndUser(UUID walletId, UUID userId, boolean includeDeleted) {
        return walletRepository.findByIdAndUserId(walletId, userId)
                .filter(wallet -> includeDeleted || !wallet.getIsDeleted())
                .map(wallet -> walletMapper.toSumary(wallet, iconService.findIconByCode(wallet.getIconCode())));
    }

}
