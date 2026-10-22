// Tab Giao dịch & Thu chi nhóm: Ghi nhận, Duyệt, Từ chối, Duyệt hàng loạt, Sửa, Xoá
const TXN_PAGE_SIZE = 20;
let txnPage = 1;
let categories = null;
let selectedTxn = new Set();

const toLocalInput = (iso) => {
    if (!iso) return '';
    const d = new Date(iso);
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};
const fromLocalInput = (v) => (v ? new Date(v).toISOString() : undefined);

async function loadCategories() {
    if (categories) return categories;
    const out = [];
    const walk = async (nodes) => {
        for (const c of nodes) {
            out.push(c);
            if (!c.is_leaf) {
                const children = dataOf(await api(`/categories/${encodeURIComponent(c.code)}/children`, { silent: true })) || [];
                await walk(children);
            }
        }
    };
    await walk(dataOf(await api('/categories', { silent: true })) || []);
    categories = out;
    return categories;
}

const categoryOption = (c) => `${'  '.repeat(c.level || 0)}${c.name}`;
const categoryName = (code) => {
    const c = (categories || []).find(x => x.code === code);
    return c ? c.name : (code || '-');
};

async function prepareTxnForm() {
    await loadCategories();
    const cur = $('tTransactor').value;
    $('tTransactor').innerHTML = memberOptions().map(([v, l]) => `<option value="${esc(v)}">${esc(l)}</option>`).join('');
    $('tTransactor').value = cur || G.myId;
    $('tCategory').innerHTML = '<option value="">-- Chọn danh mục chi tiêu --</option>'
        + categories.map(c => `<option value="${esc(c.code)}">${esc(categoryOption(c))}</option>`).join('');

    $('tParticipants').innerHTML = G.activeMembers().map(m => `
        <div class="form-row" style="align-items:center;margin-bottom:6px">
            <label class="check" style="flex:1;margin:0">
                <input type="checkbox" class="p-check" data-uid="${esc(m.user_id)}" checked>
                ${esc(m.display_name || shortId(m.user_id))}
            </label>
            <input type="number" class="p-amount" data-uid="${esc(m.user_id)}" placeholder="Số tiền phân bổ" style="width:160px">
        </div>`).join('');

    onTypeChange();
}

function onTypeChange() {
    const type = $('tType').value;
    const expense = type === 'EXPENSE';
    $('tCategoryWrap').classList.toggle('hidden', !expense);
    $('tParticipantsWrap').classList.toggle('hidden', !expense);

    if (type === 'CONTRIBUTION') {
        $('tSource').value = 'PERSONAL';
        $('tSource').disabled = true;
    } else if (type === 'REFUND') {
        $('tSource').value = 'FUND';
        $('tSource').disabled = true;
    } else {
        // EXPENSE: Member chỉ được chi bằng PERSONAL
        if (G.isMember) {
            $('tSource').value = 'PERSONAL';
            $('tSource').disabled = true;
        } else {
            $('tSource').disabled = false;
        }
    }
}

function collectParticipants(amount) {
    if ($('tSplitAll').checked) {
        const actIds = G.activeMembers().map(m => m.user_id);
        return equalSplit(amount, actIds.length ? actIds : [G.myId]);
    }
    const checked = [...document.querySelectorAll('#tParticipants .p-check:checked')];
    if (!checked.length) throw new Error('Cần chọn ít nhất một thành viên tham gia chia khoản chi này');
    return checked.map(cb => {
        const share = Number(document.querySelector(`#tParticipants .p-amount[data-uid="${cb.dataset.uid}"]`).value);
        if (!share || share <= 0) throw new Error('Mỗi người được chọn phải có số tiền > 0 (hoặc bấm Tự động chia đều)');
        return { user_id: cb.dataset.uid, share_amount: share };
    });
}

async function createTxn() {
    const type = $('tType').value;
    const amount = Number($('tAmount').value);
    if (!amount || amount <= 0) return toast('Vui lòng nhập số tiền hợp lệ (> 0)', 'error');
    if (type === 'EXPENSE' && !$('tCategory').value) return toast('Khoản chi tiêu bắt buộc phải chọn danh mục', 'error');

    const transactorId = G.isMember ? G.myId : ($('tTransactor').value || G.myId);

    let participants = [];
    if (type === 'EXPENSE') {
        try {
            participants = collectParticipants(amount);
        } catch (e) {
            return toast(e.message, 'error');
        }
        const sum = participants.reduce((s, p) => s + p.share_amount, 0);
        if (participants.length && sum !== amount) {
            return toast(`Tổng chia (${fmtMoney(sum)}) không khớp với số tiền giao dịch (${fmtMoney(amount)})`, 'error');
        }
    } else {
        // CONTRIBUTION hoặc REFUND: gán transactor là người tham gia đối ứng với share_amount = amount
        participants = [{ user_id: transactorId, share_amount: amount }];
    }

    if (!participants.length) {
        participants = [{ user_id: transactorId, share_amount: amount }];
    }

    const body = {
        type,
        money_source: G.isMember ? 'PERSONAL' : $('tSource').value,
        amount,
        transactor_id: transactorId,
        category_code: type === 'EXPENSE' ? $('tCategory').value : null,
        occurred_at: fromLocalInput($('tWhen').value) || new Date().toISOString(),
        note: $('tNote').value.trim() || null,
        participants
    };

    const res = await api(G.path('/transactions'), { method: 'POST', body });
    const st = dataOf(res)?.status || '';
    if (toastResult(res, `Đã ghi nhận giao dịch thành công (Trạng thái: ${st})`)) {
        $('tAmount').value = '';
        $('tNote').value = '';
        G.reload();
    }
}

$('tType').onchange = onTypeChange;
$('tSplitAll').onchange = () => $('tCustom').classList.toggle('hidden', $('tSplitAll').checked);
$('btnAutoSplit').onclick = () => {
    const amount = Number($('tAmount').value);
    const checked = [...document.querySelectorAll('#tParticipants .p-check:checked')].map(c => c.dataset.uid);
    if (!amount || !checked.length) return toast('Vui lòng nhập số tiền và chọn ít nhất một người', 'error');
    equalSplit(amount, checked).forEach(p => {
        const inp = document.querySelector(`#tParticipants .p-amount[data-uid="${p.user_id}"]`);
        if (inp) inp.value = p.share_amount;
    });
};
$('btnCreateTxn').onclick = createTxn;

// ----- Danh sách giao dịch -----

async function loadTxnList(page = 1) {
    txnPage = page;
    selectedTxn = new Set();
    updateBulkBar();

    const mode = $('lMode').value;
    const query = { page: page - 1, size: TXN_PAGE_SIZE, page_size: TXN_PAGE_SIZE }; // Spring Data Pageable
    const isMember = G.isMember;

    let path = '/transactions';
    if (mode === 'mine') {
        path = '/transactions/mine';
        if ($('lStatus').value) query.statuses = $('lStatus').value;
    } else if (mode === 'pending') {
        if (isMember) {
            // member chỉ xem được các giao dịch chờ duyệt của chính mình
            path = '/transactions/mine';
            query.statuses = 'PENDING';
        } else {
            // reviewer/owner xem toàn bộ giao dịch chờ duyệt của nhóm
            path = '/transactions/all';
            query.statuses = 'PENDING';
        }
    } else {
        // chế độ xem chung
        if (isMember) {
            // member gọi endpoint genera (/transactions) chỉ lấy giao dịch confirmed
            path = '/transactions';
        } else {
            // reviewer gọi /transactions/all để xem toàn bộ trạng thái
            path = '/transactions/all';
            if ($('lStatus').value) query.statuses = $('lStatus').value;
        }
    }

    if ($('lType').value) query.type = $('lType').value;
    if ($('lSource').value) query.money_source = $('lSource').value;

    const res = await api(G.path(path), { query });
    const data = dataOf(res) || {};
    // DTO GroupTxnListRes: records / content
    const rawItems = data.items || data.content || data.transactions || [];
    const items = rawItems.map(t => {
        t.id = t.id || t.txn_id || t.txnId;
        return t;
    });
    const meta = data.meta || { page, total_pages: data.totalPages, total_items: data.totalElements };

    const isReviewer = G.isReviewer;
    const showBulk = (mode === 'pending' || !mode) && isReviewer;
    $('thBulkCheck').classList.toggle('hidden', !showBulk);

    setTable($('txnBody'), items.map(t => {
        const rowCheck = showBulk && t.status === 'PENDING'
            ? `<input type="checkbox" class="row-check" data-id="${t.id}">`
            : '';

        return `<tr>
            <td class="${showBulk ? '' : 'hidden'}">${rowCheck}</td>
            <td>${badge(t.type)}</td>
            <td>${badge(t.money_source, 'info')}</td>
            <td class="right"><b>${fmtMoney(t.amount)}</b></td>
            <td>${badge(t.status)}</td>
            <td>${esc(G.name(t.transactor_id))}</td>
            <td>${esc(t.note || '-')}</td>
            <td class="nowrap">${fmtDate(t.occurred_at || t.created_at)}</td>
            <td class="nowrap">${rowActions(t)}</td>
        </tr>`;
    }).join(''), showBulk ? 9 : 8, 'Không có giao dịch nào phù hợp');

    renderPager($('txnPager'), meta, p => loadTxnList(p));

    $('txnBody').querySelectorAll('[data-act]').forEach(b => {
        b.onclick = () => txnAction(b.dataset.act, items.find(t => String(t.id) === String(b.dataset.id)));
    });

    $('txnBody').querySelectorAll('.row-check').forEach(c => {
        c.onchange = () => {
            c.checked ? selectedTxn.add(c.dataset.id) : selectedTxn.delete(c.dataset.id);
            updateBulkBar();
        };
    });
}

function rowActions(t) {
    const mine = t.created_by === G.myId || t.transactor_id === G.myId;
    const isReviewer = G.isReviewer;
    const a = [`<button class="btn btn-secondary btn-sm" data-act="detail" data-id="${t.id}">Chi tiết</button>`];

    // Chỉ Thủ quỹ hoặc Chủ nhóm mới có nút Duyệt / Từ chối
    if (isReviewer && t.status === 'PENDING') {
        a.push(
            `<button class="btn btn-success btn-sm" data-act="confirm" data-id="${t.id}">✓ Duyệt</button>`,
            `<button class="btn btn-danger btn-sm" data-act="reject" data-id="${t.id}">✕ Từ chối</button>`
        );
    }

    // Sửa giao dịch
    const canEdit = isReviewer || (mine && t.status === 'PENDING');
    if (canEdit && t.type !== 'ADJUSTMENT_UP' && t.type !== 'ADJUSTMENT_DOWN') {
        a.push(`<button class="btn btn-secondary btn-sm" data-act="edit" data-id="${t.id}">Sửa</button>`);
    }

    // Xoá giao dịch
    const canDelete = G.isOwner || (mine && t.status === 'PENDING');
    if (canDelete) {
        a.push(`<button class="btn btn-danger btn-sm" data-act="delete" data-id="${t.id}">Xoá</button>`);
    }

    return a.join(' ');
}

function updateBulkBar() {
    $('bulkBar').classList.toggle('hidden', selectedTxn.size === 0);
    $('bulkCount').textContent = selectedTxn.size;
}

async function txnAction(act, t) {
    if (!t) return;
    const done = (res, msg) => {
        if (toastResult(res, msg)) G.reload();
    };

    if (act === 'detail') return showDetail(t.id);

    // duyệt đơn lẻ
    if (act === 'confirm') {
        const res = await api(G.path('/transactions/status'), {
            method: 'POST',
            body: { txn_id: t.id, txnId: t.id, status: 'CONFIRMED' }
        });
        return done(res, 'Đã phê duyệt giao dịch vào quỹ');
    }

    // từ chối đơn lẻ
    if (act === 'reject') {
        const v = await formModal('Từ chối giao dịch', [
            { name: 'reason', label: 'Lý do từ chối (tuỳ chọn)', type: 'textarea' }
        ], 'Xác nhận từ chối');
        if (!v) return;

        const res = await api(G.path('/transactions/status'), {
            method: 'POST',
            body: { txn_id: t.id, txnId: t.id, status: 'REJECTED', reason: v.reason || null }
        });
        return done(res, 'Đã từ chối giao dịch');
    }

    if (act === 'delete') {
        if (await confirmBox('Bạn chắc chắn muốn xoá giao dịch này?')) {
            done(await api(G.path(`/transactions/${t.id}`), { method: 'DELETE' }), 'Đã xoá giao dịch');
        }
        return;
    }

    if (act === 'edit') return editTxn(t);
}

async function showDetail(id) {
    const [d, p] = await Promise.all([
        api(G.path(`/transactions/${id}`)),
        api(G.path(`/transactions/${id}/participants`))
    ]);
    const t = dataOf(d);
    if (!t) return;
    const parts = dataOf(p) || [];

    openModal('Chi tiết giao dịch', `
        <div class="grid grid-2">
            <div><span class="muted">Mã GD</span><br><code>${esc(t.id)}</code></div>
            <div><span class="muted">Số tiền</span><br><b style="font-size:20px;color:var(--primary)">${fmtMoney(t.amount)}</b></div>
            <div><span class="muted">Loại / Trạng thái</span><br>${badge(t.type)} ${badge(t.status)}</div>
            <div><span class="muted">Nguồn tiền / Danh mục</span><br>${badge(t.money_source, 'info')} ${esc(categoryName(t.category_code))}</div>
            <div><span class="muted">Người thực hiện</span><br><b>${esc(G.name(t.transactor_id))}</b></div>
            <div><span class="muted">Người tạo</span><br>${esc(G.name(t.created_by))} · ${fmtDateTime(t.created_at)}</div>
            <div><span class="muted">Người duyệt</span><br>${t.reviewed_by ? `${esc(G.name(t.reviewed_by))} · ${fmtDateTime(t.reviewed_at)}` : '<span class="muted">Chưa duyệt</span>'}</div>
            <div><span class="muted">Thời gian phát sinh</span><br>${fmtDateTime(t.occurred_at)}</div>
            <div style="grid-column:1/-1"><span class="muted">Ghi chú nội dung</span><br>${esc(t.note || '-')}</div>
        </div>
        <h4 style="margin:16px 0 8px">Thành viên cùng chia sẻ chi phí (${parts.length})</h4>
        ${parts.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Thành viên</th><th class="right">Phần phải chịu</th></tr></thead><tbody>
            ${parts.map(x => `<tr><td>${esc(G.name(x.user_id))}</td><td class="right"><b>${fmtMoney(x.share_amount)}</b></td></tr>`).join('')}
        </tbody></table></div>` : '<p class="muted">Không có danh sách chia nhỏ (CONTRIBUTION / REFUND không chia).</p>'}`);
}

async function editTxn(t) {
    await loadCategories();
    const expense = t.type === 'EXPENSE';
    const fields = [
        { name: 'amount', label: 'Số tiền (VNĐ)', type: 'number', value: t.amount, required: true },
        { name: 'when', label: 'Thời gian phát sinh', type: 'datetime-local', value: toLocalInput(t.occurred_at) },
        { name: 'transactor_id', label: t.type === 'REFUND' ? 'Người nhận hoàn tiền' : 'Người thực hiện', type: 'select', options: memberOptions(), value: t.transactor_id },
        { name: 'note', label: 'Ghi chú', value: t.note || '' }
    ];

    if (expense) {
        fields.splice(2, 0,
            { name: 'money_source', label: 'Nguồn tiền', type: 'select', options: [['FUND', 'Két quỹ (FUND)'], ['PERSONAL', 'Tiền túi cá nhân (PERSONAL)']], value: t.money_source },
            { name: 'category_code', label: 'Danh mục', type: 'select', options: categories.map(c => [c.code, categoryOption(c)]), value: t.category_code },
            { name: 'resplit', label: 'Chia đều lại cho tất cả thành viên ACTIVE', type: 'checkbox', value: true }
        );
    }

    const v = await formModal('Chỉnh sửa giao dịch', fields);
    if (!v) return;

    const amount = Number(v.amount);
    const body = {
        txn_id: t.id,
        txnId: t.id,
        amount,
        occurred_at: fromLocalInput(v.when) || t.occurred_at,
        transactor_id: v.transactor_id,
        note: v.note
    };

    if (expense) {
        body.money_source = v.money_source;
        body.category_code = v.category_code;
        if (v.resplit) body.participants = equalSplit(amount, G.activeMembers().map(m => m.user_id));
    }

    if (toastResult(await api(G.path(`/transactions/${t.id}`), { method: 'PUT', body }), 'Đã cập nhật giao dịch')) {
        G.reload();
    }
}

// duyệt hàng loạt
async function bulk(kind) {
    const ids = [...selectedTxn];
    if (!ids.length) return;

    const res = await api(G.path('/transactions/statuses'), {
        method: 'POST',
        body: {
            txn_ids: ids,
            txnIds: ids,
            status: kind === 'confirm' ? 'CONFIRMED' : 'REJECTED'
        }
    });

    if (toastResult(res, kind === 'confirm' ? 'Đã duyệt hàng loạt giao dịch' : 'Đã từ chối hàng loạt giao dịch')) {
        G.reload();
    }
}

$('btnBulkConfirm').onclick = () => bulk('confirm');
$('btnBulkReject').onclick = () => bulk('reject');

$('chkAllTxn').onchange = (e) => {
    document.querySelectorAll('.row-check').forEach(c => {
        c.checked = e.target.checked;
        e.target.checked ? selectedTxn.add(c.dataset.id) : selectedTxn.delete(c.dataset.id);
    });
    updateBulkBar();
};

['lMode', 'lType', 'lSource', 'lStatus'].forEach(id => $(id).onchange = () => loadTxnList(1));
$('btnReloadTxn').onclick = () => loadTxnList(txnPage);

G.tabs.transactions = async () => {
    await prepareTxnForm();
    loadTxnList(txnPage);
};
