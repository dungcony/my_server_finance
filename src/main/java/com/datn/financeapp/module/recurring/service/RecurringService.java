package com.datn.financeapp.module.recurring.service;

import com.datn.financeapp.module.recurring.dto.request.CreateRecurringRequest;
import com.datn.financeapp.module.recurring.dto.request.PauseRecurringRequest;
import com.datn.financeapp.module.recurring.dto.request.UpdateRecurringRequest;
import com.datn.financeapp.module.recurring.dto.response.RecurringDetailResponse;
import com.datn.financeapp.module.recurring.dto.response.RecurringListItemResponse;
import com.datn.financeapp.module.recurring.dto.response.RunNowResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý giao dịch định kỳ, điều khiển thiết lập chu kỳ, tạm dừng và chạy thủ công.
 * <p>
 * <b>Các hàm chính:</b>
 * <ul>
 *   <li>{@link #list(UUID, Boolean, String)}: Lấy danh sách giao dịch định kỳ.</li>
 *   <li>{@link #detail(UUID, UUID)}: Lấy thông tin chi tiết một giao dịch định kỳ theo ID.</li>
 *   <li>{@link #create(UUID, CreateRecurringRequest)}: Thiết lập một giao dịch định kỳ mới.</li>
 *   <li>{@link #update(UUID, UUID, UpdateRecurringRequest)}: Cập nhật thông tin giao dịch định kỳ.</li>
 *   <li>{@link #delete(UUID, UUID)}: Vô hiệu hóa (xóa mềm) giao dịch định kỳ.</li>
 *   <li>{@link #pause(UUID, UUID, PauseRecurringRequest)}: Tạm dừng hoặc bật lại giao dịch định kỳ.</li>
 *   <li>{@link #runNow(UUID, UUID)}: Kích hoạt chạy ngay giao dịch định kỳ lập tức.</li>
 * </ul>
 * </p>
 */
public interface RecurringService {

    /**
     * Truy vấn danh sách giao dịch định kỳ của người dùng theo bộ lọc.
     *
     * @param userId    ID người dùng
     * @param isEnabled Trạng thái bật/tắt ({@code true} đang bật, {@code false} đã tắt, null = tất cả)
     * @param type      Loại giao dịch ({@code expense}, {@code income}, {@code transfer})
     * @return Danh sách giao dịch định kỳ
     */
    List<RecurringListItemResponse> list(UUID userId, Boolean isEnabled, String type);

    /**
     * Lấy chi tiết thông tin một giao dịch định kỳ.
     *
     * @param userId ID người dùng
     * @param id     ID giao dịch định kỳ
     * @return Chi tiết giao dịch định kỳ
     */
    RecurringDetailResponse detail(UUID userId, UUID id);

    /**
     * Tạo mới một giao dịch định kỳ.
     *
     * @param userId ID người dùng
     * @param req    Yêu cầu thiết lập giao dịch định kỳ
     * @return Thông tin giao dịch định kỳ vừa tạo
     */
    RecurringListItemResponse create(UUID userId, CreateRecurringRequest req);

    /**
     * Cập nhật thiết lập giao dịch định kỳ.
     *
     * @param userId ID người dùng
     * @param id     ID giao dịch định kỳ
     * @param req    Thông tin yêu cầu cập nhật
     * @return Thông tin sau khi cập nhật
     */
    RecurringListItemResponse update(UUID userId, UUID id, UpdateRecurringRequest req);

    /**
     * Vô hiệu hóa (xóa mềm) giao dịch định kỳ.
     *
     * @param userId ID người dùng
     * @param id     ID giao dịch định kỳ cần xóa
     */
    void delete(UUID userId, UUID id);

    /**
     * Tạm dừng hoặc tiếp tục chạy giao dịch định kỳ.
     *
     * @param userId ID người dùng
     * @param id     ID giao dịch định kỳ
     * @param req    Yêu cầu tạm dừng/kích hoạt lại
     * @return Thông tin giao dịch định kỳ sau khi thay đổi trạng thái
     */
    RecurringListItemResponse pause(UUID userId, UUID id, PauseRecurringRequest req);

    /**
     * Thực hiện sinh giao dịch thực tế ngay lập tức cho kỳ hiện tại.
     *
     * @param userId ID người dùng
     * @param id     ID giao dịch định kỳ
     * @return Kết quả chạy ngay giao dịch
     */
    RunNowResponse runNow(UUID userId, UUID id);
}
