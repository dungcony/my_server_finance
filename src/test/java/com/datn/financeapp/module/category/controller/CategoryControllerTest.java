package com.datn.financeapp.module.category.controller;

import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryResponse;
import com.datn.financeapp.module.category.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoryController(categoryService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET /categories trả danh mục gốc, khoá JSON snake_case và không lộ id")
    void getMasters_returnsRootsInSnakeCase() throws Exception {
        when(categoryService.getMasters()).thenReturn(List.of(response("an_uong", null)));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("an_uong"))
                .andExpect(jsonPath("$.data[0].is_leaf").value(true))
                .andExpect(jsonPath("$.data[0].parent_code").doesNotExist())
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].type").doesNotExist());
    }

    @Test
    @DisplayName("GET /categories/{code}/children gọi getOfParent với đúng code")
    void getOfParent_callsServiceWithCode() throws Exception {
        when(categoryService.getOfParent("an_uong")).thenReturn(List.of(response("ca_phe", "an_uong")));

        mockMvc.perform(get("/categories/an_uong/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("ca_phe"))
                .andExpect(jsonPath("$.data[0].parent_code").value("an_uong"));
    }

    @Test
    @DisplayName("POST /categories hợp lệ trả 201 và chuyển đúng yêu cầu cho service")
    void create_validBody_returns201() throws Exception {
        CategoryCreateReq req =
                new CategoryCreateReq("Cà phê", "ca_phe", "mô tả", "ca_phe", "#3d6b7d", "an_uong");
        when(categoryService.create(req)).thenReturn(response("ca_phe", "an_uong"));

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cà phê","code":"ca_phe","description":"mô tả",
                                 "icon_code":"ca_phe","color":"#3d6b7d","parent_code":"an_uong"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("ca_phe"));
        verify(categoryService).create(req);
    }

    @Test
    @DisplayName("POST /categories với code sai định dạng hoặc màu sai trả 400 và không gọi service")
    void create_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cà phê","code":"Ca Phe","description":"mô tả",
                                 "icon_code":"ca_phe","color":"xanh"}
                                """))
                .andExpect(status().isBadRequest());
        verify(categoryService, never()).create(any());
    }

    @Test
    @DisplayName("PATCH /categories/{code} chuyển code và nội dung sửa cho service")
    void update_callsServiceWithCodeAndBody() throws Exception {
        CategoryUpdateReq req = new CategoryUpdateReq("Trà sữa", null, null, "#112233", "an_uong");
        when(categoryService.update("ca_phe", req)).thenReturn(response("ca_phe", "an_uong"));

        mockMvc.perform(patch("/categories/ca_phe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Trà sữa","color":"#112233","parent_code":"an_uong"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ca_phe"));
        verify(categoryService).update("ca_phe", req);
    }

    @Test
    @DisplayName("DELETE /categories/{code}?replacement_code= chuyển cả code thay thế cho service")
    void delete_withReplacement_callsService() throws Exception {
        mockMvc.perform(delete("/categories/ca_phe").param("replacement_code", "an_uong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(categoryService).delete("ca_phe");
    }

    @Test
    @DisplayName("DELETE /categories/{code} không có replacement_code thì truyền null")
    void delete_withoutReplacement_passesNull() throws Exception {
        mockMvc.perform(delete("/categories/ca_phe")).andExpect(status().isOk());
        verify(categoryService).delete("ca_phe");
    }

    private static CategoryResponse response(String code, String parentCode) {
        return new CategoryResponse(
                UUID.randomUUID(), "Tên " + code, code, "mô tả", parentCode, null, "#3d6b7d", 0, true, null, false);
    }
}
