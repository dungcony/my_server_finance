package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.module.transaction.dto.response.ReconcilListRes;
import com.datn.financeapp.module.wallet.dto.response.ReconcileRes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReconcilHistoryService {
    ReconcilListRes gets(Pageable pageable);
}
