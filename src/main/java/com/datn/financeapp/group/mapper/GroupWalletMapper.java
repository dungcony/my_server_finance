package com.datn.financeapp.group.mapper;

import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
import com.datn.financeapp.group.entity.GroupWallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupWalletMapper {

    GroupWalletRes toResponse(GroupWallet wallet);
}
