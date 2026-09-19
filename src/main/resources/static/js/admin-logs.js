const API_BASE = '/v1';
let eventSource = null;
let logsBuffer = [];
let isPaused = false;
let errorCount = 0;
let warnCount = 0;
let infoCount = 0;

window.addEventListener('DOMContentLoaded', () => {
    checkEnvironment();
    const savedToken = localStorage.getItem('admin_jwt_token');
    if (savedToken) {
        document.getElementById('userInfo').innerText = 'Admin (Đã lưu token)';
        connectStream(savedToken);
    } else {
        openAuthModal();
    }
});

window.addEventListener('beforeunload', () => {
    disconnectStream();
});

function checkEnvironment() {
    if (window.location.protocol === 'file:' || (window.location.port && window.location.port !== '8080')) {
        showError('⚠️ Bạn đang mở trang từ cổng ' + (window.location.port || 'file') + ' (Preview IDE). Hãy mở trên trình duyệt tại http://localhost:8080/v1/pages/admin-logs.html để kết nối tới server Spring Boot.');
    }
}

function showError(msg) {
    const errBox = document.getElementById('authError');
    if (errBox) {
        errBox.innerText = msg;
        errBox.style.display = 'block';
    } else {
        alert(msg);
    }
}

function clearError() {
    const errBox = document.getElementById('authError');
    if (errBox) {
        errBox.innerText = '';
        errBox.style.display = 'none';
    }
}

function openAuthModal() {
    document.getElementById('authModal').style.display = 'flex';
    checkEnvironment();
}

function closeAuthModal() {
    document.getElementById('authModal').style.display = 'none';
    clearError();
}

async function handleLogin() {
    clearError();
    const rawToken = document.getElementById('rawToken').value.trim();
    if (rawToken) {
        localStorage.setItem('admin_jwt_token', rawToken);
        closeAuthModal();
        connectStream(rawToken);
        return;
    }

    const email = document.getElementById('adminEmail').value.trim();
    const password = document.getElementById('adminPassword').value;

    if (!email || !password) {
        showError('Vui lòng nhập đầy đủ Email và Mật khẩu.');
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        const data = await res.json();
        if (!res.ok || !data.success) {
            showError('Đăng nhập thất bại: ' + (data.message || 'Sai thông tin tài khoản'));
            return;
        }

        const token = data.data.access_token;
        localStorage.setItem('admin_jwt_token', token);
        document.getElementById('userInfo').innerText = `Admin: ${email}`;
        closeAuthModal();
        connectStream(token);
    } catch (err) {
        if (window.location.port && window.location.port !== '8080') {
            showError('Không thể kết nối đến máy chủ. Bạn đang chạy trên cổng ' + window.location.port + ' (Preview IDE). Vui lòng mở trang tại: http://localhost:8080/v1/pages/admin-logs.html');
        } else {
            showError('Lỗi kết nối máy chủ: ' + err.message);
        }
    }
}

function connectStream(token) {
    disconnectStream();

    updateStatus(false, 'Đang kết nối...');

    const streamUrl = `${API_BASE}/admin/logs/stream?token=${encodeURIComponent(token)}`;
    eventSource = new EventSource(streamUrl);

    eventSource.addEventListener('connected', (e) => {
        updateStatus(true, 'Đang stream realtime');
        document.getElementById('btnAuth').style.display = 'none';
        document.getElementById('btnDisconnect').style.display = 'inline-block';
    });

    eventSource.addEventListener('log', (e) => {
        if (isPaused) return;
        try {
            const logEvent = JSON.parse(e.data);
            appendLog(logEvent);
        } catch (err) {
            console.error('Lỗi parse log:', err);
        }
    });

    eventSource.onerror = (err) => {
        updateStatus(false, 'Mất kết nối hoặc Token không hợp lệ / Hết hạn (401/403)');
        disconnectStream();
    };
}

function disconnectStream() {
    if (eventSource) {
        eventSource.close();
        eventSource = null;
    }
    updateStatus(false, 'Đã ngắt kết nối');
    document.getElementById('btnAuth').style.display = 'inline-block';
    document.getElementById('btnDisconnect').style.display = 'none';
}

function updateStatus(connected, text) {
    const dot = document.getElementById('statusDot');
    const txt = document.getElementById('statusText');
    if (connected) {
        dot.classList.add('connected');
    } else {
        dot.classList.remove('connected');
    }
    txt.innerText = text;
}

let isAutoScroll = true;
let unreadLogsCount = 0;
let activeMetricFilter = null; // 'ERROR', 'WARN', '4xx', '5xx'

window.addEventListener('DOMContentLoaded', () => {
    checkEnvironment();
    setupSmartScroll();
    setInterval(update5MinMetrics, 3000);

    const savedToken = localStorage.getItem('admin_jwt_token');
    if (savedToken) {
        document.getElementById('userInfo').innerText = 'Admin (Đã lưu token)';
        connectStream(savedToken);
    } else {
        openAuthModal();
    }
});

function setupSmartScroll() {
    const wrapper = document.getElementById('logTableWrapper');
    if (!wrapper) return;
    wrapper.addEventListener('scroll', () => {
        const distance = wrapper.scrollHeight - wrapper.scrollTop - wrapper.clientHeight;
        if (distance < 50) {
            isAutoScroll = true;
            unreadLogsCount = 0;
            const btn = document.getElementById('smartScrollBtn');
            if (btn) btn.style.display = 'none';
        } else {
            isAutoScroll = false;
        }
    });
}

function scrollDownToBottom() {
    const wrapper = document.getElementById('logTableWrapper');
    if (!wrapper) return;
    wrapper.scrollTo({ top: wrapper.scrollHeight, behavior: 'smooth' });
    isAutoScroll = true;
    unreadLogsCount = 0;
    const btn = document.getElementById('smartScrollBtn');
    if (btn) btn.style.display = 'none';
}

function appendLog(logEvent) {
    logEvent._receivedAt = Date.now();
    logEvent._http = parseHttpLog(logEvent.message);

    // Chuẩn hóa dữ liệu từ backend hoặc parser
    if (logEvent._http) {
        if (!logEvent.api) logEvent.api = logEvent._http.method + ' ' + logEvent._http.path;
        if (logEvent.status == null) logEvent.status = logEvent._http.status;
        if (logEvent.durationMs == null) logEvent.durationMs = logEvent._http.time;
        if (!logEvent.requestId && logEvent._http.reqId) logEvent.requestId = logEvent._http.reqId;
        if (!logEvent.userId && logEvent._http.user && logEvent._http.user !== 'anonymous') logEvent.userId = logEvent._http.user;
    }

    logsBuffer.push(logEvent);
    if (logsBuffer.length > 1000) {
        logsBuffer.shift();
    }

    update5MinMetrics();

    if (matchFilter(logEvent)) {
        renderRow(logEvent, true);
    }
}

function update5MinMetrics() {
    const now = Date.now();
    const fiveMinAgo = now - 5 * 60 * 1000;

    let errCount = 0;
    let warnCount = 0;
    let fourXxCount = 0;
    let fiveXxCount = 0;
    let totalCount = 0;
    const latencies = [];

    for (let i = logsBuffer.length - 1; i >= 0; i--) {
        const item = logsBuffer[i];
        if (item._receivedAt && item._receivedAt < fiveMinAgo) {
            break;
        }
        totalCount++;
        if (item.level === 'ERROR') errCount++;
        if (item.level === 'WARN') warnCount++;

        const status = item.status != null ? item.status : (item._http ? item._http.status : null);
        const duration = item.durationMs != null ? item.durationMs : (item._http ? item._http.time : null);

        if (status != null) {
            if (status >= 400 && status < 500) fourXxCount++;
            if (status >= 500) fiveXxCount++;
        }
        if (duration != null && !isNaN(duration)) {
            latencies.push(duration);
        }
    }

    let p95 = 0;
    if (latencies.length > 0) {
        latencies.sort((a, b) => a - b);
        const p95Idx = Math.min(latencies.length - 1, Math.floor(latencies.length * 0.95));
        p95 = latencies[p95Idx];
    }

    const elErr = document.getElementById('mError');
    if (elErr) elErr.innerText = errCount;
    const elWarn = document.getElementById('mWarn');
    if (elWarn) elWarn.innerText = warnCount;
    const el4xx = document.getElementById('m4xx');
    if (el4xx) el4xx.innerText = fourXxCount;
    const el5xx = document.getElementById('m5xx');
    if (el5xx) el5xx.innerText = fiveXxCount;
    const elP95 = document.getElementById('mP95');
    if (elP95) elP95.innerText = p95 + 'ms';
    const elTotal = document.getElementById('mTotal');
    if (elTotal) elTotal.innerText = totalCount;
}

function parseHttpLog(msg) {
    if (!msg) return null;
    if (!msg.startsWith('<-- ') && !msg.startsWith('--> ')) return null;

    const isOutgoing = msg.startsWith('<-- ');
    const firstPart = msg.split(/\s*\|\s*/)[0] || '';
    const firstTokens = firstPart.trim().split(/\s+/);
    const method = firstTokens[1] || '';
    let rawPath = firstTokens[2] || '';
    // Xóa query param dài lê thê (như ?token=...) khỏi cột api
    const qIdx = rawPath.indexOf('?');
    const cleanPath = qIdx !== -1 ? rawPath.substring(0, qIdx) : rawPath;

    const statusMatch = msg.match(/status=(\d+)/);
    const timeMatch = msg.match(/time=(\d+)ms/);
    const userMatch = msg.match(/user=([^|\s]+)/);
    const reqIdMatch = msg.match(/reqId=([^|\s\]]+)/);
    const ipMatch = msg.match(/ip=([^,\]\s]+)/);

    let detail = '-';
    const reqIdx = msg.indexOf('reqId=');
    if (reqIdx !== -1) {
        const afterReq = msg.substring(reqIdx);
        const pipeIdx = afterReq.indexOf('|');
        if (pipeIdx !== -1) {
            const rawDetail = afterReq.substring(pipeIdx + 1).trim();
            if (rawDetail) detail = rawDetail;
        }
    }

    return {
        type: 'HTTP',
        direction: isOutgoing ? 'OUT' : 'IN',
        method: method,
        path: cleanPath,
        rawPath: rawPath,
        status: statusMatch ? parseInt(statusMatch[1], 10) : null,
        time: timeMatch ? parseInt(timeMatch[1], 10) : null,
        user: userMatch ? userMatch[1].trim() : '',
        reqId: reqIdMatch ? reqIdMatch[1].trim() : '',
        ip: ipMatch ? ipMatch[1].trim() : '',
        detail: detail
    };
}

function isStaticPath(path) {
    if (!path) return false;
    const lower = path.toLowerCase();
    return lower.endsWith('.css') || lower.endsWith('.js') || lower.endsWith('.html')
        || lower.endsWith('.ico') || lower.endsWith('.png') || lower.endsWith('.jpg')
        || lower.endsWith('.map') || lower.includes('/static/') || lower.includes('/pages/')
        || lower.includes('/css/') || lower.includes('/js/');
}

function matchFilter(log) {
    const status = log.status != null ? log.status : (log._http ? log._http.status : null);

    // 1. Metric filter (khi bấm vào chip dải chỉ số 5 phút)
    if (activeMetricFilter === 'ERROR' && log.level !== 'ERROR') return false;
    if (activeMetricFilter === 'WARN' && log.level !== 'WARN') return false;
    if (activeMetricFilter === '4xx') {
        if (status == null || status < 400 || status >= 500) return false;
    }
    if (activeMetricFilter === '5xx') {
        if (status == null || status < 500) return false;
    }

    // 2. Level filter
    const filterLevel = document.getElementById('levelFilter').value;
    if (filterLevel === 'ERROR' && log.level !== 'ERROR') return false;
    if (filterLevel === 'WARN' && log.level !== 'WARN' && log.level !== 'ERROR') return false;
    if (filterLevel === 'INFO' && log.level === 'DEBUG') return false;

    // 3. Source filter
    const filterSourceEl = document.getElementById('sourceFilter');
    const filterSource = filterSourceEl ? filterSourceEl.value : 'ALL';
    const logSource = log.source || (log._http ? 'HTTP' : 'App');
    if (filterSource !== 'ALL' && logSource !== filterSource) return false;

    // 4. Hide static asset requests
    const chkHideStatic = document.getElementById('chkHideStatic');
    if (chkHideStatic && chkHideStatic.checked) {
        const msg = (log.message || '').toLowerCase();
        const logger = (log.logger || '').toLowerCase();
        const apiPath = log.api || (log._http ? log._http.path : '');
        if (apiPath && isStaticPath(apiPath)) return false;
        if (msg.includes('/static/') || msg.includes('.css') || msg.includes('.js') || msg.includes('.html') || msg.includes('/favicon.ico')) return false;
        if (logger.includes('welcomepagehandlermapping')) return false;
    }

    // 5. Search text (path, api, reqId, user, message, logger, thread, status)
    const search = document.getElementById('searchInput').value.trim().toLowerCase();
    if (search) {
        const reqId = (log.requestId || '').toLowerCase();
        const userId = (log.userId || '').toLowerCase();
        const api = (log.api || '').toLowerCase();
        const msg = (log.message || '').toLowerCase();
        const logger = (log.logger || '').toLowerCase();
        const thread = (log.thread || '').toLowerCase();
        const detail = (log._http && log._http.detail ? log._http.detail : '').toLowerCase();

        if (!reqId.includes(search) && !userId.includes(search) && !api.includes(search)
                && !msg.includes(search) && !logger.includes(search) && !thread.includes(search)
                && !detail.includes(search) && !statusStr.includes(search)) {
            return false;
        }
    }

    return true;
}

function renderRow(log, isNewArrival) {
    const tbody = document.getElementById('logTableBody');
    if (!tbody) return;

    const row = document.createElement('tr');
    row.className = 'log-row';
    row.id = `row-${log.id}`;

    const timeOnly = log.timestamp && log.timestamp.includes(' ')
        ? log.timestamp.split(' ')[1]
        : (log.timestamp || '');
    const logSource = log.source || (log._http ? 'HTTP' : 'App');
    const reqId = log.requestId || (log._http ? log._http.reqId : '') || '';
    const userId = log.userId || (log._http ? log._http.user : '') || '';
    let api = log.api || (log._http ? `${log._http.method} ${log._http.path}` : '');
    if (api && api.includes('?')) {
        api = api.substring(0, api.indexOf('?'));
    }
    const status = log.status != null ? log.status : (log._http ? log._http.status : null);
    const duration = log.durationMs != null ? log.durationMs : (log._http ? log._http.time : null);

    // 1. reqId cell
    let reqCellHtml = '';
    if (reqId) {
        const shortReqId = reqId.length > 12 ? reqId.substring(0, 8) + '…' : reqId;
        reqCellHtml = `
            <div class="cell-req-wrapper">
                <span class="req-id-pill" title="reqId: ${escapeHtml(reqId)} (Click để copy)" onclick="copyText('${escapeHtml(reqId)}', this, event)">${escapeHtml(shortReqId)}</span>
                <span class="req-time-sub">${escapeHtml(timeOnly)}</span>
            </div>
        `;
    } else {
        reqCellHtml = `
            <div class="cell-req-wrapper">
                <span style="color: var(--text-muted);">—</span>
                <span class="req-time-sub">${escapeHtml(timeOnly)}</span>
            </div>
        `;
    }

    // 2. type cell
    const typeCellHtml = `<span class="log-level ${log.level}">${log.level}</span>`;

    // 3. source cell
    const sourceCellHtml = `<span class="badge-tag tag-${logSource}">${logSource}</span>`;

    // 4. api cell
    let apiCellHtml = '<span style="color: var(--text-muted);">—</span>';
    if (api) {
        const parts = api.trim().split(/\s+/);
        const method = parts[0] || '';
        const path = parts.slice(1).join(' ') || '';
        apiCellHtml = `
            <div class="api-cell" title="${escapeHtml(api)}">
                <span class="http-method ${escapeHtml(method)}">${escapeHtml(method)}</span>
                <span class="api-path">${escapeHtml(path)}</span>
            </div>
        `;
    }

    // 5. user cell
    let userCellHtml = '<span style="color: var(--text-muted);">—</span>';
    const effectiveUser = userId || (log._http ? log._http.user : '');
    if (effectiveUser) {
        if (effectiveUser === 'anon' || effectiveUser === 'anonymous') {
            userCellHtml = `<span class="badge-user-anon">anon</span>`;
        } else {
            const shortUser = effectiveUser.length > 10 ? effectiveUser.substring(0, 8) + '…' : effectiveUser;
            userCellHtml = `<span class="badge-user-id" title="${escapeHtml(effectiveUser)}">${escapeHtml(shortUser)}</span>`;
        }
    }

    // 6. meslog cell: hiển thị chi tiết lỗi hoặc '-' nếu thành công
    let meslogText = '';
    if (log._http) {
        const detail = log._http.detail || '-';
        if (detail === '-') {
            meslogText = `<span style="color: var(--text-muted);">-</span>`;
        } else {
            meslogText = `<span class="meslog-error-text">${escapeHtml(detail)}</span>`;
        }
    } else {
        let msg = log.message || '';
        if (msg.startsWith('--> ') || msg.startsWith('<-- ')) {
            // Lược bỏ phần method + path đã có ở cột API
            const stripped = msg.replace(/^(?:-->|<--)\s+[A-Z]+\s+[^\s|\[]+\s*(?:\[|\|)?/, '').replace(/\]$/, '').trim();
            meslogText = escapeHtml(stripped || 'HTTP request');
        } else {
            meslogText = escapeHtml(msg);
        }
    }
    const meslogCellHtml = `<div class="meslog-text">${meslogText}</div>`;

    // 7. status cell
    let statusCellHtml = '<span style="color: var(--text-muted);">—</span>';
    if (status != null) {
        const statusGroup = 's' + Math.floor(status / 100) + 'xx';
        statusCellHtml = `<span class="http-status ${statusGroup}">${status}</span>`;
    }

    // 8. time cell (thời gian chạy)
    let timeCellHtml = '<span style="color: var(--text-muted);">—</span>';
    if (duration != null) {
        const latencyClass = duration > 1000 ? 'very-slow' : (duration > 300 ? 'slow' : '');
        timeCellHtml = `<span class="http-latency ${latencyClass}">${duration}ms</span>`;
    }
    row.innerHTML = `
        <td class="td-expand"><span class="log-chevron">▸</span></td>
        <td class="td-req">${reqCellHtml}</td>
        <td class="td-type">${typeCellHtml}</td>
        <td class="td-source">${sourceCellHtml}</td>
        <td class="td-api">${apiCellHtml}</td>
        <td class="td-user">${userCellHtml}</td>
        <td class="td-meslog">${meslogCellHtml}</td>
        <td class="td-status">${statusCellHtml}</td>
        <td class="td-time">${timeCellHtml}</td>
    `;

    // Row chi tiết mở rộng
    const detailRow = document.createElement('tr');
    detailRow.className = 'log-detail-row';
    detailRow.id = `detail-${log.id}`;
    detailRow.style.display = 'none';

    let stackTraceHtml = '';
    if (log.stackTrace) {
        stackTraceHtml = `
            <div style="margin-top: 8px;">
                <div style="font-weight: 700; color: #f85149; margin-bottom: 4px; display: flex; align-items: center; gap: 8px;">
                    <span>⚠️ Stack trace:</span>
                    <button class="btn-pill" onclick="copyText('${escapeJsString(log.stackTrace)}', this, event)">📋 Copy Trace</button>
                </div>
                <div class="stack-trace-box">${escapeHtml(log.stackTrace)}</div>
            </div>
        `;
    }

    detailRow.innerHTML = `
        <td colspan="9">
            <div class="detail-grid">
                <div class="detail-item">logger: <strong>${escapeHtml(log.logger || '—')}</strong></div>
                <div class="detail-item">thread: <strong>${escapeHtml(log.thread || '—')}</strong></div>
                <div class="detail-item">thời gian: <strong>${escapeHtml(log.timestamp || '—')}</strong></div>
                ${api ? `<div class="detail-item">api: <strong>${escapeHtml(api)}</strong></div>` : ''}
                ${effectiveUser ? `<div class="detail-item">user: <code>${escapeHtml(effectiveUser)}</code> <button class="btn-pill" onclick="copyText('${escapeHtml(effectiveUser)}', this, event)">📋 Copy</button> <button class="btn-pill" onclick="filterByUser('${escapeHtml(effectiveUser)}', event)">🔍 Lọc user</button></div>` : ''}
                ${reqId ? `<div class="detail-item">reqId: <code>${escapeHtml(reqId)}</code> <button class="btn-pill" onclick="copyText('${escapeHtml(reqId)}', this, event)">📋 Copy</button> <button class="btn-pill" onclick="filterByReqId('${escapeHtml(reqId)}', event)">🔍 Lọc reqId</button></div>` : ''}
            </div>
            ${log.message ? `<div class="detail-full-message"><strong>Nội dung log:</strong> ${escapeHtml(log.message)}</div>` : ''}
            ${stackTraceHtml}
        </td>
    `;

    row.addEventListener('click', (e) => {
        if (e.target.closest('button') || e.target.closest('.req-id-pill')) return;
        toggleRowDetail(log.id);
    });

    tbody.appendChild(row);
    tbody.appendChild(detailRow);

    // Giới hạn số lượng DOM nodes để giữ mượt
    if (tbody.children.length > 2000) {
        tbody.removeChild(tbody.firstElementChild);
        tbody.removeChild(tbody.firstElementChild);
    }

    const wrapper = document.getElementById('logTableWrapper');
    if (isNewArrival && !isAutoScroll) {
        unreadLogsCount++;
        const btn = document.getElementById('smartScrollBtn');
        const countSpan = document.getElementById('newLogsCount');
        if (btn && countSpan) {
            countSpan.innerText = unreadLogsCount;
            btn.style.display = 'block';
        }
    } else if (isAutoScroll && wrapper) {
        wrapper.scrollTop = wrapper.scrollHeight;
    }
}

function toggleRowDetail(id) {
    const row = document.getElementById(`row-${id}`);
    const detail = document.getElementById(`detail-${id}`);
    if (!row || !detail) return;
    const isExpanded = detail.style.display !== 'none';
    if (isExpanded) {
        detail.style.display = 'none';
        row.classList.remove('expanded');
    } else {
        detail.style.display = 'table-row';
        row.classList.add('expanded');
    }
}

function copyText(text, btn, event) {
    if (event) event.stopPropagation();
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
        if (!btn) return;
        const orig = btn.innerText;
        btn.innerText = '✓ Đã chép';
        setTimeout(() => { btn.innerText = orig; }, 1500);
    }).catch(err => {
        console.error('Không thể copy:', err);
    });
}

function escapeJsString(text) {
    if (!text) return '';
    return text
        .replace(/\\/g, '\\\\')
        .replace(/'/g, "\\'")
        .replace(/"/g, '\\"')
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '');
}

function filterByReqId(reqId, event) {
    if (event) event.stopPropagation();
    const searchInput = document.getElementById('searchInput');
    searchInput.value = reqId;
    updateActiveFilterBadge(`reqId: ${reqId}`);
    renderLogs();
}

function filterByUser(userId, event) {
    if (event) event.stopPropagation();
    const searchInput = document.getElementById('searchInput');
    searchInput.value = userId;
    updateActiveFilterBadge(`user: ${userId}`);
    renderLogs();
}

function applyMetricFilter(type) {
    if (activeMetricFilter === type || type === 'ALL') {
        activeMetricFilter = null;
        updateActiveFilterBadge(null);
    } else {
        activeMetricFilter = type;
        updateActiveFilterBadge(`5 phút qua: ${type}`);
    }
    renderLogs();
}

function updateActiveFilterBadge(label) {
    const badge = document.getElementById('activeFilterBadge');
    const text = document.getElementById('activeFilterText');
    const clearBtn = document.getElementById('btnClearSearch');
    const searchInput = document.getElementById('searchInput');

    if (clearBtn) {
        clearBtn.style.display = searchInput.value.trim() ? 'block' : 'none';
    }

    if (label || (searchInput && searchInput.value.trim())) {
        if (badge && text) {
            text.innerText = label || `Tìm: "${searchInput.value.trim()}"`;
            badge.style.display = 'inline-flex';
        }
    } else {
        if (badge) badge.style.display = 'none';
    }
}

function clearSearch() {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) searchInput.value = '';
    updateActiveFilterBadge(activeMetricFilter ? `5 phút qua: ${activeMetricFilter}` : null);
    renderLogs();
}

function resetAllFilters(event) {
    if (event) event.preventDefault();
    activeMetricFilter = null;
    document.getElementById('levelFilter').value = 'ALL';
    if (document.getElementById('sourceFilter')) document.getElementById('sourceFilter').value = 'ALL';
    const searchInput = document.getElementById('searchInput');
    if (searchInput) searchInput.value = '';
    updateActiveFilterBadge(null);
    renderLogs();
}

function renderLogs() {
    const tbody = document.getElementById('logTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    const searchInput = document.getElementById('searchInput');
    const clearBtn = document.getElementById('btnClearSearch');
    if (clearBtn && searchInput) {
        clearBtn.style.display = searchInput.value.trim() ? 'block' : 'none';
    }

    for (const log of logsBuffer) {
        if (matchFilter(log)) {
            renderRow(log, false);
        }
    }

    const wrapper = document.getElementById('logTableWrapper');
    if (isAutoScroll && wrapper) {
        wrapper.scrollTop = wrapper.scrollHeight;
    }
}

function clearLogs() {
    logsBuffer = [];
    update5MinMetrics();
    const tbody = document.getElementById('logTableBody');
    if (tbody) tbody.innerHTML = '';
}

function togglePause() {
    isPaused = !isPaused;
    const btn = document.getElementById('btnPause');
    btn.innerText = isPaused ? '▶️ Tiếp tục' : '⏸️ Tạm dừng';
}

function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
