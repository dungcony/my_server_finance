package com.datn.financeapp.module.wallet.dto.request;

import com.datn.financeapp.module.wallet.enums.WalletType;
import org.springframework.web.bind.annotation.BindParam;

import java.time.Instant;
import java.util.UUID;

/**
 * Tham số lọc dùng chung cho truy vấn danh sách ví.
 *
 * @param search      từ khoá tìm trong tên ví
 * @param type        loại ví (CASH, BANK, E_WALLET)
 * @param isDefault   chỉ lấy ví mặc định / không mặc định
 * @param minBalance  số dư tối thiểu
 * @param maxBalance  số dư tối đa
 * @param createdFrom tạo từ thời điểm (bao gồm)
 * @param createdTo   tạo trước thời điểm (không bao gồm)
 * @param isDeleted   trạng thái xoá mềm, null thì lấy cả hai;
 * @param userId      chủ ví, null thì không lọc, user thường k cho truyeefn vs @param
 */
public record WalletFillterReq(
        String search,
        WalletType type,
        @BindParam("is_default") Boolean isDefault,
        @BindParam("min_balance") Long minBalance,
        @BindParam("max_balance") Long maxBalance,
        @BindParam("created_from") Instant createdFrom,
        @BindParam("created_to") Instant createdTo,
        @BindParam("is_deleted") Boolean isDeleted,
        @BindParam("user_id") UUID userId) {

    public WalletFillterReq withUser(UUID userId) {
        return new WalletFillterReq(search, type, isDefault, minBalance, maxBalance, createdFrom, createdTo, false, userId);
    }
}
