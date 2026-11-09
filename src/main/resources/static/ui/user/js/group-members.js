// Tab Thành viên: danh sách, duyệt/từ chối, thêm, mời rời, chuyển chủ nhóm, đặt thủ quỹ (pipeline.md mục 10, 16).
const UUID_PATTERN = /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/g;

async function loadMembersTab() {
    const status = $('mStatus').value;
    const list = dataOf(await api(G.path('/members'), { query: { status } })) || [];
    // Danh sách không lọc là nguồn tên hiển thị cho toàn trang
    if (!status && list.length) G.members = list;
    $('mCount').textContent = `(${list.length})`;
    const owner = G.can('owner');

    setTable($('memberBody'), list.map(m => {
        const isMe = m.user_id === G.myId;
        const acts = [];
        if (owner && m.status === 'PENDING') {
            acts.push(btn('approve', m, 'Duyệt', 'btn-success'), btn('reject', m, 'Từ chối', 'btn-danger'));
        }
        if (owner && m.status === 'ACTIVE' && m.role !== 'OWNER') {
            acts.push(btn('owner', m, 'Chuyển chủ nhóm', 'btn-secondary'), btn('keeper', m, 'Đặt thủ quỹ', 'btn-warning'), btn('remove', m, 'Mời rời', 'btn-danger'));
        }
        return `<tr>
            <td><b>${esc(m.display_name || '(chưa đặt tên)')}</b> ${isMe ? badge('Tôi', 'info') : ''}</td>
            <td><code>${esc(m.user_id)}</code> <button class="btn btn-secondary btn-sm" data-copy="${esc(m.user_id)}">Copy</button></td>
            <td>${badge(m.role)} ${m.is_treasurer ? badge('Thủ quỹ', 'warning') : ''}</td>
            <td>${badge(m.status)}</td>
            <td class="nowrap">${fmtDateTime(m.joined_at)}</td>
            <td class="nowrap">${acts.join(' ')}</td></tr>`;
    }).join(''), 6, 'Không có thành viên phù hợp');

    $('memberBody').querySelectorAll('[data-copy]').forEach(b => b.onclick = () => copyText(b.dataset.copy));
    $('memberBody').querySelectorAll('[data-act]').forEach(b => b.onclick = () => memberAction(b.dataset.act, b.dataset.uid, b.dataset.name));
}

function btn(act, m, label, kind) {
    return `<button class="btn ${kind} btn-sm" data-act="${act}" data-uid="${esc(m.user_id)}" data-name="${esc(m.display_name || '')}">${label}</button>`;
}

async function memberAction(act, uid, name) {
    const who = name || shortId(uid);
    const done = (res, msg) => { if (toastResult(res, msg)) G.reload(); };
    if (act === 'approve') done(await api(G.path(`/members/${uid}/approve`), { method: 'POST' }), 'Đã duyệt thành viên');
    else if (act === 'reject') done(await api(G.path(`/members/${uid}/reject`), { method: 'POST' }), 'Đã từ chối thành viên');
    else if (act === 'remove') {
        if (await confirmBox(`Mời ${who} rời khỏi nhóm?`)) done(await api(G.path(`/members/${uid}`), { method: 'DELETE' }), 'Đã mời rời nhóm');
    } else if (act === 'owner') {
        if (await confirmBox(`Chuyển quyền chủ nhóm cho ${who}? Bạn sẽ trở thành MEMBER.`)) {
            done(await api(G.path(`/owner-role/${uid}/`), { method: 'PUT' }), 'Đã chuyển quyền chủ nhóm');
        }
    } else if (act === 'keeper') {
        done(await api(G.path('/fund-kepper'), { method: 'PUT', body: { keepper_id: uid } }), 'Đã đổi thủ quỹ');
    }
}

$('mStatus').onchange = loadMembersTab;

$('btnApproveAll').onclick = async () => {
    if (toastResult(await api(G.path('/approves'), { method: 'POST' }), 'Đã duyệt tất cả')) G.reload();
};

$('btnRejectAll').onclick = async () => {
    if (!await confirmBox('Từ chối toàn bộ người đang chờ? Dòng PENDING sẽ bị xoá.')) return;
    if (toastResult(await api(G.path('/rejects'), { method: 'POST' }), 'Đã từ chối tất cả')) G.reload();
};

$('btnAddMembers').onclick = async () => {
    const v = await formModal('Thêm thành viên', [
        { name: 'ids', label: 'User ID (mỗi dòng hoặc dấu phẩy một ID)', type: 'textarea', required: true }
    ], 'Thêm');
    if (!v) return;
    const ids = [...new Set(v.ids.match(UUID_PATTERN) || [])];
    if (!ids.length) return toast('Không tìm thấy UUID hợp lệ', 'error');
    if (toastResult(await api(G.path('/members'), { method: 'POST', body: { member_ids: ids } }), 'Đã thêm thành viên')) G.reload();
};

G.tabs.members = loadMembersTab;
