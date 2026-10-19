package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailRes;
import com.datn.financeapp.module.transaction.dto.response.TxnSummaryRes;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper chuyển đổi giữa entity {@link Transaction} và các DTO phản hồi.
 */
@Mapper(componentModel = "spring")
public interface TxnMapper {

    @Mapping(target = "id", source = "txn.id")
    @Mapping(target = "displayName", source = "txn.name")
    @Mapping(target = "type", source = "txn.type")
    @Mapping(target = "amount", source = "txn.amount")
    @Mapping(target = "note", source = "txn.note")
    @Mapping(target = "createdAt", source = "txn.createdAt")
    @Mapping(target = "updatedAt", source = "txn.updatedAt")
    @Mapping(target = "wallet", source = "wallet")
    @Mapping(target = "category", source = "category")
    TxnDetailRes toResponse(Transaction txn, CateSumaryRes category, WalletSumaryRes wallet);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "name", source = "displayName")
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "new", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Transaction toEntity(TxnCreateReq txnCreateReq);

    TxnSummaryRes toSummaryResponse(Transaction txn);

}

