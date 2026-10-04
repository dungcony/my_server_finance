package com.datn.financeapp.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceImplGetNamesTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("getNames: danh sách id null -> trả map rỗng, không chạm CSDL")
    void getNames_NullIds_ReturnsEmptyWithoutQuery() {
        Map<UUID, UserNameDisplayRes> result = userService.getNames(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("getNames: danh sách id rỗng -> trả map rỗng, không chạm CSDL")
    void getNames_EmptyIds_ReturnsEmptyWithoutQuery() {
        Map<UUID, UserNameDisplayRes> result = userService.getNames(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("getNames: trả map theo id, gọi repository đúng một lần cho cả danh sách")
    void getNames_KeysResultsByIdWithSingleQuery() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UserNameDisplayRes a = new UserNameDisplayRes(idA, "An", "Nguyễn", UserStatus.ACTIVE, false);
        UserNameDisplayRes b = new UserNameDisplayRes(idB, "Bình", "Trần", UserStatus.ACTIVE, false);
        when(userRepository.findDisplay(List.of(idA, idB))).thenReturn(List.of(a, b));

        Map<UUID, UserNameDisplayRes> result = userService.getNames(List.of(idA, idB));

        assertThat(result).containsEntry(idA, a).containsEntry(idB, b).hasSize(2);
        verify(userRepository).findDisplay(List.of(idA, idB));
    }
}
