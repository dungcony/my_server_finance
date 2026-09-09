package com.datn.financeapp.debt.mapper;

import com.datn.financeapp.debt.dto.response.CreateDebtResponse;
import com.datn.financeapp.debt.dto.response.CreatePaymentResponse;
import com.datn.financeapp.debt.dto.response.DebtDetailResponse;
import com.datn.financeapp.debt.dto.response.DebtListItemResponse;
import com.datn.financeapp.debt.entity.Debt;
import com.datn.financeapp.debt.entity.DebtPayment;
import com.datn.financeapp.transaction.dto.response.TransactionRefResponse;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface DebtMapper {

    default DebtListItemResponse toListItem(Debt debt, WalletRefResponse wallet, int paymentCount) {
        if (debt == null) {
            return null;
        }
        long remaining = debt.getPrincipalAmount() - debt.getPaidAmount();
        BigDecimal paidRatio = BigDecimal.valueOf(debt.getPaidAmount())
                .divide(BigDecimal.valueOf(debt.getPrincipalAmount()), 4, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now();
        Integer daysRemaining =
                debt.getDueDate() == null ? null : (int) ChronoUnit.DAYS.between(today, debt.getDueDate());
        boolean overdue = debt.getDueDate() != null
                && debt.getDueDate().isBefore(today)
                && "outstanding".equals(debt.getStatus());

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
                wallet == null ? null : new DebtListItemResponse.WalletSummary(wallet.id(), wallet.name()),
                paymentCount);
    }

    default CreateDebtResponse toCreateResponse(
            DebtListItemResponse listItem,
            UUID transactionId,
            String txnType,
            Long principalAmount,
            UUID walletId,
            Long walletNewBalance) {
        return new CreateDebtResponse(
                listItem,
                new CreateDebtResponse.OriginTransaction(transactionId, txnType, principalAmount),
                new CreateDebtResponse.NewBalance(walletId, walletNewBalance));
    }

    default CreatePaymentResponse toPaymentResponse(
            DebtPayment payment,
            long refreshedPaidAmount,
            long remainingAmount,
            String refreshedStatus,
            UUID transactionId,
            String txnType,
            Long amount,
            UUID walletId,
            Long walletNewBalance) {
        return new CreatePaymentResponse(
                new CreatePaymentResponse.Payment(
                        payment.getId(), payment.getAmount(), payment.getPaidDate(), payment.getNote()),
                new CreatePaymentResponse.DebtProgress(
                        refreshedPaidAmount,
                        remainingAmount,
                        refreshedStatus),
                new CreatePaymentResponse.TransactionSummary(transactionId, txnType, amount),
                new CreatePaymentResponse.NewBalance(walletId, walletNewBalance));
    }

    default DebtDetailResponse.PaymentHistoryItem toHistoryItem(DebtPayment payment) {
        if (payment == null) {
            return null;
        }
        return new DebtDetailResponse.PaymentHistoryItem(
                payment.getId(),
                payment.getAmount(),
                payment.getPaidDate(),
                payment.getNote(),
                payment.getTransactionId());
    }

    default DebtDetailResponse.OriginTransaction toOriginTransaction(TransactionRefResponse originRef) {
        if (originRef == null) {
            return null;
        }
        return new DebtDetailResponse.OriginTransaction(originRef.id(), originRef.amount(), originRef.date());
    }

    default DebtDetailResponse toDetailResponse(
            DebtListItemResponse listItem,
            List<DebtDetailResponse.PaymentHistoryItem> history,
            TransactionRefResponse originRef) {
        return new DebtDetailResponse(listItem, history, toOriginTransaction(originRef));
    }
}
