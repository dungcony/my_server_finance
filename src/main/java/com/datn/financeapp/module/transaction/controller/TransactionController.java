package com.datn.financeapp.module.transaction.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.transaction.dto.request.BulkCreateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.request.CreateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.request.DuplicateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.module.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.module.transaction.service.TransactionService;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller quản lý các thao tác truy vấn, tạo mới, cập nhật, xóa và nhân bản giao dịch.
 * Tương tác với {@link TransactionService} cho các giao dịch đơn lẻ và {@link TransactionBulkService} cho thao tác hàng loạt.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

}
