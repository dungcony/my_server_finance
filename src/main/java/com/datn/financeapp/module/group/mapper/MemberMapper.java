package com.datn.financeapp.module.group.mapper;

import com.datn.financeapp.module.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.module.group.dto.response.member.MemberRes;
import com.datn.financeapp.module.group.entity.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MemberMapper {

    // tên hiển thị và cờ thủ quỹ do MemberViewEnricher gắn sau
    @Mapping(target = "displayName", ignore = true)
    @Mapping(target = "isTreasurer", ignore = true)
    MemberRes toResponse(Member member);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "joinedAt", ignore = true)
    @Mapping(target = "leftAt", ignore = true)
    @Mapping(target = "new", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Member toEntity(MemberCreateReq req);
}
