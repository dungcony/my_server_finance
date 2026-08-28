package com.datn.financeapp.debt.service;

import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.debt.dto.CreateDebtRequest;
import com.datn.financeapp.debt.dto.CreateDebtResponse;
import com.datn.financeapp.debt.dto.CreatePaymentRequest;
import com.datn.financeapp.debt.dto.CreatePaymentResponse;
import com.datn.financeapp.debt.dto.DebtDetailResponse;
import com.datn.financeapp.debt.dto.DebtListItemResponse;
import com.datn.financeapp.debt.dto.DebtSummaryResponse;
import com.datn.financeapp.debt.dto.UpdateDebtRequest;
import com.datn.financeapp.debt.dto.WriteOffRequest;
import com.datn.financeapp.debt.entity.Debt;
import com.datn.financeapp.debt.entity.DebtPayment;
import com.datn.financeapp.debt.repository.DebtPaymentRepository;
import com.datn.financeapp.debt.repository.DebtRepository;
import com.datn.financeapp.notification.repository.NotificationRepository;
import com.datn.financeapp.transaction.repository.TransactionRepository;
import com.datn.financeapp.transaction.service.TransactionService;
import com.datn.financeapp.transaction.service.TransactionWriteCommand;
import com.datn.financeapp.transaction.service.TransactionWriter;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic sổ nợ (DEBT-01..07, api/08-SO-NO.md).
 *
 * <p><b>Hai nguyên tắc chi phối toàn bộ lớp này:</b>
 *
 * <p>1. <b>KHÔNG viết lại logic cập nhật số dư ví.</b> Mọi giao dịch sinh ra từ sổ nợ đều đi qua
 * {@link TransactionWriter} (D-31), mọi lần hoàn tác đều đi qua {@link TransactionService#delete}
 * — nơi đã cài đúng luồng 3 bước của CLAUDE.md quy tắc 4. Lớp này không được tự gọi
 * {@code walletRepository.adjustBalance}.
 *
 * <p>2. <b>KHÔNG ghi paid_amount / status của bảng debts.</b> Hai cột do trigger
 * {@code trg_debt_payments_sync} (V4) sở hữu. Backend chỉ chèn/xoá bản ghi {@code debt_payments};
 * muốn biết giá trị mới thì ĐỌC LẠI bằng {@link DebtRepository#findByIdNative}. Ngoại lệ DUY NHẤT
 * là {@link #writeOff} — được api/08 mục 7 cho phép tường minh, và trigger có nhánh giữ nguyên
 * trạng thái {@code written_off} nên giá trị đó không bị ghi đè về sau.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DebtService {

    private static final String TYPE_LENDING = "lending";

    private static final String STATUS_OUTSTANDING = "outstanding";
    private static final String STATUS_SETTLED = "settled";
    private static final String STATUS_WRITTEN_OFF = "written_off";

    /** api/08 mục 2 — due_soon lấy các khoản còn dưới 7 ngày là tới hạn hoặc đã quá hạn. */
    private static final int DUE_SOON_DAYS = 7;

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final CategoryRepository categoryRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionWriter transactionWriter;
    private final TransactionService transactionService;
    private final NotificationRepository notificationRepository;
    private final DebtReminderWorker debtReminderWorker;

    // ---------------------------------------------------------------------
    // Ghi
    // ---------------------------------------------------------------------

    /**
     * DEBT-01, api/08 mục 4. Trong MỘT transaction: sinh giao dịch gốc qua {@link TransactionWriter}
     * rồi tạo bản ghi nợ trỏ vào giao dịch đó.
     *
     * <p>Thứ tự bắt buộc là giao dịch TRƯỚC, khoản nợ SAU — cột {@code origin_transaction_id} là
     * NOT NULL có khoá ngoại ON DELETE RESTRICT, không thể chèn khoản nợ khi chưa có giao dịch để
     * trỏ tới.
     */
    @Transactional
    public CreateDebtResponse create(UUID userId, CreateDebtRequest req) {
        if (req.principalAmount() == null || req.principalAmount() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", HttpStatus.BAD_REQUEST.value(), "Số tiền phải lớn hơn 0.");
        }

        LocalDate issuedDate = req.issuedDate() != null ? req.issuedDate() : LocalDate.now();
        if (req.dueDate() != null && req.dueDate().isBefore(issuedDate)) {
            throw new BusinessException(
                    "INVALID_DUE_DATE", HttpStatus.BAD_REQUEST.value(), "Hạn trả không được trước ngày phát sinh.");
        }

        Wallet wallet = walletRepository
                .findByIdForUser(req.walletId(), userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));

        // lending = đưa tiền đi -> giao dịch CHI danh mục "Cho vay".
        // borrowing = nhận tiền về -> giao dịch THU danh mục "Đi vay".
        boolean lending = TYPE_LENDING.equals(req.type());
        String txnType = lending ? "expense" : "income";
        UUID categoryId = systemCategoryId(lending ? "Cho vay" : "Đi vay", txnType);

        TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                null,
                userId,
                wallet.getId(),
                null,
                categoryId,
                txnType,
                req.principalAmount(),
                issuedDate,
                req.note(),
                req.counterpartyName(),
                // ck_txn_source chỉ cho phép manual|text|ocr|auto|adjustment — KHÔNG có giá trị
                // riêng cho sổ nợ. Giao dịch này vẫn là "manual" vì người dùng chủ động tạo qua form.
                "manual",
                true,
                null,
                null,
                null));

        Debt debt = Debt.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .walletId(wallet.getId())
                .type(req.type())
                .counterpartyName(req.counterpartyName())
                .principalAmount(req.principalAmount())
                // Đây là giá trị KHỞI TẠO của bản ghi mới, không phải ghi đè cột do trigger sở
                // hữu: chưa có bản ghi debt_payments nào nên trigger chưa từng chạy cho khoản này.
                .paidAmount(0L)
                .issuedDate(issuedDate)
                .dueDate(req.dueDate())
                .note(req.note())
                .status(STATUS_OUTSTANDING)
                .originTransactionId(result.transactionId())
                .createdAt(Instant.now())
                .build();
        debtRepository.save(debt);

        return new CreateDebtResponse(
                toListItem(debt, wallet, 0),
                new CreateDebtResponse.OriginTransaction(result.transactionId(), txnType, req.principalAmount()),
                new CreateDebtResponse.NewBalance(wallet.getId(), result.walletNewBalance()));
    }

    /**
     * DEBT-03, api/08 mục 5. Chặn D-47 chạy TRƯỚC khi chạm tới trigger, để trả lỗi 400 sạch sẽ với
     * thông điệp gợi ý cách xử lý — thay vì để trigger RAISE EXCEPTION bắn lên thành lỗi CSDL thô.
     * Trigger vẫn giữ vai trò lưới an toàn ở tầng dưới (hai tầng phòng thủ).
     */
    @Transactional
    public CreatePaymentResponse addPayment(UUID userId, UUID debtId, CreatePaymentRequest req) {
        Debt debt = loadOwnedDebt(userId, debtId);

        if (STATUS_WRITTEN_OFF.equals(debt.getStatus())) {
            throw new BusinessException(
                    "DEBT_WRITTEN_OFF", HttpStatus.CONFLICT.value(), "Khoản nợ đã đánh dấu không đòi nữa.");
        }
        if (STATUS_SETTLED.equals(debt.getStatus())) {
            throw new BusinessException("DEBT_ALREADY_SETTLED", HttpStatus.CONFLICT.value(), "Khoản nợ đã trả xong.");
        }

        long remaining = debt.getPrincipalAmount() - debt.getPaidAmount();
        if (req.amount() > remaining) {
            throw new BusinessException(
                    "EXCEEDS_REMAINING_AMOUNT",
                    HttpStatus.BAD_REQUEST.value(),
                    String.format(
                            "%s chỉ còn nợ %,d đ. Ghi trả %,d đ, phần dư ghi thành khoản thu riêng.",
                            debt.getCounterpartyName(), remaining, remaining));
        }

        UUID walletId = req.walletId() != null ? req.walletId() : debt.getWalletId();
        Wallet wallet = walletRepository
                .findByIdForUser(walletId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));

        // Khoản lending ĐƯỢC trả -> tiền về ví, giao dịch THU danh mục "Thu nợ".
        // Khoản borrowing MÌNH trả -> tiền rời ví, giao dịch CHI danh mục "Trả nợ".
        boolean lending = TYPE_LENDING.equals(debt.getType());
        String txnType = lending ? "income" : "expense";
        UUID categoryId = systemCategoryId(lending ? "Thu nợ" : "Trả nợ", txnType);

        LocalDate paidDate = req.paidDate() != null ? req.paidDate() : LocalDate.now();

        TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                null,
                userId,
                wallet.getId(),
                null,
                categoryId,
                txnType,
                req.amount(),
                paidDate,
                req.note(),
                debt.getCounterpartyName(),
                "manual",
                true,
                null,
                null,
                null));

        DebtPayment payment = DebtPayment.builder()
                .id(UUID.randomUUID())
                .debtId(debtId)
                .transactionId(result.transactionId())
                .amount(req.amount())
                .paidDate(paidDate)
                .note(req.note())
                .createdAt(Instant.now())
                .build();
        // saveAndFlush: đẩy INSERT xuống CSDL NGAY để trigger chạy trước khi ta đọc lại phía dưới.
        debtPaymentRepository.saveAndFlush(payment);

        // BẮT BUỘC đọc lại: trigger vừa UPDATE bảng debts ở tầng CSDL trong cùng transaction,
        // nhưng instance debt phía trên vẫn mang paid_amount/status CŨ trong Hibernate identity map.
        // Đọc bằng query trả SCALAR — trả entity sẽ bị identity map trả lại instance cũ, xem
        // Javadoc DebtRepository.findPaidAmountNative.
        long refreshedPaidAmount = debtRepository.findPaidAmountNative(debtId).orElseThrow();
        String refreshedStatus = debtRepository.findStatusNative(debtId).orElseThrow();

        return new CreatePaymentResponse(
                new CreatePaymentResponse.Payment(
                        payment.getId(), payment.getAmount(), payment.getPaidDate(), payment.getNote()),
                new CreatePaymentResponse.DebtProgress(
                        refreshedPaidAmount,
                        debt.getPrincipalAmount() - refreshedPaidAmount,
                        refreshedStatus),
                new CreatePaymentResponse.TransactionSummary(result.transactionId(), txnType, req.amount()),
                new CreatePaymentResponse.NewBalance(wallet.getId(), result.walletNewBalance()));
    }

    /**
     * DEBT-04, api/08 mục 6 (D-46) — THỨ TỰ BẮT BUỘC, không đảo:
     *
     * <ol>
     *   <li>Xoá bản ghi trả nợ, trigger tự trừ lại paid_amount và tự MỞ LẠI settled -&gt; outstanding
     *   <li>Xoá mềm giao dịch qua {@link TransactionService#delete} để hoàn tác số dư ví đúng luồng
     *       3 bước
     * </ol>
     *
     * <p>Xoá bản ghi trả nợ TRƯỚC còn có tác dụng phụ cần thiết: {@code TransactionService.delete}
     * chặn 409 với giao dịch còn gắn sổ nợ (D-32). Vì bản ghi đã biến mất ở bước 1, kiểm tra đó
     * trả false và xoá mềm chạy bình thường.
     */
    @Transactional
    public void cancelPayment(UUID userId, UUID debtId, UUID paymentId) {
        loadOwnedDebt(userId, debtId);

        DebtPayment payment = debtPaymentRepository
                .findByIdAndDebtId(paymentId, debtId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy lần trả nợ."));

        UUID transactionId = payment.getTransactionId();

        // Bước 1 — flush ngay để trigger chạy trước khi ta đụng tới giao dịch.
        debtPaymentRepository.delete(payment);
        debtPaymentRepository.flush();

        // Bước 2 — hoàn tác ví qua đúng luồng 3 bước có sẵn, KHÔNG tự viết lại.
        transactionService.delete(userId, transactionId);
    }

    /**
     * DEBT-05, api/08 mục 7. NGOẠI LỆ DUY NHẤT được ghi thẳng cột status — trigger cố ý giữ nguyên
     * trạng thái written_off nên giá trị này không bị ghi đè về sau.
     *
     * <p>KHÔNG sinh giao dịch mới: tiền đã rời ví từ lúc cho vay, ghi thêm sẽ trừ hai lần.
     */
    @Transactional
    public DebtListItemResponse writeOff(UUID userId, UUID debtId, WriteOffRequest req) {
        Debt debt = loadOwnedDebt(userId, debtId);

        if (STATUS_WRITTEN_OFF.equals(debt.getStatus())) {
            throw new BusinessException(
                    "DEBT_WRITTEN_OFF", HttpStatus.CONFLICT.value(), "Khoản nợ đã đánh dấu không đòi nữa.");
        }

        debt.setStatus(STATUS_WRITTEN_OFF);
        if (req != null && req.reason() != null && !req.reason().isBlank()) {
            // Schema V4 không có cột riêng cho lý do — nối vào note để không mất thông tin.
            String note = debt.getNote() == null || debt.getNote().isBlank()
                    ? req.reason()
                    : debt.getNote() + "\n" + req.reason();
            debt.setNote(note);
        }
        debtRepository.save(debt);

        return toListItem(debt, loadWalletOrNull(userId, debt.getWalletId()), countPayments(debtId));
    }

    /** DEBT-02, api/08 mục 8 — chỉ ba trường counterparty_name, due_date, note sửa được. */
    @Transactional
    public DebtListItemResponse update(UUID userId, UUID debtId, UpdateDebtRequest req) {
        Debt debt = loadOwnedDebt(userId, debtId);

        if (req.counterpartyName() != null) {
            debt.setCounterpartyName(req.counterpartyName());
        }
        if (req.dueDate() != null) {
            if (req.dueDate().isBefore(debt.getIssuedDate())) {
                throw new BusinessException(
                        "INVALID_DUE_DATE",
                        HttpStatus.BAD_REQUEST.value(),
                        "Hạn trả không được trước ngày phát sinh.");
            }
            debt.setDueDate(req.dueDate());
        }
        if (req.note() != null) {
            debt.setNote(req.note());
        }
        debtRepository.save(debt);

        return toListItem(debt, loadWalletOrNull(userId, debt.getWalletId()), countPayments(debtId));
    }

    /**
     * DEBT-06, api/08 mục 8 (D-48). Xoá CỨNG bản ghi nợ (schema V4 không có cột is_deleted), hoàn
     * tác MỀM toàn bộ giao dịch liên quan — giao dịch gốc VÀ mọi giao dịch trả nợ.
     *
     * <p>Thứ tự quan trọng: đọc danh sách lần trả TRƯỚC khi xoá bản ghi nợ, vì ON DELETE CASCADE
     * sẽ cuốn theo chúng. Xoá bản ghi nợ trước khi gọi {@code transactionService.delete} cũng là
     * điều kiện để kiểm tra D-32 không chặn 409.
     */
    @Transactional
    public void delete(UUID userId, UUID debtId) {
        Debt debt = loadOwnedDebt(userId, debtId);

        List<DebtPayment> payments = debtPaymentRepository.findByDebtId(debtId);
        List<UUID> paymentTransactionIds =
                payments.stream().map(DebtPayment::getTransactionId).toList();
        UUID originTransactionId = debt.getOriginTransactionId();

        // Xoá CỨNG bản ghi nợ -> cascade dọn debt_payments. Flush ngay để không còn bản ghi nào
        // trỏ tới giao dịch khi ta xoá mềm chúng phía dưới.
        debtRepository.delete(debt);
        debtRepository.flush();

        for (UUID transactionId : paymentTransactionIds) {
            transactionService.delete(userId, transactionId);
        }
        transactionService.delete(userId, originTransactionId);
    }

    // ---------------------------------------------------------------------
    // Đọc
    // ---------------------------------------------------------------------

    /** api/08 mục 1. isOverdue lọc ở Java vì là số dẫn xuất từ due_date so với hôm nay. */
    @Transactional(readOnly = true)
    public List<DebtListItemResponse> list(UUID userId, String type, String status, Boolean isOverdue) {
        List<Debt> debts = debtRepository.findAllForUser(userId, type, status);
        Map<UUID, Wallet> wallets = loadWallets(userId, debts);

        List<DebtListItemResponse> result = new ArrayList<>();
        for (Debt debt : debts) {
            DebtListItemResponse item =
                    toListItem(debt, wallets.get(debt.getWalletId()), countPayments(debt.getId()));
            if (isOverdue == null || isOverdue == item.isOverdue()) {
                result.add(item);
            }
        }
        return result;
    }

    /** api/08 mục 3 — chi tiết kèm lịch sử trả và giao dịch gốc. */
    @Transactional(readOnly = true)
    public DebtDetailResponse detail(UUID userId, UUID debtId) {
        Debt debt = loadOwnedDebt(userId, debtId);

        List<DebtPayment> payments = debtPaymentRepository.findByDebtIdOrderByPaidDateDesc(debtId);
        List<DebtDetailResponse.PaymentHistoryItem> history = payments.stream()
                .map(p -> new DebtDetailResponse.PaymentHistoryItem(
                        p.getId(), p.getAmount(), p.getPaidDate(), p.getNote(), p.getTransactionId()))
                .toList();

        DebtDetailResponse.OriginTransaction origin = transactionRepository
                .findById(debt.getOriginTransactionId())
                .map(t -> new DebtDetailResponse.OriginTransaction(t.getId(), t.getAmount(), t.getDate()))
                .orElse(null);

        return new DebtDetailResponse(
                toListItem(debt, loadWalletOrNull(userId, debt.getWalletId()), payments.size()), history, origin);
    }

    /**
     * api/08 mục 2. Chỉ tính trên khoản outstanding — khoản đã trả xong hoặc đã xoá nợ không còn là
     * tiền sẽ về hay sẽ đi.
     */
    @Transactional(readOnly = true)
    public DebtSummaryResponse summary(UUID userId) {
        List<Debt> debts = debtRepository.findAllForUser(userId, null, STATUS_OUTSTANDING);
        LocalDate today = LocalDate.now();

        long receivableTotal = 0;
        int receivableCount = 0;
        long receivableOverdueTotal = 0;
        int receivableOverdueCount = 0;
        long payableTotal = 0;
        int payableCount = 0;
        long payableOverdueTotal = 0;
        int payableOverdueCount = 0;
        List<DebtSummaryResponse.DueSoonItem> dueSoon = new ArrayList<>();

        for (Debt debt : debts) {
            long remaining = debt.getPrincipalAmount() - debt.getPaidAmount();
            boolean overdue = debt.getDueDate() != null && debt.getDueDate().isBefore(today);

            if (TYPE_LENDING.equals(debt.getType())) {
                receivableTotal += remaining;
                receivableCount++;
                if (overdue) {
                    receivableOverdueTotal += remaining;
                    receivableOverdueCount++;
                }
            } else {
                payableTotal += remaining;
                payableCount++;
                if (overdue) {
                    payableOverdueTotal += remaining;
                    payableOverdueCount++;
                }
            }

            if (debt.getDueDate() != null) {
                int daysRemaining = (int) ChronoUnit.DAYS.between(today, debt.getDueDate());
                if (daysRemaining <= DUE_SOON_DAYS) {
                    dueSoon.add(new DebtSummaryResponse.DueSoonItem(
                            debt.getId(),
                            debt.getCounterpartyName(),
                            remaining,
                            debt.getDueDate(),
                            daysRemaining,
                            debt.getType()));
                }
            }
        }

        dueSoon.sort((a, b) -> a.dueDate().compareTo(b.dueDate()));

        return new DebtSummaryResponse(
                new DebtSummaryResponse.Side(
                        receivableTotal,
                        receivableCount,
                        new DebtSummaryResponse.OverduePart(receivableOverdueTotal, receivableOverdueCount)),
                new DebtSummaryResponse.Side(
                        payableTotal,
                        payableCount,
                        new DebtSummaryResponse.OverduePart(payableOverdueTotal, payableOverdueCount)),
                receivableTotal - payableTotal,
                dueSoon);
    }

    // ---------------------------------------------------------------------
    // Tác vụ nền JOB-04 (D-57, api/08 mục 9)
    // ---------------------------------------------------------------------

    /**
     * Quét TOÀN HỆ THỐNG khoản nợ còn outstanding có due_date, ghi nhắc nợ đúng ba mốc: còn 7
     * ngày, còn 1 ngày, quá hạn nhắc lại mỗi 7 ngày. Mỗi khoản xử lý ĐỘC LẬP trong transaction
     * riêng của {@link DebtReminderWorker#evaluateAndNotify} (cùng tinh thần D-51/D-52/T-04-20) —
     * một khoản lỗi không chặn khoản khác.
     */
    public void sendDueReminders() {
        List<Debt> debts = debtRepository.findAllOutstandingWithDueDate();
        for (Debt debt : debts) {
            try {
                debtReminderWorker.evaluateAndNotify(debt);
            } catch (Exception e) {
                log.error("Lỗi khi gửi nhắc nợ cho khoản {}", debt.getId(), e);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    /** Quyền nằm trong SQL; không có quyền thì 404, không phải 403 (T-04-09). */
    private Debt loadOwnedDebt(UUID userId, UUID debtId) {
        return debtRepository
                .findByIdAndUserId(debtId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy khoản nợ."));
    }

    private UUID systemCategoryId(String name, String type) {
        return categoryRepository
                .findSystemCategoryByName(name, type)
                .map(Category::getId)
                .orElseThrow(() -> new BusinessException(
                        "CATEGORY_NOT_FOUND",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Thiếu danh mục hệ thống cho sổ nợ: " + name));
    }

    private Wallet loadWalletOrNull(UUID userId, UUID walletId) {
        return walletRepository.findByIdForUser(walletId, userId).orElse(null);
    }

    private Map<UUID, Wallet> loadWallets(UUID userId, List<Debt> debts) {
        Map<UUID, Wallet> wallets = new HashMap<>();
        debts.stream()
                .map(Debt::getWalletId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(id -> walletRepository.findByIdForUser(id, userId).ifPresent(w -> wallets.put(id, w)));
        return wallets;
    }

    private int countPayments(UUID debtId) {
        return debtPaymentRepository.findByDebtId(debtId).size();
    }

    private DebtListItemResponse toListItem(Debt debt, Wallet wallet, int paymentCount) {
        long remaining = debt.getPrincipalAmount() - debt.getPaidAmount();
        BigDecimal paidRatio = BigDecimal.valueOf(debt.getPaidAmount())
                .divide(BigDecimal.valueOf(debt.getPrincipalAmount()), 4, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now();
        Integer daysRemaining =
                debt.getDueDate() == null ? null : (int) ChronoUnit.DAYS.between(today, debt.getDueDate());
        // Chỉ khoản CÒN NỢ mới quá hạn được — đã trả xong hoặc đã xoá nợ thì hạn trả hết ý nghĩa.
        boolean overdue = debt.getDueDate() != null
                && debt.getDueDate().isBefore(today)
                && STATUS_OUTSTANDING.equals(debt.getStatus());

        return new DebtListItemResponse(
                debt.getId(),
                debt.getType(),
                debt.getCounterpartyName(),
                debt.getPrincipalAmount(),
                debt.getPaidAmount(),
                remaining,
                paidRatio,
                debt.getIssuedDate(),
                debt.getDueDate(),
                daysRemaining,
                overdue,
                debt.getStatus(),
                debt.getNote(),
                wallet == null ? null : new DebtListItemResponse.WalletSummary(wallet.getId(), wallet.getName()),
                paymentCount);
    }
}
