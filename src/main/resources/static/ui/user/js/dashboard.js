(async function () {
    const [wallet, groups, unread] = await Promise.all([
        api('/wallets/summary'),
        api('/groups'),
        api('/notifications', { query: { is_read: false, page: 1, page_size: 1 }, silent: true })
    ]);

    const w = dataOf(wallet);
    if (w) {
        $('sPersonal').textContent = fmtMoney(w.personal_total);
        $('sPersonalSub').textContent = `${w.personal_wallet_count} ví`;
        $('sShared').textContent = fmtMoney(w.shared_total);
        $('sSharedSub').textContent = `${w.shared_wallet_count} ví`;
        setTable($('typeBody'), (w.by_type || []).map(t =>
            `<tr><td>${badge(t.type, 'info')}</td><td>${t.wallet_count}</td><td class="right">${fmtMoney(t.total)}</td></tr>`).join(''), 3, 'Chưa có ví');
    }

    const g = dataOf(groups) || [];
    $('sGroups').textContent = g.length;
    $('sGroupsSub').textContent = `${g.filter(x => x.my_role === 'OWNER').length} nhóm do tôi làm chủ`;
    setTable($('groupBody'), g.slice(0, 6).map(x =>
        `<tr><td><a href="./group-detail.html?id=${esc(x.id)}">${esc(x.name)}</a></td><td>${badge(x.my_role)}</td><td>${fmtMoney(x.fund_balance)}</td></tr>`).join(''), 3, 'Chưa tham gia nhóm nào');

    const u = unread.data && unread.data.pagination;
    $('sUnread').textContent = u ? u.total_items : '-';
})();
