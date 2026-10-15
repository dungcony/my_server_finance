package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletListRes;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * userId là id của người dùng người dùng bth sẽ truyền null và service sẽ dùng security để lấy còn admin thì sẽ truyền user cố định vào
 */
public interface WalletBehavierService {

    /**
     * Lấy danh sách ví tài chính của người dùng theo bộ lọc.
     *
     * @return danh sách phản hồi thông tin ví
     */
    WalletListRes gets(UUID userId, Pageable pageable);

    WalletListRes getAll(Pageable pageable);

    WalletRes get(UUID userId, UUID walletId);

    /**
     * Tạo mới một ví tài chính.
     *
     * @param req thông tin tạo ví
     * @return DTO thông tin ví vừa tạo
     */
    WalletRes create(UUID userId, WalletCreateReq req);

    /**
     * Cập nhật thông tin ví.
     *
     * @param walletId ID ví
     * @param req      DTO cập nhật ví
     * @return DTO thông tin ví sau cập nhật
     */
    WalletRes update(UUID userId, UUID walletId, WalletupdateReq req);

    /**
     * Xóa ví tài chính (xóa mềm). Xóa ví sẽ xóa luôn các giao dịch cùng ví
     *
     * @param walletId ID ví
     */
    Void softDelete(UUID userId, UUID walletId);
}
