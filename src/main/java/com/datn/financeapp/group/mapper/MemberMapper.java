package com.datn.financeapp.group.mapper;

import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    MemberRes toResponse(Member member);
}
