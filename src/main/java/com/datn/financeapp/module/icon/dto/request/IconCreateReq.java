package com.datn.financeapp.module.icon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu yêu cầu tạo mới biểu tượng trong hệ thống.
 *
 * @param code           Mã định danh biểu tượng (duy nhất)
 * @param displayName    Tên hiển thị
 * @param pathData       Dữ liệu đường vẽ SVG
 * @param iconGroup      Mã nhóm biểu tượng
 * @param searchKeywords Từ khóa tìm kiếm (tùy chọn)
 * @param sortOrder      Thứ tự sắp xếp hiển thị
 */
public record IconCreateReq(
        @NotBlank(message = "Mã biểu tượng không được để trống.")
        @Size(max = 50, message = "Mã biểu tượng tối đa 50 ký tự.")
        String code,

        @NotBlank(message = "Tên hiển thị không được để trống.")
        @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự.")
        String displayName,

        @NotBlank(message = "Dữ liệu đường vẽ SVG không được để trống.")
        String pathData,

        @NotBlank(message = "Nhóm biểu tượng không được để trống.")
        @Pattern(regexp = "^(an_uong|di_lai|mua_sam|giai_tri|suc_khoe|hoc_tap|tai_chinh|khac)$",
                message = "Nhóm biểu tượng không hợp lệ.")
        String iconGroup,

        String searchKeywords,
        Integer sortOrder) {
}
