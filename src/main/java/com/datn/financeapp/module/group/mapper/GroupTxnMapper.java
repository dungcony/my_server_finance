package com.datn.financeapp.module.group.mapper;

import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnParticipantRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnSumaryRes;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.GroupTxnParticipant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupTxnMapper {

    GroupTxnDetailRes toDetailResponse(GroupTxn txn);

    @Mapping(target = "categoryCode", source = "categoryCode")
    GroupTxnDetailRes toDetailResponse(GroupTxn txn, String categoryCode);

    @Mapping(target = "txnId", source = "txn.id")
    GroupTxnSumaryRes toSumaryResponse(GroupTxn txn);

    @Mapping(target = "userId", source = "participant.userId")
    @Mapping(target = "shareAmount", source = "participant.shareAmount")
    GroupTxnParticipantRes toParticipantResponse(GroupTxnParticipant participant);
}
