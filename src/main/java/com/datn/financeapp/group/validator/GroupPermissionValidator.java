package com.datn.financeapp.group.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.*;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.repository.GroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Validator kiểm tra tư cách thành viên, phân quyền thao tác và tính hợp lệ của
 * nhóm tài chính.
 * <p>
 * Tái sử dụng tập trung cho các service trong module {@code group}, quản lý cache
 * Redis cho thông tin phân quyền thành viên {@link MemberAuthInfo}.
 * </p>
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #getAuthInfo}: Lấy và xác thực thông tin quyền thành viên (có cache Redis).</li>
 *   <li>{@link #verifyMember}: Xác thực người dùng phải là thành viên hoạt động.</li>
 *   <li>{@link #verifyOwner}: Xác thực quyền Trưởng nhóm (OWNER).</li>
 *   <li>{@link #verifyOwnerOrTreasurer}: Xác thực quyền Trưởng nhóm hoặc Thủ quỹ.</li>
 *   <li>{@link #verifyTransactionEditPermission}: Xác thực quyền sửa giao dịch nhóm.</li>
 *   <li>{@link #verifyTransactionDeletePermission}: Xác thực quyền xóa giao dịch nhóm.</li>
 *   <li>{@link #evictMember}: Hủy cache quyền của một thành viên cụ thể.</li>
 *   <li>{@link #evictGroup}: Hủy cache quyền toàn bộ thành viên trong nhóm qua version.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPermissionValidator {

    private static final String KEY_PREFIX = "group:auth:";
    private static final String VERSION_PREFIX = "group:auth_version:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final GroupRepository groupRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;


    /**
     * Lấy thông tin auth và xác thực trạng thái nhóm (ACTIVE) và tư cách thành viên (ACTIVE).
     * Mặc định chặn nhóm đã lưu trữ — dùng cho mọi thao tác ghi.
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng
     * @return {@link MemberAuthInfo} chứa trạng thái và role
     */
    public MemberAuthInfo getAuthInfo(UUID groupId, UUID operatorId) {
        return getAuthInfo(groupId, operatorId, false);
    }

    /**
     * Lấy thông tin auth và xác thực nhóm chưa bị xoá cùng tư cách thành viên (ACTIVE).
     *
     * @param groupId       ID nhóm
     * @param operatorId    ID người dùng
     * @param allowArchived {@code true} cho thao tác chỉ đọc (và xoá/mở lại nhóm), {@code false} thì nhóm đã lưu trữ
     *                      bị chặn bằng {@link ErrorCode#GROUP_ARCHIVED}
     * @return {@link MemberAuthInfo} chứa trạng thái và role
     */
    public MemberAuthInfo getAuthInfo(UUID groupId, UUID operatorId, boolean allowArchived) {
        if (groupId == null || operatorId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return validateAuthInfo(
                findAuthInfoCached(groupId, operatorId),
                allowArchived
        );
    }

    /**
     * Hủy cache quyền của một thành viên cụ thể trong nhóm khi thay đổi vai trò hoặc trạng thái.
     *
     * @param groupId ID nhóm
     * @param userId  ID người dùng cần xóa cache
     */
    public void evictMember(UUID groupId, UUID userId) {
        if (stringRedisTemplate == null || groupId == null || userId == null) {
            return;
        }
        try {
            long version = getGroupVersion(groupId);
            stringRedisTemplate.delete(buildKey(groupId, version, userId));
        } catch (Exception e) {
            log.warn("Lỗi xóa cache quyền thành viên nhóm {} user {}: {}", groupId, userId, e.getMessage());
        }
    }

    /**
     * Hủy cache toàn bộ thành viên trong nhóm bằng cách tăng số phiên bản của nhóm.
     *
     * @param groupId ID nhóm cần vô hiệu hóa cache
     */
    public void evictGroup(UUID groupId) {
        if (stringRedisTemplate == null || groupId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().increment(VERSION_PREFIX + groupId);
        } catch (Exception e) {
            log.warn("Lỗi tăng phiên bản cache nhóm {}: {}", groupId, e.getMessage());
        }
    }

    private Optional<MemberAuthInfo> findAuthInfoCached(UUID groupId, UUID userId) {
        if (stringRedisTemplate == null || objectMapper == null) {
            return groupRepository.findAuthInfo(groupId, userId);
        }

        long version = getGroupVersion(groupId);
        String key = buildKey(groupId, version, userId);

        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, MemberAuthInfo.class));
            }
        } catch (Exception e) {
            log.warn("Không thể đọc cache quyền thành viên nhóm {} user {}: {}", groupId, userId, e.getMessage());
        }

        // truy vấn csdl khi cache miss hoặc redis lỗi
        Optional<MemberAuthInfo> infoOpt = groupRepository.findAuthInfo(groupId, userId);
        infoOpt.ifPresent(info -> setCache(key, info));
        return infoOpt;
    }

    private long getGroupVersion(UUID groupId) {
        try {
            String val = stringRedisTemplate.opsForValue().get(VERSION_PREFIX + groupId);
            return val != null ? Long.parseLong(val) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void setCache(String key, MemberAuthInfo info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            stringRedisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.warn("Lỗi ghi cache quyền thành viên: {}", e.getMessage());
        }
    }

    private String buildKey(UUID groupId, long version, UUID userId) {
        return KEY_PREFIX + groupId + ":" + version + ":" + userId;
    }

    /**
     * Lấy thông tin auth và xác thực trạng thái nhóm (ACTIVE) và tư cách thành viên (ACTIVE)
     *
     * @param inviteCode mã mời nhóm
     * @param operatorId ID người dùng
     * @return {@link MemberAuthInfo} chứa trạng thái và role
     */
    public MemberAuthInfo getAuthInfo(String inviteCode, UUID operatorId) {
        if (inviteCode == null || operatorId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }


        return validateAuthInfo(
                groupRepository.findAuthInfo(inviteCode, operatorId),
                false
        );
    }

    /**
     * Xác thực người dùng phải là thành viên đang hoạt động (ACTIVE) của nhóm chưa bị xoá, không cần vai trò cụ thể.
     * Dành cho chỗ chỉ cần kiểm tra quyền và không dùng thông tin auth trả về.
     *
     * @param groupId       ID nhóm
     * @param operatorId    ID người dùng thực hiện thao tác
     * @param allowArchived {@code true} cho thao tác chỉ đọc: nhóm đã lưu trữ vẫn qua
     * @throws BusinessException nếu không phải thành viên đang hoạt động hoặc nhóm không hợp lệ
     */
    public void verifyMember(UUID groupId, UUID operatorId, boolean allowArchived) {
        getAuthInfo(groupId, operatorId, allowArchived);
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) đang hoạt động.
     * Mặc định chặn nhóm đã lưu trữ — dùng cho mọi thao tác ghi.
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng thực hiện thao tác
     * @throws BusinessException nếu không phải Owner
     *                           ({@link ErrorCode#GROUP_OWNER_REQUIRED})
     */
    public void verifyOwner(UUID groupId, UUID operatorId) {
        verifyOwner(groupId, operatorId, false);
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) đang hoạt động.
     *
     * @param groupId       ID nhóm
     * @param operatorId    ID người dùng thực hiện thao tác
     * @param allowArchived {@code true} cho thao tác vẫn làm được khi nhóm đã lưu trữ (xoá, mở lại nhóm)
     * @throws BusinessException nếu không phải Owner
     *                           ({@link ErrorCode#GROUP_OWNER_REQUIRED})
     */
    public void verifyOwner(UUID groupId, UUID operatorId, boolean allowArchived) {

        var info = getAuthInfo(groupId, operatorId, allowArchived);

        if (info.memberRole() != MemberRole.OWNER)
            throw new BusinessException(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) hoặc Thủ quỹ (người đang giữ quỹ).
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng đang thực hiện thao tác
     * @param keepperId  ID người dùng đang giữ quỹ
     * @throws BusinessException nếu không phải Owner và không phải Thủ quỹ
     *                           ({@link ErrorCode#GROUP_TREASURER_REQUIRED})
     */
    public void verifyOwnerOrTreasurer(UUID groupId, UUID operatorId, UUID keepperId) {
        var info = getAuthInfo(groupId, operatorId);
        boolean isOwner = info.memberRole() == MemberRole.OWNER;
        boolean isTreasurer = keepperId != null && keepperId.equals(operatorId);
        if (!isOwner && !isTreasurer)
            throw new BusinessException(ErrorCode.GROUP_TREASURER_REQUIRED);
    }

    /**
     * Xác thực quyền chỉnh sửa giao dịch tài chính nhóm.
     * <p>
     * Quy tắc:
     * <ul>
     * <li>Trưởng nhóm (OWNER) và Thủ quỹ (TREASURER): Được sửa mọi giao dịch.</li>
     * <li>Thành viên thường: Chỉ được sửa giao dịch do chính mình tạo (EXPENSE,
     * CONTRIBUTION).</li>
     * </ul>
     * </p>
     *
     * @param txn        Giao dịch cần chỉnh sửa
     * @param operatorId ID người thực hiện chỉnh sửa
     * @param authInfo   Thông tin quyền hạn của người thực hiện trong nhóm
     */
    public void verifyTransactionEditPermission(GTransaction txn, UUID operatorId, MemberAuthInfo authInfo) {
        // trưởng nhóm và thủ quỹ có toàn quyền sửa mọi giao dịch
        if (authInfo.isOwner() || authInfo.isTreasurer()) {
            return;
        }

        // thành viên thường chỉ được sửa giao dịch do chính mình tạo
        boolean isCreator = txn.getCreatedBy().equals(operatorId);
        if (!isCreator) {
            throw new BusinessException(ErrorCode.GROUP_TXN_EDIT_FORBIDDEN);
        }

        // thành viên thường không được sửa giao dịch đã duyệt
        if (txn.getStatus() == GTransactionStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.GROUP_TXN_CONFIRMED_EDIT_FORBIDDEN);
        }

        // thành viên thường không được sửa giao dịch can thiệp trực tiếp vào quỹ
        if (txn.getType() != GTransactionType.EXPENSE && txn.getType() != GTransactionType.CONTRIBUTION) {
            throw new BusinessException(ErrorCode.GROUP_TREASURER_REQUIRED);
        }
    }

    /**
     * Xác thực quyền xóa giao dịch tài chính nhóm.
     * <p>
     * Quy tắc:
     * <ul>
     * <li>Trưởng nhóm (OWNER): Xóa được mọi giao dịch.</li>
     * <li>Thủ quỹ (TREASURER): Không có quyền xóa.</li>
     * <li>Thành viên thường: Chỉ xóa giao dịch do chính mình tạo và đang PENDING.</li>
     * </ul>
     *
     * @param txn        Giao dịch cần xóa
     * @param operatorId ID người thực hiện xóa
     * @param authInfo   Thông tin quyền hạn của người thực hiện trong nhóm
     */
    public void verifyTransactionDeletePermission(GTransaction txn, UUID operatorId, MemberAuthInfo authInfo) {
        // chủ nhóm xóa được mọi giao dịch
        if (authInfo.isOwner()) return;

        // thủ quỹ không có quyền xóa
        if (authInfo.isTreasurer())
            throw new BusinessException(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN);

        // thành viên thường chỉ xóa giao dịch do mình tạo
        if (!txn.getCreatedBy().equals(operatorId))
            throw new BusinessException(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN);

        // và chỉ khi giao dịch còn chờ duyệt
        if (txn.getStatus() != GTransactionStatus.PENDING)
            throw new BusinessException(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN);
    }

    private MemberAuthInfo validateAuthInfo(Optional<MemberAuthInfo> authInfo, boolean allowArchived) {
        MemberAuthInfo info = requireNotDeleted(authInfo);

        if (!allowArchived && info.groupStatus() == GroupStatus.ARCHIVED)
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);

        return info;
    }

    private MemberAuthInfo requireNotDeleted(Optional<MemberAuthInfo> authInfo) {
        return authInfo
                .filter(i -> i.groupStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }
}
