package com.datn.financeapp.module.transaction.mapper;

import com.datn.financeapp.module.transaction.dto.response.TransactionDetailResponse;
import com.datn.financeapp.module.transaction.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper chuyển đổi giữa entity {@link Transaction} và các DTO phản hồi.
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {
    @Mapping(target = "displayName", source = "name")
    TransactionDetailResponse toResponse(Transaction txn);
}
