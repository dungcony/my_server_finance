package com.datn.financeapp.module.transaction.controller;

import com.datn.financeapp.module.transaction.service.TxnBehavierService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller quản lý các thao tác truy vấn, tạo mới, cập nhật, xóa và nhân bản giao dịch.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TxnBehavierService txnBehavierService;

}
