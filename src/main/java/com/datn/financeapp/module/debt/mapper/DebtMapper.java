package com.datn.financeapp.module.debt.mapper;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.debt.dto.request.DebtCreateReq;
import com.datn.financeapp.module.debt.dto.response.DebtListRes;
import com.datn.financeapp.module.debt.dto.response.DebtRes;
import com.datn.financeapp.module.debt.entity.Debt;
import com.datn.financeapp.module.debt.entity.DebtPayment;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalListRes;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Mapper chuyển đổi giữa entity Debt, DebtPayment và các DTO response.
 */
@Mapper(componentModel = "spring")
public interface DebtMapper {
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "status", constant = "INPROCESS")
    @Mapping(target = "walletId", source = "walletId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "new", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Debt toEntity(DebtCreateReq req, UUID walletId);

    DebtRes toRes(Debt debt);

    DebtListRes.DebtSumaryRes toSumary(Debt debt);

    default DebtListRes toDebtListRes(List<Debt> debts, PageMeta meta) {
        List<DebtListRes.DebtSumaryRes> items = debts.stream()
                .map(this::toSumary)
                .toList();
        return new DebtListRes(items, meta);
    }
}
