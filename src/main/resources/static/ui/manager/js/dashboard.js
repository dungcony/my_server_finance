// Dashboard khu vực Quản lý Nhóm & Quỹ chung
let allGroups = [];

async function loadManagerDashboard() {
    const res = await api('/groups');
    allGroups = dataOf(res) || [];

    const myId = Auth.userId();
    const ownerCount = allGroups.filter(g => g.my_role === 'OWNER').length;
    // Kiểm tra thủ quỹ: nếu g.is_treasurer hoặc fund.keepper_id === myId
    const treasurerCount = allGroups.filter(g => g.is_treasurer || (g.fund && g.fund.keepper_id === myId)).length;
    const memberOnlyCount = allGroups.filter(g => g.my_role !== 'OWNER' && !g.is_treasurer && !(g.fund && g.fund.keepper_id === myId)).length;

    const totalFund = allGroups.reduce((sum, g) => sum + Number(g.fund_balance || (g.fund ? g.fund.current_balance : 0) || 0), 0);

    $('sTotalGroups').textContent = allGroups.length;
    $('sTotalGroupsSub').textContent = `${allGroups.filter(g => g.status === 'ACTIVE').length} nhóm ACTIVE`;
    $('sTotalFund').textContent = fmtMoney(totalFund);
    $('sRolesSummary').textContent = `${ownerCount} 👑 · ${treasurerCount} 💰`;
    $('sRolesSub').textContent = `${ownerCount} Chủ nhóm · ${treasurerCount} Thủ quỹ`;
    $('sMemberOnly').textContent = memberOnlyCount;

    renderGroupsTable();
}

function renderGroupsTable() {
    const roleFilter = $('roleFilter').value;
    const myId = Auth.userId();

    const filtered = allGroups.filter(g => {
        const isOwner = g.my_role === 'OWNER';
        const isTreasurer = g.is_treasurer || (g.fund && g.fund.keepper_id === myId);
        if (roleFilter === 'OWNER') return isOwner;
        if (roleFilter === 'TREASURER') return isTreasurer;
        if (roleFilter === 'MEMBER') return !isOwner && !isTreasurer;
        return true;
    });

    setTable($('groupBody'), filtered.map(g => {
        const isOwner = g.my_role === 'OWNER';
        const isTreasurer = g.is_treasurer || (g.fund && g.fund.keepper_id === myId);

        let roleBadge = '';
        if (isOwner) {
            roleBadge = '<span class="badge purple">👑 Chủ nhóm</span>';
            if (isTreasurer) roleBadge += ' <span class="badge warning">💰 Kiêm Thủ quỹ</span>';
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
            <td>${g.member_count ?? (g.members ? g.members.length : '-')}</td>
            <td class="right"><b>${fmtMoney(bal)}</b></td>
            <td class="right">${g.target != null ? fmtMoney(g.target) : '-'}</td>
            <td>
                <a class="btn btn-sm" href="./group-detail.html?id=${esc(g.id)}">Vào quản lý →</a>
            </td>
        </tr>`;
    }).join(''), 8, 'Không có nhóm nào phù hợp bộ lọc');

    $('groupBody').querySelectorAll('[data-copy]').forEach(b => b.onclick = () => copyText(b.dataset.copy));
}

$('roleFilter').onchange = renderGroupsTable;
$('btnReload').onclick = loadManagerDashboard;

loadManagerDashboard();
