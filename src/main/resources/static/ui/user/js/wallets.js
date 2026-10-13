const WALLET_TYPES = [['cash', 'Tiền mặt'], ['bank', 'Ngân hàng'], ['e_wallet', 'Ví điện tử'], ['credit_card', 'Thẻ tín dụng']];
const typeLabel = (t) => (WALLET_TYPES.find(x => x[0] === t) || [t, t])[1];

async function loadSummary() {
    const s = dataOf(await api('/wallets/summary', { silent: true }));
    if (!s) return;
    $('summaryCards').innerHTML = `
        <div class="stat"><div class="label">Ví cá nhân (${s.personal_wallet_count})</div><div class="value">${fmtMoney(s.personal_total)}</div></div>
        <div class="stat"><div class="label">Ví chung (${s.shared_wallet_count})</div><div class="value">${fmtMoney(s.shared_total)}</div></div>`
        + (s.by_type || []).map(t => `<div class="stat"><div class="label">${esc(typeLabel(t.type))} (${t.wallet_count})</div><div class="value">${fmtMoney(t.total)}</div></div>`).join('');
}

async function loadWallets() {
    const res = await api('/wallets', {
        query: { type: $('fType').value, only_in_total: $('fOnlyTotal').checked ? true : '', include_shared: $('fShared').checked }
    });
    const list = dataOf(res) || [];
    $('count').textContent = `(${list.length})`;
    setTable($('walletBody'), list.map(w => `
        <tr>
            <td><span style="color:${esc(w.color || 'inherit')}">●</span> <b>${esc(w.name)}</b> ${w.is_default ? badge('Mặc định', 'info') : ''}</td>
            <td>${esc(typeLabel(w.type))}</td>
            <td class="right"><b>${fmtMoney(w.current_balance)}</b></td>
            <td>${w.include_in_total ? badge('Có', 'success') : badge('Không')}</td>
            <td>${w.group_id ? `<a href="./group-detail.html?id=${esc(w.group_id)}">${shortId(w.group_id)}</a>` : '-'}</td>
            <td class="nowrap">
                <button class="btn btn-secondary btn-sm" data-act="detail" data-id="${w.id}">Chi tiết</button>
                <button class="btn btn-secondary btn-sm" data-act="edit" data-id="${w.id}">Sửa</button>
                <button class="btn btn-secondary btn-sm" data-act="reconcile" data-id="${w.id}">Đối soát</button>
                <button class="btn btn-danger btn-sm" data-act="delete" data-id="${w.id}">Xoá</button>
            </td>
        </tr>`).join(''), 6, 'Chưa có ví nào');
    $('walletBody').querySelectorAll('button[data-act]').forEach(b => b.onclick = () => ACTIONS[b.dataset.act](b.dataset.id, list.find(w => w.id === b.dataset.id)));
}

const ACTIONS = {
    async detail(id) {
        const w = dataOf(await api(`/wallets/${id}`));
        if (!w) return;
        const st = w.stats || {};
        openModal('Chi tiết ví', `
            <div class="grid grid-2">
                <div><span class="muted">Tên</span><br><b>${esc(w.name)}</b></div>
                <div><span class="muted">Loại</span><br>${esc(typeLabel(w.type))}</div>
                <div><span class="muted">Số dư ban đầu</span><br>${fmtMoney(w.initial_balance)}</div>
                <div><span class="muted">Số dư hiện tại</span><br><b>${fmtMoney(w.current_balance)}</b></div>
                <div><span class="muted">Dự kiến</span><br>${w.projected_balance != null ? fmtMoney(w.projected_balance) : '-'}</div>
                <div><span class="muted">Ví chung</span><br>${w.is_shared ? 'Có' : 'Không'}</div>
                <div><span class="muted">Số giao dịch</span><br>${st.transaction_count ?? 0}</div>
                <div><span class="muted">Thu / Chi tháng này</span><br>${fmtMoney(st.income_this_month)} / ${fmtMoney(st.expense_this_month)}</div>
                <div><span class="muted">Giao dịch gần nhất</span><br>${fmtDate(st.last_transaction_date)}</div>
                <div><span class="muted">Tạo lúc</span><br>${fmtDateTime(w.created_at)}</div>
            </div>`);
    },

    async edit(id, w) {
        const v = await formModal('Sửa ví', [
            { name: 'name', label: 'Tên', value: w.name, required: true },
            { name: 'icon', label: 'Icon', value: w.icon || '' },
            { name: 'color', label: 'Màu (#RRGGBB)', value: w.color || '' },
            { name: 'include_in_total', label: 'Tính vào tổng tài sản', type: 'checkbox', value: w.include_in_total },
            { name: 'is_default', label: 'Đặt làm ví mặc định', type: 'checkbox', value: w.is_default }
        ]);
        if (!v) return;
        const body = { name: v.name, include_in_total: v.include_in_total };
        if (v.icon) body.icon = v.icon;
        if (v.color) body.color = v.color;
        if (v.is_default && !w.is_default) body.is_default = true;
        if (toastResult(await api(`/wallets/${id}`, { method: 'PATCH', body }), 'Đã cập nhật ví')) refresh();
    },

    async reconcile(id) {
        const res = await api(`/wallets/${id}/reconcile`, { method: 'POST' });
        const r = dataOf(res);
        if (!r) return;
        if (r.matches) return toast('Số dư khớp với tổng giao dịch', 'success');
        openModal('Số dư lệch', `
            <p>Số dư lưu: <b>${fmtMoney(r.stored_balance)}</b><br>Số dư tính lại: <b>${fmtMoney(r.computed_balance)}</b><br>Chênh lệch: ${signedMoney(r.difference)}</p>`, [
            { label: 'Đóng', kind: 'btn-secondary' },
            {
                label: 'Tự sửa số dư', kind: 'btn-warning', onClick: async () => {
                    if (toastResult(await api(`/wallets/${id}/reconcile`, { method: 'POST', query: { auto_fix: true } }), 'Đã sửa số dư')) refresh();
                }
            }]);
    },

    async delete(id, w) {
        const v = await formModal(`Xoá ví "${w.name}"`, [
            { name: 'delete_transactions', label: 'Xoá luôn các giao dịch của ví', type: 'checkbox', value: false }
        ], 'Xoá');
        if (!v) return;
        if (toastResult(await api(`/wallets/${id}`, { method: 'DELETE', query: { delete_transactions: v.delete_transactions } }), 'Đã xoá ví')) refresh();
    }
};

$('btnCreate').onclick = async () => {
    const v = await formModal('Tạo ví', [
        { name: 'name', label: 'Tên ví', required: true },
        { name: 'type', label: 'Loại', type: 'select', options: WALLET_TYPES, value: 'cash' },
        { name: 'initial_balance', label: 'Số dư ban đầu (đ)', type: 'number', value: 0, required: true },
        { name: 'icon', label: 'Icon (tuỳ chọn)' },
        { name: 'color', label: 'Màu #RRGGBB (tuỳ chọn)', placeholder: '#2563eb' },
        { name: 'group_id', label: 'Group ID (tạo ví chung, tuỳ chọn)' },
        { name: 'include_in_total', label: 'Tính vào tổng tài sản', type: 'checkbox', value: true }
    ], 'Tạo');
    if (!v) return;
    const body = { name: v.name, type: v.type, initial_balance: Number(v.initial_balance), include_in_total: v.include_in_total };
    ['icon', 'color', 'group_id'].forEach(k => { if (v[k]) body[k] = v[k]; });
    if (toastResult(await api('/wallets', { method: 'POST', body }), 'Đã tạo ví')) refresh();
};

function refresh() { loadSummary(); loadWallets(); }

['fType', 'fOnlyTotal', 'fShared'].forEach(id => $(id).onchange = loadWallets);
$('btnReload').onclick = refresh;
refresh();
