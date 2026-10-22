package com.datn.financeapp.module.debt.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.debt.dto.request.CreateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.CreatePaymentRequest;
import com.datn.financeapp.module.debt.dto.request.UpdateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.WriteOffRequest;
import com.datn.financeapp.module.debt.dto.response.CreateDebtResponse;
import com.datn.financeapp.module.debt.dto.response.CreatePaymentResponse;
import com.datn.financeapp.module.debt.dto.response.DebtDetailResponse;
import com.datn.financeapp.module.debt.dto.response.DebtListItemResponse;
import com.datn.financeapp.module.debt.dto.response.DebtSummaryResponse;
import com.datn.financeapp.module.debt.entity.Debt;
import com.datn.financeapp.module.debt.entity.DebtPayment;
import com.datn.financeapp.module.debt.mapper.DebtMapper;
import com.datn.financeapp.module.debt.repository.DebtPaymentRepository;
import com.datn.financeapp.module.debt.repository.DebtRepository;
import com.datn.financeapp.module.debt.service.DebtAdminService;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailRes;
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
 * Triển khai nghiệp vụ sổ nợ dành riêng cho Admin ({@link DebtAdminService}).
 * Tách biệt hoàn toàn với logic của User thông thường.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DebtAdminServiceImpl implements DebtAdminService {

    private static final String TYPE_LENDING = "lending";
    private static final String STATUS_OUTSTANDING = "outstanding";
    private static final String STATUS_SETTLED = "settled";
    private static final String STATUS_WRITTEN_OFF = "written_off";

    private static final int DUE_SOON_DAYS = 7;

    private static final String CAT_CHO_VAY = "cho_vay";
    private static final String CAT_DI_VAY = "di_vay";
    private static final String CAT_THU_NO = "thu_no";
    private static final String CAT_TRA_NO = "tra_no";

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final TxnBehavierService txnBehavierService;
    private final DebtMapper debtMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DebtListItemResponse> list(UUID userId, String type, String status, Boolean isOverdue) {
        List<Debt> debts = debtRepository.findAllForAdmin(userId, type, status);
        Map<UUID, WalletSumaryRes> wallets = loadWallets(debts);

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

    @Override
    @Transactional(readOnly = true)
    public DebtDetailResponse detail(UUID debtId) {
        Debt debt = debtRepository.findByIdAndIsDeletedFalse(debtId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khoản nợ."));

        List<DebtPayment> payments = debtPaymentRepository.findByDebtIdAndIsDeletedFalseOrderByPaidDateDesc(debtId);
        List<DebtDetailResponse.PaymentHistoryItem> history = payments.stream()
                .map(debtMapper::toHistoryItem)
                .toList();

        TxnDetailRes originTxn = null;
        if (debt.getOriginTransactionId() != null) {
            try {
                originTxn = txnBehavierService.get(debt.getOriginTransactionId(), true);
            } catch (Exception e) {
                log.warn("Admin: Không tìm thấy giao dịch gốc {}", debt.getOriginTransactionId());
            }
        }

        WalletSumaryRes wallet = loadWalletOrNull(debt.getWalletId());
        return debtMapper.toDetailResponse(
                debtMapper.toListItem(debt, wallet, payments.size()),
                history,
                originTxn);
    }

    @Override
    @Transactional(readOnly = true)
    public DebtSummaryResponse summary(UUID userId) {
        List<Debt> debts = debtRepository.findAllForAdmin(userId, null, STATUS_OUTSTANDING);
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

    @Override
    @Transactional
    public CreateDebtResponse createForUser(UUID targetUserId, CreateDebtRequest req) {
        if (targetUserId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Target userId là bắt buộc.");
        }
        if (req.principalAmount() == null || req.principalAmount() <= 0) {
            throw new BusinessException(ErrorCode.TRANSACTION_AMOUNT_INVALID);
        }

        LocalDate issuedDate = req.issuedDate() != null ? req.issuedDate() : LocalDate.now();
        if (req.dueDate() != null && req.dueDate().isBefore(issuedDate)) {
            throw new BusinessException(ErrorCode.DEBT_DUE_DATE_INVALID);
        }

        // Với Admin, kiểm tra ví thuộc về targetUserId
        WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(req.walletId(), targetUserId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví hợp lệ của người dùng này."));

        boolean lending = TYPE_LENDING.equals(req.type());
        TxnType txnType = lending ? TxnType.EXPENSE : TxnType.INCOME;
        String categoryCode = lending ? CAT_CHO_VAY : CAT_DI_VAY;

        Instant occurredAt = issuedDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        // Admin tạo giao dịch hộ user
        TxnDetailRes txnRes = txnBehavierService.create(targetUserId, new TxnCreateReq(
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
                .userId(targetUserId)
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

    @Override
    @Transactional
    public CreatePaymentResponse addPaymentForUser(UUID debtId, CreatePaymentRequest req) {
        Debt debt = debtRepository.findByIdAndIsDeletedFalse(debtId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khoản nợ."));

        UUID targetUserId = debt.getUserId();

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
        WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(walletId, targetUserId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví hợp lệ của người dùng này."));

        boolean lending = TYPE_LENDING.equals(debt.getType());
        TxnType txnType = lending ? TxnType.INCOME : TxnType.EXPENSE;
        String categoryCode = lending ? CAT_THU_NO : CAT_TRA_NO;

        LocalDate paidDate = req.paidDate() != null ? req.paidDate() : LocalDate.now();
        Instant occurredAt = paidDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        TxnDetailRes txnRes = txnBehavierService.create(targetUserId, new TxnCreateReq(
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

    @Override
    @Transactional
    public DebtListItemResponse update(UUID debtId, UpdateDebtRequest req) {
        Debt debt = debtRepository.findByIdAndIsDeletedFalse(debtId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khoản nợ."));

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

        WalletSumaryRes wallet = loadWalletOrNull(debt.getWalletId());
        return debtMapper.toListItem(debt, wallet, countPayments(debtId));
    }

    @Override
    @Transactional
    public DebtListItemResponse writeOff(UUID debtId, WriteOffRequest req) {
        Debt debt = debtRepository.findByIdAndIsDeletedFalse(debtId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khoản nợ."));

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

        WalletSumaryRes wallet = loadWalletOrNull(debt.getWalletId());
        return debtMapper.toListItem(debt, wallet, countPayments(debtId));
    }

    @Override
    @Transactional
    public void cancelPayment(UUID debtId, UUID paymentId) {
        // Kiểm tra khoản nợ tồn tại
        if (!debtRepository.existsById(debtId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khoản nợ.");
        }

        DebtPayment payment = debtPaymentRepository
                .findByIdAndDebtIdAndIsDeletedFalse(paymentId, debtId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy lần trả nợ."));

        UUID transactionId = payment.getTransactionId();

        // Xóa bản ghi thanh toán nợ
        debtPaymentRepository.delete(payment);
        debtPaymentRepository.flush();

        // Admin xóa mềm giao dịch (isAdmin = true)
        txnBehavierService.softDelete(transactionId, true);
    }

    @Override
    @Transactional
    public void delete(UUID debtId) {
        Debt debt = debtRepository.findByIdAndIsDeletedFalse(debtId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khoản nợ."));

        List<DebtPayment> payments = debtPaymentRepository.findByDebtIdAndIsDeletedFalse(debtId);
        List<UUID> paymentTransactionIds =
                payments.stream().map(DebtPayment::getTransactionId).toList();
        UUID originTransactionId = debt.getOriginTransactionId();

        // Xóa bản ghi nợ
        debtRepository.delete(debt);
        debtRepository.flush();

        // Admin xóa mềm các giao dịch liên quan để hoàn tác số dư ví (isAdmin = true)
        for (UUID transactionId : paymentTransactionIds) {
            try {
                txnBehavierService.softDelete(transactionId, true);
            } catch (Exception e) {
                log.warn("Admin: Không thể xóa giao dịch trả nợ {}", transactionId, e);
            }
        }
        if (originTransactionId != null) {
            try {
                txnBehavierService.softDelete(originTransactionId, true);
            } catch (Exception e) {
                log.warn("Admin: Không thể xóa giao dịch nợ gốc {}", originTransactionId, e);
            }
        }
    }

    private WalletSumaryRes loadWalletOrNull(UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletService.findSumaryByIdAndUser(walletId, null, false).orElse(null);
    }

    private Map<UUID, WalletSumaryRes> loadWallets(List<Debt> debts) {
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
