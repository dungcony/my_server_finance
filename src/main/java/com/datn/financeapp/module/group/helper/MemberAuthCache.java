package com.datn.financeapp.module.group.helper;

import com.datn.financeapp.module.group.repository.GroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache thông tin quyền của thành viên trong nhóm ({@link MemberAuthInfo}) trên Redis.
 * <p>
 * Key: {@code group:member:auth:{groupId}:{version}:{userId}}, TTL 30 phút.
 * Version của nhóm lưu ở {@code group:version:{groupId}}, chưa có thì tính là 0.
 * </p>
 * <p>
 * <b>Version dùng để làm gì:</b>
 * </p>
 * <ul>
 *   <li>Khi nhóm đổi trạng thái (lưu trữ, mở lại, xóa), thông tin đã cache của mọi thành viên có thể sai.
 *   Ta tăng version của nhóm thay vì xóa từng key.</li>
 *   <li>Key mới có version mới nên key cũ không còn được tìm thấy, request sau sẽ query DB và ghi lại cache.
 *   Key cũ tự hết hạn theo TTL.</li>
 * </ul>
 * <p>
 * <b>Các hàm:</b>
 * </p>
 * <ul>
 *   <li>{@link #get}: Lấy thông tin quyền từ cache, miss thì query DB rồi ghi cache.</li>
 *   <li>{@link #evictMember}: Xóa key của một thành viên.</li>
 *   <li>{@link #evictGroup}: Tăng version để vô hiệu cache của cả nhóm.</li>
 * </ul>
 * <p>
 * <b>Khi nào xóa cache:</b>
 * </p>
 * <ul>
 *   <li>{@link #evictMember}: rời nhóm, bị xóa khỏi nhóm, đổi chủ nhóm (chủ cũ và mới),
 *   đổi thủ quỹ (thủ quỹ cũ và mới).</li>
 *   <li>{@link #evictGroup}: lưu trữ, mở lại, xóa nhóm.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberAuthCache {

    private static final String KEY_PREFIX = "group:member:auth:";
    private static final String VERSION_PREFIX = "group:version:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final GroupRepository groupRepository;
    private final ObjectMapper objectMapper;

    /**
     * Lấy thông tin quyền của thành viên đang hoạt động trong nhóm (có cache).
     *
     * @param groupId ID nhóm
     * @param userId  ID người dùng
     * @return thông tin quyền, rỗng nếu người dùng không phải thành viên ACTIVE của nhóm
     */
    public Optional<MemberAuthInfo> get(UUID groupId, UUID userId) {
        Optional<Long> version = getGroupVersion(groupId);

        if (version.isEmpty()) {
            return groupRepository.findAuthInfo(groupId, userId);
        }

        String key = buildKey(groupId, version.get(), userId);
        Optional<MemberAuthInfo> cached = readCache(key, groupId, userId);
        if (cached.isPresent()) {
            return cached;
        }

        // truy vấn csdl khi cache miss hoặc redis lỗi
        Optional<MemberAuthInfo> infoOpt = groupRepository.findAuthInfo(groupId, userId);
        infoOpt.ifPresent(info -> setCache(key, info));
        return infoOpt;
    }

    /**
     * Xóa cache quyền của một thành viên trong nhóm.
     *
     * @param groupId ID nhóm
     * @param userId  ID người dùng cần xóa cache
     */
    public void evictMember(UUID groupId, UUID userId) {
        try {
            getGroupVersion(groupId)
                    .ifPresent(v -> stringRedisTemplate.delete(buildKey(groupId, v, userId)));

            log.debug("đã xóa cache quyền member {} của group {}", userId, groupId);
        } catch (Exception e) {
            log.warn("Lỗi xóa cache quyền thành viên nhóm {} user {}: {}", groupId, userId, e.getMessage());
        }
    }

    /**
     * Vô hiệu cache quyền của toàn bộ thành viên trong nhóm bằng cách tăng version của nhóm.
     *
     * @param groupId ID nhóm
     */
    public void evictGroup(UUID groupId) {
        try {
            stringRedisTemplate.opsForValue().increment(VERSION_PREFIX + groupId);

            log.info("đổi version group {} thành công", groupId);

        } catch (Exception e) {
            log.warn("Lỗi tăng version cache quyền nhóm {}: {}", groupId, e.getMessage());
        }
    }

    private Optional<Long> getGroupVersion(UUID groupId) {
        try {
            String val = stringRedisTemplate.opsForValue().get(VERSION_PREFIX + groupId);
            // chưa có key version nghĩa là nhóm chưa bị vô hiệu lần nào

            if (val == null)
                return Optional.of(0L);

            return Optional.of(Long.parseLong(val));

        } catch (Exception e) {
            log.warn("Không thể đọc version cache quyền nhóm {}: {}", groupId, e.getMessage());
            return Optional.empty();
        }
    }


    private Optional<MemberAuthInfo> readCache(String key, UUID groupId, UUID userId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, MemberAuthInfo.class));
            }
        } catch (Exception e) {
            log.warn("Không thể đọc cache quyền thành viên nhóm {} user {}: {}", groupId, userId, e.getMessage());
        }
        return Optional.empty();
    }

    private void setCache(String key, MemberAuthInfo info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            stringRedisTemplate.opsForValue().set(key, json, TTL);

            log.debug("lưu thành công key {} của group {}", key, info.groupId());

        } catch (Exception e) {
            log.warn("Lỗi ghi cache quyền thành viên: {}", e.getMessage());
        }
    }

    private String buildKey(UUID groupId, long version, UUID userId) {
        return KEY_PREFIX + groupId + ":" + version + ":" + userId;
    }
}
