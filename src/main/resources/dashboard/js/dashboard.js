/* ===== FormulaRacing Dashboard JS ===== */

const API_BASE = '/api/v1/readonly';

// ===== Navigation & Routing =====
function getPage() {
    const params = new URLSearchParams(window.location.search);
    return params.get('page') || 'overview';
}

function renderPage() {
    const page = getPage();
    
    // Update active nav
    document.querySelectorAll('.nav-item[data-page]').forEach(el => {
        el.classList.toggle('active', el.dataset.page === page);
    });

    // Render content
    switch (page) {
        case 'overview': renderOverview(); break;
        case 'tracks': renderTracks(); break;
        case 'players': renderPlayers(); break;
        case 'events': renderEvents(); break;
        case 'logs': renderLogs(); break;
        case 'status': renderStatus(); break;
        default: renderOverview();
    }
}

// ===== API Fetch Helper =====
async function apiFetch(endpoint) {
    try {
        const res = await fetch(`${API_BASE}${endpoint}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
    } catch (e) {
        console.error(`API Error: ${endpoint}`, e);
        return null;
    }
}

// ===== Overview Page =====
async function renderOverview() {
    const content = document.getElementById('page-content');
    document.getElementById('page-title').textContent = 'Dashboard';
    document.getElementById('page-subtitle').textContent = 'Real-time overview of your FormulaRacing server';

    const status = await apiFetch('/status');
    const tracks = await apiFetch('/tracks');
    const players = await apiFetch('/players');

    const onlineCount = players?.total ?? 0;
    const trackCount = tracks?.number ?? status?.total_tracks ?? 0;
    const activeHeats = status?.active_heats ?? 0;
    const totalEvents = status?.total_events ?? 0;

    content.innerHTML = `
        <div class="stats-grid">
            <div class="stat-card color-green">
                <div class="stat-label">Players Online</div>
                <div class="stat-value">${onlineCount}</div>
                <div class="stat-change" style="color: var(--text-muted);">
                    Max: ${status?.max_players ?? '?'}
                </div>
            </div>
            <div class="stat-card color-blue">
                <div class="stat-label">Tracks</div>
                <div class="stat-value">${trackCount}</div>
            </div>
            <div class="stat-card color-red">
                <div class="stat-label">Active Heats</div>
                <div class="stat-value">${activeHeats}</div>
                <div class="stat-change" style="color: ${activeHeats > 0 ? 'var(--accent-green)' : 'var(--text-muted)'};">
                    ${activeHeats > 0 ? '🔴 Racing' : 'Idle'}
                </div>
            </div>
            <div class="stat-card color-yellow">
                <div class="stat-label">Total Events</div>
                <div class="stat-value">${totalEvents}</div>
            </div>
        </div>

        <div class="section-card">
            <div class="section-header">
                <h3>🏁 Active Events</h3>
                <span class="status-badge ${activeHeats > 0 ? 'live' : 'waiting'}">
                    <span class="pulse-dot"></span>
                    ${activeHeats > 0 ? 'Live' : 'No active races'}
                </span>
            </div>
            <div class="section-body" id="active-events-list">
                <p style="color: var(--text-muted);">Loading...</p>
            </div>
        </div>

        <div class="section-card">
            <div class="section-header">
                <h3>👥 Online Players</h3>
            </div>
            <div class="section-body" id="players-list">
                <p style="color: var(--text-muted);">Loading...</p>
            </div>
        </div>
    `;

    // Load active events
    const events = await apiFetch('/live/events');
    const eventsList = document.getElementById('active-events-list');
    if (events?.events?.length > 0) {
        eventsList.innerHTML = `<table class="data-table">
            <thead><tr>
                <th>Event</th>
                <th>Track</th>
                <th>Round</th>
                <th>Drivers</th>
                <th>Status</th>
            </tr></thead>
            <tbody>
                ${events.events.map(e => `
                <tr>
                    <td><strong>${e.name}</strong></td>
                    <td>${e.track ?? '—'}</td>
                    <td>${e.round ?? '—'}</td>
                    <td>${e.drivers ?? 0}</td>
                    <td><span class="status-badge ${e.state === 'RACING' ? 'racing' : 'live'}">${e.state}</span></td>
                </tr>`).join('')}
            </tbody>
        </table>`;
    } else {
        eventsList.innerHTML = '<p style="color: var(--text-muted);">No active events</p>';
    }

    // Load online players
    const playersList = document.getElementById('players-list');
    if (players?.players?.length > 0) {
        playersList.innerHTML = `<table class="data-table">
            <thead><tr>
                <th>Player</th>
                <th>Language</th>
                <th>Boat</th>
                <th>Status</th>
            </tr></thead>
            <tbody>
                ${players.players.map(p => `
                <tr>
                    <td><span class="status-online">●</span> ${p.name}</td>
                    <td>${p.language ?? 'en'}</td>
                    <td>Type ${p.boat_type}</td>
                    <td><span class="status-badge live">Online</span></td>
                </tr>`).join('')}
            </tbody>
        </table>`;
    } else {
        playersList.innerHTML = '<p style="color: var(--text-muted);">No players online</p>';
    }

    // Update badge
    document.getElementById('active-events-badge').textContent = totalEvents;
}

// ===== Tracks Page =====
async function renderTracks() {
    document.getElementById('page-title').textContent = 'Tracks';
    document.getElementById('page-subtitle').textContent = 'All tracks on the server';

    const tracks = await apiFetch('/tracks');
    if (!tracks?.tracks) {
        document.getElementById('page-content').innerHTML = '<p style="color: var(--text-muted);">No tracks found</p>';
        return;
    }

    const content = document.getElementById('page-content');
    content.innerHTML = `
        <div class="stats-grid">
            <div class="stat-card color-blue">
                <div class="stat-label">Total Tracks</div>
                <div class="stat-value">${tracks.tracks.length}</div>
            </div>
        </div>
        <div class="section-card">
            <div class="section-header"><h3>🏁 Track List</h3></div>
            <div class="section-body">
                <table class="data-table">
                    <thead><tr>
                        <th>Name</th>
                        <th>World</th>
                        <th>Creator</th>
                        <th>Checkpoints</th>
                        <th>Icon</th>
                    </tr></thead>
                    <tbody>
                        ${tracks.tracks.map(t => `
                        <tr>
                            <td><strong>${t.name}</strong></td>
                            <td>${t.world ?? '—'}</td>
                            <td>${t.creator ?? '—'}</td>
                            <td>${t.checkpoints ?? 0}</td>
                            <td>${t.icon ?? '—'}</td>
                        </tr>`).join('')}
                    </tbody>
                </table>
            </div>
        </div>
    `;
}

// ===== Players Page =====
async function renderPlayers() {
    document.getElementById('page-title').textContent = 'Players';
    document.getElementById('page-subtitle').textContent = 'Online players and their settings';

    const data = await apiFetch('/players');
    if (!data?.players) {
        document.getElementById('page-content').innerHTML = '<p style="color: var(--text-muted);">No players online</p>';
        return;
    }

    const content = document.getElementById('page-content');
    content.innerHTML = `
        <div class="stats-grid">
            <div class="stat-card color-green">
                <div class="stat-label">Online Players</div>
                <div class="stat-value">${data.total}</div>
            </div>
        </div>
        <div class="section-card">
            <div class="section-header"><h3>👥 Player List</h3></div>
            <div class="section-body">
                <table class="data-table">
                    <thead><tr>
                        <th>Name</th>
                        <th>UUID</th>
                        <th>Language</th>
                        <th>Colors</th>
                        <th>Boat</th>
                    </tr></thead>
                    <tbody>
                        ${data.players.map(p => `
                        <tr>
                            <td><span class="status-online">●</span> <strong>${p.name}</strong></td>
                            <td style="font-family: monospace; font-size: 12px;">${p.uuid.substring(0, 8)}..</td>
                            <td>${p.language ?? 'en'}</td>
                            <td><span style="color:${p.color1};">■</span> <span style="color:${p.color2};">■</span></td>
                            <td>Type ${p.boat_type}</td>
                        </tr>`).join('')}
                    </tbody>
                </table>
            </div>
        </div>
    `;
}

// ===== Events Page =====
async function renderEvents() {
    document.getElementById('page-title').textContent = 'Events';
    document.getElementById('page-subtitle').textContent = 'All events on the server';

    const data = await apiFetch('/events');
    if (!data?.events) {
        document.getElementById('page-content').innerHTML = '<p style="color: var(--text-muted);">No events</p>';
        return;
    }

    const content = document.getElementById('page-content');
    content.innerHTML = `
        <div class="stats-grid">
            <div class="stat-card color-yellow">
                <div class="stat-label">Total Events</div>
                <div class="stat-value">${data.total}</div>
            </div>
        </div>
        <div class="section-card">
            <div class="section-header"><h3>🏆 Event List</h3></div>
            <div class="section-body">
                <table class="data-table">
                    <thead><tr>
                        <th>ID</th>
                        <th>Name</th>
                        <th>Track</th>
                        <th>Drivers</th>
                        <th>Status</th>
                    </tr></thead>
                    <tbody>
                        ${data.events.map(e => `
                        <tr>
                            <td>#${e.id}</td>
                            <td><strong>${e.name}</strong></td>
                            <td>${e.track ?? '—'}</td>
                            <td>${e.drivers ?? 0}</td>
                            <td><span class="status-badge ${e.active ? 'live' : 'waiting'}">${e.state}</span></td>
                        </tr>`).join('')}
                    </tbody>
                </table>
            </div>
        </div>
    `;
}

// ===== Logs Page =====
async function renderLogs() {
    document.getElementById('page-title').textContent = 'System Logs';
    document.getElementById('page-subtitle').textContent = 'Recent server activity and logs';

    const data = await apiFetch('/logs?limit=50');
    const logs = data?.logs ?? [];

    const content = document.getElementById('page-content');
    content.innerHTML = `
        <div class="section-card">
            <div class="section-header">
                <h3>📋 Recent Logs</h3>
                <span class="status-badge live">${logs.length} entries</span>
            </div>
            <div class="section-body" style="max-height: 600px; overflow-y: auto; font-family: monospace; font-size: 13px;">
                ${logs.length > 0 ? logs.map(log => `
                    <div style="padding: 6px 0; border-bottom: 1px solid var(--border-light); display: flex; gap: 12px;">
                        <span style="color: var(--text-muted); flex-shrink: 0;">
                            ${new Date(log.timestamp ?? Date.now()).toLocaleTimeString()}
                        </span>
                        <span style="color: ${
                            log.level === 'ERROR' ? 'var(--accent-red)' :
                            log.level === 'WARN' ? 'var(--accent-yellow)' :
                            log.level === 'INFO' ? 'var(--accent-green)' :
                            'var(--text-secondary)'
                        }; flex-shrink: 0; min-width: 50px;">
                            [${log.level}]
                        </span>
                        <span style="color: var(--text-secondary);">${log.message}</span>
                    </div>
                `).join('') : '<p style="color: var(--text-muted);">No logs available</p>'}
            </div>
        </div>
    `;
}

// ===== Status Page =====
async function renderStatus() {
    document.getElementById('page-title').textContent = 'API Status';
    document.getElementById('page-subtitle').textContent = 'Server and API health information';

    const status = await apiFetch('/status');

    const content = document.getElementById('page-content');
    content.innerHTML = `
        <div class="section-card">
            <div class="section-header">
                <h3>⚡ Server Status</h3>
                <span class="status-badge live">● Online</span>
            </div>
            <div class="section-body">
                <table class="data-table">
                    <tbody>
                        <tr><td><strong>Status</strong></td><td style="color: var(--accent-green);">${status?.status ?? 'unknown'}</td></tr>
                        <tr><td><strong>Version</strong></td><td>${status?.version ?? '—'}</td></tr>
                        <tr><td><strong>Server</strong></td><td>${status?.server ?? '—'}</td></tr>
                        <tr><td><strong>Players Online</strong></td><td>${status?.players_online ?? 0} / ${status?.max_players ?? 0}</td></tr>
                        <tr><td><strong>Total Tracks</strong></td><td>${status?.total_tracks ?? 0}</td></tr>
                        <tr><td><strong>Active Heats</strong></td><td>${status?.active_heats ?? 0}</td></tr>
                        <tr><td><strong>Total Events</strong></td><td>${status?.total_events ?? 0}</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;
}

// ===== Auto-refresh =====
let refreshInterval;

function startAutoRefresh() {
    stopAutoRefresh();
    refreshInterval = setInterval(() => {
        const page = getPage();
        if (page === 'overview') renderOverview();
    }, 5000);
}

function stopAutoRefresh() {
    if (refreshInterval) {
        clearInterval(refreshInterval);
        refreshInterval = null;
    }
}

// ===== Init =====
document.addEventListener('DOMContentLoaded', () => {
    renderPage();
    startAutoRefresh();

    // Handle nav clicks
    document.querySelectorAll('.nav-item[data-page]').forEach(el => {
        el.addEventListener('click', (e) => {
            e.preventDefault();
            const page = el.dataset.page;
            history.pushState({ page }, '', `?page=${page}`);
            renderPage();
        });
    });

    // Handle browser back/forward
    window.addEventListener('popstate', renderPage);
});
