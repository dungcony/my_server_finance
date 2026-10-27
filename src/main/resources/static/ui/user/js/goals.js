// Quản lý mục tiêu tiết kiệm: danh sách dạng thẻ, tiến độ, gợi ý AI, nộp tiền/góp tiền, rút tiền, tạo mới và xóa mục tiêu.

const STATUS_MAP = {
    in_progress: '<span class="badge info">Đang tích lũy</span>',
    completed: '<span class="badge success">Đã hoàn thành 🎉</span>',
    cancelled: '<span class="badge warning">Đã hủy</span>',
    IN_PROGRESS: '<span class="badge info">Đang tích lũy</span>',
    COMPLETED: '<span class="badge success">Đã hoàn thành 🎉</span>',
    CANCELLED: '<span class="badge warning">Đã hủy</span>'
};

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
        const stUpper = (g.status || '').toUpperCase();
        if (stUpper === 'COMPLETED') completedCount++;
        else if (stUpper === 'IN_PROGRESS') inProgressCount++;
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
        const tAmt = g.target_amount ?? g.targetAmount ?? 0;
        const sAmt = g.saved_amount ?? g.savedAmount ?? 0;
        const tDate = g.target_date || g.targetDate;
        const daysLeft = g.days_remaining ?? g.daysRemaining;
        const note = g.note || 'Nuôi heo đất / Tự giữ';

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
                            📍 <b>${esc(note)}</b>
                        </div>
                    </div>

                    ${suggHtml}
                </div>

                <div style="margin-top:16px;display:flex;gap:6px;justify-content:flex-end">
                    ${(g.status === 'in_progress' || g.status === 'IN_PROGRESS') ? `
                        <button class="btn btn-sm" onclick="openDepositModal('${g.id}', '${esc(g.name)}')">+ Góp tiền</button>
                    ` : ''}
                    ${sAmt > 0 ? `
                        <button class="btn btn-secondary btn-sm" onclick="openWithdrawModal('${g.id}', '${esc(g.name)}', ${sAmt})">- Rút tiền</button>
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
    const today = new Date().toISOString().split('T')[0];

    openModal(`Góp tiền vào: ${goalName}`, `
        <div class="form-group">
            <label>Số tiền nạp (VNĐ) *</label>
            <input type="number" id="mDepAmount" placeholder="VD: 500000" min="1" required>
        </div>
        <div class="form-group">
            <label>Ngày nạp tiền</label>
            <input type="date" id="mDepDate" value="${today}">
        </div>
        <div class="form-group">
            <label>Ghi chú</label>
            <input type="text" id="mDepNote" placeholder="VD: Bỏ heo đất, trích thưởng tuần...">
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Xác nhận nạp tiền',
            kind: 'btn-primary',
            onClick: async () => {
                const amount = Number($('mDepAmount').value);
                const contributed_date = $('mDepDate').value || null;
                const note = ($('mDepNote').value || '').trim() || null;

                if (!amount || amount <= 0) {
                    toast('Số tiền phải lớn hơn 0', 'error');
                    return false;
                }

                const res = await api(`/goals/${goalId}/contributions`, {
                    method: 'POST',
                    body: {
                        amount,
                        contributed_date,
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

// Rút bớt tiền khi có việc gấp
function openWithdrawModal(goalId, goalName, currentSaved) {
    const today = new Date().toISOString().split('T')[0];

    openModal(`Rút tiền từ: ${goalName}`, `
        <div class="form-group">
            <label>Số tiền rút (VNĐ) * (Tối đa: ${fmtMoney(currentSaved)})</label>
            <input type="number" id="mWithAmount" placeholder="VD: 200000" min="1" max="${currentSaved}" required>
        </div>
        <div class="form-group">
            <label>Ngày rút</label>
            <input type="date" id="mWithDate" value="${today}">
        </div>
        <div class="form-group">
            <label>Lý do rút tiền</label>
            <input type="text" id="mWithNote" placeholder="VD: Cần tiền gấp, sửa xe...">
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Xác nhận rút tiền',
            kind: 'btn-danger',
            onClick: async () => {
                const amount = Number($('mWithAmount').value);
                const withdraw_date = $('mWithDate').value || null;
                const note = ($('mWithNote').value || '').trim() || null;

                if (!amount || amount <= 0) {
                    toast('Số tiền rút phải lớn hơn 0', 'error');
                    return false;
                }
                if (amount > currentSaved) {
                    toast(`Số tiền rút không được vượt quá ${fmtMoney(currentSaved)}`, 'error');
                    return false;
                }

                const res = await api(`/goals/${goalId}/withdraw`, {
                    method: 'POST',
                    body: {
                        amount,
                        withdraw_date,
                        note
                    }
                });

                if (toastResult(res, 'Đã rút tiền khỏi mục tiêu')) {
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

    const goalInfo = g.goal || g;
    const contributions = g.contributions || [];
    const tAmt = goalInfo.target_amount ?? goalInfo.targetAmount ?? 0;
    const sAmt = goalInfo.saved_amount ?? goalInfo.savedAmount ?? 0;
    const mAmt = goalInfo.missing_amount ?? goalInfo.missingAmount ?? (tAmt - sAmt);
    const tDate = goalInfo.target_date || goalInfo.targetDate;

    const contribRows = contributions.length > 0 ? contributions.map(c => {
        const isWithdraw = c.amount < 0;
        const amtCls = isWithdraw ? 'neg' : 'pos';
        const sign = isWithdraw ? '' : '+';
        return `
            <tr>
                <td>${fmtDate(c.contributed_date || c.contributedDate || c.created_at || c.createdAt)}</td>
                <td class="${amtCls}" style="font-weight:600">${sign}${fmtMoney(c.amount)}</td>
                <td class="muted">${esc(c.note || '-')}</td>
            </tr>
        `;
    }).join('') : '<tr><td colspan="3" class="muted center">Chưa có lịch sử nạp/rút tiền nào</td></tr>';

    openModal(`Chi tiết: ${esc(goalInfo.name)}`, `
        <div style="display:flex;gap:12px;margin-bottom:16px;align-items:center">
            ${STATUS_MAP[goalInfo.status] || badge(goalInfo.status)}
            <span class="muted">📍 Nơi giữ: <b>${esc(goalInfo.note || 'Nuôi heo đất / Tự giữ')}</b></span>
        </div>

        <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px;margin-bottom:20px;text-align:center">
            <div class="card" style="padding:12px;margin:0">
                <small class="muted">Mục tiêu</small>
                <div style="font-size:16px;font-weight:700">${fmtMoney(tAmt)}</div>
            </div>
            <div class="card" style="padding:12px;margin:0">
                <small class="muted">Đã tích lũy</small>
                <div style="font-size:16px;font-weight:700" class="pos">${fmtMoney(sAmt)}</div>
            </div>
            <div class="card" style="padding:12px;margin:0">
                <small class="muted">Còn thiếu</small>
                <div style="font-size:16px;font-weight:700" class="${mAmt > 0 ? 'neg' : 'pos'}">${fmtMoney(Math.max(0, mAmt))}</div>
            </div>
        </div>

        <h4 style="margin-bottom:8px">Lịch sử tích lũy & rút tiền (${contributions.length})</h4>
        <div style="max-height:240px;overflow-y:auto;border:1px solid var(--border);border-radius:6px">
            <table class="table" style="margin:0">
                <thead>
                    <tr>
                        <th>Ngày</th>
                        <th>Số tiền</th>
                        <th>Ghi chú</th>
                    </tr>
                </thead>
                <tbody>
                    ${contribRows}
                </tbody>
            </table>
        </div>
    `, [
        { label: 'Đóng', kind: 'btn-secondary' }
    ]);
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
    openModal('Tạo mục tiêu tiết kiệm mới', `
        <div class="form-group">
            <label>Tên mục tiêu tích lũy *</label>
            <input type="text" id="mGoalName" placeholder="VD: Mua Macbook M4, Quỹ dự phòng..." required maxlength="100">
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
                <label>Ghi chú nơi giữ tiền</label>
                <input type="text" id="mGoalNote" placeholder="VD: Heo đất trong tủ, Sổ tiết kiệm Agribank, Mẹ giữ hộ...">
            </div>
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
                const note = ($('mGoalNote').value || '').trim() || null;

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
                        note
                    }
                });

                if (toastResult(res, 'Tạo mục tiêu tiết kiệm thành công')) {
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
(function init() {
    loadGoals();
})();
