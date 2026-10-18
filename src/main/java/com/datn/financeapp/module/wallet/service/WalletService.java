package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface định nghĩa các dịch vụ nghiệp vụ liên quan đến quản lý ví tài chính.
 */
public interface WalletService {
    /**
     * Lấy ví của user, nếu ví đã bị xoá mềm th trả null
     *
     * @param walletId       ví
     * @param userId         user nếu null thì tức là admin xem nên không cần check có thuọc user kh
     * @param includeDeleted false: chỉ lấy ví chưa xoá; true: lấy cả ví đã xoá
     * @return thông tin cơ bản để hiển thị
     */
    Optional<WalletSumaryRes> findSumaryByIdAndUser(UUID walletId, UUID userId, boolean includeDeleted);

}
