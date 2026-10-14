package com.datn.financeapp.module.icon.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.icon.dto.response.IconGroupListResponse;
import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.datn.financeapp.module.icon.entity.Icon;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IconImplTest {

    @Mock
    private IconRepository iconRepository;

    @Mock
    private IconMapper iconMapper;

    private IconImpl iconImpl;

    @BeforeEach
    void setUp() {
        iconImpl = new IconImpl(iconRepository, iconMapper);
        lenient().when(iconMapper.toIconResponse(any(Icon.class))).thenAnswer(invocation -> {
            Icon icon = invocation.getArgument(0);
            return new IconResponse(icon.getId(), icon.getCode(), icon.getDisplayName(), icon.getPathData());
        });
    }

    @Test
    @DisplayName("Gom biểu tượng theo nhóm, giữ thứ tự và đếm đúng tổng")
    void listIcons_groupsByIconGroupInOrder() {
        when(iconRepository.search(null, null))
                .thenReturn(List.of(icon("an_uong", "an_uong"), icon("ca_phe", "an_uong"), icon("xe_may", "di_lai")));

        IconGroupListResponse result = iconImpl.listIcons(null, null);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.group()).hasSize(2);
        assertThat(result.group().get(0).groupCode()).isEqualTo("an_uong");
        assertThat(result.group().get(0).groupName()).isEqualTo("Ăn uống");
        assertThat(result.group().get(0).items()).extracting(IconResponse::code).containsExactly("an_uong", "ca_phe");
        assertThat(result.group().get(1).groupCode()).isEqualTo("di_lai");
        assertThat(result.group().get(1).groupName()).isEqualTo("Đi lại");
        assertThat(result.group().get(1).items()).extracting(IconResponse::code).containsExactly("xe_may");
    }

    @Test
    @DisplayName("Không có biểu tượng nào thì trả danh sách nhóm rỗng và tổng 0")
    void listIcons_noIcons_returnsEmpty() {
        when(iconRepository.search(null, null)).thenReturn(List.of());

        IconGroupListResponse result = iconImpl.listIcons(null, null);

        assertThat(result.group()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    @DisplayName("Từ khóa trống thì truyền null để không lọc")
    void listIcons_blankSearch_passesNull() {
        when(iconRepository.search("an_uong", null)).thenReturn(List.of());

        iconImpl.listIcons("an_uong", "   ");

        verify(iconRepository).search("an_uong", null);
    }

    @Test
    @DisplayName("Từ khóa được cắt khoảng trắng, viết thường và bọc dấu % để tìm LIKE")
    void listIcons_search_buildsLowerCaseLikePattern() {
        when(iconRepository.search(null, "%cà phê%")).thenReturn(List.of());

        iconImpl.listIcons(null, "  Cà Phê ");

        verify(iconRepository).search(null, "%cà phê%");
    }

    @Test
    @DisplayName("getActive trả biểu tượng đang dùng theo code")
    void getActive_returnsActiveIcon() {
        Icon coffee = icon("ca_phe", "an_uong");
        when(iconRepository.findByCodeAndIsActiveTrue("ca_phe")).thenReturn(Optional.of(coffee));

        IconResponse response = iconImpl.getActive("ca_phe");
        assertThat(response.code()).isEqualTo("ca_phe");
    }

    @Test
    @DisplayName("getActive báo ICON_NOT_FOUND khi biểu tượng không tồn tại hoặc đã ngừng dùng")
    void getActive_missingIcon_throws() {
        assertThatThrownBy(() -> iconImpl.getActive("khong_co"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ICON_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("getActiveById trả biểu tượng đang dùng theo ID")
    void getActiveById_returnsActiveIcon() {
        UUID id = UUID.randomUUID();
        Icon coffee = icon("ca_phe", "an_uong");
        coffee.setId(id);
        when(iconRepository.findByIdAndIsActiveTrue(id)).thenReturn(Optional.of(coffee));

        IconResponse response = iconImpl.getActiveById(id);
        assertThat(response.code()).isEqualTo("ca_phe");
    }

    @Test
    @DisplayName("getActiveByIds trả map các biểu tượng đang dùng theo danh sách ID")
    void getActiveByIds_returnsMap() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Icon icon1 = icon("ca_phe", "an_uong");
        icon1.setId(id1);
        Icon icon2 = icon("xe_may", "di_lai");
        icon2.setId(id2);

        when(iconRepository.findByIdInAndIsActiveTrue(List.of(id1, id2))).thenReturn(List.of(icon1, icon2));

        Map<UUID, IconResponse> map = iconImpl.getActiveByIds(List.of(id1, id2));
        assertThat(map).hasSize(2);
        assertThat(map.get(id1).code()).isEqualTo("ca_phe");
        assertThat(map.get(id2).code()).isEqualTo("xe_may");
    }

    private static Icon icon(String code, String group) {
        return Icon.builder()
                .id(UUID.randomUUID())
                .code(code)
                .displayName(code)
                .pathData("M0 0")
                .isActive(true)
                .iconGroup(group)
                .sortOrder(1)
                .build();
    }
}
