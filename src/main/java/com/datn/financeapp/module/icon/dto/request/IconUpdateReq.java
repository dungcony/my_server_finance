package com.datn.financeapp.module.icon.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu yêu cầu cập nhật biểu tượng. Các trường null được giữ nguyên giá trị cũ.
 *
 * @param displayName    Tên hiển thị mới (tùy chọn)
 * @param pathData       Dữ liệu đường vẽ SVG mới (tùy chọn)
 * @param iconGroup      Nhóm biểu tượng mới (tùy chọn)
 * @param searchKeywords Từ khóa tìm kiếm mới (tùy chọn)
 * @param sortOrder      Thứ tự sắp xếp mới (tùy chọn)
 * @param isActive       Trạng thái kích hoạt (tùy chọn)
 */
public record IconUpdateReq(
        @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự.")
        String displayName,

        String pathData,

        @Pattern(regexp = "^(an_uong|di_lai|mua_sam|giai_tri|suc_khoe|hoc_tap|tai_chinh|khac)$",
                message = "Nhóm biểu tượng không hợp lệ.")
        String iconGroup,

        String searchKeywords,
        Integer sortOrder,
        Boolean isActive) {
}
