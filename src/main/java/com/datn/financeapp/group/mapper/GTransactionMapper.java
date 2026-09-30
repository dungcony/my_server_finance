package com.datn.financeapp.group.mapper;

import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionParticipantRes;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GTransactionMapper {

    @Mapping(target = "id", source = "txn.id")
    @Mapping(target = "groupId", source = "txn.groupId")
    @Mapping(target = "moneySource", source = "txn.moneySource")
    @Mapping(target = "transactorId", source = "txn.transactorId")
    @Mapping(target = "createdBy", source = "txn.createdBy")
    @Mapping(target = "categoryId", source = "txn.categoryId")
    @Mapping(target = "type", source = "txn.type")
    @Mapping(target = "status", source = "txn.status")
    @Mapping(target = "reviewedBy", source = "txn.reviewedBy")
    @Mapping(target = "reviewedAt", source = "txn.reviewedAt")
    @Mapping(target = "amount", source = "txn.amount")
    @Mapping(target = "occurredAt", source = "txn.occurredAt")
    @Mapping(target = "note", source = "txn.note")
    @Mapping(target = "createdAt", source = "txn.createdAt")
    @Mapping(target = "updatedAt", source = "txn.updatedAt")
    @Mapping(target = "participants", source = "participants")
    GroupTransactionDetailRes toDetailResponse(
            GTransaction txn,
            List<GroupTransactionParticipantRes> participants);

    @Mapping(target = "userId", source = "participant.userId")
    @Mapping(target = "shareAmount", source = "participant.shareAmount")
    GroupTransactionParticipantRes toParticipantResponse(TransactionParticipant participant);
}
