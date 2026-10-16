package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.module.wallet.dto.response.ReconcilListRes;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Đọc lịch sử đối chiếu số dư ví. Lịch sử chỉ do job đối chiếu ghi ({@code WalletRepository.reconcileAll}),
 * không sửa/xoá.
 */
public interface ReconcilHistoryService {

    /**
     * Lịch sử đối chiếu của mọi ví, dành cho admin.
     *
     * @param pageable phân trang
     * @return danh sách lịch sử
     */
    ReconcilListRes gets(Pageable pageable);

    /**
     * Lịch sử đối chiếu của một ví, ví phải thuộc người dùng.
     *
     * @param userId   ID người dùng, null thì lấy người đang đăng nhập
     * @param walletId ID ví
     * @param pageable phân trang
     * @return danh sách lịch sử
     */
    ReconcilListRes getsByWallet(UUID userId, UUID walletId, Pageable pageable);
}
