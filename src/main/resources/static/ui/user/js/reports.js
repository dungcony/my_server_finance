// Báo cáo & phân tích tài chính: tóm tắt thu chi, phân tích tỷ trọng danh mục, xu hướng theo ngày/tháng và xuất dữ liệu.
let walletsMap = new Map();

async function loadWallets() {
    const res = await api('/wallets', { silent: true });
    const data = dataOf(res) || {};
    const list = data.wallets || (Array.isArray(data) ? data : []);
    walletsMap.clear();

    const select = $('fWallet');
    select.innerHTML = '<option value="">-- Tất cả các ví --</option>';

    list.forEach(w => {
        walletsMap.set(w.id, w.name);
        const opt = document.createElement('option');
        opt.value = w.id;
        opt.textContent = w.name;
        select.appendChild(opt);
    });
    return list;
}

function getFilterParams() {
    const period = $('fPeriod').value;
    const walletId = $('fWallet').value;
    const fromDate = $('fFromDate').value;
    const toDate = $('fToDate').value;

    const query = {};
    if (period !== 'custom') {
        query.period = period;
    } else {
        if (fromDate) query.from_date = fromDate;
        if (toDate) query.to_date = toDate;
    }
    if (walletId) query.wallet_id = walletId;
    return query;
}

async function loadSummary() {
    const query = getFilterParams();
    const res = await api('/reports/summary', { query, silent: true });
    const s = dataOf(res);
    if (!s) return;

    const totalIncome = s.total_income ?? s.totalIncome ?? 0;
    const totalExpense = s.total_expense ?? s.totalExpense ?? 0;
    const netIncome = s.net_income ?? s.netIncome ?? (totalIncome - totalExpense);
    const avgDaily = s.average_daily_expense ?? s.averageDailyExpense ?? 0;
    const txnCount = s.transaction_count ?? s.transactionCount ?? 0;
    const openBal = s.opening_balance ?? s.openingBalance ?? 0;
    const closeBal = s.closing_balance ?? s.closingBalance ?? 0;

    $('rIncome').textContent = fmtMoney(totalIncome);
    $('rExpense').textContent = fmtMoney(totalExpense);

    const net = Number(netIncome);
    $('rNet').textContent = fmtMoney(net);
    $('rNet').className = 'value ' + (net >= 0 ? 'pos' : 'neg');

    $('rDailyAvg').textContent = fmtMoney(avgDaily);
    $('rTxnCount').textContent = `${txnCount} giao dịch`;

    // Nhãn so sánh với kỳ trước
    const comp = s.comparison || {};
    if (comp.label) {
        $('rExpenseSub').textContent = esc(comp.label);
    } else if (comp.difference != null) {
        const diff = Number(comp.difference);
        $('rExpenseSub').textContent = `${diff >= 0 ? 'Tăng' : 'Giảm'} ${fmtMoney(Math.abs(diff))} so với kỳ trước`;
    } else {
        $('rExpenseSub').textContent = 'so với kỳ trước';
    }

    $('rBalanceSub').textContent = `Đầu kỳ: ${fmtMoney(openBal)} | Cuối kỳ: ${fmtMoney(closeBal)}`;
}

async function loadCategoryBreakdown() {
    const query = getFilterParams();
    query.type = $('fCateType').value || 'expense';
    query.limit = 15;

    $('cateBreakdownBody').innerHTML = '<tr><td colspan="4" class="center muted">Đang tải phân tích danh mục...</td></tr>';

    const res = await api('/reports/by-category', { query, silent: true });
    const data = dataOf(res);
    const items = (data && data.categories) || [];

    if (items.length === 0) {
        $('cateBreakdownBody').innerHTML = '<tr><td colspan="4" class="center muted" style="padding:24px">Chưa có giao dịch phát sinh trong kỳ này.</td></tr>';
        return;
    }

    $('cateBreakdownBody').innerHTML = items.map(c => {
        const pct = c.percentage != null ? Number(c.percentage).toFixed(1) : '0';
        const color = c.color || '#3b82f6';
        const txnCount = c.transaction_count ?? c.transactionCount ?? 0;
        return `
            <tr>
                <td>
                    <span style="color:${esc(color)}">●</span>
                    <b>${esc(c.name || c.code)}</b>
                </td>
                <td class="right muted">${txnCount}</td>
                <td class="right" style="font-weight:600">${fmtMoney(c.amount)}</td>
                <td>
                    <div style="display:flex;align-items:center;gap:6px">
                        <div style="flex:1;background:#e2e8f0;height:6px;border-radius:3px;overflow:hidden">
                            <div style="width:${Math.min(100, Math.round(pct))}%;height:100%;background:${esc(color)};border-radius:3px"></div>
                        </div>
                        <span style="font-size:11px;font-weight:600;min-width:36px;text-align:right">${pct}%</span>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

async function loadTrend() {
    const view = $('fTrendView').value;
    const walletId = $('fWallet').value;
    const query = {};
    if (walletId) query.wallet_id = walletId;

    $('trendBody').innerHTML = '<tr><td colspan="4" class="center muted">Đang tải xu hướng...</td></tr>';

    if (view === 'daily') {
        const res = await api('/reports/daily-trend', { query, silent: true });
        const data = dataOf(res);
        const days = (data && data.days) || [];

        if (days.length === 0) {
            $('trendBody').innerHTML = '<tr><td colspan="4" class="center muted" style="padding:24px">Không có dữ liệu ngày nào trong tháng.</td></tr>';
            return;
        }

        $('trendBody').innerHTML = days.map(d => {
            const net = Number(d.net || 0);
            return `
                <tr>
                    <td class="nowrap"><b>${fmtDate(d.date)}</b></td>
                    <td class="right pos">${d.income > 0 ? '+' + fmtMoney(d.income) : '-'}</td>
                    <td class="right neg">${d.expense > 0 ? '-' + fmtMoney(d.expense) : '-'}</td>
                    <td class="right ${net >= 0 ? 'pos' : 'neg'}" style="font-weight:600">${fmtMoney(net)}</td>
                </tr>
            `;
        }).join('');
    } else {
        query.months_count = 6;
        const res = await api('/reports/monthly-trend', { query, silent: true });
        const data = dataOf(res);
        const months = (data && data.months) || [];

        if (months.length === 0) {
            $('trendBody').innerHTML = '<tr><td colspan="4" class="center muted" style="padding:24px">Không có dữ liệu xu hướng tháng.</td></tr>';
            return;
        }

        $('trendBody').innerHTML = months.map(m => {
            const net = Number(m.net || 0);
            return `
                <tr>
                    <td class="nowrap"><b>Tháng ${esc(m.month)}</b></td>
                    <td class="right pos">${m.income > 0 ? '+' + fmtMoney(m.income) : '-'}</td>
                    <td class="right neg">${m.expense > 0 ? '-' + fmtMoney(m.expense) : '-'}</td>
                    <td class="right ${net >= 0 ? 'pos' : 'neg'}" style="font-weight:600">${fmtMoney(net)}</td>
                </tr>
            `;
        }).join('');
    }
}

// Xuất file CSV
async function handleExportCsv() {
    const query = getFilterParams();
    const reqBody = {
        format: 'csv'
    };
    if (query.period) reqBody.period = query.period;
    if (query.from_date) reqBody.from_date = query.from_date;
    if (query.to_date) reqBody.to_date = query.to_date;

    toast('Đang tạo tiến trình xuất báo cáo...', 'info');

    const res = await api('/reports/export', {
        method: 'POST',
        body: reqBody
    });

    const job = dataOf(res);
    if (!job) {
        toast('Không thể khởi tạo tiến trình xuất báo cáo', 'error');
        return;
    }

    toast(`Tiến trình xuất file #${job.id || 'job'} đang xử lý. Bạn có thể tải khi hoàn tất.`, 'success');
}

// Gán sự kiện
$('fPeriod').onchange = () => {
    const isCustom = $('fPeriod').value === 'custom';
    $('boxFromDate').style.display = isCustom ? 'block' : 'none';
    $('boxToDate').style.display = isCustom ? 'flex' : 'none';
    if (!isCustom) {
        loadSummary();
        loadCategoryBreakdown();
        loadTrend();
    }
};

$('btnApplyReport').onclick = () => {
    loadSummary();
    loadCategoryBreakdown();
    loadTrend();
};

$('fWallet').onchange = () => {
    loadSummary();
    loadCategoryBreakdown();
    loadTrend();
};

$('fCateType').onchange = loadCategoryBreakdown;
$('fTrendView').onchange = loadTrend;
$('btnExportCsv').onclick = handleExportCsv;

// Khởi chạy
(async function init() {
    $('boxFromDate').style.display = 'none';
    $('boxToDate').style.display = 'flex';
    await loadWallets();
    loadSummary();
    loadCategoryBreakdown();
    loadTrend();
})();
