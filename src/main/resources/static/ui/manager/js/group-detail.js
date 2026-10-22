// Lõi trang chi tiết nhóm: Quản lý trạng thái, Phân quyền vai trò (Owner, Treasurer, Member),
// và Chế độ xem thử vai trò (Role Simulator) phục vụ kiểm thử ứng dụng thực tế.
const G = {
    id: queryParam('id'),
    group: null,
    members: [],
    simRole: null, // null (auto), 'OWNER', 'TREASURER', 'MEMBER'
    tabs: {},
    activeTab: 'members',

    get fund() { return this.group && this.group.fund; },
    get myId() { return Auth.userId(); },

    // Vai trò thực tế từ Server
    get realIsOwner() { return !!this.group && this.group.my_role === 'OWNER'; },
    get realIsTreasurer() { return !!this.fund && this.fund.keepper_id === this.myId; },
    get realRole() {
        if (this.realIsOwner) return 'OWNER';
        if (this.realIsTreasurer) return 'TREASURER';
        return 'MEMBER';
    },

    // Vai trò hiện tại đang hiển thị (tính theo chế độ test simulation hoặc thực tế)
    get currentRole() {
        return this.simRole || this.realRole;
    },

    get isOwner() { return this.currentRole === 'OWNER'; },
    get isTreasurer() { return this.currentRole === 'TREASURER'; },
    get isReviewer() { return this.isOwner || this.isTreasurer; },
    get isMember() { return this.currentRole === 'MEMBER'; },
    get archived() { return !!this.group && this.group.status === 'ARCHIVED'; },

    can(need) {
        if (need === 'owner') return this.isOwner;
        if (need === 'treasurer') return this.isTreasurer;
        if (need === 'reviewer') return this.isReviewer;
        return true;
    },

    path(suffix = '') {
        return `/groups/${this.id}${suffix}`;
    },

    name(uid) {
        if (!uid) return '-';
        if (uid === this.myId) return 'Tôi (' + Auth.displayName() + ')';
        const m = this.members.find(x => x.user_id === uid);
        return (m && m.display_name) || shortId(uid);
    },

    activeMembers() {
        return this.members.filter(m => m.status === 'ACTIVE');
    },

    async init() {
        if (!this.id) {
            toast('Thiếu mã ID nhóm', 'error');
            location.href = './groups.html';
            return;
        }

        // Tab click
        $('tabs').querySelectorAll('button').forEach(b => {
            b.onclick = () => this.openTab(b.dataset.tab);
        });

        // Copy invite code
        $('gCopyInvite').onclick = e => {
            e.preventDefault();
            copyText(this.group && this.group.invite_code);
        };

        // Role Simulator buttons
        const simBtns = document.querySelectorAll('.role-sim-btn');
        simBtns.forEach(btn => {
            btn.onclick = () => {
                simBtns.forEach(b => b.classList.remove('active', 'owner', 'treasurer', 'member'));
                const r = btn.dataset.role;
                this.simRole = r ? r : null;
                btn.classList.add('active');
                if (r === 'OWNER') btn.classList.add('owner');
                else if (r === 'TREASURER') btn.classList.add('treasurer');
                else if (r === 'MEMBER') btn.classList.add('member');

                toast(`Đã chuyển góc nhìn xem thử: ${r || 'Tài khoản thực'}`, 'info');
                this.applyRoleView();
            };
        });

        await this.reload();
    },

    async reload() {
        const res = await api(this.path());
        const g = dataOf(res);
        if (!g) return;

        this.group = g;
        this.members = g.members || [];
        $('tabMemberCount').textContent = this.members.length;

        this.renderHeader();
        this.applyRoleView();
        this.loadPending();
        this.openTab(this.activeTab);
    },

    async loadPending() {
        const pendingMembers = (this.members || []).filter(m => m.status === 'PENDING').length;
        let pendingTxns = 0;
        try {
            const pPath = this.isReviewer ? '/transactions/all' : '/transactions/mine';
            const res = await api(this.path(pPath), { query: { statuses: 'PENDING', size: 1 }, silent: true });
            const d = dataOf(res) || {};
            pendingTxns = d.meta?.total_items ?? d.totalElements ?? (d.items ? d.items.length : 0);
        } catch(e) {}
        $('gPending').textContent = `${pendingMembers} TV · ${pendingTxns} GD`;
    },

    renderHeader() {
        const g = this.group;
        document.title = `${g.name} - Quản lý Nhóm & Quỹ`;
        $('gName').textContent = g.name;

        // Header Badges
        const badges = [];
        if (this.realIsOwner) badges.push('<span class="badge purple">👑 Chủ nhóm</span>');
        if (this.realIsTreasurer) badges.push('<span class="badge warning">💰 Thủ quỹ</span>');
        if (!this.realIsOwner && !this.realIsTreasurer) badges.push('<span class="badge info">👤 Thành viên</span>');
        badges.push(badge(g.status));
        $('gBadges').innerHTML = badges.join(' ');

        $('gBalance').textContent = fmtMoney(this.fund && this.fund.current_balance);
        $('gTarget').textContent = g.target != null ? `Mục tiêu quỹ: ${fmtMoney(g.target)}` : 'Không đặt mục tiêu';
        $('gInvite').textContent = g.invite_code || '-';
        $('gSettings').innerHTML = `Quyết toán bù trừ: <b>${g.is_settlement_enabled ? 'Bật' : 'Tắt'}</b><br>Vào nhóm không duyệt: <b>${g.is_join_without_confirm ? 'Bật' : 'Tắt'}</b>`;
        $('gDesc').textContent = g.description || 'Chưa có mô tả nhóm.';

        // Render Action Buttons
        const actions = $('gActions');
        actions.innerHTML = `
            <span id="ownerActionBtns" class="btn-row">
                <button class="btn btn-secondary" id="btnEditGroup">⚙️ Sửa cài đặt</button>
                ${this.archived
                    ? '<button class="btn btn-secondary" id="btnUnarchive">📂 Mở lại</button>'
                    : '<button class="btn btn-secondary" id="btnArchive">📦 Lưu trữ</button>'}
                <button class="btn btn-danger" id="btnDeleteGroup">🗑️ Xoá nhóm</button>
            </span>
            <button class="btn btn-secondary" id="btnLeave">🚪 Rời nhóm</button>`;

        $('btnEditGroup').onclick = () => this.editGroup();
        const arch = $('btnArchive') || $('btnUnarchive');
        if (arch) {
            arch.onclick = () => this.simplePost(this.archived ? '/unarchive' : '/archive', this.archived ? 'Đã mở lại nhóm' : 'Đã lưu trữ nhóm');
        }
        $('btnDeleteGroup').onclick = () => this.deleteGroup();
        $('btnLeave').onclick = () => this.leave();
    },

    // Cập nhật giao diện toàn diện theo vai trò (Owner, Treasurer, Member)
    applyRoleView() {
        const role = this.currentRole;
        const banner = $('roleBanner');
        const icon = $('roleIcon');
        const title = $('roleTitle');
        const desc = $('roleDesc');
        const powers = $('rolePowers');

        banner.className = 'role-banner ' + role.toLowerCase();

        if (role === 'OWNER') {
            icon.textContent = '👑';
            title.innerHTML = 'VAI TRÒ: CHỦ NHÓM (GROUP OWNER) <span class="badge purple">Toàn quyền cao nhất</span>';
            desc.textContent = 'Bạn là người sáng lập/quản trị viên cao nhất. Bạn có toàn quyền cấu hình nhóm, phê duyệt thành viên mới, bổ nhiệm/thay đổi Thủ quỹ, duyệt mọi giao dịch thu chi và giải tán nhóm.';
            powers.innerHTML = `
                <span>⚙️ Cài đặt nhóm</span>
                <span>👥 Quản lý thành viên & Duyệt TV</span>
                <span>💰 Bổ nhiệm Thủ quỹ</span>
                <span>✓ Duyệt mọi giao dịch thu chi</span>
                <span>📊 Báo cáo tài chính & Quyết toán</span>
                <span>🗑️ Giải tán nhóm</span>`;
        } else if (role === 'TREASURER') {
            icon.textContent = '💰';
            title.innerHTML = 'VAI TRÒ: THỦ QUỸ (TREASURER) <span class="badge warning">Két quỹ & Thu chi</span>';
            desc.textContent = 'Bạn chịu trách nhiệm giữ két tiền và quản lý tài chính quỹ. Bạn có quyền ghi nhận thu/chi quỹ, xét duyệt các yêu cầu hoàn tiền chi hộ của thành viên và thực hiện đối soát số dư két thực tế.';
            powers.innerHTML = `
                <span>💰 Quản lý két tiền</span>
                <span>⚖️ Kiểm kê đối soát số dư két</span>
                <span>✓ Duyệt thu - chi tiền quỹ</span>
                <span>💸 Duyệt yêu cầu hoàn tiền chi hộ</span>
                <span>📊 Báo cáo tài chính & Công nợ</span>`;
        } else {
            icon.textContent = '👤';
            title.innerHTML = 'VAI TRÒ: THÀNH VIÊN THƯỜNG (MEMBER) <span class="badge info">Thành viên đóng góp</span>';
            desc.textContent = 'Bạn là thành viên của quỹ nhóm. Bạn có thể theo dõi số dư quỹ chung, nộp tiền đóng góp, báo cáo chi tiêu cá nhân chi hộ nhóm để xin hoàn trả và xem minh bạch toàn bộ sổ thu chi.';
            powers.innerHTML = `
                <span>💸 Nộp tiền góp quỹ (Pending)</span>
                <span>🧾 Báo cáo chi hộ nhóm (Xin hoàn tiền)</span>
                <span>📊 Theo dõi phần tiền cá nhân trong quỹ</span>
                <span>🔍 Xem minh bạch lịch sử thu chi</span>
                <span>🚪 Rời khỏi nhóm</span>`;
        }

        // Quyền hiển thị các khối trong trang
        const isOwner = this.isOwner;
        const isTreasurer = this.isTreasurer;
        const isReviewer = this.isReviewer;
        const isMember = this.isMember;

        // Nút quản trị nhóm trên header: chỉ Chủ nhóm mới có
        $('ownerActionBtns').classList.toggle('hidden', !isOwner);

        // Nút quản lý thành viên: chỉ Chủ nhóm mới thấy
        $('ownerMemberBtns').classList.toggle('hidden', !isOwner);
        $('thMemberActions').classList.toggle('hidden', !isOwner);

        // Khối tóm tắt cá nhân của Member:
        $('memberSummaryCard').classList.toggle('hidden', false);

        // Tab Két quỹ & Kiểm kê:
        // Đổi thủ quỹ: chỉ Owner
        $('cardKeeperChange').classList.toggle('hidden', !isOwner);
        // Kiểm kê két quỹ: Reviewer (Owner + Treasurer)
        $('cardReconcile').classList.toggle('hidden', !isReviewer);
        // Hộp thông điệp dành cho Member
        $('fundMemberNote').classList.toggle('hidden', isReviewer);

        // Tab Giao dịch:
        // Cột chọn hàng loạt: chỉ Reviewer
        $('thBulkCheck').classList.toggle('hidden', !isReviewer);

        // Form Giao dịch: điều chỉnh tiêu đề & hướng dẫn theo vai trò
        if (isMember) {
            $('formTxnTitle').textContent = '➕ Nộp quỹ hoặc Báo cáo chi hộ nhóm';
            $('formTxnHint').textContent = 'Yêu cầu của bạn sẽ ở trạng thái PENDING chờ Thủ quỹ / Chủ nhóm xét duyệt.';
            $('tSourceGroup').classList.add('hidden'); // Member không tự chọn quỹ
            $('tSource').value = 'PERSONAL';
            $('tTransactorGroup').classList.add('hidden');
            $('tTransactor').value = this.myId;
            if ($('lMode')) {
                $('lMode').innerHTML = `
                    <option value="all">Sổ giao dịch chung (Đã duyệt)</option>
                    <option value="mine">Chỉ giao dịch của tôi</option>
                    <option value="pending">⏳ Giao dịch của tôi chờ duyệt</option>
                `;
            }
        } else {
            $('formTxnTitle').textContent = isOwner ? '👑 Ghi nhận / Điều chỉnh giao dịch (Chủ nhóm)' : '💰 Ghi nhận giao dịch quỹ (Thủ quỹ)';
            $('formTxnHint').textContent = 'Giao dịch do Chủ nhóm/Thủ quỹ tạo sẽ được CONFIRMED trực tiếp vào quỹ.';
            $('tSourceGroup').classList.remove('hidden');
            $('tTransactorGroup').classList.remove('hidden');
            if ($('lMode')) {
                $('lMode').innerHTML = `
                    <option value="all">Toàn bộ giao dịch nhóm</option>
                    <option value="mine">Chỉ giao dịch của tôi</option>
                    <option value="pending">⏳ Đang chờ duyệt (PENDING)</option>
                `;
            }
        }

        // Tải lại nội dung tab hiện tại để cập nhật bảng
        if (this.tabs[this.activeTab]) {
            this.tabs[this.activeTab]();
        }
    },

    openTab(tab) {
        this.activeTab = tab;
        $('tabs').querySelectorAll('button').forEach(b => {
            b.classList.toggle('active', b.dataset.tab === tab);
        });
        document.querySelectorAll('.tab-pane').forEach(p => {
            p.classList.toggle('active', p.id === 'pane-' + tab);
        });
        if (this.tabs[tab]) this.tabs[tab]();
    },

    async simplePost(suffix, okMsg) {
        if (toastResult(await api(this.path(suffix), { method: 'POST' }), okMsg)) this.reload();
    },

    async editGroup() {
        const g = this.group;
        const v = await formModal('Sửa cài đặt nhóm', [
            { name: 'name', label: 'Tên nhóm', value: g.name, required: true },
            { name: 'description', label: 'Mô tả nhóm', value: g.description || '' },
            { name: 'target', label: 'Mục tiêu quỹ (VNĐ)', type: 'number', value: g.target ?? '' },
            { name: 'is_settlement_enabled', label: 'Bật tính năng tính thừa thiếu & quyết toán', type: 'checkbox', value: g.is_settlement_enabled },
            { name: 'is_join_without_confirm', label: 'Cho phép vào nhóm tự động không cần duyệt', type: 'checkbox', value: g.is_join_without_confirm }
        ]);
        if (!v) return;

        const body = {
            name: v.name || undefined,
            description: v.description || undefined,
            target: v.target !== '' ? Number(v.target) : undefined,
            is_settlement_enabled: v.is_settlement_enabled,
            is_join_without_confirm: v.is_join_without_confirm
        };

        if (toastResult(await api(this.path(), { method: 'PATCH', body }), 'Đã cập nhật cấu hình nhóm')) {
            this.reload();
        }
    },

    async deleteGroup() {
        if (!await confirmBox('Xác nhận xoá giải tán nhóm? Yêu cầu số dư quỹ bằng 0 và không còn giao dịch PENDING.')) return;
        const res = await api(this.path(), { method: 'DELETE' });
        if (toastResult(res, 'Đã xoá giải tán nhóm')) {
            setTimeout(() => location.href = './groups.html', 800);
        }
    },

    async leave() {
        if (!await confirmBox('Bạn chắc chắn muốn rời khỏi nhóm này?')) return;
        const res = await api(this.path('/leave'), { method: 'POST' });
        if (toastResult(res, 'Đã rời nhóm thành công')) {
            setTimeout(() => location.href = './groups.html', 800);
        }
    }
};

function equalSplit(amount, userIds) {
    const ids = [...userIds].sort();
    const base = Math.floor(amount / ids.length);
    const rem = amount % ids.length;
    return ids.map((uid, i) => ({ user_id: uid, share_amount: base + (i < rem ? 1 : 0) }));
}

function memberOptions(onlyActive = true) {
    return (onlyActive ? G.activeMembers() : G.members).map(m => [
        m.user_id,
        `${m.display_name || shortId(m.user_id)}${m.user_id === G.myId ? ' (tôi)' : ''}`
    ]);
}
