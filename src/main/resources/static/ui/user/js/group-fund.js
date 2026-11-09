// Tab Quỹ: thông tin quỹ, đổi thủ quỹ, kiểm kê (pipeline.md mục 3, 9, 16).
function loadFundTab() {
    const f = G.fund;
    if (!f) {
        $('fundCards').innerHTML = '<div class="notice danger">Nhóm chưa có quỹ.</div>';
        return;
    }
    $('fundCards').innerHTML = `
        <div class="stat"><div class="label">Số dư quỹ hiện tại</div><div class="value">${fmtMoney(f.current_balance)}</div></div>
        <div class="stat"><div class="label">Thủ quỹ</div><div class="value" style="font-size:16px">${esc(G.name(f.keepper_id))}</div><div class="sub"><code>${esc(f.keepper_id)}</code></div></div>
        <div class="stat"><div class="label">Tạo quỹ lúc</div><div class="value" style="font-size:16px">${fmtDateTime(f.created_at)}</div></div>`;

    const options = memberOptions().map(([v, l]) => `<option value="${esc(v)}" ${v === f.keepper_id ? 'selected' : ''}>${esc(l)}</option>`).join('');
    $('fKeeper').innerHTML = options;
    $('rExcluded').innerHTML = memberOptions().map(([v, l]) =>
        `<label class="check"><input type="checkbox" class="ex-check" value="${esc(v)}"> ${esc(l)}</label>`).join('');
}

$('btnKeeper').onclick = async () => {
    const res = await api(G.path('/fund-kepper'), { method: 'PUT', body: { keepper_id: $('fKeeper').value } });
    if (toastResult(res, 'Đã đổi thủ quỹ')) G.reload();
};

$('btnReconcile').onclick = async () => {
    const actual = $('rActual').value;
    if (actual === '') return toast('Nhập số tiền thực tế', 'error');
    const excluded = [...document.querySelectorAll('.ex-check:checked')].map(c => c.value);
    const res = await api(G.path('/fund/reconcile'), {
        method: 'POST',
        body: { actual_balance: Number(actual), reason: $('rReason').value.trim() || null, excluded_user_ids: excluded.length ? excluded : null }
    });
    const r = dataOf(res);
    if (r) {
        openModal('Kết quả kiểm kê', `
            <p>Sổ trước kiểm kê: <b>${fmtMoney(r.previous_balance)}</b><br>Thực tế: <b>${fmtMoney(r.actual_balance)}</b><br>
            Chênh lệch: ${signedMoney(r.difference)}<br>Giao dịch điều chỉnh: ${badge(r.adjustment_type)} <code>${esc(r.transaction_id)}</code></p>`);
        $('rActual').value = '';
        G.reload();
    }
};

G.tabs.fund = loadFundTab;
