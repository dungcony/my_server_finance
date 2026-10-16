package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public WalletRes findDefaultWallet(UUID userId) {
        return walletRepository.findByUserIdAndIsDefaultTrue(userId)
                .map(w -> walletMapper.toResponse(w, ""))
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_FOUND));
    }

    @Override
    public WalletSumaryRes findSumary(UUID walletId, UUID userId) {
        return walletRepository.findByIdAndUserIdAndIsDeletedFalse(walletId, userId)
                .map(w -> walletMapper.toSumary(w, ""))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));
    }

    @Override
    public WalletSumaryRes findSumary(@NonNull UUID walletId) {
        return walletRepository.findById(walletId)
                .map(w -> walletMapper.toSumary(w, ""))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Override
    public WalletRes findIfMyWallet(@NonNull UUID userId, UUID walletId) {
        var wallet = walletRepository.findByIdAndUserIdAndIsDeletedFalse(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // icon chỉ để hiển thị: ví chưa chọn icon hoặc icon đã bị tắt thì để rỗng, không chặn giao dịch
        IconResponse icon = wallet.getIconId() == null ? null
                : iconService.findActiveByIds(List.of(wallet.getIconId())).get(wallet.getIconId());

        return walletMapper.toResponse(wallet, icon != null ? icon.pathData() : "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void reconcileAll() {
        int fixed = walletRepository.reconcileAll();
        log.info("Đối chiếu số dư: đã sửa {} ví bị lệch", fixed);
    }
}
