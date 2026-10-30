const GROUP_TXN_PREFIX = 'GROUP_TXN_';
let currentPage = 1;

async function load(page = 1) {
    currentPage = page;
    const res = await api('/notifications', { query: { is_read: $('fRead').value, page, page_size: 15 } });
    const payload = (res.ok && res.data && res.data.data) || {};
    const items = Array.isArray(payload) ? payload : (payload.items || []);
    const meta = payload.meta || res.data?.pagination;

    setTable($('body'), items.map(n => {
        const groupLink = n.data && n.data.group_id
            ? `<a class="btn btn-secondary btn-sm" href="../manager/group-detail.html?id=${esc(n.data.group_id)}">Vào nhóm →</a>`
            : '';
        const isRead = n.is_read ?? n.isRead;
        const markReadBtn = isRead
            ? ''
            : `<button class="btn btn-secondary btn-sm" data-read="${n.id}">Đã đọc</button>`;

        return `<tr>
            <td>${badge(n.type, n.type && n.type.startsWith(GROUP_TXN_PREFIX) ? 'warning' : 'info')}</td>
            <td>
                <b>${esc(n.title)}</b>
                <div class="muted">${esc(n.content)}</div>
                ${n.reference_id ? `<code>${esc(n.reference_id)}</code>` : ''}
            </td>
            <td class="nowrap">${fmtDateTime(n.created_at || n.createdAt)}</td>
            <td>${isRead ? badge('Đã đọc') : badge('Chưa đọc', 'warning')}</td>
            <td class="nowrap btn-row">
                ${markReadBtn}
                ${groupLink}
            </td>
        </tr>`;
    }).join(''), 5, 'Không có thông báo');

    renderPager($('pager'), meta, load);
    $('body').querySelectorAll('[data-read]').forEach(b => b.onclick = async () => {
        if (toastResult(await api(`/notifications/${b.dataset.read}/read`, { method: 'PATCH' }), 'Đã đánh dấu đã đọc')) load(currentPage);
    });
}

$('fRead').onchange = () => load(1);
$('btnReload').onclick = () => load(currentPage);
load();
