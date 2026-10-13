// Lớp gọi API và quản lý phiên dùng chung cho mọi trang trong /ui.
// Context path được suy ra từ URL trang (phần đứng trước /ui/), nên đổi API_PATH không phải sửa lại.
const API_BASE = location.pathname.split('/ui/')[0];
const UI_ROOT = API_BASE + '/ui/';

const KEY_ACCESS = 'finance_ai_access_token';
const KEY_REFRESH = 'finance_ai_refresh_token';
const KEY_USER = 'finance_ai_user_info';

const Auth = {
    token() {
        return localStorage.getItem(KEY_ACCESS) || '';
    },
    refreshToken() {
        return localStorage.getItem(KEY_REFRESH) || '';
    },
    user() {
        try {
            return JSON.parse(localStorage.getItem(KEY_USER) || 'null');
        } catch (e) {
            return null;
        }
    },
    // Lưu kết quả AuthRes { user, token: { access, refresh } } của login / verify-email / google / refresh
    save(authRes) {
        const token = authRes && authRes.token;
        const access = token && (token.access || token.access_token);
        if (!access) return false;
        localStorage.setItem(KEY_ACCESS, access);
        if (token.refresh) localStorage.setItem(KEY_REFRESH, token.refresh);
        if (authRes.user) localStorage.setItem(KEY_USER, JSON.stringify(authRes.user));
        return true;
    },
    saveRawAccess(access) {
        localStorage.setItem(KEY_ACCESS, access);
        localStorage.removeItem(KEY_REFRESH);
        localStorage.removeItem(KEY_USER);
    },
    clear() {
        [KEY_ACCESS, KEY_REFRESH, KEY_USER].forEach(k => localStorage.removeItem(k));
    },
    // Giải payload JWT (chỉ để ẩn/hiện menu, quyền thật do server quyết định)
    claims() {
        const t = this.token();
        if (!t) return null;
        try {
            const part = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
            return JSON.parse(decodeURIComponent(escape(atob(part))));
        } catch (e) {
            return null;
        }
    },
    userId() {
        const c = this.claims();
        return (c && c.sub) || (this.user() && this.user().id) || null;
    },
    authorities() {
        const c = this.claims();
        return (c && c.authorities) || [];
    },
    isAdmin() {
        return this.authorities().some(a => a === 'ROLE_ADMIN' || a === 'ADMIN');
    },
    displayName() {
        const u = this.user();
        if (!u) return 'Người dùng';
        const full = [u.first_name, u.last_name].filter(Boolean).join(' ');
        return full || u.email || 'Người dùng';
    },
    home() {
        return UI_ROOT + (this.isAdmin() ? 'admin/dashboard.html' : 'user/dashboard.html');
    },
    // Gọi sau khi đăng nhập thành công: lưu phiên rồi chuyển về trang chủ theo vai trò
    startSession(authRes) {
        if (!this.save(authRes)) return false;
        location.href = this.home();
        return true;
    },
    async logout() {
        const refresh = this.refreshToken();
        if (refresh) {
            await api('/auth/logout', { method: 'POST', body: { refresh_token: refresh, logout_all_devices: false }, silent: true });
        }
        this.clear();
        location.href = UI_ROOT + 'login.html';
    }
};

let refreshing = null;

// Làm mới access token bằng refresh token; trả về true nếu thành công
async function tryRefresh() {
    const refresh = Auth.refreshToken();
    if (!refresh) return false;
    if (!refreshing) {
        refreshing = fetch(API_BASE + '/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify({ refresh_token: refresh })
        }).then(r => r.json().catch(() => null).then(d => ({ ok: r.ok, d })))
            .then(({ ok, d }) => ok && d && d.data && Auth.save(d.data))
            .catch(() => false)
            .finally(() => { refreshing = null; });
    }
    return refreshing;
}

function toQuery(query) {
    if (!query) return '';
    const p = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
        if (v !== undefined && v !== null && v !== '') p.append(k, v);
    });
    const s = p.toString();
    return s ? '?' + s : '';
}

// Gọi API. Trả về { ok, status, data, error } — data là JSON đã parse (ApiResponse hoặc ErrorResponse).
// options: method, body, query, headers, silent (không ghi vào khung API console, không toast lỗi)
async function api(path, options = {}) {
    const { method = 'GET', body = null, query = null, headers: extra = {}, silent = false, retried = false } = options;
    const url = API_BASE + path + toQuery(query);
    const headers = { Accept: 'application/json', ...extra };
    const token = Auth.token();
    if (token) headers.Authorization = 'Bearer ' + token;
    if (body !== null && body !== undefined) headers['Content-Type'] = 'application/json';

    const started = performance.now();
    let result;
    try {
        const res = await fetch(url, {
            method,
            headers,
            body: body !== null && body !== undefined ? JSON.stringify(body) : undefined
        });
        const text = await res.text();
        let data = null;
        try {
            data = text ? JSON.parse(text) : null;
        } catch (e) {
            data = text;
        }

        if (res.status === 401 && !retried && Auth.refreshToken() && !path.startsWith('/auth/')) {
            if (await tryRefresh()) return api(path, { ...options, retried: true });
        }
        result = { ok: res.ok, status: res.status, data, ms: Math.round(performance.now() - started) };
    } catch (err) {
        result = { ok: false, status: 0, data: null, error: err, ms: Math.round(performance.now() - started) };
    }

    if (!silent) {
        window.dispatchEvent(new CustomEvent('api-call', { detail: { method, url, body, result } }));
        if (!result.ok) {
            if (result.status === 401 && !path.startsWith('/auth/')) {
                Auth.clear();
                location.href = UI_ROOT + 'login.html';
            } else if (typeof toast === 'function') {
                toast(errMsg(result), 'error');
            }
        }
    }
    return result;
}

// Lấy thông điệp lỗi dễ đọc từ ErrorResponse { error: { code, message, fields } }
function errMsg(res) {
    if (res.error) return 'Không kết nối được server: ' + res.error.message;
    const e = res.data && res.data.error;
    if (e) {
        const fields = (e.fields || []).map(f => `${f.field}: ${f.message}`).join('; ');
        return `[${e.code}] ${e.message || ''}${fields ? ' — ' + fields : ''}`;
    }
    return `HTTP ${res.status}`;
}

// Phần data của ApiResponse (hoặc undefined nếu lỗi)
function dataOf(res) {
    return res.ok && res.data ? res.data.data : undefined;
}
