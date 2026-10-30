// Danh sách nhóm, tạo nhóm, vào nhóm bằng mã mời.
const UUID_RE = /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/g;

async function loadGroups() {
    const list = dataOf(await api('/groups')) || [];
    setTable($('body'), list.map(g => `
        <tr>
            <td><a href="./group-detail.html?id=${esc(g.id)}"><b>${esc(g.name)}</b></a></td>
            <td>${badge(g.my_role)}</td>
            <td>${badge(g.status)}</td>
            <td><code>${esc(g.invite_code)}</code> <button class="btn btn-secondary btn-sm" data-copy="${esc(g.invite_code)}">Copy</button></td>
            <td>${g.member_count ?? 0}</td>
            <td class="right">${fmtMoney(g.fund_balance)}</td>
            <td class="right">${g.target != null ? fmtMoney(g.target) : '-'}</td>
            <td><a class="btn btn-secondary btn-sm" href="./group-detail.html?id=${esc(g.id)}">Mở</a></td>
        </tr>`).join(''), 8, 'Bạn chưa tham gia nhóm nào');
    $('body').querySelectorAll('[data-copy]').forEach(b => b.onclick = () => copyText(b.dataset.copy));
}

$('btnJoin').onclick = async () => {
    const code = $('inviteCode').value.trim();
    if (!code) return toast('Vui lòng nhập mã mời', 'error');
    if (toastResult(await api('/groups/join', { method: 'POST', body: { invite_code: code } }), 'Đã gửi yêu cầu vào nhóm')) {
        $('inviteCode').value = '';
        loadGroups();
    }
};

$('btnCreate').onclick = async () => {
    const v = await formModal('Tạo nhóm', [
        { name: 'name', label: 'Tên nhóm (≤ 100)', required: true },
        { name: 'description', label: 'Mô tả (≤ 255)' },
        { name: 'target', label: 'Mục tiêu quỹ (đ, tuỳ chọn)', type: 'number' },
        { name: 'members', label: 'User ID thành viên khác (mỗi dòng/dấu phẩy một ID, ≥ 1)', type: 'textarea', required: true },
        { name: 'is_settlement_enabled', label: 'Bật tính thừa thiếu', type: 'checkbox', value: true },
        { name: 'is_join_without_confirm', label: 'Vào nhóm không cần duyệt', type: 'checkbox', value: true }
    ], 'Tạo nhóm');
    if (!v) return;
    const members = [...new Set(v.members.match(UUID_RE) || [])];
    const res = await api('/groups', {
        method: 'POST',
        body: {
            name: v.name,
            description: v.description || null,
            target: v.target !== '' ? Number(v.target) : null,
            is_settlement_enabled: v.is_settlement_enabled,
            is_join_without_confirm: v.is_join_without_confirm,
            members
        }
    });
    if (toastResult(res, 'Đã tạo nhóm')) {
        const g = dataOf(res);
        if (g && g.id) location.href = `./group-detail.html?id=${g.id}`; else loadGroups();
    }
};

$('btnReload').onclick = loadGroups;
loadGroups();
