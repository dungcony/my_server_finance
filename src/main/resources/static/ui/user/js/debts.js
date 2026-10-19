// Quản lý sổ nợ & cho vay: tổng quan công nợ, khoản sắp đến hạn, ghi nhận thu/trả nợ, thêm và xóa khoản nợ.
let walletsMap = new Map();

async function loadWallets() {
    const res = await api('/wallets', { silent: true });
    const data = dataOf(res) || {};
    const list = data.wallets || (Array.isArray(data) ? data : []);
    walletsMap.clear();
    list.forEach(w => walletsMap.set(w.id, w.name));
    return list;
}

async function loadSummary() {
    const res = await api('/debts/summary', { silent: true });
    const s = dataOf(res);
    if (!s) return;

    const rec = s.receivable || {};
    const pay = s.payable || {};

    $('sReceivable').textContent = fmtMoney(rec.total || 0);
    $('sReceivableSub').textContent = `${rec.count || 0} khoản cho vay`;

    $('sPayable').textContent = fmtMoney(pay.total || 0);
    $('sPayableSub').textContent = `${pay.count || 0} khoản đi vay`;

    const diff = Number(s.difference || 0);
    $('sDiff').textContent = fmtMoney(diff);
    $('sDiff').className = 'value ' + (diff >= 0 ? 'pos' : 'neg');

    const overdueRec = rec.is_overdue || rec.isOverdue || {};
    const overduePay = pay.is_overdue || pay.isOverdue || {};
    const overdueCount = (overdueRec.count || 0) + (overduePay.count || 0);
    const overdueTotal = (overdueRec.total || 0) + (overduePay.total || 0);
    $('sOverdueCount').textContent = overdueCount;
    $('sOverdueCount').className = 'value ' + (overdueCount > 0 ? 'neg' : '');
    $('sOverdueTotal').textContent = `Tổng: ${fmtMoney(overdueTotal)}`;

    // Render banner các khoản sắp đến hạn
    const dueSoon = s.due_soon || s.dueSoon || [];
    const container = $('dueSoonContainer');
    if (dueSoon.length > 0) {
        container.style.display = 'block';
        container.innerHTML = dueSoon.map(d => {
            const cpName = d.counterparty_name || d.counterpartyName;
            const rAmt = d.remaining_amount ?? d.remainingAmount ?? 0;
            const dDate = d.due_date || d.dueDate;
            const daysLeft = d.days_remaining ?? d.daysRemaining;
            return `
                <div class="due-card">
                    <div>
                        ⏰ <b>Khoản nợ sắp đến hạn:</b>
                        ${d.type === 'lending' ? 'Khoản cho' : 'Khoản vay'} <b>${esc(cpName)}</b>
                        (Còn lại: <span style="font-weight:700">${fmtMoney(rAmt)}</span>,
                        hạn ngày: <b>${fmtDate(dDate)}</b> — còn <b>${daysLeft}</b> ngày).
                    </div>
                    <button class="btn btn-sm" onclick="openPaymentModal('${d.id}', '${esc(cpName)}', '${d.type}', ${rAmt})">
                        ${d.type === 'lending' ? 'Thu nợ ngay' : 'Trả nợ ngay'}
                    </button>
                </div>
            `;
        }).join('');
    } else {
        container.style.display = 'none';
        container.innerHTML = '';
    }
}

async function loadDebts() {
    const query = {};
    const type = $('fType').value;
    if (type) query.type = type;

    const status = $('fStatus').value;
    if (status) query.status = status;

    const overdue = $('fOverdue').value;
    if (overdue) query.is_overdue = overdue === 'true';

    $('debtTableBody').innerHTML = '<tr><td colspan="9" class="center muted">Đang tải dữ liệu...</td></tr>';

    const res = await api('/debts', { query });
    const list = dataOf(res) || [];

    $('debtCountText').textContent = `(${list.length})`;

    if (list.length === 0) {
        $('debtTableBody').innerHTML = '<tr><td colspan="9" class="center muted" style="padding:24px">Không có khoản nợ hoặc cho vay nào.</td></tr>';
        return;
    }

    $('debtTableBody').innerHTML = list.map(d => {
        const isLending = d.type === 'lending';
        const typeBadge = isLending
            ? '<span class="badge success">Cho vay</span>'
            : '<span class="badge danger">Đi vay</span>';

        const pRatio = d.paid_ratio ?? d.paidRatio;
        const ratio = pRatio != null ? (Number(pRatio) * 100).toFixed(0) : '0';
        const wId = d.wallet_id || d.walletId;
        const wName = (d.wallet && d.wallet.name) || (wId ? walletsMap.get(wId) : '-');
        const cpName = d.counterparty_name || d.counterpartyName;
        const pAmt = d.principal_amount ?? d.principalAmount ?? 0;
        const paidAmt = d.paid_amount ?? d.paidAmount ?? 0;
        const rAmt = d.remaining_amount ?? d.remainingAmount ?? (pAmt - paidAmt);
        const iDate = d.issued_date || d.issuedDate;
        const dDate = d.due_date || d.dueDate;
        const daysLeft = d.days_remaining ?? d.daysRemaining;
        const isOverdue = d.is_overdue ?? d.isOverdue;

        let statusBadge = '';
        if (d.status === 'completed' || d.status === 'settled' || Number(rAmt) <= 0) {
            statusBadge = '<span class="badge success">Đã tất toán</span>';
        } else if (isOverdue) {
            statusBadge = '<span class="badge danger">Quá hạn</span>';
        } else {
            statusBadge = '<span class="badge info">Chưa trả xong</span>';
        }

        return `
            <tr>
                <td>${typeBadge}</td>
                <td>
                    <b>${esc(cpName)}</b>
                    ${d.note ? `<div class="muted" style="font-size:11px">${esc(d.note)}</div>` : ''}
                </td>
                <td class="right" style="font-weight:600">${fmtMoney(pAmt)}</td>
                <td class="right" style="font-size:12px">
                    ${fmtMoney(paidAmt)}
                    <div class="muted">(${ratio}%)</div>
                </td>
                <td class="right ${isLending ? 'pos' : 'neg'}" style="font-weight:700">
                    ${fmtMoney(rAmt)}
                </td>
                <td style="font-size:12px">
                    <div>📅 Tạo: ${fmtDate(iDate)}</div>
                    <div>${dDate ? `⌛ Hạn: <b>${fmtDate(dDate)}</b>` : '<span class="muted">Không hẹn</span>'}</div>
                    ${daysLeft != null && rAmt > 0 ? `<div style="font-size:11px;color:${daysLeft < 0 ? '#dc2626' : '#64748b'}">${daysLeft < 0 ? 'Đã quá hạn ' + Math.abs(daysLeft) + ' ngày' : 'Còn ' + daysLeft + ' ngày'}</div>` : ''}
                </td>
                <td class="center">${statusBadge}</td>
                <td style="font-size:12px">👛 ${esc(wName)}</td>
                <td class="center nowrap">
                    ${rAmt > 0 ? `
                        <button class="btn btn-sm" onclick="openPaymentModal('${d.id}', '${esc(cpName)}', '${d.type}', ${rAmt})">
                            ${isLending ? '+ Thu' : '+ Trả'}
                        </button>
                    ` : ''}
                    <button class="btn btn-secondary btn-sm" onclick="showDebtDetail('${d.id}')">Chi tiết</button>
                    <button class="btn btn-danger btn-sm" onclick="deleteDebt('${d.id}')">Xóa</button>
                </td>
            </tr>
        `;
    }).join('');
}

// Ghi nhận thu / trả nợ
function openPaymentModal(debtId, counterpartyName, debtType, remainingAmount) {
    const isLending = debtType === 'lending';
    const actionName = isLending ? 'Ghi nhận thu nợ từ' : 'Ghi nhận trả nợ cho';
    const today = new Date().toISOString().split('T')[0];

    const walletOpts = Array.from(walletsMap.entries()).map(([wId, name]) =>
        `<option value="${esc(wId)}">${esc(name)}</option>`).join('');

    openModal(`${actionName} ${counterpartyName}`, `
        <div style="font-size:13px;margin-bottom:12px;background:#f8fafc;padding:10px;border-radius:6px">
            Số tiền nợ còn lại: <b class="${isLending ? 'pos' : 'neg'}">${fmtMoney(remainingAmount)}</b>
        </div>
        <div class="form-group">
            <label>Số tiền thanh toán (VNĐ) *</label>
            <input type="number" id="mPayAmount" value="${remainingAmount}" min="1" max="${remainingAmount}" required>
        </div>
        <div class="form-group">
            <label>Ví giao dịch *</label>
            <select id="mPayWallet" required>
                <option value="">-- Chọn ví --</option>
                ${walletOpts}
            </select>
        </div>
        <div class="form-group">
            <label>Ngày thanh toán</label>
            <input type="date" id="mPayDate" value="${today}">
        </div>
        <div class="form-group">
            <label>Ghi chú đợt thanh toán</label>
            <input type="text" id="mPayNote" placeholder="VD: Trả bớt một phần, chuyển khoản Momo...">
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: isLending ? 'Xác nhận thu nợ' : 'Xác nhận trả nợ',
            kind: 'btn-primary',
            onClick: async () => {
                const amount = Number($('mPayAmount').value);
                const wallet_id = $('mPayWallet').value || null;
                const paid_date = $('mPayDate').value || null;
                const note = ($('mPayNote').value || '').trim() || null;

                if (!amount || amount <= 0) {
                    toast('Số tiền phải lớn hơn 0', 'error');
                    return false;
                }

                const res = await api(`/debts/${debtId}/payments`, {
                    method: 'POST',
                    body: {
                        amount,
                        wallet_id,
                        paid_date,
                        note
                    }
                });

                if (toastResult(res, 'Ghi nhận thanh toán nợ thành công!')) {
                    loadSummary();
                    loadDebts();
                    return true;
                }
                return false;
            }
        }
    ]);
}

// Xem chi tiết & lịch sử các đợt thanh toán
async function showDebtDetail(id) {
    const res = await api(`/debts/${id}`);
    const d = dataOf(res);
    if (!d) return;

    const isLending = d.type === 'lending';
    const payments = d.payments || [];
    const cpName = d.counterparty_name || d.counterpartyName;
    const pAmt = d.principal_amount ?? d.principalAmount ?? 0;
    const paidAmt = d.paid_amount ?? d.paidAmount ?? 0;
    const rAmt = d.remaining_amount ?? d.remainingAmount ?? (pAmt - paidAmt);
    const iDate = d.issued_date || d.issuedDate;
    const dDate = d.due_date || d.dueDate;
    const wId = d.wallet_id || d.walletId;

    const paymentRows = payments.length > 0 ? payments.map(p => {
        const pDate = p.paid_date || p.paidDate || p.created_at || p.createdAt;
        const wName = p.wallet_name || p.walletName || getWalletName(p.wallet_id || p.walletId);
        return `
            <tr>
                <td>${fmtDate(pDate)}</td>
                <td class="${isLending ? 'pos' : 'neg'}" style="font-weight:600">${fmtMoney(p.amount)}</td>
                <td>${esc(wName || '-')}</td>
                <td class="muted">${esc(p.note || '-')}</td>
                <td class="center">
                    <button class="btn btn-danger btn-sm" onclick="cancelPayment('${d.id}', '${p.id}')">Hủy</button>
                </td>
            </tr>
        `;
    }).join('') : '<tr><td colspan="5" class="center muted">Chưa có đợt thanh toán nào.</td></tr>';

    openModal(`Chi tiết khoản nợ: ${esc(cpName)}`, `
        <div style="font-size:13px">
            <div class="grid grid-2" style="gap:8px;background:#f8fafc;padding:12px;border-radius:8px;margin-bottom:12px">
                <div><b>Phân loại:</b> ${isLending ? '<span class="badge success">Cho vay</span>' : '<span class="badge danger">Đi vay</span>'}</div>
                <div><b>Người liên quan:</b> ${esc(cpName)}</div>
                <div><b>Số tiền gốc:</b> ${fmtMoney(pAmt)}</div>
                <div><b>Đã trả:</b> ${fmtMoney(paidAmt)}</div>
                <div><b>Còn lại:</b> <span class="${isLending ? 'pos' : 'neg'}" style="font-weight:700">${fmtMoney(rAmt)}</span></div>
                <div><b>Ngày tạo:</b> ${fmtDate(iDate)}</div>
                <div><b>Hạn trả:</b> ${fmtDate(dDate)}</div>
                <div><b>Ví liên quan:</b> ${esc(getWalletName(wId))}</div>
            </div>

            <div style="font-weight:600;margin-bottom:6px">Lịch sử thanh toán (${payments.length} lần):</div>
            <div class="table-wrap" style="max-height:220px;overflow-y:auto">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Ngày</th>
                            <th>Số tiền</th>
                            <th>Ví</th>
                            <th>Ghi chú</th>
                            <th class="center">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody>${paymentRows}</tbody>
                </table>
            </div>
        </div>
    `, [{ label: 'Đóng', kind: 'btn-secondary' }]);
}

// Hủy đợt thanh toán
async function cancelPayment(debtId, paymentId) {
    if (!await confirmBox('Bạn có chắc chắn muốn hủy đợt thanh toán này? Số tiền sẽ được hoàn nguyên.')) return;
    const res = await api(`/debts/${debtId}/payments/${paymentId}`, { method: 'DELETE' });
    if (toastResult(res, 'Đã hủy đợt thanh toán')) {
        loadSummary();
        loadDebts();
    }
}

// Xóa khoản nợ
async function deleteDebt(id) {
    if (!await confirmBox('Bạn có chắc muốn xóa khoản nợ này?')) return;
    const res = await api(`/debts/${id}`, { method: 'DELETE' });
    if (toastResult(res, 'Đã xóa khoản nợ')) {
        loadSummary();
        loadDebts();
    }
}

// Tạo mới khoản nợ / cho vay
function openCreateDebt() {
    const walletOpts = Array.from(walletsMap.entries()).map(([wId, name]) =>
        `<option value="${esc(wId)}">${esc(name)}</option>`).join('');

    const today = new Date().toISOString().split('T')[0];

    openModal('Tạo khoản nợ / cho vay mới', `
        <div class="form-row">
            <div class="form-group">
                <label>Phân loại giao dịch *</label>
                <select id="mDebtType" required>
                    <option value="lending">Cho vay (Người khác nợ tôi)</option>
                    <option value="borrowing">Đi vay (Tôi nợ người khác)</option>
                </select>
            </div>
            <div class="form-group">
                <label>Tên người liên quan / Đối tác *</label>
                <input type="text" id="mCounterparty" placeholder="VD: Bạn Minh, Anh Tuấn..." required maxlength="100">
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Số tiền gốc (VNĐ) *</label>
                <input type="number" id="mPrincipalAmount" placeholder="VD: 5000000" min="1" required>
            </div>
            <div class="form-group">
                <label>Ví thực hiện giao dịch *</label>
                <select id="mDebtWallet" required>
                    <option value="">-- Chọn ví --</option>
                    ${walletOpts}
                </select>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Ngày phát sinh nợ</label>
                <input type="date" id="mIssuedDate" value="${today}">
            </div>
            <div class="form-group">
                <label>Ngày hẹn trả / thu nợ</label>
                <input type="date" id="mDueDate">
            </div>
        </div>
        <div class="form-group">
            <label>Ghi chú</label>
            <input type="text" id="mDebtNote" placeholder="VD: Vay mua laptop, Cho mượn đóng tiền nhà...">
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Lưu khoản nợ',
            kind: 'btn-primary',
            onClick: async () => {
                const type = $('mDebtType').value;
                const counterparty_name = ($('mCounterparty').value || '').trim();
                const principal_amount = Number($('mPrincipalAmount').value);
                const wallet_id = $('mDebtWallet').value;
                const issued_date = $('mIssuedDate').value || null;
                const due_date = $('mDueDate').value || null;
                const note = ($('mDebtNote').value || '').trim() || null;

                if (!counterparty_name) {
                    toast('Vui lòng nhập tên người liên quan', 'error');
                    return false;
                }
                if (!principal_amount || principal_amount <= 0) {
                    toast('Số tiền phải lớn hơn 0', 'error');
                    return false;
                }
                if (!wallet_id) {
                    toast('Vui lòng chọn ví giao dịch', 'error');
                    return false;
                }

                const res = await api('/debts', {
                    method: 'POST',
                    body: {
                        type,
                        counterparty_name,
                        principal_amount,
                        wallet_id,
                        issued_date,
                        due_date,
                        note
                    }
                });

                if (toastResult(res, 'Tạo khoản nợ thành công')) {
                    loadSummary();
                    loadDebts();
                    return true;
                }
                return false;
            }
        }
    ]);
}

// Gán sự kiện
$('btnCreateDebt').onclick = openCreateDebt;
$('btnReloadDebts').onclick = () => {
    loadSummary();
    loadDebts();
};
$('fType').onchange = loadDebts;
$('fStatus').onchange = loadDebts;
$('fOverdue').onchange = loadDebts;

// Khởi chạy
(async function init() {
    await loadWallets();
    loadSummary();
    loadDebts();
})();
