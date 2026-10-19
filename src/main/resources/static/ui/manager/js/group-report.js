// Tab Báo cáo tài chính & Quyết toán công nợ nhóm
const curMonth = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};

async function loadSummary() {
    const m = $('rMonth').value || curMonth();
    const res = await api(G.path('/summary'), { query: { month: m } });
    const s = dataOf(res);
    if (!s) {
        $('summaryBox').innerHTML = '<div class="notice">Chưa có dữ liệu báo cáo tháng này.</div>';
        return;
    }

    $('summaryBox').innerHTML = `
        <div class="stat">
            <div class="label">Tổng thu vào quỹ (${esc(m)})</div>
            <div class="value pos">${fmtMoney(s.total_contribution)}</div>
            <div class="sub">${s.contribution_count ?? 0} lượt đóng góp</div>
        </div>
        <div class="stat">
            <div class="label">Tổng chi từ quỹ</div>
            <div class="value neg">${fmtMoney(s.total_expense_from_fund)}</div>
            <div class="sub">${s.expense_count ?? 0} khoản chi tiêu</div>
        </div>
        <div class="stat">
            <div class="label">Chi tiền túi thành viên</div>
            <div class="value">${fmtMoney(s.total_expense_from_personal)}</div>
            <div class="sub">đã chi hộ nhóm</div>
        </div>
        <div class="stat">
            <div class="label">Đã hoàn tiền cho TV</div>
            <div class="value">${fmtMoney(s.total_refund)}</div>
            <div class="sub">quỹ đã thanh toán lại</div>
        </div>`;
}

async function loadBalances() {
    const res = await api(G.path('/balances'));
    const rep = dataOf(res);
    if (!rep) return;

    const list = rep.balances || [];
    const isOwner = G.isOwner;
    const isSettlement = !!rep.is_settlement_enabled;

    // hiển thị tóm tắt công nợ chung
    $('balanceCards').innerHTML = `
        <div class="stat">
            <div class="label">Số dư két quỹ hiện tại</div>
            <div class="value pos">${fmtMoney(rep.fund_balance)}</div>
            <div class="sub">tiền mặt trong két quỹ</div>
        </div>
        <div class="stat">
            <div class="label">Mục tiêu quỹ</div>
            <div class="value">${rep.target != null ? fmtMoney(rep.target) : 'Không đặt mục tiêu'}</div>
            <div class="sub">mục tiêu nhóm đề ra</div>
        </div>
        <div class="stat">
            <div class="label">Cơ chế quyết toán bù trừ</div>
            <div class="value" style="font-size:16px">${isSettlement ? '✓ Đang kích hoạt' : '✕ Đang tắt'}</div>
            <div class="sub">${isSettlement ? `Tổng cần bù thêm: ${fmtMoney(rep.total_needed_contribution)}` : 'Không tính thừa thiếu'}</div>
        </div>`;

    setTable($('balanceBody'), list.map(b => {
        const isMe = b.user_id === G.myId;
        const net = Number(b.net_balance ?? 0);
        const needed = Number(b.needed_contribution ?? 0);
        const needHtml = isSettlement
            ? (needed > 0 ? `<b class="neg">+ ${fmtMoney(needed)}</b>` : '<span class="pos">0 đ (Đủ)</span>')
            : '<span class="muted">-</span>';

        return `<tr>
            <td>
                <b>${esc(b.full_name || G.name(b.user_id))}</b>
                ${isMe ? '<span class="badge info">Tôi</span>' : ''}
            </td>
            <td>${badge(b.status || 'ACTIVE')}</td>
            <td class="right">${fmtMoney(b.total_refunded)}</td>
            <td class="right"><b>${fmtMoney(b.total_share_amount)}</b></td>
            <td class="right">${signedMoney(net)}</td>
            <td class="right">${needHtml}</td>
        </tr>`;
    }).join(''), 6, 'Chưa có dữ liệu phân bổ công nợ');

    // Nếu có danh sách gợi ý thanh toán bù trừ (Settlement suggestions)
    if (rep.settlement_suggestions && rep.settlement_suggestions.length) {
        let sugHtml = `
            <div class="card mt" style="background:#fefce8;border-color:#fde047">
                <div class="card-title">
                    <span>💡 Gợi ý thanh toán bù trừ tối ưu nhất (${rep.settlement_suggestions.length} giao dịch)</span>
                </div>
                <p class="muted" style="font-size:12px">
                    Chỉ cần thực hiện các giao dịch chuyển khoản sau là toàn bộ công nợ trong nhóm sẽ được giải quyết dứt điểm:
                </p>
                <div class="table-wrap">
                    <table class="table">
                        <thead>
                            <tr>
                                <th>Người chuyển tiền</th>
                                <th>→</th>
                                <th>Người nhận tiền</th>
                                <th class="right">Số tiền chuyển</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rep.settlement_suggestions.map(s => `
                                <tr>
                                    <td><b>${esc(G.name(s.from_user_id))}</b></td>
                                    <td>→</td>
                                    <td><b>${esc(G.name(s.to_user_id))}</b></td>
                                    <td class="right"><b style="color:var(--primary);font-size:15px">${fmtMoney(s.amount)}</b></td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            </div>`;

        let existingSug = $('settlementWrap');
        if (!existingSug) {
            existingSug = document.createElement('div');
            existingSug.id = 'settlementWrap';
            $('pane-report').appendChild(existingSug);
        }
        existingSug.innerHTML = sugHtml;
    }
}

$('btnSummary').onclick = loadSummary;
$('btnBalances').onclick = loadBalances;

G.tabs.report = () => {
    if (!$('rMonth').value) $('rMonth').value = curMonth();
    loadSummary();
    loadBalances();
};
