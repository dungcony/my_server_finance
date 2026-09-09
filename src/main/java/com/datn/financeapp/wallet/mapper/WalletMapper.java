package com.datn.financeapp.wallet.mapper;

import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.wallet.dto.response.WalletResponse;
import com.datn.financeapp.wallet.entity.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    WalletRefResponse toRef(Wallet wallet);

    @Mapping(target = "id", source = "wallet.id")
    @Mapping(target = "name", source = "wallet.name")
    @Mapping(target = "type", source = "wallet.type")
    @Mapping(target = "currentBalance", source = "currentBalanceAsOfToday")
    @Mapping(target = "includeInTotal", source = "wallet.includeInTotal")
    @Mapping(target = "isShared", expression = "java(wallet.getGroupId() != null)")
    @Mapping(target = "groupId", source = "wallet.groupId")
    @Mapping(target = "icon", source = "wallet.icon")
    @Mapping(target = "color", source = "wallet.color")
    @Mapping(target = "sortOrder", source = "wallet.sortOrder")
    @Mapping(target = "createdAt", source = "wallet.createdAt")
    @Mapping(target = "projectedBalance", source = "projectedBalance")
    WalletResponse toResponse(Wallet wallet, long currentBalanceAsOfToday, Long projectedBalance);
}
