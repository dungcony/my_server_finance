const ROLES = [['ROLE_USER', 'ROLE_USER'], ['ROLE_ADMIN', 'ROLE_ADMIN']];
let users = [];

async function load() {
    users = dataOf(await api('/admin/user/all')) || [];
    render();
}

function render() {
    const q = $('search').value.trim().toLowerCase();
    const st = $('fStatus').value;
    const list = users.filter(u =>
        (!st || u.status === st) &&
        (!q || `${u.email} ${u.first_name || ''} ${u.last_name || ''}`.toLowerCase().includes(q)));
    $('count').textContent = `(${list.length}/${users.length})`;

    setTable($('body'), list.map(u => {
        const isBlocked = u.status === 'BLOCKED' || u.status === 'blocked';
        const lockBtn = isBlocked
            ? `<button class="btn btn-success btn-sm" data-act="unlock" data-id="${u.id}">Mở khoá</button>`
            : `<button class="btn btn-warning btn-sm" data-act="lock" data-id="${u.id}">Khoá</button>`;

        return `<tr>
            <td><b>${esc(u.email)}</b><div><code>${esc(u.id)}</code></div></td>
            <td>${esc([u.first_name, u.last_name].filter(Boolean).join(' ') || '-')}</td>
            <td>${badge(u.plan, 'info')}</td>
            <td>${badge(u.status)}</td>
            <td>${(u.roles || []).map(r => badge(r.name, 'purple')).join(' ')}</td>
            <td class="nowrap">
                <button class="btn btn-secondary btn-sm" data-act="detail" data-id="${u.id}">Chi tiết</button>
                <button class="btn btn-secondary btn-sm" data-act="addRole" data-id="${u.id}">+ Vai trò</button>
                <button class="btn btn-secondary btn-sm" data-act="removeRole" data-id="${u.id}">− Vai trò</button>
                ${lockBtn}
                <button class="btn btn-danger btn-sm" data-act="delete" data-id="${u.id}">Xoá</button>
            </td>
        </tr>`;
    }).join(''), 6, 'Không có người dùng');

    $('body').querySelectorAll('[data-act]').forEach(b => {
        b.onclick = () => act(b.dataset.act, users.find(u => u.id === b.dataset.id));
    });
}

async function roleChange(u, method, title) {
    const v = await formModal(`${title}: ${u.email}`, [{ name: 'role', label: 'Vai trò', type: 'select', options: ROLES }]);
    if (!v) return;
    const res = await api('/admin/user/role', { method, body: { user_id: u.id, role_name: v.role } });
    if (toastResult(res, 'Đã cập nhật vai trò')) load();
}

async function act(name, u) {
    if (name === 'detail') {
        const d = dataOf(await api(`/admin/user/${u.id}`));
        if (d) openModal('Chi tiết người dùng', `<pre style="white-space:pre-wrap;word-break:break-all">${esc(JSON.stringify(d, null, 2))}</pre>`);
    } else if (name === 'addRole') {
        await roleChange(u, 'POST', 'Gán vai trò');
    } else if (name === 'removeRole') {
        await roleChange(u, 'DELETE', 'Gỡ vai trò');
    } else if (name === 'lock') {
        const v = await formModal(`Khoá tài khoản ${u.email}`, [{ name: 'reason', label: 'Lý do khoá (≤ 500 ký tự)', type: 'textarea' }], 'Xác nhận khoá');
        if (v && toastResult(await api('/admin/user/lock', { method: 'PATCH', body: { user_id: u.id, reason: v.reason || null } }), 'Đã khoá tài khoản')) load();
    } else if (name === 'unlock') {
        if (await confirmBox(`Mở khoá tài khoản cho ${u.email}?`)) {
            const res = await api('/admin/user/unlock', { method: 'PATCH', body: { user_id: u.id, userId: u.id } });
            if (toastResult(res, 'Đã mở khoá tài khoản thành công')) load();
        }
    } else if (name === 'delete') {
        if (await confirmBox(`Xoá tài khoản người dùng ${u.email}?`) && toastResult(await api(`/admin/user/${u.id}`, { method: 'DELETE' }), 'Đã xoá người dùng')) load();
    }
}

$('search').oninput = render;
$('fStatus').onchange = render;
$('btnReload').onclick = load;
load();
