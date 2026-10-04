package com.datn.financeapp.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Toàn bộ mã lỗi nghiệp vụ của backend, mỗi mã gắn sẵn HTTP status và câu thông báo mặc định.
 *
 * <p>
 * <b>Vì sao là enum chứ không phải hằng số String.</b> Trước đây mỗi chỗ ném lỗi gõ tay ba mẩu — mã, status, câu thông báo — ở 147 chỗ. Gõ sai mã ({@code "WALLET_NOTFOUND"}) thì build vẫn qua, test vẫn xanh, và app Flutter rơi vào nhánh xử lý mặc định; lỗi chỉ lộ khi người dùng bấm trúng nút đó. Hằng số {@code String} không giải quyết được vì việc dùng chung vẫn là tự nguyện — ai vội vẫn gõ thẳng chuỗi vào được. Enum làm nó thành bắt buộc: tham số nhận kiểu {@code ErrorCode} nên gõ chuỗi là lỗi biên dịch.
 *
 * <p>
 * <b>Tên hằng số CHÍNH LÀ mã lỗi đi ra JSON</b> — {@link #getCode()} trả {@code name()}. Đây là hợp đồng với app Flutter (xem {@code lib/core/network/api_error.dart}), nên đổi tên một hằng số ở đây là đổi hợp đồng API: phải sửa {@code api/*.md} và app trong cùng lần thay đổi.
 *
 * <p>
 * <b>Câu thông báo ở đây chỉ là mặc định.</b> Nhiều chỗ cần câu cụ thể hơn theo ngữ cảnh — rõ nhất là {@link #NOT_FOUND}, một mã dùng chung cho 46 chỗ với 15 câu khác nhau ("Không tìm thấy ví.", "Không tìm thấy danh mục."…). Dùng {@link BusinessException#BusinessException(ErrorCode, String)} để ghi đè.
 *
 * <p>
 * Đối chiếu đầy đủ với {@code api/*.md}: xem {@code prd/02-CHUAN-HOA-KIEN-TRUC-BACKEND/ E1-DOI-SOAT-MA-LOI.md}.
 */
@Getter
public enum ErrorCode {

    // ---------------------------------------------------------------------
    // Dùng chung — api/00-QUY-UOC-CHUNG.md mục 6
    // ---------------------------------------------------------------------

    /**
     * Dữ liệu gửi lên sai định dạng hoặc thiếu. {@code GlobalExceptionHandler} cũng phát mã này khi bean validation trượt, kèm danh sách lỗi theo từng trường.
     */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Dữ liệu vừa được người khác thay đổi, vui lòng tải lại và thử lại."),
    // Thiếu thẻ truy cập. Do {@code GlobalExceptionHandler} phát, không ném từ service.
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để tiếp tục."),

    // Thẻ truy cập đã hết hạn. Do {@code GlobalExceptionHandler} phát.
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn."),

    // Thẻ sai hoặc đã bị thu hồi. Do {@code GlobalExceptionHandler} phát.
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Thẻ truy cập không hợp lệ."),

    // Không được phép truy cập tài nguyên này. Do {@code GlobalExceptionHandler} phát.
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),

    /**
     * Không tìm thấy bản ghi.
     *
     * <p>
     * <b>Cố ý dùng chung một mã cho mọi loại tài nguyên</b> — ví, danh mục, giao dịch, ngân sách đều trả {@code NOT_FOUND}. Đây là quy tắc nghiệp vụ bất biến số 7: khi người dùng không có quyền, trả 404 chứ không 403, để không lộ việc bản ghi có tồn tại hay không. Tách thành {@code WALLET_NOT_FOUND}, {@code BUDGET_NOT_FOUND}… sẽ khiến chính mã lỗi tiết lộ loại tài nguyên vừa dò trúng — đúng thứ thiết kế đang giấu. Câu thông báo thì được ghi đè theo ngữ cảnh, vì nó chỉ hiện cho chủ sở hữu hợp lệ.
     */
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi."),

    // Vi phạm ràng buộc duy nhất.
    DUPLICATE(HttpStatus.CONFLICT, "Bản ghi đã tồn tại."),

    // Gọi quá nhiều lần.
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau."),

    // Phương thức HTTP không được hỗ trợ ở điểm cuối này. Do {@code GlobalExceptionHandler} phát.
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ."),

    // Lỗi ngoài dự kiến. Không bao giờ đưa message gốc ra response (T-02-02).
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại."),

    /**
     * Yêu cầu trước đó với cùng {@code Idempotency-Key} đang được xử lý. Xem {@code api/00-QUY-UOC-CHUNG.md} mục idempotency.
     */
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "Yêu cầu trước đó đang được xử lý, vui lòng thử lại sau."),

    // ---------------------------------------------------------------------
    // Xác thực & tài khoản — api/01-XAC-THUC.md
    // ---------------------------------------------------------------------

    // Đăng ký với email đã có người dùng.
    AUTH_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã có người dùng."),

    // Sai email hoặc mật khẩu khi đăng nhập. Cố ý không nói sai cái nào.
    AUTH_CREDENTIALS_INVALID(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."),

    /**
     * Khoá TẠM 15 phút do đăng nhập sai 5 lần liên tiếp — tự mở sau khi hết hạn. Khác hẳn {@link #AUTH_ACCOUNT_BLOCKED}; app hiện hai câu khác nhau (PRD 01 nhóm B5).
     */
    AUTH_ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản tạm khoá do đăng nhập sai nhiều lần."),

    // tài khoản chưa được xác thực
    AUTH_ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chưa được xác thực email."),

    /**
     * Khoá VĨNH VIỄN do quản trị viên đặt cờ {@code is_blocked} — không tự mở. Khác hẳn {@link #AUTH_ACCOUNT_LOCKED}.
     */
    AUTH_ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá. Vui lòng liên hệ hỗ trợ."),

    // Thẻ làm mới sai, hết hạn, hoặc đã bị thu hồi (kể cả do phát hiện dùng lại).
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Thẻ làm mới không hợp lệ."),

    // Mã OTP 6 chữ số (xác thực email hoặc đặt lại mật khẩu) sai, hết hạn hoặc đã dùng. Một mã chung cho cả hai luồng
    // vì app xử lý như nhau và không cần cho người dùng biết mã sai hay đã hết hạn.
    AUTH_CODE_INVALID(HttpStatus.BAD_REQUEST, "Mã không hợp lệ, đã hết hạn hoặc đã được sử dụng."),

    // Tài khoản đã được kích hoạt trước đó.
    AUTH_ACCOUNT_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "Tài khoản đã được xác thực."),

    // Đổi mật khẩu nhưng gõ sai mật khẩu hiện tại. Nhãn theo đúng ô nhập trên app (PRD 01 mục 6.5).
    AUTH_OLD_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Mật khẩu cũ không đúng."),

    // Mật khẩu mới trùng với mật khẩu cũ hiện tại.
    AUTH_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được trùng với mật khẩu cũ."),

    // Xoá tài khoản nhưng gõ sai mật khẩu xác nhận.
    AUTH_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Mật khẩu không đúng."),

    // Tài khoản Google thuần chưa từng đặt mật khẩu — không đổi/không xác nhận bằng mật khẩu được.
    AUTH_PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "Tài khoản đăng nhập bằng Google, chưa đặt mật khẩu."),

    // Tạo mật khẩu lần đầu nhưng tài khoản đã có mật khẩu — muốn đổi thì dùng đổi mật khẩu.
    AUTH_PASSWORD_ALREADY_SET(HttpStatus.CONFLICT, "Tài khoản đã có mật khẩu."),

    // {@code id_token} của Google không kiểm chứng được, hoặc máy chủ chưa cấu hình client id.
    AUTH_GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Đăng nhập bằng Google thất bại. Vui lòng thử lại."),

    // Thu hồi vai trò mặc định (ROLE_USER) của người dùng — mọi tài khoản phải giữ vai trò này.
    USER_DEFAULT_ROLE_NOT_REMOVABLE(HttpStatus.FORBIDDEN, "Không thể thu hồi vai trò mặc định của người dùng."),

    // Gán vai trò cho người dùng nhưng người đó đã có vai trò này.
    USER_ROLE_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Người dùng đã có vai trò này."),

    // Gán quyền hạn cho vai trò nhưng vai trò đã có quyền hạn này.
    USER_ROLE_PERMISSION_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Vai trò đã có quyền hạn này."),

    // ---------------------------------------------------------------------
    // Ví — api/02-VI.md
    // ---------------------------------------------------------------------

    // Trùng tên ví trong cùng một chủ sở hữu.
    WALLET_NAME_EXISTS(HttpStatus.CONFLICT, "Đã có ví cùng tên."),

    // Xoá ví cuối cùng — người dùng phải luôn còn ít nhất một ví.
    WALLET_LAST_NOT_DELETABLE(HttpStatus.CONFLICT, "Phải còn ít nhất một ví."),

    // Xoá ví còn giao dịch mà chưa xác nhận xoá kèm.
    WALLET_HAS_TRANSACTIONS(HttpStatus.CONFLICT, "Ví còn giao dịch, cần xác nhận xoá kèm giao dịch."),

    // Sửa số dư hoặc loại ví qua PATCH — hai trường này cố ý không cho sửa trực tiếp.
    WALLET_BALANCE_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "Không sửa được số dư và loại ví qua PATCH."),

    // Chuyển tiền giữa hai ví trùng nhau.
    WALLET_TRANSFER_SAME_DESTINATION(HttpStatus.BAD_REQUEST, "Hai ví phải khác nhau."),

    // Số dư ví không đủ cho giao dịch.
    WALLET_BALANCE_INSUFFICIENT(HttpStatus.UNPROCESSABLE_ENTITY, "Số dư ví không đủ để thực hiện giao dịch này."),


    // ---------------------------------------------------------------------
    // Danh mục — api/03-DANH-MUC.md
    // ---------------------------------------------------------------------

    // Trùng tên danh mục ở cùng một cấp.
    CATEGORY_NAME_EXISTS(HttpStatus.CONFLICT, "Đã có danh mục cùng tên ở cấp này."),

    // Danh mục chỉ được tối đa hai cấp — quy tắc nghiệp vụ số 1.
    CATEGORY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "Danh mục chỉ được tối đa hai cấp."),

    // Danh mục cha bắt buộc thuộc một nhóm lớn.
    CATEGORY_GROUP_REQUIRED(HttpStatus.BAD_REQUEST, "Danh mục cha bắt buộc có nhóm lớn."),

    // Chuyển danh mục thành cấp con trong khi nó đang có con. Khác {@link #CATEGORY_CHILDREN_EXIST}.
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "Danh mục đang có con, không thể chuyển thành cấp con."),

    // Xoá danh mục khi còn danh mục con. Khác {@link #CATEGORY_HAS_CHILDREN}.
    CATEGORY_CHILDREN_EXIST(HttpStatus.CONFLICT, "Còn danh mục con, phải xoá con trước."),

    // Xoá danh mục còn giao dịch mà chưa chỉ định danh mục thay thế.
    CATEGORY_HAS_TRANSACTIONS(HttpStatus.CONFLICT, "Còn giao dịch, cần chỉ định danh mục thay thế."),

    // Không tìm thấy danh mục.
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục."),

    // Danh mục con phải cùng loại thu/chi với cha.
    CATEGORY_TYPE_MISMATCH_PARENT(HttpStatus.BAD_REQUEST, "Danh mục con phải cùng loại thu/chi với cha."),

    // Đổi loại thu/chi của danh mục đã có — không cho sửa.
    CATEGORY_TYPE_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "Không đổi được loại thu/chi."),

    // Sửa danh mục do hệ thống tạo sẵn.
    SYSTEM_CATEGORY_NOT_EDITABLE(HttpStatus.FORBIDDEN, "Danh mục hệ thống không sửa được."),

    // Xoá danh mục do hệ thống tạo sẵn.
    SYSTEM_CATEGORY_NOT_DELETABLE(HttpStatus.FORBIDDEN, "Danh mục hệ thống không xoá được."),

    // Biểu tượng không tồn tại trong kho {@code icons} hoặc đã ngừng dùng.
    CATEGORY_ICON_INVALID(HttpStatus.BAD_REQUEST, "Biểu tượng không tồn tại hoặc đã ngừng dùng."),

    /**
     * Thiếu danh mục hệ thống mà nghiệp vụ sổ nợ cần (lỗi dữ liệu nền, không phải lỗi người dùng). Là 500 chứ không phải 404 — đổi tên từ {@code CATEGORY_NOT_FOUND} cũ vì tên đó gợi nhầm sang lỗi tra cứu 404 thông thường (E1 mục 3).
     */
    SYSTEM_CATEGORY_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "Thiếu danh mục hệ thống."),

    // ---------------------------------------------------------------------
    // Giao dịch — api/04-GIAO-DICH.md
    // ---------------------------------------------------------------------

    // Số tiền phải là số dương — quy tắc nghiệp vụ số 3, không bao giờ dùng số âm để biểu diễn chi.
    TRANSACTION_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "Số tiền phải lớn hơn 0."),

    // Giao dịch chi/thu bắt buộc có danh mục.
    TRANSACTION_CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, "Chi/thu phải có danh mục."),

    // Danh mục không hợp lệ với loại giao dịch, hoặc giao dịch chuyển tiền lại kèm danh mục.
    TRANSACTION_CATEGORY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Danh mục không hợp lệ."),

    // Gán danh mục thu cho khoản chi hoặc ngược lại.
    TRANSACTION_CATEGORY_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Danh mục thu gán cho khoản chi hoặc ngược lại."),

    // Giao dịch chuyển tiền bắt buộc có ví đích.
    TRANSACTION_DEST_WALLET_REQUIRED(HttpStatus.BAD_REQUEST, "Giao dịch chuyển phải có ví đích."),

    // Giao dịch chi/thu không được có ví đích.
    TRANSACTION_DEST_WALLET_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Giao dịch chi/thu không được có ví đích."),

    // Xoá thẳng giao dịch sinh ra từ một lần trả nợ — phải huỷ ở sổ nợ.
    TRANSACTION_LINKED_TO_DEBT(HttpStatus.CONFLICT,
            "Giao dịch này là một lần trả nợ — huỷ ở sổ nợ, không xoá trực tiếp."),

    // Nhập hàng loạt vượt quá số dòng cho phép.
    TRANSACTION_IMPORT_ROWS_EXCEEDED(HttpStatus.BAD_REQUEST, "Vượt quá số dòng cho phép."),

    // ---------------------------------------------------------------------
    // Ngân sách — api/05-NGAN-SACH.md
    // ---------------------------------------------------------------------

    // Đã có ngân sách cho danh mục này trong cùng kỳ.
    BUDGET_ALREADY_EXISTS(HttpStatus.CONFLICT, "Đã có ngân sách cho danh mục này trong kỳ."),

    // Chỉ đặt được ngân sách cho danh mục chi.
    BUDGET_CATEGORY_NOT_EXPENSE(HttpStatus.BAD_REQUEST, "Chỉ đặt ngân sách cho danh mục chi."),

    // Đổi danh mục hoặc kỳ của ngân sách đã tạo — phải xoá và tạo lại.
    BUDGET_CATEGORY_NOT_EDITABLE(HttpStatus.BAD_REQUEST,
            "Không đổi danh mục hoặc kỳ của ngân sách — xoá và tạo lại."),

    // ---------------------------------------------------------------------
    // Báo cáo & xuất tệp — api/06-BAO-CAO.md
    // ---------------------------------------------------------------------

    // Đường dẫn tải tệp xuất đã hết hạn.
    EXPORT_LINK_EXPIRED(HttpStatus.GONE, "Đường dẫn tải đã hết hạn."),

    // Định dạng xuất chưa được hỗ trợ.
    EXPORT_FORMAT_NOT_SUPPORTED(HttpStatus.NOT_IMPLEMENTED, "Định dạng chưa được hỗ trợ."),

    // ---------------------------------------------------------------------
    // Sổ nợ — api/08-SO-NO.md
    // ---------------------------------------------------------------------

    // Trả thêm cho khoản nợ đã tất toán.
    DEBT_ALREADY_SETTLED(HttpStatus.CONFLICT, "Khoản nợ đã trả xong."),

    // Thao tác trên khoản nợ đã đánh dấu không đòi nữa.
    DEBT_WRITTEN_OFF(HttpStatus.CONFLICT, "Khoản nợ đã đánh dấu không đòi nữa."),

    // Số tiền trả vượt quá phần còn lại của khoản nợ.
    DEBT_PAYMENT_EXCEEDS_REMAINING(HttpStatus.BAD_REQUEST, "Số tiền trả vượt quá phần còn lại."),

    // Hạn trả không được trước ngày phát sinh khoản nợ.
    DEBT_DUE_DATE_INVALID(HttpStatus.BAD_REQUEST, "Hạn trả không được trước ngày phát sinh."),

    // ---------------------------------------------------------------------
    // Định kỳ & mục tiêu — api/09-DINH-KY-MUC-TIEU.md
    // ---------------------------------------------------------------------

    // Tần suất lặp không nằm trong tập cho phép.
    RECURRING_FREQUENCY_INVALID(HttpStatus.BAD_REQUEST, "Tần suất phải là day, week, month hoặc year."),

    // Khoảng lặp phải từ 1 trở lên.
    RECURRING_INTERVAL_INVALID(HttpStatus.BAD_REQUEST, "Khoảng lặp phải lớn hơn hoặc bằng 1."),

    // Loại khoản định kỳ không hợp lệ.
    RECURRING_TYPE_INVALID(HttpStatus.BAD_REQUEST, "Loại khoản định kỳ phải là expense hoặc income."),

    // Ngày kết thúc phải sau ngày bắt đầu.
    RECURRING_END_DATE_INVALID(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu."),

    // Khoản định kỳ đã sinh giao dịch trong hôm nay rồi.
    RECURRING_ALREADY_RUN_TODAY(HttpStatus.CONFLICT, "Đã ghi giao dịch hôm nay cho khoản định kỳ này."),

    // Ngày mục tiêu phải nằm ở tương lai.
    GOAL_TARGET_DATE_INVALID(HttpStatus.BAD_REQUEST, "Ngày mục tiêu phải sau hôm nay."),

    // Mục tiêu đã đạt, không nạp thêm được.
    GOAL_ALREADY_COMPLETED(HttpStatus.CONFLICT, "Mục tiêu đã đạt."),

    // Mục tiêu đã huỷ.
    GOAL_CANCELLED(HttpStatus.CONFLICT, "Mục tiêu đã huỷ."),

    // Nạp tiền thật vào mục tiêu chưa gắn ví.
    GOAL_WALLET_REQUIRED(HttpStatus.BAD_REQUEST, "Mục tiêu chưa gắn ví, không thể tạo giao dịch thật."),

    // ---------------------------------------------------------------------
    // Nhóm chung quỹ — docs/prd/03-NHOM-CHUNG-QUY/thiet-ke/api.md
    // ---------------------------------------------------------------------

    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm hoặc bạn không có quyền truy cập."),

    GROUP_NOT_ARCHIVED(HttpStatus.BAD_REQUEST, "Nhóm không bị lưu trữ"),

    GROUP_FUND_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy quỹ của nhóm."),

    GROUP_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch nhóm."),

    GROUP_TXN_ACCESS_DENIED(HttpStatus.BAD_REQUEST, "Chỉ chủ nhóm và thủ quỹ mới được xem giao dịch của người khác"),

    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thành viên trong nhóm."),

    GROUP_MEMBER_NOT_IN_GROUP(HttpStatus.BAD_REQUEST, "Tồn tại thành viên không trong group"),

    GROUP_MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bạn đã là thành viên của nhóm này."),

    GROUP_MEMBER_PENDING(HttpStatus.CONFLICT, "Bạn đã xin vào nhóm này rồi."),

    GROUP_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "Bạn không phải thành viên của nhóm này."),

    GROUP_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "Thao tác này chỉ dành cho chủ nhóm."),

    GROUP_TREASURER_REQUIRED(HttpStatus.FORBIDDEN, "Thao tác này chỉ dành cho người giữ quỹ hoặc chủ nhóm."),

    GROUP_TXN_CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, "Khoản chi tiêu bắt buộc phải có danh mục."),

    GROUP_TXN_SYSTEM_CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, "Giao dịch nhóm chỉ được chọn danh mục hệ thống."),

    GROUP_TXN_PARTICIPANTS_SUM_MISMATCH(HttpStatus.BAD_REQUEST,
            "Tổng số tiền các thành viên chịu không khớp với số tiền giao dịch."),

    GROUP_TXN_PARTICIPANTS_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "Số tiền đưa vào không hợp lệ."),

    GROUP_TXN_PARTICIPANT_DATA_INVALID(HttpStatus.BAD_REQUEST, "Dữ liệu người tham gia giao dịch không hợp lệ."),

    GROUP_DELETE_BALANCE_NOT_ZERO(HttpStatus.BAD_REQUEST, "Không thể xóa nhóm khi tổng số dư các quỹ chưa bằng 0."),

    GROUP_OWNER_NOT_REMOVABLE(HttpStatus.BAD_REQUEST,
            "Không thể xóa chủ nhóm hoặc chủ nhóm không thể tự rời khi là chủ nhóm duy nhất."),

    GROUP_ARCHIVED(HttpStatus.BAD_REQUEST, "Nhóm đã lưu trữ, chỉ cho phép xem."),

    GROUP_PENDING_TXN_EXIST(HttpStatus.BAD_REQUEST, "Nhóm vẫn còn giao dịch đang chờ duyệt."),

    GROUP_SETTLEMENT_REQUIRED(HttpStatus.BAD_REQUEST,
            "Tất cả thành viên phải tất toán công nợ trước khi xóa hoặc rời nhóm."),

    GROUP_MEMBER_SHARE_NOT_ZERO(HttpStatus.CONFLICT, "Phần trong quỹ của thành viên chưa bằng 0."),

    GROUP_OWNER_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "Chủ nhóm phải chuyển quyền trước khi rời nhóm."),

    GROUP_TREASURER_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "Thủ quỹ phải bàn giao quỹ trước khi rời nhóm."),

    GROUP_FUND_HOLDER_NOT_MEMBER(HttpStatus.BAD_REQUEST, "Người giữ quỹ phải là thành viên hợp lệ của nhóm."),

    GROUP_NEW_OWNER_NOT_MEMBER(HttpStatus.BAD_REQUEST, "Chủ nhóm mới phải là thành viên của nhóm."),

    GROUP_TRANSFER_TO_SELF(HttpStatus.BAD_REQUEST, "Không thể chuyển quyền cho chính mình."),

    GROUP_TXN_DATE_IN_FUTURE(HttpStatus.BAD_REQUEST, "Thời điểm giao dịch không được ở tương lai."),

    GROUP_TXN_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Loại giao dịch không được phép."),

    GROUP_TXN_MONEY_SOURCE_INVALID(HttpStatus.BAD_REQUEST, "Nguồn tiền không hợp lệ cho loại giao dịch này."),

    GROUP_TXN_PAYER_NOT_MEMBER(HttpStatus.BAD_REQUEST,
            "Người trả hoặc người góp không phải là thành viên có mặt tại thời điểm này."),

    GROUP_TXN_PARTICIPANT_NOT_MEMBER(HttpStatus.BAD_REQUEST, "Người tham gia không phải là thành viên có mặt tại thời điểm này."),

    GROUP_TXN_PARTICIPANT_DUPLICATED(HttpStatus.CONFLICT, "Chỉ được thêm mỗi người 1 lần."),

    GROUP_TXN_PARTICIPANTS_SHARE_MIXED(HttpStatus.BAD_REQUEST,
            "Số tiền chia của các thành viên phải đồng nhất (cùng có hoặc cùng trống)."),

    GROUP_TXN_PARTICIPANTS_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Loại giao dịch này không được chỉ định người tham gia."),

    GROUP_TXN_PARTICIPANTS_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng cung cấp danh sách người chia tiền khi thay đổi tổng số tiền."),

    GROUP_TXN_EDIT_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền chỉnh sửa giao dịch này."),

    GROUP_TXN_CONFIRMED_EDIT_FORBIDDEN(HttpStatus.FORBIDDEN, "Giao dịch đã duyệt chỉ được sửa bởi trưởng nhóm hoặc thủ quỹ."),

    GROUP_TXN_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền xóa giao dịch này."),

    GROUP_TXN_NOT_PENDING(HttpStatus.CONFLICT, "Giao dịch không ở trạng thái chờ duyệt."),

    GROUP_TXN_AMOUNT_EXCEEDS_SHARE(HttpStatus.CONFLICT, "Số tiền vượt quá phần trong quỹ hiện có của thành viên."),

    GROUP_TXN_REFUND_EXCEEDS_BALANCE(HttpStatus.BAD_REQUEST, "Số tiền hoàn vượt quá phần trong quỹ hiện có."),

    GROUP_TXN_DELETE_OWNER_ONLY(HttpStatus.FORBIDDEN, "Chỉ chủ nhóm mới có quyền xóa giao dịch."),

    GROUP_TXN_ADJUSTMENT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Lý do điều chỉnh là bắt buộc."),

    GROUP_TXN_ADJUSTMENT_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "Giao dịch điều chỉnh từ kiểm kê không được phép sửa."),

    GROUP_MINIMUM_MEMBERS_REQUIRED(HttpStatus.BAD_REQUEST, "1 nhóm phải có từ 2 người trở lên"),

    GROUP_OWNER_IN_MEMBER_LIST(HttpStatus.BAD_REQUEST, "người tạo k được trong danh sách member");


    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    // Mã lỗi đi ra JSON — chính là tên hằng số. Giữ nguyên tên là giữ nguyên hợp đồng với app.
    public String getCode() {
        return name();
    }

    // HTTP status dạng số, tiện cho {@code ResponseEntity.status(...)}.
    public int getHttpStatus() {
        return status.value();
    }
}
