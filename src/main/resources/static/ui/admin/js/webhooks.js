// Dashboard webhook payOS: danh sách các lần payOS gọi webhook và lỗi xảy ra khi nào.
// Dữ liệu lấy từ API admin (chỉ quản trị viên đọc được), trang này chỉ hiển thị.
const REFRESH_MS = 15000;
const PAGE_SIZE = 20;

// Các loại kết quả theo thứ tự hiển thị; kind quyết định màu nhãn. Lỗi là nhóm màu đỏ/cam.
const RESULTS = [
    ['PROCESSED', 'success', 'Đã giao hàng'],
    ['ALREADY_PAID', 'info', 'payOS gửi lại'],
    ['IGNORED_NOT_SUCCESS', '', 'Bỏ qua (không thành công)'],
    ['IGNORED_EMPTY', '', 'Bỏ qua (không có dữ liệu/webhook thử)'],
    ['ORDER_NOT_FOUND', 'danger', 'Không có đơn'],
    ['LINK_MISMATCH', 'danger', 'Link không khớp'],
    ['ORDER_CLOSED', 'danger', 'Đơn đã đóng nhưng nhận tiền'],
    ['UNDERPAID', 'danger', 'Thiếu tiền'],
    ['INVALID_BODY', 'warning', 'Body hỏng'],
    ['INVALID_SIGNATURE', 'warning', 'Sai chữ ký'],
    ['ERROR', 'danger', 'Lỗi xử lý']
];
const KIND = Object.fromEntries(RESULTS.map(r => [r[0], r[1]]));
const LABEL = Object.fromEntries(RESULTS.map(r => [r[0], r[2]]));

let page = 1;
let resultFilter = '';
let paused = false;
let timer = null;

function hours() {
    return Number($('fHours').value) || 24;
}

function fromDate() {
    // lọc danh sách theo cùng khoảng thời gian với thống kê (ngày theo giờ máy người xem)
    const d = new Date(Date.now() - hours() * 3600 * 1000);
    const p = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

async function loadSummary() {
    const res = await api('/payments/admin/webhook-logs/summary', { query: { hours: hours() }, silent: true });
    if (res.status === 403) {
        document.getElementById('page').innerHTML =
            '<div class="card"><div class="empty">Bạn không có quyền xem trang này (cần quyền PAYMENT_READ hoặc ROLE_ADMIN).</div></div>';
        return false;
    }
    const s = dataOf(res);
    if (!s) return true;

    $('hoursLabel').textContent = s.hours;
    $('sTotal').textContent = s.total;
    $('sFailed').textContent = s.failed;
    $('sRate').textContent = s.total ? `${(s.failure_rate * 100).toFixed(1)}% trên tổng số` : 'chưa có webhook nào';
    $('sLastOk').textContent = fmtDateTime(s.last_success_at);
    $('sLastFail').textContent = fmtDateTime(s.last_failure_at);

    $('chips').innerHTML = RESULTS.map(([key, kind, label]) => {
        const n = (s.counts_by_result || {})[key] || 0;
        const active = resultFilter === key ? ' style="outline:2px solid var(--primary)"' : '';
        return `<button class="btn btn-secondary btn-sm" data-result="${key}"${active}>
            ${badge(key, kind)} ${esc(label)} <b>${n}</b></button>`;
    }).join('');
    $('chips').querySelectorAll('[data-result]').forEach(b => {
        b.onclick = () => {
            resultFilter = resultFilter === b.dataset.result ? '' : b.dataset.result;
            if (resultFilter) $('fScope').value = 'all';
            page = 1;
            refresh();
        };
    });

    const missing = s.paid_without_webhook || [];
    $('warnBox').classList.toggle('hidden', missing.length === 0);
    if (missing.length) {
        $('warnText').innerHTML = `Các đơn đã <b>PAID</b> quá ${s.missing_minutes} phút mà không có webhook xác nhận
            (được ghi nhận qua polling hoặc job, webhook có thể không tới): ` +
            missing.map(c => `<a href="#" data-order="${c}"><code>${c}</code></a>`).join(', ');
        $('warnText').querySelectorAll('[data-order]').forEach(a => {
            a.onclick = (e) => { e.preventDefault(); $('fOrder').value = a.dataset.order; $('fScope').value = 'all'; page = 1; refresh(); };
        });
    }
    return true;
}

async function loadList() {
    const query = {
        page,
        page_size: PAGE_SIZE,
        only_failed: $('fScope').value === 'failed' && !resultFilter,
        result: resultFilter || undefined,
        order_code: $('fOrder').value || undefined,
        from_date: fromDate()
    };
    const d = dataOf(await api('/payments/admin/webhook-logs', { query, silent: true }));
    if (!d) return;
    $('count').textContent = d.meta ? `(${d.meta.total_items})` : '';
    setTable($('body'), (d.items || []).map(row), 8, 'Không có webhook nào trong khoảng này');
    renderPager($('pager'), d.meta, (p) => { page = p; loadList(); });
}

function row(l) {
    const merged = l.aggregated ? ` <span class="badge warning">gộp ${l.occurrences}</span>` : '';
    const httpBad = l.http_status >= 400;
    return `<tr>
        <td class="nowrap">${esc(fmtDateTime(l.received_at))}</td>
        <td>${badge(l.result, KIND[l.result])}${merged}<div class="muted" style="font-size:11px">${esc(LABEL[l.result] || '')}</div></td>
        <td>${l.order_code != null ? `<code>${esc(l.order_code)}</code>` : '-'}</td>
        <td>${l.amount != null ? esc(fmtMoney(l.amount)) : '-'}</td>
        <td>${httpBad ? `<span class="neg">${esc(l.http_status)}</span>` : esc(l.http_status)}</td>
        <td class="nowrap">${esc(l.duration_ms)} ms</td>
        <td class="nowrap">${esc(l.client_ip || '-')}</td>
        <td style="max-width:360px;word-break:break-word">${esc(l.message || '')}</td>
    </tr>`;
}

async function refresh() {
    const allowed = await loadSummary();
    if (allowed) await loadList();
}

function schedule() {
    clearInterval(timer);
    timer = setInterval(() => { if (!paused) refresh(); }, REFRESH_MS);
}

$('btnReload').onclick = () => { page = 1; refresh(); };
$('btnPause').onclick = () => {
    paused = !paused;
    $('btnPause').textContent = paused ? '▶️ Bật tự làm mới' : '⏸️ Tạm dừng tự làm mới';
};
['fScope', 'fHours'].forEach(id => $(id).onchange = () => { page = 1; if (id === 'fScope') resultFilter = ''; refresh(); });
$('fOrder').onchange = () => { page = 1; refresh(); };

refresh();
schedule();
