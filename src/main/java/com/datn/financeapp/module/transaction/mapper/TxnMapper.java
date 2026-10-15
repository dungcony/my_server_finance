package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.TxnSummaryRes;
import com.datn.financeapp.module.transaction.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Mapper chuyển đổi giữa entity {@link Transaction} và các DTO phản hồi.
 */
@Mapper(componentModel = "spring")
public interface TxnMapper {

    ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mapping(target = "displayName", source = "name")
    @Mapping(target = "date", expression = "java(mapDate(txn))")
    @Mapping(target = "receiptUrl", ignore = true)
    TxnDetailResponse toResponse(Transaction txn);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "name", source = "displayName")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "recurringId", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Transaction toEntity(TxnCreateReq txnCreateReq);

    TxnSummaryRes toSummaryResponse(Transaction txn);

}

