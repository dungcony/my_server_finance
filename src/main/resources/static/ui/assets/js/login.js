// Trang đăng nhập: email/mật khẩu, Google, dán token thủ công.
if (Auth.token()) {
    location.replace(Auth.home());
}

async function doLogin() {
    const email = $('email').value.trim();
    const password = $('password').value;
    if (!email || !password) {
        toast('Vui lòng nhập email và mật khẩu', 'error');
        return;
    }
    const res = await api('/auth/login', { method: 'POST', body: { email, password } });
    if (res.ok) Auth.startSession(dataOf(res));
}

// Callback của Google Identity Services
async function handleGoogleResponse(response) {
    if (!response.credential) return;
    const res = await api('/auth/google', { method: 'POST', body: { id_token: response.credential } });
    if (res.ok) Auth.startSession(dataOf(res));
}

$('btnLogin').onclick = doLogin;
$('password').addEventListener('keydown', e => { if (e.key === 'Enter') doLogin(); });
$('btnRaw').onclick = () => {
    const t = $('rawToken').value.trim().replace(/^Bearer\s+/i, '');
    if (!t) return toast('Chưa nhập token', 'error');
    Auth.saveRawAccess(t);
    location.href = Auth.home();
};
