// Dựng khung trang (sidebar + topbar + API console) và chặn truy cập khi chưa đăng nhập / thiếu vai trò.
// Trang khai báo: <body data-area="user|admin" data-active="wallets" data-title="Ví"> và <main id="page">.
(function () {
    const NAV = {
        user: [
            ['dashboard', '🏠', 'Tổng quan'],
            ['wallets', '👛', 'Ví'],
            ['groups', '👥', 'Nhóm chung quỹ'],
            ['categories', '🗂️', 'Danh mục'],
            ['notifications', '🔔', 'Thông báo'],
            ['profile', '🙍', 'Tài khoản']
        ],
        admin: [
            ['dashboard', '📊', 'Tổng quan'],
            ['users', '🧑‍💼', 'Người dùng'],
            ['roles', '🔑', 'Vai trò & quyền'],
            ['groups', '👥', 'Nhóm'],
            ['group-transactions', '💸', 'Giao dịch nhóm'],
            ['logs', '🖥️', 'Log realtime']
        ]
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
        location.replace(UI_ROOT + 'user/dashboard.html');
        return;
    }

    const active = body.dataset.active;
    const title = body.dataset.title || document.title;
    const name = Auth.displayName();

    const links = NAV[area].map(([key, icon, label]) =>
        `<a href="${UI_ROOT}${area}/${key}.html" class="${key === active ? 'active' : ''}"><span>${icon}</span>${label}</a>`).join('');

    const switchLink = area === 'admin'
        ? `<a href="${UI_ROOT}user/dashboard.html">→ Sang giao diện người dùng</a>`
        : (Auth.isAdmin() ? `<a href="${UI_ROOT}admin/dashboard.html">→ Sang giao diện quản trị</a>` : '');

    const shell = document.createElement('div');
    shell.className = 'shell';
    shell.innerHTML = `
        <aside class="sidebar">
            <div class="brand">Finance AI<small>${area === 'admin' ? 'Khu vực quản trị' : 'Khu vực người dùng'}</small></div>
            <nav>${links}</nav>
            <div class="side-foot">${switchLink}</div>
        </aside>
        <div class="content">
            <header class="topbar">
                <h1>${esc(title)}</h1>
                <div class="right-box">
                    ${area === 'user' ? `<a class="bell" href="${UI_ROOT}user/notifications.html" title="Thông báo">🔔<span class="count hidden" id="bellCount">0</span></a>` : ''}
                    <div class="userbox"><div class="avatar">${esc(name.charAt(0).toUpperCase())}</div><span>${esc(name)}</span></div>
                    <button class="btn btn-secondary btn-sm" id="btnLogout">Đăng xuất</button>
                </div>
            </header>
        </div>`;

    const page = document.getElementById('page');
    shell.querySelector('.content').appendChild(page);
    body.prepend(shell);
    document.getElementById('btnLogout').onclick = () => Auth.logout();

    // ----- Chuông thông báo -----
    if (area === 'user') {
        api('/notifications', { query: { is_read: false, page: 1, page_size: 1 }, silent: true }).then(res => {
            const total = res.ok && res.data && res.data.pagination ? res.data.pagination.total_items : 0;
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
