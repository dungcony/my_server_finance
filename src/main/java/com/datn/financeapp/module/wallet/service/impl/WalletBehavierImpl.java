package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletListRes;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletBehavierImpl implements WalletBehavierService {

    private final WalletRepository walletRepository;
    private final IconService iconService;
    private final WalletMapper walletMapper;


    @Override
    public WalletListRes gets(UUID userId, Pageable pageable) {

        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        List<WalletSumaryRes> res = new ArrayList<>();

        Page<Wallet> wallets = walletRepository.findAllByUserId(userId, pageable);

        for (Wallet wallet : wallets) {
            if (wallet.getIconId() != null)
                res.add(walletMapper.toSumary(wallet, iconService.findActiveById(wallet.getIconId()).pathData()));
        }

        return new WalletListRes(
                res,
                PageMeta.from(wallets)
        );
    }

    @Override
    public WalletListRes getAll(Pageable pageable) {

        List<WalletSumaryRes> res = new ArrayList<>();

        Page<Wallet> wallets = walletRepository.findAll(pageable);

        for (Wallet wallet : wallets) {
            if (wallet.getIconId() != null)
                res.add(walletMapper.toSumary(wallet, iconService.findActiveById(wallet.getIconId()).pathData()));
        }

        return new WalletListRes(
                res,
                PageMeta.from(wallets)
        );
    }

    @Override
    public WalletRes get(UUID userId, UUID walletId) {
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        Wallet wallet = walletRepository.findByIdAndUserIdAndIsDeletedFalse(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "không tìm thấy ví"));

        return walletMapper.toResponse(wallet, iconService.findActiveById(wallet.getIconId()).pathData());
    }

    @Override
    @Transactional
    public WalletRes create(UUID userId, WalletCreateReq req) {
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        if (walletRepository.existsByUserIdAndNameIgnoreCaseAndTypeAndIsDeletedFalse(userId, req.name(), req.type()))
            throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);

        Wallet wallet = walletMapper.toEntity(req);
        String iconData = "";
        if (req.iconId() != null)
            iconData = iconService.findActiveById(req.iconId()).pathData();

        walletRepository.save(wallet);

        return walletMapper.toResponse(wallet, iconData);
    }


    @Override
    @Transactional
    public WalletRes update(UUID userId, UUID walletId, WalletupdateReq req) {
        
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        String iconData = "";

        Wallet wallet = walletRepository.findByIdAndUserIdAndIsDeletedFalse(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));

        if (req.name() != null)
            wallet.setName(req.name());
        if (req.balance() != null)
            wallet.setBalance(req.balance());

        if (req.iconId() != null) {
            iconData = iconService.findActiveById(req.iconId()).pathData();
            wallet.setIconId(req.iconId());
        }

        if (req.color() != null)
            wallet.setColor(req.color());

        walletRepository.save(wallet);

        return walletMapper.toResponse(wallet, iconData);
    }


    @Override
    @Transactional
    public Void softDelete(UUID userId, UUID walletId) {

        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        Wallet wallet = walletRepository.findByIdAndUserIdAndIsDeletedFalse(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));

        if (wallet.getIsDefault() == true)
            throw new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_DELETABLE);

        wallet.setIsDeleted(true);
        walletRepository.save(wallet);

        return null;
    }

}
