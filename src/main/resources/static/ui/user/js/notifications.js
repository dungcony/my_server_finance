const GROUP_TXN_PREFIX = 'GROUP_TXN_';
let currentPage = 1;

async function load(page = 1) {
    currentPage = page;
    const res = await api('/notifications', { query: { is_read: $('fRead').value, page, page_size: 15 } });
    const items = (res.ok && res.data && res.data.data) || [];
    setTable($('body'), items.map(n => `
        <tr>
            <td>${badge(n.type, n.type.startsWith(GROUP_TXN_PREFIX) ? 'warning' : 'info')}</td>
            <td><b>${esc(n.title)}</b><div class="muted">${esc(n.content)}</div>${n.reference_id ? `<code>${esc(n.reference_id)}</code>` : ''}${n.data && n.data.group_id ? ` <span class="muted">nhóm <code>${esc(n.data.group_id)}</code></span>` : ''}</td>
            <td class="nowrap">${fmtDateTime(n.created_at)}</td>
            <td>${n.is_read ? badge('Đã đọc') : badge('Chưa đọc', 'warning')}</td>
            <td class="nowrap">
                ${n.is_read ? '' : `<button class="btn btn-secondary btn-sm" data-read="${n.id}">Đánh dấu đã đọc</button>`}
            </td>
        </tr>`).join(''), 5, 'Không có thông báo');
    renderPager($('pager'), res.data && res.data.pagination, load);
    $('body').querySelectorAll('[data-read]').forEach(b => b.onclick = async () => {
        if (toastResult(await api(`/notifications/${b.dataset.read}/read`, { method: 'PATCH' }), 'Đã đánh dấu đã đọc')) load(currentPage);
    });
}

$('fRead').onchange = () => load(1);
$('btnReload').onclick = () => load(currentPage);
load();
