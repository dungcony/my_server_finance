package com.datn.financeapp.module.debt.service;

import com.datn.financeapp.module.debt.dto.request.CreateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.CreatePaymentRequest;
import com.datn.financeapp.module.debt.dto.request.UpdateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.WriteOffRequest;
import com.datn.financeapp.module.debt.dto.response.CreateDebtResponse;
import com.datn.financeapp.module.debt.dto.response.CreatePaymentResponse;
import com.datn.financeapp.module.debt.dto.response.DebtDetailResponse;
import com.datn.financeapp.module.debt.dto.response.DebtListItemResponse;
import com.datn.financeapp.module.debt.dto.response.DebtSummaryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý khoản nợ/cho vay, ghi nhận thanh toán, xóa nợ xấu và gửi thông báo nhắc nợ.
 * <p>
 * <b>Các hàm chính:</b>
 * <ul>
 *   <li>{@link #create(UUID, CreateDebtRequest)}: Ghi nhận một khoản nợ hoặc cho vay mới.</li>
 *   <li>{@link #addPayment(UUID, UUID, CreatePaymentRequest)}: Ghi nhận đợt thu/trả nợ.</li>
 *   <li>{@link #cancelPayment(UUID, UUID, UUID)}: Hủy đợt thanh toán nợ.</li>
 *   <li>{@link #writeOff(UUID, UUID, WriteOffRequest)}: Xóa nợ xấu (miễn nợ/không thu hồi được).</li>
 *   <li>{@link #update(UUID, UUID, UpdateDebtRequest)}: Cập nhật thông tin khoản nợ.</li>
 *   <li>{@link #delete(UUID, UUID)}: Xóa mềm khoản nợ.</li>
 *   <li>{@link #list(UUID, String, String, Boolean)}: Truy vấn danh sách khoản nợ.</li>
 *   <li>{@link #detail(UUID, UUID)}: Lấy chi tiết khoản nợ kèm lịch sử thanh toán.</li>
 *   <li>{@link #summary(UUID)}: Tổng hợp số liệu nợ/cho vay.</li>
 *   <li>{@link #sendDueReminders()}: Tác vụ nền gửi thông báo nhắc nợ đến hạn.</li>
 * </ul>
 * </p>
 */
public interface DebtService {

    /**
     * Tạo mới một khoản nợ (đi vay) hoặc khoản cho vay.
     *
     * @param userId ID người dùng
     * @param req    Yêu cầu tạo khoản nợ
     * @return Kết quả ghi nhận khoản nợ kèm giao dịch tự động sinh ra
     */
    CreateDebtResponse create(UUID userId, CreateDebtRequest req);

    /**
     * Ghi nhận một đợt thanh toán (trả nợ hoặc thu nợ).
     *
     * @param userId ID người dùng
     * @param debtId ID khoản nợ
     * @param req    Thông tin đợt thanh toán
     * @return Kết quả ghi nhận đợt thanh toán
     */
    CreatePaymentResponse addPayment(UUID userId, UUID debtId, CreatePaymentRequest req);

    /**
     * Hủy bỏ một đợt thanh toán nợ đã ghi nhận trước đó.
     *
     * @param userId    ID người dùng
     * @param debtId    ID khoản nợ
     * @param paymentId ID đợt thanh toán cần hủy
     */
    void cancelPayment(UUID userId, UUID debtId, UUID paymentId);

    /**
     * Xóa nợ xấu (ghi nhận không thể thu hồi hoặc được miễn trả).
     *
     * @param userId ID người dùng
     * @param debtId ID khoản nợ
     * @param req    Yêu cầu xóa nợ
     * @return Thông tin khoản nợ sau khi xóa nợ
     */
    DebtListItemResponse writeOff(UUID userId, UUID debtId, WriteOffRequest req);

    /**
     * Cập nhật thông tin khoản nợ (người liên quan, hạn trả, ghi chú).
     *
     * @param userId ID người dùng
     * @param debtId ID khoản nợ
     * @param req    Thông tin yêu cầu cập nhật
     * @return Thông tin khoản nợ sau khi cập nhật
     */
    DebtListItemResponse update(UUID userId, UUID debtId, UpdateDebtRequest req);

    /**
     * Vô hiệu hóa (xóa mềm) khoản nợ.
     *
     * @param userId ID người dùng
     * @param debtId ID khoản nợ cần xóa
     */
    void delete(UUID userId, UUID debtId);

    /**
     * Truy vấn danh sách các khoản nợ của người dùng theo bộ lọc.
     *
     * @param userId    ID người dùng
     * @param type      Loại nợ ({@code debt_payable} hoặc {@code debt_receivable})
     * @param status    Trạng thái ({@code active}, {@code completed}, {@code written_off})
     * @param isOverdue {@code true} nếu chỉ muốn lấy các khoản nợ quá hạn
     * @return Danh sách các khoản nợ thỏa mãn
     */
    List<DebtListItemResponse> list(UUID userId, String type, String status, Boolean isOverdue);

    /**
     * Lấy thông tin chi tiết một khoản nợ kèm lịch sử các đợt thanh toán.
     *
     * @param userId ID người dùng
     * @param debtId ID khoản nợ
     * @return Chi tiết khoản nợ
     */
    DebtDetailResponse detail(UUID userId, UUID debtId);

    /**
     * Tổng hợp tình hình nợ và cho vay của người dùng.
     *
     * @param userId ID người dùng
     * @return Báo cáo tổng hợp sổ nợ
     */
    DebtSummaryResponse summary(UUID userId);

    /**
     * Tác vụ nền quét và tự động gửi thông báo nhắc nhở nợ sắp/đã đến hạn.
     */
    void sendDueReminders();
}
