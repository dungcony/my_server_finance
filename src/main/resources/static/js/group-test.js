// Quản lý trạng thái và cấu hình gọi API
const STORAGE_KEY_TOKEN = 'finance_ai_access_token';
const STORAGE_KEY_USER = 'finance_ai_user_info';
const STORAGE_KEY_GROUP_ID = 'finance_ai_current_group_id';

// trạng thái vai trò trong nhóm đang chọn (reset khi đổi nhóm)
let currentGroupRole = null;
let currentGroupIsTreasurer = false;
let cachedCategories = null;
let currentGroupMembersList = [];

document.addEventListener('DOMContentLoaded', () => {
    // Khôi phục session và groupId đã lưu trong localStorage
    updateAuthUI();

    const savedGroupId = localStorage.getItem(STORAGE_KEY_GROUP_ID);
    if (savedGroupId) {
        document.getElementById('currentGroupId').value = savedGroupId;
        const groupLabel = document.getElementById('selectedGroupLabel');
        if (groupLabel) {
            groupLabel.innerText = ` (Đang chọn: ${savedGroupId})`;
        }
    }

    // Nếu đã có token, tự động nạp danh sách nhóm
    if (getToken()) {
        listMyGroups();
    }
});

// Lấy Access Token từ bộ nhớ hoặc ô nhập
function getToken() {
    return localStorage.getItem(STORAGE_KEY_TOKEN) || '';
}

// Cập nhật giao diện theo trạng thái đăng nhập
function updateAuthUI() {
    const token = getToken();
    const userJson = localStorage.getItem(STORAGE_KEY_USER);
    const loggedInBox = document.getElementById('loggedInBox');
    const loginFormBox = document.getElementById('loginFormBox');
    const authStatusText = document.getElementById('authStatusText');

    if (token) {
        loggedInBox.style.display = 'block';
        loginFormBox.style.display = 'none';
        authStatusText.innerText = 'Đã kết nối';
        authStatusText.style.color = 'var(--success)';

        let user = null;
        try {
            user = userJson ? JSON.parse(userJson) : null;
        } catch (e) {}

        const userName = user?.full_name || user?.name || user?.email || 'Người Dùng';
        const userEmail = user?.email || 'Đã xác thực qua Token';
        const userId = user?.id || '---';

        document.getElementById('userName').innerText = userName;
        document.getElementById('userEmail').innerText = userEmail;
        document.getElementById('userAvatar').innerText = userName.charAt(0).toUpperCase();
        document.getElementById('userIdShort').innerText = userId;
    } else {
        loggedInBox.style.display = 'none';
        loginFormBox.style.display = 'block';
        authStatusText.innerText = 'Chưa đăng nhập';
        authStatusText.style.color = 'var(--text-muted)';
    }
}

// Chuyển đổi tab phương thức đăng nhập
function switchAuthSubtab(subtab) {
    document.querySelectorAll('.auth-subtab-btn').forEach(btn => btn.classList.remove('active'));
    document.getElementById('authSubtabEmail').style.display = 'none';
    document.getElementById('authSubtabGoogle').style.display = 'none';
    document.getElementById('authSubtabRegister').style.display = 'none';
    document.getElementById('authSubtabManual').style.display = 'none';

    if (subtab === 'email') {
        document.getElementById('btnSubtabEmail').classList.add('active');
        document.getElementById('authSubtabEmail').style.display = 'block';
    } else if (subtab === 'google') {
        document.getElementById('btnSubtabGoogle').classList.add('active');
        document.getElementById('authSubtabGoogle').style.display = 'block';
    } else if (subtab === 'register') {
        document.getElementById('btnSubtabRegister').classList.add('active');
        document.getElementById('authSubtabRegister').style.display = 'block';
    } else if (subtab === 'manual') {
        document.getElementById('btnSubtabManual').classList.add('active');
        document.getElementById('authSubtabManual').style.display = 'block';
    }
}

// Xử lý sau khi nhận token thành công
function onAuthSuccess(authResult) {
    const token = authResult.token?.access || authResult.token?.accessToken || authResult.access_token || authResult.accessToken;
    if (!token) {
        console.warn('Không tìm thấy token trong phản hồi xác thực:', authResult);
        return;
    }

    localStorage.setItem(STORAGE_KEY_TOKEN, token);

    if (authResult.user) {
        localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(authResult.user));
    }

    updateAuthUI();
    listMyGroups();
}

// Đăng nhập bằng Email & Mật khẩu
async function loginWithEmail() {
    const email = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value.trim();

    if (!email || !password) {
        alert('Vui lòng nhập đầy đủ Email và Mật khẩu!');
        return;
    }

    const res = await callApi('/v1/auth/login', 'POST', { email, password });
    if (res.ok && res.data && res.data.data) {
        onAuthSuccess(res.data.data);
    }
}

// Gửi yêu cầu đăng ký tài khoản mới
async function registerAccount() {
    const email = document.getElementById('regEmail').value.trim();
    const password = document.getElementById('regPassword').value.trim();

    if (!email || !password) {
        alert('Vui lòng nhập đầy đủ Email và Mật khẩu!');
        return;
    }
    if (password.length < 8) {
        alert('Mật khẩu phải có tối thiểu 8 ký tự!');
        return;
    }

    const res = await callApi('/v1/auth/register', 'POST', { email, password });
    if (res.ok) {
        document.getElementById('verifyTargetEmail').innerText = email;
        document.getElementById('verifyOtpStep').style.display = 'block';
        alert('Đăng ký thành công! Vui lòng nhập mã OTP để kích hoạt tài khoản.');
    }
}

// Xác thực mã OTP và tự động đăng nhập
async function verifyEmailOtp() {
    const email = document.getElementById('regEmail').value.trim();
    const code = document.getElementById('regOtpCode').value.trim();

    if (!code) {
        alert('Vui lòng nhập mã xác thực OTP!');
        return;
    }

    const res = await callApi('/v1/auth/verify-email', 'POST', { email, code });
    if (res.ok && res.data && res.data.data) {
        alert('Kích hoạt tài khoản thành công! Tự động kết nối phiên làm việc.');
        onAuthSuccess(res.data.data);
    }
}

// Gửi lại mã xác thực OTP
async function resendVerificationOtp() {
    const email = document.getElementById('regEmail').value.trim();
    if (!email) {
        alert('Vui lòng nhập email đăng ký trước khi gửi lại OTP!');
        return;
    }

    const res = await callApi('/v1/auth/resend-verification', 'POST', { email });
    if (res.ok) {
        alert('Đã gửi lại mã xác thực OTP mới.');
    }
}

// Xử lý callback khi đăng nhập Google thành công
async function handleGoogleLogin(response) {
    const idToken = response.credential;
    if (!idToken) return;

    const res = await callApi('/v1/auth/google', 'POST', { id_token: idToken });
    if (res.ok && res.data && res.data.data) {
        onAuthSuccess(res.data.data);
    }
}

// Áp dụng token dán thủ công
function applyManualToken() {
    const token = document.getElementById('manualBearerToken').value.trim();
    if (!token) {
        alert('Vui lòng nhập hoặc dán Access Token!');
        return;
    }

    localStorage.setItem(STORAGE_KEY_TOKEN, token);
    localStorage.removeItem(STORAGE_KEY_USER);
    updateAuthUI();
    listMyGroups();
}

// Đăng xuất
function logout() {
    localStorage.removeItem(STORAGE_KEY_TOKEN);
    localStorage.removeItem(STORAGE_KEY_USER);
    updateAuthUI();
    const tbody = document.getElementById('groupTableBody');
    if (tbody) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">Đã đăng xuất. Vui lòng đăng nhập để xem nhóm.</td></tr>';
    }
}

// Gửi request sinh mật khẩu cho tài khoản Google thuần
async function generatePasswordForGoogle() {
    const res = await callApi('/v1/users/me/password', 'POST');
    if (res.ok) {
        alert(res.data?.msg || 'Mật khẩu đã được tạo và gửi về email của bạn.');
    }
}

// Chuyển đổi tab chức năng
function switchTab(tabId) {
    document.querySelectorAll('.nav-tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

    const tabBtn = document.querySelector(`[data-tab="${tabId}"]`);
    const tabContent = document.getElementById(tabId);

    if (tabBtn && tabContent) {
        tabBtn.classList.add('active');
        tabContent.classList.add('active');
    }

    const currentGroupId = getCurrentGroupIdSilent();
    if (!currentGroupId) return;

    // nạp dữ liệu theo từng tab khi đã chọn nhóm
    if (tabId === 'tab-members') {
        const filterStatus = document.getElementById('memberStatusFilter')?.value || '';
        filterMembersByStatus(filterStatus);
    } else if (tabId === 'tab-transactions') {
        const isReviewer = currentGroupRole === 'OWNER' || currentGroupIsTreasurer;
        if (isReviewer) loadPendingTransactions();
        loadMyTransactions();
        loadAllTransactions();
        loadExpenseCategories();
        if (!currentGroupMembersList || currentGroupMembersList.length === 0) {
            filterMembersByStatus('');
        }
    } else if (tabId === 'tab-fund') {
        detailGroup();
    } else if (tabId === 'tab-report') {
        const isReviewer = currentGroupRole === 'OWNER' || currentGroupIsTreasurer;
        if (isReviewer) {
            getGroupSummaryReport();
            getGroupBalancesReport();
        }
    }
}

// Lưu Group ID đang chọn thuần túy không gọi API
function setCurrentGroupId(groupId, role, groupName) {
    document.getElementById('currentGroupId').value = groupId;
    localStorage.setItem(STORAGE_KEY_GROUP_ID, groupId);

    // cập nhật vai trò nếu có sẵn từ danh sách nhóm
    if (role) {
        currentGroupRole = role;
    }
    currentGroupIsTreasurer = false;
    currentGroupMembersList = [];
    applyRoleVisibility();

    // cập nhật nhãn hiển thị nhóm đang chọn
    const groupLabel = document.getElementById('selectedGroupLabel');
    if (groupLabel) {
        groupLabel.innerText = groupName ? ` (Đang chọn: ${groupName})` : ` (Đang chọn: ${groupId})`;
    }

    // đánh dấu highlight dòng nhóm đang chọn trên bảng
    document.querySelectorAll('#groupTableBody tr').forEach(tr => tr.classList.remove('selected-group-row'));
    const targetTr = document.getElementById(`group-row-${groupId}`);
    if (targetTr) {
        targetTr.classList.add('selected-group-row');
    }
}

// Lấy thông tin Group ID hiện tại
function getCurrentGroupId() {
    const groupId = document.getElementById('currentGroupId').value.trim();
    if (!groupId) {
        alert('Vui lòng chọn hoặc nhập Group ID trước khi thực hiện thao tác này!');
        return null;
    }
    return groupId;
}

// Lấy Group ID mà không hiện alert (dùng cho kiểm tra điều kiện)
function getCurrentGroupIdSilent() {
    return document.getElementById('currentGroupId').value.trim() || null;
}

// Hàm gửi request API dùng chung
async function callApi(endpoint, method = 'GET', body = null) {
    const token = getToken();
    const statusBadge = document.getElementById('statusBadge');
    const responseView = document.getElementById('responseView');

    statusBadge.className = 'status-badge status-idle';
    statusBadge.innerText = 'Đang gọi ' + method + ' ' + endpoint + '...';
    responseView.innerText = 'Loading...';

    const headers = {
        'Accept': 'application/json'
    };

    if (token) {
        headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    }

    if (body && (method === 'POST' || method === 'PUT' || method === 'PATCH')) {
        headers['Content-Type'] = 'application/json';
    }

    const startTime = performance.now();

    try {
        const options = {
            method,
            headers
        };

        if (body) {
            options.body = JSON.stringify(body);
        }

        const res = await fetch(endpoint, options);
        const duration = Math.round(performance.now() - startTime);

        let data = null;
        const text = await res.text();
        try {
            data = JSON.parse(text);
        } catch (e) {
            data = text;
        }

        if (res.ok) {
            statusBadge.className = 'status-badge status-success';
            statusBadge.innerText = `HTTP ${res.status} OK (${duration}ms)`;
        } else {
            statusBadge.className = 'status-badge status-error';
            statusBadge.innerText = `HTTP ${res.status} Error (${duration}ms)`;
            // tự động đăng xuất khi phiên đăng nhập hết hạn
            if (res.status === 401) {
                logout();
                alert('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại!');
            }
        }

        responseView.innerText = typeof data === 'object' ? JSON.stringify(data, null, 2) : data;
        return { ok: res.ok, status: res.status, data };
    } catch (err) {
        statusBadge.className = 'status-badge status-error';
        statusBadge.innerText = 'Lỗi kết nối tới Server!';
        responseView.innerText = err.toString();
        return { ok: false, error: err };
    }
}

// Sao chép response JSON vào clipboard
function copyResponse() {
    const text = document.getElementById('responseView').innerText;
    navigator.clipboard.writeText(text).then(() => {
        alert('Đã sao chép phản hồi vào bộ nhớ tạm.');
    });
}

// ----------------- GROUP APIS -----------------

// Lấy danh sách nhóm
async function listMyGroups() {
    const res = await callApi('/v1/groups', 'GET');
    if (res.ok && res.data && res.data.data) {
        const tbody = document.getElementById('groupTableBody');
        tbody.innerHTML = '';

        const groups = res.data.data;
        const currentSelectedId = getCurrentGroupIdSilent();

        if (Array.isArray(groups) && groups.length > 0) {
            groups.forEach(g => {
                const tr = document.createElement('tr');
                tr.id = `group-row-${g.id}`;
                if (currentSelectedId === g.id) {
                    tr.classList.add('selected-group-row');
                }

                const role = g.my_role || g.myRole || '';
                const name = (g.name || '').replace(/'/g, "\\'");
                const inviteCode = g.invite_code || g.inviteCode || '';
                const memberCount = g.member_count ?? g.memberCount ?? 0;

                tr.innerHTML = `
                    <td><code>${g.id}</code></td>
                    <td><strong>${g.name || ''}</strong></td>
                    <td>${role}</td>
                    <td>${g.status || ''}</td>
                    <td><code>${inviteCode}</code></td>
                    <td>${memberCount}</td>
                    <td>
                        <button class="btn btn-secondary" style="padding: 4px 8px; font-size: 12px;" onclick="setCurrentGroupId('${g.id}', '${role}', '${name}')">
                            Chọn
                        </button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        } else {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">Không có nhóm nào</td></tr>';
        }
    }
}

// Tạo nhóm mới
async function createGroup() {
    const name = document.getElementById('createGroupName').value.trim();
    const desc = document.getElementById('createGroupDesc').value.trim();
    const targetStr = document.getElementById('createGroupTarget').value.trim();
    const isSettlement = document.getElementById('createGroupSettlement').checked;
    const isJoinWithoutConfirm = document.getElementById('createGroupJoinWithoutConfirm')?.checked ?? true;
    const membersRaw = document.getElementById('createGroupMembers')?.value.trim();
    const members = membersRaw ? membersRaw.split(',').map(s => s.trim()).filter(Boolean) : [];

    if (!name) {
        alert('Vui lòng nhập tên nhóm!');
        return;
    }

    const payload = {
        name,
        description: desc || null,
        target: targetStr ? parseInt(targetStr, 10) : null,
        is_settlement_enabled: isSettlement,
        is_join_without_confirm: isJoinWithoutConfirm,
        members: members
    };

    const res = await callApi('/v1/groups', 'POST', payload);
    if (res.ok && res.data && res.data.data && res.data.data.id) {
        setCurrentGroupId(res.data.data.id, 'OWNER', res.data.data.name);
        listMyGroups();
    }
}

// Xem chi tiết nhóm
async function detailGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    const res = await callApi(`/v1/groups/${groupId}`, 'GET');
    if (res.ok && res.data && res.data.data) {
        renderGroupDetail(res.data.data);
    }
}

// Hiển thị chi tiết nhóm lên giao diện
function renderGroupDetail(group) {
    if (!group) return;

    // Hiển thị thẻ chi tiết nhóm ở tab Quản lý nhóm
    const detailCard = document.getElementById('groupDetailCard');
    if (detailCard) detailCard.style.display = 'block';

    const nameEl = document.getElementById('detailGroupName');
    if (nameEl) nameEl.innerText = group.name || 'Không có tên';

    const roleBadge = document.getElementById('detailGroupRoleBadge');
    if (roleBadge) {
        const role = group.my_role || group.myRole || 'MEMBER';
        roleBadge.innerText = role;
        roleBadge.className = 'status-badge ' + (role === 'OWNER' ? 'status-warning' : 'status-idle');
    }

    const statusBadge = document.getElementById('detailGroupStatusBadge');
    if (statusBadge) {
        const status = group.status || 'ACTIVE';
        statusBadge.innerText = status;
        statusBadge.className = 'status-badge ' + (status === 'ACTIVE' ? 'status-success' : 'status-error');
    }

    const inviteCodeEl = document.getElementById('detailGroupInviteCode');
    if (inviteCodeEl) inviteCodeEl.innerText = group.invite_code || group.inviteCode || '-';

    const balanceEl = document.getElementById('detailGroupBalance');
    if (balanceEl) {
        const bal = group.fund?.current_balance ?? group.fund?.currentBalance ?? 0;
        balanceEl.innerText = Number(bal).toLocaleString('vi-VN') + ' đ';
    }

    const targetEl = document.getElementById('detailGroupTarget');
    if (targetEl) {
        targetEl.innerText = group.target ? Number(group.target).toLocaleString('vi-VN') + ' đ' : 'Không đặt mục tiêu';
    }

    const settingsEl = document.getElementById('detailGroupSettings');
    if (settingsEl) {
        const settlement = (group.is_settlement_enabled ?? group.isSettlementEnabled) ? 'Quyết toán: Bật' : 'Quyết toán: Tắt';
        const joinDirect = (group.is_join_without_confirm ?? group.isJoinWithoutConfirm) ? 'Vào thẳng: Bật' : 'Vào thẳng: Tắt';
        settingsEl.innerText = `${settlement} | ${joinDirect}`;
    }

    const descEl = document.getElementById('detailGroupDesc');
    if (descEl) descEl.innerText = group.description || 'Không có mô tả';

    // tính toán vai trò để phân quyền hiển thị
    const myRole = group.my_role || group.myRole || 'MEMBER';
    currentGroupRole = myRole;
    currentGroupIsTreasurer = false;
    const userJson = localStorage.getItem(STORAGE_KEY_USER);
    if (userJson) {
        try {
            const user = JSON.parse(userJson);
            const myId = user?.id;
            const members = group.members || [];
            const myMember = members.find(m => (m.user_id || m.userId) === myId);
            if (myMember) {
                currentGroupIsTreasurer = myMember.is_treasurer ?? myMember.isTreasurer ?? false;
            }
        } catch (e) {}
    }
    applyRoleVisibility();

    // render thông tin quỹ nhóm ở tab Quỹ
    renderFundInfo(group);

    // điền User ID hiện tại vào ô Người thực hiện của form tạo giao dịch
    const txnTransactorInput = document.getElementById('txnTransactorId');
    if (txnTransactorInput && !txnTransactorInput.value) {
        try {
            const u = JSON.parse(localStorage.getItem(STORAGE_KEY_USER));
            if (u?.id) txnTransactorInput.value = u.id;
        } catch (e) {}
    }

    // Điền trước thông tin vào form Cập nhật nhóm
    const updateName = document.getElementById('updateGroupName');
    if (updateName) updateName.value = group.name || '';
    const updateDesc = document.getElementById('updateGroupDesc');
    if (updateDesc) updateDesc.value = group.description || '';
    const updateTarget = document.getElementById('updateGroupTarget');
    if (updateTarget) updateTarget.value = group.target ?? '';
    const updateSettlement = document.getElementById('updateGroupSettlement');
    if (updateSettlement) updateSettlement.checked = group.is_settlement_enabled ?? group.isSettlementEnabled ?? true;

    // Hiển thị danh sách thành viên vào bảng ở tab Thành viên
    renderMemberList(group.members || []);
}

// Hiển thị danh sách thành viên lên bảng
function renderMemberList(members) {
    const countBadge = document.getElementById('memberCountBadge');
    if (countBadge) countBadge.innerText = `${members.length} thành viên`;

    // đồng bộ vai trò người dùng hiện tại từ danh sách thành viên
    const userJson = localStorage.getItem(STORAGE_KEY_USER);
    if (userJson) {
        try {
            const user = JSON.parse(userJson);
            const myId = user?.id;
            const myMember = members.find(m => (m.user_id || m.userId) === myId);
            if (myMember) {
                currentGroupRole = myMember.role || 'MEMBER';
                currentGroupIsTreasurer = myMember.is_treasurer ?? myMember.isTreasurer ?? false;
                applyRoleVisibility();
            }
        } catch (e) {}
    }

    // nạp danh sách thành viên vào các dropdown và bảng chia tiền ở tab giao dịch
    populateTransactionMemberSelects(members);

    const memberTbody = document.getElementById('memberTableBody');
    if (!memberTbody) return;

    memberTbody.innerHTML = '';
    if (members.length === 0) {
        memberTbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Không có thành viên nào phù hợp</td></tr>';
        return;
    }

    members.forEach(m => {
        const userId = m.user_id || m.userId || '';
        const role = m.role || 'MEMBER';
        const status = m.status || 'ACTIVE';
        const rawDate = m.joined_at || m.joinedAt;
        const joinedText = rawDate ? new Date(rawDate).toLocaleString('vi-VN') : '-';
        const displayName = m.display_name || m.displayName || '';
        const isTreasurer = m.is_treasurer ?? m.isTreasurer ?? false;

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>
                <div style="font-weight: 600; color: #0f172a;">${displayName || '<i>(Chưa đặt tên)</i>'}</div>
            </td>
            <td>
                <code style="font-weight: 600;">${userId}</code>
                <button class="btn btn-secondary" style="padding: 2px 6px; font-size: 11px; margin-left: 4px;" onclick="copyText('${userId}')">Copy</button>
            </td>
            <td>
                <span class="status-badge ${role === 'OWNER' ? 'status-warning' : 'status-idle'}">${role}</span>
                ${isTreasurer ? '<span class="status-badge status-warning" style="margin-left: 4px;">💰 Thủ Quỹ</span>' : ''}
            </td>
            <td>
                <span class="status-badge ${status === 'ACTIVE' ? 'status-success' : 'status-error'}">${status}</span>
            </td>
            <td>${joinedText}</td>
            <td style="display: flex; gap: 6px; flex-wrap: wrap;">
                <button class="btn btn-secondary" style="padding: 3px 8px; font-size: 11px;" onclick="fillMemberUserId('${userId}')">
                    Điền ID
                </button>
                ${status === 'PENDING' ? `
                    <button class="btn btn-success" style="padding: 3px 8px; font-size: 11px;" onclick="quickApproveMember('${userId}')">
                        Duyệt
                    </button>
                    <button class="btn btn-danger" style="padding: 3px 8px; font-size: 11px;" onclick="quickRejectMember('${userId}')">
                        Từ Chối
                    </button>
                ` : ''}
                ${status === 'ACTIVE' && role !== 'OWNER' ? `
                    <button class="btn btn-danger" style="padding: 3px 8px; font-size: 11px;" onclick="quickRemoveMember('${userId}')">
                        Kick
                    </button>
                ` : ''}
                ${status === 'ACTIVE' && !isTreasurer ? `
                    <button class="btn btn-warning" style="padding: 3px 8px; font-size: 11px;" onclick="selectMemberAsTreasurer('${userId}')">
                        Đặt Thủ Quỹ
                    </button>
                ` : ''}
            </td>
        `;
        memberTbody.appendChild(tr);
    });
}

// Lọc danh sách thành viên theo trạng thái
async function filterMembersByStatus(status) {
    const groupId = getCurrentGroupIdSilent();
    if (!groupId) return;

    const query = status ? `?status=${status}` : '';
    const res = await callApi(`/v1/groups/${groupId}/members${query}`, 'GET');
    if (res.ok && res.data && res.data.data) {
        renderMemberList(res.data.data);
    }
}

// Đếm số việc đang chờ duyệt để hiện badge
async function loadPendingCount(groupId) {
    if (!groupId) {
        groupId = getCurrentGroupId();
        if (!groupId) return;
    }
    const res = await callApi(`/v1/groups/${groupId}/pending-count`);
    if (res.ok && res.data && res.data.data) {
        const d = res.data.data;
        const pendingMembers = d.pending_members ?? 0;
        const pendingTxns = d.pending_transactions ?? 0;

        const mBadge = document.getElementById('pendingMembersBadge');
        if (mBadge) {
            mBadge.innerText = `👤 ${pendingMembers} thành viên chờ duyệt`;
            mBadge.className = pendingMembers > 0 ? 'status-badge status-warning' : 'status-badge status-idle';
        }

        const tBadge = document.getElementById('pendingTransactionsBadge');
        if (tBadge) {
            tBadge.innerText = `⏳ ${pendingTxns} giao dịch chờ duyệt`;
            tBadge.className = pendingTxns > 0 ? 'status-badge status-warning' : 'status-badge status-idle';
        }

        const cBadge = document.getElementById('pendingTxnCountBadge');
        if (cBadge) {
            cBadge.innerText = `${pendingTxns} giao dịch`;
            cBadge.className = pendingTxns > 0 ? 'status-badge status-warning' : 'status-badge status-idle';
        }
    }
}

// Lấy danh sách giao dịch đang chờ duyệt
async function loadPendingTransactions() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const res = await callApi(`/v1/groups/${groupId}/transactions/pending?page=1&size=50`, 'GET');
    if (res.ok && res.data && res.data.data) {
        const listData = res.data.data.items || res.data.data || [];
        renderPendingTransactions(listData);
    }
}

// Hiển thị danh sách giao dịch chờ duyệt lên bảng
function renderPendingTransactions(items) {
    const tbody = document.getElementById('pendingTxnTableBody');
    const badge = document.getElementById('pendingTxnCountBadge');
    if (badge) {
        badge.innerText = `${items.length} giao dịch`;
        badge.className = items.length > 0 ? 'status-badge status-warning' : 'status-badge status-idle';
    }
    if (!tbody) return;

    tbody.innerHTML = '';
    if (!Array.isArray(items) || items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">Không có giao dịch nào đang chờ duyệt</td></tr>';
        return;
    }

    items.forEach(tx => {
        const txnId = tx.id || '';
        const transactor = tx.transactor_name || tx.transactor_id || '-';
        const amount = Number(tx.amount || 0).toLocaleString('vi-VN') + ' đ';
        const moneySource = tx.money_source || '-';
        const desc = tx.description || '-';
        const createdAt = tx.created_at ? new Date(tx.created_at).toLocaleString('vi-VN') : '-';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><code>${txnId}</code></td>
            <td><strong>${transactor}</strong></td>
            <td style="font-weight: 600; color: var(--primary);">${amount}</td>
            <td><span class="status-badge status-idle">${moneySource}</span></td>
            <td>${desc}</td>
            <td>${createdAt}</td>
            <td style="display: flex; gap: 4px; flex-wrap: wrap;">
                <button class="btn btn-secondary" style="padding: 3px 6px; font-size: 11px;" onclick="showTransactionDetail('${txnId}')">
                    🔍 Chi tiết
                </button>
                <button class="btn btn-success" style="padding: 3px 8px; font-size: 11px;" onclick="confirmPendingTxn('${txnId}')">
                    Duyệt
                </button>
                <button class="btn btn-danger" style="padding: 3px 8px; font-size: 11px;" onclick="rejectPendingTxn('${txnId}')">
                    Từ Chối
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// Duyệt từng giao dịch chờ duyệt
async function confirmPendingTxn(txnId) {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const res = await callApi(`/v1/groups/${groupId}/transactions/${txnId}/confirm`, 'POST');
    if (res.ok) {
        loadPendingTransactions();
        loadPendingCount(groupId);
        detailGroup();
    }
}

// Từ chối từng giao dịch chờ duyệt
async function rejectPendingTxn(txnId) {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const res = await callApi(`/v1/groups/${groupId}/transactions/${txnId}/reject`, 'POST');
    if (res.ok) {
        loadPendingTransactions();
        loadPendingCount(groupId);
        detailGroup();
    }
}

// Duyệt tất cả giao dịch chờ duyệt
async function bulkConfirmPendingTxns() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const res = await callApi(`/v1/groups/${groupId}/transactions/pending?page=1&size=50`, 'GET');
    if (!res.ok || !res.data || !res.data.data) return;

    const items = res.data.data.items || res.data.data || [];
    const txnIds = items.map(tx => tx.id).filter(Boolean);
    if (txnIds.length === 0) {
        alert('Không có giao dịch nào đang chờ duyệt!');
        return;
    }

    const confirmRes = await callApi(`/v1/groups/${groupId}/transactions/bulk-confirm`, 'POST', { transaction_ids: txnIds });
    if (confirmRes.ok) {
        loadPendingTransactions();
        loadPendingCount(groupId);
        detailGroup();
    }
}

// Từ chối tất cả giao dịch chờ duyệt
async function bulkRejectPendingTxns() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const res = await callApi(`/v1/groups/${groupId}/transactions/pending?page=1&size=50`, 'GET');
    if (!res.ok || !res.data || !res.data.data) return;

    const items = res.data.data.items || res.data.data || [];
    const txnIds = items.map(tx => tx.id).filter(Boolean);
    if (txnIds.length === 0) {
        alert('Không có giao dịch nào đang chờ duyệt!');
        return;
    }

    const rejectRes = await callApi(`/v1/groups/${groupId}/transactions/bulk-reject`, 'POST', { transaction_ids: txnIds });
    if (rejectRes.ok) {
        loadPendingTransactions();
        loadPendingCount(groupId);
        detailGroup();
    }
}

// Chọn thành viên làm thủ quỹ
function selectMemberAsTreasurer(userId) {
    const input = document.getElementById('newKeepperUserId');
    if (input) input.value = userId;
    switchTab('tab-fund');
    alert(`Đã điền User ID: ${userId} vào ô Thủ Quỹ. Bấm "Cập Nhật Thủ Quỹ" để xác nhận.`);
}

// Sao chép mã mời
function copyInviteCode() {
    const code = document.getElementById('detailGroupInviteCode')?.innerText;
    if (code && code !== '-') {
        copyText(code);
    }
}

// Sao chép chuỗi bất kỳ
function copyText(text) {
    if (text) {
        navigator.clipboard.writeText(text).then(() => {
            alert('Đã sao chép: ' + text);
        });
    }
}

// Điền User ID thành viên vào các ô thao tác
function fillMemberUserId(userId) {
    const targetOwner = document.getElementById('targetOwnerUserId');
    const approveInput = document.getElementById('approveMemberUserId');
    const rejectInput = document.getElementById('rejectMemberUserId');
    const kickInput = document.getElementById('removeMemberUserId');
    const transactorSelect = document.getElementById('txnTransactorId');

    if (targetOwner) targetOwner.value = userId;
    if (approveInput) approveInput.value = userId;
    if (rejectInput) rejectInput.value = userId;
    if (kickInput) kickInput.value = userId;
    if (transactorSelect) transactorSelect.value = userId;

    alert('Đã điền User ID: ' + userId + ' vào các ô thao tác thành viên.');
}

// Duyệt nhanh thành viên từ bảng
async function quickApproveMember(memberUserId) {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    const res = await callApi(`/v1/groups/${groupId}/members/${memberUserId}/approve`, 'POST');
    if (res.ok) {
        detailGroup();
        loadPendingCount(groupId);
    }
}

// Từ chối nhanh thành viên từ bảng
async function quickRejectMember(memberUserId) {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    const res = await callApi(`/v1/groups/${groupId}/members/${memberUserId}/reject`, 'POST');
    if (res.ok) {
        detailGroup();
        loadPendingCount(groupId);
    }
}

// Mời nhanh thành viên ra khỏi nhóm từ bảng
async function quickRemoveMember(memberUserId) {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    if (!confirm(`Bạn có chắc chắn muốn kick thành viên ${memberUserId} khỏi nhóm không?`)) return;
    const res = await callApi(`/v1/groups/${groupId}/members/${memberUserId}`, 'DELETE');
    if (res.ok) {
        detailGroup();
    }
}

// Cập nhật thông tin nhóm
async function updateGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const name = document.getElementById('updateGroupName').value.trim();
    const desc = document.getElementById('updateGroupDesc').value.trim();
    const targetStr = document.getElementById('updateGroupTarget').value.trim();
    const isSettlement = document.getElementById('updateGroupSettlement').checked;

    const payload = {
        name: name || undefined,
        description: desc || undefined,
        target: targetStr ? parseInt(targetStr, 10) : undefined,
        is_settlement_enabled: isSettlement
    };

    const res = await callApi(`/v1/groups/${groupId}`, 'PATCH', payload);
    if (res.ok) {
        detailGroup();
        listMyGroups();
    }
}

// Xóa nhóm
async function deleteGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    if (!confirm('Bạn có chắc chắn muốn xóa nhóm này không?')) return;
    const res = await callApi(`/v1/groups/${groupId}`, 'DELETE');
    if (res.ok) {
        listMyGroups();
    }
}

// Tham gia nhóm bằng mã mời
async function joinGroupByCode() {
    const inviteCode = document.getElementById('joinInviteCode').value.trim();
    if (!inviteCode) {
        alert('Vui lòng nhập mã mời (ví dụ: GRP12345)!');
        return;
    }

    const res = await callApi('/v1/groups/join', 'POST', { invite_code: inviteCode });
    if (res.ok) {
        listMyGroups();
    }
}

// Lưu trữ nhóm
async function archiveGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    const res = await callApi(`/v1/groups/${groupId}/archive`, 'POST');
    if (res.ok) {
        listMyGroups();
    }
}

// Hủy lưu trữ nhóm
async function unarchiveGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    const res = await callApi(`/v1/groups/${groupId}/unarchive`, 'POST');
    if (res.ok) {
        listMyGroups();
    }
}

// ----------------- MEMBER APIS -----------------

// Thêm danh sách thành viên vào nhóm
async function addMembers() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const idsInput = document.getElementById('addMemberUserIds').value.trim();
    if (!idsInput) {
        alert('Vui lòng nhập ít nhất một User ID!');
        return;
    }

    const memberIds = idsInput.split(/[\n,]+/).map(s => s.trim()).filter(Boolean);

    const payload = {
        member_ids: memberIds
    };

    const res = await callApi(`/v1/groups/${groupId}/members`, 'POST', payload);
    if (res.ok) {
        detailGroup();
    }
}

// Đóng mở khung thêm thành viên nhanh
function toggleAddMemberForm() {
    const card = document.getElementById('addMemberQuickCard');
    if (!card) return;
    if (card.style.display === 'none' || !card.style.display) {
        card.style.display = 'block';
        const input = document.getElementById('quickAddMemberUserIds');
        if (input) input.focus();
    } else {
        card.style.display = 'none';
    }
}

// Xác nhận thêm thành viên từ form nhanh
async function addMembersQuick() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const input = document.getElementById('quickAddMemberUserIds');
    const idsText = input ? input.value.trim() : '';
    if (!idsText) {
        alert('Vui lòng nhập ít nhất một User ID (UUID) thành viên cần thêm!');
        return;
    }

    const memberIds = idsText.split(/[\n,]+/).map(s => s.trim()).filter(Boolean);
    if (memberIds.length === 0) {
        alert('Danh sách User ID không hợp lệ!');
        return;
    }

    const res = await callApi(`/v1/groups/${groupId}/members`, 'POST', { member_ids: memberIds });
    if (res.ok) {
        alert('Đã thêm thành viên thành công!');
        if (input) input.value = '';
        toggleAddMemberForm();
        filterMembersByStatus(document.getElementById('memberStatusFilter')?.value || '');
        detailGroup();
    }
}

// Chuyển quyền chủ nhóm (Owner)
async function transferOwnership() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const targetUserId = document.getElementById('targetOwnerUserId').value.trim();
    if (!targetUserId) {
        alert('Vui lòng nhập User ID của thành viên muốn chuyển quyền chủ nhóm!');
        return;
    }

    // Mapping PUT có dấu / ở cuối theo controller
    const res = await callApi(`/v1/groups/${groupId}/owner-role/${targetUserId}/`, 'PUT');
    if (res.ok) {
        detailGroup();
        listMyGroups();
    }
}

// Duyệt từng thành viên
async function approveMember() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const memberUserId = document.getElementById('approveMemberUserId').value.trim();
    if (!memberUserId) {
        alert('Vui lòng nhập User ID thành viên cần duyệt!');
        return;
    }

    const res = await callApi(`/v1/groups/${groupId}/members/${memberUserId}/approve`, 'POST');
    if (res.ok) {
        detailGroup();
    }
}

// Duyệt toàn bộ thành viên đang chờ
async function approveAllMembers() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    const res = await callApi(`/v1/groups/${groupId}/approves`, 'POST');
    if (res.ok) {
        detailGroup();
    }
}

// Từ chối từng thành viên
async function rejectMember() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const memberUserId = document.getElementById('rejectMemberUserId').value.trim();
    if (!memberUserId) {
        alert('Vui lòng nhập User ID thành viên muốn từ chối!');
        return;
    }

    const res = await callApi(`/v1/groups/${groupId}/members/${memberUserId}/reject`, 'POST');
    if (res.ok) {
        detailGroup();
    }
}

// Từ chối tất cả thành viên đang chờ
async function rejectAllMembers() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    const res = await callApi(`/v1/groups/${groupId}/rejects`, 'POST');
    if (res.ok) {
        detailGroup();
    }
}

// Xóa thành viên khỏi nhóm
async function removeMember() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const memberUserId = document.getElementById('removeMemberUserId').value.trim();
    if (!memberUserId) {
        alert('Vui lòng nhập User ID thành viên muốn xóa!');
        return;
    }

    if (!confirm(`Bạn có chắc muốn xóa thành viên ${memberUserId} khỏi nhóm?`)) return;

    const res = await callApi(`/v1/groups/${groupId}/members/${memberUserId}`, 'DELETE');
    if (res.ok) {
        detailGroup();
    }
}

// Tự rời khỏi nhóm
async function leaveGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    if (!confirm('Bạn có chắc chắn muốn rời khỏi nhóm này không?')) return;

    const res = await callApi(`/v1/groups/${groupId}/leave`, 'POST');
    if (res.ok) {
        listMyGroups();
    }
}

// ----------------- FUND INFO -----------------

function renderFundInfo(group) {
    const container = document.getElementById('fundInfoContent');
    if (!container) return;

    const fund = group.fund;
    if (!fund) {
        container.innerHTML = '<p style="color: var(--text-muted);">Nhóm chưa có quỹ.</p>';
        return;
    }

    const balance = Number(fund.current_balance ?? fund.currentBalance ?? 0).toLocaleString('vi-VN') + ' đ';
    const keepperId = fund.kepper_id ?? fund.keepperId;
    const createdAt = fund.created_at ?? fund.createdAt;
    const createdAtStr = createdAt ? new Date(createdAt).toLocaleDateString('vi-VN') : '-';

    // tìm tên thủ quỹ từ danh sách thành viên
    let treasurerName = '-';
    if (keepperId && group.members) {
        const treasurer = group.members.find(m => (m.user_id || m.userId) === keepperId);
        treasurerName = treasurer
            ? `${treasurer.display_name || treasurer.displayName || 'Không rõ tên'}`
            : keepperId.substring(0, 8) + '...';
    }

    container.innerHTML = `
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px;">
            <div style="padding: 16px; background: var(--surface); border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 13px; color: var(--text-muted); margin-bottom: 4px;">Số dư quỹ hiện tại</div>
                <div style="font-size: 22px; font-weight: 700; color: var(--primary);">${balance}</div>
            </div>
            <div style="padding: 16px; background: var(--surface); border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 13px; color: var(--text-muted); margin-bottom: 4px;">Thủ quỹ hiện tại</div>
                <div style="font-size: 16px; font-weight: 600;">${treasurerName}</div>
                ${keepperId ? `<div style="font-size: 11px; color: var(--text-muted); margin-top: 2px;"><code>${keepperId}</code></div>` : ''}
            </div>
            <div style="padding: 16px; background: var(--surface); border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 13px; color: var(--text-muted); margin-bottom: 4px;">Ngày tạo quỹ</div>
                <div style="font-size: 16px; font-weight: 600;">${createdAtStr}</div>
            </div>
        </div>
    `;

    loadMemberBalances();
}

async function loadMemberBalances() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const container = document.getElementById('memberBalancesContent');
    if (!container) return;

    container.innerHTML = '<p style="color: var(--text-muted); text-align: center;">Đang tải số dư thành viên...</p>';

    const res = await callApi(`/v1/groups/${groupId}/balances`, 'GET', null, false);
    if (!res.ok || !res.data?.data) {
        container.innerHTML = '<p style="color: var(--danger); text-align: center;">Không tải được số dư thành viên.</p>';
        return;
    }

    const report = res.data.data;
    const balances = report.balances || [];

    if (balances.length === 0) {
        container.innerHTML = '<p style="color: var(--text-muted); text-align: center;">Chưa có dữ liệu số dư.</p>';
        return;
    }

    const fmt = (v) => Number(v ?? 0).toLocaleString('vi-VN');
    const colorVal = (v) => {
        const n = Number(v ?? 0);
        if (n > 0) return 'color: var(--success)';
        if (n < 0) return 'color: var(--danger)';
        return 'color: var(--text-muted)';
    };

    let rows = balances.map(b => {
        const name = b.full_name || b.fullName || 'Không rõ';
        const net = Number(b.net_balance ?? b.netBalance ?? 0);
        const paid = Number(b.total_paid_out_of_pocket ?? b.totalPaidOutOfPocket ?? 0);
        const share = Number(b.total_share_amount ?? b.totalShareAmount ?? 0);
        const refunded = Number(b.total_refunded ?? b.totalRefunded ?? 0);
        const needed = Number(b.needed_contribution ?? b.neededContribution ?? 0);
        const status = b.status || '';
        const statusBadge = status === 'ACTIVE'
            ? '<span style="color: var(--success); font-size: 11px;">● Hoạt động</span>'
            : `<span style="color: var(--text-muted); font-size: 11px;">● ${status}</span>`;

        return `
            <tr>
                <td style="padding: 10px 12px;">
                    <div style="font-weight: 600;">${name}</div>
                    <div style="margin-top: 2px;">${statusBadge}</div>
                </td>
                <td style="padding: 10px 12px; text-align: right;">${fmt(paid)} đ</td>
                <td style="padding: 10px 12px; text-align: right;">${fmt(share)} đ</td>
                <td style="padding: 10px 12px; text-align: right;">${fmt(refunded)} đ</td>
                <td style="padding: 10px 12px; text-align: right; font-weight: 700; ${colorVal(net)}">${net >= 0 ? '+' : ''}${fmt(net)} đ</td>
                <td style="padding: 10px 12px; text-align: right; ${needed > 0 ? 'color: var(--warning)' : 'color: var(--text-muted)'}">${needed > 0 ? fmt(needed) + ' đ' : '—'}</td>
            </tr>`;
    }).join('');

    container.innerHTML = `
        <div style="font-size: 14px; font-weight: 600; margin-bottom: 10px;">📋 Số Dư Thành Viên</div>
        <div style="overflow-x: auto;">
            <table style="width: 100%; border-collapse: collapse; font-size: 13px;">
                <thead>
                    <tr style="border-bottom: 1px solid var(--border); color: var(--text-muted);">
                        <th style="padding: 8px 12px; text-align: left;">Thành viên</th>
                        <th style="padding: 8px 12px; text-align: right;">Đã chi hộ</th>
                        <th style="padding: 8px 12px; text-align: right;">Phần phải chịu</th>
                        <th style="padding: 8px 12px; text-align: right;">Đã hoàn</th>
                        <th style="padding: 8px 12px; text-align: right;">Số dư ròng</th>
                        <th style="padding: 8px 12px; text-align: right;">Cần đóng thêm</th>
                    </tr>
                </thead>
                <tbody>${rows}</tbody>
            </table>
        </div>
    `;
}

// ----------------- FUND APIS -----------------

// Chuyển giao người giữ quỹ
async function updateFundKeepper() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const newKeepperId = document.getElementById('newKeepperUserId').value.trim();
    if (!newKeepperId) {
        alert('Vui lòng nhập User ID của thủ quỹ mới!');
        return;
    }

    const payload = {
        keepper_id: newKeepperId
    };

    const res = await callApi(`/v1/groups/${groupId}/fund-kepper`, 'PUT', payload);
    if (res.ok) {
        detailGroup();
    }
}

// Kiểm kê và đối soát số dư quỹ
async function reconcileFund() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const actualBalanceStr = document.getElementById('reconcileActualBalance').value.trim();
    const reconcileDate = document.getElementById('reconcileDate').value.trim();
    const reason = document.getElementById('reconcileReason').value.trim();

    if (!actualBalanceStr) {
        alert('Vui lòng nhập số dư thực tế kiểm kê!');
        return;
    }

    const payload = {
        actual_balance: parseInt(actualBalanceStr, 10),
        reconcile_date: reconcileDate || new Date().toISOString().split('T')[0],
        reason: reason || 'Kiểm kê đối soát định kỳ'
    };

    await callApi(`/v1/groups/${groupId}/fund/reconcile`, 'POST', payload);
}

// ----------------- REPORT APIS -----------------

// Xem báo cáo tổng quan tài chính nhóm
async function getGroupSummaryReport() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const monthInput = document.getElementById('reportMonth').value.trim();
    const query = monthInput ? `?month=${encodeURIComponent(monthInput)}` : '';

    const container = document.getElementById('summaryReportResult');
    if (container) {
        container.innerHTML = '<p style="color: var(--text-muted); text-align: center; padding: 12px;">Đang tải báo cáo tổng quan...</p>';
    }

    const res = await callApi(`/v1/groups/${groupId}/summary${query}`, 'GET');
    if (res.ok && res.data && res.data.data) {
        renderSummaryReport(res.data.data);
    } else if (container) {
        container.innerHTML = '<p style="color: var(--danger); text-align: center; padding: 12px;">Không thể tải dữ liệu báo cáo tổng quan.</p>';
    }
}

// Hiển thị trực quan dữ liệu báo cáo tổng quan
function renderSummaryReport(data) {
    const container = document.getElementById('summaryReportResult');
    if (!container) return;

    const fmt = v => Number(v ?? 0).toLocaleString('vi-VN');
    const period = data.period || 'Toàn thời gian';
    const currentBalance = data.fund?.current_balance ?? 0;
    const target = data.target;
    const totalExpense = data.total_expense ?? 0;
    const totalContribution = data.total_contribution ?? 0;

    container.innerHTML = `
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px;">
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 12px;">
                <div style="font-size: 11px; color: var(--text-muted);">Kỳ báo cáo</div>
                <div style="font-size: 14px; font-weight: 600; color: var(--primary); margin-top: 4px;">${period}</div>
            </div>
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 12px;">
                <div style="font-size: 11px; color: var(--text-muted);">Số dư quỹ hiện tại</div>
                <div style="font-size: 18px; font-weight: 700; color: var(--success); margin-top: 4px;">${fmt(currentBalance)} đ</div>
            </div>
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 12px;">
                <div style="font-size: 11px; color: var(--text-muted);">Mục tiêu tiết kiệm</div>
                <div style="font-size: 16px; font-weight: 600; color: var(--info); margin-top: 4px;">${target ? fmt(target) + ' đ' : 'Chưa đặt'}</div>
            </div>
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 12px;">
                <div style="font-size: 11px; color: var(--text-muted);">Tổng chi tiêu</div>
                <div style="font-size: 16px; font-weight: 600; color: var(--danger); margin-top: 4px;">${fmt(totalExpense)} đ</div>
            </div>
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 12px;">
                <div style="font-size: 11px; color: var(--text-muted);">Tổng đóng góp</div>
                <div style="font-size: 16px; font-weight: 600; color: var(--primary); margin-top: 4px;">${fmt(totalContribution)} đ</div>
            </div>
        </div>
    `;
}

// Xem báo cáo cân đối thu chi thành viên
async function getGroupBalancesReport() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const container = document.getElementById('balancesReportResult');
    if (container) {
        container.innerHTML = '<p style="color: var(--text-muted); text-align: center; padding: 12px;">Đang tải báo cáo cân đối thành viên...</p>';
    }

    const res = await callApi(`/v1/groups/${groupId}/balances`, 'GET');
    if (res.ok && res.data && res.data.data) {
        renderBalancesReport(res.data.data);
    } else if (container) {
        container.innerHTML = '<p style="color: var(--danger); text-align: center; padding: 12px;">Không thể tải dữ liệu báo cáo cân đối.</p>';
    }
}

// Hiển thị trực quan dữ liệu báo cáo cân đối thành viên
function renderBalancesReport(data) {
    const container = document.getElementById('balancesReportResult');
    if (!container) return;

    const fmt = v => Number(v ?? 0).toLocaleString('vi-VN');
    const colorVal = v => {
        const n = Number(v ?? 0);
        if (n > 0) return 'color: var(--success); font-weight: 700;';
        if (n < 0) return 'color: var(--danger); font-weight: 700;';
        return 'color: var(--text-muted);';
    };

    const fundBalance = data.fund_balance ?? 0;
    const needed = data.total_needed_contribution ?? 0;
    const isSettlement = data.is_settlement_enabled ? 'Đang bật' : 'Tắt';
    const balances = data.balances || [];

    let rowsHtml = '';
    if (balances.length === 0) {
        rowsHtml = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 12px;">Chưa có dữ liệu cân đối thành viên</td></tr>';
    } else {
        rowsHtml = balances.map(b => {
            const name = b.full_name || 'Thành viên';
            const userId = b.user_id || '';
            const status = b.status || 'ACTIVE';
            const statusBadge = status === 'ACTIVE'
                ? '<span class="status-badge status-success" style="font-size: 11px; padding: 2px 6px;">ACTIVE</span>'
                : `<span class="status-badge status-idle" style="font-size: 11px; padding: 2px 6px;">${status}</span>`;
            const paid = b.total_paid_out_of_pocket ?? 0;
            const refunded = b.total_refunded ?? 0;
            const share = b.total_share_amount ?? 0;
            const net = b.net_balance ?? 0;
            const need = b.needed_contribution ?? 0;

            return `
                <tr style="border-bottom: 1px solid var(--border);">
                    <td style="padding: 10px 12px;">
                        <div style="font-weight: 600;">${name}</div>
                        <div style="font-size: 11px; color: var(--text-muted); font-family: monospace;">${userId ? userId.substring(0, 8) + '...' : ''}</div>
                    </td>
                    <td style="padding: 10px 12px; text-align: center;">${statusBadge}</td>
                    <td style="padding: 10px 12px; text-align: right;">${fmt(paid)} đ</td>
                    <td style="padding: 10px 12px; text-align: right;">${fmt(refunded)} đ</td>
                    <td style="padding: 10px 12px; text-align: right;">${fmt(share)} đ</td>
                    <td style="padding: 10px 12px; text-align: right; ${colorVal(net)}">${net >= 0 ? '+' : ''}${fmt(net)} đ</td>
                    <td style="padding: 10px 12px; text-align: right; font-weight: 600; ${need > 0 ? 'color: var(--warning);' : 'color: var(--text-muted);'}">
                        ${need > 0 ? fmt(need) + ' đ' : '0 đ'}
                    </td>
                </tr>
            `;
        }).join('');
    }

    container.innerHTML = `
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 14px;">
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 12px;">
                <div style="font-size: 11px; color: var(--text-muted);">Số dư quỹ hiện tại</div>
                <div style="font-size: 18px; font-weight: 700; color: var(--primary); margin-top: 4px;">${fmt(fundBalance)} đ</div>
            </div>
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 12px;">
                <div style="font-size: 11px; color: var(--text-muted);">Tổng tiền cần đóng thêm</div>
                <div style="font-size: 18px; font-weight: 700; color: ${needed > 0 ? 'var(--warning)' : 'var(--text-muted)'}; margin-top: 4px;">${fmt(needed)} đ</div>
            </div>
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 12px;">
                <div style="font-size: 11px; color: var(--text-muted);">Quyết toán tự động</div>
                <div style="font-size: 15px; font-weight: 600; color: var(--text); margin-top: 4px;">${isSettlement}</div>
            </div>
        </div>
        <div class="table-container" style="border: 1px solid var(--border); border-radius: 8px;">
            <table style="width: 100%; border-collapse: collapse; font-size: 13px;">
                <thead>
                    <tr style="background: #f8fafc; border-bottom: 1px solid var(--border);">
                        <th style="padding: 10px 12px; text-align: left;">Thành Viên</th>
                        <th style="padding: 10px 12px; text-align: center;">Trạng Thái</th>
                        <th style="padding: 10px 12px; text-align: right;">Tự Chi Hộ</th>
                        <th style="padding: 10px 12px; text-align: right;">Đã Hoàn</th>
                        <th style="padding: 10px 12px; text-align: right;">Phần Phải Chịu</th>
                        <th style="padding: 10px 12px; text-align: right;">Cân Đối Ròng</th>
                        <th style="padding: 10px 12px; text-align: right;">Cần Đóng Thêm</th>
                    </tr>
                </thead>
                <tbody>${rowsHtml}</tbody>
            </table>
        </div>
    `;
}

// ----------------- PHÂN QUYỀN HIỂN THỊ -----------------

function applyRoleVisibility() {
    const isOwner = currentGroupRole === 'OWNER';
    const isReviewer = isOwner || currentGroupIsTreasurer;

    document.querySelectorAll('.role-owner-only').forEach(el => {
        el.style.display = isOwner ? '' : 'none';
    });
    document.querySelectorAll('.role-reviewer-only').forEach(el => {
        el.style.display = isReviewer ? '' : 'none';
    });

    // tự động chuyển tab về Quản lý nhóm nếu tài khoản không có quyền xem tab báo cáo
    if (!isReviewer) {
        const reportContent = document.getElementById('tab-report');
        if (reportContent && reportContent.classList.contains('active')) {
            switchTab('tab-groups');
        }
    }
}

// ----------------- TRANSACTION APIS -----------------

// Tải danh mục chi tiêu cho dropdown
async function loadExpenseCategories() {
    if (cachedCategories && cachedCategories.length > 0) {
        populateCategorySelect(cachedCategories);
        return;
    }

    const res = await callApi('/v1/categories?as_tree=false', 'GET');
    if (res.ok && res.data && res.data.data) {
        cachedCategories = res.data.data;
        populateCategorySelect(cachedCategories);
    }
}

// Điền danh mục vào dropdown
function populateCategorySelect(categories) {
    const select = document.getElementById('txnCategoryId');
    if (!select) return;

    const currentVal = select.value;
    select.innerHTML = '<option value="">-- Chọn danh mục (*) --</option>';

    if (Array.isArray(categories)) {
        categories.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.innerText = c.name || c.id;
            select.appendChild(opt);
        });
    }

    if (currentVal && Array.from(select.options).some(o => o.value === currentVal)) {
        select.value = currentVal;
    }
}

// Điền thành viên vào dropdown Người thực hiện và danh sách Người tham gia
function populateTransactionMemberSelects(members) {
    currentGroupMembersList = members || [];

    // nạp dropdown Người thực hiện
    const transactorSelect = document.getElementById('txnTransactorId');
    if (transactorSelect) {
        const currentVal = transactorSelect.value;
        transactorSelect.innerHTML = '<option value="">-- Chọn thành viên (*) --</option>';

        let myId = null;
        try {
            const u = JSON.parse(localStorage.getItem(STORAGE_KEY_USER));
            myId = u?.id;
        } catch (e) {}

        currentGroupMembersList.forEach(m => {
            const uid = m.user_id || m.userId || '';
            const name = m.display_name || m.displayName || uid;
            const opt = document.createElement('option');
            opt.value = uid;
            opt.innerText = `${name} (${uid.substring(0, 8)}...)`;
            transactorSelect.appendChild(opt);
        });

        if (currentVal && Array.from(transactorSelect.options).some(o => o.value === currentVal)) {
            transactorSelect.value = currentVal;
        } else if (myId && Array.from(transactorSelect.options).some(o => o.value === myId)) {
            transactorSelect.value = myId;
        }
    }

    // nạp danh sách Người tham gia
    const container = document.getElementById('txnParticipantsContainer');
    if (container) {
        container.innerHTML = '';
        if (currentGroupMembersList.length === 0) {
            container.innerHTML = '<div style="font-size: 12px; color: var(--text-muted); text-align: center; padding: 10px;">Chưa có thành viên nào</div>';
            return;
        }

        currentGroupMembersList.forEach(m => {
            const uid = m.user_id || m.userId || '';
            const name = m.display_name || m.displayName || uid;

            const row = document.createElement('div');
            row.style.cssText = 'display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 4px 6px; background: #fff; border: 1px solid var(--border); border-radius: 6px;';
            row.innerHTML = `
                <label style="display: flex; align-items: center; gap: 6px; font-size: 13px; cursor: pointer; flex: 1;">
                    <input type="checkbox" class="participant-checkbox" data-user-id="${uid}" checked>
                    <strong>${name}</strong>
                    <code style="font-size: 11px; color: var(--text-muted);">${uid.substring(0, 8)}...</code>
                </label>
                <div style="width: 140px;">
                    <input type="number" class="participant-amount" data-user-id="${uid}" placeholder="Số tiền (đ)" style="padding: 4px 6px; font-size: 12px; border-radius: 4px; border: 1px solid var(--border); width: 100%;">
                </div>
            `;
            container.appendChild(row);
        });
    }
}

// Bật tắt danh sách tùy chỉnh người tham gia
function toggleParticipantCustomSplit() {
    const isSplitEquallyAll = document.getElementById('splitEquallyAll')?.checked;
    const customList = document.getElementById('customParticipantsList');
    if (customList) {
        customList.style.display = isSplitEquallyAll ? 'none' : 'block';
    }
}

// Tự động chia đều số tiền giao dịch cho các thành viên được chọn
function autoSplitSelectedParticipants() {
    const amountStr = document.getElementById('txnAmount')?.value?.trim();
    const amount = amountStr ? parseInt(amountStr, 10) : 0;
    if (isNaN(amount) || amount <= 0) {
        alert('Vui lòng nhập số tiền giao dịch hợp lệ (> 0) trước khi chia đều!');
        return;
    }

    const checkedBoxes = document.querySelectorAll('#txnParticipantsContainer .participant-checkbox:checked');
    const count = checkedBoxes.length;
    if (count === 0) {
        alert('Vui lòng tick chọn ít nhất một thành viên để chia đều!');
        return;
    }

    const base = Math.floor(amount / count);
    const rem = amount % count;
    checkedBoxes.forEach((cb, index) => {
        const uid = cb.getAttribute('data-user-id');
        const input = document.querySelector(`.participant-amount[data-user-id="${uid}"]`);
        if (input) {
            input.value = base + (index < rem ? 1 : 0);
        }
    });
}

// Xử lý khi thay đổi loại giao dịch
function onTxnTypeChange() {
    const type = document.getElementById('txnType').value;
    const isExpense = type === 'EXPENSE';

    const catWrapper = document.getElementById('txnCategoryWrapper');
    if (catWrapper) {
        catWrapper.style.display = isExpense ? '' : 'none';
    }

    const participantsSection = document.getElementById('txnParticipantsSection');
    if (participantsSection) {
        participantsSection.style.display = isExpense ? '' : 'none';
    }

    const moneySourceSelect = document.getElementById('txnMoneySource');
    if (moneySourceSelect) {
        if (!isExpense) {
            moneySourceSelect.value = 'PERSONAL';
        }
    }
}

// Tạo giao dịch nhóm
async function createGroupTransaction() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const type = document.getElementById('txnType').value;
    const moneySource = document.getElementById('txnMoneySource').value;
    const amountStr = document.getElementById('txnAmount').value.trim();
    const transactorId = document.getElementById('txnTransactorId').value.trim();
    const categoryId = document.getElementById('txnCategoryId').value.trim();
    const date = document.getElementById('txnDate').value.trim();
    const note = document.getElementById('txnNote').value.trim();

    if (!amountStr || !transactorId) {
        alert('Vui lòng nhập số tiền và chọn người thực hiện!');
        return;
    }

    if (type === 'EXPENSE' && !categoryId) {
        alert('Giao dịch chi tiêu (EXPENSE) bắt buộc phải chọn danh mục!');
        return;
    }

    // thu thập danh sách người tham gia chia tiền
    let participants = [];
    if (type === 'EXPENSE') {
        const isSplitEquallyAll = document.getElementById('splitEquallyAll')?.checked ?? true;
        if (!isSplitEquallyAll) {
            const checkedBoxes = document.querySelectorAll('#txnParticipantsContainer .participant-checkbox:checked');
            if (checkedBoxes.length === 0) {
                alert('Vui lòng tick chọn ít nhất một thành viên tham gia chia tiền!');
                return;
            }

            const totalAmount = parseInt(amountStr, 10);
            let totalSplit = 0;

            for (const cb of checkedBoxes) {
                const uid = cb.getAttribute('data-user-id');
                const amountInput = document.querySelector(`.participant-amount[data-user-id="${uid}"]`);
                const shareAmountStr = amountInput?.value?.trim();

                if (!shareAmountStr || isNaN(parseInt(shareAmountStr, 10)) || parseInt(shareAmountStr, 10) <= 0) {
                    alert('Vui lòng nhập số tiền hợp lệ (> 0) cho tất cả thành viên được chọn hoặc bấm "⚡ Tự động chia đều"!');
                    return;
                }

                const shareAmount = parseInt(shareAmountStr, 10);
                totalSplit += shareAmount;
                participants.push({
                    user_id: uid,
                    share_amount: shareAmount
                });
            }

            if (totalSplit !== totalAmount) {
                alert(`Tổng tiền chia (${totalSplit.toLocaleString()}đ) không khớp với số tiền giao dịch (${totalAmount.toLocaleString()}đ)! Vui lòng kiểm tra lại.`);
                return;
            }
        }
    }

    const payload = {
        type,
        money_source: moneySource,
        amount: parseInt(amountStr, 10),
        transactor_id: transactorId,
        category_id: type === 'EXPENSE' ? (categoryId || null) : null,
        date: date || null,
        note: note || null,
        participants: participants
    };

    const res = await callApi(`/v1/groups/${groupId}/transactions`, 'POST', payload);
    if (res.ok) {
        loadMyTransactions();
        loadAllTransactions();
        loadPendingCount(groupId);
    }
}

// Tải giao dịch của tôi (transactor_id = user hiện tại)
async function loadMyTransactions() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    let myId = null;
    try {
        const user = JSON.parse(localStorage.getItem(STORAGE_KEY_USER));
        myId = user?.id;
    } catch (e) {}

    if (!myId) {
        alert('Không xác định được User ID hiện tại!');
        return;
    }

    const res = await callApi(`/v1/groups/${groupId}/transactions/mine?page=1&size=50`, 'GET');
    if (res.ok && res.data && res.data.data) {
        const items = res.data.data.items || res.data.data || [];
        renderMyTransactions(items, myId);
    }
}

// Hiển thị bảng giao dịch của tôi với nút sửa/xóa theo quyền
function renderMyTransactions(items, myId) {
    const badge = document.getElementById('myTxnCountBadge');
    if (badge) {
        badge.innerText = `${items.length} giao dịch`;
        badge.className = items.length > 0 ? 'status-badge status-success' : 'status-badge status-idle';
    }

    const tbody = document.getElementById('myTxnTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (!Array.isArray(items) || items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-muted);">Không có giao dịch nào</td></tr>';
        return;
    }

    const isOwner = currentGroupRole === 'OWNER';
    const isReviewer = isOwner || currentGroupIsTreasurer;
    const isPlainMember = !isOwner && !currentGroupIsTreasurer;

    items.forEach(tx => {
        const txnId = tx.id || '';
        const type = tx.type || '-';
        const amount = Number(tx.amount || 0).toLocaleString('vi-VN') + ' đ';
        const status = tx.status || '-';
        const createdBy = tx.created_by || tx.createdBy || '-';
        const note = tx.note || '-';
        const occurredAt = tx.occurred_at ? new Date(tx.occurred_at).toLocaleDateString('vi-VN') : '-';

        const isCreator = createdBy === myId;
        const isEditableType = type === 'EXPENSE' || type === 'CONTRIBUTION';

        // nút Cập nhật: OWNER/THỦ QUỸ mọi dòng, MEMBER chỉ do mình tạo + PENDING + EXPENSE/CONTRIBUTION
        let showEdit = isReviewer || (isCreator && isEditableType && status === 'PENDING');
        // nút Xóa: OWNER mọi dòng, MEMBER thường chỉ do mình tạo + PENDING. THỦ QUỸ không xóa
        let showDelete = isOwner || (isPlainMember && isCreator && status === 'PENDING');

        let actions = `<button class="btn btn-secondary" style="padding: 3px 6px; font-size: 11px;" onclick="showTransactionDetail('${txnId}')">🔍 Chi tiết</button> `;
        if (showEdit) {
            actions += `<button class="btn btn-secondary" style="padding: 3px 8px; font-size: 11px;" onclick="promptUpdateTransaction('${txnId}')">Cập nhật</button> `;
        }
        if (showDelete) {
            actions += `<button class="btn btn-danger" style="padding: 3px 8px; font-size: 11px;" onclick="deleteGroupTransaction('${txnId}')">Xóa</button>`;
        }

        const statusClass = status === 'CONFIRMED' ? 'status-success' : status === 'PENDING' ? 'status-warning' : 'status-error';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><code style="font-size: 11px;">${txnId.substring(0, 8)}...</code></td>
            <td><span class="status-badge status-idle">${type}</span></td>
            <td style="font-weight: 600; color: var(--primary);">${amount}</td>
            <td><span class="status-badge ${statusClass}">${status}</span></td>
            <td><code style="font-size: 11px;">${createdBy === myId ? 'Tôi' : createdBy.substring(0, 8) + '...'}</code></td>
            <td>${note}</td>
            <td>${occurredAt}</td>
            <td style="display: flex; gap: 4px; flex-wrap: wrap;">${actions || '<span style="color: var(--text-muted); font-size: 12px;">—</span>'}</td>
        `;
        tbody.appendChild(tr);
    });
}

// Cập nhật giao dịch (prompt đơn giản cho trang test)
async function promptUpdateTransaction(txnId) {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const newAmount = prompt('Nhập số tiền mới (VNĐ):');
    if (newAmount === null) return;
    const newNote = prompt('Nhập ghi chú mới (để trống giữ nguyên):');

    const payload = {};
    if (newAmount) payload.amount = parseInt(newAmount, 10);
    if (newNote) payload.note = newNote;

    const res = await callApi(`/v1/groups/${groupId}/transactions/${txnId}`, 'PUT', payload);
    if (res.ok) {
        loadMyTransactions();
        detailGroup();
    }
}

// Xóa giao dịch
async function deleteGroupTransaction(txnId) {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    if (!confirm('Bạn có chắc chắn muốn xóa giao dịch này?')) return;

    const res = await callApi(`/v1/groups/${groupId}/transactions/${txnId}`, 'DELETE');
    if (res.ok) {
        loadMyTransactions();
        loadPendingCount(groupId);
        detailGroup();
    }
}

// Tải danh sách giao dịch chung (có bộ lọc)
async function loadAllTransactions() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const moneySource = document.getElementById('filterMoneySource').value;
    const type = document.getElementById('filterType').value;
    const status = document.getElementById('filterStatus').value;

    let query = '?page=1&size=50';
    if (moneySource) query += `&money_source=${moneySource}`;
    if (type) query += `&type=${type}`;
    if (status) query += `&status=${status}`;

    const res = await callApi(`/v1/groups/${groupId}/transactions${query}`, 'GET');
    if (res.ok && res.data && res.data.data) {
        const items = res.data.data.items || res.data.data || [];
        renderAllTransactions(items);
    }
}

// Hiển thị danh sách giao dịch chung
function renderAllTransactions(items) {
    const badge = document.getElementById('allTxnCountBadge');
    if (badge) {
        badge.innerText = `${items.length} giao dịch`;
        badge.className = items.length > 0 ? 'status-badge status-success' : 'status-badge status-idle';
    }

    const tbody = document.getElementById('allTxnTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (!Array.isArray(items) || items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-muted);">Không có giao dịch nào</td></tr>';
        return;
    }

    items.forEach(tx => {
        const txnId = tx.id || '';
        const type = tx.type || '-';
        const moneySource = tx.money_source || '-';
        const amount = Number(tx.amount || 0).toLocaleString('vi-VN') + ' đ';
        const status = tx.status || '-';
        const transactorId = tx.transactor_id || '-';
        const note = tx.note || '-';
        const occurredAt = tx.occurred_at ? new Date(tx.occurred_at).toLocaleDateString('vi-VN') : '-';

        const statusClass = status === 'CONFIRMED' ? 'status-success' : status === 'PENDING' ? 'status-warning' : 'status-error';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><code style="font-size: 11px;">${txnId.substring(0, 8)}...</code></td>
            <td><span class="status-badge status-idle">${type}</span></td>
            <td><span class="status-badge status-idle">${moneySource}</span></td>
            <td style="font-weight: 600; color: var(--primary);">${amount}</td>
            <td><span class="status-badge ${statusClass}">${status}</span></td>
            <td><code style="font-size: 11px;">${transactorId.substring(0, 8)}...</code></td>
            <td>${note}</td>
            <td>${occurredAt}</td>
            <td>
                <button class="btn btn-secondary" style="padding: 3px 6px; font-size: 11px;" onclick="showTransactionDetail('${txnId}')">
                    🔍 Chi tiết
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// ----------------- TRANSACTION DETAIL MODAL -----------------

// Xem chi tiết giao dịch
async function showTransactionDetail(txnId) {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const modal = document.getElementById('txnDetailModal');
    const body = document.getElementById('txnDetailModalBody');
    if (!modal || !body) return;

    modal.style.display = 'flex';
    body.innerHTML = '<p style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải chi tiết giao dịch...</p>';

    const res = await callApi(`/v1/groups/${groupId}/transactions/${txnId}`, 'GET');
    if (!res.ok || !res.data?.data) {
        body.innerHTML = '<p style="text-align: center; color: var(--danger); padding: 16px;">Không tải được chi tiết giao dịch.</p>';
        return;
    }

    const tx = res.data.data;
    const fmt = v => Number(v ?? 0).toLocaleString('vi-VN');

    // tìm tên danh mục từ cache nếu có
    let catName = tx.category_id || '-';
    if (cachedCategories && tx.category_id) {
        const foundCat = cachedCategories.find(c => c.id === tx.category_id);
        if (foundCat) catName = `${foundCat.name || foundCat.id}`;
    }

    // tìm tên thành viên từ cache
    const findMemberName = uid => {
        if (!uid) return '-';
        if (currentGroupMembersList && currentGroupMembersList.length > 0) {
            const found = currentGroupMembersList.find(m => (m.user_id || m.userId) === uid);
            if (found) return found.display_name || found.displayName || uid;
        }
        return uid;
    };

    const transactorName = findMemberName(tx.transactor_id);
    const createdByName = findMemberName(tx.created_by);
    const reviewedByName = tx.reviewed_by ? findMemberName(tx.reviewed_by) : null;

    const statusClass = tx.status === 'CONFIRMED' ? 'status-success' : tx.status === 'PENDING' ? 'status-warning' : 'status-error';
    const occurredAtStr = tx.occurred_at ? new Date(tx.occurred_at).toLocaleDateString('vi-VN') : '-';
    const createdAtStr = tx.created_at ? new Date(tx.created_at).toLocaleString('vi-VN') : '-';
    const reviewedAtStr = tx.reviewed_at ? new Date(tx.reviewed_at).toLocaleString('vi-VN') : null;

    // render danh sách người tham gia chia tiền
    let participantsHtml = '<p style="color: var(--text-muted); font-size: 13px; margin: 4px 0;">Không có thông tin chia tiền riêng (hoặc chia đều theo cấu hình).</p>';
    if (Array.isArray(tx.participants) && tx.participants.length > 0) {
        const rows = tx.participants.map(p => {
            const mName = findMemberName(p.user_id);
            const share = p.share_amount != null ? `${fmt(p.share_amount)} đ` : 'Tự chia đều';
            return `
                <tr style="border-bottom: 1px solid var(--border);">
                    <td style="padding: 6px 10px;">
                        <strong>${mName}</strong>
                        <div style="font-size: 11px; color: var(--text-muted); font-family: monospace;">${p.user_id}</div>
                    </td>
                    <td style="padding: 6px 10px; text-align: right; font-weight: 600; color: var(--primary);">${share}</td>
                </tr>
            `;
        }).join('');

        participantsHtml = `
            <div class="table-container" style="border: 1px solid var(--border); border-radius: 6px; margin-top: 6px;">
                <table style="width: 100%; border-collapse: collapse; font-size: 13px;">
                    <thead>
                        <tr style="background: #f8fafc; border-bottom: 1px solid var(--border);">
                            <th style="padding: 6px 10px; text-align: left;">Thành Viên</th>
                            <th style="padding: 6px 10px; text-align: right;">Số Tiền Chịu (Share)</th>
                        </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        `;
    }

    body.innerHTML = `
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin-bottom: 14px;">
            <div style="background: #f8fafc; padding: 10px; border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 11px; color: var(--text-muted);">Mã giao dịch</div>
                <div style="font-size: 12px; font-family: monospace; font-weight: 600; word-break: break-all; margin-top: 2px;">${tx.id}</div>
            </div>
            <div style="background: #f8fafc; padding: 10px; border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 11px; color: var(--text-muted);">Số tiền</div>
                <div style="font-size: 18px; font-weight: 700; color: var(--primary); margin-top: 2px;">${fmt(tx.amount)} đ</div>
            </div>
            <div style="background: #f8fafc; padding: 10px; border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 11px; color: var(--text-muted);">Trạng thái & Loại</div>
                <div style="display: flex; gap: 6px; align-items: center; margin-top: 4px;">
                    <span class="status-badge ${statusClass}">${tx.status}</span>
                    <span class="status-badge status-idle">${tx.type}</span>
                </div>
            </div>
            <div style="background: #f8fafc; padding: 10px; border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 11px; color: var(--text-muted);">Nguồn tiền & Danh mục</div>
                <div style="font-size: 13px; font-weight: 600; margin-top: 2px;">${tx.money_source} | ${catName}</div>
            </div>
            <div style="background: #f8fafc; padding: 10px; border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 11px; color: var(--text-muted);">Người thực hiện</div>
                <div style="font-size: 13px; font-weight: 600; margin-top: 2px;">${transactorName}</div>
            </div>
            <div style="background: #f8fafc; padding: 10px; border-radius: 8px; border: 1px solid var(--border);">
                <div style="font-size: 11px; color: var(--text-muted);">Người tạo & Ngày tạo</div>
                <div style="font-size: 12px; margin-top: 2px;">${createdByName} (${createdAtStr})</div>
            </div>
        </div>

        <div style="margin-bottom: 14px; font-size: 13px;">
            <strong>Ghi chú:</strong> <span style="color: var(--text);">${tx.note || 'Không có ghi chú'}</span>
        </div>

        ${reviewedByName ? `
        <div style="margin-bottom: 14px; font-size: 12px; color: var(--text-muted); background: #f1f5f9; padding: 8px 10px; border-radius: 6px;">
            Duyệt bởi: <strong>${reviewedByName}</strong> vào lúc ${reviewedAtStr || '-'}
        </div>` : ''}

        <div style="margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--border);">
            <div style="font-size: 14px; font-weight: 600; margin-bottom: 6px;">👥 Danh Sách Người Tham Gia Chia Tiền (${tx.participants ? tx.participants.length : 0})</div>
            ${participantsHtml}
        </div>

        <div style="display: flex; justify-content: flex-end; margin-top: 16px;">
            <button class="btn btn-secondary" onclick="closeTransactionDetailModal()">Đóng</button>
        </div>
    `;
}

// Đóng modal chi tiết giao dịch
function closeTransactionDetailModal() {
    const modal = document.getElementById('txnDetailModal');
    if (modal) modal.style.display = 'none';
}

// Xử lý khi click vào backdrop ngoài modal
function onTxnModalOverlayClick(e) {
    if (e.target && e.target.id === 'txnDetailModal') {
        closeTransactionDetailModal();
    }
}
