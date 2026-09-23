(() => {
    'use strict';
    const { fetchJson, createMessenger, notify } = window.LottoCommon;
    const main = document.querySelector('main');
    const from = document.getElementById('from-draw');
    const to = document.getElementById('to-draw');
    const presets = [...document.querySelectorAll('[data-recent]')];
    const canvas = document.getElementById('winners-chart');
    const table = document.getElementById('winners-table');
    const yearlyTable = document.getElementById('yearly-table');
    const chartMeta = document.getElementById('chart-meta');
    const message = createMessenger(document.getElementById('status'));
    const stat = id => document.getElementById(id);
    const darkScheme = window.matchMedia('(prefers-color-scheme: dark)');
    /** 한 해 회차 수. 이보다 적은 해는 일부만 집계한 것으로 표시한다 */
    const DRAWS_PER_YEAR = 52;
    let all = [];
    let draws = [];
    let byDrawNo = new Map();
    let chart;
    /** 선택한 기간의 평균 (툴팁이 읽는다) */
    let average = 0;

    /** 캔버스는 CSS 변수를 읽지 못하므로 토큰 값을 꺼내 넘긴다 */
    function tokens() {
        const style = getComputedStyle(document.documentElement);
        const read = name => style.getPropertyValue(name).trim();
        return {
            font: read('--font-sans'), accent: read('--accent'),
            text: read('--text'), secondary: read('--text-secondary'), tertiary: read('--text-tertiary'),
            border: read('--border'), bg: read('--bg'), surface: read('--surface')
        };
    }

    function formatDate(value) {
        return value && value.length === 8 ? `${value.slice(0, 4)}.${value.slice(4, 6)}.${value.slice(6)}` : '-';
    }
    function formatAmount(value) {
        if (value == null) return '-';
        if (value >= 1e8) return `${(value / 1e8).toLocaleString('ko-KR', { maximumFractionDigits: 1 })}억 원`;
        return `${Math.round(value / 1e4).toLocaleString('ko-KR')}만 원`;
    }
    function formatCount(value, digits = 0) {
        // 평균처럼 소수를 보이는 값은 자릿수를 고정한다 (11명과 10.3명이 섞이지 않게)
        return `${value.toLocaleString('ko-KR', { minimumFractionDigits: digits, maximumFractionDigits: digits })}명`;
    }
    /** 판매량 대비 지수(실제 ÷ 기대)를 기대보다 몇 % 많거나 적은지로 표시 */
    function formatIndex(ratio) {
        // 소수 첫째 자리로 먼저 반올림해야 −0.04%가 "−0.0%"로 나오지 않는다
        const percent = Math.round((ratio - 1) * 1000) / 10;
        const sign = percent > 0 ? '+' : percent < 0 ? '−' : '';
        return `${sign}${Math.abs(percent).toFixed(1)}%`;
    }
    function mean(values) {
        return values.reduce((sum, value) => sum + value, 0) / values.length;
    }

    /** 당첨자 수가 가장 많은(또는 적은) 회차. 같은 값이 여럿이면 최근 회차와 동점 수를 함께 반환 */
    function extreme(rows, better) {
        let best = rows[0];
        let ties = 0;
        rows.forEach(row => {
            if (better(row.winners, best.winners)) { best = row; ties = 1; }
            else if (row.winners === best.winners) { best = row; ties++; }
        });
        return { row: best, ties };
    }
    function extremeMeta({ row, ties }) {
        const others = ties > 1 ? ` 외 ${ties - 1}회` : '';
        return `${row.drawNo}회${others} · 1인당 ${formatAmount(row.amountPerWinner)}`;
    }

    /**
     * 판매량 대비 지수: 실제 1등 합계 ÷ 기대 1등 합계 (기대값이 있는 회차만, 서버가 836회 미만은 null로 준다)
     *
     * @return 계산할 회차가 없으면 null
     */
    function popularity(rows) {
        const usable = rows.filter(row => row.winners != null && row.expectedWinners != null);
        if (!usable.length) return null;
        const actual = usable.reduce((sum, row) => sum + row.winners, 0);
        const expected = usable.reduce((sum, row) => sum + row.expectedWinners, 0);
        return { ratio: actual / expected, actual, expected, draws: usable.length };
    }

    function renderStats(rows) {
        const max = extreme(rows, (a, b) => a > b);
        const min = extreme(rows, (a, b) => a < b);
        const index = popularity(rows);
        stat('stat-average').textContent = formatCount(average, 1);
        stat('stat-average-meta').textContent = `${rows.length.toLocaleString('ko-KR')}회차 기준`;
        stat('stat-max').textContent = formatCount(max.row.winners);
        stat('stat-max-meta').textContent = extremeMeta(max);
        stat('stat-min').textContent = formatCount(min.row.winners);
        stat('stat-min-meta').textContent = extremeMeta(min);
        stat('stat-index').textContent = index ? formatIndex(index.ratio) : '-';
        stat('stat-index-meta').textContent = index
            ? `실제 ${formatCount(index.actual)} / 기대 ${formatCount(index.expected, 1)}`
            : '판매금액이 있는 회차가 없습니다';
    }

    function clearStats() {
        ['average', 'max', 'min', 'index'].forEach(key => {
            stat(`stat-${key}`).textContent = '-';
            stat(`stat-${key}-meta`).textContent = '-';
        });
    }

    function cell(text, sub) {
        const td = document.createElement('td');
        td.textContent = text;
        if (sub) {
            const small = document.createElement('span');
            small.className = 'cell-sub';
            small.textContent = sub;
            td.append(small);
        }
        return td;
    }
    function rowHeader(text, sub) {
        const th = document.createElement('th');
        th.scope = 'row';
        th.className = 'cell-strong';
        th.textContent = text;
        if (sub) {
            const small = document.createElement('span');
            small.className = 'cell-sub';
            small.textContent = sub;
            th.append(small);
        }
        return th;
    }

    function renderTable(rows) {
        const fragment = document.createDocumentFragment();
        [...rows].reverse().forEach(row => {
            const tr = document.createElement('tr');
            tr.append(rowHeader(`${row.drawNo}회`), cell(formatDate(row.drawDate)),
                cell(row.winners == null ? '-' : formatCount(row.winners)), cell(formatAmount(row.amountPerWinner)));
            fragment.append(tr);
        });
        table.replaceChildren(fragment);
    }

    /** 연도별 비교 (기간 선택과 무관하게 전체 회차로 계산) */
    function renderYearly() {
        const years = new Map();
        all.filter(row => row.winners != null && row.drawDate).forEach(row => {
            const year = row.drawDate.slice(0, 4);
            if (!years.has(year)) years.set(year, []);
            years.get(year).push(row);
        });
        const fragment = document.createDocumentFragment();
        [...years].sort(([a], [b]) => a.localeCompare(b)).forEach(([year, rows]) => {
            const max = extreme(rows, (a, b) => a > b).row;
            const min = extreme(rows, (a, b) => a < b).row;
            const amounts = rows.map(row => row.amountPerWinner).filter(value => value != null);
            const index = popularity(rows);
            const tr = document.createElement('tr');
            tr.append(
                rowHeader(`${year}년`, rows.length < DRAWS_PER_YEAR ? '일부' : null),
                cell(`${rows.length}회`),
                cell(formatCount(mean(rows.map(row => row.winners)), 1)),
                cell(`${formatCount(max.winners)} · ${formatCount(min.winners)}`, `${max.drawNo}회 · ${min.drawNo}회`),
                cell(amounts.length ? formatAmount(mean(amounts)) : '-'),
                cell(index ? formatIndex(index.ratio) : '-',
                    index ? `실제 ${index.actual.toLocaleString('ko-KR')} / 기대 ${index.expected.toFixed(1)}` : null));
            fragment.append(tr);
        });
        yearlyTable.replaceChildren(fragment);
    }

    function applyTheme() {
        if (!chart) return;
        const color = tokens();
        const [winners, averageLine] = chart.data.datasets;
        winners.borderColor = color.accent;
        winners.pointHoverBackgroundColor = color.accent;
        winners.pointHoverBorderColor = color.surface;
        // 평균선은 당첨자 선(포인트 색)과 구분되도록 본문 색으로 그린다
        averageLine.borderColor = color.text;
        const { x, y } = chart.options.scales;
        [x, y].forEach(scale => { scale.ticks.color = color.tertiary; scale.border.color = color.border; });
        y.grid.color = color.border;
        y.title.color = color.secondary;
        const tooltip = chart.options.plugins.tooltip;
        tooltip.backgroundColor = color.text;
        tooltip.titleColor = color.bg;
        tooltip.bodyColor = color.bg;
        chart.options.plugins.legend.labels.color = color.secondary;
        chart.update('none');
    }

    function createChart() {
        Chart.defaults.font.family = tokens().font;
        chart = new Chart(canvas, {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    // 동기화 전 회차는 null이라 선이 끊긴다 (0명과 구분, spanGaps 기본값 false).
                    // 점이 수백 개라 평소에는 숨기고 마우스를 올린 회차만 표시한다.
                    { label: '1등 당첨자 수', data: [], borderWidth: 1.5, pointRadius: 0, pointHoverRadius: 4, pointHoverBorderWidth: 2, order: 2, pointStyle: 'line' },
                    // order가 작을수록 위에 그려진다 (평균선을 당첨자 선 위에). pointStyle은 범례 모양에만 쓴다.
                    { label: '평균', data: [], borderWidth: 2, borderDash: [6, 4], pointRadius: 0, pointHoverRadius: 0, order: 1, pointStyle: 'line' }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                interaction: { mode: 'index', intersect: false },
                scales: {
                    x: { grid: { display: false }, border: {}, ticks: { maxRotation: 0, autoSkipPadding: 16, callback(value) { return `${this.getLabelForValue(value)}회`; } } },
                    y: { beginAtZero: true, border: {}, grid: {}, ticks: { precision: 0 }, title: { display: true, text: '당첨자 수 (명)' } }
                },
                plugins: {
                    // 범례·툴팁은 그리는 순서(order)가 아니라 데이터 순서대로 (당첨자 수 → 평균)
                    legend: { position: 'top', align: 'end', labels: { usePointStyle: true, boxWidth: 12, boxHeight: 12, sort: (a, b) => a.datasetIndex - b.datasetIndex } },
                    tooltip: {
                        displayColors: false,
                        itemSort: (a, b) => a.datasetIndex - b.datasetIndex,
                        callbacks: {
                            title: items => {
                                const row = byDrawNo.get(Number(items[0].label));
                                return `${row.drawNo}회 · ${formatDate(row.drawDate)}`;
                            },
                            label: item => item.datasetIndex === 0
                                ? (item.raw == null ? '동기화 전' : `1등 ${formatCount(item.raw)}`)
                                : `평균 ${formatCount(average, 1)}`,
                            afterLabel: item => item.datasetIndex === 0 && item.raw != null
                                ? `1인당 ${formatAmount(byDrawNo.get(Number(item.label)).amountPerWinner)}` : ''
                        }
                    }
                }
            }
        });
        applyTheme();
        darkScheme.addEventListener('change', applyTheme);
    }

    function renderChart(range) {
        if (typeof Chart === 'undefined') return;
        if (!chart) createChart();
        chart.data.labels = range.map(row => String(row.drawNo));
        chart.data.datasets[0].data = range.map(row => row.winners);
        chart.data.datasets[1].data = range.map(() => average);
        chart.update('none');
    }

    /** 선택한 회차 범위로 요약·차트·표를 다시 그림 */
    function applyRange() {
        const start = Number(from.value), end = Number(to.value);
        const range = all.filter(row => row.drawNo >= start && row.drawNo <= end);
        const rows = range.filter(row => row.winners != null);
        presets.forEach(button => button.setAttribute('aria-pressed', String(presetMatches(button, start, end))));
        chartMeta.textContent = `${start}회 ~ ${end}회`;
        renderTable(range);
        if (!rows.length) {
            average = 0;
            clearStats();
            renderChart([]);
            message('이 기간에는 1등 당첨자 수가 있는 회차가 없습니다. /system/sync로 동기화해 주세요.', true);
            return;
        }
        average = mean(rows.map(row => row.winners));
        renderStats(rows);
        renderChart(range);
        message(typeof Chart === 'undefined' ? '차트 라이브러리를 불러오지 못했습니다. 아래 표로 확인해 주세요.' : '',
            typeof Chart === 'undefined');
        canvas.setAttribute('aria-label', `${start}회부터 ${end}회까지 회차별 1등 당첨자 수 꺾은선 차트. 평균 ${formatCount(average, 1)}.`);
    }

    /** 빠른 선택 버튼이 가리키는 시작 회차 (최근 N회 또는 전체) */
    function presetStart(button) {
        const recent = button.dataset.recent;
        return recent === 'all' ? draws[0] : draws[Math.max(0, draws.length - Number(recent))];
    }
    function presetMatches(button, start, end) {
        return end === draws[draws.length - 1] && start === presetStart(button);
    }

    function options(select, values, selected) {
        select.replaceChildren(...values.map(value => new Option(`${value}회`, String(value))));
        select.value = String(selected);
    }
    /** 종료 회차는 시작 회차 이후만 고를 수 있게 */
    function updateEnd() {
        const allowed = draws.filter(draw => draw >= Number(from.value));
        const current = Number(to.value);
        options(to, allowed, allowed.includes(current) ? current : allowed[allowed.length - 1]);
    }

    from.addEventListener('change', () => { updateEnd(); applyRange(); });
    to.addEventListener('change', applyRange);
    presets.forEach(button => button.addEventListener('click', () => {
        options(from, draws, presetStart(button));
        options(to, draws.filter(draw => draw >= Number(from.value)), draws[draws.length - 1]);
        applyRange();
    }));

    async function load() {
        try {
            all = await fetchJson(main.dataset.url);
            draws = all.map(row => row.drawNo);
            byDrawNo = new Map(all.map(row => [row.drawNo, row]));
            if (!draws.length) {
                message('저장된 당첨 이력이 없습니다. /system/sync로 동기화해 주세요.', true);
                return;
            }
            renderYearly();
            options(from, draws, draws[0]);
            options(to, draws, draws[draws.length - 1]);
            [from, to, ...presets].forEach(control => { control.disabled = false; });
            applyRange();
        } catch (error) {
            message('');
            notify(error.message, { type: 'error', title: '1등 당첨자수를 불러오지 못했습니다' });
        }
    }

    load();
})();
