package com.datn.financeapp.group.mapper;

import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupMemberRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.GroupRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    @Mapping(target = "id", source = "group.id")
    @Mapping(target = "name", source = "group.name")
    @Mapping(target = "myRole", source = "myRole")
    @Mapping(target = "status", source = "group.status")
    @Mapping(target = "memberCount", source = "memberCount")
    @Mapping(target = "fundBalance", source = "fundBalance")
    @Mapping(target = "target", source = "group.target")
    @Mapping(target = "createdAt", source = "group.createdAt")
    GroupSummaryRes toSummaryResponse(
            Group group,
            GroupRole myRole,
            long memberCount,
            Long fundBalance);

    @Mapping(target = "id", source = "group.id")
    @Mapping(target = "name", source = "group.name")
    @Mapping(target = "description", source = "group.description")
    @Mapping(target = "status", source = "group.status")
    @Mapping(target = "target", source = "group.target")
    @Mapping(target = "isSettlementEnabled", source = "group.isSettlementEnabled")
    @Mapping(target = "isJoinWithoutConfirm", source = "group.isJoinWithoutConfirm")
    @Mapping(target = "createdAt", source = "group.createdAt")
    @Mapping(target = "myRole", source = "myRole")
    @Mapping(target = "fund", source = "fund")
    @Mapping(target = "members", source = "members")
    GroupDetailRes toDetailResponse(
            Group group,
            GroupRole myRole,
            GroupWalletRes fund,
            List<GroupMemberRes> members);

    @Mapping(target = "id", source = "groupId")
    @Mapping(target = "name", source = "req.name")
    @Mapping(target = "description", source = "req.description")
    @Mapping(target = "target", source = "req.target")
    @Mapping(target = "isSettlementEnabled", source = "req.isSettlementEnabled")
    @Mapping(target = "isJoinWithoutConfirm", source = "req.isJoinWithoutConfirm")
    @Mapping(target = "inviteCode", source = "inviteCode")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", source = "now")
    @Mapping(target = "updatedAt", source = "now")
    Group toEntity(
            GroupCreateReq req,
            UUID groupId,
            String inviteCode,
            Instant now
    );
}
