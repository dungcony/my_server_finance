package com.datn.financeapp.module.icon.service;

import com.datn.financeapp.module.icon.dto.response.IconRes;

import java.util.*;

/**
 * Service đọc kho biểu tượng hệ thống.
 */
public interface IconService {

    /**
     * Tìm Icon theo code
     *
     * @param code           code icon
     * @param includeDeleted false là chỉ lấy chưa xoá, true là cho phép cả 2
     */
    Optional<IconRes> findByCode(String code, boolean includeDeleted);

    /**
     * Lấy giá trị iconPath bằng code
     *
     * @param code Có thể null
     * @return "" nếu code = null hoặc k tìm thấy icon
     */
    String findIconByCode(String code);

    Map<String, String> findIconsByCodes(List<String> codes);
}
