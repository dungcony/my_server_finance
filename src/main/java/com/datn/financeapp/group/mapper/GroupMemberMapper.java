package com.datn.financeapp.group.mapper;

import com.datn.financeapp.group.dto.response.group.GroupMemberRes;
import com.datn.financeapp.group.entity.GroupMember;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GroupMemberMapper {

    GroupMemberRes toResponse(GroupMember member);
}
