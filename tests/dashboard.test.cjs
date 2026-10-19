const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync('src/main/webapp/resources/app/dashboard.js', 'utf8');

function harness() {
    const tasks = new Map();
    const elements = new Map(['connection-status', 'connection-text', 'connection-warning'].map(id => [id, { dataset: {}, hidden: true, textContent: '' }]));
    let nextId = 0;
    let requests = 0;
    const window = {
        setTimeout(fn) { const id = ++nextId; tasks.set(id, fn); return id; },
        refreshDashboard() { requests++; },
    };
    vm.runInNewContext(source, { window, document: { getElementById: id => elements.get(id) } });
    return {
        lab: window.LiveLab,
        elements,
        requests: () => requests,
        flush() { for (const [id, fn] of [...tasks]) { tasks.delete(id); fn(); } },
        timers: () => tasks.size,
    };
}

test('a burst of push invalidations causes one HTTP refresh, not one per message', () => {
    const h = harness();
    for (let i = 0; i < 100; i++) h.lab.onDashboardMessage({ type: 'dashboard-changed', version: i });
    assert.equal(h.timers(), 1);
    h.flush();
    assert.equal(h.requests(), 1);
    for (let i = 0; i < 50; i++) h.lab.onDashboardMessage({ type: 'dashboard-changed' });
    assert.equal(h.timers(), 0);
    h.lab.endRefresh();
    h.flush();
    assert.equal(h.requests(), 2);
});

test('opening channels resynchronizes the authoritative data and marks both sockets connected', () => {
    const h = harness();
    h.lab.onOpen('dashboard');
    h.lab.onOpen('personal');
    assert.equal(h.elements.get('connection-status').dataset.state, 'connected');
    h.flush();
    assert.equal(h.requests(), 1);
    h.lab.endRefresh();
    h.lab.onOpen('dashboard');
    h.flush();
    assert.equal(h.requests(), 2);
});

test('a private notification refreshes data; unrelated messages do not', () => {
    const h = harness();
    h.lab.onPersonalMessage({ type: 'unrelated' });
    assert.equal(h.timers(), 0);
    h.lab.onPersonalMessage({ type: 'private-notification' });
    h.flush();
    assert.equal(h.requests(), 1);
});

test('the server ready handshake restores connection state and reloads data after reconnect', () => {
    const h = harness();
    h.lab.onOpen('dashboard'); h.lab.onOpen('personal'); h.flush(); h.lab.endRefresh();
    h.lab.onError(1006, 'dashboard');
    assert.equal(h.elements.get('connection-status').dataset.state, 'reconnecting');
    h.lab.onDashboardMessage({ type: 'channel-ready' }, 'dashboard');
    h.flush();
    assert.equal(h.requests(), 2);
    assert.equal(h.elements.get('connection-status').dataset.state, 'connected');
});

test('session failures release the request lock and expose a visible recovery message', () => {
    const h = harness();
    h.lab.onDashboardMessage({ type: 'dashboard-changed' });
    h.flush();
    h.lab.refreshFailed();
    assert.equal(h.elements.get('connection-warning').hidden, false);
    h.lab.onClose(1000, 'personal');
    assert.match(h.elements.get('connection-text').textContent, /Session ended/);
    assert.equal(h.timers(), 0);
});
