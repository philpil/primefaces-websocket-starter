/* WebSocket messages invalidate the view. A coalesced Faces request reads the
 * authoritative snapshot; settings continue to use normal PrimeFaces commands. */
(function (global) {
    'use strict';
    let timer = null;
    let pending = false;
    let busy = false;
    const connectedChannels = new Set();

    function showState(state, text) {
        const badge = document.getElementById('connection-status');
        const label = document.getElementById('connection-text');
        const warning = document.getElementById('connection-warning');
        if (badge) badge.dataset.state = state;
        if (label) label.textContent = text;
        if (warning) warning.hidden = state === 'connected' || state === 'connecting';
    }

    function requestRefresh() {
        pending = true;
        if (busy || timer !== null) return;
        // This is an event debounce, not a recurring polling timer.
        timer = global.setTimeout(function () {
            timer = null;
            if (busy || !pending) return;
            if (typeof global.refreshDashboard !== 'function') return;
            pending = false;
            busy = true;
            global.refreshDashboard();
        }, 120);
    }

    function channelReady(channel) {
        connectedChannels.add(channel);
        if (connectedChannels.size === 2) showState('connected', 'Live · WebSocket connected');
        else showState('connecting', 'Connecting live channels…');
        requestRefresh();
    }

    global.LiveLab = {
        onOpen: function (channel) {
            channelReady(channel);
        },
        onDashboardMessage: function (message, channel) {
            if (message && message.type === 'channel-ready') { channelReady(channel || 'dashboard'); return; }
            if (message && message.type === 'dashboard-changed') requestRefresh();
        },
        onPersonalMessage: function (message, channel) {
            if (message && message.type === 'channel-ready') { channelReady(channel || 'personal'); return; }
            if (message && message.type === 'private-notification') requestRefresh();
        },
        onClose: function (code, channel) {
            connectedChannels.delete(channel);
            showState('disconnected', code === 1000 ? 'Session ended · refresh to sign in' : 'Live connection closed');
        },
        onError: function (code, channel) {
            connectedChannels.delete(channel);
            showState('reconnecting', 'Reconnecting live updates…');
        },
        beginRefresh: function () { busy = true; },
        endRefresh: function () {
            busy = false;
            if (pending) requestRefresh();
        },
        refreshFailed: function () {
            busy = false;
            pending = true;
            showState('disconnected', 'Session unavailable · refresh to sign in');
        }
    };
})(window);
