// Sổ giao dịch cá nhân: hiển thị, lọc, phân trang, thêm, xem chi tiết, sửa và xóa giao dịch.
let currentPage = 0;
const PAGE_SIZE = 15;
let totalPages = 1;

let walletsMap = new Map();
let categoriesMap = new Map();
let categoriesList = [];

// Convert ISO string to datetime-local input string
function toDateTimeLocal(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

// Convert datetime-local value to ISO string
function fromDateTimeLocal(val) {
    if (!val) return new Date().toISOString();
    return new Date(val).toISOString();
}

async function loadWallets() {
    const res = await api('/wallets', { silent: true });
    const data = dataOf(res) || {};
    const list = data.wallets || (Array.isArray(data) ? data : []);
    walletsMap.clear();

    const select = $('fWallet');
    select.innerHTML = '<option value="">-- Tất cả ví --</option>';

    list.forEach(w => {
        walletsMap.set(w.id, w.name);
        const opt = document.createElement('option');
        opt.value = w.id;
        opt.textContent = w.name;
        select.appendChild(opt);
    });
    return list;
}

async function loadCategories() {
    const out = [];
    const walk = async (nodes) => {
        for (const c of nodes) {
            out.push(c);
            categoriesMap.set(c.code, c.name);
            if (!c.is_leaf && !c.isLeaf) {
                const children = dataOf(await api(`/categories/${encodeURIComponent(c.code)}/children`, { silent: true })) || [];
                await walk(children);
            }
        }
    };
    try {
        const rootCates = dataOf(await api('/categories', { silent: true })) || [];
        await walk(rootCates);
    } catch (e) {
        console.warn('Lỗi tải danh mục:', e);
    }
    categoriesList = out;
    return out;
}

function getWalletName(id) {
    return walletsMap.get(id) || shortId(id);
}

function getCategoryName(code) {
    if (!code) return '<span class="muted">-</span>';
    return esc(categoriesMap.get(code) || code);
}

function getTypeBadge(type) {
    switch (type) {
        case 'INCOME':
            return '<span class="badge success">Thu nhập</span>';
        case 'EXPENSE':
            return '<span class="badge danger">Chi tiêu</span>';
        case 'LOAN':
            return '<span class="badge purple">Vay / Nợ</span>';
        default:
            return badge(type);
    }
}

async function loadTransactions() {
    const query = {
        page: currentPage,
        size: PAGE_SIZE,
        sort: 'occurredAt,desc'
    };

    const walletId = $('fWallet').value;
    if (walletId) query.wallet_id = walletId;

    const type = $('fType').value;
    if (type) query.type = type;

    const period = $('fPeriod').value;
    if (period) query.period = period;

    const search = ($('fSearch').value || '').trim();
    if (search) query.search = search;

    const from = $('fFrom').value;
    if (from) query.from = new Date(from + 'T00:00:00').toISOString();

    const to = $('fTo').value;
    if (to) query.to = new Date(to + 'T23:59:59').toISOString();

    const minAmount = $('fMinAmount').value;
    if (minAmount) query.min_amount = minAmount;

    const maxAmount = $('fMaxAmount').value;
    if (maxAmount) query.max_amount = maxAmount;

    $('txnTableBody').innerHTML = '<tr><td colspan="7" class="center muted">Đang tải dữ liệu...</td></tr>';

    const res = await api('/transactions', { query });
    const data = dataOf(res) || {};
    const txns = data.txns || [];
    const meta = data.page || {};

    totalPages = meta.total_pages ?? meta.totalPages ?? 1;
    const totalItems = meta.total_items ?? meta.totalItems ?? txns.length;
    $('paginationInfo').textContent = `Trang ${currentPage + 1} / ${totalPages} (Tổng ${totalItems} giao dịch)`;
    $('totalItemsText').textContent = `(${totalItems})`;
    $('btnPrevPage').disabled = currentPage <= 0;
    $('btnNextPage').disabled = currentPage >= totalPages - 1;

    // Tính thống kê theo trang hiện tại
    let inc = 0, exp = 0;
    txns.forEach(t => {
        if (t.type === 'INCOME') inc += Number(t.amount || 0);
        else if (t.type === 'EXPENSE') exp += Number(t.amount || 0);
    });
    $('sIncome').textContent = fmtMoney(inc);
    $('sExpense').textContent = fmtMoney(exp);
    const net = inc - exp;
    $('sNet').textContent = fmtMoney(net);
    $('sNet').className = 'value ' + (net >= 0 ? 'pos' : 'neg');
    $('sCount').textContent = totalItems;
    $('sPageSub').textContent = `trang ${currentPage + 1}/${totalPages}`;

    if (txns.length === 0) {
        $('txnTableBody').innerHTML = '<tr><td colspan="7" class="center muted" style="padding:24px">Không tìm thấy giao dịch nào.</td></tr>';
        return;
    }

    $('txnTableBody').innerHTML = txns.map(t => {
        const amtCls = t.type === 'INCOME' ? 'pos' : (t.type === 'EXPENSE' ? 'neg' : '');
        const sign = t.type === 'INCOME' ? '+' : (t.type === 'EXPENSE' ? '-' : '');
        const occurred = t.occurred_at || t.occurredAt;
        const wId = t.wallet_id || t.walletId;
        const cCode = t.category_code || t.categoryCode;
        return `
            <tr>
                <td class="nowrap">${fmtDateTime(occurred)}</td>
                <td>${getTypeBadge(t.type)}</td>
                <td><b>${esc(t.name || '(Không có tên)')}</b></td>
                <td>${getCategoryName(cCode)}</td>
                <td><span class="muted">👛</span> ${esc(getWalletName(wId))}</td>
                <td class="right nowrap"><span class="${amtCls}" style="font-weight:600">${sign}${fmtMoney(t.amount)}</span></td>
                <td class="center nowrap">
                    <button class="btn btn-secondary btn-sm" onclick="showDetail('${t.id}')">Xem</button>
                    <button class="btn btn-secondary btn-sm" onclick="openEditTxn('${t.id}')">Sửa</button>
                    <button class="btn btn-danger btn-sm" onclick="deleteTxn('${t.id}')">Xóa</button>
                </td>
            </tr>
        `;
    }).join('');
}

// Xem chi tiết giao dịch
async function showDetail(id) {
    const res = await api(`/transactions/${id}`);
    const t = dataOf(res);
    if (!t) return;

    const wName = (t.wallet && t.wallet.name) ? t.wallet.name : getWalletName(t.wallet_id || t.walletId);
    const cName = (t.category && t.category.name) ? t.category.name : getCategoryName(t.category_code || t.categoryCode);
    const occurred = t.occurred_at || t.occurredAt;
    const dispName = t.display_name || t.displayName || t.name || '(Không có tên)';

    openModal('Chi tiết giao dịch', `
        <div style="line-height:1.7;font-size:13px">
            <div style="font-size:16px;font-weight:700;margin-bottom:12px;display:flex;align-items:center;justify-content:space-between">
                <span>${esc(dispName)}</span>
                ${getTypeBadge(t.type)}
            </div>
            <div class="grid grid-2" style="gap:8px;margin-bottom:12px;background:#f8fafc;padding:12px;border-radius:8px">
                <div><b>Số tiền:</b> <span class="${t.type === 'INCOME' ? 'pos' : 'neg'}" style="font-size:15px;font-weight:700">${fmtMoney(t.amount)}</span></div>
                <div><b>Ví thanh toán:</b> ${esc(wName)}</div>
                <div><b>Danh mục:</b> ${esc(cName)}</div>
                <div><b>Thời gian:</b> ${fmtDateTime(occurred)}</div>
            </div>
            <div style="margin-bottom:8px">
                <b>Ghi chú:</b>
                <div style="color:var(--text-muted);margin-top:2px;white-space:pre-wrap;background:#fff;padding:8px;border:1px solid var(--border);border-radius:6px">${esc(t.note || 'Không có ghi chú')}</div>
            </div>
            <div style="font-size:11px;color:var(--text-muted);display:flex;justify-content:space-between;border-top:1px solid var(--border);padding-top:8px">
                <span>ID: <code>${t.id}</code></span>
                <span>Tạo lúc: ${fmtDateTime(t.created_at || t.createdAt)}</span>
            </div>
        </div>
    `, [{ label: 'Đóng', kind: 'btn-secondary' }]);
}

// Xóa giao dịch
async function deleteTxn(id) {
    if (!await confirmBox('Bạn có chắc chắn muốn xóa giao dịch này? Số dư ví sẽ được hoàn nguyên tự động.')) return;
    const res = await api(`/transactions/${id}`, { method: 'DELETE' });
    if (toastResult(res, 'Đã xóa giao dịch thành công')) {
        loadTransactions();
    }
}

// Tạo giao dịch mới
function openCreateTxn() {
    const walletOpts = Array.from(walletsMap.entries()).map(([id, name]) =>
        `<option value="${esc(id)}">${esc(name)}</option>`).join('');

    const cateOpts = categoriesList.map(c =>
        `<option value="${esc(c.code)}">${'  '.repeat(c.level || 0)}${esc(c.name)}</option>`).join('');

    const now = toDateTimeLocal(new Date().toISOString());

    openModal('Thêm giao dịch mới', `
        <div class="form-row">
            <div class="form-group">
                <label>Loại giao dịch *</label>
                <select id="mType">
                    <option value="EXPENSE">Chi tiêu (EXPENSE)</option>
                    <option value="INCOME">Thu nhập (INCOME)</option>
                    <option value="LOAN">Khoản vay (LOAN)</option>
                </select>
            </div>
            <div class="form-group">
                <label>Số tiền (VNĐ) *</label>
                <input type="number" id="mAmount" placeholder="VD: 50000" min="1" required>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Ví giao dịch *</label>
                <select id="mWallet" required>
                    <option value="">-- Chọn ví --</option>
                    ${walletOpts}
                </select>
            </div>
            <div class="form-group">
                <label>Danh mục</label>
                <select id="mCategory">
                    <option value="">-- Chọn danh mục --</option>
                    ${cateOpts}
                </select>
            </div>
        </div>
        <div class="form-group">
            <label>Thời gian giao dịch *</label>
            <input type="datetime-local" id="mOccurredAt" value="${now}" required>
        </div>
        <div class="form-group">
            <label>Tên / Nội dung giao dịch</label>
            <input type="text" id="mDisplayName" placeholder="VD: Ăn trưa, Tiền lương tháng 10...">
        </div>
        <div class="form-group">
            <label>Ghi chú</label>
            <textarea id="mNote" rows="2" placeholder="Ghi chú chi tiết nếu có..."></textarea>
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Lưu giao dịch',
            kind: 'btn-primary',
            onClick: async () => {
                const type = $('mType').value;
                const amount = Number($('mAmount').value);
                const wallet_id = $('mWallet').value;
                const category_code = $('mCategory').value || null;
                const occurred_at = fromDateTimeLocal($('mOccurredAt').value);
                const display_name = ($('mDisplayName').value || '').trim() || null;
                const note = ($('mNote').value || '').trim() || null;

                if (!amount || amount <= 0) {
                    toast('Số tiền phải lớn hơn 0', 'error');
                    return false;
                }
                if (!wallet_id) {
                    toast('Vui lòng chọn ví thanh toán', 'error');
                    return false;
                }

                // Gửi đúng format snake_case theo cấu hình Jackson của server
                const res = await api('/transactions', {
                    method: 'POST',
                    body: {
                        type,
                        amount,
                        wallet_id,
                        category_code,
                        occurred_at,
                        display_name,
                        note
                    }
                });

                if (toastResult(res, 'Thêm giao dịch thành công')) {
                    loadTransactions();
                    return true;
                }
                return false;
            }
        }
    ]);
}

// Sửa giao dịch
async function openEditTxn(id) {
    const res = await api(`/transactions/${id}`);
    const t = dataOf(res);
    if (!t) return;

    const curWalletId = (t.wallet && t.wallet.id) || t.wallet_id || t.walletId;
    const curCateCode = (t.category && t.category.code) || t.category_code || t.categoryCode;
    const occurred = t.occurred_at || t.occurredAt;
    const dispName = t.display_name || t.displayName || t.name || '';

    const walletOpts = Array.from(walletsMap.entries()).map(([wId, name]) =>
        `<option value="${esc(wId)}" ${wId === curWalletId ? 'selected' : ''}>${esc(name)}</option>`).join('');

    const cateOpts = categoriesList.map(c =>
        `<option value="${esc(c.code)}" ${c.code === curCateCode ? 'selected' : ''}>${'  '.repeat(c.level || 0)}${esc(c.name)}</option>`).join('');

    const curTime = toDateTimeLocal(occurred);

    openModal('Cập nhật giao dịch', `
        <div class="form-row">
            <div class="form-group">
                <label>Loại giao dịch *</label>
                <select id="mType">
                    <option value="EXPENSE" ${t.type === 'EXPENSE' ? 'selected' : ''}>Chi tiêu (EXPENSE)</option>
                    <option value="INCOME" ${t.type === 'INCOME' ? 'selected' : ''}>Thu nhập (INCOME)</option>
                    <option value="LOAN" ${t.type === 'LOAN' ? 'selected' : ''}>Khoản vay (LOAN)</option>
                </select>
            </div>
            <div class="form-group">
                <label>Số tiền (VNĐ) *</label>
                <input type="number" id="mAmount" value="${t.amount}" min="1" required>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Ví giao dịch *</label>
                <select id="mWallet" required>
                    ${walletOpts}
                </select>
            </div>
            <div class="form-group">
                <label>Danh mục</label>
                <select id="mCategory">
                    <option value="">-- Không có danh mục --</option>
                    ${cateOpts}
                </select>
            </div>
        </div>
        <div class="form-group">
            <label>Thời gian giao dịch *</label>
            <input type="datetime-local" id="mOccurredAt" value="${curTime}" required>
        </div>
        <div class="form-group">
            <label>Tên / Nội dung giao dịch</label>
            <input type="text" id="mDisplayName" value="${esc(dispName)}">
        </div>
        <div class="form-group">
            <label>Ghi chú</label>
            <textarea id="mNote" rows="2">${esc(t.note || '')}</textarea>
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Lưu thay đổi',
            kind: 'btn-primary',
            onClick: async () => {
                const type = $('mType').value;
                const amount = Number($('mAmount').value);
                const wallet_id = $('mWallet').value;
                const category_code = $('mCategory').value || null;
                const occurred_at = fromDateTimeLocal($('mOccurredAt').value);
                const display_name = ($('mDisplayName').value || '').trim() || null;
                const note = ($('mNote').value || '').trim() || null;

                if (!amount || amount <= 0) {
                    toast('Số tiền phải lớn hơn 0', 'error');
                    return false;
                }

                // TxnUpdateReq dùng occuredAt -> gửi cả occured_at và occurred_at cho tương thích
                const updateRes = await api(`/transactions/${id}`, {
                    method: 'PATCH',
                    body: {
                        type,
                        amount,
                        wallet_id,
                        category_code,
                        occured_at: occurred_at,
                        occurred_at,
                        display_name,
                        note
                    }
                });

                if (toastResult(updateRes, 'Cập nhật giao dịch thành công')) {
                    loadTransactions();
                    return true;
                }
                return false;
            }
        }
    ]);
}

// Gán sự kiện
$('btnCreateTxn').onclick = openCreateTxn;
$('btnReloadTable').onclick = loadTransactions;
$('btnFilter').onclick = () => {
    currentPage = 0;
    loadTransactions();
};
$('btnResetFilters').onclick = () => {
    $('fWallet').value = '';
    $('fType').value = '';
    $('fPeriod').value = 'month';
    $('fSearch').value = '';
    $('fFrom').value = '';
    $('fTo').value = '';
    $('fMinAmount').value = '';
    $('fMaxAmount').value = '';
    currentPage = 0;
    loadTransactions();
};
$('fPeriod').onchange = () => {
    if ($('fPeriod').value) {
        $('fFrom').value = '';
        $('fTo').value = '';
    }
};
$('btnPrevPage').onclick = () => {
    if (currentPage > 0) {
        currentPage--;
        loadTransactions();
    }
};
$('btnNextPage').onclick = () => {
    if (currentPage < totalPages - 1) {
        currentPage++;
        loadTransactions();
    }
};

// Khởi chạy
(async function init() {
    await Promise.all([loadWallets(), loadCategories()]);
    loadTransactions();
})();
