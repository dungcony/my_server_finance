package com.datn.financeapp.module.category.controller;

import com.datn.financeapp.module.category.dto.response.IconGroupListResponse;
import com.datn.financeapp.module.category.dto.response.IconGroupResponse;
import com.datn.financeapp.module.category.dto.response.IconResponse;
import com.datn.financeapp.module.category.service.IconService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IconControllerTest {

    @Mock
    private IconService iconService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(new IconController(iconService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET /icons chuyển icon_group và search cho service, trả nhóm biểu tượng snake_case")
    void icons_passesFiltersAndReturnsGroups() throws Exception {
        IconResponse icon = new IconResponse(UUID.randomUUID(), "ca_phe", "Ly cà phê", "M6 8h12");
        when(iconService.listIcons("an_uong", "cà"))
                .thenReturn(new IconGroupListResponse(List.of(new IconGroupResponse("an_uong", "Ăn uống", List.of(icon))), 1));

        mockMvc.perform(get("/icons").param("icon_group", "an_uong").param("search", "cà"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.group[0].group_code").value("an_uong"))
                .andExpect(jsonPath("$.data.group[0].group_name").value("Ăn uống"))
                .andExpect(jsonPath("$.data.group[0].items[0].display_name").value("Ly cà phê"))
                .andExpect(jsonPath("$.data.group[0].items[0].path_data").value("M6 8h12"))
                .andExpect(jsonPath("$.data.group[0].items[0].id").doesNotExist());
    }

    @Test
    @DisplayName("GET /icons không tham số thì truyền null cho cả hai bộ lọc")
    void icons_noParams_passesNulls() throws Exception {
        when(iconService.listIcons(null, null)).thenReturn(new IconGroupListResponse(List.of(), 0));

        mockMvc.perform(get("/icons")).andExpect(status().isOk());
        verify(iconService).listIcons(null, null);
    }
}
