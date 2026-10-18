package com.datn.financeapp.module.category.service;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.dto.response.CategoryRes;

import java.util.*;

/**
 * Service quản lý cây danh mục thu chi nhiều cấp do admin quản lý; người dùng thường chỉ đọc.
 */
public interface CategoryService {


    Optional<CateSumaryRes> findByIdAndIsDeletedAndIsLeaf(UUID id, boolean includeDeleted, boolean isLeaf);

    /**
     * Lấy một danh mục chưa xóa theo id.
     *
     * @param id ID danh mục
     * @return danh mục tìm được
     * @throws com.datn.financeapp.common.exception.BusinessException nếu không có hoặc đã xóa
     */
    CategoryRes findActiveById(UUID id);

    CateSumaryRes findSumaryActiveById(UUID id);

    /**
     * Lấy một danh mục chưa xóa theo id. Và phải là lá
     *
     * @param id ID danh mục
     * @return danh mục tìm được
     * @throws com.datn.financeapp.common.exception.BusinessException nếu không có hoặc đã xóa
     */
    CateSumaryRes findIfIsleaf(UUID id);

    /**
     * Lấy một danh mục chưa xóa theo id. dành cho nơi khác đọc.
     *
     * @param id ID danh mục
     * @return danh mục tìm được nếu tìm thấy và null nếu k tìm được
     */
    CategoryRes findByIdForRead(UUID id);

    /**
     * Lấy id danh mục theo code, tra từ cache RAM (không query DB). Dành cho module khác cần gắn giao dịch vào một danh mục
     * hệ thống biết trước code (vd. giao dịch sinh từ nhóm).
     *
     * @param code code danh mục
     * @return ID danh mục
     * @throws com.datn.financeapp.common.exception.BusinessException nếu không có hoặc đã xóa
     */
    UUID findIdByCode(String code);

    /**
     * Lấy code của nhiều danh mục theo id trong một lượt, tra từ cache RAM nên không query DB.
     * Id không có trong cache bị bỏ qua.
     *
     * @param ids các ID danh mục
     * @return map id → code
     */
    Map<UUID, String> findCodesByIds(Collection<UUID> ids);

    /**
     * Id của danh mục này cùng mọi tổ tiên của nó (đi lên tới gốc). Dùng để tìm các ngân sách phải cộng gộp một giao dịch ghi ở danh mục con.
     *
     * @param id ID danh mục
     * @return danh sách id, rỗng nếu không có danh mục này
     */
    List<UUID> findSelfAndAncestorIds(UUID id);

    /**
     * Id của danh mục này cùng mọi con cháu (mọi cấp). Dùng để cộng gộp chi tiêu của cả cây vào danh mục cha.
     *
     * @param id ID danh mục
     * @return danh sách id, rỗng nếu không có danh mục này
     */
    List<UUID> findSelfAndDescendantIds(UUID id);
}
