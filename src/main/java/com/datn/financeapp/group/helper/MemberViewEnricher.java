package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.user.service.UserService;
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

    private final UserService userService;

    public List<MemberRes> enrich(List<MemberRes> members, UUID keeperId) {
        if (members.isEmpty())
            return members;

        List<UUID> ids = members.stream()
                .map(MemberRes::userId)
                .toList();

        Map<UUID, UserNameDisplayRes> names = userService.getNames(ids);

        return members.stream()
                .map(m -> m.withDisplay(
                        names.get(m.userId()).fullName(), // phẩy tách tham số ở đây
                        m.userId().equals(keeperId)       // tham số thứ 2
                )) // đóng ngoặc của withDisplay() và map()
                .toList();
    }
}
