(async function () {
    const [u, r] = await Promise.all([api('/admin/user/all'), api('/admin/role/all')]);
    const users = dataOf(u) || [];
    const roles = dataOf(r) || [];
    const isAdmin = (x) => (x.roles || []).some(role => role.name === 'ROLE_ADMIN');

    $('sUsers').textContent = users.length;
    $('sActive').textContent = users.filter(x => x.status === 'ACTIVE').length;
    $('sBlocked').textContent = users.filter(x => x.status === 'BLOCKED').length;
    $('sAdmins').textContent = users.filter(isAdmin).length;

    setTable($('userBody'), users.slice(-6).reverse().map(x =>
        `<tr><td>${esc(x.email)}</td><td>${badge(x.status)}</td><td>${(x.roles || []).map(role => badge(role.name, 'purple')).join(' ')}</td></tr>`).join(''), 3);
    setTable($('roleBody'), roles.map(x =>
        `<tr><td>${badge(x.name, 'purple')}</td><td>${esc(x.level)}</td><td>${(x.permissions || []).length}</td></tr>`).join(''), 3);
})();
