package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interface định nghĩa các dịch vụ nghiệp vụ liên quan đến quản lý ví tài chính.
 */
public interface WalletService {

    /**
     * Lấy id ví mặc định của người dùng, nơi nhận các khoản tự ghi từ giao dịch nhóm.
     *
     * @param userId ID người dùng
     * @return ID ví mặc định
     */
    WalletRes findDefaultWallet(UUID userId);

    /**
     * Lấy ví của user
     *
     * @param walletId ví
     * @param userId   user
     * @return thông tin cơ bản để hiển thị
     */
    WalletSumaryRes findSumary(UUID walletId, UUID userId);

    /**
     * Kiểm tra ví có phải của user k
     *
     * @param userId   id user
     * @param walletId id ví
     * @return true nếu là ví của bản thân
     */
    boolean myWallet(UUID userId, UUID walletId);
}
