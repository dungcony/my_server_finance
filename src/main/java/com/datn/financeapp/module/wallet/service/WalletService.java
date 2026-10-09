package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.wallet.dto.request.CreateWalletRequest;
import com.datn.financeapp.module.wallet.dto.request.ReorderWalletsRequest;
import com.datn.financeapp.module.wallet.dto.request.UpdateWalletRequest;
import com.datn.financeapp.module.wallet.dto.response.WalletDetailResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletRawBalanceResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletSummaryResponse;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interface định nghĩa các dịch vụ nghiệp vụ liên quan đến quản lý ví tài chính.
 * Cung cấp các phương thức như {@link #list}, {@link #summary}, {@link #detail}, {@link #create},
 * {@link #update}, {@link #delete}, {@link #reorder}, {@link #adjustBalance} và {@link #lockWalletsInOrder}.
 */
public interface WalletService {

    /**
     * Lấy danh sách ví tài chính của người dùng theo bộ lọc.
     *
     * @param userId        ID người dùng
     * @param type          loại ví cần lọc (tùy chọn)
     * @param onlyInTotal   có chỉ tính ví nằm trong tổng số dư hay không (tùy chọn)
     * @param includeShared có bao gồm ví chia sẻ hay không
     * @return danh sách phản hồi thông tin ví
     */
    List<WalletResponse> list(UUID userId, String type, Boolean onlyInTotal, boolean includeShared);

    /**
     * Tổng quan số dư tài chính của người dùng.
     *
     * @param userId ID người dùng
     * @return DTO tổng quan ví
     */
    WalletSummaryResponse summary(UUID userId);

    /**
     * Lấy thông tin chi tiết của một ví.
     *
     * @param userId   ID người dùng
     * @param walletId ID ví
     * @return DTO chi tiết ví
     * @throws BusinessException nếu không tìm thấy ví
     */
    WalletDetailResponse detail(UUID userId, UUID walletId);

    /**
     * Tạo mới một ví tài chính.
     *
     * @param userId ID người dùng
     * @param req    thông tin tạo ví
     * @return DTO thông tin ví vừa tạo
     */
    WalletResponse create(UUID userId, CreateWalletRequest req);

    /**
     * Tạo ví tiền mặt mặc định cho người dùng mới đăng ký.
     *
     * @param userId    ID người dùng
     * @param createdAt thời điểm tạo
     */
    void createDefaultCashWallet(UUID userId, Instant createdAt);

    /**
     * Tìm thông tin tham chiếu ví nếu người dùng có quyền truy cập.
     *
     * @param userId   ID người dùng
     * @param walletId ID ví
     * @return DTO tham chiếu ví hoặc null nếu không tìm thấy
     */
    WalletRefResponse findRefForUser(UUID userId, UUID walletId);

    /**
     * Lấy danh sách ví kèm số dư thô.
     *
     * @param userId        ID người dùng
     * @param includeShared có bao gồm ví chia sẻ hay không
     * @return danh sách ví kèm số dư thô
     */
    List<WalletRawBalanceResponse> listWithRawBalance(UUID userId, boolean includeShared);

    /**
     * Tìm danh sách tham chiếu ví cho tập hợp ID ví.
     *
     * @param walletIds danh sách ID ví
     * @param userId    ID người dùng
     * @return Map ánh xạ ID ví với DTO tham chiếu
     */
    Map<UUID, WalletRefResponse> findRefsForUser(Collection<UUID> walletIds, UUID userId);

    /**
     * Lấy số dư thô của ví, ném ngoại lệ nếu không tìm thấy.
     *
     * @param walletId ID ví
     * @return số dư thô của ví
     * @throws BusinessException nếu không tìm thấy ví
     */
    long requireRawBalance(UUID walletId);

    /**
     * Khóa danh sách ví theo thứ tự ID tăng dần để tránh deadlock.
     *
     * @param walletIds danh sách ID ví cần khóa
     * @param userId    ID người dùng
     */
    void lockWalletsInOrder(Set<UUID> walletIds, UUID userId);

    /**
     * Điều chỉnh số dư thô của ví theo giá trị chênh lệch (delta).
     *
     * @param walletId ID ví
     * @param delta    giá trị thay đổi (dương là tăng, âm là giảm)
     */
    void adjustBalance(UUID walletId, long delta);

    /**
     * Tìm số dư thô của ví.
     *
     * @param walletId ID ví
     * @return số dư thô hoặc null nếu không tìm thấy
     */
    Long findRawBalance(UUID walletId);

    /**
     * Tìm thông tin tham chiếu ví theo ID.
     *
     * @param walletId ID ví
     * @return DTO tham chiếu ví hoặc null nếu không tìm thấy
     */
    WalletRefResponse findRefById(UUID walletId);

    /**
     * Đếm số lượng ví đang hoạt động của người dùng.
     *
     * @param userId ID người dùng
     * @return số lượng ví active
     */
    long countActiveWallets(UUID userId);

    /**
     * Cập nhật thông tin ví.
     *
     * @param userId   ID người dùng
     * @param walletId ID ví
     * @param req      DTO cập nhật ví
     * @return DTO thông tin ví sau cập nhật
     */
    WalletResponse update(UUID userId, UUID walletId, UpdateWalletRequest req);

    /**
     * Xóa ví tài chính (xóa mềm).
     *
     * @param userId             ID người dùng
     * @param walletId           ID ví
     * @param deleteTransactions có xóa các giao dịch thuộc ví hay không
     */
    void delete(UUID userId, UUID walletId, boolean deleteTransactions);

    /**
     * Thay đổi thứ tự sắp xếp các ví của người dùng.
     *
     * @param userId ID người dùng
     * @param req    DTO chứa danh sách thứ tự ví mới
     */
    void reorder(UUID userId, ReorderWalletsRequest req);
}
