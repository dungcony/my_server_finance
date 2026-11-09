const WALLET_TYPES = [
    ['CASH', 'Tiền mặt'],
    ['BANK', 'Ngân hàng'],
    ['E_WALLET', 'Ví điện tử'],
    ['LOAN', 'Khoản vay'],
    ['SAVINGS', 'Tiết kiệm']
];
const typeLabel = (t) => (WALLET_TYPES.find(x => x[0] === t) || [t, t])[1];

async function loadSummary() {
    const s = dataOf(await api('/reports/home', { silent: true }));
    if (!s) return;
    const b = s.balance || {};
    const personal = b.personal_total ?? b.personalTotal ?? 0;
    const shared = b.shared_total ?? b.sharedTotal ?? 0;

    $('summaryCards').innerHTML = `
        <div class="stat">
            <div class="label">Tổng tiền ví cá nhân</div>
            <div class="value pos">${fmtMoney(personal)}</div>
            <div class="sub">tổng tài sản sở hữu</div>
        </div>
        <div class="stat">
            <div class="label">Tổng tiền ví chung</div>
            <div class="value">${fmtMoney(shared)}</div>
            <div class="sub">các quỹ tham gia</div>
        </div>
        <div class="stat">
            <div class="label">Tổng cộng tài chính</div>
            <div class="value">${fmtMoney(Number(personal) + Number(shared))}</div>
            <div class="sub">cá nhân + quỹ chung</div>
        </div>`;
}

async function loadWallets() {
    const q = ($('fSearch').value || '').trim();
    const type = $('fType').value;
    const query = {};
    if (q) query.search = q;
    if (type) query.type = type;

    const res = await api('/wallets', { query });
    const data = dataOf(res) || {};
    // WalletListRes: { wallets: [WalletSumaryRes(id, name, icon, color)], pageMeta }
    const summaries = data.wallets || (Array.isArray(data) ? data : []);

    $('count').textContent = `(${summaries.length})`;

    // Lấy chi tiết từng ví để hiện balance & type
    const detailedWallets = await Promise.all(summaries.map(async w => {
        const detailRes = await api(`/wallets/${w.id}`, { silent: true });
        const detail = dataOf(detailRes);
        return detail || w;
    }));

    setTable($('walletBody'), detailedWallets.map(w => `
        <tr>
            <td>
                <span style="color:${esc(w.color || '#2563eb')}">●</span>
                <b>${esc(w.name)}</b>
                ${w.is_default || w.isDefault ? '<span class="badge info">Mặc định</span>' : ''}
            </td>
            <td>${badge(typeLabel(w.type), 'info')}</td>
            <td class="right"><b>${fmtMoney(w.balance ?? w.current_balance)}</b></td>
            <td>${w.is_default || w.isDefault ? '<span class="badge success">Có</span>' : '<span class="badge">Không</span>'}</td>
            <td class="nowrap">${fmtDateTime(w.created_at || w.createdAt)}</td>
            <td class="nowrap">
                <button class="btn btn-secondary btn-sm" data-act="detail" data-id="${w.id}">Chi tiết</button>
                <button class="btn btn-secondary btn-sm" data-act="edit" data-id="${w.id}">Sửa</button>
                <button class="btn btn-secondary btn-sm" data-act="history" data-id="${w.id}">Lịch sử đối soát</button>
                <button class="btn btn-danger btn-sm" data-act="delete" data-id="${w.id}">Xoá</button>
            </td>
        </tr>`).join(''), 6, 'Chưa có ví nào phù hợp');

    $('walletBody').querySelectorAll('button[data-act]').forEach(b => {
        b.onclick = () => ACTIONS[b.dataset.act](b.dataset.id, detailedWallets.find(w => w.id === b.dataset.id));
    });
}

const ACTIONS = {
    async detail(id, w) {
        const d = dataOf(await api(`/wallets/${id}`)) || w;
        if (!d) return;
        openModal('Chi tiết ví tài chính', `
            <div class="grid grid-2">
                <div><span class="muted">Tên ví</span><br><b style="font-size:16px">${esc(d.name)}</b></div>
                <div><span class="muted">Loại ví</span><br>${badge(typeLabel(d.type), 'info')}</div>
                <div><span class="muted">Số dư hiện tại</span><br><b style="font-size:18px;color:var(--primary)">${fmtMoney(d.balance ?? d.current_balance)}</b></div>
                <div><span class="muted">Ví mặc định</span><br>${(d.is_default || d.isDefault) ? 'Có' : 'Không'}</div>
                <div><span class="muted">Mã Icon</span><br><code>${esc(d.icon || d.iconCode || 'vi_tien')}</code></div>
                <div><span class="muted">Mã Màu</span><br><span style="color:${esc(d.color || '#2563eb')}">● ${esc(d.color || '#2563eb')}</span></div>
                <div style="grid-column:1/-1"><span class="muted">Thời gian tạo</span><br>${fmtDateTime(d.created_at || d.createdAt)}</div>
            </div>`);
    },

    async edit(id, w) {
        const v = await formModal('Sửa thông tin ví', [
            { name: 'name', label: 'Tên ví (1 - 50 ký tự)', value: w.name, required: true },
            { name: 'iconCode', label: 'Mã Icon (ví dụ: vi_tien, tien_mat, an_uong)', value: w.icon || w.iconCode || 'vi_tien', required: true },
            { name: 'color', label: 'Mã màu (#RRGGBB)', value: w.color || '#2563eb', required: true }
        ]);
        if (!v) return;

        const body = {
            name: v.name,
            iconCode: v.iconCode,
            color: v.color
        };

        if (toastResult(await api(`/wallets/${id}`, { method: 'PATCH', body }), 'Đã cập nhật ví thành công')) {
            refresh();
        }
    },

    async history(id) {
        const res = await api(`/wallets/${id}/reconciliations`);
        const h = dataOf(res);
        const list = (h && h.content) || (Array.isArray(h) ? h : []);

        openModal('Lịch sử đối soát số dư ví', `
            <div class="table-wrap">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Thời điểm</th>
                            <th class="right">Số dư trước</th>
                            <th class="right">Số dư sau</th>
                            <th class="right">Chênh lệch</th>
                            <th>Ghi chú</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${list.length ? list.map(item => `
                            <tr>
                                <td>${fmtDateTime(item.created_at || item.createdAt)}</td>
                                <td class="right">${fmtMoney(item.previous_balance || item.previousBalance)}</td>
                                <td class="right">${fmtMoney(item.actual_balance || item.actualBalance)}</td>
                                <td class="right">${signedMoney(item.difference)}</td>
                                <td>${esc(item.reason || '-')}</td>
                            </tr>
                        `).join('') : '<tr><td colspan="5" class="empty">Chưa có bản ghi đối soát nào cho ví này</td></tr>'}
                    </tbody>
                </table>
            </div>`);
    },

    async delete(id, w) {
        if (w && (w.is_default || w.isDefault)) {
            return toast('Không thể xóa ví mặc định của hệ thống', 'error');
        }
        if (!await confirmBox(`Bạn chắc chắn muốn xoá ví "${w.name}"?`)) return;

        if (toastResult(await api(`/wallets/${id}`, { method: 'DELETE' }), 'Đã xoá ví thành công')) {
            refresh();
        }
    }
};

$('btnCreate').onclick = async () => {
    const v = await formModal('Tạo ví cá nhân mới', [
        { name: 'name', label: 'Tên ví (1 - 50 ký tự)', required: true },
        { name: 'type', label: 'Loại ví', type: 'select', options: WALLET_TYPES, value: 'CASH' },
        { name: 'balance', label: 'Số dư ban đầu (VNĐ)', type: 'number', value: 0, required: true },
        { name: 'iconCode', label: 'Mã biểu tượng (Icon)', value: 'vi_tien', required: true },
        { name: 'color', label: 'Màu nhận diện (#RRGGBB)', value: '#2563eb', required: true }
    ], 'Tạo ví ngay');

    if (!v) return;

    const body = {
        name: v.name,
        type: v.type,
        balance: Number(v.balance || 0),
        iconCode: v.iconCode || 'vi_tien',
        color: v.color || '#2563eb'
    };

    if (toastResult(await api('/wallets', { method: 'POST', body }), 'Đã tạo ví mới thành công')) {
        refresh();
    }
};

function refresh() {
    loadSummary();
    loadWallets();
}

$('fSearch').oninput = loadWallets;
$('fType').onchange = loadWallets;
$('btnReload').onclick = refresh;

refresh();
