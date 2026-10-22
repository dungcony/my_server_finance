// Tab Báo cáo: tổng quan theo tháng và bảng phần của từng thành viên trong quỹ (pipeline.md mục 4, 5).
async function loadSummary() {
    const month = $('rMonth').value;
    const s = dataOf(await api(G.path('/summary'), { query: { month } }));
    if (!s) return;
    const f = s.fund || {};
    $('summaryBox').innerHTML = `
        <div class="stat"><div class="label">Kỳ báo cáo</div><div class="value" style="font-size:16px">${esc(s.period || 'Toàn thời gian')}</div></div>
        <div class="stat"><div class="label">Số dư quỹ</div><div class="value">${fmtMoney(f.current_balance)}</div></div>
        <div class="stat"><div class="label">Mục tiêu</div><div class="value" style="font-size:16px">${s.target != null ? fmtMoney(s.target) : 'Chưa đặt'}</div></div>
        <div class="stat"><div class="label">Tổng chi</div><div class="value neg">${fmtMoney(s.total_expense)}</div></div>
        <div class="stat"><div class="label">Tổng góp</div><div class="value pos">${fmtMoney(s.total_contribution)}</div></div>`;
}

async function loadBalances() {
    const r = dataOf(await api(G.path('/balances')));
    if (!r) return;
    const settle = !!r.is_settlement_enabled;
    $('thNeed').classList.toggle('hidden', !settle);
    $('balanceCards').innerHTML = `
        <div class="stat"><div class="label">Số dư quỹ</div><div class="value">${fmtMoney(r.fund_balance)}</div></div>
        <div class="stat"><div class="label">Mục tiêu</div><div class="value" style="font-size:16px">${r.target != null ? fmtMoney(r.target) : 'Chưa đặt'}</div></div>
        <div class="stat"><div class="label">Tính thừa thiếu</div><div class="value" style="font-size:16px">${settle ? 'Bật' : 'Tắt'}</div>
            <div class="sub">${settle ? `Tổng cần nộp thêm: ${fmtMoney(r.total_needed_contribution)}` : 'Chỉ hiển thị phần còn lại trong quỹ'}</div></div>`;

    setTable($('balanceBody'), (r.balances || []).map(b => `
        <tr>
            <td><b>${esc(b.full_name || shortId(b.user_id))}</b></td>
            <td>${badge(b.status)}</td>
            <td class="right">${fmtMoney(b.total_paid_out_of_pocket)}</td>
            <td class="right">${fmtMoney(b.total_refunded)}</td>
            <td class="right">${fmtMoney(b.total_share_amount)}</td>
            <td class="right">${signedMoney(b.net_balance)}</td>
            <td class="right ${settle ? '' : 'hidden'}">${b.needed_contribution > 0 ? `<b class="neg">${fmtMoney(b.needed_contribution)}</b>` : '0 đ'}</td>
        </tr>`).join(''), 7, 'Chưa có dữ liệu');
}

$('btnSummary').onclick = loadSummary;
$('btnBalances').onclick = loadBalances;
G.tabs.report = () => { loadSummary(); loadBalances(); };
