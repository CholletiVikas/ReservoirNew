document.addEventListener('DOMContentLoaded', function () {
    const dateInput = document.getElementById('dateInput');
    dateInput.value = todayLocalIso();           // default: present day
    dateInput.addEventListener('change', loadOverview);
    document.getElementById('downloadBtn').addEventListener('click', downloadExcel);
    loadOverview();
});

function todayLocalIso() {
    const d = new Date();
    const offset = d.getTimezoneOffset() * 60000;
    return new Date(d.getTime() - offset).toISOString().split('T')[0];
}

function selectedDate() {
    const v = document.getElementById('dateInput').value;
    return v || todayLocalIso();
}

async function loadOverview() {
    const content = document.getElementById('content');
    content.className = 'loading';
    content.textContent = 'Loading…';
    try {
        const response = await fetch('/api/overview?date=' + encodeURIComponent(selectedDate()));
        const data = await response.json();

        if (!response.ok || data.status === 'error') {
            content.className = '';
            content.innerHTML = '<div class="card">' +
                (data.message || 'Error loading overview') + '</div>';
            return;
        }

        content.className = '';
        content.innerHTML =
            summaryCard(data) +
            withDataCard(data.withData) +
            noDataCard(data.noData);
    } catch (e) {
        content.className = '';
        content.innerHTML = '<div class="card">Error loading overview. Please try again.</div>';
    }
}

function esc(v) {
    return (v === null || v === undefined) ? '' : String(v);
}

function summaryCard(data) {
    return '<div class="card summary">' +
        '<p>Date: <span>' + esc(data.date) + '</span></p>' +
        '<p>Reservoirs: <span>' + esc(data.withDataCount) + '/' + esc(data.total) + '</span></p>' +
        '</div>';
}

function withDataCard(rows) {
    let body = '';
    (rows || []).forEach(r => {
        body += '<tr><td>' + esc(r.name) + '</td>' +
            '<td class="num">' + esc(r.level) + '</td>' +
            '<td class="num">' + esc(r.storage) + '</td></tr>';
    });
    if (!body) {
        body = '<tr><td colspan="3">No reservoirs submitted yet today.</td></tr>';
    }
    return '<div class="card">' +
        '<table><thead><tr><th>Reservoir</th>' +
        '<th class="num">Level</th><th class="num">Storage</th></tr></thead>' +
        '<tbody>' + body + '</tbody></table></div>';
}

function noDataCard(rows) {
    if (!rows || rows.length === 0) {
        return '';
    }
    let body = '';
    rows.forEach(r => {
        body += '<tr><td>' + esc(r.name) + '</td>' +
            '<td class="nodata" colspan="2">No data</td></tr>';
    });
    return '<div class="card">' +
        '<div class="section-title">Reservoirs with No Data</div>' +
        '<table><thead><tr><th>Reservoir</th>' +
        '<th class="num">Level</th><th class="num">Storage</th></tr></thead>' +
        '<tbody>' + body + '</tbody></table></div>';
}

async function downloadExcel() {
    const btn = document.getElementById('downloadBtn');
    btn.disabled = true;
    const original = btn.textContent;
    btn.textContent = 'Preparing…';
    try {
        const response = await fetch('/api/download?date=' + encodeURIComponent(selectedDate()));
        if (!response.ok) {
            alert('Could not download the sheet. Make sure data was submitted on the selected date.');
            return;
        }
        const blob = await response.blob();
        const disposition = response.headers.get('Content-Disposition') || '';
        let fileName = 'reservoir.xlsx';
        const match = disposition.match(/filename="?([^"]+)"?/);
        if (match) fileName = match[1];

        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
    } catch (e) {
        alert('Error downloading the sheet. Please try again.');
    } finally {
        btn.disabled = false;
        btn.textContent = original;
    }
}
