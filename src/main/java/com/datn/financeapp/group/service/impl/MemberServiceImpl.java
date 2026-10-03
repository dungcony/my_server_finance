package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.mapper.MemberMapper;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;

    @Override
    @Transactional
    public Optional<MemberRes> create(MemberCreateReq req) {
        MemberStatus status = req.status() != null ? req.status() : MemberStatus.PENDING;

        // LEFT và REMOVED cần left_at, không thể là trạng thái khi tạo mới
        if (status == MemberStatus.LEFT || status == MemberStatus.REMOVED)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);

        Member member = memberMapper.toEntity(req);
        member.setId(UUID.randomUUID());
        member.setRole(req.role() != null ? req.role() : MemberRole.MEMBER);
        member.setStatus(status);

        // PENDING chưa có joined_at, chỉ ACTIVE mới ghi thời điểm vào nhóm
        if (status == MemberStatus.ACTIVE)
            member.setJoinedAt(Instant.now());

        log.info("tạo thành viên {} trong nhóm {} với trạng thái {}", req.userId(), req.groupId(), status);
        return Optional.of(memberMapper.toResponse(memberRepository.save(member)));
    }

    @Override
    @Transactional
    public List<MemberRes> creates(List<MemberCreateReq> req) {

        List<Member> result = new ArrayList<>();

        if (req.isEmpty())
            return List.of();

        for (MemberCreateReq req1 : req) {

            Member member = memberMapper.toEntity(req1);
            member.setId(UUID.randomUUID());
            member.setRole(req1.role() != null ? req1.role() : MemberRole.MEMBER);
            member.setStatus(req1.status());

            // PENDING chưa có joined_at, chỉ ACTIVE mới ghi thời điểm vào nhóm
            if (req1.status() == MemberStatus.ACTIVE)
                member.setJoinedAt(Instant.now());

            result.add(member);

        }
        result = memberRepository.saveAll(result);

        log.info("tạo danh sách thành viên thành công");

        return result.stream()
                .map(memberMapper::toResponse)
                .toList();
    }

    @Override
    public long countActiveMembers(UUID groupId) {
        return memberRepository.countByGroupIdAndStatus(groupId, MemberStatus.ACTIVE);
    }

    @Override
    public long countPendingMembers(UUID groupId) {
        return memberRepository.countByGroupIdAndStatus(groupId, MemberStatus.PENDING);
    }

    @Override
    public MemberRes getMember(UUID groupId, UUID memberId, MemberStatus status) {

        if (status == null)
            return memberRepository.findByGroupIdAndUserId(groupId, memberId)
                    .map(memberMapper::toResponse)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_REQUIRED));

        return memberRepository.findByGroupIdAndUserIdAndStatus(groupId, memberId, status)
                .map(memberMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_REQUIRED));
    }

    @Override
    public List<MemberRes> getActivateMembers(UUID groupId) {

        return memberRepository.findAllByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)
                .stream()
                .map(memberMapper::toResponse)
                .toList();

    }

    @Override
    public List<MemberRes> getMembersWithStatusIn(UUID groupId, List<MemberStatus> statuses) {
        return memberRepository.findAllByGroupIdAndStatusIn(groupId, statuses)
                .stream()
                .map(memberMapper::toResponse)
                .toList();
    }

    @Override
    public List<UUID> findIdAllMember(UUID groupId) {
        return memberRepository.findByGroupIdAndStatusOrderByJoinedAtDesc(groupId, MemberStatus.ACTIVE)
                .stream()
                .map(Member::getUserId)
                .toList();
    }

    @Override
    public boolean allMemberInGroup(UUID groupId, List<UUID> memberIds) {
        return memberRepository.allMemberInGroup(groupId, memberIds);
    }

    @Override
    public void assertNotInGroup(UUID groupId, UUID memberId) {

        Member mem = memberRepository.findByGroupIdAndUserIdAndStatusIn(groupId, memberId, List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))
                .orElse(null);

        if (mem == null)
            return;

        if (mem.getStatus() == MemberStatus.ACTIVE)
            throw new BusinessException(ErrorCode.GROUP_MEMBER_ALREADY_EXISTS);

        throw new BusinessException(ErrorCode.GROUP_MEMBER_PENDING);
    }


}
