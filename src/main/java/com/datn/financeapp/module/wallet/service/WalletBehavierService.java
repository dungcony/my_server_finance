package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;

import java.util.List;
import java.util.UUID;

public interface WalletBehavierService {

    /**
     * Lấy danh sách ví tài chính của người dùng theo bộ lọc.
     *
     * @return danh sách phản hồi thông tin ví
     */
    List<WalletRes> gets();

    /**
     * Tạo mới một ví tài chính.
     *
     * @param req thông tin tạo ví
     * @return DTO thông tin ví vừa tạo
     */
    WalletRes create(WalletCreateReq req);

    /**
     * Cập nhật thông tin ví.
     *
     * @param walletId ID ví
     * @param req      DTO cập nhật ví
     * @return DTO thông tin ví sau cập nhật
     */
    WalletRes update(UUID walletId, WalletupdateReq req);

    /**
     * Xóa ví tài chính (xóa mềm). Xóa ví sẽ xóa luôn các giao dịch cùng ví
     *
     * @param walletId ID ví
     */
    Void softDelete(UUID walletId);
}
