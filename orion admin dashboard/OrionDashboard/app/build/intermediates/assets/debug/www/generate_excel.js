const ExcelJS = require('exceljs');
const fs = require('fs');
const https = require('https');

const generateFakeData = () => {
    const users = [];
    // Per-user skill multiplier (generated separately, not included in data.js)
    const skillMultipliers = {};

    for (let i = 1; i <= 25; i++) {
        let frequency;
        if (i <= 11) frequency = 'intense';
        else if (i <= 18) frequency = 'medium';
        else frequency = 'rare';

        const userId = `USR-${i.toString().padStart(3, '0')}`;
        skillMultipliers[userId] = 0.85 + Math.random() * 0.3; // 0.85–1.15

        users.push({
            id: userId,
            type: i >= 24 ? 'Low Vision' : 'Total',
            name: `Siswa ${i}`,
            frequency: frequency
        });
    }

    // ======= SCHOOL LAYOUT (84m x 55m) =======
    // Coordinate system: (0,0) = front-left corner, x→right, y→back
    const locationCoords = {
        'Satpam':       { x: 42, y: 2 },    // Front gate, center
        'Pendopo':      { x: 22, y: 12 },   // Near front, left area
        'R. Kepsek':    { x: 58, y: 8 },    // Near front, right area
        'R. Guru':      { x: 52, y: 20 },   // Middle right
        'Perpustakaan': { x: 14, y: 20 },   // Left middle
        'Toilet':       { x: 38, y: 30 },   // Central area
        'Kelas 7':      { x: 10, y: 40 },   // Left back
        'Kelas 10':     { x: 70, y: 42 },   // Right back (far from Kelas 7)
        'Kantin':       { x: 78, y: 25 },   // Right side
        'Musholla':     { x: 76, y: 52 },   // Right back corner
    };

    // Pre-calculate walking distances (cached per pair)
    const distanceCache = {};
    const getWalkingDistance = (from, to) => {
        const key = [from, to].sort().join('|');
        if (distanceCache[key]) return distanceCache[key];

        const a = locationCoords[from];
        const b = locationCoords[to];
        const straightLine = Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
        // Corridor/path factor: real buildings require walking around corridors
        const corridorFactor = 1.25 + Math.random() * 0.25; // 1.25–1.50x
        const walkDist = Math.round(straightLine * corridorFactor);
        distanceCache[key] = walkDist;
        return walkDist;
    };

    const destinations = Object.keys(locationCoords);
    const logs = [];
    const startDate = new Date('2026-02-27T06:00:00');
    const endDate = new Date('2026-04-16T14:00:00');

    const totalDays = Math.ceil((endDate.getTime() - startDate.getTime()) / (1000 * 3600 * 24));

    for (let day = 0; day < totalDays; day++) {
        const currentDate = new Date(startDate.getTime() + day * 24 * 60 * 60 * 1000);
        const dayOfWeek = currentDate.getDay();

        // Skip weekends
        if (dayOfWeek === 0 || dayOfWeek === 6) continue;

        const progress = day / Math.max(1, totalDays - 1); // 0.0 to 1.0

        // Number of active users decays (familiarity grows)
        const minUsers = 10 - Math.floor(progress * 7); // 10 → 3
        const maxUsers = 15 - Math.floor(progress * 9); // 15 → 6
        const numUsersToday = Math.floor(Math.random() * (maxUsers - minUsers + 1)) + minUsers;

        // Weighted user pool
        const userPool = [];
        users.forEach(user => {
            let weight = 1;
            if (user.frequency === 'intense') weight = 10;
            else if (user.frequency === 'medium') weight = 4;
            else if (user.frequency === 'rare') weight = 1;
            for (let w = 0; w < weight; w++) userPool.push(user);
        });

        const selectedUsers = new Set();
        while (selectedUsers.size < numUsersToday && userPool.length > 0) {
            const randomIndex = Math.floor(Math.random() * userPool.length);
            selectedUsers.add(userPool[randomIndex]);
        }

        const selectedUsersArray = Array.from(selectedUsers);
        const maxLateStayers = Math.max(1, Math.floor(4 * (1 - progress)));
        const numLateStayers = Math.min(
            selectedUsersArray.length,
            Math.floor(Math.random() * Math.max(1, maxLateStayers)) + Math.min(1, maxLateStayers)
        );
        const lateStayers = new Set(
            [...selectedUsersArray].sort(() => 0.5 - Math.random()).slice(0, numLateStayers)
        );

        selectedUsers.forEach(user => {
            const isLateStayer = lateStayers.has(user);

            // Trips decrease over time
            const minTrips = Math.max(1, 6 - Math.floor(progress * 5)); // 6 → 1
            const maxTrips = Math.max(minTrips, 10 - Math.floor(progress * 5)); // 10 → 5
            const trips = Math.floor(Math.random() * (maxTrips - minTrips + 1)) + minTrips;

            for (let t = 0; t < trips; t++) {
                let from = destinations[Math.floor(Math.random() * destinations.length)];
                let to = destinations[Math.floor(Math.random() * destinations.length)];
                while (from === to) {
                    to = destinations[Math.floor(Math.random() * destinations.length)];
                }

                const distance = getWalkingDistance(from, to);

                // ====== DISTANCE-BASED DURATION CALCULATION ======
                const isTotal = user.type === 'Total';

                // Walking speed (m/s): improves over time
                // Total:      0.7  → 1.0 m/s
                // Low Vision: 0.8  → 1.15 m/s
                const baseSpeed = isTotal ? 0.7 : 0.8;
                const targetSpeed = isTotal ? 0.78 : 0.88;
                const currentSpeed = baseSpeed + (targetSpeed - baseSpeed) * Math.pow(progress, 0.7);

                // Learning/hesitation factor: high at start, diminishes over time
                // Accounts for pausing, re-checking device, slight wrong turns
                const userSkill = skillMultipliers[user.id];
                const learningFactor = 1.0 + 0.4 * Math.pow(1 - progress, 1.5) * userSkill;

                // Base duration = distance / speed * learning factor
                let durationSec = (distance / currentSpeed) * learningFactor;

                // Random variance ±15%
                const variance = 0.85 + Math.random() * 0.30;
                durationSec = Math.round(durationSec * variance);

                // Clamp: minimum 30s, maximum 240s (4 minutes)
                durationSec = Math.max(30, Math.min(durationSec, 240));

                // ====== TIME BUCKET ======
                const isRandomTime = Math.random() < 0.15;
                let startHour, startMinute, endHour, endMinute;
                if (isRandomTime) {
                    startHour = 8; startMinute = 0; endHour = 14; endMinute = 0;
                } else {
                    let availableBuckets = [1, 2, 3];
                    if (isLateStayer) availableBuckets.push(4, 4);
                    const selectedBucket = availableBuckets[Math.floor(Math.random() * availableBuckets.length)];

                    if (selectedBucket === 1) { startHour = 7; startMinute = 0; endHour = 8; endMinute = 0; }
                    else if (selectedBucket === 2) { startHour = 12; startMinute = 0; endHour = 12; endMinute = 30; }
                    else if (selectedBucket === 3) { startHour = 14; startMinute = 0; endHour = 14; endMinute = 30; }
                    else if (selectedBucket === 4) { startHour = 14; startMinute = 30; endHour = 16; endMinute = 0; }
                }

                const tripDate = new Date(currentDate);
                tripDate.setHours(startHour, startMinute, 0, 0);
                const durationMs = ((endHour * 60 + endMinute) - (startHour * 60 + startMinute)) * 60 * 1000;
                const finalTripDate = new Date(tripDate.getTime() + Math.random() * durationMs);

                // Skip Apr 16 logs after 14:00
                if (finalTripDate.getFullYear() === 2026 && finalTripDate.getMonth() === 3 && finalTripDate.getDate() === 16 && finalTripDate.getHours() >= 14) {
                    continue;
                }

                logs.push({
                    date: finalTripDate.toISOString(),
                    userId: user.id,
                    userType: user.type,
                    from: from,
                    to: to,
                    distance: distance,
                    duration: durationSec,
                    dayIndex: day
                });
            }
        });
    }

    logs.sort((a, b) => new Date(b.date) - new Date(a.date));
    return { users, logs };
};

const appData = generateFakeData();

// ====== Write data.js ======
const dataJsContent = `const appData = {
    users: ${JSON.stringify(appData.users, null, 4)},
    logs: ${JSON.stringify(appData.logs.map(log => ({
        ...log,
        date: "__NEW_DATE__" + log.date
    })), null, 4).replace(/"__NEW_DATE__(.*?)"/g, "new Date('$1')")}
};`;

fs.writeFileSync('data.js', dataJsContent);
console.log('Successfully overwrote data.js with distance-based logic!');

// ====== Build Excel ======
async function buildExcel() {
    const workbook = new ExcelJS.Workbook();
    workbook.creator = 'Orion Admin';
    workbook.created = new Date();

    // --- SHEET 1: Navigation Logs ---
    const logSheet = workbook.addWorksheet('Navigation Logs', { views: [{ state: 'frozen', xSplit: 0, ySplit: 1 }] });
    logSheet.columns = [
        { header: 'Tanggal', key: 'tanggal', width: 15 },
        { header: 'Waktu', key: 'waktu', width: 12 },
        { header: 'User ID', key: 'userId', width: 12 },
        { header: 'Nama Siswa', key: 'nama', width: 25 },
        { header: 'Tipe Visual', key: 'type', width: 20 },
        { header: 'Asal', key: 'from', width: 15 },
        { header: 'Tujuan', key: 'to', width: 15 },
        { header: 'Jarak (m)', key: 'distance', width: 12 },
        { header: 'Durasi (Detik)', key: 'durasi', width: 15 },
        { header: 'Durasi (Format)', key: 'durasiFmt', width: 15 }
    ];

    logSheet.getRow(1).font = { bold: true, color: { argb: 'FFFFFFFF' } };
    logSheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF4F46E5' } };
    logSheet.autoFilter = 'A1:J1';

    appData.logs.forEach(log => {
        const d = new Date(log.date);
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, '0');
        const dd = String(d.getDate()).padStart(2, '0');
        const hh = String(d.getHours()).padStart(2, '0');
        const minTime = String(d.getMinutes()).padStart(2, '0');

        const dateStr = `${yyyy}-${mm}-${dd}`;
        const timeStr = `${hh}:${minTime}`;

        const user = appData.users.find(u => u.id === log.userId) || {};
        const min = Math.floor(log.duration / 60);
        const sec = log.duration % 60;

        logSheet.addRow({
            tanggal: dateStr,
            waktu: timeStr,
            userId: log.userId,
            nama: user.name || '',
            type: log.userType,
            from: log.from,
            to: log.to,
            distance: log.distance,
            durasi: log.duration,
            durasiFmt: `${min}m ${sec}s`
        });
    });

    // --- SHEET 2: Daftar Siswa ---
    const studentSheet = workbook.addWorksheet('Daftar Siswa', { views: [{ state: 'frozen', xSplit: 0, ySplit: 1 }] });
    studentSheet.columns = [
        { header: 'User ID', key: 'userId', width: 12 },
        { header: 'Nama', key: 'nama', width: 20 },
        { header: 'Tipe Visual', key: 'type', width: 20 },
        { header: 'Kategori Frekuensi', key: 'freq', width: 18 },
        { header: 'Total Perjalanan (Trip)', key: 'trips', width: 22 }
    ];
    studentSheet.getRow(1).font = { bold: true, color: { argb: 'FFFFFFFF' } };
    studentSheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF8B5CF6' } };
    studentSheet.autoFilter = 'A1:E1';

    appData.users.forEach(user => {
        const totalTrips = appData.logs.filter(l => l.userId === user.id).length;
        studentSheet.addRow({
            userId: user.id,
            nama: user.name,
            type: user.type,
            freq: user.frequency,
            trips: totalTrips
        });
    });

    // --- SHEET 3: Statistik Bulanan ---
    const statsSheet = workbook.addWorksheet('Statistik Bulanan');
    statsSheet.columns = [
        { header: 'Bulan', key: 'bulan', width: 15 },
        { header: 'Total Penggunaan (Trip)', key: 'total', width: 25 },
        { header: 'Avg Durasi (Detik)', key: 'avgSec', width: 18 },
        { header: 'Avg Durasi (Menit)', key: 'avgMin', width: 18 },
        { header: 'Avg Jarak (m)', key: 'avgDist', width: 15 }
    ];
    statsSheet.getRow(1).font = { bold: true };
    const monthNames = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'];
    [1, 2, 3].forEach(mIndex => {
        const logsInMonth = appData.logs.filter(l => new Date(l.date).getMonth() === mIndex);
        if (logsInMonth.length > 0) {
            const sumDur = logsInMonth.reduce((a, b) => a + b.duration, 0);
            const sumDist = logsInMonth.reduce((a, b) => a + b.distance, 0);
            const avgDur = sumDur / logsInMonth.length;
            const avgDist = sumDist / logsInMonth.length;
            statsSheet.addRow({
                bulan: monthNames[mIndex],
                total: logsInMonth.length,
                avgSec: Math.round(avgDur),
                avgMin: (avgDur / 60).toFixed(2),
                avgDist: Math.round(avgDist)
            });
        }
    });

    // --- SHEET 4: Top Rute ---
    const routeSheet = workbook.addWorksheet('Top Rute');
    routeSheet.columns = [
        { header: 'Nama Rute', key: 'route', width: 35 },
        { header: 'Jumlah Trip', key: 'count', width: 15 },
        { header: 'Jarak (m)', key: 'distance', width: 12 }
    ];
    routeSheet.getRow(1).font = { bold: true };

    const routes = {};
    const routeDistances = {};
    appData.logs.forEach(l => {
        const arr = [l.from, l.to].sort();
        const routeName = `${arr[0]} <-> ${arr[1]}`;
        routes[routeName] = (routes[routeName] || 0) + 1;
        routeDistances[routeName] = l.distance;
    });
    const sortedRoutes = Object.entries(routes).sort((a, b) => b[1] - a[1]);
    sortedRoutes.forEach(([rName, cnt]) => {
        routeSheet.addRow({ route: rName, count: cnt, distance: routeDistances[rName] || 0 });
    });

    // --- SHEET 5: Peningkatan Siswa ---
    const improvementSheet = workbook.addWorksheet('Peningkatan Siswa');
    improvementSheet.columns = [
        { header: 'ID Siswa', key: 'userId', width: 12 },
        { header: 'Nama Siswa', key: 'name', width: 25 },
        { header: 'Tipe Visual', key: 'type', width: 20 },
        { header: 'Total Trip', key: 'totalTrips', width: 12 },
        { header: 'Avg Jarak (m)', key: 'avgDist', width: 15 },
        { header: 'Waktu Rata-rata Awal (detik)', key: 'avgAwalSec', width: 28 },
        { header: 'Waktu Rata-rata Saat Ini (detik)', key: 'avgAkhirSec', width: 30 },
        { header: '% Peningkatan (Lebih Cepat)', key: 'improvementPercent', width: 30 }
    ];
    improvementSheet.getRow(1).font = { bold: true, color: { argb: 'FFFFFFFF' } };
    improvementSheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF10B981' } };
    improvementSheet.autoFilter = 'A1:H1';

    const maxDay = Math.max(...appData.logs.map(l => l.dayIndex));
    const improvementData = [];

    appData.users.forEach(user => {
        const userLogs = appData.logs.filter(l => l.userId === user.id);
        const earlyLogs = userLogs.filter(l => l.dayIndex <= 10);
        const lateLogs = userLogs.filter(l => l.dayIndex >= maxDay - 10);

        let avgAwal = earlyLogs.length > 0 ? earlyLogs.reduce((acc, c) => acc + c.duration, 0) / earlyLogs.length : 0;
        let avgAkhir = lateLogs.length > 0 ? lateLogs.reduce((acc, c) => acc + c.duration, 0) / lateLogs.length : 0;

        // Fallback for users with very few logs
        if (avgAwal === 0) avgAwal = (user.type === 'Total' ? 120 : 90);
        if (avgAkhir === 0) avgAkhir = (user.type === 'Total' ? 55 : 40);

        const improvement = avgAwal > 0 ? ((avgAwal - avgAkhir) / avgAwal) * 100 : 0;
        const avgDist = userLogs.length > 0 ? userLogs.reduce((a, c) => a + c.distance, 0) / userLogs.length : 0;

        improvementData.push({
            userId: user.id,
            name: user.name,
            type: user.type,
            totalTrips: userLogs.length,
            avgDist: Math.round(avgDist),
            avgAwalSec: Math.round(avgAwal),
            avgAkhirSec: Math.round(avgAkhir),
            improvementPercent: Math.round(improvement)
        });
    });

    improvementData.forEach(d => {
        const row = improvementSheet.addRow(d);
        // Highlight high-improvement rows
        if (d.improvementPercent > 35) {
            row.getCell('improvementPercent').font = { color: { argb: 'FF059669' }, bold: true };
        }
    });

    // --- Generate Chart Image via QuickChart ---
    const chartLabels = improvementData.map(d => d.name);
    const chartDataAwal = improvementData.map(d => Math.round(d.avgAwalSec));
    const chartDataAkhir = improvementData.map(d => Math.round(d.avgAkhirSec));

    const chartConfig = {
        type: 'bar',
        data: {
            labels: chartLabels,
            datasets: [
                { label: 'Waktu Awal (Detik)', data: chartDataAwal, backgroundColor: 'rgba(239, 68, 68, 0.7)' },
                { label: 'Saat Ini (Detik)', data: chartDataAkhir, backgroundColor: 'rgba(16, 185, 129, 0.7)' }
            ]
        },
        options: {
            title: { display: true, text: 'Peningkatan Waktu Navigasi Per Siswa (Detik)' },
            devicePixelRatio: 2
        }
    };

    const chartUrl = `https://quickchart.io/chart?w=900&h=450&c=${encodeURIComponent(JSON.stringify(chartConfig))}`;

    console.log('Downloading chart image...');
    try {
        await new Promise((resolve, reject) => {
            https.get(chartUrl, (res) => {
                if (res.statusCode !== 200) return reject(new Error('Failed fetching chart'));
                const writeStream = fs.createWriteStream('chart_improvement.png');
                res.pipe(writeStream);
                writeStream.on('finish', () => {
                    writeStream.close();
                    resolve();
                });
            }).on('error', reject);
        });

        const imageId = workbook.addImage({
            filename: 'chart_improvement.png',
            extension: 'png',
        });

        improvementSheet.addImage(imageId, {
            tl: { col: 9, row: 1 },
            ext: { width: 900, height: 450 }
        });
        console.log('Chart embedded successfully!');
    } catch (e) {
        console.error('Warning: Failed to generate or embed chart.', e.message);
    }

    await workbook.xlsx.writeFile('Orion_Navigation_Logs.xlsx');
    console.log('Successfully created Orion_Navigation_Logs.xlsx with distance-based metrics!');
}

buildExcel();
