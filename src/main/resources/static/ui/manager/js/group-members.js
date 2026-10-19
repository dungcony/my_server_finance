// Tab Quản lý thành viên trong nhóm
const UUID_PATTERN = /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/g;

async function loadMembersTab() {
    const status = $('mStatus').value;
    const list = dataOf(await api(G.path('/members'), { query: { status } })) || [];

    if (!status && list.length) G.members = list;
    $('mCount').textContent = `(${list.length})`;

    const isOwner = G.isOwner;
    const myId = G.myId;

    // Cập nhật thẻ trạng thái đóng góp của tôi
    updateMyMemberCard();

    setTable($('memberBody'), list.map(m => {
        const isMe = m.user_id === myId;
        const acts = [];

        // Chỉ Chủ nhóm mới có quyền duyệt / chuyển vai trò / kích thành viên
        if (isOwner) {
            if (m.status === 'PENDING') {
                acts.push(
                    btnAct('approve', m, '✓ Duyệt', 'btn-success'),
                    btnAct('reject', m, '✕ Từ chối', 'btn-danger')
                );
            }
            if (m.status === 'ACTIVE' && m.role !== 'OWNER') {
                acts.push(
                    btnAct('owner', m, '👑 Chuyển chủ nhóm', 'btn-secondary'),
                    btnAct('keeper', m, '💰 Đặt thủ quỹ', 'btn-warning'),
                    btnAct('remove', m, '🚪 Mời rời', 'btn-danger')
                );
            }
        }

        let roleBadge = '';
        if (m.role === 'OWNER') {
            roleBadge = '<span class="badge purple">👑 Chủ nhóm</span>';
            if (m.is_treasurer) roleBadge += ' <span class="badge warning">💰 Thủ quỹ</span>';
        } else if (m.is_treasurer) {
            roleBadge = '<span class="badge warning">💰 Thủ quỹ</span>';
        } else {
            roleBadge = '<span class="badge info">👤 Thành viên</span>';
        }

        return `<tr>
            <td>
                <b>${esc(m.display_name || '(Chưa đặt tên)')}</b>
                ${isMe ? '<span class="badge info">Tôi</span>' : ''}
            </td>
            <td><code>${esc(m.user_id)}</code> <button class="btn btn-secondary btn-sm" data-copy="${esc(m.user_id)}">Copy</button></td>
            <td>${roleBadge}</td>
            <td>${badge(m.status)}</td>
            <td class="nowrap">${fmtDateTime(m.joined_at)}</td>
            <td class="nowrap ${isOwner ? '' : 'hidden'}">${acts.length ? acts.join(' ') : '-'}</td>
        </tr>`;
    }).join(''), isOwner ? 6 : 5, 'Không tìm thấy thành viên phù hợp');

    $('memberBody').querySelectorAll('[data-copy]').forEach(b => b.onclick = () => copyText(b.dataset.copy));
    $('memberBody').querySelectorAll('[data-act]').forEach(b => b.onclick = () => memberAction(b.dataset.act, b.dataset.uid, b.dataset.name));
}

function btnAct(act, m, label, kind) {
    return `<button class="btn ${kind} btn-sm" data-act="${act}" data-uid="${esc(m.user_id)}" data-name="${esc(m.display_name || '')}">${label}</button>`;
}

async function updateMyMemberCard() {
    const rep = dataOf(await api(G.path('/balances'), { silent: true }));
    if (!rep || !rep.balances) {
        $('myContributed').textContent = '-';
        $('myOutOfPocket').textContent = '-';
        $('myNetBalance').textContent = '-';
        return;
    }

    const myId = G.myId;
    const myItem = rep.balances.find(b => b.user_id === myId);

    if (myItem) {
        $('myContributed').textContent = fmtMoney(myItem.total_share_amount);
        $('myOutOfPocket').textContent = fmtMoney(myItem.total_refunded);

        const net = Number(myItem.net_balance ?? 0);
        const needed = Number(myItem.needed_contribution ?? 0);
        if (net < 0 || needed > 0) {
            $('myNetBalance').innerHTML = `<span class="neg">Cần nộp thêm ${fmtMoney(needed || Math.abs(net))}</span>`;
            $('myNetSub').textContent = 'Bạn đang nộp thiếu so với chi phí phân bổ';
        } else if (net > 0) {
            $('myNetBalance').innerHTML = `<span class="pos">Được nhận lại ${fmtMoney(net)}</span>`;
            $('myNetSub').textContent = 'Quỹ nhóm đang giữ số dư của bạn';
        } else {
            $('myNetBalance').innerHTML = '<span class="pos">Đã cân bằng (0 đ)</span>';
            $('myNetSub').textContent = 'Công nợ của bạn đã được đối soát đủ';
        }
    }
}

async function memberAction(act, uid, name) {
    const who = name || shortId(uid);
    const done = (res, msg) => {
        if (toastResult(res, msg)) G.reload();
    };

    if (act === 'approve') {
        done(await api(G.path(`/members/${uid}/approve`), { method: 'POST' }), 'Đã phê duyệt thành viên vào nhóm');
    } else if (act === 'reject') {
        done(await api(G.path(`/members/${uid}/reject`), { method: 'POST' }), 'Đã từ chối yêu cầu gia nhập');
    } else if (act === 'remove') {
        if (await confirmBox(`Mời thành viên ${who} rời khỏi nhóm?`)) {
            done(await api(G.path(`/members/${uid}`), { method: 'DELETE' }), 'Đã mời thành viên rời nhóm');
        }
    } else if (act === 'owner') {
        if (await confirmBox(`Chuyển quyền Chủ nhóm cho ${who}? Bạn sẽ trở thành Thành viên thường (MEMBER).`)) {
            done(await api(G.path(`/owner-role/${uid}`), { method: 'PUT' }), 'Đã chuyển giao quyền Chủ nhóm');
        }
    } else if (act === 'keeper') {
        done(await api(G.path('/fund-kepper'), { method: 'PUT', body: { keepper_id: uid } }), `Đã bổ nhiệm ${who} làm Thủ quỹ mới`);
    }
}

$('mStatus').onchange = loadMembersTab;

$('btnApproveAll').onclick = async () => {
    if (toastResult(await api(G.path('/approves'), { method: 'POST' }), 'Đã duyệt tất cả thành viên PENDING')) {
        G.reload();
    }
};

$('btnRejectAll').onclick = async () => {
    if (!await confirmBox('Từ chối tất cả yêu cầu đang chờ? Dữ liệu PENDING sẽ bị loại bỏ.')) return;
    if (toastResult(await api(G.path('/rejects'), { method: 'POST' }), 'Đã từ chối tất cả yêu cầu PENDING')) {
        G.reload();
    }
};

$('btnAddMembers').onclick = async () => {
    const v = await formModal('Thêm thành viên vào nhóm', [
        { name: 'ids', label: 'User ID thành viên (mỗi dòng hoặc dấu phẩy một UUID)', type: 'textarea', required: true }
    ], 'Thêm vào nhóm');
    if (!v) return;

    const ids = [...new Set(v.ids.match(UUID_PATTERN) || [])];
    if (!ids.length) return toast('Không tìm thấy UUID hợp lệ', 'error');

    if (toastResult(await api(G.path('/members'), { method: 'POST', body: { member_ids: ids } }), 'Đã thêm thành viên vào nhóm')) {
        G.reload();
    }
};

G.tabs.members = loadMembersTab;
