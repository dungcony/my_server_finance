package com.datn.financeapp.module.icon.service;

import com.datn.financeapp.module.icon.dto.request.IconCreateReq;
import com.datn.financeapp.module.icon.dto.request.IconUpdateReq;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * Quản lý các hành động nghiệp vụ với biểu tượng trong kho hệ thống.
 */
public interface IconBehavierService {

    /**
     * Tạo mới một biểu tượng trong kho hệ thống.
     *
     * @param req thông tin tạo biểu tượng
     * @return thông tin biểu tượng vừa tạo
     * @throws com.datn.financeapp.common.exception.BusinessException nếu mã biểu tượng đã tồn tại
     */
    IconRes create(IconCreateReq req);

    /**
     * Lấy danh sách biểu tượng đang hoạt động theo phân trang dạng Slice.
     *
     * @param pageable thông tin phân trang
     * @return lát cắt dữ liệu biểu tượng đang hoạt động
     */
    Slice<IconRes> getAll(Pageable pageable);

    /**
     * Cập nhật thông tin biểu tượng theo code.
     *
     * @param code code biểu tượng cần cập nhật
     * @param req  thông tin cần cập nhật
     * @return thông tin biểu tượng sau khi cập nhật
     * @throws com.datn.financeapp.common.exception.BusinessException nếu không tìm thấy biểu tượng
     */
    IconRes update(String code, IconUpdateReq req);

    /**
     * Xóa mềm biểu tượng bằng cách chuyển trạng thái sang không hoạt động.
     *
     * @param code code biểu tượng cần xóa
     * @return thông tin biểu tượng vừa bị vô hiệu hóa
     * @throws com.datn.financeapp.common.exception.BusinessException nếu không tìm thấy biểu tượng
     */
    IconRes delete(String code);
}
