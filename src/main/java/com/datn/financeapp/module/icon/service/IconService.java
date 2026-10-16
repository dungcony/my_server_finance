package com.datn.financeapp.module.icon.service;

import com.datn.financeapp.module.icon.dto.response.IconResponse;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Service đọc kho biểu tượng hệ thống.
 */
public interface IconService {

    /**
     * Lấy default icon
     *
     */
    IconResponse findDefaultWallet();

    /**
     * Lấy biểu tượng đang dùng theo mã code.
     *
     * @param code mã code biểu tượng
     * @return thông tin biểu tượng
     */
    IconResponse findActiveByCode(String code);

    /**
     * Lấy biểu tượng đang bật theo id. Hàm dùng nội bộ: bên gọi phải tự kiểm {@code id} khác null
     * (ví chưa chọn biểu tượng thì không gọi hàm này).
     *
     * @param id ID biểu tượng, không được null
     * @return thông tin biểu tượng
     * @throws com.datn.financeapp.common.exception.BusinessException {@code ICON_NOT_FOUND} nếu biểu tượng không tồn tại hoặc đã tắt
     */
    IconResponse findActiveById(@NonNull UUID id);

    /**
     * Lấy map các biểu tượng đang dùng theo danh sách ID.
     *
     * @param ids danh sách ID biểu tượng
     * @return map ID sang IconResponse
     */
    Map<UUID, IconResponse> findActiveByIds(Collection<UUID> ids);
}
