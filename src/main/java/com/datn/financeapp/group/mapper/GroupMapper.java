package com.datn.financeapp.group.mapper;

import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.response.fund.GroupFundRes;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.MemberRole;

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
    @Mapping(target = "inviteCode", source = "group.inviteCode")
    @Mapping(target = "memberCount", source = "memberCount")
    @Mapping(target = "fundBalance", source = "fundBalance")
    @Mapping(target = "target", source = "group.target")
    @Mapping(target = "createdAt", source = "group.createdAt")
    GroupSummaryRes toSummaryResponse(
            Group group,
            MemberRole myRole,
            long memberCount,
            Long fundBalance);

    @Mapping(target = "id", source = "group.id")
    @Mapping(target = "name", source = "group.name")
    @Mapping(target = "description", source = "group.description")
    @Mapping(target = "status", source = "group.status")
    @Mapping(target = "target", source = "group.target")
    @Mapping(target = "inviteCode", source = "group.inviteCode")
    @Mapping(target = "isSettlementEnabled", source = "group.isSettlementEnabled")
    @Mapping(target = "isJoinWithoutConfirm", source = "group.isJoinWithoutConfirm")
    @Mapping(target = "createdAt", source = "group.createdAt")
    @Mapping(target = "myRole", source = "myRole")
    @Mapping(target = "fund", source = "fund")
    @Mapping(target = "members", source = "members")
    GroupDetailRes toDetailResponse(
            Group group,
            MemberRole myRole,
            GroupFundRes fund,
            List<MemberRes> members);

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
    @Mapping(target = "fund", ignore = true)
    Group toEntity(
            GroupCreateReq req,
            UUID groupId,
            String inviteCode,
            Instant now
    );
}
