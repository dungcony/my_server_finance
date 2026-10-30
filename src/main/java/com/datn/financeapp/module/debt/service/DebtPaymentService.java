package com.datn.financeapp.module.debt.service;

import com.datn.financeapp.module.debt.dto.request.DebtPaymentCreateReq;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentFilterReq;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentUpdateReq;
import com.datn.financeapp.module.debt.dto.response.DebtPaymentListRes;
import com.datn.financeapp.module.debt.dto.response.DebtPaymentRes;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DebtPaymentService extends UpdateService<DebtPaymentUpdateReq, DebtPaymentRes>,
        DeleteService {
    DebtPaymentRes create(UUID debtId, DebtPaymentCreateReq req, boolean isAdmin);

    DebtPaymentListRes gets(UUID debtId, DebtPaymentFilterReq req, Pageable pageable);

}
