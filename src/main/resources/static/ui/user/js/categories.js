// Trang Danh mục: đọc cây và kho biểu tượng (mọi người dùng); tạo/sửa/xoá cần quyền categories:manage (server trả 403 nếu thiếu).
// Chuyển từ category-test.js, dùng lớp gọi API dùng chung (api.js) thay cho phiên đăng nhập riêng.
const SVG_NS = 'http://www.w3.org/2000/svg';

let formMode = 'create'; // 'create' | 'edit'
let editingCode = null;
const expandedCodes = new Set(); // code các nút đang mở, giữ lại khi làm mới cây
const categoryByCode = new Map(); // code -> danh mục (từ lần tải gần nhất)
const iconByCode = new Map(); // code -> path_data, để xem trước biểu tượng
const codeByPath = new Map(); // path_data -> code, để dò ngược mã code từ path_data

// Tạo phần tử với class và nội dung chữ (textContent, không dùng innerHTML để tránh XSS)
function h(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

// quiet = true: không ghi vào API console khi tải tự động cây/biểu tượng
function callApi(path, method = 'GET', body = null, options = {}) {
    const { quiet = false, headers = {} } = options;
    return api(path, { method, body, headers, silent: quiet });
}

// Chuyển đổi linh hoạt icon (có thể là svg path string, icon code hoặc object) thành path_data
function resolvePathData(icon) {
    if (!icon) return '';
    if (typeof icon === 'object') return icon.path_data || icon.pathData || '';
    if (typeof icon === 'string') {
        const s = icon.trim();
        if (/^[Mm]\s*[-0-9]/.test(s)) return s;
        if (iconByCode.has(s)) return iconByCode.get(s);
        return s;
    }
    return '';
}

// Dò ngược icon_code từ object, mã code hoặc svg pathData
function resolveIconCode(icon) {
    if (!icon) return '';
    if (typeof icon === 'object') return icon.code || '';
    if (typeof icon === 'string') {
        const s = icon.trim();
        if (iconByCode.has(s)) return s;
        if (codeByPath.has(s)) return codeByPath.get(s);
        if (!/^[Mm]\s*[-0-9]/.test(s)) return s;
    }
    return '';
}

// Vẽ biểu tượng SVG 24x24 từ path_data hoặc icon code
function makeIcon(iconOrPath) {
    const box = h('span', 'icon-box');
    const pathData = resolvePathData(iconOrPath);
    if (!pathData) return box;
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '2');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    const path = document.createElementNS(SVG_NS, 'path');
    path.setAttribute('d', pathData);
    svg.appendChild(path);
    box.appendChild(svg);
    return box;
}

document.addEventListener('DOMContentLoaded', async () => {
    $('fIconCode').addEventListener('input', updateIconPreview);
    const groupSelect = $('iconGroup');
    if (groupSelect) groupSelect.addEventListener('change', () => loadIcons());

    try {
        await loadIcons(true);
    } catch (e) {
        console.error('Lỗi khi tải danh sách biểu tượng:', e);
    }
    await refreshTree();
});

// ----------------- CÂY DANH MỤC -----------------

async function refreshTree() {
    const root = $('treeRoot');
    const res = await callApi('/categories', 'GET', null, { quiet: true });
    if (!res.ok) {
        root.replaceChildren(h('div', 'muted', 'Không tải được cây danh mục (xem API console).'));
        return;
    }
    categoryByCode.clear();
    const list = h('ul');
    root.replaceChildren(list);
    const roots = res.data?.data || [];
    if (roots.length === 0) {
        root.replaceChildren(h('div', 'muted', 'Chưa có danh mục nào.'));
        return;
    }
    for (const category of roots) {
        await renderNode(category, list);
    }
}

async function renderNode(category, parentList) {
    categoryByCode.set(category.code, category);

    const item = h('li');
    const childList = h('ul');
    childList.hidden = true;

    const toggle = h('button', 'tree-toggle', '▸');
    toggle.disabled = category.is_leaf === true;
    toggle.onclick = () => toggleNode(category, toggle, childList);

    const swatch = h('span', 'swatch');
    swatch.style.background = category.color || '#999999';

    const actions = h('div', 'tree-actions');
    actions.append(
        actionButton('+ Con', () => startCreateChild(category.code)),
        actionButton('Sửa', () => startEdit(category)),
        actionButton('Xoá', () => prepareDelete(category.code)),
    );

    const row = h('div', 'tree-row');
    row.append(
        toggle,
        swatch,
        makeIcon(category.icon),
        h('span', 'tree-name', category.name),
        h('span', 'tree-code', category.code),
        h('span', 'badge', `L${category.level}`),
        h('span', category.is_leaf ? 'badge badge-leaf' : 'badge badge-branch', category.is_leaf ? 'LÁ' : 'CÓ CON'),
        actions,
    );
    if (category.description) row.title = category.description;

    item.append(row, childList);
    parentList.appendChild(item);

    if (!category.is_leaf && expandedCodes.has(category.code)) {
        await openNode(category, toggle, childList);
    }
}

function actionButton(label, handler) {
    const button = h('button', 'btn btn-secondary btn-sm', label);
    button.onclick = handler;
    return button;
}

async function toggleNode(category, toggle, childList) {
    if (childList.hidden) {
        await openNode(category, toggle, childList);
    } else {
        childList.hidden = true;
        toggle.textContent = '▸';
        expandedCodes.delete(category.code);
    }
}

async function openNode(category, toggle, childList) {
    const res = await callApi(`/categories/${encodeURIComponent(category.code)}/children`, 'GET', null, { quiet: true });
    if (!res.ok) return;
    childList.replaceChildren();
    for (const child of res.data?.data || []) {
        await renderNode(child, childList);
    }
    childList.hidden = false;
    toggle.textContent = '▾';
    expandedCodes.add(category.code);
}

// Mở mọi nút còn con: đánh dấu rồi vẽ lại, lặp tới khi không còn nút nào chưa mở
async function expandAll() {
    let added = true;
    while (added) {
        added = false;
        for (const [code, category] of categoryByCode) {
            if (!category.is_leaf && !expandedCodes.has(code)) {
                expandedCodes.add(code);
                added = true;
            }
        }
        if (added) await refreshTree();
    }
}

async function collapseAll() {
    expandedCodes.clear();
    await refreshTree();
}

// ----------------- FORM TẠO / SỬA -----------------

function updateIconPreview() {
    const code = $('fIconCode').value.trim();
    $('iconPreview').replaceChildren(...makeIcon(code).childNodes);
    document.querySelectorAll('.icon-item').forEach((item) => {
        item.classList.toggle('selected', item.dataset.code === code);
    });
}

function setFormMode(mode, code) {
    formMode = mode;
    editingCode = mode === 'edit' ? code : null;
    const isEdit = mode === 'edit';
    $('formTitle').textContent = isEdit ? `✏️ Sửa Danh Mục: ${code}` : '➕ Tạo Danh Mục';
    const badge = $('formModeBadge');
    badge.className = `api-badge ${isEdit ? 'patch' : 'post'}`
    badge.textContent = isEdit ? `PATCH /categories/${code}` : 'POST /categories';
    $('btnSubmit').textContent = isEdit ? 'Lưu thay đổi' : 'Tạo danh mục';
    $('fCode').disabled = isEdit;
    $('idemRow').style.display = isEdit ? 'none' : 'flex';
}

function resetForm() {
    setFormMode('create');
    $('fName').value = '';
    $('fCode').value = '';
    $('fDescription').value = '';
    $('fColor').value = '#3d6b7d';
    $('fParentCode').value = '';
    $('fIconCode').value = '';
    updateIconPreview();
}

function startCreateChild(parentCode) {
    resetForm();
    $('fParentCode').value = parentCode;
    $('formTitle').scrollIntoView({ behavior: 'smooth', block: 'center' });
    $('fName').focus();
}

function startEdit(category) {
    setFormMode('edit', category.code);
    $('fName').value = category.name || '';
    $('fCode').value = category.code;
    $('fDescription').value = category.description || '';
    $('fColor').value = category.color || '#3d6b7d';
    $('fParentCode').value = category.parent_code || '';
    $('fIconCode').value = resolveIconCode(category.icon);
    updateIconPreview();
    $('formTitle').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

// Bỏ các trường trống để body gọn, trường thiếu sẽ do server báo lỗi xác thực
function compact(body) {
    return Object.fromEntries(Object.entries(body).filter(([, value]) => value !== ''));
}

function newIdempotencyKey() {
    return typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `key-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function submitForm() {
    const fields = {
        name: $('fName').value.trim(),
        description: $('fDescription').value.trim(),
        icon_code: $('fIconCode').value.trim(),
        color: $('fColor').value,
        parent_code: $('fParentCode').value.trim(),
    };

    let res;
    if (formMode === 'create') {
        const body = compact({ ...fields, code: $('fCode').value.trim() });
        const headers = $('fIdempotency').checked ? { 'Idempotency-Key': newIdempotencyKey() } : {};
        res = await callApi('/categories', 'POST', body, { headers });
    } else {
        res = await callApi(`/categories/${encodeURIComponent(editingCode)}`, 'PATCH', compact(fields));
    }

    if (res.ok) {
        if (fields.parent_code) expandedCodes.add(fields.parent_code);
        resetForm();
        await refreshTree();
    }
}

// ----------------- XOÁ -----------------

function prepareDelete(code) {
    $('dCode').value = code;
    $('dReplacement').value = '';
    $('dCode').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

async function deleteCategory() {
    const code = $('dCode').value.trim();
    const replacement = $('dReplacement').value.trim();
    if (!code) {
        alert('Nhập mã danh mục cần xoá (hoặc bấm "Xoá" trên cây).');
        return;
    }
    if (!confirm(`Xoá "${code}" cùng TOÀN BỘ danh mục con cháu của nó?`)) return;

    const query = replacement ? `?${new URLSearchParams({ replacement_code: replacement })}` : '';
    const res = await callApi(`/categories/${encodeURIComponent(code)}${query}`, 'DELETE');
    if (res.ok) {
        $('dCode').value = '';
        $('dReplacement').value = '';
        await refreshTree();
    }
}

// ----------------- KHO BIỂU TƯỢNG -----------------

async function loadIcons(quiet = false) {
    const params = new URLSearchParams();
    params.set('size', '100'); // Lấy tối đa 100 biểu tượng
    const search = $('iconSearch').value.trim().toLowerCase();
    const query = `?${params.toString()}`;

    const res = await callApi(`/icons${query}`, 'GET', null, { quiet });
    const container = $('iconGrid');
    if (!res.ok) {
        container.replaceChildren(h('div', 'muted', 'Không tải được kho biểu tượng (HTTP ' + res.status + ') — xem API console.'));
        return;
    }

    const data = res.data?.data;
    // Backend trả về dạng Page/Slice: data.content hoặc mảng phẳng
    let items = Array.isArray(data?.content) ? data.content : (Array.isArray(data) ? data : null);

    if (items) {
        if (search) {
            items = items.filter(icon =>
                (icon.code && icon.code.toLowerCase().includes(search)) ||
                (icon.display_name && icon.display_name.toLowerCase().includes(search)) ||
                (icon.displayName && icon.displayName.toLowerCase().includes(search))
            );
        }
        $('iconTotal').textContent = `(${items.length} biểu tượng)`;
        container.replaceChildren();
        const list = h('div', 'icon-list');
        for (const icon of items) {
            const path = icon.path_data || icon.pathData || '';
            iconByCode.set(icon.code, path);
            if (path) codeByPath.set(path, icon.code);

            const button = h('button', 'icon-item');
            button.type = 'button';
            button.dataset.code = icon.code;
            button.title = icon.code;
            button.append(makeIcon(path), h('span', '', icon.display_name || icon.displayName || icon.code));
            button.onclick = () => {
                $('fIconCode').value = icon.code;
                updateIconPreview();
            };
            list.appendChild(button);
        }
        container.appendChild(list);
    } else if (Array.isArray(data?.group)) {
        // Hỗ trợ trường hợp nếu backend trả về theo nhóm
        $('iconTotal').textContent = `(${data.total ?? 0} biểu tượng)`;
        container.replaceChildren();
        for (const group of data.group) {
            container.appendChild(h('div', 'icon-group-title', `${group.group_name || group.groupName} (${group.items?.length || 0})`));
            const list = h('div', 'icon-list');
            for (const icon of group.items || []) {
                const path = icon.path_data || icon.pathData || '';
                iconByCode.set(icon.code, path);
                if (path) codeByPath.set(path, icon.code);

                const button = h('button', 'icon-item');
                button.type = 'button';
                button.dataset.code = icon.code;
                button.title = icon.code;
                button.append(makeIcon(path), h('span', '', icon.display_name || icon.displayName || icon.code));
                button.onclick = () => {
                    $('fIconCode').value = icon.code;
                    updateIconPreview();
                };
                list.appendChild(button);
            }
            container.appendChild(list);
        }
    } else {
        container.replaceChildren(h('div', 'muted', 'Chưa có biểu tượng nào.'));
    }
    updateIconPreview();
}
