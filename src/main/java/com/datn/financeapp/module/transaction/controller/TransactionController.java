package com.datn.financeapp.module.transaction.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.transaction.dto.request.BulkCreateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.response.BulkCreateTransactionResponse;
import com.datn.financeapp.module.transaction.dto.request.CreateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.response.CreateTransactionResponse;
import com.datn.financeapp.module.transaction.dto.response.DeleteTransactionResponse;
import com.datn.financeapp.module.transaction.dto.request.DuplicateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.response.TransactionByDateResponse;
import com.datn.financeapp.module.transaction.dto.response.TransactionDetailResponse;
import com.datn.financeapp.module.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.module.transaction.dto.response.TransactionListResponse;
import com.datn.financeapp.module.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.module.transaction.service.TransactionBulkService;
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
    private final TransactionBulkService transactionBulkService;

    /**
     * Lấy danh sách giao dịch có phân trang và bộ lọc.
     *
     * @param fromDate         ngày bắt đầu
     * @param toDate           ngày kết thúc
     * @param period           chu kỳ thời gian (month, week, year)
     * @param type             loại giao dịch (income, expense, transfer)
     * @param walletId         ID ví
     * @param categoryId       ID danh mục
     * @param source           nguồn giao dịch (manual, auto, bot)
     * @param countsInReport   lọc theo việc có tính vào báo cáo hay không
     * @param search           từ khóa tìm kiếm theo tên hoặc ghi chú
     * @param minAmount        số tiền tối thiểu
     * @param maxAmount        số tiền tối đa
     * @param includeTransfers bao gồm giao dịch chuyển tiền hay không
     * @param page             số trang
     * @param pageSize         kích thước trang
     * @param sortBy           trường sắp xếp
     * @param sortOrder        thứ tự sắp xếp (asc, desc)
     * @return {@link TransactionListResponse} chứa danh sách phân trang
     */
    @GetMapping
    public TransactionListResponse list(
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String type,
            @RequestParam(name = "wallet_id", required = false) UUID walletId,
            @RequestParam(name = "category_id", required = false) UUID categoryId,
            @RequestParam(required = false) String source,
            @RequestParam(name = "counts_in_report", required = false) Boolean countsInReport,
            @RequestParam(required = false) String search,
            @RequestParam(name = "min_amount", required = false) Long minAmount,
            @RequestParam(name = "max_amount", required = false) Long maxAmount,
            @RequestParam(name = "include_transfers", required = false) Boolean includeTransfers,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_order", required = false) String sortOrder) {
        UUID userId = SecurityContextUtil.currentUserId();
        TransactionFilterRequest filters = new TransactionFilterRequest(
                fromDate,
                toDate,
                period,
                type,
                walletId,
                categoryId,
                source,
                countsInReport,
                search,
                minAmount,
                maxAmount,
                includeTransfers);
        PageRequestParams pageParams =
                PageRequestParams.of(page, pageSize, sortBy != null ? sortBy : "date", sortOrder);
        return transactionService.list(userId, filters, pageParams);
    }

    /**
     * Lấy danh sách giao dịch gom nhóm theo ngày.
     *
     * @param fromDate         ngày bắt đầu
     * @param toDate           ngày kết thúc
     * @param period           chu kỳ thời gian
     * @param type             loại giao dịch
     * @param walletId         ID ví
     * @param categoryId       ID danh mục
     * @param source           nguồn giao dịch
     * @param countsInReport   lọc theo việc có tính vào báo cáo hay không
     * @param search           từ khóa tìm kiếm
     * @param minAmount        số tiền tối thiểu
     * @param maxAmount        số tiền tối đa
     * @param includeTransfers bao gồm giao dịch chuyển tiền hay không
     * @return {@link ApiResponse} chứa {@link TransactionByDateResponse}
     */
    @GetMapping("/by-date")
    public ApiResponse<TransactionByDateResponse> listByDate(
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String type,
            @RequestParam(name = "wallet_id", required = false) UUID walletId,
            @RequestParam(name = "category_id", required = false) UUID categoryId,
            @RequestParam(required = false) String source,
            @RequestParam(name = "counts_in_report", required = false) Boolean countsInReport,
            @RequestParam(required = false) String search,
            @RequestParam(name = "min_amount", required = false) Long minAmount,
            @RequestParam(name = "max_amount", required = false) Long maxAmount,
            @RequestParam(name = "include_transfers", required = false) Boolean includeTransfers) {
        UUID userId = SecurityContextUtil.currentUserId();
        TransactionFilterRequest filters = new TransactionFilterRequest(
                fromDate,
                toDate,
                period,
                type,
                walletId,
                categoryId,
                source,
                countsInReport,
                search,
                minAmount,
                maxAmount,
                includeTransfers);
        return ApiResponse.of(transactionService.listByDate(userId, filters));
    }

    /**
     * Lấy thông tin chi tiết của một giao dịch theo ID.
     *
     * @param id ID giao dịch
     * @return {@link ApiResponse} chứa {@link TransactionDetailResponse}
     */
    @GetMapping("/{id}")
    public ApiResponse<TransactionDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.detail(userId, id));
    }

    /**
     * Tạo mới một giao dịch đơn lẻ.
     *
     * @param req thông tin yêu cầu tạo giao dịch
     * @return {@link ApiResponse} chứa {@link CreateTransactionResponse}
     */
    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateTransactionResponse> create(@Valid @RequestBody CreateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.create(userId, req));
    }

    /**
     * Tạo nhiều giao dịch cùng một lúc (hàng loạt).
     *
     * @param req thông tin yêu cầu tạo hàng loạt giao dịch
     * @return {@link ApiResponse} chứa {@link BulkCreateTransactionResponse}
     */
    @PostMapping("/bulk")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BulkCreateTransactionResponse> createBulk(
            @Valid @RequestBody BulkCreateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionBulkService.createBulk(userId, req));
    }

    /**
     * Cập nhật thông tin giao dịch hiện có.
     *
     * @param id  ID giao dịch cần sửa
     * @param req thông tin cập nhật giao dịch
     * @return {@link ApiResponse} chứa {@link CreateTransactionResponse}
     */
    @PutMapping("/{id}")
    public ApiResponse<CreateTransactionResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.update(userId, id, req));
    }

    /**
     * Xóa mềm một giao dịch.
     *
     * @param id ID giao dịch cần xóa
     * @return {@link ApiResponse} chứa {@link DeleteTransactionResponse}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<DeleteTransactionResponse> delete(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.delete(userId, id));
    }

    /**
     * Nhân bản một giao dịch có sẵn sang giao dịch mới.
     *
     * @param id  ID giao dịch mẫu
     * @param req thông tin thay đổi khi nhân bản (tùy chọn)
     * @return {@link ApiResponse} chứa {@link CreateTransactionResponse}
     */
    @PostMapping("/{id}/duplicate")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateTransactionResponse> duplicate(
            @PathVariable UUID id, @RequestBody(required = false) DuplicateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.duplicate(userId, id, req));
    }
}
