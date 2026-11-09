// Tiện ích giao diện dùng chung: escape HTML, định dạng, toast, modal, phân trang.
const $ = (id) => document.getElementById(id);

function esc(v) {
    if (v === null || v === undefined) return '';
    return String(v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function fmtMoney(v) {
    return Number(v ?? 0).toLocaleString('vi-VN') + ' đ';
}

function fmtDateTime(v) {
    return v ? new Date(v).toLocaleString('vi-VN') : '-';
}

function fmtDate(v) {
    return v ? new Date(v).toLocaleDateString('vi-VN') : '-';
}

function shortId(id) {
    return id ? String(id).substring(0, 8) + '…' : '-';
}

// Số dương xanh, âm đỏ
function signedMoney(v) {
    const n = Number(v ?? 0);
    const cls = n > 0 ? 'pos' : n < 0 ? 'neg' : 'muted';
    return `<span class="${cls}">${n > 0 ? '+' : ''}${n.toLocaleString('vi-VN')} đ</span>`;
}

const BADGE_KIND = {
    ACTIVE: 'success', CONFIRMED: 'success', OWNER: 'purple', true: 'success',
    PENDING: 'warning', ARCHIVED: 'warning',
    REJECTED: 'danger', LEFT: 'danger', REMOVED: 'danger', BLOCKED: 'danger', DELETED: 'danger',
    EXPENSE: 'info', CONTRIBUTION: 'info', REFUND: 'info'
};

function badge(text, kind) {
    if (text === null || text === undefined || text === '') return '';
    return `<span class="badge ${kind || BADGE_KIND[text] || ''}">${esc(text)}</span>`;
}

function toast(msg, kind = '') {
    let box = $('toasts');
    if (!box) {
        box = document.createElement('div');
        box.id = 'toasts';
        document.body.appendChild(box);
    }
    const t = document.createElement('div');
    t.className = 'toast ' + kind;
    t.textContent = msg;
    box.appendChild(t);
    setTimeout(() => t.remove(), kind === 'error' ? 7000 : 3500);
}

// Toast theo kết quả API: thành công hiện msg, thất bại api() đã tự toast lỗi
function toastResult(res, okMsg) {
    if (res.ok) toast(okMsg || (res.data && res.data.msg) || 'Thành công', 'success');
    return res.ok;
}

// Hộp thoại: body là chuỗi HTML hoặc Node. actions = [{ label, kind, onClick(close) }]
function openModal(title, body, actions) {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    const modal = document.createElement('div');
    modal.className = 'modal';
    const h3 = document.createElement('h3');
    h3.textContent = title;
    const content = document.createElement('div');
    if (typeof body === 'string') content.innerHTML = body; else content.appendChild(body);
    modal.append(h3, content);

    const close = () => overlay.remove();
    const bar = document.createElement('div');
    bar.className = 'modal-actions';
    (actions || [{ label: 'Đóng', kind: 'btn-secondary' }]).forEach(a => {
        const b = document.createElement('button');
        b.className = 'btn ' + (a.kind || '');
        b.textContent = a.label;
        b.onclick = async () => {
            if (!a.onClick) return close();
            const keepOpen = await a.onClick(close, modal);
            if (keepOpen !== false && a.autoClose !== false) close();
        };
        bar.appendChild(b);
    });
    modal.appendChild(bar);
    overlay.appendChild(modal);
    overlay.addEventListener('mousedown', e => { if (e.target === overlay) close(); });
    document.body.appendChild(overlay);
    return { close, modal };
}

function confirmBox(message) {
    return new Promise(resolve => {
        openModal('Xác nhận', `<p>${esc(message)}</p>`, [
            { label: 'Huỷ', kind: 'btn-secondary', onClick: close => { resolve(false); } },
            { label: 'Đồng ý', kind: 'btn-danger', onClick: () => { resolve(true); } }
        ]);
    });
}

// Dựng form trong modal từ danh sách trường, trả về object giá trị khi bấm Lưu (hoặc null nếu huỷ).
// fields: [{ name, label, type='text', value, options:[[value,label]], placeholder, required }]
function formModal(title, fields, submitLabel = 'Lưu') {
    return new Promise(resolve => {
        const html = fields.map(f => {
            const id = 'fm_' + f.name;
            let input;
            if (f.type === 'select') {
                input = `<select id="${id}">${(f.options || []).map(([v, l]) => `<option value="${esc(v)}" ${String(f.value ?? '') === String(v) ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`;
            } else if (f.type === 'checkbox') {
                return `<label class="check"><input type="checkbox" id="${id}" ${f.value ? 'checked' : ''}> ${esc(f.label)}</label>`;
            } else if (f.type === 'textarea') {
                input = `<textarea id="${id}" rows="3" placeholder="${esc(f.placeholder || '')}">${esc(f.value ?? '')}</textarea>`;
            } else {
                input = `<input type="${f.type || 'text'}" id="${id}" value="${esc(f.value ?? '')}" placeholder="${esc(f.placeholder || '')}">`;
            }
            return `<div class="form-group"><label for="${id}">${esc(f.label)}${f.required ? ' *' : ''}</label>${input}</div>`;
        }).join('');

        openModal(title, html, [
            { label: 'Huỷ', kind: 'btn-secondary', onClick: () => resolve(null) },
            {
                label: submitLabel,
                onClick: (close, modal) => {
                    const out = {};
                    for (const f of fields) {
                        const el = modal.querySelector('#fm_' + f.name);
                        const v = f.type === 'checkbox' ? el.checked : el.value.trim();
                        if (f.required && (v === '' || v === null)) {
                            toast(`Vui lòng nhập: ${f.label}`, 'error');
                            return false;
                        }
                        out[f.name] = v;
                    }
                    resolve(out);
                }
            }
        ]);
    });
}

// Vẽ thanh phân trang vào container từ PageMeta { page, page_size, total_items, total_pages }
function renderPager(container, meta, onPage) {
    if (!container) return;
    if (!meta || !meta.total_pages || meta.total_pages <= 1) {
        container.innerHTML = meta && meta.total_items !== undefined ? `<span class="muted">${meta.total_items} mục</span>` : '';
        return;
    }
    container.innerHTML = `
        <span class="muted">${meta.total_items} mục · trang ${meta.page}/${meta.total_pages}</span>
        <button class="btn btn-secondary btn-sm" ${meta.page <= 1 ? 'disabled' : ''} data-p="${meta.page - 1}">‹ Trước</button>
        <button class="btn btn-secondary btn-sm" ${meta.page >= meta.total_pages ? 'disabled' : ''} data-p="${meta.page + 1}">Sau ›</button>`;
    container.querySelectorAll('button[data-p]').forEach(b => b.onclick = () => onPage(Number(b.dataset.p)));
}

function setTable(tbody, rowsHtml, colspan, emptyText = 'Không có dữ liệu') {
    tbody.innerHTML = rowsHtml && rowsHtml.length
        ? rowsHtml
        : `<tr><td class="empty" colspan="${colspan}">${esc(emptyText)}</td></tr>`;
}

function copyText(text) {
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => toast('Đã sao chép: ' + text, 'success'));
}

function queryParam(name) {
    return new URLSearchParams(location.search).get(name);
}
