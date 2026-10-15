package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.TxnSummaryRes;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
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
    @Mapping(target = "occruredAt", source = "txn.occurredAt") // Lưu ý: entity là occurredAt
    @Mapping(target = "createdAt", source = "txn.createdAt")
    @Mapping(target = "updatedAt", source = "txn.updatedAt")
    @Mapping(target = "wallet", source = "walletRes")
    @Mapping(target = "category", source = "categoryRes")
    TxnDetailResponse toResponse(Transaction txn, CategoryRes categoryRes, WalletRes walletRes);


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

