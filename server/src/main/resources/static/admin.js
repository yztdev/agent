/* Tokens are held in page memory, never in browser storage. */
let token = '';
let offset = 0;
const element = id => document.getElementById(id);

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }
    });
    if (!response.ok) throw new Error('请求失败：' + response.status);
    const body = await response.text();
    return body ? JSON.parse(body) : null;
}

function showError(error) { element('error').textContent = error.message; }
function appendCell(row, value) {
    const cell = document.createElement('td');
    cell.textContent = value ?? '';
    row.append(cell);
}

async function loadUsers() {
    try {
        element('error').textContent = '';
        const query = '?offset=' + offset + '&search=' + encodeURIComponent(element('search').value);
        const users = await api('/api/admin/users' + query);
        element('users').replaceChildren();
        for (const user of users) {
            const row = document.createElement('tr');
            for (const key of ['username', 'status', 'quota_total', 'settled_total', 'reserved_total', 'available']) {
                appendCell(row, user[key]);
            }
            const actions = document.createElement('td');
            const details = document.createElement('button');
            details.textContent = '明细';
            details.onclick = async () => {
                try { element('details').textContent = JSON.stringify(await api('/api/admin/users/' + user.id + '/requests'), null, 2); }
                catch (error) { showError(error); }
            };
            const adjust = document.createElement('button');
            adjust.textContent = '调整';
            adjust.onclick = () => adjustQuota(user);
            actions.append(details, adjust);
            row.append(actions);
            element('users').append(row);
        }
    } catch (error) { showError(error); }
}

async function adjustQuota(user) {
    const value = prompt('新总额度（整数）', String(user.quota_total));
    if (value === null) return;
    const reason = prompt('调整原因');
    if (reason === null) return;
    if (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)) || !reason.trim()) {
        showError(new Error('额度或原因无效')); return;
    }
    if (!confirm('确认从 ' + user.quota_total + ' 调整为 ' + value + '？')) return;
    try {
        await api('/api/admin/users/' + user.id + '/quota', {
            method: 'POST', body: JSON.stringify({ quota: Number(value), reason })
        });
        await loadUsers();
    } catch (error) { showError(error); }
}

async function resolveRequest() {
    const id = element('requestid').value;
    const input = element('inputusage').value;
    const output = element('outputusage').value;
    const reason = element('reason').value;
    if (!id || !/^\d+$/.test(input) || !/^\d+$/.test(output) || !reason.trim() ||
        !Number.isSafeInteger(Number(input)) || !Number.isSafeInteger(Number(output))) {
        showError(new Error('请填写有效的请求 ID、实际用量和原因')); return;
    }
    if (!confirm('请确认实际用量已核实，确认后不可重复结算。')) return;
    try {
        await api('/api/admin/requests/' + encodeURIComponent(id) + '/resolve', {
            method: 'POST', body: JSON.stringify({ input: Number(input), output: Number(output), reason })
        });
        await loadUsers();
        element('details').textContent = '已结算，请重新查看明细。';
    } catch (error) { showError(error); }
}

element('signin').onclick = async () => {
    try {
        const result = await api('/api/login', {
            method: 'POST', body: JSON.stringify({ username: element('user').value, password: element('pass').value })
        });
        element('pass').value = '';
        token = result.token;
        await api('/api/admin/users?offset=0');
        element('login').hidden = true;
        element('panel').hidden = false;
        await loadUsers();
    } catch (error) { token = ''; element('pass').value = ''; showError(error); }
};
element('resolve').onclick = resolveRequest;
element('find').onclick = () => { offset = 0; loadUsers(); };
element('next').onclick = () => { offset += 50; loadUsers(); };
element('previous').onclick = () => { offset = Math.max(0, offset - 50); loadUsers(); };
element('logout').onclick = async () => {
    try {
        await api('/api/logout', { method: 'POST' });
        token = '';
        element('panel').hidden = true;
        element('login').hidden = false;
    } catch (error) { showError(error); }
};
