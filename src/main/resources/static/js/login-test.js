const API_ENDPOINT = '/v1/auth/google';

// Xử lý khi đăng nhập Google thành công qua nút bấm Google
function handleGoogleResponse(response) {
    console.log("Nhận được ID Token từ Google:", response.credential);
    document.getElementById('manualToken').value = response.credential;
    sendTokenToServer(response.credential);
}

// Gửi token nhập thủ công
function sendManualToken() {
    const token = document.getElementById('manualToken').value.trim();
    if (!token) {
        alert('Vui lòng nhập hoặc dán Google ID Token!');
        return;
    }
    sendTokenToServer(token);
}

// Gửi request POST /auth/google
async function sendTokenToServer(idToken) {
    const statusBadge = document.getElementById('statusBadge');
    const responseView = document.getElementById('responseView');

    statusBadge.className = 'status-badge status-idle';
    statusBadge.innerText = 'Đang kết nối tới server...';
    responseView.innerText = 'Sending POST ' + API_ENDPOINT + ' ...';

    try {
        const res = await fetch(API_ENDPOINT, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ id_token: idToken })
        });

        const data = await res.json();

        if (res.ok) {
            statusBadge.className = 'status-badge status-success';
            statusBadge.innerText = `HTTP ${res.status} OK — Đăng nhập thành công!`;
            // Lưu token và thông tin người dùng vào bộ nhớ trình duyệt để chia sẻ với các trang test
            if (data && data.data) {
                const token = data.data.token?.access || data.data.token?.accessToken || data.data.access_token || data.data.accessToken;
                if (token) {
                    localStorage.setItem('finance_ai_access_token', token);
                }
                if (data.data.user) {
                    localStorage.setItem('finance_ai_user_info', JSON.stringify(data.data.user));
                }
            }
        } else {
            statusBadge.className = 'status-badge status-error';
            statusBadge.innerText = `HTTP ${res.status} Error — Thất bại!`;
        }

        responseView.innerText = JSON.stringify(data, null, 2);
    } catch (err) {
        statusBadge.className = 'status-badge status-error';
        statusBadge.innerText = 'Lỗi mạng hoặc Server chưa bật!';
        responseView.innerText = err.toString();
    }
}

// Đăng ký tài khoản mới qua POST /v1/auth/register
async function registerNewUser() {
    const email = document.getElementById('regEmail').value.trim();
    const password = document.getElementById('regPassword').value.trim();
    const statusBadge = document.getElementById('statusBadge');
    const responseView = document.getElementById('responseView');

    if (!email || !password) {
        alert('Vui lòng nhập Email và Mật khẩu!');
        return;
    }
    if (password.length < 8) {
        alert('Mật khẩu tối thiểu phải 8 ký tự!');
        return;
    }

    statusBadge.className = 'status-badge status-idle';
    statusBadge.innerText = 'Đang gửi đăng ký...';
    responseView.innerText = 'Sending POST /v1/auth/register ...';

    try {
        const res = await fetch('/v1/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();

        if (res.ok) {
            statusBadge.className = 'status-badge status-success';
            statusBadge.innerText = `HTTP ${res.status} Created — Đăng ký thành công!`;
            document.getElementById('verifyTargetEmail').innerText = email;
            document.getElementById('verifyOtpBox').style.display = 'block';
        } else {
            statusBadge.className = 'status-badge status-error';
            statusBadge.innerText = `HTTP ${res.status} Error — Đăng ký thất bại!`;
        }

        responseView.innerText = JSON.stringify(data, null, 2);
    } catch (err) {
        statusBadge.className = 'status-badge status-error';
        statusBadge.innerText = 'Lỗi kết nối Server!';
        responseView.innerText = err.toString();
    }
}

// Xác thực tài khoản qua OTP và nhận token
async function verifyUserEmail() {
    const email = document.getElementById('regEmail').value.trim();
    const code = document.getElementById('regOtpCode').value.trim();
    const statusBadge = document.getElementById('statusBadge');
    const responseView = document.getElementById('responseView');

    if (!code) {
        alert('Vui lòng nhập mã OTP!');
        return;
    }

    statusBadge.className = 'status-badge status-idle';
    statusBadge.innerText = 'Đang xác thực OTP...';
    responseView.innerText = 'Sending POST /v1/auth/verify-email ...';

    try {
        const res = await fetch('/v1/auth/verify-email', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, code })
        });
        const data = await res.json();

        if (res.ok) {
            statusBadge.className = 'status-badge status-success';
            statusBadge.innerText = `HTTP ${res.status} OK — Kích hoạt tài khoản thành công!`;
            if (data && data.data) {
                const token = data.data.token?.access || data.data.token?.accessToken || data.data.access_token || data.data.accessToken;
                if (token) {
                    localStorage.setItem('finance_ai_access_token', token);
                }
                if (data.data.user) {
                    localStorage.setItem('finance_ai_user_info', JSON.stringify(data.data.user));
                }
            }
        } else {
            statusBadge.className = 'status-badge status-error';
            statusBadge.innerText = `HTTP ${res.status} Error — Xác thực OTP thất bại!`;
        }

        responseView.innerText = JSON.stringify(data, null, 2);
    } catch (err) {
        statusBadge.className = 'status-badge status-error';
        statusBadge.innerText = 'Lỗi kết nối Server!';
        responseView.innerText = err.toString();
    }
}

// Gửi lại mã xác thực OTP
async function resendUserOtp() {
    const email = document.getElementById('regEmail').value.trim();
    const statusBadge = document.getElementById('statusBadge');
    const responseView = document.getElementById('responseView');

    if (!email) {
        alert('Vui lòng nhập Email!');
        return;
    }

    statusBadge.className = 'status-badge status-idle';
    statusBadge.innerText = 'Đang yêu cầu gửi lại OTP...';

    try {
        const res = await fetch('/v1/auth/resend-verification', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email })
        });
        const data = await res.json();

        if (res.ok) {
            statusBadge.className = 'status-badge status-success';
            statusBadge.innerText = `HTTP ${res.status} OK — Đã gửi lại mã OTP!`;
        } else {
            statusBadge.className = 'status-badge status-error';
            statusBadge.innerText = `HTTP ${res.status} Error — Gửi lại OTP thất bại!`;
        }

        responseView.innerText = JSON.stringify(data, null, 2);
    } catch (err) {
        statusBadge.className = 'status-badge status-error';
        statusBadge.innerText = 'Lỗi kết nối Server!';
        responseView.innerText = err.toString();
    }
}

// Gửi request đăng nhập bằng email và mật khẩu (POST /v1/auth/login)
async function loginWithEmailPassword() {
    const email = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value.trim();

    if (!email || !password) {
        alert('Vui lòng nhập đầy đủ Email và Mật khẩu!');
        return;
    }

    const statusBadge = document.getElementById('statusBadge');
    const responseView = document.getElementById('responseView');

    statusBadge.className = 'status-badge status-idle';
    statusBadge.innerText = 'Đang đăng nhập...';
    responseView.innerText = 'Sending POST /v1/auth/login ...';

    try {
        const res = await fetch('/v1/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ email, password })
        });

        const data = await res.json();

        if (res.ok) {
            statusBadge.className = 'status-badge status-success';
            statusBadge.innerText = `HTTP ${res.status} OK — Đăng nhập thành công!`;
            // lưu token và thông tin người dùng vào bộ nhớ trình duyệt
            if (data && data.data) {
                const token = data.data.token?.access || data.data.token?.accessToken || data.data.access_token || data.data.accessToken;
                if (token) {
                    localStorage.setItem('finance_ai_access_token', token);
                }
                if (data.data.user) {
                    localStorage.setItem('finance_ai_user_info', JSON.stringify(data.data.user));
                }
            }
        } else {
            statusBadge.className = 'status-badge status-error';
            statusBadge.innerText = `HTTP ${res.status} Error — Thất bại!`;
        }

        responseView.innerText = JSON.stringify(data, null, 2);
    } catch (err) {
        statusBadge.className = 'status-badge status-error';
        statusBadge.innerText = 'Lỗi kết nối tới server!';
        responseView.innerText = err.toString();
    }
}

// Gửi request tạo mật khẩu cho tài khoản đăng nhập Google thuần (POST /v1/users/me/password)
async function generatePasswordForGoogle() {
    const token = localStorage.getItem('finance_ai_access_token');
    if (!token) {
        alert('Vui lòng đăng nhập Google trước để có token thực hiện thao tác này!');
        return;
    }

    const statusBadge = document.getElementById('statusBadge');
    const responseView = document.getElementById('responseView');

    statusBadge.className = 'status-badge status-idle';
    statusBadge.innerText = 'Đang gửi yêu cầu tạo mật khẩu...';
    responseView.innerText = 'Sending POST /v1/users/me/password ...';

    try {
        const res = await fetch('/v1/users/me/password', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Accept': 'application/json'
            }
        });

        const data = await res.json();

        if (res.ok) {
            statusBadge.className = 'status-badge status-success';
            statusBadge.innerText = `HTTP ${res.status} OK — Tạo mật khẩu thành công!`;
            alert(data.msg || 'Mật khẩu đã được tạo và gửi về email của bạn.');
        } else {
            statusBadge.className = 'status-badge status-error';
            statusBadge.innerText = `HTTP ${res.status} Error — Thất bại!`;
        }

        responseView.innerText = JSON.stringify(data, null, 2);
    } catch (err) {
        statusBadge.className = 'status-badge status-error';
        statusBadge.innerText = 'Lỗi kết nối tới server!';
        responseView.innerText = err.toString();
    }
}
