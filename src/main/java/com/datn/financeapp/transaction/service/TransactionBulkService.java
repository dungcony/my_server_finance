package com.datn.financeapp.transaction.service;

import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.transaction.dto.BulkCreateTransactionResponse;
import com.datn.financeapp.transaction.dto.CreateTransactionRequest;
import com.datn.financeapp.transaction.dto.TransactionListItemResponse;
import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.transaction.dto.BulkCreateTransactionRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * {@code POST /transactions/bulk} (TXN-04, D-34/D-35/D-35a). KHÔNG {@code @Transactional} ở bất
 * kỳ method nào của class này — mấu chốt để mỗi lời gọi {@code transactionWriter.write(...)}
 * bên trong vòng lặp {@link #createBulk} tự mở một transaction RIÊNG qua Spring AOP proxy thật
 * của {@link TransactionWriter} (bean khác), đạt D-34 "mỗi dòng một DB transaction riêng" mà
 * KHÔNG cần {@code propagation = REQUIRES_NEW} tường minh.
 *
 * <p>{@code @Idempotent} (D-35) chỉ bọc NGOÀI ở tầng Controller — method này không mang
 * {@code @Transactional} nên {@code IdempotencyAspect} không mở transaction bao trùm cả lô
 * (D-35a).
 */
@Service
@RequiredArgsConstructor
public class TransactionBulkService {

    private static final int MAX_ROWS = 50;

    private final TransactionWriter transactionWriter;
    private final TransactionService transactionService;
    private final CategoryRepository categoryRepository;

    /**
     * Xử lý tối đa {@value #MAX_ROWS} dòng. Vượt hạn mức -> {@code TOO_MANY_ROWS} 400 NGAY ĐẦU,
     * không xử lý dòng nào (T-03-11). Mỗi dòng validate quyền wallet/category giống hệt
     * {@code TransactionService.create()} — không có đường tắt bỏ qua kiểm tra quyền vì đang ở
     * trong vòng lặp bulk (T-03-12).
     */
    public BulkCreateTransactionResponse createBulk(UUID userId, BulkCreateTransactionRequest req) {
        if (req.items().size() > MAX_ROWS) {
            throw new BusinessException(
                    "TOO_MANY_ROWS",
                    HttpStatus.BAD_REQUEST.value(),
                    "Tối đa " + MAX_ROWS + " giao dịch mỗi lần gọi.");
        }

        List<TransactionListItemResponse> saved = new ArrayList<>();
        List<BulkCreateTransactionResponse.RowError> errors = new ArrayList<>();
        Map<UUID, Long> latestBalances = new LinkedHashMap<>();

        for (int i = 0; i < req.items().size(); i++) {
            CreateTransactionRequest item = req.items().get(i);
            try {
                TransactionWriter.WriteResult result = processRow(userId, item);

                Transaction persisted =
                        transactionService.findPersistedOrThrow(result.transactionId());
                saved.add(transactionService.buildListItemResponse(persisted));

                latestBalances.put(item.walletId(), result.walletNewBalance());
                if (result.destinationWalletNewBalance() != null) {
                    latestBalances.put(item.destinationWalletId(), result.destinationWalletNewBalance());
                }
            } catch (BusinessException ex) {
                // Lỗi dữ liệu có chủ đích (validate) — ghi vào row_errors, KHÔNG chặn dòng khác
                // (D-34).
                errors.add(new BulkCreateTransactionResponse.RowError(i, ex.getCode(), ex.getMessage()));
            } catch (DataAccessException ex) {
                // Lỗi hệ thống thật sự (VD FK constraint vi phạm vì wallet_id không tồn tại) —
                // dòng này TRƯỚC đã tự commit ở transaction riêng, dòng này thất bại không cuốn
                // theo dòng trước hay dòng sau (D-35a). KHÔNG để crash toàn bộ request.
                errors.add(new BulkCreateTransactionResponse.RowError(i, "INTERNAL_ERROR", "Lỗi hệ thống khi ghi dòng này."));
            }
        }

        List<BulkCreateTransactionResponse.NewBalanceItem> newBalance = latestBalances.entrySet().stream()
                .map(e -> new BulkCreateTransactionResponse.NewBalanceItem(e.getKey(), e.getValue()))
                .toList();

        return new BulkCreateTransactionResponse(saved.size(), errors.size(), saved, errors, newBalance);
    }

    /**
     * Validate y hệt {@link TransactionService#create} (dùng lại {@code validateShape}, KHÔNG
     * copy-paste), rồi ghi qua {@link TransactionWriter#write} — đây là lời gọi cross-bean mở
     * transaction riêng cho dòng này.
     */
    private TransactionWriter.WriteResult processRow(UUID userId, CreateTransactionRequest item) {
        transactionService.validateShape(item.type(), item.categoryId(), item.destinationWalletId(), item.walletId());

        if (item.amount() == null || item.amount() <= 0) {
            throw new BusinessException(
                    "INVALID_AMOUNT", HttpStatus.BAD_REQUEST.value(), "Số tiền phải lớn hơn 0.");
        }

        if (item.categoryId() != null) {
            Category category = categoryRepository
                    .findByIdAndVisibleToUser(item.categoryId(), userId)
                    .orElseThrow(() -> new BusinessException(
                            "CATEGORY_NOT_ALLOWED", HttpStatus.BAD_REQUEST.value(), "Danh mục không hợp lệ."));
            if (!category.getType().equals(item.type())) {
                throw new BusinessException(
                        "CATEGORY_TYPE_MISMATCH",
                        HttpStatus.BAD_REQUEST.value(),
                        "Danh mục thu gán cho khoản chi hoặc ngược lại.");
            }
        }

        LocalDate date = item.date() != null ? item.date() : LocalDate.now();
        return transactionWriter.write(new TransactionWriteCommand(
                UUID.randomUUID(),
                userId,
                item.walletId(),
                item.destinationWalletId(),
                item.categoryId(),
                item.type(),
                item.amount(),
                date,
                item.note(),
                item.displayName(),
                item.source() != null ? item.source() : "manual",
                item.countsInReport() == null || item.countsInReport(),
                null,
                item.draftId(),
                item.receiptUrl()));
    }
}
