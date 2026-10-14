package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.wallet.dto.request.CreateWalletRequest;
import com.datn.financeapp.module.wallet.dto.response.WalletDetailResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletSummaryResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WalletBehavierService {

    /**
     * Lấy danh sách ví tài chính của người dùng theo bộ lọc.
     *
     * @param userId        ID người dùng
     * @param type          loại ví cần lọc (tùy chọn)
     * @param onlyInTotal   có chỉ tính ví nằm trong tổng số dư hay không (tùy chọn)
     * @param includeShared có bao gồm ví chia sẻ hay không
     * @return danh sách phản hồi thông tin ví
     */
    List<WalletResponse> getMyWallets(UUID userId, String type, Boolean onlyInTotal, boolean includeShared);

    /**
     * Tạo mới một ví tài chính.
     *
     * @param userId ID người dùng
     * @param req    thông tin tạo ví
     * @return DTO thông tin ví vừa tạo
     */
    WalletResponse create(UUID userId, CreateWalletRequest req);

    /**
     * Tổng quan số dư tài chính của người dùng.
     *
     * @param userId ID người dùng
     * @return DTO tổng quan ví
     */
    WalletSummaryResponse summary(UUID userId);

    /**
     * Lấy thông tin chi tiết của một ví.
     *
     * @param userId   ID người dùng
     * @param walletId ID ví
     * @return DTO chi tiết ví
     * @throws BusinessException nếu không tìm thấy ví
     */
    WalletDetailResponse detail(UUID userId, UUID walletId);

}
