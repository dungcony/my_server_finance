// Dựng khung trang (sidebar + topbar + portal switcher + API console) và chặn truy cập khi chưa đăng nhập.
// Trang khai báo: <body data-area="user|manager|admin" data-active="wallets" data-title="Ví"> và <main id="page">.
(function () {
    const NAV = {
        user: [
            ['dashboard', '🏠', 'Tổng quan cá nhân'],
            ['transactions', '💸', 'Sổ giao dịch'],
            ['wallets', '👛', 'Ví cá nhân'],
            ['budgets', '📊', 'Ngân sách'],
            ['goals', '🎯', 'Mục tiêu tích lũy'],
            ['debts', '📝', 'Sổ nợ & Cho vay'],
            ['categories', '🗂️', 'Danh mục'],
            ['reports', '📈', 'Báo cáo'],
            ['notifications', '🔔', 'Thông báo'],
            ['profile', '🙎', 'Tài khoản']
        ],
        manager: [
            ['dashboard', '📊', 'Tổng quan quản lý'],
            ['groups', '👥', 'Danh sách nhóm']
        ],
        admin: [
            ['dashboard', '📊', 'Tổng quan quản trị'],
            ['users', '🧑‍💼', 'Người dùng'],
            ['roles', '🔑', 'Vai trò & quyền'],
            ['groups', '👥', 'Nhóm hệ thống'],
            ['group-transactions', '💸', 'Giao dịch nhóm'],
            ['webhooks', '🔔', 'Webhook thanh toán'],
            ['logs', '🖥️', 'Log realtime']
        ]
    };

    const AREA_META = {
        user: { label: 'Tài chính cá nhân', icon: '👤' },
        manager: { label: 'Quản lý nhóm & Quỹ', icon: '👥' },
        admin: { label: 'Quản trị hệ thống', icon: '🛡️' }
    };

    const body = document.body;
    const area = body.dataset.area;
    if (!area) return;

    // ----- Guard -----
    if (!Auth.token()) {
        location.replace(UI_ROOT + 'login.html');
        return;
    }
    if (area === 'admin' && !Auth.isAdmin()) {
        // Cho phép xem nếu đang test hoặc redirect
        const testMode = localStorage.getItem('finance_ai_test_mode') === 'true';
        if (!testMode) {
            toast('Khu vực này yêu cầu quyền quản trị viên (ROLE_ADMIN)', 'error');
            setTimeout(() => location.replace(UI_ROOT + 'user/dashboard.html'), 1500);
            return;
        }
    }

    const active = body.dataset.active;
    const title = body.dataset.title || document.title;
    const name = Auth.displayName();
    const isAdmin = Auth.isAdmin();

    const links = (NAV[area] || []).map(([key, icon, label]) =>
        `<a href="${UI_ROOT}${area}/${key}.html" class="${key === active ? 'active' : ''}"><span>${icon}</span>${label}</a>`).join('');

    const shell = document.createElement('div');
    shell.className = 'shell';
    shell.innerHTML = `
        <aside class="sidebar">
            <div class="brand">
                Finance AI
                <small>${AREA_META[area]?.icon || ''} ${AREA_META[area]?.label || area}</small>
            </div>
            <nav>${links}</nav>
            <div class="side-foot">
                ${area === 'admin' ? `
                    <div style="font-size:11px;color:#94a3b8;margin-bottom:4px">KHÔNG GIAN HỆ THỐNG</div>
                    <div style="font-size:12px;color:var(--text-muted)">🛡️ Dành riêng cho Quản trị viên</div>
                ` : `
                    <div style="font-size:11px;color:#94a3b8;margin-bottom:6px">CHUYỂN KHÔNG GIAN LÀM VIỆC</div>
                    ${area !== 'user' ? `<div><a href="${UI_ROOT}user/dashboard.html">👤 Trang Cá nhân</a></div>` : ''}\
                    ${area !== 'manager' ? `<div><a href="${UI_ROOT}manager/dashboard.html">👥 Trang Quản lý nhóm</a></div>` : ''}\
                    ${isAdmin ? `<div><a href="${UI_ROOT}admin/dashboard.html">🛡️ Trang Quản trị</a></div>` : ''}
                `}
            </div>
        </aside>
        <div class="content">
            <header class="topbar">
                <div class="left-box">
                    <h1>${esc(title)}</h1>
                    <div class="portal-nav">
                        ${area === 'admin' ? `
                            <span class="portal-pill active" title="Khu vực quản trị hệ thống">🛡️ Quản trị hệ thống</span>
                        ` : `
                            <a href="${UI_ROOT}user/dashboard.html" class="portal-pill ${area === 'user' ? 'active' : ''}" title="Trang tài chính cá nhân">👤 Cá nhân</a>
                            <a href="${UI_ROOT}manager/dashboard.html" class="portal-pill ${area === 'manager' ? 'active' : ''}" title="Trang quản lý nhóm & quỹ">👥 Quản lý nhóm</a>
                            ${isAdmin ? `<a href="${UI_ROOT}admin/dashboard.html" class="portal-pill" title="Trang quản trị hệ thống">🛡️ Quản trị</a>` : ''}
                        `}
                    </div>
                </div>
                <div class="right-box">
                    ${area !== 'admin' ? `<a class="bell" href="${UI_ROOT}user/notifications.html" title="Thông báo">🔔<span class="count hidden" id="bellCount">0</span></a>` : ''}
                    <div class="userbox">
                        <div class="avatar">${esc(name.charAt(0).toUpperCase())}</div>
                        <div>
                            <div style="font-weight:600;font-size:13px;line-height:1.2">${esc(name)}</div>
                            <div style="font-size:11px;color:var(--text-muted)">${isAdmin ? '<span class="badge purple" style="font-size:10px;padding:1px 5px">Admin</span>' : '<span class="badge info" style="font-size:10px;padding:1px 5px">User</span>'}</div>
                        </div>
                    </div>
                    <button class="btn btn-secondary btn-sm" id="btnLogout" title="Đăng xuất">Đăng xuất</button>
                </div>
            </header>
        </div>`;

    const page = document.getElementById('page');
    shell.querySelector('.content').appendChild(page);
    body.prepend(shell);
    document.getElementById('btnLogout').onclick = () => Auth.logout();

    // chuông thông báo cá nhân
    if (area !== 'admin') {
        api('/notifications', { query: { is_read: false, page: 1, page_size: 1 }, silent: true }).then(res => {
            const total = (res.ok && res.data?.data?.meta?.total_items) ?? (res.data?.pagination?.total_items) ?? 0;
            const el = document.getElementById('bellCount');
            if (el && total > 0) {
                el.textContent = total > 99 ? '99+' : total;
                el.classList.remove('hidden');
            }
        });
    }

    // ----- API console (hiện request/response gần nhất để test) -----
    const dock = document.createElement('div');
    dock.id = 'apiDock';
    dock.innerHTML = `
        <div class="dock-head"><span>📡 API console — <span id="dockStatus">chưa có request</span></span><span>▲</span></div>
        <div class="dock-body"><pre id="dockBody">Kết quả request gần nhất sẽ hiện ở đây.</pre>
        <button class="btn btn-secondary btn-sm" id="dockCopy">Sao chép</button></div>`;
    body.appendChild(dock);
    dock.querySelector('.dock-head').onclick = () => dock.classList.toggle('open');
    dock.querySelector('#dockCopy').onclick = () => copyText(document.getElementById('dockBody').textContent);

    window.addEventListener('api-call', e => {
        const { method, url, body: reqBody, result } = e.detail;
        const st = document.getElementById('dockStatus');
        st.innerHTML = `<span class="${result.ok ? 'ok' : 'bad'}">${result.status || 'ERR'}</span> ${esc(method)} ${esc(url)} · ${result.ms}ms`;
        const resp = result.error ? String(result.error) : (typeof result.data === 'object' ? JSON.stringify(result.data, null, 2) : result.data);
        document.getElementById('dockBody').textContent =
            (reqBody ? '// request\n' + JSON.stringify(reqBody, null, 2) + '\n\n' : '') + '// response\n' + resp;
    });
})();
