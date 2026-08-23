package com.datn.financeapp.transaction.service;

import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.transaction.dto.CreateTransactionRequest;
import com.datn.financeapp.transaction.dto.CreateTransactionResponse;
import com.datn.financeapp.transaction.dto.DeleteTransactionResponse;
import com.datn.financeapp.transaction.dto.DuplicateTransactionRequest;
import com.datn.financeapp.transaction.dto.TransactionResponse;
import com.datn.financeapp.transaction.dto.UpdateTransactionRequest;
import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.transaction.repository.TransactionRepository;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD giao dịch đơn lẻ (TXN-03, TXN-05, TXN-06, TXN-07) — module lõi rủi ro cao nhất của dự án.
 * {@code create()}/{@code duplicate()} ghi bản ghi mới qua {@link TransactionWriter} (D-31, điểm
 * ghi duy nhất). {@code update()}/{@code delete()} sửa TẠI CHỖ nên gọi trực tiếp
 * {@link TransactionRepository#save} + {@link WalletRepository#adjustBalance} trong CÙNG một
 * {@code @Transactional} — {@code TransactionWriter.write()} luôn INSERT bản ghi mới, không phù
 * hợp cho UPDATE tại chỗ.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionWriter transactionWriter;

    /**
     * api/04-GIAO-DICH.md mục 4. Validate ràng buộc theo {@code type} Ở TẦNG SERVICE TRƯỚC KHI
     * chạm CSDL — không dựa vào {@code ck_txn_shape}/trigger để báo lỗi nghiệp vụ (constraint DB
     * là lưới an toàn cuối). KHÔNG chặn ngày tương lai (D-36) — không có bất kỳ
     * {@code if (date.isAfter(...))} nào ở đây.
     */
    @Transactional
    public CreateTransactionResponse create(UUID userId, CreateTransactionRequest req) {
        validateShape(req.type(), req.categoryId(), req.destinationWalletId(), req.walletId());

        if (req.amount() == null || req.amount() <= 0) {
            throw new BusinessException(
                    "INVALID_AMOUNT", HttpStatus.BAD_REQUEST.value(), "Số tiền phải lớn hơn 0.");
        }

        if ("transfer".equals(req.type())) {
            lockWalletsInOrder(Set.of(req.walletId(), req.destinationWalletId()), userId);
        } else {
            lockWalletsInOrder(Set.of(req.walletId()), userId);
        }

        Category category = null;
        if (req.categoryId() != null) {
            category = categoryRepository
                    .findByIdAndVisibleToUser(req.categoryId(), userId)
                    .orElseThrow(() -> new BusinessException(
                            "CATEGORY_NOT_ALLOWED", HttpStatus.BAD_REQUEST.value(), "Danh mục không hợp lệ."));
            if (!category.getType().equals(req.type())) {
                throw new BusinessException(
                        "CATEGORY_TYPE_MISMATCH",
                        HttpStatus.BAD_REQUEST.value(),
                        "Danh mục thu gán cho khoản chi hoặc ngược lại.");
            }
        }

        LocalDate date = req.date() != null ? req.date() : LocalDate.now();
        TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                UUID.randomUUID(),
                userId,
                req.walletId(),
                req.destinationWalletId(),
                req.categoryId(),
                req.type(),
                req.amount(),
                date,
                req.note(),
                req.displayName(),
                req.source() != null ? req.source() : "manual",
                req.countsInReport() == null || req.countsInReport(),
                null,
                req.draftId(),
                req.receiptUrl()));

        Transaction saved = transactionRepository.findById(result.transactionId()).orElseThrow();
        return new CreateTransactionResponse(
                toResponse(saved),
                new CreateTransactionResponse.NewBalance(req.walletId(), result.walletNewBalance()));
    }

    /**
     * api/04-GIAO-DICH.md mục 8 — trình tự 3 bước bất di bất dịch (CLAUDE.md §4), toàn bộ trong
     * MỘT {@code @Transactional}: hoàn tác ảnh hưởng CŨ → ghi giá trị MỚI → áp dụng ảnh hưởng
     * MỚI. Khoá tối đa 4 ví (D-33) theo {@code UUID.compareTo()} tăng dần, loại trùng trước khi
     * khoá.
     */
    @Transactional
    public CreateTransactionResponse update(UUID userId, UUID transactionId, UpdateTransactionRequest req) {
        Transaction old = transactionRepository
                .findByIdAndUserIdAndIsDeletedFalse(transactionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy giao dịch."));

        validateShape(req.type(), req.categoryId(), req.destinationWalletId(), req.walletId());

        if (req.amount() == null || req.amount() <= 0) {
            throw new BusinessException(
                    "INVALID_AMOUNT", HttpStatus.BAD_REQUEST.value(), "Số tiền phải lớn hơn 0.");
        }

        Set<UUID> walletIds = new HashSet<>();
        walletIds.add(old.getWalletId());
        if (old.getDestinationWalletId() != null) {
            walletIds.add(old.getDestinationWalletId());
        }
        walletIds.add(req.walletId());
        if (req.destinationWalletId() != null) {
            walletIds.add(req.destinationWalletId());
        }
        lockWalletsInOrder(walletIds, userId);

        if (req.categoryId() != null) {
            Category category = categoryRepository
                    .findByIdAndVisibleToUser(req.categoryId(), userId)
                    .orElseThrow(() -> new BusinessException(
                            "CATEGORY_NOT_ALLOWED", HttpStatus.BAD_REQUEST.value(), "Danh mục không hợp lệ."));
            if (!category.getType().equals(req.type())) {
                throw new BusinessException(
                        "CATEGORY_TYPE_MISMATCH",
                        HttpStatus.BAD_REQUEST.value(),
                        "Danh mục thu gán cho khoản chi hoặc ngược lại.");
            }
        }

        // Bước 1 — HOÀN TÁC ảnh hưởng CŨ lên (các) ví CŨ.
        applyEffect(old.getType(), old.getWalletId(), old.getDestinationWalletId(), old.getAmount(), true);

        // Bước 2 — GHI giá trị MỚI vào bản ghi (cùng id, không tạo entity mới).
        old.setType(req.type());
        old.setAmount(req.amount());
        old.setWalletId(req.walletId());
        old.setDestinationWalletId(req.destinationWalletId());
        old.setCategoryId(req.categoryId());
        old.setDate(req.date() != null ? req.date() : LocalDate.now());
        old.setNote(req.note());
        old.setDisplayName(req.displayName());
        if (req.receiptUrl() != null) {
            old.setReceiptUrl(req.receiptUrl());
        }
        if (req.countsInReport() != null) {
            old.setCountsInReport(req.countsInReport());
        }
        old.setUpdatedAt(Instant.now());
        transactionRepository.save(old);

        // Bước 3 — ÁP DỤNG ảnh hưởng MỚI lên (các) ví MỚI.
        applyEffect(req.type(), req.walletId(), req.destinationWalletId(), req.amount(), false);

        long walletNewBalance = walletRepository.findCurrentBalanceNative(req.walletId()).orElseThrow();

        Transaction reloaded = transactionRepository.findById(transactionId).orElseThrow();
        return new CreateTransactionResponse(
                toResponse(reloaded),
                new CreateTransactionResponse.NewBalance(req.walletId(), walletNewBalance));
    }

    /**
     * api/04-GIAO-DICH.md mục 9 (D-32). Chặn TRƯỚC khi hoàn tác số dư — không có đường nào bỏ
     * qua kiểm tra {@code existsDebtPaymentLink}. TUYỆT ĐỐI KHÔNG ghi
     * {@code debts.paid_amount}/{@code debts.status} — cột do trigger V4 sở hữu.
     */
    @Transactional
    public DeleteTransactionResponse delete(UUID userId, UUID transactionId) {
        Transaction txn = transactionRepository
                .findByIdAndUserIdAndIsDeletedFalse(transactionId, userId)
                .orElse(null);

        if (txn == null) {
            // Phân biệt "đã xoá idempotent" (200, CORE-06) với "không tồn tại/của người khác"
            // (404) — copy khuôn WalletService.delete().
            Transaction existing = transactionRepository
                    .findByIdAndUserId(transactionId, userId)
                    .orElseThrow(() -> new BusinessException(
                            "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy giao dịch."));
            // Đã xoá mềm từ trước — idempotent, không làm gì thêm, không ném lỗi.
            long balance = walletRepository.findCurrentBalanceNative(existing.getWalletId()).orElseThrow();
            return new DeleteTransactionResponse(
                    new DeleteTransactionResponse.NewBalance(existing.getWalletId(), balance));
        }

        if (transactionRepository.existsDebtPaymentLink(transactionId)) {
            throw new BusinessException(
                    "TRANSACTION_LINKED_TO_DEBT",
                    HttpStatus.CONFLICT.value(),
                    "Giao dịch này là một lần trả nợ — huỷ ở sổ nợ, không xoá trực tiếp.");
        }

        Set<UUID> walletIds = new HashSet<>();
        walletIds.add(txn.getWalletId());
        if (txn.getDestinationWalletId() != null) {
            walletIds.add(txn.getDestinationWalletId());
        }
        lockWalletsInOrder(walletIds, userId);

        applyEffect(txn.getType(), txn.getWalletId(), txn.getDestinationWalletId(), txn.getAmount(), true);

        txn.setIsDeleted(true);
        txn.setUpdatedAt(Instant.now());
        transactionRepository.save(txn);

        long walletNewBalance = walletRepository.findCurrentBalanceNative(txn.getWalletId()).orElseThrow();
        return new DeleteTransactionResponse(
                new DeleteTransactionResponse.NewBalance(txn.getWalletId(), walletNewBalance));
    }

    /**
     * api/04-GIAO-DICH.md mục 10 (TXN-07). Ngày mặc định HÔM NAY (không phải ngày gốc),
     * {@code source} LUÔN {@code "manual"} (không kế thừa nguồn gốc).
     */
    @Transactional
    public CreateTransactionResponse duplicate(UUID userId, UUID transactionId, DuplicateTransactionRequest req) {
        Transaction original = transactionRepository
                .findByIdAndUserIdAndIsDeletedFalse(transactionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy giao dịch."));

        LocalDate date = (req != null && req.date() != null) ? req.date() : LocalDate.now();
        Long amount = (req != null && req.amount() != null) ? req.amount() : original.getAmount();

        TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                UUID.randomUUID(),
                userId,
                original.getWalletId(),
                original.getDestinationWalletId(),
                original.getCategoryId(),
                original.getType(),
                amount,
                date,
                original.getNote(),
                original.getDisplayName(),
                "manual",
                original.getCountsInReport(),
                null,
                null,
                original.getReceiptUrl()));

        Transaction saved = transactionRepository.findById(result.transactionId()).orElseThrow();
        return new CreateTransactionResponse(
                toResponse(saved),
                new CreateTransactionResponse.NewBalance(original.getWalletId(), result.walletNewBalance()));
    }

    /**
     * Validate ràng buộc theo {@code type} (đối chiếu {@code ck_txn_shape} — db/migration/
     * V2__giao_dich.sql): category bắt buộc/rỗng, destination bắt buộc/rỗng theo {@code type}.
     *
     * <p>package-private: also called by TransactionBulkService (plan 03-04) to validate each
     * bulk row without duplicating logic.
     */
    void validateShape(String type, UUID categoryId, UUID destinationWalletId, UUID walletId) {
        if ("transfer".equals(type)) {
            if (destinationWalletId == null) {
                throw new BusinessException(
                        "DESTINATION_WALLET_REQUIRED",
                        HttpStatus.BAD_REQUEST.value(),
                        "Giao dịch chuyển phải có ví đích.");
            }
            if (categoryId != null) {
                throw new BusinessException(
                        "CATEGORY_NOT_ALLOWED",
                        HttpStatus.BAD_REQUEST.value(),
                        "Giao dịch chuyển không được có danh mục.");
            }
            if (walletId != null && walletId.equals(destinationWalletId)) {
                throw new BusinessException(
                        "SAME_SOURCE_AND_DESTINATION", HttpStatus.BAD_REQUEST.value(), "Hai ví phải khác nhau.");
            }
        } else {
            if (categoryId == null) {
                throw new BusinessException(
                        "CATEGORY_REQUIRED", HttpStatus.BAD_REQUEST.value(), "Chi/thu phải có danh mục.");
            }
            if (destinationWalletId != null) {
                throw new BusinessException(
                        "DESTINATION_WALLET_NOT_ALLOWED",
                        HttpStatus.BAD_REQUEST.value(),
                        "Giao dịch chi/thu không được có ví đích.");
            }
        }
    }

    /**
     * Khoá tất cả ví liên quan theo thứ tự {@code UUID.compareTo()} tăng dần (D-33, chống
     * deadlock — mở rộng nguyên thuật toán từ {@code WalletTransferService} 2 ví lên tối đa 4
     * ví), kiểm tra quyền sở hữu ngay sau khi khoá từng ví.
     */
    private Map<UUID, Wallet> lockWalletsInOrder(Set<UUID> walletIds, UUID userId) {
        List<UUID> sortedIds = new ArrayList<>(walletIds);
        sortedIds.sort(UUID::compareTo);

        Map<UUID, Wallet> lockedWallets = new HashMap<>();
        for (UUID walletId : sortedIds) {
            Wallet wallet = walletRepository
                    .findByIdForUpdate(walletId)
                    .orElseThrow(() -> new BusinessException(
                            "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));
            if (!userId.equals(wallet.getUserId())) {
                throw new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví.");
            }
            lockedWallets.put(walletId, wallet);
        }
        return lockedWallets;
    }

    /**
     * Áp dụng (hoặc hoàn tác, nếu {@code reverse=true}) ảnh hưởng của một giao dịch lên (các) ví
     * liên quan — dùng chung cho bước 1 (hoàn tác CŨ) và bước 3 (áp dụng MỚI) của
     * {@link #update}, cũng như bước hoàn tác của {@link #delete}.
     */
    private void applyEffect(
            String type, UUID walletId, UUID destinationWalletId, long amount, boolean reverse) {
        switch (type) {
            case "expense" -> walletRepository.adjustBalance(walletId, reverse ? amount : -amount);
            case "income" -> walletRepository.adjustBalance(walletId, reverse ? -amount : amount);
            case "transfer" -> {
                walletRepository.adjustBalance(walletId, reverse ? amount : -amount);
                walletRepository.adjustBalance(destinationWalletId, reverse ? -amount : amount);
            }
            default -> throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + type);
        }
    }

    private TransactionResponse toResponse(Transaction txn) {
        Wallet wallet = walletRepository.findById(txn.getWalletId()).orElse(null);
        Wallet destinationWallet = txn.getDestinationWalletId() != null
                ? walletRepository.findById(txn.getDestinationWalletId()).orElse(null)
                : null;
        Category category = txn.getCategoryId() != null
                ? categoryRepository.findById(txn.getCategoryId()).orElse(null)
                : null;

        return new TransactionResponse(
                txn.getId(),
                txn.getType(),
                txn.getAmount(),
                txn.getDate(),
                txn.getDisplayName(),
                txn.getNote(),
                txn.getSource(),
                wallet != null ? new TransactionResponse.WalletRef(wallet.getId(), wallet.getName(), wallet.getType()) : null,
                destinationWallet != null
                        ? new TransactionResponse.WalletRef(
                                destinationWallet.getId(), destinationWallet.getName(), destinationWallet.getType())
                        : null,
                category != null
                        ? new TransactionResponse.CategoryRef(
                                category.getId(), category.getName(), category.getType(), category.getParentCategoryId())
                        : null,
                txn.getReceiptUrl(),
                txn.getRecurringId(),
                txn.getDraftId(),
                txn.getCreatedAt(),
                txn.getUpdatedAt());
    }
}
