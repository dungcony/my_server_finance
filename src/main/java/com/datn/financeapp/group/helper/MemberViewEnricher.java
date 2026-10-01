package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bổ sung thông tin hiển thị cho danh sách thành viên: tên và cờ thủ quỹ.
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #enrich}: gắn tên hiển thị (1 lượt truy vấn cho cả danh sách) và đánh dấu thủ quỹ.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class MemberViewEnricher {

    private final ProfileService profileService;

    public List<MemberRes> enrich(List<MemberRes> members, UUID keeperId) {
        if (members.isEmpty())
            return members;

        Map<UUID, String> names = profileService.getDisplayNames(
                members.stream().map(MemberRes::userId).toList());

        return members.stream()
                .map(m -> m.withDisplay(names.get(m.userId()), m.userId().equals(keeperId)))
                .toList();
    }
}
