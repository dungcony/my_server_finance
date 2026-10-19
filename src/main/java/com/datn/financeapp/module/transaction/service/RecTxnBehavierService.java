package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.dto.request.RecCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecTxnFilterReq;
import com.datn.financeapp.module.transaction.dto.request.RecUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.RecTxnRes;
import com.datn.financeapp.module.transaction.dto.response.RecTxnListRes;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RecTxnBehavierService {

    /**
     * Lấy chi tiết thông tin một giao dịch định kỳ.
     *
     * @param id      ID giao dịch định kỳ
     * @param isAdmin true nếu admin gọi, false nếu user gọi
     * @return Chi tiết giao dịch định kỳ
     */
    RecTxnRes get(UUID id, boolean isAdmin);

    /**
     * Lấy toàn bộ thông tin một giao dịch định kỳ.
     *
     * @param isAdmin true nếu admin gọi, false nếu user gọi
     * @return Chi tiết giao dịch định kỳ
     */
    RecTxnListRes gets(RecTxnFilterReq filter, boolean isAdmin, Pageable pageable);

    /**
     * Tạo mới một giao dịch định kỳ.
     *
     * @param req     Yêu cầu thiết lập giao dịch định kỳ
     * @param isAdmin true nếu admin tạo, false nếu user tạo
     * @return Thông tin giao dịch định kỳ vừa tạo
     */
    RecTxnRes create(RecCreateReq req, boolean isAdmin);

    /**
     * Cập nhật thiết lập giao dịch định kỳ.
     *
     * @param id      ID giao dịch định kỳ
     * @param req     Thông tin yêu cầu cập nhật
     * @param isAdmin true nếu admin cập nhật, false nếu user cập nhật
     * @return Thông tin sau khi cập nhật
     */
    RecTxnRes update(UUID id, RecUpdateReq req, boolean isAdmin);

    /**
     * Bật giao dịch định kỳ.
     *
     * @param id      ID giao dịch định kỳ
     * @param isAdmin true nếu admin gọi, false nếu user gọi
     * @return Void
     */
    Void enable(UUID id, boolean isAdmin);

    /**
     * Tắt giao dịch định kỳ.
     *
     * @param id      ID giao dịch định kỳ
     * @param isAdmin true nếu admin gọi, false nếu user gọi
     * @return Void
     */
    Void disable(UUID id, boolean isAdmin);

    /**
     * Vô hiệu hóa (xóa mềm) giao dịch định kỳ.
     *
     * @param id      ID giao dịch định kỳ cần xóa
     * @param isAdmin true nếu admin gọi, false nếu user gọi
     */
    Void delete(UUID id, boolean isAdmin);

}
