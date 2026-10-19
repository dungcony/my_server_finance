// Quản lý mục tiêu tiết kiệm: danh sách dạng thẻ, tiến độ, gợi ý AI, nộp tiền/góp tiền, tạo mới và xóa mục tiêu.
let walletsMap = new Map();

const STATUS_MAP = {
    in_progress: '<span class="badge info">Đang tích lũy</span>',
    completed: '<span class="badge success">Đã hoàn thành 🎉</span>',
    cancelled: '<span class="badge warning">Đã hủy</span>'
};

async function loadWallets() {
    const res = await api('/wallets', { silent: true });
    const data = dataOf(res) || {};
    const list = data.wallets || (Array.isArray(data) ? data : []);
    walletsMap.clear();
    list.forEach(w => walletsMap.set(w.id, w.name));
    return list;
}

async function loadGoals() {
    const status = $('fStatus').value;
    const query = {};
    if (status) query.status = status;

    $('goalsContainer').innerHTML = '<div class="muted center" style="grid-column:1/-1;padding:32px">Đang tải dữ liệu mục tiêu...</div>';

    const res = await api('/goals', { query });
    const list = dataOf(res) || [];

    // Tính thống kê
    let totalTarget = 0;
    let totalSaved = 0;
    let totalMissing = 0;
    let completedCount = 0;
    let inProgressCount = 0;

    list.forEach(g => {
        const tAmt = g.target_amount ?? g.targetAmount ?? 0;
        const sAmt = g.saved_amount ?? g.savedAmount ?? 0;
        const mAmt = g.missing_amount ?? g.missingAmount ?? (tAmt - sAmt);
        totalTarget += Number(tAmt);
        totalSaved += Number(sAmt);
        totalMissing += Number(mAmt);
        if (g.status === 'completed') completedCount++;
        else if (g.status === 'in_progress') inProgressCount++;
    });

    $('sTarget').textContent = fmtMoney(totalTarget);
    $('sSaved').textContent = fmtMoney(totalSaved);
    $('sMissing').textContent = fmtMoney(totalMissing);
    $('sGoalCount').textContent = `${list.length} mục tiêu`;

    const avgPct = totalTarget > 0 ? ((totalSaved / totalTarget) * 100).toFixed(1) : '0';
    $('sAvgProgress').textContent = `đạt ${avgPct}% toàn bộ`;
    $('sCompletedCount').textContent = completedCount;
    $('sInProgressCount').textContent = `${inProgressCount} đang tiến hành`;

    if (list.length === 0) {
        $('goalsContainer').innerHTML = '<div class="muted center" style="grid-column:1/-1;padding:48px">Chưa có mục tiêu tiết kiệm nào. Hãy bấm "+ Tạo mục tiêu mới" để bắt đầu!</div>';
        return;
    }

    $('goalsContainer').innerHTML = list.map(g => {
        const pctVal = Number(g.progress || 0);
        const percent = Math.min(100, Math.round(pctVal * 100));
        const stBadge = STATUS_MAP[g.status] || badge(g.status);
        const wId = g.wallet_id || g.walletId;
        const wName = (g.wallet && g.wallet.name) || (wId ? walletsMap.get(wId) : null);
        const tAmt = g.target_amount ?? g.targetAmount ?? 0;
        const sAmt = g.saved_amount ?? g.savedAmount ?? 0;
        const tDate = g.target_date || g.targetDate;
        const daysLeft = g.days_remaining ?? g.daysRemaining;

        let suggHtml = '';
        if (g.suggestion && g.suggestion.content) {
            const mReq = g.suggestion.monthly_required ?? g.suggestion.monthlyRequired;
            suggHtml = `
                <div class="suggestion-box">
                    💡 <b>Gợi ý AI:</b> ${esc(g.suggestion.content)}
                    ${mReq ? `<div style="margin-top:2px;font-weight:600">Cần tiết kiệm: ${fmtMoney(mReq)}/tháng</div>` : ''}
                </div>
            `;
        }

        return `
            <div class="goal-card">
                <div>
                    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:8px">
                        <div style="font-size:16px;font-weight:700;color:var(--text)">🎯 ${esc(g.name)}</div>
                        ${stBadge}
                    </div>

                    <div style="display:flex;justify-content:space-between;font-size:12px;margin-top:12px">
                        <span class="muted">Đã tích lũy:</span>
                        <span class="pos" style="font-weight:700">${fmtMoney(sAmt)}</span>
                    </div>
                    <div class="progress-bar-wrap">
                        <div class="progress-bar-fill" style="width:${percent}%"></div>
                    </div>
                    <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--text-muted)">
                        <span>${g.progress_label || g.progressLabel || percent + '%'}</span>
                        <span>Mục tiêu: <b>${fmtMoney(tAmt)}</b></span>
                    </div>

                    <div style="margin-top:12px;padding-top:10px;border-top:1px solid var(--border);font-size:12px;display:flex;justify-content:space-between;color:var(--text-muted)">
                        <div>
                            ${tDate ? `📅 Hạn: <b>${fmtDate(tDate)}</b>` : 'Không đặt hạn'}
                            ${daysLeft != null ? `<small class="badge ${daysLeft < 0 ? 'danger' : 'info'}" style="margin-left:4px">${daysLeft < 0 ? 'Quá hạn' : 'Còn ' + daysLeft + ' ngày'}</small>` : ''}
                        </div>
                        <div>
                            ${wName ? `👛 ${esc(wName)}` : '<span class="muted">Chưa gắn ví</span>'}
                        </div>
                    </div>

                    ${suggHtml}
                </div>

                <div style="margin-top:16px;display:flex;gap:6px;justify-content:flex-end">
                    ${g.status === 'in_progress' ? `
                        <button class="btn btn-sm" onclick="openDepositModal('${g.id}', '${esc(g.name)}')">+ Góp tiền</button>
                    ` : ''}
                    <button class="btn btn-secondary btn-sm" onclick="showGoalDetail('${g.id}')">Chi tiết</button>
                    <button class="btn btn-danger btn-sm" onclick="deleteGoal('${g.id}')">Xóa</button>
                </div>
            </div>
        `;
    }).join('');
}

// Nạp tiền vào mục tiêu
function openDepositModal(goalId, goalName) {
    const walletOpts = Array.from(walletsMap.entries()).map(([wId, name]) =>
        `<option value="${esc(wId)}">${esc(name)}</option>`).join('');

    const today = new Date().toISOString().split('T')[0];

    openModal(`Góp tiền vào: ${goalName}`, `
        <div class="form-group">
            <label>Số tiền nạp (VNĐ) *</label>
            <input type="number" id="mDepAmount" placeholder="VD: 500000" min="1" required>
        </div>
        <div class="form-group">
            <label>Trích từ ví nguồn *</label>
            <select id="mDepWallet">
                <option value="">-- Chọn ví trích tiền --</option>
                ${walletOpts}
            </select>
        </div>
        <div class="form-group">
            <label>Ngày nạp tiền</label>
            <input type="date" id="mDepDate" value="${today}">
        </div>
        <div class="form-group">
            <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
                <input type="checkbox" id="mCreateTxn" checked>
                <span>Tự động tạo giao dịch chi tiêu trong ví nguồn</span>
            </label>
        </div>
        <div class="form-group">
            <label>Ghi chú</label>
            <input type="text" id="mDepNote" placeholder="VD: Tiền thưởng tuần, tiết kiệm heo đất...">
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Xác nhận nạp tiền',
            kind: 'btn-primary',
            onClick: async () => {
                const amount = Number($('mDepAmount').value);
                const source_wallet_id = $('mDepWallet').value || null;
                const contributed_date = $('mDepDate').value || null;
                const create_transaction = $('mCreateTxn').checked;
                const note = ($('mDepNote').value || '').trim() || null;

                if (!amount || amount <= 0) {
                    toast('Số tiền phải lớn hơn 0', 'error');
                    return false;
                }

                const res = await api(`/goals/${goalId}/contributions`, {
                    method: 'POST',
                    body: {
                        amount,
                        source_wallet_id,
                        contributed_date,
                        create_transaction,
                        note
                    }
                });

                if (toastResult(res, 'Đã nạp tiền vào mục tiêu thành công! 🎉')) {
                    loadGoals();
                    return true;
                }
                return false;
            }
        }
    ]);
}

// Xem chi tiết & lịch sử đóng góp
async function showGoalDetail(id) {
    const res = await api(`/goals/${id}`);
    const g = dataOf(res);
    if (!g) return;

    const wId = g.wallet_id || g.walletId;
    const wName = (g.wallet && g.wallet.name) || (wId ? walletsMap.get(wId) : 'Chưa gắn ví');
    const contributions = g.contributions || [];
    const tAmt = g.target_amount ?? g.targetAmount ?? 0;
    const sAmt = g.saved_amount ?? g.savedAmount ?? 0;
    const mAmt = g.missing_amount ?? g.missingAmount ?? (tAmt - sAmt);
    const tDate = g.target_date || g.targetDate;

    const contribRows = contributions.length > 0 ? contributions.map(c => `
        <tr>
            <td>${fmtDate(c.contributed_date || c.contributedDate || c.created_at || c.createdAt)}</td>
            <td class="pos" style="font-weight:600">+${fmtMoney(c.amount)}</td>
            <td>${esc(c.source_wallet_name || c.sourceWalletName || getWalletName(c.source_wallet_id || c.sourceWalletId) || '-')}</td>
            <td class="muted">${esc(c.note || '-')}</td>
        </tr>
    `).join('') : '<tr><td colspan="4" class="center muted">Chưa có lượt đóng góp nào.</td></tr>';

    openModal(`Chi tiết mục tiêu: ${esc(g.name)}`, `
        <div style="font-size:13px">
            <div class="grid grid-2" style="gap:8px;background:#f8fafc;padding:12px;border-radius:8px;margin-bottom:12px">
                <div><b>Mục tiêu cần đạt:</b> ${fmtMoney(tAmt)}</div>
                <div><b>Đã tiết kiệm:</b> <span class="pos" style="font-weight:700">${fmtMoney(sAmt)}</span></div>
                <div><b>Còn thiếu:</b> <span class="neg">${fmtMoney(mAmt)}</span></div>
                <div><b>Hạn hoàn thành:</b> ${fmtDate(tDate)}</div>
                <div><b>Ví liên kết:</b> ${esc(wName)}</div>
                <div><b>Trạng thái:</b> ${STATUS_MAP[g.status] || badge(g.status)}</div>
            </div>

            <div style="font-weight:600;margin-bottom:6px">Lịch sử nạp/rút tiền (${contributions.length} lần):</div>
            <div class="table-wrap" style="max-height:220px;overflow-y:auto">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Ngày</th>
                            <th>Số tiền</th>
                            <th>Ví nguồn</th>
                            <th>Ghi chú</th>
                        </tr>
                    </thead>
                    <tbody>${contribRows}</tbody>
                </table>
            </div>
        </div>
    `, [{ label: 'Đóng', kind: 'btn-secondary' }]);
}

// Xóa mục tiêu
async function deleteGoal(id) {
    if (!await confirmBox('Bạn có chắc chắn muốn xóa mục tiêu này?')) return;
    const res = await api(`/goals/${id}`, { method: 'DELETE' });
    if (toastResult(res, 'Đã xóa mục tiêu')) {
        loadGoals();
    }
}

// Tạo mới mục tiêu
function openCreateGoal() {
    const walletOpts = Array.from(walletsMap.entries()).map(([wId, name]) =>
        `<option value="${esc(wId)}">${esc(name)}</option>`).join('');

    openModal('Tạo mục tiêu tiết kiệm mới', `
        <div class="form-group">
            <label>Tên mục tiêu tích lũy *</label>
            <input type="text" id="mGoalName" placeholder="VD: Mua Macbook M3, Quỹ dự phòng..." required maxlength="100">
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Số tiền mục tiêu (VNĐ) *</label>
                <input type="number" id="mTargetAmount" placeholder="VD: 30000000" min="1" required>
            </div>
            <div class="form-group">
                <label>Ngày dự kiến hoàn thành</label>
                <input type="date" id="mTargetDate">
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Số tiền ban đầu (nếu có)</label>
                <input type="number" id="mInitialAmount" placeholder="VD: 1000000" min="0">
            </div>
            <div class="form-group">
                <label>Gắn vào ví có sẵn</label>
                <select id="mGoalWallet">
                    <option value="">-- Không gắn ví --</option>
                    ${walletOpts}
                </select>
            </div>
        </div>
        <div class="form-group" style="padding-top:4px">
            <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
                <input type="checkbox" id="mManageWithWallet">
                <span>Tự động tạo một ví Tiết kiệm riêng mang tên mục tiêu này</span>
            </label>
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Tạo mục tiêu',
            kind: 'btn-primary',
            onClick: async () => {
                const name = ($('mGoalName').value || '').trim();
                const target_amount = Number($('mTargetAmount').value);
                const target_date = $('mTargetDate').value || null;
                const initial_amount = $('mInitialAmount').value ? Number($('mInitialAmount').value) : null;
                const wallet_id = $('mGoalWallet').value || null;
                const manage_with_wallet = $('mManageWithWallet').checked;

                if (!name) {
                    toast('Vui lòng nhập tên mục tiêu', 'error');
                    return false;
                }
                if (!target_amount || target_amount <= 0) {
                    toast('Số tiền mục tiêu phải lớn hơn 0', 'error');
                    return false;
                }

                const res = await api('/goals', {
                    method: 'POST',
                    body: {
                        name,
                        target_amount,
                        target_date,
                        initial_amount,
                        wallet_id,
                        manage_with_wallet
                    }
                });

                if (toastResult(res, 'Tạo mục tiêu tiết kiệm thành công')) {
                    loadWallets();
                    loadGoals();
                    return true;
                }
                return false;
            }
        }
    ]);
}

// Gán sự kiện
$('btnCreateGoal').onclick = openCreateGoal;
$('btnReloadGoals').onclick = loadGoals;
$('fStatus').onchange = loadGoals;

// Khởi chạy
(async function init() {
    await loadWallets();
    loadGoals();
})();
