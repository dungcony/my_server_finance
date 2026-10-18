package com.datn.financeapp.module.icon.controller;

import com.datn.financeapp.module.icon.dto.request.IconCreateReq;
import com.datn.financeapp.module.icon.dto.request.IconUpdateReq;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.service.IconBehavierService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IconControllerTest {

    @Mock
    private IconBehavierService iconBehavierService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(new IconController(iconBehavierService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET /icons trả danh sách biểu tượng phân trang Slice")
    void getAll_returnsSlice() throws Exception {
        IconRes icon = new IconRes(UUID.randomUUID(), "ca_phe", "Ly cà phê", "M6 8h12");
        when(iconBehavierService.getAll(any()))
                .thenReturn(new SliceImpl<>(List.of(icon), PageRequest.of(0, 20), false));

        mockMvc.perform(get("/icons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].code").value("ca_phe"))
                .andExpect(jsonPath("$.data.content[0].display_name").value("Ly cà phê"))
                .andExpect(jsonPath("$.data.content[0].path_data").value("M6 8h12"));

        verify(iconBehavierService).getAll(any());
    }

    @Test
    @DisplayName("POST /icons tạo biểu tượng thành công trả 201 Created")
    void create_validRequest_returnsCreated() throws Exception {
        IconCreateReq req = new IconCreateReq(
                "tra_sua", "Trà sữa", "M1 1h5", "an_uong", "trà sữa,uống", 1);
        IconRes icon = new IconRes(UUID.randomUUID(), "tra_sua", "Trà sữa", "M1 1h5");

        when(iconBehavierService.create(any(IconCreateReq.class))).thenReturn(icon);

        mockMvc.perform(post("/icons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("tra_sua"))
                .andExpect(jsonPath("$.data.display_name").value("Trà sữa"));

        verify(iconBehavierService).create(any(IconCreateReq.class));
    }

    @Test
    @DisplayName("PATCH /icons/{id} cập nhật biểu tượng thành công")
    void update_validRequest_returnsUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        IconUpdateReq req = new IconUpdateReq("Trà đào", "M2 2", "an_uong", "đào", 2, true);
        IconRes icon = new IconRes(id, "tra_sua", "Trà đào", "M2 2");

        when(iconBehavierService.update(eq(id), any(IconUpdateReq.class))).thenReturn(icon);

        mockMvc.perform(patch("/icons/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.display_name").value("Trà đào"));

        verify(iconBehavierService).update(eq(id), any(IconUpdateReq.class));
    }

    @Test
    @DisplayName("DELETE /icons/{id} vô hiệu hóa biểu tượng thành công")
    void delete_validId_returnsDeleted() throws Exception {
        UUID id = UUID.randomUUID();
        IconRes icon = new IconRes(id, "tra_sua", "Trà sữa", "M1 1h5");

        when(iconBehavierService.delete(id)).thenReturn(icon);

        mockMvc.perform(delete("/icons/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("tra_sua"));

        verify(iconBehavierService).delete(id);
    }
}
