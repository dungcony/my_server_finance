package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletFillterReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletListRes;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.helper.WalletSpecifial;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletBehavierImpl implements WalletBehavierService {

    private final WalletRepository walletRepository;
    private final IconService iconService;
    private final WalletMapper walletMapper;

    @Override
    @Transactional
    public WalletRes create(WalletCreateReq req, boolean isAdmin) {

        Wallet wallet = walletMapper.toEntity(req);
        if (!isAdmin) wallet.setUserId(SecurityContextUtil.currentUserId());
        else if (req.userId() == null)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn người dùng.");
        wallet.setId(UUID.randomUUID());

        String icon = iconService.findByCode(wallet.getIconCode(), false)
                .map(IconRes::pathData)
                .orElseThrow(() -> new BusinessException(ErrorCode.ICON_NOT_FOUND));

        if (walletRepository.insertIfNameNotExist(wallet) < 1)
            throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);

        return walletMapper.toResponse(wallet, icon);
    }


    @Override
    public WalletListRes gets(WalletFillterReq filter, Pageable pageable, boolean isAdmin) {

        // không phải admin thì lấy theo userId
        if (!isAdmin)
            filter = filter.withUser(SecurityContextUtil.currentUserId());

        Specification<Wallet> spec = WalletSpecifial.filter(filter);
        Page<Wallet> wallets = walletRepository.findAll(spec, pageable);
        var icons = iconService.findIconsByCodes(wallets.getContent().stream().map(Wallet::getIconCode).toList());

        List<WalletSumaryRes> res = wallets.getContent().stream()
                .map(w -> walletMapper.toSumary(w, icons.get(w.getIconCode())))
                .toList();

        return new WalletListRes(
                res,
                PageMeta.from(wallets)
        );
    }

    @Override
    public WalletRes get(UUID walletId, boolean isAdmin) {
        Wallet wallet;
        if (isAdmin)
            wallet = walletRepository.findById(walletId).orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        else {
            UUID userId = SecurityContextUtil.currentUserId();

            wallet = walletRepository.findByIdAndUserIdAndIsDeleted(walletId, userId, false)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "không tìm thấy ví"));
        }
        return walletMapper.toResponse(wallet, iconService.findIconByCode(wallet.getIconCode()));
    }


    // 4 roup-trip : get wallet -> check icon -> update wallet -> geticon
    @Override
    @Transactional
    public WalletRes update(UUID walletId, WalletupdateReq req, boolean isAdmin) {

        Wallet wallet = walletRepository.findByIdAndIsDeletedFalse(walletId).orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        // nếu không phải admin thì người thay đổi phải là user của ví
        if (!isAdmin && !wallet.getUserId().equals(SecurityContextUtil.currentUserId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được phép cập nhật thông tin ví này");

        if (req.name() != null && !req.name().equals(wallet.getName())) {
            if (walletRepository.existsByUserIdAndNameAndIsDeletedFalse(wallet.getUserId(), req.name()))
                throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);
            wallet.setName(req.name());
        }
        // Nếu có yêu cầu đổi icon + icon khác cũ + icon là active thì đổi
        if (req.iconCode() != null && !req.iconCode().equals(wallet.getIconCode())) {
            if (iconService.findByCode(req.iconCode(), false).isEmpty())
                throw new BusinessException(ErrorCode.ICON_NOT_FOUND);
            wallet.setIconCode(req.iconCode());
        }

        if (req.color() != null) wallet.setColor(req.color());

        walletRepository.save(wallet);

        return walletMapper.toResponse(wallet, iconService.findIconByCode(wallet.getIconCode()));
    }


    @Override
    @Transactional
    public Void softDelete(UUID walletId, boolean isAdmin) {

        Wallet wallet = walletRepository.findByIdAndIsDeletedFalse(walletId).orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        // nếu không phải admin thì người thay đổi phải là user của ví
        if (!isAdmin && !wallet.getUserId().equals(SecurityContextUtil.currentUserId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được phép xoá ví này");

        if (wallet.getIsDefault() == true)
            throw new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_DELETABLE);

        wallet.setIsDeleted(true);
        walletRepository.save(wallet);
        return null;
    }

}
