/* ===== FormulaRacing Live Map - Real-time minimap renderer ===== */

const API_BASE = '/api/v1/readonly';

// ===== State =====
const state = {
    positions: new Map(),       // uuid -> { x, y, z, yaw, pitch, world, timestamp }
    trails: new Map(),          // uuid -> [{ x, z }]
    drivers: [],                // [{ uuid, name, position, ... }]
    heatId: null,
    heats: [],
    trackName: '',
    follow: true,
    showNames: true,
    showTrails: false,
    showBg: true,
    panX: 0,
    panY: 0,
    zoom: 1,
    isPanning: false,
    panStart: { x: 0, y: 0 },
    lastFetch: 0,
    driverColors: {},
};

// ===== Canvas Setup =====
const canvas = document.getElementById('map-canvas');
const ctx = canvas.getContext('2d');

function resizeCanvas() {
    const wrapper = canvas.parentElement;
    const rect = wrapper.getBoundingClientRect();
    canvas.width = rect.width * window.devicePixelRatio;
    canvas.height = rect.height * window.devicePixelRatio;
    canvas.style.width = rect.width + 'px';
    canvas.style.height = rect.height + 'px';
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    canvas._w = rect.width;
    canvas._h = rect.height;
}

resizeCanvas();
window.addEventListener('resize', resizeCanvas);

// ===== Color Generation =====
const DRIVER_COLORS = [
    '#ef4444', '#3b82f6', '#22c55e', '#eab308', '#a855f7',
    '#f97316', '#06b6d4', '#ec4899', '#14b8a6', '#f43f5e',
    '#8b5cf6', '#0ea5e9', '#10b981', '#f59e0b', '#6366f1',
    '#d946ef', '#0891b2', '#84cc16', '#e11d48', '#2dd4bf',
];

function getDriverColor(uuid) {
    if (!state.driverColors[uuid]) {
        const idx = Object.keys(state.driverColors).length % DRIVER_COLORS.length;
        state.driverColors[uuid] = DRIVER_COLORS[idx];
    }
    return state.driverColors[uuid];
}

// ===== Coordinate Conversion =====
// Converts Minecraft world coords to canvas pixel coords
let trackOriginX = 0;
let trackOriginZ = 0;
let trackScale = 0.05; // pixels per block (default, auto-calculated)

function worldToCanvas(x, z) {
    const cx = (x - trackOriginX) * trackScale + state.panX;
    const cy = (trackOriginZ - z) * trackScale + state.panY;
    const w = canvas._w || canvas.width;
    const h = canvas._h || canvas.height;
    return {
        x: cx + w / 2,
        y: cy + h / 2,
    };
}

function autoCalculateScale(positions) {
    if (positions.length < 2) return;
    
    let minX = Infinity, maxX = -Infinity;
    let minZ = Infinity, maxZ = -Infinity;
    
    for (const p of positions) {
        if (p.x < minX) minX = p.x;
        if (p.x > maxX) maxX = p.x;
        if (p.z < minZ) minZ = p.z;
        if (p.z > maxZ) maxZ = p.z;
    }
    
    const rangeX = maxX - minX || 100;
    const rangeZ = maxZ - minZ || 100;
    const maxRange = Math.max(rangeX, rangeZ);
    
    trackOriginX = (minX + maxX) / 2;
    trackOriginZ = (minZ + maxZ) / 2;
    
    const w = canvas._w || canvas.width;
    const h = canvas._h || canvas.height;
    const padding = 80;
    
    const scaleX = (w / 2 - padding) / (maxRange / 2);
    const scaleZ = (h / 2 - padding) / (maxRange / 2);
    trackScale = Math.min(scaleX, scaleZ, 0.2); // cap at 0.2 to avoid zooming too close
}

// ===== Drawing Functions =====

function drawGrid() {
    const w = canvas._w || canvas.width;
    const h = canvas._h || canvas.height;
    
    ctx.strokeStyle = 'rgba(255,255,255,0.03)';
    ctx.lineWidth = 1;
    
    const gridSize = Math.max(10, Math.floor(50 / trackScale / 10) * 10);
    const startX = -w / 2 / trackScale;
    const endX = w / 2 / trackScale;
    const startZ = -h / 2 / trackScale;
    const endZ = h / 2 / trackScale;
    
    for (let x = Math.floor(startX / gridSize) * gridSize; x <= endX; x += gridSize) {
        const p1 = worldToCanvas(trackOriginX + x, trackOriginZ + startZ);
        const p2 = worldToCanvas(trackOriginX + x, trackOriginZ + endZ);
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();
    }
    
    for (let z = Math.floor(startZ / gridSize) * gridSize; z <= endZ; z += gridSize) {
        const p1 = worldToCanvas(trackOriginX + startX, trackOriginZ + z);
        const p2 = worldToCanvas(trackOriginX + endX, trackOriginZ + z);
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();
    }
}

function drawTrails() {
    if (!state.showTrails) return;
    
    for (const [uuid, trail] of state.trails) {
        if (trail.length < 2) continue;
        const color = getDriverColor(uuid);
        
        ctx.beginPath();
        for (let i = 0; i < trail.length; i++) {
            const p = worldToCanvas(trail[i].x, trail[i].z);
            if (i === 0) ctx.moveTo(p.x, p.y);
            else ctx.lineTo(p.x, p.y);
        }
        ctx.strokeStyle = color + '40';
        ctx.lineWidth = 2;
        ctx.stroke();
    }
}

function drawDrivers() {
    const now = Date.now();
    const positions = Array.from(state.positions.entries());
    
    // Sort by position (1st drawn last = on top)
    const driverMap = new Map();
    for (const d of state.drivers) {
        driverMap.set(d.uuid, d);
    }
    
    positions.sort((a, b) => {
        const da = driverMap.get(a[0]);
        const db = driverMap.get(b[0]);
        return (da?.position ?? 99) - (db?.position ?? 99);
    });
    
    for (const [uuid, pos] of positions) {
        const driver = driverMap.get(uuid);
        const color = getDriverColor(uuid);
        const p = worldToCanvas(pos.x, pos.z);
        const age = now - (pos.timestamp || now);
        const alpha = age < 2000 ? 1 : Math.max(0, 1 - (age - 2000) / 3000);
        const size = 12 * state.zoom;
        
        if (p.x < -50 || p.x > (canvas._w || canvas.width) + 50 ||
            p.y < -50 || p.y > (canvas._h || canvas.height) + 50) continue;
        
        // Car body
        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.globalAlpha = alpha;
        
        // Rotation based on yaw
        const yawRad = ((pos.yaw || 0) + 180) * Math.PI / 180;
        ctx.rotate(yawRad);
        
        // Shadow/glow
        ctx.shadowColor = color;
        ctx.shadowBlur = 15;
        
        // Car shape (triangle pointing in direction of travel)
        ctx.beginPath();
        ctx.moveTo(size, 0);                    // front
        ctx.lineTo(-size * 0.6, -size * 0.5);   // back-left
        ctx.lineTo(-size * 0.6, size * 0.5);    // back-right
        ctx.closePath();
        
        ctx.fillStyle = color;
        ctx.fill();
        
        // Car outline
        ctx.shadowBlur = 0;
        ctx.strokeStyle = '#ffffff88';
        ctx.lineWidth = 1.5;
        ctx.stroke();
        
        ctx.restore();
        
        // Driver name
        if (state.showNames && driver) {
            ctx.globalAlpha = alpha;
            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold 12px Inter, sans-serif';
            ctx.textAlign = 'center';
            ctx.shadowColor = 'rgba(0,0,0,0.8)';
            ctx.shadowBlur = 4;
            const nameY = p.y + size + 16;
            
            // Background pill
            const name = driver.name || 'Unknown';
            const nameWidth = ctx.measureText(name).width;
            ctx.shadowBlur = 0;
            ctx.fillStyle = 'rgba(0,0,0,0.6)';
            const pillPad = 6;
            roundRect(ctx, p.x - nameWidth/2 - pillPad, nameY - 10, nameWidth + pillPad * 2, 20, 4);
            ctx.fill();
            
            ctx.fillStyle = '#fff';
            ctx.font = 'bold 12px Inter, sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText(name, p.x, nameY + 4);
            
            ctx.shadowBlur = 0;
        }
        
        ctx.globalAlpha = 1;
    }
}

function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
}

function drawBackground() {
    const w = canvas._w || canvas.width;
    const h = canvas._h || canvas.height;
    
    // Dark gradient background
    const grad = ctx.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, Math.max(w, h) * 0.7);
    grad.addColorStop(0, '#0f1729');
    grad.addColorStop(1, '#060a12');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, w, h);
}

// ===== Main Render Loop =====
function render() {
    ctx.save();
    
    // Reset transform to identity for background
    ctx.setTransform(window.devicePixelRatio, 0, 0, window.devicePixelRatio, 0, 0);
    
    drawBackground();
    drawGrid();
    
    if (state.positions.size > 0) {
        drawTrails();
        drawDrivers();
    } else {
        ctx.fillStyle = 'rgba(255,255,255,0.15)';
        ctx.font = '16px Inter, sans-serif';
        ctx.textAlign = 'center';
        const w = canvas._w || canvas.width;
        const h = canvas._h || canvas.height;
        ctx.fillText('Select a heat to view live positions', w / 2, h / 2);
    }
    
    ctx.restore();
    requestAnimationFrame(render);
}

// ===== API Fetching =====

async function fetchHeats() {
    try {
        const res = await fetch(`${API_BASE}/live/events`);
        if (!res.ok) return;
        const data = await res.json();
        
        if (data?.events) {
            // Also fetch running-heats for more details
            const heatsRes = await fetch(`${API_BASE}/events/running-heats`);
            if (heatsRes.ok) {
                const heatsData = await heatsRes.json();
                state.heats = Array.isArray(heatsData) ? heatsData : [];
            }
        }
        
        updateHeatSelector();
    } catch (e) {
        console.error('Failed to fetch heats:', e);
    }
}

async function fetchPositions() {
    try {
        const res = await fetch(`${API_BASE}/positions/live`);
        if (!res.ok) return;
        const data = await res.json();
        
        state.lastFetch = Date.now();
        
        if (data?.drivers) {
            const newPositions = new Map();
            for (const d of data.drivers) {
                newPositions.set(d.uuid, {
                    x: d.x,
                    y: d.y,
                    z: d.z,
                    yaw: d.yaw,
                    pitch: d.pitch,
                    world: d.world,
                    timestamp: d.t,
                });
                
                // Update trails
                if (state.showTrails) {
                    if (!state.trails.has(d.uuid)) {
                        state.trails.set(d.uuid, []);
                    }
                    const trail = state.trails.get(d.uuid);
                    trail.push({ x: d.x, z: d.z });
                    if (trail.length > 200) trail.shift(); // Keep last 200 points
                }
            }
            state.positions = newPositions;
            
            // Auto-calculate scale if positions exist
            if (newPositions.size > 0 && state.zoom === 1 && state.panX === 0 && state.panY === 0) {
                autoCalculateScale(Array.from(newPositions.values()));
            }
        }
        
        document.getElementById('map-refresh-rate').textContent = 
            Date.now() - state.lastFetch;
    } catch (e) {
        console.error('Failed to fetch positions:', e);
    }
}

async function fetchRunningHeats() {
    try {
        const res = await fetch(`${API_BASE}/events/running-heats`);
        if (!res.ok) return;
        const data = await res.json();
        
        if (Array.isArray(data) && state.heatId) {
            const currentHeat = data.find(h => h.id === state.heatId);
            if (currentHeat?.driver_details) {
                state.drivers = currentHeat.driver_details;
                document.getElementById('live-status-text').textContent = 
                    `Heat #${state.heatId}`;
                updateDriverList();
            }
        }
    } catch (e) {
        console.error('Failed to fetch running heats:', e);
    }
}

// ===== UI Updates =====

function updateHeatSelector() {
    const select = document.getElementById('heat-select');
    const currentValue = select.value;
    
    // Clear all options except the placeholder
    select.innerHTML = '<option value="">— Select a heat —</option>';
    
    const heatSet = new Set();
    
    // From live/events
    if (state.heats.length > 0) {
        for (const heat of state.heats) {
            const id = heat.heat_id || heat.id;
            if (id && !heatSet.has(id)) {
                heatSet.add(id);
                const opt = document.createElement('option');
                opt.value = id;
                opt.textContent = `Heat #${id} - ${heat.track || heat.name || 'Unknown'} (${heat.heat_state || 'active'})`;
                select.appendChild(opt);
            }
        }
    }
    
    if (currentValue && heatSet.has(parseInt(currentValue))) {
        select.value = currentValue;
    }
}

function updateDriverList() {
    const container = document.getElementById('driver-items');
    const countEl = document.getElementById('map-driver-count');
    
    if (state.drivers.length === 0) {
        container.innerHTML = '<div style="color: var(--text-muted); font-size: 13px; padding: 8px 4px;">No drivers in this heat</div>';
        return;
    }
    
    // Sort by position
    const sorted = [...state.drivers].sort((a, b) => (a.position || 99) - (b.position || 99));
    
    countEl.textContent = `${sorted.length} drivers`;
    
    // Find leader time for gap calculation
    let leaderTime = Infinity;
    for (const d of sorted) {
        if (d.total_time_ms > 0 && d.total_time_ms < leaderTime) {
            leaderTime = d.total_time_ms;
        }
    }
    
    container.innerHTML = sorted.map((d, i) => {
        const gap = d.total_time_ms > 0 && leaderTime > 0 && d.total_time_ms < Infinity
            ? `+${((d.total_time_ms - leaderTime) / 1000).toFixed(1)}s`
            : d.total_time_ms > 0 ? formatTime(d.total_time_ms) : '—';
        
        const posClass = i === 0 ? 'pos-1st' : i === 1 ? 'pos-2nd' : i === 2 ? 'pos-3rd' : '';
        const color = getDriverColor(d.uuid);
        
        return `
            <div class="driver-item ${posClass}">
                <span class="pos">P${i + 1}</span>
                <span class="color-dot" style="background: ${color};"></span>
                <span class="name">${d.name || 'Unknown'}</span>
                <span class="gap">${gap}</span>
            </div>
        `;
    }).join('');
}

function formatTime(ms) {
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    const millis = ms % 1000;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(millis).padStart(3, '0')}`;
}

// ===== Heat Selection =====

document.getElementById('heat-select').addEventListener('change', (e) => {
    const value = e.target.value;
    if (value) {
        state.heatId = parseInt(value);
        state.trackName = e.target.options[e.target.selectedIndex].text.split(' - ')[1] || '';
        document.getElementById('map-track-name').innerHTML = `<strong>${state.trackName}</strong>`;
        
        // Reset view for new heat
        state.panX = 0;
        state.panY = 0;
        state.zoom = 1;
        trackScale = 0.05;
        trackOriginX = 0;
        trackOriginZ = 0;
        
        // Clear old trails
        state.trails.clear();
        state.driverColors = {};
        
        fetchRunningHeats();
    } else {
        state.heatId = null;
        state.drivers = [];
        state.positions.clear();
        state.trails.clear();
        document.getElementById('map-track-name').innerHTML = '<strong>No heat selected</strong>';
        document.getElementById('live-status-text').textContent = 'Live';
        document.getElementById('map-driver-count').textContent = '';
        updateDriverList();
    }
});

// ===== Controls =====

document.getElementById('btn-follow').addEventListener('click', (e) => {
    state.follow = !state.follow;
    e.target.classList.toggle('active');
});

document.getElementById('btn-names').addEventListener('click', (e) => {
    state.showNames = !state.showNames;
    e.target.classList.toggle('active');
});

document.getElementById('btn-trails').addEventListener('click', (e) => {
    state.showTrails = !state.showTrails;
    e.target.classList.toggle('active');
    if (!state.showTrails) state.trails.clear();
});

document.getElementById('btn-bg').addEventListener('click', (e) => {
    state.showBg = !state.showBg;
    e.target.classList.toggle('active');
});

document.getElementById('btn-reset-view').addEventListener('click', () => {
    state.panX = 0;
    state.panY = 0;
    state.zoom = 1;
    trackScale = 0.05;
    trackOriginX = 0;
    trackOriginZ = 0;
    state.trails.clear();
});

// ===== Mouse/Touch Pan =====

canvas.addEventListener('mousedown', (e) => {
    state.isPanning = true;
    state.panStart = { x: e.clientX - state.panX, y: e.clientY - state.panY };
    canvas.style.cursor = 'grabbing';
});

window.addEventListener('mousemove', (e) => {
    if (!state.isPanning) return;
    state.panX = e.clientX - state.panStart.x;
    state.panY = e.clientY - state.panStart.y;
});

window.addEventListener('mouseup', () => {
    state.isPanning = false;
    canvas.style.cursor = 'grab';
});

canvas.addEventListener('wheel', (e) => {
    e.preventDefault();
    const delta = -e.deltaY * 0.001;
    state.zoom = Math.max(0.2, Math.min(5, state.zoom + delta));
    trackScale *= (1 + delta / state.zoom);
});

// Touch support
canvas.addEventListener('touchstart', (e) => {
    if (e.touches.length === 1) {
        state.isPanning = true;
        state.panStart = { x: e.touches[0].clientX - state.panX, y: e.touches[0].clientY - state.panY };
    }
}, { passive: true });

canvas.addEventListener('touchmove', (e) => {
    if (!state.isPanning || e.touches.length !== 1) return;
    state.panX = e.touches[0].clientX - state.panStart.x;
    state.panY = e.touches[0].clientY - state.panStart.y;
}, { passive: true });

canvas.addEventListener('touchend', () => {
    state.isPanning = false;
}, { passive: true });

// ===== Init =====

function init() {
    // Start render loop
    render();
    
    // Fetch heats initially
    fetchHeats();
    
    // Fetch positions every 200ms
    setInterval(fetchPositions, 200);
    
    // Fetch heat details every 1s
    setInterval(() => {
        if (state.heatId) fetchRunningHeats();
    }, 1000);
    
    // Refresh heat list every 5s
    setInterval(fetchHeats, 5000);
    
    // Hide live indicator if no connection
    setTimeout(() => {
        if (state.positions.size === 0) {
            document.getElementById('live-status-text').textContent = 'Waiting...';
        }
    }, 3000);
}

document.addEventListener('DOMContentLoaded', init);
