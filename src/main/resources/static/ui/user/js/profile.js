async function loadProfile() {
    const u = dataOf(await api('/users/me'));
    if (!u) return;
    $('pEmail').value = u.email || '';
    $('pFirst').value = u.first_name || '';
    $('pLast').value = u.last_name || '';
    $('pAvatar').value = u.avatar_url || '';
    $('pId').textContent = u.id || '';
    $('pBadges').innerHTML = badge(u.status) + ' ' + badge(u.plan, 'info');
    localStorage.setItem(KEY_USER, JSON.stringify({ ...(Auth.user() || {}), ...u }));
}

$('btnSave').onclick = async () => {
    const res = await api('/users/me', {
        method: 'PATCH',
        body: { first_name: $('pFirst').value.trim(), last_name: $('pLast').value.trim(), avatar_url: $('pAvatar').value.trim() || null }
    });
    if (toastResult(res, 'Đã cập nhật hồ sơ')) loadProfile();
};

$('btnChangePw').onclick = async () => {
    const body = { old_password: $('oldPw').value, new_password: $('newPw').value };
    if (!body.old_password || body.new_password.length < 8) return toast('Nhập mật khẩu cũ và mật khẩu mới ≥ 8 ký tự', 'error');
    const res = await api('/users/me/password', { method: 'PUT', body });
    if (toastResult(res, 'Đã đổi mật khẩu')) { $('oldPw').value = ''; $('newPw').value = ''; }
};

$('btnGenPw').onclick = async () => {
    toastResult(await api('/users/me/password', { method: 'POST' }), 'Mật khẩu đã được tạo và gửi về email');
};

$('btnDelete').onclick = async () => {
    if (!await confirmBox('Xoá vĩnh viễn tài khoản của bạn?')) return;
    const pw = $('delPw').value;
    const res = await api('/users/me', { method: 'DELETE', body: pw ? { password: pw } : null });
    if (res.ok) { Auth.clear(); location.href = UI_ROOT + 'login.html'; }
};

(function showRoles() {
    const a = Auth.authorities();
    const c = Auth.claims() || {};
    $('pRoles').innerHTML = `Cấp vai trò cao nhất: <b>${esc(c.roles_level_top)}</b> · Gói: ${badge(c.plan, 'info')}<div class="mt">`
        + (a.length ? a.map(x => badge(x, x.startsWith('ROLE_') ? 'purple' : '')).join(' ') : '<span class="muted">Không có quyền đặc biệt</span>') + '</div>';
})();

loadProfile();
