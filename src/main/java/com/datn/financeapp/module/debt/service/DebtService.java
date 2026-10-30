package com.datn.financeapp.module.debt.service;

import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.debt.dto.request.DebtCreateReq;
import com.datn.financeapp.module.debt.dto.request.DebtFilterReq;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentCreateReq;
import com.datn.financeapp.module.debt.dto.request.DebtUpdateReq;
import com.datn.financeapp.module.debt.dto.response.DebtListRes;
import com.datn.financeapp.module.debt.dto.response.DebtRes;
import com.datn.financeapp.module.goal.dto.request.SavingGoalFilterReq;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý khoản nợ/cho vay, ghi nhận thanh toán, xóa nợ xấu và gửi thông báo nhắc nợ.
 * <p>
 */
public interface DebtService extends CreateService<DebtCreateReq, DebtRes>,
        UpdateService<DebtUpdateReq, DebtRes>,
        DeleteService {
    DebtRes create(UUID userId, DebtCreateReq req);

    default DebtRes create(DebtCreateReq req) {
        return create(SecurityContextUtil.currentUserId(), req);
    }

    DebtRes get(UUID id);

    DebtListRes gets(UUID userId, DebtFilterReq req, Pageable pageable);

    /**
     * user tự lọc
     */
    default DebtListRes gets(DebtFilterReq filter, Pageable pageable) {
        return gets(SecurityContextUtil.currentUserId(), filter, pageable);
    }

    void sendDueReminders();
}
