# Sửa các lỗi UX/UI giao diện và đồng bộ DTO API

## Bối cảnh

- Người dùng yêu cầu khắc phục 3 vấn đề giao diện được ghi nhận trong `docs/problem/ux/ui.md` và rà soát sửa toàn bộ các
  lỗi phát sinh khác ở giao diện người dùng.
- Yêu cầu bất biến: **Tuyệt đối không can thiệp vào code main backend (`src/main/java`)**.
- Hiện trạng các vấn đề:
    1. Thành viên thường (Member) khi truy cập giao dịch nhóm thì giao diện tự động gọi
       `GET /groups/{groupId}/transactions/all`, dẫn đến lỗi `403 FORBIDDEN` từ backend do API `/all` chỉ dành cho Chủ
       nhóm (Owner) và Thủ quỹ (Treasurer). Endpoint đúng cho Member là `GET /groups/{groupId}/transactions` (`genera` /
       `generalList`).
    2. Giao diện luôn hiển thị nút "🛡️ Quản trị" trên thanh điều hướng topbar cho cả người dùng thường, khi bấm vào bị
       văng ra kèm thông báo lỗi. Trang quản trị là không gian độc lập của quản trị viên, cần ẩn nút này với user
       thường, đồng thời khi Admin ở trong trang quản trị thì không hiển thị các mục chuyển đổi của user thường.
    3. Bảng công nợ/số dư (`balances`) hiển thị `0 đ` trên toàn bộ các cột và thẻ thống kê mặc dù trong database có dữ
       liệu thật. Nguyên nhân gốc: Frontend đang đọc các tên trường sai lệch hoàn toàn so với Jackson DTO backend
       (`GroupBalanceReportRes`, `GroupBalanceItemRes`).
    4. Lỗi phát sinh thêm phát hiện trong quá trình rà soát:
        - `group-transactions.js`: Các action Duyệt đơn lẻ (`confirm`), Từ chối (`reject`), Duyệt hàng loạt (`bulk`),
          Sửa (`editTxn`) gửi sai định dạng key sang backend (`txnId`, `txnIds` hoặc thiếu `txn_id`), vi phạm cấu hình
          `SNAKE_CASE` của Jackson dẫn đến lỗi validation.
        - `users.js` (Admin): Chức năng Mở khóa tài khoản (`unlock`) gửi key `userId` thay vì `user_id`.
        - `group-detail.js`: Hàm đếm giao dịch `loadPending` gọi `/transactions/all` ngay cả khi Member đang xem trang,
          gây lỗi 403 nền.

## Quyết định đã chốt

- Không sửa code backend Java (`src/main/java`).
- Chỉ chỉnh sửa các file frontend tĩnh thuộc `src/main/resources/static/ui/`.
- Với Member: Khi truy cập sổ giao dịch, mặc định gọi endpoint `GET /groups/{groupId}/transactions` (`genera`), chỉ cho
  phép xem các giao dịch đã duyệt (`CONFIRMED`). Chỉ Owner/Treasurer mới gọi `/transactions/all`.
- Với User thường: Ẩn nút "🛡️ Quản trị" trên topbar (`.portal-nav`) và liên kết chân sidebar (`.side-foot`).
- Với Trang Quản trị (`area === 'admin'`): Tách biệt không gian Admin, ẩn các nút chuyển sang "👤 Cá nhân" và "👥 Quản lý
  nhóm" ở topbar và sidebar, ẩn chuông thông báo cá nhân trên topbar Admin.
- Đồng bộ toàn bộ tên trường của Báo cáo công nợ & Thẻ cá nhân theo đúng DTO backend: `fund_balance`, `target`,
  `is_settlement_enabled`, `total_needed_contribution`, `total_refunded`, `total_share_amount`, `net_balance`,
  `needed_contribution`.
- Sửa payload các API giao dịch và quản trị người dùng gửi đúng chuẩn `snake_case` (`txn_id`, `txn_ids`, `user_id`).

---

## 1. Các file sẽ thay đổi

### Frontend (Giao diện)

- `src/main/resources/static/ui/assets/js/layout.js`: Phân quyền hiển thị nút "🛡️ Quản trị" theo `Auth.isAdmin()`; tách
  biệt không gian trang quản trị Admin, ẩn các liên kết của user thường khi ở `area === 'admin'`.
- `src/main/resources/static/ui/manager/js/group-transactions.js`: Sửa logic phân nhánh API lấy danh sách giao dịch cho
  Member (`/transactions` - genera) và Reviewer (`/transactions/all`); sửa payload gửi `txn_id`, `txn_ids` chuẩn
  `snake_case` khi duyệt, từ chối, cập nhật giao dịch.
- `src/main/resources/static/ui/manager/js/group-detail.js`: Điều chỉnh hàm `loadPending` chỉ gọi `/transactions/all`
  đối với Reviewer; cập nhật bộ lọc vai trò giao diện.
- `src/main/resources/static/ui/manager/js/group-report.js`: Sửa các thuộc tính đọc dữ liệu công nợ trong
  `loadBalances()` khớp đúng với DTO backend (`GroupBalanceReportRes` và `GroupBalanceItemRes`).
- `src/main/resources/static/ui/manager/js/group-members.js`: Sửa hàm `updateMyMemberCard()` đọc đúng các trường
  `total_share_amount`, `total_refunded`, `net_balance`, `needed_contribution`.
- `src/main/resources/static/ui/manager/group-detail.html`: Cập nhật tiêu đề các cột trong bảng balances và thẻ tóm tắt
  cá nhân để khớp với dữ liệu phân tích công nợ thực tế.
- `src/main/resources/static/ui/admin/js/users.js`: Sửa payload mở khóa tài khoản gửi `user_id` thay vì `userId`.

### Tài liệu

- `docs/problem/ux/ui.md`: Đánh dấu đã sửa và hoàn tất các vấn đề được nêu.

---

## 2. Lý do thay đổi

### Lỗi 1: Member bấm vào tab giao dịch bị gọi `/transactions/all`

- **Vị trí
  backend**: [GroupTxnGetImpl.java:63-66](file:///d:/projects/java/server/src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnGetImpl.java#L63-L66)
  ```java
  memberAuthCache.get(groupId, operatorId)
          .filter(m -> !m.isMember()) // k dành cho member
          .filter(MemberAuthInfo::inGroupNotDeleted)
          .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
  ```
  Backend chặn không cho Member gọi `/all` và ném lỗi `FORBIDDEN (403)`.
- **Vị trí
  frontend**: [group-transactions.js:173](file:///d:/projects/java/server/src/main/resources/static/ui/manager/js/group-transactions.js#L173)
  Frontend gán cứng `path = '/transactions/all'` cho mọi trường hợp xem danh sách giao dịch chung, kể cả khi người dùng
  là `MEMBER`.
- **Giải pháp**: Nếu `G.isMember` là true, gán `path = '/transactions'` (endpoint `genera`), chỉ gọi `/transactions/all`
  khi `!G.isMember` (Owner hoặc Treasurer).

### Lỗi 2: Hiển thị nút Trang Quản trị cho user thường và Admin hiển thị mục của user thường

- **Vị trí
  frontend**: [layout.js:82](file:///d:/projects/java/server/src/main/resources/static/ui/assets/js/layout.js#L82)
  Trong `topbar`, `.portal-nav` render cứng `<a href="${UI_ROOT}admin/dashboard.html"...>🛡️ Quản trị</a>` cho tất cả mọi
  người mà không kiểm tra `Auth.isAdmin()`.
- **Vị trí
  frontend**: [layout.js:68-73](file:///d:/projects/java/server/src/main/resources/static/ui/assets/js/layout.js#L68-L73)
  và [layout.js:79-84](file:///d:/projects/java/server/src/main/resources/static/ui/assets/js/layout.js#L79-L84)
  Khi đang ở không gian quản trị (`area === 'admin'`), topbar vẫn hiển thị các nút chuyển đổi sang "👤 Cá nhân", "👥 Quản
  lý nhóm", và chuông thông báo trỏ tới `user/notifications.html`.
- **Giải pháp**:
    - Với User thường: Chỉ hiển thị 2 portal "👤 Cá nhân" và "👥 Quản lý nhóm". Không hiển thị nút "🛡️ Quản trị".
    - Với Admin khi ở `area === 'admin'`: Không hiển thị thanh chuyển đổi sang Cá nhân/Nhóm của user thường; ẩn chuông
      thông báo cá nhân, biến topbar thành khu vực quản trị chuyên biệt.

### Lỗi 3: Balances hiển thị `0 đ` do đọc sai DTO

- **Vị trí
  backend**: [GroupBalanceReportRes.java:6-13](file:///d:/projects/java/server/src/main/java/com/datn/financeapp/module/group/dto/response/report/GroupBalanceReportRes.java#L6-L13)
  và [GroupBalanceItemRes.java:16-24](file:///d:/projects/java/server/src/main/java/com/datn/financeapp/module/group/dto/response/report/GroupBalanceItemRes.java#L16-L24)
  Cấu hình `application.yml` chỉ định `property-naming-strategy: SNAKE_CASE`. DTO trả về:
    + Báo cáo nhóm: `group_id`, `target`, `fund_balance`, `total_needed_contribution`, `is_settlement_enabled`,
      `balances`.
    + Từng item: `user_id`, `full_name`, `status`, `total_refunded`, `total_share_amount`, `net_balance`,
      `needed_contribution`.
- **Vị trí
  frontend**: [group-report.js:48-90](file:///d:/projects/java/server/src/main/resources/static/ui/manager/js/group-report.js#L48-L90)
  và [group-members.js:81-86](file:///d:/projects/java/server/src/main/resources/static/ui/manager/js/group-members.js#L81-L86)
  Frontend đọc các trường không hề tồn tại trên DTO: `rep.total_group_expense`, `rep.total_fund_collected`,
  `rep.settlement_enabled`, `b.paid_out_of_pocket`, `b.refunded_amount`, `b.must_bear_amount`, `b.share_in_fund`,
  `b.need_to_pay`, `myItem.paid_to_fund`, `myItem.paid_out_of_pocket`. Toàn bộ giá trị bị `undefined`, dẫn đến hiển thị
  toàn bộ là `0 đ`.
- **Giải pháp**: Cập nhật lại toàn bộ mapping trường dữ liệu khớp chuẩn 100% với DTO backend.

### Lỗi 4: Lỗi payload JSON camelCase / thiếu trường khi thao tác

- [group-transactions.js:267, 281](file:///d:/projects/java/server/src/main/resources/static/ui/manager/js/group-transactions.js#L267):
  Gửi `{ txnId: t.id }` trong khi DTO backend `GroupTxnUpdateStatusReq` yêu cầu `txn_id`.
- [group-transactions.js:371](file:///d:/projects/java/server/src/main/resources/static/ui/manager/js/group-transactions.js#L371):
  Gửi `{ txnIds: ids }` trong khi DTO backend `BatchGroupTxnUpdateStatusReq` yêu cầu `txn_ids`.
- [group-transactions.js:345-350](file:///d:/projects/java/server/src/main/resources/static/ui/manager/js/group-transactions.js#L345-L350):
  Thiếu trường `txn_id` trong body khi gọi `PUT /groups/{groupId}/transactions/{id}` (DTO `GroupTxnUpdateReq` bắt buộc
  `@NotNull UUID txnId`).
- [users.js:64](file:///d:/projects/java/server/src/main/resources/static/ui/admin/js/users.js#L64): Gửi
  `{ userId: u.id }` trong khi DTO backend `UserUnlockRequest` yêu cầu `user_id`.

---

## 3. Hướng giải quyết / code xem trước

### A. Phân quyền giao diện và thanh điều hướng trong `layout.js`

```javascript
    // phân quyền không gian làm việc trên topbar
    let portalNavHtml = '';
    if (area === 'admin') {
        // không gian quản trị hệ thống: không hiển thị các mục của user thường
        portalNavHtml = '<span class="portal-pill active" title="Khu vực quản trị hệ thống">🛡️ Quản trị hệ thống</span>';
    } else {
        // không gian người dùng thường và quản lý nhóm
        portalNavHtml = `
            <a href="${UI_ROOT}user/dashboard.html" class="portal-pill ${area === 'user' ? 'active' : ''}" title="Trang tài chính cá nhân">👤 Cá nhân</a>
            <a href="${UI_ROOT}manager/dashboard.html" class="portal-pill ${area === 'manager' ? 'active' : ''}" title="Trang quản lý nhóm & quỹ">👥 Quản lý nhóm</a>
            ${isAdmin ? `<a href="${UI_ROOT}admin/dashboard.html" class="portal-pill" title="Trang quản trị hệ thống">🛡️ Quản trị</a>` : ''}
        `;
    }

    // chân sidebar chuyển không gian làm việc
    let sideFootHtml = '';
    if (area === 'admin') {
        sideFootHtml = `
            <div style="font-size:11px;color:#94a3b8;margin-bottom:4px">KHÔNG GIAN HỆ THỐNG</div>
            <div style="font-size:12px;color:var(--text-muted)">🛡️ Dành riêng cho Quản trị viên</div>
        `;
    } else {
        sideFootHtml = `
            <div style="font-size:11px;color:#94a3b8;margin-bottom:6px">CHUYỂN KHÔNG GIAN LÀM VIỆC</div>
            ${area !== 'user' ? `<div><a href="${UI_ROOT}user/dashboard.html">👤 Trang Cá nhân</a></div>` : ''}
            ${area !== 'manager' ? `<div><a href="${UI_ROOT}manager/dashboard.html">👥 Trang Quản lý nhóm</a></div>` : ''}
            ${isAdmin ? `<div><a href="${UI_ROOT}admin/dashboard.html">🛡️ Trang Quản trị</a></div>` : ''}
        `;
    }
```

### B. Gọi đúng API giao dịch cho Member (`genera`) và Reviewer (`all`) trong `group-transactions.js`

```javascript
    const mode = $('lMode').value;
    const query = { page: page - 1, size: TXN_PAGE_SIZE };
    const isMember = G.isMember;

    let path = '/transactions';
    if (mode === 'mine') {
        path = '/transactions/mine';
        if ($('lStatus').value) query.statuses = $('lStatus').value;
    } else if (mode === 'pending') {
        if (isMember) {
            // member chỉ xem được các giao dịch chờ duyệt của chính mình
            path = '/transactions/mine';
            query.statuses = 'PENDING';
        } else {
            // reviewer/owner xem toàn bộ giao dịch chờ duyệt của nhóm
            path = '/transactions/all';
            query.statuses = 'PENDING';
        }
    } else {
        // chế độ xem chung (all)
        if (isMember) {
            // member gọi endpoint genera (/transactions) chỉ lấy giao dịch confirmed
            path = '/transactions';
        } else {
            // reviewer gọi /transactions/all để xem toàn bộ trạng thái
            path = '/transactions/all';
            if ($('lStatus').value) query.statuses = $('lStatus').value;
        }
    }
```

### C. Khắc phục hiển thị Balances trong `group-report.js` và `group-members.js`

```javascript
// group-report.js: Đọc đúng DTO GroupBalanceReportRes và GroupBalanceItemRes
async function loadBalances() {
    const res = await api(G.path('/balances'));
    const rep = dataOf(res);
    if (!rep) return;

    const list = rep.balances || [];
    const isSettlement = !!rep.is_settlement_enabled;

    $('balanceCards').innerHTML = `
        <div class="stat">
            <div class="label">Số dư két quỹ hiện tại</div>
            <div class="value pos">${fmtMoney(rep.fund_balance)}</div>
            <div class="sub">tiền mặt trong két quỹ</div>
        </div>
        <div class="stat">
            <div class="label">Mục tiêu tích luỹ quỹ</div>
            <div class="value">${rep.target != null ? fmtMoney(rep.target) : 'Không đặt mục tiêu'}</div>
            <div class="sub">mục tiêu nhóm đề ra</div>
        </div>
        <div class="stat">
            <div class="label">Cơ chế quyết toán bù trừ</div>
            <div class="value" style="font-size:16px">${isSettlement ? '✓ Đang kích hoạt' : '✕ Đang tắt'}</div>
            <div class="sub">${isSettlement ? `Tổng cần bù thêm: ${fmtMoney(rep.total_needed_contribution)}` : 'Không tính thừa thiếu'}</div>
        </div>`;

    setTable($('balanceBody'), list.map(b => {
        const isMe = b.user_id === G.myId;
        const net = Number(b.net_balance ?? 0);
        const needed = Number(b.needed_contribution ?? 0);
        const needHtml = isSettlement
            ? (needed > 0 ? `<b class="neg">+ ${fmtMoney(needed)}</b>` : '<span class="pos">0 đ (Đủ)</span>')
            : '<span class="muted">-</span>';

        return `<tr>
            <td>
                <b>${esc(b.full_name || G.name(b.user_id))}</b>
                ${isMe ? '<span class="badge info">Tôi</span>' : ''}
            </td>
            <td>${badge(b.status || 'ACTIVE')}</td>
            <td class="right">${fmtMoney(b.total_refunded)}</td>
            <td class="right"><b>${fmtMoney(b.total_share_amount)}</b></td>
            <td class="right">${signedMoney(net)}</td>
            <td class="right">${needHtml}</td>
        </tr>`;
    }).join(''), 6, 'Chưa có dữ liệu phân bổ công nợ');
}
```

```javascript
// group-members.js: updateMyMemberCard đọc đúng DTO
async function updateMyMemberCard() {
    const rep = dataOf(await api(G.path('/balances'), { silent: true }));
    if (!rep || !rep.balances) {
        $('myContributed').textContent = '-';
        $('myOutOfPocket').textContent = '-';
        $('myNetBalance').textContent = '-';
        return;
    }

    const myId = G.myId;
    const myItem = rep.balances.find(b => b.user_id === myId);

    if (myItem) {
        $('myContributed').textContent = fmtMoney(myItem.total_share_amount);
        $('myOutOfPocket').textContent = fmtMoney(myItem.total_refunded);

        const net = Number(myItem.net_balance ?? 0);
        const needed = Number(myItem.needed_contribution ?? 0);
        if (net < 0 || needed > 0) {
            $('myNetBalance').innerHTML = `<span class="neg">Cần nộp thêm ${fmtMoney(needed || Math.abs(net))}</span>`;
            $('myNetSub').textContent = 'Bạn đang nộp thiếu so với chi phí phân bổ';
        } else if (net > 0) {
            $('myNetBalance').innerHTML = `<span class="pos">Được nhận lại ${fmtMoney(net)}</span>`;
            $('myNetSub').textContent = 'Quỹ nhóm đang giữ số dư của bạn';
        } else {
            $('myNetBalance').innerHTML = '<span class="pos">Đã cân bằng (0 đ)</span>';
            $('myNetSub').textContent = 'Công nợ của bạn đã được đối soát đủ';
        }
    }
}
```

---

## 4. Danh sách công việc chi tiết

- [ ] Cập nhật `src/main/resources/static/ui/assets/js/layout.js`:
    + Chỉ hiển thị nút "🛡️ Quản trị" cho tài khoản có quyền `ROLE_ADMIN`.
    + Tách biệt giao diện trang Quản trị (`area === 'admin'`): ẩn các lối tắt của user thường trên topbar, sidebar và ẩn
      chuông thông báo cá nhân.
- [ ] Cập nhật `src/main/resources/static/ui/manager/js/group-transactions.js`:
    + Phân biệt Member và Reviewer: Member gọi `/transactions` (genera), Reviewer gọi `/transactions/all`.
    + Chuẩn hóa payload gửi sang backend theo `snake_case` (`txn_id`, `txn_ids`).
    + Bổ sung trường `txn_id` trong body khi gửi yêu cầu chỉnh sửa giao dịch (PUT).
- [ ] Cập nhật `src/main/resources/static/ui/manager/js/group-detail.js`:
    + Chỉ gọi đếm giao dịch PENDING qua `/transactions/all` nếu `G.isReviewer` là true để Member không bị lỗi 403.
- [ ] Cập nhật `src/main/resources/static/ui/manager/group-detail.html`:
    + Cập nhật tiêu đề các cột trong bảng balances: Thành viên | Trạng thái | Đã được hoàn | Phần cùng chịu | Số dư ròng
      (Net) | Cần nộp thêm.
    + Cập nhật nhãn thống kê trên `memberSummaryCard` khớp với dữ liệu: Phần chi phí gánh chịu | Tiền quỹ đã hoàn trả |
      Cân đối tài chính.
- [ ] Cập nhật `src/main/resources/static/ui/manager/js/group-report.js`:
    + Sửa toàn bộ các trường đọc `rep` và `balances` khớp chuẩn `GroupBalanceReportRes` và `GroupBalanceItemRes`.
- [ ] Cập nhật `src/main/resources/static/ui/manager/js/group-members.js`:
    + Sửa hàm `updateMyMemberCard()` hiển thị đúng dữ liệu tài chính của thành viên đăng nhập.
- [ ] Cập nhật `src/main/resources/static/ui/admin/js/users.js`:
    + Sửa payload mở khóa tài khoản gửi `{ user_id: u.id, userId: u.id }`.
- [ ] Cập nhật `docs/problem/ux/ui.md`:
    + Đánh dấu trạng thái hoàn thành và giải pháp đã thực hiện.

---

## Ngoài phạm vi

- Không sửa bất kỳ file backend Java nào trong `src/main/java`.
- Các file legacy cũ trong `src/main/resources/static/ui/user/js/group-*.js` không can thiệp do toàn bộ hệ thống đã
  chuyển tiếp thống nhất sang `src/main/resources/static/ui/manager/`.

---

## Kiểm tra

- Quy tắc: **Không tự ý chạy test khi người dùng chưa yêu cầu**.
- Kiểm tra thủ công trên trình duyệt sau khi triển khai:
    1. Đăng nhập bằng tài khoản **Member thường**:
        - Kiểm tra topbar và sidebar: Xác nhận **không còn** nút "🛡️ Quản trị".
        - Vào chi tiết nhóm bất kỳ -> mở tab "Sổ thu chi & Giao dịch": Xác nhận không còn lỗi 403, giao diện gọi thành
          công endpoint `/transactions` (`genera`) và hiển thị các giao dịch đã duyệt.
        - Mở tab "Thành viên": Xác nhận thẻ cá nhân hiển thị đúng số tiền phân bổ và trạng thái công nợ thực tế thay vì
          0 đ.
        - Mở tab "Báo cáo & Quyết toán": Xác nhận bảng balances hiển thị đầy đủ số tiền hoàn, phần cùng chịu, số dư ròng
          và số tiền cần bù thay vì toàn bộ 0 đ.
    2. Đăng nhập bằng tài khoản **Quản trị viên (Admin)**:
        - Khi ở trang `user/dashboard.html` hoặc `manager/dashboard.html`: Có nút "🛡️ Quản trị" trên topbar để chuyển
          sang khu vực quản trị.
        - Khi ở trang Quản trị (`admin/dashboard.html`): Topbar và sidebar là không gian riêng biệt, không hiển thị các
          mục của user thường.
        - Kiểm tra chức năng Mở khóa tài khoản người dùng (`unlock`) hoạt động bình thường, không còn lỗi validation.
    3. Thử thao tác Duyệt đơn lẻ, Duyệt hàng loạt và Chỉnh sửa giao dịch với vai trò Chủ nhóm/Thủ quỹ để xác nhận
       payload `txn_id` và `txn_ids` hoạt động thông suốt.

---

## Câu hỏi còn mở

- Không có câu hỏi nào còn mở. Toàn bộ nguyên nhân gốc và phương án sửa chữa đã được đối chiếu chính xác với mã nguồn
  hiện tại.

---

## Bảng công việc

| Nhiệm vụ                                                                                      | Trạng thái |
|:----------------------------------------------------------------------------------------------|:-----------|
| Sửa phân quyền thanh điều hướng và giao diện quản trị trong `layout.js`                       | Chưa làm   |
| Sửa gọi API giao dịch cho Member (`genera`) và Reviewer (`all`) trong `group-transactions.js` | Chưa làm   |
| Sửa payload `txn_id`, `txn_ids` và bổ sung `txn_id` khi update giao dịch                      | Chưa làm   |
| Sửa `loadPending` trong `group-detail.js` tránh gọi 403 cho Member                            | Chưa làm   |
| Cập nhật tiêu đề bảng balances và thẻ cá nhân trong `group-detail.html`                       | Chưa làm   |
| Sửa hiển thị balances theo đúng DTO trong `group-report.js`                                   | Chưa làm   |
| Sửa thẻ thông tin cá nhân trong `group-members.js`                                            | Chưa làm   |
| Sửa payload mở khóa tài khoản người dùng trong `admin/js/users.js`                            | Chưa làm   |
| Cập nhật tài liệu ghi nhận sự cố `docs/problem/ux/ui.md`                                      | Chưa làm   |
