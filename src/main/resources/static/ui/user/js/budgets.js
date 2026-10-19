// Quản lý ngân sách: tổng quan hạn mức, danh sách cảnh báo, tiến độ chi tiêu, thêm, sửa, xóa ngân sách.
let categoriesList = [];

const PERIOD_LABELS = {
    WEEK: 'Hàng Tuần',
    MONTH: 'Hàng Tháng',
    QUARTER: 'Hàng Quý',
    YEAR: 'Hàng Năm'
};

const STATUS_BADGES = {
    NORMAL: '<span class="badge success">Bình thường</span>',
    NEAR_LIMIT: '<span class="badge warning">Sắp chạm</span>',
    OVER_LIMIT: '<span class="badge danger">Vượt hạn mức</span>'
};

async function loadCategories() {
    const out = [];
    const walk = async (nodes) => {
        for (const c of nodes) {
            out.push(c);
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

async function loadSummary() {
    const res = await api('/budgets/summary', { silent: true });
    const s = dataOf(res);
    if (!s) return;

    const totalLimit = s.total_limit ?? s.totalLimit ?? 0;
    const totalSpent = s.total_spent ?? s.totalSpent ?? 0;
    const totalRemaining = s.total_remaining ?? s.totalRemaining ?? 0;
    const budgetCount = s.budget_count ?? s.budgetCount ?? 0;
    const overLimitCount = s.over_limit_count ?? s.overLimitCount ?? 0;
    const nearLimitCount = s.near_limit_count ?? s.nearLimitCount ?? 0;

    $('sLimit').textContent = fmtMoney(totalLimit);
    $('sSpent').textContent = fmtMoney(totalSpent);
    $('sRemaining').textContent = fmtMoney(totalRemaining);
    $('sRemaining').className = 'value ' + (totalRemaining >= 0 ? 'pos' : 'neg');

    const pct = s.ratio != null ? (Number(s.ratio) * 100).toFixed(1) : '0';
    $('sRatio').textContent = `đã dùng ${pct}% hạn mức`;
    $('sBudgetCount').textContent = `${budgetCount} ngân sách`;

    const alertTotal = overLimitCount + nearLimitCount;
    $('sAlertCount').textContent = alertTotal;
    $('sAlertCount').className = 'value ' + (alertTotal > 0 ? 'neg' : '');
    $('sAlertSub').textContent = `${overLimitCount} vượt, ${nearLimitCount} sắp chạm`;
}

async function loadAlerts() {
    const res = await api('/budgets/alerts', { silent: true });
    const alerts = dataOf(res) || [];
    const container = $('alertsContainer');

    if (alerts.length === 0) {
        container.style.display = 'none';
        container.innerHTML = '';
        return;
    }

    container.style.display = 'block';
    container.innerHTML = alerts.map(a => {
        const cName = a.category_name || a.categoryName || a.category_code || a.categoryCode;
        const spent = a.spent_amount ?? a.spentAmount ?? 0;
        const limit = a.limit_amount ?? a.limitAmount ?? 0;
        return `
            <div class="alert-card">
                <div>
                    <b>⚠️ Cảnh báo:</b> Ngân sách danh mục <b>${esc(cName)}</b>
                    ${a.status === 'OVER_LIMIT' ? 'đã <b>vượt quá hạn mức</b>' : 'sắp chạm ngưỡng tối đa'}
                    (Đã chi: <span style="font-weight:700">${fmtMoney(spent)}</span> / ${fmtMoney(limit)}).
                </div>
                <span class="badge ${a.status === 'OVER_LIMIT' ? 'danger' : 'warning'}">${a.status === 'OVER_LIMIT' ? 'Vượt mức' : 'Cảnh báo'}</span>
            </div>
        `;
    }).join('');
}

async function loadBudgets() {
    const query = {};
    const period = $('fPeriod').value;
    if (period) query.period = period;

    const active = $('fActive').value;
    if (active !== '') query.is_active = active === 'true';

    $('budgetTableBody').innerHTML = '<tr><td colspan="9" class="center muted">Đang tải dữ liệu...</td></tr>';

    const res = await api('/budgets', { query });
    const list = dataOf(res) || [];

    $('budgetListCount').textContent = `(${list.length})`;

    if (list.length === 0) {
        $('budgetTableBody').innerHTML = '<tr><td colspan="9" class="center muted" style="padding:24px">Chưa có ngân sách nào phù hợp.</td></tr>';
        return;
    }

    $('budgetTableBody').innerHTML = list.map(b => {
        const ratioVal = Number(b.ratio || 0);
        const percent = Math.min(100, Math.round(ratioVal * 100));
        let progressCls = 'progress-green';
        if (ratioVal >= 1.0) progressCls = 'progress-red';
        else if (ratioVal >= 0.8) progressCls = 'progress-yellow';

        const cCode = b.category_code || b.categoryCode;
        const cName = (b.category && b.category.name) || cCode;
        const color = (b.category && b.category.color) || '#3b82f6';
        const pType = b.period_type || b.periodType;
        const pLabel = PERIOD_LABELS[pType] || pType;
        const stBadge = STATUS_BADGES[b.status] || badge(b.status);
        const limitAmt = b.limit_amount ?? b.limitAmount ?? 0;
        const spentAmt = b.spent_amount ?? b.spentAmount ?? 0;
        const remainAmt = b.remaining ?? (limitAmt - spentAmt);
        const daysLeft = b.days_remaining ?? b.daysRemaining;
        const autoRn = b.auto_renew ?? b.autoRenew;

        return `
            <tr>
                <td>
                    <span style="color:${esc(color)}">●</span>
                    <b>${esc(cName)}</b>
                    <small class="muted" style="display:block;font-size:11px">Mã: ${esc(cCode)}</small>
                </td>
                <td><span class="badge info">${esc(pLabel)}</span></td>
                <td class="right" style="font-weight:600">${fmtMoney(limitAmt)}</td>
                <td class="right neg" style="font-weight:600">${fmtMoney(spentAmt)}</td>
                <td class="right ${remainAmt >= 0 ? 'pos' : 'neg'}" style="font-weight:600">${fmtMoney(remainAmt)}</td>
                <td>
                    <div style="display:flex;justify-content:space-between;font-size:11px;font-weight:600;margin-bottom:2px">
                        <span class="${ratioVal >= 1 ? 'neg' : ''}">${(ratioVal * 100).toFixed(1)}%</span>
                        <span class="muted">${fmtMoney(spentAmt)} / ${fmtMoney(limitAmt)}</span>
                    </div>
                    <div class="progress-bar-wrap">
                        <div class="progress-bar-fill ${progressCls}" style="width:${percent}%"></div>
                    </div>
                </td>
                <td class="center">${stBadge}</td>
                <td class="center nowrap" style="font-size:12px">
                    ${daysLeft != null ? `Còn <b>${daysLeft}</b> ngày` : '-'}
                    <div class="muted" style="font-size:11px">${autoRn ? '🔄 Tự gia hạn' : 'Một lần'}</div>
                </td>
                <td class="center nowrap">
                    <button class="btn btn-secondary btn-sm" onclick="openEditBudget('${b.id}')">Sửa</button>
                    <button class="btn btn-danger btn-sm" onclick="deleteBudget('${b.id}')">Xóa</button>
                </td>
            </tr>
        `;
    }).join('');
}

// Xóa ngân sách
async function deleteBudget(id) {
    if (!await confirmBox('Bạn có chắc muốn xóa ngân sách này?')) return;
    const res = await api(`/budgets/${id}`, { method: 'DELETE' });
    if (toastResult(res, 'Đã xóa ngân sách')) {
        loadSummary();
        loadAlerts();
        loadBudgets();
    }
}

// Tạo mới ngân sách
function openCreateBudget() {
    const cateOpts = categoriesList.map(c =>
        `<option value="${esc(c.code)}">${'  '.repeat(c.level || 0)}${esc(c.name)}</option>`).join('');

    const today = new Date().toISOString().split('T')[0];

    openModal('Thiết lập ngân sách chi tiêu', `
        <div class="form-group">
            <label>Danh mục chi tiêu *</label>
            <select id="mCategory" required>
                <option value="">-- Chọn danh mục --</option>
                ${cateOpts}
            </select>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Hạn mức tối đa (VNĐ) *</label>
                <input type="number" id="mLimitAmount" placeholder="VD: 3000000" min="1" required>
            </div>
            <div class="form-group">
                <label>Kỳ ngân sách *</label>
                <select id="mPeriodType" required>
                    <option value="WEEK">Theo Tuần (WEEK)</option>
                    <option value="MONTH" selected>Theo Tháng (MONTH)</option>
                    <option value="QUARTER">Theo Quý (QUARTER)</option>
                    <option value="YEAR">Theo Năm (YEAR)</option>
                </select>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Ngày bắt đầu</label>
                <input type="date" id="mStartDate" value="${today}">
            </div>
            <div class="form-group" style="display:flex;align-items:center;padding-top:24px">
                <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
                    <input type="checkbox" id="mAutoRenew" checked>
                    <span>Tự động gia hạn khi sang kỳ mới</span>
                </label>
            </div>
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Lưu ngân sách',
            kind: 'btn-primary',
            onClick: async () => {
                const category_code = $('mCategory').value;
                const limit_amount = Number($('mLimitAmount').value);
                const period_type = $('mPeriodType').value;
                const start_date = $('mStartDate').value || null;
                const auto_renew = $('mAutoRenew').checked;

                if (!category_code) {
                    toast('Vui lòng chọn danh mục chi tiêu', 'error');
                    return false;
                }
                if (!limit_amount || limit_amount <= 0) {
                    toast('Hạn mức phải lớn hơn 0', 'error');
                    return false;
                }

                const res = await api('/budgets', {
                    method: 'POST',
                    body: {
                        category_code,
                        limit_amount,
                        period_type,
                        start_date,
                        auto_renew
                    }
                });

                if (toastResult(res, 'Tạo ngân sách thành công')) {
                    loadSummary();
                    loadAlerts();
                    loadBudgets();
                    return true;
                }
                return false;
            }
        }
    ]);
}

// Sửa ngân sách
async function openEditBudget(id) {
    const res = await api(`/budgets/${id}`);
    const b = dataOf(res);
    if (!b) return;

    const cCode = b.category_code || b.categoryCode;
    const cName = (b.category && b.category.name) || cCode;
    const pType = b.period_type || b.periodType;
    const limitAmt = b.limit_amount ?? b.limitAmount ?? 0;
    const autoRn = b.auto_renew ?? b.autoRenew ?? true;
    const isAct = b.is_active ?? b.isActive ?? true;

    openModal(`Cập nhật ngân sách - ${esc(cName)}`, `
        <div class="form-group">
            <label>Danh mục</label>
            <input type="text" value="${esc(cName)}" disabled style="background:#f1f5f9">
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Kỳ áp dụng</label>
                <input type="text" value="${esc(PERIOD_LABELS[pType] || pType)}" disabled style="background:#f1f5f9">
            </div>
            <div class="form-group">
                <label>Hạn mức mới (VNĐ) *</label>
                <input type="number" id="mEditLimit" value="${limitAmt}" min="1" required>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label style="display:flex;align-items:center;gap:8px;cursor:pointer;margin-top:8px">
                    <input type="checkbox" id="mEditAutoRenew" ${autoRn ? 'checked' : ''}>
                    <span>Tự động gia hạn</span>
                </label>
            </div>
            <div class="form-group">
                <label style="display:flex;align-items:center;gap:8px;cursor:pointer;margin-top:8px">
                    <input type="checkbox" id="mEditIsActive" ${isAct ? 'checked' : ''}>
                    <span>Đang theo dõi (Hiệu lực)</span>
                </label>
            </div>
        </div>
    `, [
        { label: 'Hủy', kind: 'btn-secondary' },
        {
            label: 'Cập nhật',
            kind: 'btn-primary',
            onClick: async () => {
                const limit_amount = Number($('mEditLimit').value);
                const auto_renew = $('mEditAutoRenew').checked;
                const is_active = $('mEditIsActive').checked;

                if (!limit_amount || limit_amount <= 0) {
                    toast('Hạn mức phải lớn hơn 0', 'error');
                    return false;
                }

                const updateRes = await api(`/budgets/${id}`, {
                    method: 'PATCH',
                    body: {
                        limit_amount,
                        auto_renew,
                        is_active,
                        category_code: cCode,
                        period_type: pType
                    }
                });

                if (toastResult(updateRes, 'Cập nhật ngân sách thành công')) {
                    loadSummary();
                    loadAlerts();
                    loadBudgets();
                    return true;
                }
                return false;
            }
        }
    ]);
}

// Gán sự kiện
$('btnCreateBudget').onclick = openCreateBudget;
$('btnReloadBudgets').onclick = () => {
    loadSummary();
    loadAlerts();
    loadBudgets();
};
$('fPeriod').onchange = loadBudgets;
$('fActive').onchange = loadBudgets;

// Khởi chạy
(async function init() {
    await loadCategories();
    loadSummary();
    loadAlerts();
    loadBudgets();
})();
