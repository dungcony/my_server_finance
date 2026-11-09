// Lõi trang chi tiết nhóm: trạng thái chung (G), tiêu đề nhóm, chuyển tab, phân quyền hiển thị.
// Các tab nằm ở group-members.js, group-transactions.js, group-fund.js, group-report.js và đăng ký loader vào G.tabs.
const G = {
    id: queryParam('id'),
    group: null,
    members: [],
    showAll: false,
    tabs: {},
    activeTab: 'members',

    get fund() { return this.group && this.group.fund; },
    get myId() { return Auth.userId(); },
    get isOwner() { return !!this.group && this.group.my_role === 'OWNER'; },
    get isTreasurer() { return !!this.fund && this.fund.keepper_id === this.myId; },
    get isReviewer() { return this.isOwner || this.isTreasurer; },
    get archived() { return !!this.group && this.group.status === 'ARCHIVED'; },

    // Quyền hiển thị nút; chế độ test cho phép hiện hết để thử lỗi quyền từ server
    can(need) {
        if (this.showAll) return true;
        return need === 'owner' ? this.isOwner : need === 'reviewer' ? this.isReviewer : true;
    },

    path(suffix = '') {
        return `/groups/${this.id}${suffix}`;
    },

    // Tên hiển thị của thành viên theo user id (tra trong danh sách thành viên đã nạp)
    name(uid) {
        if (!uid) return '-';
        if (uid === this.myId) return 'Tôi';
        const m = this.members.find(x => x.user_id === uid);
        return (m && m.display_name) || shortId(uid);
    },

    activeMembers() {
        return this.members.filter(m => m.status === 'ACTIVE');
    },

    async init() {
        if (!this.id) {
            toast('Thiếu id nhóm', 'error');
            location.href = './groups.html';
            return;
        }
        $('tabs').querySelectorAll('button').forEach(b => b.onclick = () => this.openTab(b.dataset.tab));
        $('chkShowAll').onchange = e => { this.showAll = e.target.checked; this.applyVisibility(); };
        $('gCopyInvite').onclick = e => { e.preventDefault(); copyText(this.group && this.group.invite_code); };
        await this.reload();
    },

    // Nạp lại thông tin nhóm (kèm danh sách thành viên và quỹ) rồi vẽ lại tab đang mở
    async reload() {
        const g = dataOf(await api(this.path()));
        if (!g) return;
        this.group = g;
        this.members = g.members || [];
        this.renderHeader();
        this.applyVisibility();
        this.loadPending();
        this.openTab(this.activeTab);
    },

    async loadPending() {
        const p = dataOf(await api(this.path('/pending-count'), { silent: true }));
        $('gPending').textContent = p ? `${p.pending_members ?? 0} · ${p.pending_transactions ?? 0}` : '-';
    },

    renderHeader() {
        const g = this.group;
        document.title = g.name + ' - Finance AI';
        $('gName').textContent = g.name;
        $('gBadges').innerHTML = `${badge(g.my_role)} ${badge(g.status)} ${this.isTreasurer ? badge('Thủ quỹ', 'warning') : ''}`;
        $('gBalance').textContent = fmtMoney(this.fund && this.fund.current_balance);
        $('gTarget').textContent = g.target != null ? `Mục tiêu ${fmtMoney(g.target)}` : 'Không đặt mục tiêu';
        $('gInvite').textContent = g.invite_code || '-';
        $('gSettings').innerHTML = `Tính thừa thiếu: <b>${g.is_settlement_enabled ? 'Bật' : 'Tắt'}</b><br>Vào nhóm không duyệt: <b>${g.is_join_without_confirm ? 'Bật' : 'Tắt'}</b>`;
        $('gDesc').textContent = g.description || '';

        const actions = $('gActions');
        actions.innerHTML = `
            <span data-need="owner" class="btn-row">
                <button class="btn btn-secondary" id="btnEditGroup">Sửa nhóm</button>
                ${this.archived
                    ? '<button class="btn btn-secondary" id="btnUnarchive">Mở lại</button>'
                    : '<button class="btn btn-secondary" id="btnArchive">Lưu trữ</button>'}
                <button class="btn btn-danger" id="btnDeleteGroup">Xoá nhóm</button>
            </span>
            <button class="btn btn-secondary" id="btnLeave">Rời nhóm</button>`;
        $('btnEditGroup').onclick = () => this.editGroup();
        const arch = $('btnArchive') || $('btnUnarchive');
        arch.onclick = () => this.simplePost(this.archived ? '/unarchive' : '/archive', this.archived ? 'Đã mở lại nhóm' : 'Đã lưu trữ nhóm');
        $('btnDeleteGroup').onclick = () => this.deleteGroup();
        $('btnLeave').onclick = () => this.leave();
    },

    // Ẩn/hiện các phần tử có data-need theo vai trò hiện tại
    applyVisibility() {
        document.querySelectorAll('[data-need]').forEach(el => el.classList.toggle('hidden', !this.can(el.dataset.need)));
        const reportBtn = $('tabs').querySelector('[data-tab="report"]');
        reportBtn.classList.toggle('hidden', !this.can('reviewer'));
        if (!this.can('reviewer') && this.activeTab === 'report') this.openTab('members');
    },

    openTab(tab) {
        this.activeTab = tab;
        $('tabs').querySelectorAll('button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
        document.querySelectorAll('.tab-pane').forEach(p => p.classList.toggle('active', p.id === 'pane-' + tab));
        if (this.tabs[tab]) this.tabs[tab]();
    },

    async simplePost(suffix, okMsg) {
        if (toastResult(await api(this.path(suffix), { method: 'POST' }), okMsg)) this.reload();
    },

    async editGroup() {
        const g = this.group;
        const v = await formModal('Sửa nhóm', [
            { name: 'name', label: 'Tên nhóm', value: g.name },
            { name: 'description', label: 'Mô tả', value: g.description || '' },
            { name: 'target', label: 'Mục tiêu (đ)', type: 'number', value: g.target ?? '' },
            { name: 'is_settlement_enabled', label: 'Bật tính thừa thiếu', type: 'checkbox', value: g.is_settlement_enabled },
            { name: 'is_join_without_confirm', label: 'Vào nhóm không cần duyệt', type: 'checkbox', value: g.is_join_without_confirm }
        ]);
        if (!v) return;
        const body = {
            name: v.name || undefined,
            description: v.description || undefined,
            target: v.target !== '' ? Number(v.target) : undefined,
            is_settlement_enabled: v.is_settlement_enabled,
            is_join_without_confirm: v.is_join_without_confirm
        };
        if (toastResult(await api(this.path(), { method: 'PATCH', body }), 'Đã cập nhật nhóm')) this.reload();
    },

    async deleteGroup() {
        if (!await confirmBox('Xoá nhóm? Yêu cầu số dư quỹ bằng 0 và không còn giao dịch PENDING.')) return;
        const res = await api(this.path(), { method: 'DELETE' });
        if (toastResult(res, 'Đã xoá nhóm')) setTimeout(() => location.href = './groups.html', 800);
    },

    async leave() {
        if (!await confirmBox('Bạn chắc chắn muốn rời nhóm?')) return;
        const res = await api(this.path('/leave'), { method: 'POST' });
        if (toastResult(res, 'Đã rời nhóm')) setTimeout(() => location.href = './groups.html', 800);
    }
};

// Chia đều theo quy tắc pipeline.md mục 2: sắp theo user_id tăng dần, phần dư cộng 1đ cho những người đầu tiên
function equalSplit(amount, userIds) {
    const ids = [...userIds].sort();
    const base = Math.floor(amount / ids.length);
    const rem = amount % ids.length;
    return ids.map((uid, i) => ({ user_id: uid, share_amount: base + (i < rem ? 1 : 0) }));
}

// Dropdown thành viên (mặc định chỉ ACTIVE)
function memberOptions(onlyActive = true) {
    return (onlyActive ? G.activeMembers() : G.members).map(m => [m.user_id, `${m.display_name || shortId(m.user_id)}${m.user_id === G.myId ? ' (tôi)' : ''}`]);
}
