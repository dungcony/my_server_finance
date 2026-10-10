package com.datn.financeapp.module.transaction.service.impl;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.datn.financeapp.module.budget.service.BudgetSpendService;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.module.transaction.dto.request.CreateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.request.DuplicateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.module.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.mapper.TransactionMapper;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.transaction.service.TransactionService;
import com.datn.financeapp.module.transaction.helper.TransactionWriter;
import com.datn.financeapp.module.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

/**
 * Service xử lý các yêu cầu nghiệp vụ liên quan đến giao dịch tài chính đơn lẻ.
 */
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final BudgetSpendService budgetSpendService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionWriter transactionWriter;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionMapper transactionMapper;

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

}
