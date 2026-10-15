package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.dto.request.RecCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.RecDetailRes;

import java.util.UUID;

public interface RecTxnBehavierService {

    /**
     * Lấy chi tiết thông tin một giao dịch định kỳ.
     *
     * @param userId id của người dùng nếu người gửi là admin thì là null
     * @param id     ID giao dịch định kỳ
     * @return Chi tiết giao dịch định kỳ
     */
    RecDetailRes detail(UUID id, UUID userId);

    /**
     * Tạo mới một giao dịch định kỳ.
     *
     * @param userId id của người dùng nếu người gửi là admin thì là null
     * @param req    Yêu cầu thiết lập giao dịch định kỳ
     * @return Thông tin giao dịch định kỳ vừa tạo
     */
    RecDetailRes create(UUID userId, RecCreateReq req);

    /**
     * Cập nhật thiết lập giao dịch định kỳ.
     *
     * @param userId id của người dùng nếu người gửi là admin thì là null
     * @param id     ID giao dịch định kỳ
     * @param req    Thông tin yêu cầu cập nhật
     * @return Thông tin sau khi cập nhật
     */
    RecDetailRes update(UUID userId, UUID id, RecUpdateReq req);

    /**
     * Bật giao dịch định kỳ.
     *
     * @param userId id của người dùng nếu người gửi là admin thì là null
     * @param id     ID giao dịch định kỳ
     * @return Thông tin giao dịch định kỳ sau khi thay đổi trạng thái
     */
    Void enable(UUID userId, UUID id);

    /**
     * Tắt giao dịch định kỳ.
     *
     * @param userId id của người dùng nếu người gửi là admin thì là null
     * @param id     ID giao dịch định kỳ
     * @return Thông tin giao dịch định kỳ sau khi thay đổi trạng thái
     */
    Void disable(UUID userId, UUID id);

    /**
     * Vô hiệu hóa (xóa mềm) giao dịch định kỳ.
     *
     * @param userId id của người dùng nếu người gửi là admin thì là null
     * @param id     ID giao dịch định kỳ cần xóa
     */
    Void delete(UUID userId, UUID id);

}
