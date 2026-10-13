package com.datn.financeapp.module.group.helper;

import com.datn.financeapp.module.group.enums.GroupStatus;
import com.datn.financeapp.module.group.enums.MemberRole;
import com.datn.financeapp.module.group.enums.MemberStatus;
import com.datn.financeapp.module.group.repository.GroupRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class MemberAuthCacheTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private GroupRepository groupRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MemberAuthCache cache;

    private final UUID groupId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String versionKey = "group:version:" + groupId;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new MemberAuthCache(stringRedisTemplate, groupRepository, objectMapper);
    }

    @Test
    @DisplayName("get khi có trong cache thì trả về luôn và không truy vấn CSDL")
    void get_cacheHit_skipsDatabase() throws JsonProcessingException {
        MemberAuthInfo info = info(MemberRole.OWNER);
        when(valueOperations.get(versionKey)).thenReturn(null);
        when(valueOperations.get(memberKey(0))).thenReturn(objectMapper.writeValueAsString(info));

        Optional<MemberAuthInfo> result = cache.get(groupId, userId);

        assertThat(result).contains(info);
        verifyNoInteractions(groupRepository);
    }

    @Test
    @DisplayName("get khi cache miss thì truy vấn CSDL rồi ghi cache kèm TTL 30 phút")
    void get_cacheMiss_loadsDatabaseAndCaches() {
        MemberAuthInfo info = info(MemberRole.MEMBER);
        when(valueOperations.get(versionKey)).thenReturn(null);
        when(valueOperations.get(memberKey(0))).thenReturn(null);
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.of(info));

        Optional<MemberAuthInfo> result = cache.get(groupId, userId);

        assertThat(result).contains(info);
        verify(valueOperations).set(eq(memberKey(0)), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("get khi CSDL không có thành viên thì không ghi cache")
    void get_databaseEmpty_doesNotCache() {
        when(valueOperations.get(versionKey)).thenReturn(null);
        when(valueOperations.get(memberKey(0))).thenReturn(null);
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.empty());

        Optional<MemberAuthInfo> result = cache.get(groupId, userId);

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("get dùng version của nhóm trong key cache")
    void get_usesGroupVersionInKey() {
        MemberAuthInfo info = info(MemberRole.MEMBER);
        when(valueOperations.get(versionKey)).thenReturn("3");
        when(valueOperations.get(memberKey(3))).thenReturn(null);
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.of(info));

        cache.get(groupId, userId);

        verify(valueOperations).get(memberKey(3));
        verify(valueOperations).set(eq(memberKey(3)), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("get khi không đọc được version (Redis lỗi) thì lấy từ CSDL và không ghi cache")
    void get_versionReadFails_usesDatabaseWithoutCaching() {
        MemberAuthInfo info = info(MemberRole.MEMBER);
        when(valueOperations.get(versionKey)).thenThrow(new IllegalStateException("redis down"));
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.of(info));

        Optional<MemberAuthInfo> result = cache.get(groupId, userId);

        assertThat(result).contains(info);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("get khi version không phải số thì lấy từ CSDL và không ghi cache")
    void get_versionNotNumber_usesDatabaseWithoutCaching() {
        MemberAuthInfo info = info(MemberRole.MEMBER);
        when(valueOperations.get(versionKey)).thenReturn("abc");
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.of(info));

        Optional<MemberAuthInfo> result = cache.get(groupId, userId);

        assertThat(result).contains(info);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("get khi JSON trong cache hỏng thì lấy từ CSDL và ghi đè cache")
    void get_corruptJson_fallsBackToDatabaseAndOverwrites() {
        MemberAuthInfo info = info(MemberRole.OWNER);
        when(valueOperations.get(versionKey)).thenReturn(null);
        when(valueOperations.get(memberKey(0))).thenReturn("{khong-phai-json");
        when(groupRepository.findAuthInfo(groupId, userId)).thenReturn(Optional.of(info));

        Optional<MemberAuthInfo> result = cache.get(groupId, userId);

        assertThat(result).contains(info);
        verify(valueOperations).set(eq(memberKey(0)), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("evictMember xóa đúng key của thành viên ở version hiện tại")
    void evictMember_deletesKeyAtCurrentVersion() {
        when(valueOperations.get(versionKey)).thenReturn("5");

        cache.evictMember(groupId, userId);

        verify(stringRedisTemplate).delete(memberKey(5));
    }

    @Test
    @DisplayName("evictMember khi Redis lỗi thì không ném exception")
    void evictMember_redisFails_doesNotThrow() {
        when(valueOperations.get(versionKey)).thenReturn("5");
        doThrow(new IllegalStateException("redis down")).when(stringRedisTemplate).delete(memberKey(5));

        assertThatCode(() -> cache.evictMember(groupId, userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("evictMember khi không đọc được version thì không xóa key nào và không ném exception")
    void evictMember_versionReadFails_skipsDelete() {
        when(valueOperations.get(versionKey)).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> cache.evictMember(groupId, userId)).doesNotThrowAnyException();
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("evictGroup tăng version của nhóm")
    void evictGroup_incrementsVersionKey() {
        cache.evictGroup(groupId);

        verify(valueOperations).increment(versionKey);
    }

    @Test
    @DisplayName("evictGroup khi Redis lỗi thì không ném exception")
    void evictGroup_redisFails_doesNotThrow() {
        when(valueOperations.increment(versionKey)).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> cache.evictGroup(groupId)).doesNotThrowAnyException();
    }

    private String memberKey(long version) {
        return "group:member:auth:" + groupId + ":" + version + ":" + userId;
    }

    private MemberAuthInfo info(MemberRole role) {
        return new MemberAuthInfo(groupId, userId, GroupStatus.ACTIVE, false, MemberStatus.ACTIVE, role, null);
    }
}
