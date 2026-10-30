// Trang đăng nhập: email/mật khẩu, Google, demo 1-click login, dán token thủ công.
if (Auth.token()) {
    location.replace(Auth.home());
}

async function doLogin(emailVal, passVal) {
    const email = (emailVal || $('email').value).trim();
    const password = passVal || $('password').value;
    if (!email || !password) {
        toast('Vui lòng nhập email và mật khẩu', 'error');
        return;
    }
    const res = await api('/auth/login', { method: 'POST', body: { email, password } });
    if (res.ok) {
        toast('Đăng nhập thành công!', 'success');
        setTimeout(() => Auth.startSession(dataOf(res)), 400);
    }
}

// Callback của Google Identity Services
async function handleGoogleResponse(response) {
    if (!response.credential) return;
    const res = await api('/auth/google', { method: 'POST', body: { id_token: response.credential } });
    if (res.ok) Auth.startSession(dataOf(res));
}

$('btnLogin').onclick = () => doLogin();
$('password').addEventListener('keydown', e => { if (e.key === 'Enter') doLogin(); });

$('btnRaw').onclick = () => {
    const t = $('rawToken').value.trim().replace(/^Bearer\s+/i, '');
    if (!t) return toast('Chưa nhập token', 'error');
    Auth.saveRawAccess(t);
    location.href = Auth.home();
};

// Demo quick-login buttons
document.querySelectorAll('.demo-btn').forEach(btn => {
    btn.onclick = () => {
        const email = btn.dataset.email;
        const pass = 'Admin@123';
        $('email').value = email;
        $('password').value = pass;
        doLogin(email, pass);
    };
});
