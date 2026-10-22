(async function () {
    const [reportHome, groups, unread, walletsList] = await Promise.all([
        api('/reports/home', { silent: true }),
        api('/groups'),
        api('/notifications', { query: { is_read: false, page: 1, page_size: 1 }, silent: true }),
        api('/wallets', { silent: true })
    ]);

    const rh = dataOf(reportHome) || {};
    const bal = rh.balance || {};
    const personalTotal = bal.personal_total ?? bal.personalTotal ?? 0;
    const sharedTotal = bal.shared_total ?? bal.sharedTotal ?? 0;

    $('sPersonal').textContent = fmtMoney(personalTotal);
    $('sShared').textContent = fmtMoney(sharedTotal);

    // Danh sách ví từ /reports/home (top_wallets) hoặc từ /wallets
    const topW = rh.top_wallets || rh.topWallets || [];
    const wData = dataOf(walletsList) || {};
    const rawWallets = wData.wallets || (Array.isArray(wData) ? wData : []);

    $('sPersonalSub').textContent = `${topW.length || rawWallets.length} ví hoạt động`;
    $('sSharedSub').textContent = 'tổng số dư chung';

    if (topW.length > 0) {
        setTable($('typeBody'), topW.map(t =>
            `<tr>
                <td><b>${esc(t.name)}</b></td>
                <td>${badge(t.type, 'info')}</td>
                <td class="right">${fmtMoney(t.current_balance ?? t.currentBalance ?? t.balance)}</td>
            </tr>`), 3, 'Chưa có ví');
    } else if (rawWallets.length > 0) {
        setTable($('typeBody'), rawWallets.map(w =>
            `<tr>
                <td><b>${esc(w.name)}</b></td>
                <td>${badge(w.icon || 'VÍ', 'info')}</td>
                <td class="right">-</td>
            </tr>`), 3, 'Chưa có ví');
    } else {
        setTable($('typeBody'), [], 3, 'Chưa có ví nào');
    }

    const g = dataOf(groups) || [];
    const myId = Auth.userId();
    $('sGroups').textContent = g.length;
    $('sGroupsSub').textContent = `${g.filter(x => x.my_role === 'OWNER').length} nhóm làm Chủ`;

    setTable($('groupBody'), g.slice(0, 6).map(x => {
        const isOwner = x.my_role === 'OWNER';
        const isTreasurer = x.is_treasurer || (x.fund && x.fund.keepper_id === myId);

        let roleBadge = '';
        if (isOwner) roleBadge = '<span class="badge purple">👑 Chủ nhóm</span>';
        else if (isTreasurer) roleBadge = '<span class="badge warning">💰 Thủ quỹ</span>';
        else roleBadge = '<span class="badge info">👤 Thành viên</span>';

        return `<tr>
            <td><a href="../manager/group-detail.html?id=${esc(x.id)}"><b>${esc(x.name)}</b></a></td>
            <td>${roleBadge}</td>
            <td class="right">${fmtMoney(x.fund_balance)}</td>
        </tr>`;
    }).join(''), 3, 'Chưa tham gia nhóm nào');

    const u = unread && unread.data && unread.data.pagination;
    $('sUnread').textContent = u ? u.total_items : '-';
})();
