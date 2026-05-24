/* ============================================
   ORION ADMIN DASHBOARD - APP LOGIC
   ============================================ */

let homeTrendChart, analyticsTrendChart, analyticsBarChart, analyticsRouteChart, detailTrendChart;
let currentStudentFilter = 'all';
let currentScreen = 'home';

// =============== NAVIGATION ===============
function navigateTo(screenId) {
    // Hide bottom nav for student-detail
    const bottomNav = document.getElementById('bottomNav');

    // Deactivate all screens
    document.querySelectorAll('.screen').forEach(s => {
        s.classList.remove('active');
    });

    // Activate target screen
    const target = document.getElementById(`screen-${screenId}`);
    if (target) {
        target.classList.add('active');
        // Scroll to top
        const content = target.querySelector('.screen-content');
        if (content) content.scrollTop = 0;
    }

    // Update bottom nav active state
    document.querySelectorAll('.nav-item').forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.screen === screenId) {
            btn.classList.add('active');
        }
    });

    // Show / hide bottom nav
    if (screenId === 'student-detail') {
        bottomNav.style.display = 'none';
    } else {
        bottomNav.style.display = 'flex';
    }

    currentScreen = screenId;

    // Render screen-specific content
    switch(screenId) {
        case 'home': renderHomeScreen(); break;
        case 'analytics': renderAnalyticsScreen(); break;
        case 'students': renderStudentList(); break;
        case 'logs': renderLogScreen(); break;
    }
}

// =============== UTILITIES ===============
function formatDuration(sec) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}m ${s}s`;
}

function formatShortDuration(sec) {
    const m = (sec / 60).toFixed(1);
    return `${m} min`;
}

function getChartColors() {
    return {
        accent: '#00d2ff',
        accentBg: 'rgba(0, 210, 255, 0.1)',
        purple: '#8b5cf6',
        purpleBg: 'rgba(139, 92, 246, 0.1)',
        success: '#00e676',
        warning: '#ffb020',
        danger: '#e94560',
        grid: 'rgba(255,255,255,0.04)',
        tick: '#6a6aa0',
    };
}

function getStats(logs) {
    const total = logs.length;
    const febLogs = logs.filter(l => l.date.getMonth() === 1);
    const aprLogs = logs.filter(l => l.date.getMonth() === 3);
    const avgFeb = febLogs.length ? febLogs.reduce((a, b) => a + b.duration, 0) / febLogs.length : 0;
    const avgApr = aprLogs.length ? aprLogs.reduce((a, b) => a + b.duration, 0) / aprLogs.length : 0;
    const improvement = avgFeb > 0 && avgApr > 0 ? ((avgFeb - avgApr) / avgFeb) * 100 : 0;
    return { total, avgFeb, avgApr, improvement };
}

function getTopRoutes(logs, limit = 5) {
    const routes = {};
    logs.forEach(l => {
        const arr = [l.from, l.to].sort();
        const route = `${arr[0]} ↔ ${arr[1]}`;
        routes[route] = (routes[route] || 0) + 1;
    });
    return Object.entries(routes).sort((a, b) => b[1] - a[1]).slice(0, limit);
}

function getDailyAverages(logs) {
    const dailyAvg = [];
    for (let i = 0; i < 49; i++) {
        const dayLogs = logs.filter(l => l.dayIndex === i);
        const avg = dayLogs.length ? dayLogs.reduce((a, b) => a + b.duration, 0) / dayLogs.length : 0;
        dailyAvg.push(+(avg / 60).toFixed(2));
    }
    return dailyAvg;
}

function getDayLabels() {
    return Array.from({ length: 49 }, (_, i) => {
        const d = new Date(2026, 1, 27);
        d.setDate(d.getDate() + i);
        return d.toLocaleDateString('id-ID', { day: 'numeric', month: 'short' });
    });
}

// =============== HOME SCREEN ===============
function renderHomeScreen() {
    const stats = getStats(appData.logs);

    document.getElementById('home-totalNav').textContent = stats.total.toLocaleString();
    document.getElementById('home-activeStudents').textContent = appData.users.length;
    document.getElementById('home-avgNow').textContent = formatDuration(Math.round(stats.avgApr));
    document.getElementById('home-improvement').textContent = `+${stats.improvement.toFixed(1)}%`;

    // Mini trend chart
    renderHomeTrendChart();

    // Top routes
    renderHomeTopRoutes();

    // Recent logs
    renderHomeRecentLogs();
}

function renderHomeTrendChart() {
    const ctx = document.getElementById('homeTrendChart').getContext('2d');
    const c = getChartColors();
    const dailyAvg = getDailyAverages(appData.logs);
    const labels = getDayLabels();

    if (homeTrendChart) homeTrendChart.destroy();

    const gradient = ctx.createLinearGradient(0, 0, 0, 200);
    gradient.addColorStop(0, 'rgba(0, 210, 255, 0.2)');
    gradient.addColorStop(1, 'rgba(0, 210, 255, 0.0)');

    homeTrendChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                data: dailyAvg,
                borderColor: c.accent,
                backgroundColor: gradient,
                borderWidth: 2.5,
                fill: true,
                tension: 0.4,
                pointRadius: 0,
                pointHitRadius: 10,
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            aspectRatio: 2,
            scales: {
                y: {
                    beginAtZero: true,
                    grid: { color: c.grid, drawBorder: false },
                    ticks: { color: c.tick, font: { size: 10 } },
                    title: { display: true, text: 'Menit', color: c.tick, font: { size: 10 } },
                    border: { display: false }
                },
                x: {
                    grid: { display: false },
                    ticks: { color: c.tick, font: { size: 9 }, maxTicksLimit: 6 },
                    border: { display: false }
                }
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#1a1a3a',
                    titleColor: '#fff',
                    bodyColor: '#e0e0ff',
                    borderColor: c.accent,
                    borderWidth: 1,
                    cornerRadius: 8,
                    padding: 10,
                    callbacks: { label: ctx => ` ${ctx.parsed.y} menit` }
                }
            },
            interaction: { intersect: false, mode: 'index' }
        }
    });
}

function renderHomeTopRoutes() {
    const container = document.getElementById('home-topRoutes');
    const topRoutes = getTopRoutes(appData.logs, 3);
    const maxCount = topRoutes.length ? topRoutes[0][1] : 1;

    container.innerHTML = topRoutes.map(([name, count], i) => {
        const rankClass = i < 3 ? `rank-${i + 1}` : 'rank-default';
        const barWidth = (count / maxCount * 100).toFixed(0);
        return `
        <div class="route-item">
            <div class="route-rank ${rankClass}">${i + 1}</div>
            <div class="route-info">
                <div class="route-name">${name}</div>
                <div class="route-count">${count} trips</div>
            </div>
            <div class="route-bar-wrap">
                <div class="route-bar" style="width: ${barWidth}%"></div>
            </div>
        </div>`;
    }).join('');
}

function renderHomeRecentLogs() {
    const container = document.getElementById('home-recentLogs');
    const recentLogs = appData.logs.slice(0, 5);

    container.innerHTML = recentLogs.map(log => `
        <div class="log-item">
            <div class="log-icon">
                <span class="material-icons-round">near_me</span>
            </div>
            <div class="log-info">
                <div class="log-route">${log.from} → ${log.to}</div>
                <div class="log-meta">
                    <span>${log.userId}</span>
                    <span>•</span>
                    <span>${log.distance || ''}m</span>
                    <span>•</span>
                    <span>${log.date.toLocaleDateString('id-ID', { day: 'numeric', month: 'short' })} ${log.date.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })}</span>
                </div>
            </div>
            <div class="log-duration">${formatDuration(log.duration)}</div>
        </div>
    `).join('');
}

// =============== ANALYTICS SCREEN ===============
function renderAnalyticsScreen() {
    const stats = getStats(appData.logs);
    document.getElementById('analytics-avgFeb').textContent = formatDuration(Math.round(stats.avgFeb));
    document.getElementById('analytics-avgApr').textContent = formatDuration(Math.round(stats.avgApr));
    document.getElementById('analytics-improvement').textContent = `+${stats.improvement.toFixed(1)}%`;

    renderAnalyticsTrendChart();
    renderAnalyticsBarChart();
    renderAnalyticsRouteChart();
}

function renderAnalyticsTrendChart() {
    const ctx = document.getElementById('analyticsTrendChart').getContext('2d');
    const c = getChartColors();
    const dailyAvg = getDailyAverages(appData.logs);
    const labels = getDayLabels();

    if (analyticsTrendChart) analyticsTrendChart.destroy();

    const gradient = ctx.createLinearGradient(0, 0, 0, 250);
    gradient.addColorStop(0, 'rgba(0, 210, 255, 0.25)');
    gradient.addColorStop(1, 'rgba(0, 210, 255, 0.0)');

    analyticsTrendChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'Rata-rata Waktu (Menit)',
                data: dailyAvg,
                borderColor: c.accent,
                backgroundColor: gradient,
                borderWidth: 3,
                fill: true,
                tension: 0.4,
                pointRadius: Array.from({ length: 49 }, (_, i) => i % 5 === 0 ? 4 : 0),
                pointBackgroundColor: '#0d0d1e',
                pointBorderColor: c.accent,
                pointBorderWidth: 2,
                pointHitRadius: 10,
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            aspectRatio: 1.6,
            scales: {
                y: {
                    beginAtZero: true,
                    title: { display: true, text: 'Menit', color: c.tick, font: { size: 10 } },
                    grid: { color: c.grid, drawBorder: false },
                    ticks: { color: c.tick, font: { size: 10 } },
                    border: { display: false }
                },
                x: {
                    grid: { display: false },
                    ticks: { color: c.tick, maxTicksLimit: 8, font: { size: 9 } },
                    border: { display: false }
                }
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#1a1a3a',
                    titleColor: '#fff',
                    bodyColor: '#e0e0ff',
                    borderColor: c.accent,
                    borderWidth: 1,
                    cornerRadius: 8,
                    padding: 10,
                    callbacks: { label: ctx => ` ${ctx.parsed.y} menit` }
                }
            },
            interaction: { intersect: false, mode: 'index' }
        }
    });
}

function renderAnalyticsBarChart() {
    const ctx = document.getElementById('analyticsBarChart').getContext('2d');
    const c = getChartColors();

    const febs = appData.logs.filter(l => l.date.getMonth() === 1);
    const mars = appData.logs.filter(l => l.date.getMonth() === 2);
    const aprs = appData.logs.filter(l => l.date.getMonth() === 3);
    const calcAvg = arr => arr.length ? arr.reduce((a, b) => a + b.duration, 0) / arr.length / 60 : 0;

    if (analyticsBarChart) analyticsBarChart.destroy();

    analyticsBarChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: ['Februari', 'Maret', 'April'],
            datasets: [{
                label: 'Rata-rata Waktu (Menit)',
                data: [calcAvg(febs).toFixed(2), calcAvg(mars).toFixed(2), calcAvg(aprs).toFixed(2)],
                backgroundColor: [c.danger, c.warning, c.success],
                borderRadius: 8,
                barPercentage: 0.5,
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            aspectRatio: 1.6,
            scales: {
                y: {
                    beginAtZero: true,
                    grid: { color: c.grid, drawBorder: false },
                    ticks: { color: c.tick, font: { size: 10 } },
                    border: { display: false }
                },
                x: {
                    grid: { display: false },
                    ticks: { color: c.tick, font: { size: 11 } },
                    border: { display: false }
                }
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#1a1a3a',
                    borderColor: c.accent,
                    borderWidth: 1,
                    cornerRadius: 8,
                }
            }
        }
    });
}

function renderAnalyticsRouteChart() {
    const ctx = document.getElementById('analyticsRouteChart').getContext('2d');
    const c = getChartColors();
    const topRoutes = getTopRoutes(appData.logs, 5);

    if (analyticsRouteChart) analyticsRouteChart.destroy();

    const barColors = [c.accent, c.purple, c.success, c.warning, c.danger];

    analyticsRouteChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: topRoutes.map(r => r[0]),
            datasets: [{
                label: 'Jumlah Trip',
                data: topRoutes.map(r => r[1]),
                backgroundColor: barColors.map(cl => cl + '99'),
                borderColor: barColors,
                borderWidth: 1,
                borderRadius: 6,
                barPercentage: 0.6,
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: true,
            aspectRatio: 1.4,
            scales: {
                x: { grid: { color: c.grid, drawBorder: false }, ticks: { color: c.tick, font: { size: 10 } }, border: { display: false } },
                y: { grid: { display: false }, ticks: { color: '#e0e0ff', font: { size: 10 } }, border: { display: false } }
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#1a1a3a',
                    borderColor: c.accent,
                    borderWidth: 1,
                    cornerRadius: 8,
                }
            }
        }
    });
}

// =============== STUDENTS SCREEN ===============
function renderStudentList() {
    const container = document.getElementById('studentList');
    const searchTerm = (document.getElementById('studentSearch')?.value || '').toLowerCase();

    let students = appData.users;
    if (currentStudentFilter !== 'all') {
        students = students.filter(u => u.type === currentStudentFilter);
    }
    if (searchTerm) {
        students = students.filter(u =>
            u.id.toLowerCase().includes(searchTerm) ||
            u.name.toLowerCase().includes(searchTerm)
        );
    }

    container.innerHTML = students.map((user, i) => {
        const userLogs = appData.logs.filter(l => l.userId === user.id);
        const totalTrips = userLogs.length;
        const initials = user.id.replace('USR-', '');
        const typeBadgeClass = user.type === 'Low Vision' ? 'low-vision' : '';

        return `
        <div class="student-card" onclick="showStudentDetail('${user.id}')" style="animation-delay: ${i * 0.04}s">
            <div class="student-avatar">${initials}</div>
            <div class="student-info">
                <div class="student-name">${user.name}</div>
                <div class="student-meta">
                    <span class="student-type-badge ${typeBadgeClass}">${user.type}</span>
                    <span>${user.id}</span>
                </div>
            </div>
            <div class="student-trips">
                <div class="student-trips-count">${totalTrips}</div>
                <div class="student-trips-label">trips</div>
            </div>
        </div>`;
    }).join('');
}

function filterByType(type, btn) {
    currentStudentFilter = type;
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    renderStudentList();
}

function filterStudentList() {
    renderStudentList();
}

// =============== STUDENT DETAIL ===============
function showStudentDetail(userId) {
    const user = appData.users.find(u => u.id === userId);
    if (!user) return;

    const userLogs = appData.logs.filter(l => l.userId === userId);
    const stats = getStats(userLogs);

    // Title
    document.getElementById('detail-title').textContent = user.name;

    // Profile
    const initials = user.id.replace('USR-', '');
    const typeBadgeClass = user.type === 'Low Vision' ? 'low-vision' : '';
    document.getElementById('detail-profile').innerHTML = `
        <div class="detail-avatar">${initials}</div>
        <div class="detail-info">
            <div class="detail-name">${user.name}</div>
            <div class="detail-meta">
                <span class="student-type-badge ${typeBadgeClass}">${user.type}</span>
                &nbsp; ${user.id}
            </div>
        </div>
    `;

    // Stats
    document.getElementById('detail-totalTrips').textContent = stats.total;
    document.getElementById('detail-avgFeb').textContent = formatDuration(Math.round(stats.avgFeb));
    document.getElementById('detail-avgApr').textContent = formatDuration(Math.round(stats.avgApr));

    // Trend chart
    renderDetailTrendChart(userLogs);

    // Favorite routes
    const topRoutes = getTopRoutes(userLogs, 5);
    const maxCount = topRoutes.length ? topRoutes[0][1] : 1;
    document.getElementById('detail-routes').innerHTML = topRoutes.map(([name, count], i) => {
        const rankClass = i < 3 ? `rank-${i + 1}` : 'rank-default';
        const barWidth = (count / maxCount * 100).toFixed(0);
        return `
        <div class="route-item">
            <div class="route-rank ${rankClass}">${i + 1}</div>
            <div class="route-info">
                <div class="route-name">${name}</div>
                <div class="route-count">${count} trips</div>
            </div>
            <div class="route-bar-wrap">
                <div class="route-bar" style="width: ${barWidth}%"></div>
            </div>
        </div>`;
    }).join('');

    // Recent logs
    document.getElementById('detail-logs').innerHTML = userLogs.slice(0, 10).map(log => `
        <div class="log-item">
            <div class="log-icon">
                <span class="material-icons-round">near_me</span>
            </div>
            <div class="log-info">
                <div class="log-route">${log.from} → ${log.to}</div>
                <div class="log-meta">
                    <span>${log.distance || ''}m</span>
                    <span>•</span>
                    <span>${log.date.toLocaleDateString('id-ID', { day: 'numeric', month: 'short' })} ${log.date.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })}</span>
                </div>
            </div>
            <div class="log-duration">${formatDuration(log.duration)}</div>
        </div>
    `).join('');

    navigateTo('student-detail');
}

function renderDetailTrendChart(logs) {
    const ctx = document.getElementById('detailTrendChart').getContext('2d');
    const c = getChartColors();
    const dailyAvg = [];
    for (let i = 0; i < 49; i++) {
        const dayLogs = logs.filter(l => l.dayIndex === i);
        const avg = dayLogs.length ? dayLogs.reduce((a, b) => a + b.duration, 0) / dayLogs.length : 0;
        dailyAvg.push(+(avg / 60).toFixed(2));
    }
    const labels = getDayLabels();

    if (detailTrendChart) detailTrendChart.destroy();

    const gradient = ctx.createLinearGradient(0, 0, 0, 200);
    gradient.addColorStop(0, 'rgba(139, 92, 246, 0.2)');
    gradient.addColorStop(1, 'rgba(139, 92, 246, 0.0)');

    detailTrendChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                data: dailyAvg,
                borderColor: c.purple,
                backgroundColor: gradient,
                borderWidth: 2.5,
                fill: true,
                tension: 0.4,
                pointRadius: 0,
                pointHitRadius: 10,
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            aspectRatio: 1.8,
            scales: {
                y: {
                    beginAtZero: true,
                    grid: { color: c.grid, drawBorder: false },
                    ticks: { color: c.tick, font: { size: 10 } },
                    title: { display: true, text: 'Menit', color: c.tick, font: { size: 10 } },
                    border: { display: false }
                },
                x: {
                    grid: { display: false },
                    ticks: { color: c.tick, font: { size: 9 }, maxTicksLimit: 6 },
                    border: { display: false }
                }
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#1a1a3a',
                    borderColor: c.purple,
                    borderWidth: 1,
                    cornerRadius: 8,
                    padding: 10,
                    callbacks: { label: ctx => ` ${ctx.parsed.y} menit` }
                }
            },
            interaction: { intersect: false, mode: 'index' }
        }
    });
}

// =============== LOG SCREEN ===============
function renderLogScreen() {
    const filterSelect = document.getElementById('logUserFilter');
    const dateInput = document.getElementById('logDateFilter');
    const dateClearBtn = document.getElementById('dateClearBtn');
    const dateFilterLabel = document.getElementById('dateFilterLabel');

    // Populate filter if empty
    if (filterSelect.options.length <= 1) {
        appData.users.forEach(user => {
            const option = document.createElement('option');
            option.value = user.id;
            option.textContent = `${user.id} (${user.type})`;
            filterSelect.appendChild(option);
        });
    }

    const selectedUser = filterSelect.value;
    const selectedDate = dateInput.value; // "YYYY-MM-DD" or ""

    // Filter by user
    let filteredLogs = selectedUser === 'all'
        ? appData.logs
        : appData.logs.filter(l => l.userId === selectedUser);

    // Filter by date
    if (selectedDate) {
        dateInput.classList.add('has-value');
        const picked = new Date(selectedDate);
        filteredLogs = filteredLogs.filter(l => {
            return l.date.getFullYear() === picked.getFullYear() &&
                   l.date.getMonth() === picked.getMonth() &&
                   l.date.getDate() === picked.getDate();
        });

        // Show clear button and label
        dateClearBtn.style.display = 'flex';
        dateFilterLabel.textContent = picked.toLocaleDateString('id-ID', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    } else {
        dateInput.classList.remove('has-value');
        dateClearBtn.style.display = 'none';
        dateFilterLabel.textContent = 'Semua Tanggal';
    }

    const container = document.getElementById('logList');
    const displayLogs = filteredLogs.slice(0, 80);

    if (displayLogs.length === 0) {
        container.innerHTML = `
            <div style="text-align: center; padding: 40px 20px;">
                <span class="material-icons-round" style="font-size: 48px; color: var(--text-muted); display: block; margin-bottom: 12px;">search_off</span>
                <p style="color: var(--text-secondary); font-size: 14px; font-weight: 500;">Tidak ada log ditemukan</p>
                <p style="color: var(--text-muted); font-size: 12px; margin-top: 4px;">Coba pilih tanggal atau siswa yang lain</p>
            </div>
        `;
        return;
    }

    container.innerHTML = displayLogs.map(log => `
        <div class="log-item">
            <div class="log-icon">
                <span class="material-icons-round">near_me</span>
            </div>
            <div class="log-info">
                <div class="log-route">${log.from} → ${log.to} <span style="color:#00d2ff;font-size:11px;font-weight:600;">${log.distance || ''}m</span></div>
                <div class="log-meta">
                    <span>${log.userId}</span>
                    <span>•</span>
                    <span>${log.userType}</span>
                    <span>•</span>
                    <span>${log.date.toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric' })} ${log.date.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })}</span>
                </div>
            </div>
            <div class="log-duration">${formatDuration(log.duration)}</div>
        </div>
    `).join('');
}

function clearDateFilter() {
    const dateInput = document.getElementById('logDateFilter');
    dateInput.value = '';
    dateInput.classList.remove('has-value');
    renderLogScreen();
}

// =============== INIT ===============
function initApp() {
    renderHomeScreen();
}

document.addEventListener('DOMContentLoaded', initApp);
