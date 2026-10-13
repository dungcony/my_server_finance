package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper chuyển đổi giữa entity {@link Transaction} và các DTO phản hồi.
 */
@Mapper(componentModel = "spring")
public interface TxnMapper {
    @Mapping(target = "displayName", source = "name")
    TxnDetailResponse toResponse(Transaction txn);

    Transaction toEntity(Transaction txnDetailResponse);
}
