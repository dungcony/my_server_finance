package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    public Map<UUID, WalletSumaryRes> findSummaryMapByIds(Set<UUID> walletIds) {
        if (walletIds == null || walletIds.isEmpty()) {
            return Map.of();
        }

        // Query danh sách ví 1 lần duy nhất bằng IN
        List<Wallet> wallets = walletRepository.findAllById(walletIds);
        if (wallets.isEmpty()) {
            return Map.of();
        }

        // Batch query lấy icon cho tất cả các ví (tránh N+1 query icon)
        List<String> iconCodes = wallets.stream()
                .map(Wallet::getIconCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        
        Map<String, String> icons = iconService.findIconsByCodes(iconCodes);

        // Chuyển đổi thành Map<UUID, WalletSumaryRes>
        return wallets.stream()
                .collect(Collectors.toMap(
                        Wallet::getId,
                        w -> walletMapper.toSumary(w, icons.get(w.getIconCode()))
                ));
    }

}
