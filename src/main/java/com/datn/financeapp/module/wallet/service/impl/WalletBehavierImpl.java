package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        Page<Wallet> wallets = walletRepository.findAllByUserIdAndIsDeleted(userId, false, pageable);

        return new WalletListRes(
                toSumaries(wallets),
                PageMeta.from(wallets)
        );
    }

    @Override
    public WalletListRes getAll(Pageable pageable) {

        Page<Wallet> wallets = walletRepository.findAll(pageable);

        return new WalletListRes(
                toSumaries(wallets),
                PageMeta.from(wallets)
        );
    }

    @Override
    public WalletRes get(UUID userId, UUID walletId) {
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        Wallet wallet = walletRepository.findByIdAndUserIdAndIsDeletedFalse(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "không tìm thấy ví"));

        return walletMapper.toResponse(wallet, iconPathOf(wallet.getIconId()));
    }

    @Override
    @Transactional
    public WalletRes create(UUID userId, WalletCreateReq req) {
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        if (walletRepository.existsByUserIdAndNameAndIsDeletedFalse(userId, req.name()))
            throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);

        Wallet wallet = walletMapper.toEntity(req);
        wallet.setUserId(userId);
        wallet.setId(UUID.randomUUID());

        String icon = wallet.getIconId() == null ? "" : iconService.findActiveById(wallet.getIconId()).pathData();

        walletRepository.save(wallet);

        return walletMapper.toResponse(wallet, icon);
    }


    @Override
    @Transactional
    public WalletRes update(UUID userId, UUID walletId, WalletupdateReq req) {

        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        String icon;

        Wallet wallet = walletRepository.findByIdAndUserIdAndIsDeletedFalse(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));

        if (req.name() != null) {
            boolean isRename = !req.name().equals(wallet.getName());

            if (isRename && walletRepository.existsByUserIdAndNameAndIsDeletedFalse(userId, req.name()))
                throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);

            wallet.setName(req.name());
        }

        // chọn icon mới thì phải còn bật (findActiveById báo ICON_NOT_FOUND), giữ icon cũ thì chỉ hiển thị
        if (req.iconId() != null) {
            icon = iconService.findActiveById(req.iconId()).pathData();
            wallet.setIconId(req.iconId());
        } else
            icon = iconPathOf(wallet.getIconId());

        if (req.color() != null)
            wallet.setColor(req.color());

        walletRepository.save(wallet);

        return walletMapper.toResponse(wallet, icon);
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

    // icon chỉ để hiển thị: ví chưa chọn icon hoặc icon đã bị tắt thì trả rỗng, không báo lỗi
    private String iconPathOf(UUID iconId) {
        if (iconId == null)
            return "";

        return pathOf(iconService.findActiveByIds(List.of(iconId)).get(iconId));
    }

    // đổi một trang ví sang danh sách tóm tắt, lấy icon của cả trang trong một lần gọi
    private List<WalletSumaryRes> toSumaries(Page<Wallet> wallets) {
        Map<UUID, IconResponse> icons = iconService.findActiveByIds(
                wallets.stream().map(Wallet::getIconId).filter(Objects::nonNull).toList());

        return wallets.stream()
                .map(wallet -> walletMapper.toSumary(wallet, pathOf(icons.get(wallet.getIconId()))))
                .toList();
    }

    private String pathOf(IconResponse icon) {
        return icon != null ? icon.pathData() : "";
    }

}
