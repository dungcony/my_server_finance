package com.datn.financeapp.module.debt.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.debt.dto.request.CreateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.CreatePaymentRequest;
import com.datn.financeapp.module.debt.dto.request.UpdateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.WriteOffRequest;
import com.datn.financeapp.module.debt.dto.response.*;
import com.datn.financeapp.module.debt.entity.Debt;
import com.datn.financeapp.module.debt.entity.DebtPayment;
import com.datn.financeapp.module.debt.mapper.DebtMapper;
import com.datn.financeapp.module.debt.repository.DebtPaymentRepository;
import com.datn.financeapp.module.debt.repository.DebtRepository;
import com.datn.financeapp.module.debt.service.DebtService;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ quản lý sổ nợ cá nhân và các đợt thanh toán (Debt & DebtPayment).
 *
 * <p><b>Nguyên tắc kiến trúc:</b>
 * <ul>
 *   <li><b>Đồng bộ dòng tiền:</b> Khi tạo nợ hoặc thanh toán nợ, tự động sinh Transaction tương ứng qua {@link TxnBehavierService}.</li>
 *   <li><b>Trigger DB sở hữu:</b> Số tiền đã trả ({@code paid_amount}) và trạng thái ({@code status})
 *       được tính toán và cập nhật tự động bằng database trigger {@code trg_debt_payments_sync}.</li>
 *   <li><b>Toàn vẹn xóa:</b> Khi hủy lần trả nợ hoặc xóa khoản nợ, các giao dịch liên quan được xóa mềm
 *       để hoàn tác số dư ví.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DebtServiceImpl implements DebtService {

    private static final String TYPE_LENDING = "lending";
    private static final String TYPE_BORROWING = "borrowing";

    private static final String STATUS_OUTSTANDING = "outstanding";
    private static final String STATUS_SETTLED = "settled";
    private static final String STATUS_WRITTEN_OFF = "written_off";

    // Số ngày coi là "sắp đến hạn" để cảnh báo
    private static final int DUE_SOON_DAYS = 7;

    // Mã danh mục hệ thống cố định
    private static final String CAT_CHO_VAY = "cho_vay";
    private static final String CAT_DI_VAY = "di_vay";
    private static final String CAT_THU_NO = "thu_no";
    private static final String CAT_TRA_NO = "tra_no";

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final TxnBehavierService txnBehavierService;
    private final DebtReminderWorker debtReminderWorker;
    private final DebtMapper debtMapper;

    // ---------------------------------------------------------------------
    // Tạo và cập nhật nợ
    // ---------------------------------------------------------------------

    /**
     * Tạo mới một khoản nợ (Cho vay hoặc Đi vay).
     * <ol>
     *   <li>Kiểm tra tính hợp lệ của số tiền và ngày hẹn trả.</li>
     *   <li>Tự động sinh giao dịch tài chính (Chi cho vay hoặc Thu đi vay) gắn với ví tiền.</li>
     *   <li>Lưu bản ghi nợ liên kết với ID của giao dịch vừa tạo.</li>
     * </ol>
     */
    @Override
    @Transactional
    public CreateDebtResponse create(UUID userId, CreateDebtRequest req) {
        if (req.principalAmount() == null || req.principalAmount() <= 0) {
            throw new BusinessException(ErrorCode.TRANSACTION_AMOUNT_INVALID);
        }

        LocalDate issuedDate = req.issuedDate() != null ? req.issuedDate() : LocalDate.now();
        if (req.dueDate() != null && req.dueDate().isBefore(issuedDate)) {
            throw new BusinessException(ErrorCode.DEBT_DUE_DATE_INVALID);
        }

        WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(req.walletId(), userId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví hợp lệ."));

        // lending = cho vay -> giao dịch CHI danh mục "cho_vay"
        // borrowing = đi vay -> giao dịch THU danh mục "di_vay"
        boolean lending = TYPE_LENDING.equals(req.type());
        TxnType txnType = lending ? TxnType.EXPENSE : TxnType.INCOME;
        String categoryCode = lending ? CAT_CHO_VAY : CAT_DI_VAY;

        Instant occurredAt = issuedDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        // Tự động sinh giao dịch liên kết vào ví
        TxnDetailResponse txnRes = txnBehavierService.create(userId, new TxnCreateReq(
                txnType,
                req.principalAmount(),
                wallet.id(),
                categoryCode,
                occurredAt,
                req.counterpartyName(),
                req.note()
        ));

        Debt debt = Debt.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .walletId(wallet.id())
                .type(req.type())
                .counterpartyName(req.counterpartyName())
                .principalAmount(req.principalAmount())
                .paidAmount(0L)
                .issuedDate(issuedDate)
                .dueDate(req.dueDate())
                .note(req.note())
                .status(STATUS_OUTSTANDING)
                .originTransactionId(txnRes.id())
                .build();
        debtRepository.save(debt);

        Long walletNewBalance = walletRepository.findById(wallet.id())
                .map(Wallet::getBalance)
                .orElse(0L);

        return debtMapper.toCreateResponse(
                debtMapper.toListItem(debt, wallet, 0),
                txnRes.id(),
                txnType.name(),
                req.principalAmount(),
                wallet.id(),
                walletNewBalance);
    }

    /**
     * Ghi nhận một lần thanh toán nợ (Trả nợ / Thu nợ).
     * <ol>
     *   <li>Kiểm tra trạng thái khoản nợ và số tiền trả không được vượt quá số dư nợ còn lại.</li>
     *   <li>Sinh giao dịch tài chính (Thu nếu là thu nợ, Chi nếu là trả nợ) vào ví chỉ định.</li>
     *   <li>Lưu bản ghi {@code debt_payments}. Trigger DB sẽ tự động cập nhật lại {@code paid_amount} và {@code status}.</li>
     * </ol>
     */
    @Override
    @Transactional
    public CreatePaymentResponse addPayment(UUID userId, UUID debtId, CreatePaymentRequest req) {
        Debt debt = loadOwnedDebt(userId, debtId);

        if (STATUS_WRITTEN_OFF.equals(debt.getStatus())) {
            throw new BusinessException(ErrorCode.DEBT_WRITTEN_OFF);
        }
        if (STATUS_SETTLED.equals(debt.getStatus())) {
            throw new BusinessException(ErrorCode.DEBT_ALREADY_SETTLED);
        }

        long remaining = debt.getPrincipalAmount() - debt.getPaidAmount();
        if (req.amount() > remaining) {
            throw new BusinessException(ErrorCode.DEBT_PAYMENT_EXCEEDS_REMAINING, String.format(
                    Locale.GERMANY,
                    "%s chỉ còn nợ %,d đ. Ghi trả %,d đ, phần dư vui lòng ghi thành khoản riêng.",
                    debt.getCounterpartyName(), remaining, remaining));
        }

        UUID walletId = req.walletId() != null ? req.walletId() : debt.getWalletId();
        WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(walletId, userId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví hợp lệ."));

        // Khoản lending ĐƯỢC trả -> tiền về ví, giao dịch THU danh mục "thu_no".
        // Khoản borrowing MÌNH trả -> tiền rời ví, giao dịch CHI danh mục "tra_no".
        boolean lending = TYPE_LENDING.equals(debt.getType());
        TxnType txnType = lending ? TxnType.INCOME : TxnType.EXPENSE;
        String categoryCode = lending ? CAT_THU_NO : CAT_TRA_NO;

        LocalDate paidDate = req.paidDate() != null ? req.paidDate() : LocalDate.now();
        Instant occurredAt = paidDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        TxnDetailResponse txnRes = txnBehavierService.create(userId, new TxnCreateReq(
                txnType,
                req.amount(),
                wallet.id(),
                categoryCode,
                occurredAt,
                debt.getCounterpartyName(),
                req.note()
        ));

        DebtPayment payment = DebtPayment.builder()
                .id(UUID.randomUUID())
                .debtId(debtId)
                .transactionId(txnRes.id())
                .amount(req.amount())
                .paidDate(paidDate)
                .note(req.note())
                .build();
        debtPaymentRepository.saveAndFlush(payment);

        // Đọc lại giá trị mới nhất do trigger cập nhật (trả scalar để không dính cache)
        long refreshedPaidAmount = debtRepository.findPaidAmountNative(debtId).orElse(debt.getPaidAmount() + req.amount());
        String refreshedStatus = debtRepository.findStatusNative(debtId).orElse(STATUS_OUTSTANDING);

        Long walletNewBalance = walletRepository.findById(wallet.id())
                .map(Wallet::getBalance)
                .orElse(0L);

        return debtMapper.toPaymentResponse(
                payment,
                refreshedPaidAmount,
                debt.getPrincipalAmount() - refreshedPaidAmount,
                refreshedStatus,
                txnRes.id(),
                txnType.name(),
                req.amount(),
                wallet.id(),
                walletNewBalance);
    }

    /**
     * Hủy một lần thanh toán nợ:
     * <ol>
     *   <li>Xóa bản ghi thanh toán nợ trong {@code debt_payments} (Trigger tự trừ lại {@code paid_amount}).</li>
     *   <li>Xóa mềm giao dịch tài chính tương ứng để hoàn tác số dư ví.</li>
     * </ol>
     */
    @Override
    @Transactional
    public void cancelPayment(UUID userId, UUID debtId, UUID paymentId) {
        loadOwnedDebt(userId, debtId);

        DebtPayment payment = debtPaymentRepository
                .findByIdAndDebtIdAndIsDeletedFalse(paymentId, debtId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy lần trả nợ."));

        UUID transactionId = payment.getTransactionId();

        // Bước 1: Xóa bản ghi thanh toán nợ
        debtPaymentRepository.delete(payment);
        debtPaymentRepository.flush();

        // Bước 2: Hoàn tác số dư ví qua việc xóa mềm giao dịch
        txnBehavierService.softDelete(transactionId, false);
    }

    /**
     * Xóa khoản nợ: Xóa bản ghi nợ và toàn bộ các giao dịch liên quan (gốc và các lần trả nợ).
     */
    @Override
    @Transactional
    public void delete(UUID userId, UUID debtId) {
        Debt debt = loadOwnedDebt(userId, debtId);

        List<DebtPayment> payments = debtPaymentRepository.findByDebtIdAndIsDeletedFalse(debtId);
        List<UUID> paymentTransactionIds =
                payments.stream().map(DebtPayment::getTransactionId).toList();
        UUID originTransactionId = debt.getOriginTransactionId();

        // Xóa bản ghi nợ
        debtRepository.delete(debt);
        debtRepository.flush();

        // Xóa mềm các giao dịch liên kết để hoàn tác số dư ví
        for (UUID transactionId : paymentTransactionIds) {
            try {
                txnBehavierService.softDelete(transactionId, false);
            } catch (Exception e) {
                log.warn("Không thể xóa giao dịch trả nợ {}", transactionId, e);
            }
        }
        if (originTransactionId != null) {
            try {
                txnBehavierService.softDelete(originTransactionId, false);
            } catch (Exception e) {
                log.warn("Không thể xóa giao dịch nợ gốc {}", originTransactionId, e);
            }
        }
    }

    /**
     * Xóa nợ khó đòi (Write-off): Khoản nợ được đánh dấu là không đòi được nữa.
     * Không hoàn tác tiền trong ví vì tiền đã thực sự rời khỏi ví từ trước.
     */
    @Override
    @Transactional
    public DebtListItemResponse writeOff(UUID userId, UUID debtId, WriteOffRequest req) {
        Debt debt = loadOwnedDebt(userId, debtId);

        if (STATUS_WRITTEN_OFF.equals(debt.getStatus())) {
            throw new BusinessException(ErrorCode.DEBT_WRITTEN_OFF);
        }

        debt.setStatus(STATUS_WRITTEN_OFF);
        if (req != null && req.reason() != null && !req.reason().isBlank()) {
            String note = debt.getNote() == null || debt.getNote().isBlank()
                    ? req.reason()
                    : debt.getNote() + "\n" + req.reason();
            debt.setNote(note);
        }
        debtRepository.save(debt);

        WalletSumaryRes wallet = loadWalletOrNull(userId, debt.getWalletId());
        return debtMapper.toListItem(debt, wallet, countPayments(debtId));
    }

    /**
     * Cập nhật thông tin khoản nợ (tên đối tác, hạn trả, ghi chú).
     */
    @Override
    @Transactional
    public DebtListItemResponse update(UUID userId, UUID debtId, UpdateDebtRequest req) {
        Debt debt = loadOwnedDebt(userId, debtId);

        if (req.counterpartyName() != null) {
            debt.setCounterpartyName(req.counterpartyName());
        }
        if (req.dueDate() != null) {
            if (req.dueDate().isBefore(debt.getIssuedDate())) {
                throw new BusinessException(ErrorCode.DEBT_DUE_DATE_INVALID);
            }
            debt.setDueDate(req.dueDate());
        }
        if (req.note() != null) {
            debt.setNote(req.note());
        }
        debtRepository.save(debt);

        WalletSumaryRes wallet = loadWalletOrNull(userId, debt.getWalletId());
        return debtMapper.toListItem(debt, wallet, countPayments(debtId));
    }

    // ---------------------------------------------------------------------
    // Truy vấn dữ liệu
    // ---------------------------------------------------------------------

    /**
     * Lấy danh sách các khoản nợ của người dùng theo bộ lọc (type, status, isOverdue).
     */
    @Override
    @Transactional(readOnly = true)
    public List<DebtListItemResponse> list(UUID userId, String type, String status, Boolean isOverdue) {
        List<Debt> debts = debtRepository.findAllForUser(userId, type, status);
        Map<UUID, WalletSumaryRes> wallets = loadWallets(userId, debts);

        List<DebtListItemResponse> result = new ArrayList<>();
        for (Debt debt : debts) {
            DebtListItemResponse item =
                    debtMapper.toListItem(debt, wallets.get(debt.getWalletId()), countPayments(debt.getId()));
            if (isOverdue == null || isOverdue == item.isOverdue()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Lấy chi tiết một khoản nợ kèm lịch sử các đợt thanh toán và giao dịch gốc.
     */
    @Override
    @Transactional(readOnly = true)
    public DebtDetailResponse detail(UUID userId, UUID debtId) {
        Debt debt = loadOwnedDebt(userId, debtId);

        List<DebtPayment> payments = debtPaymentRepository.findByDebtIdAndIsDeletedFalseOrderByPaidDateDesc(debtId);
        List<DebtDetailResponse.PaymentHistoryItem> history = payments.stream()
                .map(debtMapper::toHistoryItem)
                .toList();

        TxnDetailResponse originTxn = null;
        if (debt.getOriginTransactionId() != null) {
            try {
                originTxn = txnBehavierService.get(debt.getOriginTransactionId(), false);
            } catch (Exception e) {
                log.warn("Không tìm thấy giao dịch gốc {}", debt.getOriginTransactionId());
            }
        }

        WalletSumaryRes wallet = loadWalletOrNull(userId, debt.getWalletId());
        return debtMapper.toDetailResponse(
                debtMapper.toListItem(debt, wallet, payments.size()),
                history,
                originTxn);
    }

    /**
     * Lấy báo cáo tổng quan sổ nợ (Tổng phải thu, Tổng phải trả, Quá hạn, Sắp đến hạn).
     */
    @Override
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

        dueSoon.sort(Comparator.comparing(DebtSummaryResponse.DueSoonItem::dueDate));

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
    // Tác vụ nền nhắc nợ
    // ---------------------------------------------------------------------

    /**
     * Quét toàn hệ thống các khoản nợ đang mở có hạn trả để gửi thông báo nhắc nhở.
     */
    @Override
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
    // Các phương thức trợ giúp (Helper)
    // ---------------------------------------------------------------------

    private Debt loadOwnedDebt(UUID userId, UUID debtId) {
        return debtRepository
                .findByIdAndUserId(debtId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khoản nợ."));
    }

    private WalletSumaryRes loadWalletOrNull(UUID userId, UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletService.findSumaryByIdAndUser(walletId, userId, false).orElse(null);
    }

    private Map<UUID, WalletSumaryRes> loadWallets(UUID userId, List<Debt> debts) {
        Set<UUID> ids = debts.stream()
                .map(Debt::getWalletId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return walletService.findSummaryMapByIds(ids);
    }

    private int countPayments(UUID debtId) {
        return debtPaymentRepository.findByDebtIdAndIsDeletedFalse(debtId).size();
    }
}
