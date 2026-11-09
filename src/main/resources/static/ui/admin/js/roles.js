// Giá trị quyền = "{resource}:{action}" theo PermissionName.getValue()
const PERMISSIONS = [
    'users:read', 'users:update', 'users:delete',
    'roles:read', 'roles:update', 'roles:delete',
    'user_role:read', 'user_role:update', 'user_role:delete',
    'role_permission:read', 'role_permission:update', 'role_permission:delete',
    'categories:manage', 'system:view_stats', 'group:read'
];

async function load() {
    const roles = dataOf(await api('/admin/role/all')) || [];
    setTable($('body'), roles.map(r => `
        <tr>
            <td>${badge(r.name, 'purple')}</td>
            <td>${esc(r.level)}</td>
            <td>${esc(r.desc || '')}</td>
            <td>${(r.permissions || []).map(p => `<code title="${esc(p.desc || '')}">${esc(p.name)}</code>`).join(' ') || '<span class="muted">Chưa có quyền</span>'}</td>
            <td class="nowrap">
                <button class="btn btn-secondary btn-sm" data-act="view" data-name="${esc(r.name)}">Xem từ server</button>
                <button class="btn btn-sm" data-act="add" data-name="${esc(r.name)}">+ Gán quyền</button>
            </td>
        </tr>`).join(''), 5, 'Không có vai trò');
    $('body').querySelectorAll('[data-act]').forEach(b => b.onclick = () => act(b.dataset.act, b.dataset.name));
}

async function act(name, role) {
    if (name === 'view') {
        const perms = dataOf(await api(`/admin/role/${role}/permissions`)) || [];
        openModal(`Quyền của ${role}`, perms.length
            ? `<ul>${perms.map(p => `<li><code>${esc(p.name)}</code> — ${esc(p.desc || '')}</li>`).join('')}</ul>`
            : '<p class="muted">Chưa có quyền nào.</p>');
        return;
    }
    const v = await formModal(`Gán quyền cho ${role}`, [
        { name: 'permission_name', label: 'Quyền', type: 'select', options: PERMISSIONS.map(p => [p, p]) }
    ], 'Gán');
    if (v && toastResult(await api('/admin/role/permission', { method: 'POST', body: { role_name: role, permission_name: v.permission_name } }), 'Đã gán quyền')) load();
}

$('btnReload').onclick = load;
load();
