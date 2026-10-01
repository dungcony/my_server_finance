// Quản lý trạng thái và cấu hình gọi API
const STORAGE_KEY_TOKEN = 'finance_ai_access_token';
const STORAGE_KEY_USER = 'finance_ai_user_info';
const STORAGE_KEY_GROUP_ID = 'finance_ai_current_group_id';

document.addEventListener('DOMContentLoaded', () => {
    // Khôi phục session và groupId đã lưu trong localStorage
    updateAuthUI();

    const savedGroupId = localStorage.getItem(STORAGE_KEY_GROUP_ID);
    if (savedGroupId) {
        document.getElementById('currentGroupId').value = savedGroupId;
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
    const token = authResult.access_token || authResult.accessToken;
    if (!token) return;

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
}

// Lưu Group ID đang chọn
function setCurrentGroupId(groupId) {
    document.getElementById('currentGroupId').value = groupId;
    localStorage.setItem(STORAGE_KEY_GROUP_ID, groupId);
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
        if (Array.isArray(groups) && groups.length > 0) {
            groups.forEach(g => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><code>${g.id}</code></td>
                    <td><strong>${g.name || ''}</strong></td>
                    <td>${g.my_role || ''}</td>
                    <td>${g.status || ''}</td>
                    <td><code>${g.invite_code || ''}</code></td>
                    <td>${g.member_count ?? 0}</td>
                    <td>
                        <button class="btn btn-secondary" style="padding: 4px 8px; font-size: 12px;" onclick="setCurrentGroupId('${g.id}')">
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
        setCurrentGroupId(res.data.data.id);
        listMyGroups();
    }
}

// Xem chi tiết nhóm
async function detailGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    await callApi(`/v1/groups/${groupId}`, 'GET');
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

    await callApi(`/v1/groups/${groupId}`, 'PATCH', payload);
}

// Xóa nhóm
async function deleteGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    if (!confirm('Bạn có chắc chắn muốn xóa nhóm này không?')) return;
    await callApi(`/v1/groups/${groupId}`, 'DELETE');
    listMyGroups();
}

// Tham gia nhóm bằng mã mời
async function joinGroupByCode() {
    const inviteCode = document.getElementById('joinInviteCode').value.trim();
    if (!inviteCode) {
        alert('Vui lòng nhập mã mời (ví dụ: GRP12345)!');
        return;
    }

    await callApi('/v1/groups/join', 'POST', { invite_code: inviteCode });
    listMyGroups();
}

// Lưu trữ nhóm
async function archiveGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    await callApi(`/v1/groups/${groupId}/archive`, 'POST');
    listMyGroups();
}

// Hủy lưu trữ nhóm
async function unarchiveGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    await callApi(`/v1/groups/${groupId}/unarchive`, 'POST');
    listMyGroups();
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

    await callApi(`/v1/groups/${groupId}/members`, 'POST', payload);
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
    await callApi(`/v1/groups/${groupId}/owner-role/${targetUserId}/`, 'PUT');
}

// Duyệt 1 thành viên
async function approveMember() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const memberUserId = document.getElementById('approveMemberUserId').value.trim();
    if (!memberUserId) {
        alert('Vui lòng nhập User ID thành viên cần duyệt!');
        return;
    }

    await callApi(`/v1/groups/${groupId}/members/${memberUserId}/approve`, 'POST');
}

// Duyệt toàn bộ thành viên đang chờ
async function approveAllMembers() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    await callApi(`/v1/groups/${groupId}/approves`, 'POST');
}

// Từ chối 1 thành viên
async function rejectMember() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    const memberUserId = document.getElementById('rejectMemberUserId').value.trim();
    if (!memberUserId) {
        alert('Vui lòng nhập User ID thành viên muốn từ chối!');
        return;
    }

    await callApi(`/v1/groups/${groupId}/members/${memberUserId}/reject`, 'POST');
}

// Từ chối tất cả thành viên đang chờ
async function rejectAllMembers() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;
    await callApi(`/v1/groups/${groupId}/rejects`, 'POST');
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

    await callApi(`/v1/groups/${groupId}/members/${memberUserId}`, 'DELETE');
}

// Tự rời khỏi nhóm
async function leaveGroup() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    if (!confirm('Bạn có chắc chắn muốn rời khỏi nhóm này không?')) return;

    await callApi(`/v1/groups/${groupId}/leave`, 'POST');
    listMyGroups();
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

    await callApi(`/v1/groups/${groupId}/fund-kepper`, 'PUT', payload);
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

    await callApi(`/v1/groups/${groupId}/summary${query}`, 'GET');
}

// Xem báo cáo cân đối thu chi thành viên
async function getGroupBalancesReport() {
    const groupId = getCurrentGroupId();
    if (!groupId) return;

    await callApi(`/v1/groups/${groupId}/balances`, 'GET');
}
