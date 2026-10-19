// Quản lý danh sách nhóm, tạo nhóm, tham gia bằng mã mời
const UUID_RE = /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/g;
let groupList = [];

async function loadGroups() {
    const list = dataOf(await api('/groups')) || [];
    groupList = list;
    renderList();
}

function renderList() {
    const q = ($('searchName').value || '').trim().toLowerCase();
    const role = $('filterRole').value;
    const status = $('filterStatus').value;
    const myId = Auth.userId();

    const filtered = groupList.filter(g => {
        const isOwner = g.my_role === 'OWNER';
        const isTreasurer = g.is_treasurer || (g.fund && g.fund.keepper_id === myId);

        if (q && !g.name.toLowerCase().includes(q)) return false;
        if (status && g.status !== status) return false;
        if (role === 'OWNER' && !isOwner) return false;
        if (role === 'TREASURER' && !isTreasurer) return false;
        if (role === 'MEMBER' && (isOwner || isTreasurer)) return false;
        return true;
    });

    setTable($('body'), filtered.map(g => {
        const isOwner = g.my_role === 'OWNER';
        const isTreasurer = g.is_treasurer || (g.fund && g.fund.keepper_id === myId);

        let roleBadge = '';
        if (isOwner) {
            roleBadge = '<span class="badge purple">👑 Chủ nhóm</span>';
            if (isTreasurer) roleBadge += ' <span class="badge warning">💰 Thủ quỹ</span>';
        } else if (isTreasurer) {
            roleBadge = '<span class="badge warning">💰 Thủ quỹ</span>';
        } else {
            roleBadge = '<span class="badge info">👤 Thành viên</span>';
        }

        const bal = g.fund_balance ?? (g.fund ? g.fund.current_balance : 0);

        return `<tr>
            <td>
                <a href="./group-detail.html?id=${esc(g.id)}"><b>${esc(g.name)}</b></a>
                ${g.description ? `<div class="muted" style="font-size:12px">${esc(g.description)}</div>` : ''}
            </td>
            <td>${roleBadge}</td>
            <td>${badge(g.status)}</td>
            <td><code>${esc(g.invite_code || '-')}</code> <button class="btn btn-secondary btn-sm" data-copy="${esc(g.invite_code)}">Copy</button></td>
            <td>${g.member_count ?? 0}</td>
            <td class="right"><b>${fmtMoney(bal)}</b></td>
            <td class="right">${g.target != null ? fmtMoney(g.target) : '-'}</td>
            <td>
                <a class="btn btn-secondary btn-sm" href="./group-detail.html?id=${esc(g.id)}">Mở chi tiết →</a>
            </td>
        </tr>`;
    }).join(''), 8, 'Bạn chưa tham gia nhóm nào phù hợp');

    $('body').querySelectorAll('[data-copy]').forEach(b => b.onclick = () => copyText(b.dataset.copy));
}

$('btnJoin').onclick = async () => {
    const code = $('inviteCode').value.trim();
    if (!code) return toast('Vui lòng nhập mã mời', 'error');

    // GroupController.java expects query param ?code=...
    const res = await api('/groups/join', {
        method: 'POST',
        query: { code },
        body: { invite_code: code }
    });

    if (toastResult(res, 'Đã gửi yêu cầu tham gia nhóm thành công!')) {
        $('inviteCode').value = '';
        loadGroups();
    }
};

$('btnCreate').onclick = async () => {
    const v = await formModal('Tạo nhóm quỹ mới', [
        { name: 'name', label: 'Tên nhóm (tối đa 100 ký tự)', required: true },
        { name: 'description', label: 'Mô tả nhóm' },
        { name: 'target', label: 'Mục tiêu quỹ (đ, tuỳ chọn)', type: 'number' },
        { name: 'members', label: 'User ID thành viên cùng tham gia (mỗi dòng hoặc dấu phẩy 1 UUID)', type: 'textarea' },
        { name: 'is_settlement_enabled', label: 'Bật tính năng tính thừa thiếu / quyết toán', type: 'checkbox', value: true },
        { name: 'is_join_without_confirm', label: 'Cho phép vào nhóm tự động không cần duyệt', type: 'checkbox', value: true }
    ], 'Tạo nhóm ngay');

    if (!v) return;

    let memberIds = [];
    if (v.members) {
        memberIds = [...new Set(v.members.match(UUID_RE) || [])];
    }

    const res = await api('/groups', {
        method: 'POST',
        body: {
            name: v.name,
            description: v.description || null,
            target: v.target !== '' && v.target != null ? Number(v.target) : null,
            is_settlement_enabled: v.is_settlement_enabled,
            is_join_without_confirm: v.is_join_without_confirm,
            members: memberIds
        }
    });

    if (toastResult(res, 'Đã tạo nhóm thành công!')) {
        const g = dataOf(res);
        if (g && g.id) {
            location.href = `./group-detail.html?id=${g.id}`;
        } else {
            loadGroups();
        }
    }
};

$('searchName').oninput = renderList;
$('filterRole').onchange = renderList;
$('filterStatus').onchange = renderList;
$('btnReload').onclick = loadGroups;

// URL action handling (e.g. ?action=create or ?action=join)
const urlAction = queryParam('action');
if (urlAction === 'create') {
    setTimeout(() => $('btnCreate').click(), 300);
} else if (urlAction === 'join') {
    $('inviteCode').focus();
}

loadGroups();
