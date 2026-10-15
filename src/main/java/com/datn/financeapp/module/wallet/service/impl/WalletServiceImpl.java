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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
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
    public WalletSumaryRes findSumary(UUID walletId) {
        return walletRepository.findByIdAndIsDefaultTrue(walletId)
                .map(w -> walletMapper.toSumary(w, ""))
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_FOUND));
    }

    @Override
    public WalletRes findIfMyWallet(UUID userId, UUID walletId) {
        var wallet = walletRepository.findByUserIdAndIsDefaultTrue(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return walletMapper.toResponse(wallet, iconService.findActiveById(wallet.getIconId()).pathData());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reconcileAll() {
        List<UUID> allWalletIds = walletRepository.findAllIds();
        for (UUID walletId : allWalletIds) {
            try {
                walletRepository.reconcil(walletId);
            } catch (Exception e) {
                log.error("Lỗi khi đối chiếu ví {}", walletId, e);
            }
        }
    }
}
