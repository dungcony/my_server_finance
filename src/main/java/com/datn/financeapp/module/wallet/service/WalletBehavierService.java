package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletFillterReq;
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
     * Tạo mới một ví tài chính.
     *
     * @param req thông tin tạo ví
     * @return DTO thông tin ví vừa tạo
     */
    WalletRes create(WalletCreateReq req, boolean isAdmin);

    /**
     * Lấy danh sách ví tài chính của người dùng theo bộ lọc
     *
     * @param filter  thông tin fillter
     * @param isAdmin đánh dấu người gọi là user hay admin/manager
     * @return danh sách phản hồi thông tin ví
     */
    WalletListRes gets(WalletFillterReq filter, Pageable pageable, boolean isAdmin);

    /**
     * Lấy thông tin chi tiết của 1 ví
     *
     * @param walletId id vi
     * @param isAdmin  người gọi là admin
     */
    WalletRes get(UUID walletId, boolean isAdmin);

    /**
     * Cập nhật thông tin ví.
     *
     * @param walletId ID ví
     * @param req      DTO cập nhật ví
     * @return DTO thông tin ví sau cập nhật
     */
    WalletRes update(UUID walletId, WalletupdateReq req, boolean isAdmin);

    /**
     * Xóa ví tài chính (xóa mềm). Các giao dịch của ví được xoá mềm theo bằng trigger
     * {@code trg_wallets_soft_delete_cascade} , số dư ví tự về 0. Không xoá được ví mặc định.
     *
     * @param walletId ID ví
     */
    Void softDelete(UUID walletId, boolean isAdmin);
}
