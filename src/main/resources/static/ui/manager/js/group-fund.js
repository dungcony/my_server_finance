// Tab Két quỹ & Kiểm kê: Đối soát số dư két thực tế, Bổ nhiệm thủ quỹ
function loadFundTab() {
    const f = G.fund;
    if (!f) {
        $('fundCards').innerHTML = '<div class="notice danger">Nhóm chưa khởi tạo quỹ.</div>';
        return;
    }

    const keeperName = G.name(f.keepper_id);
    const isMeKeeper = f.keepper_id === G.myId;

    $('fundCards').innerHTML = `
        <div class="stat">
            <div class="label">Số dư sổ sách hệ thống</div>
            <div class="value pos">${fmtMoney(f.current_balance)}</div>
            <div class="sub">tiền mặt trong két quỹ hiện tại</div>
        </div>
        <div class="stat">
            <div class="label">Người giữ két (Thủ quỹ)</div>
            <div class="value" style="font-size:17px">
                💰 ${esc(keeperName)} ${isMeKeeper ? '<span class="badge warning">Tôi</span>' : ''}
            </div>
            <div class="sub"><code>${esc(f.keepper_id)}</code></div>
        </div>
        <div class="stat">
            <div class="label">Thời điểm lập quỹ</div>
            <div class="value" style="font-size:17px">${fmtDateTime(f.created_at)}</div>
            <div class="sub">trạng thái vận hành ổn định</div>
        </div>`;

    // Dropdown chọn thủ quỹ mới (dành cho Chủ nhóm)
    const options = memberOptions().map(([v, l]) =>
        `<option value="${esc(v)}" ${v === f.keepper_id ? 'selected' : ''}>${esc(l)}</option>`).join('');
    $('fKeeper').innerHTML = options;

    // Checkbox loại trừ khi kiểm kê
    $('rExcluded').innerHTML = memberOptions().map(([v, l]) =>
        `<label class="check" style="margin-bottom:4px">
            <input type="checkbox" class="ex-check" value="${esc(v)}"> ${esc(l)}
        </label>`).join('');
}

// Đổi thủ quỹ (Chủ nhóm)
$('btnKeeper').onclick = async () => {
    const newKeeperId = $('fKeeper').value;
    if (!newKeeperId) return toast('Vui lòng chọn thủ quỹ', 'error');

    const res = await api(G.path('/fund-kepper'), {
        method: 'PUT',
        body: { keepper_id: newKeeperId }
    });

    if (toastResult(res, 'Đã bổ nhiệm Thủ quỹ mới thành công!')) {
        G.reload();
    }
};

// Kiểm kê đối soát quỹ (Thủ quỹ & Chủ nhóm)
$('btnReconcile').onclick = async () => {
    const actual = $('rActual').value;
    if (actual === '') return toast('Vui lòng nhập số tiền thực tế đếm được trong két', 'error');

    const excluded = [...document.querySelectorAll('.ex-check:checked')].map(c => c.value);

    const res = await api(G.path('/fund/reconcile'), {
        method: 'POST',
        body: {
            actual_balance: Number(actual),
            reason: $('rReason').value.trim() || null,
            excluded_user_ids: excluded.length ? excluded : null
        }
    });

    const r = dataOf(res);
    if (r) {
        openModal('Kết quả kiểm kê đối soát két quỹ', `
            <div class="stat" style="margin-bottom:14px">
                <div class="label">Chênh lệch két tiền</div>
                <div class="value">${signedMoney(r.difference)}</div>
                <div class="sub">${r.difference === 0 ? 'Khớp hoàn toàn với sổ sách' : (r.difference > 0 ? 'Két thừa tiền so với sổ' : 'Két hụt tiền so với sổ')}</div>
            </div>
            <p>
                Số dư sổ sách trước kiểm kê: <b>${fmtMoney(r.previous_balance)}</b><br>
                Số tiền thực tế đếm được: <b>${fmtMoney(r.actual_balance)}</b><br>
                Giao dịch điều chỉnh tự động: ${badge(r.adjustment_type)} <code>${esc(r.transaction_id)}</code>
            </p>`, [
            { label: 'Đóng', kind: 'btn-secondary' }
        ]);

        $('rActual').value = '';
        $('rReason').value = '';
        G.reload();
    }
};

G.tabs.fund = loadFundTab;
