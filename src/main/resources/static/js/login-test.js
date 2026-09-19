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
