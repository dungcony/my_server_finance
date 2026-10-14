package com.datn.financeapp.module.wallet.mapper;

import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    @Mapping(target = "icon", source = "icon")
    WalletRes toResponse(Wallet wallet, String icon);
    
    Wallet toEntity(WalletCreateReq wallet);
}
