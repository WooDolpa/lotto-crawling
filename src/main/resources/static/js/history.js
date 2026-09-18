(() => {
    'use strict';
    const { fetchJson, createMessenger, setupTabs, notify } = window.LottoCommon;
    const { validHistory, ball, sheet } = window.LottoSheet;
    const main = document.querySelector('main');
    const from = document.getElementById('from-draw');
    const to = document.getElementById('to-draw');
    const button = document.getElementById('search-button');
    const results = document.getElementById('results');
    const tabs = [document.getElementById('numbers-tab'), document.getElementById('cards-tab')];
    const footer = document.getElementById('history-footer');
    const pagination = document.getElementById('history-pagination');
    const pageSummary = document.getElementById('page-summary');
    const stat = id => document.getElementById(id);
    const stats = {
        total: stat('stat-total'), latest: stat('stat-latest'), latestMeta: stat('stat-latest-meta'),
        range: stat('stat-range'), rangeMeta: stat('stat-range-meta'),
        frequent: stat('stat-frequent'), frequentMeta: stat('stat-frequent-meta')
    };
    const message = createMessenger(document.getElementById('status'));
    const pageSize = 20;
    const arrowLabels = { '«': '첫 페이지', '‹': '이전 페이지', '›': '다음 페이지', '»': '마지막 페이지' };
    let histories = [];
    let page = 1;
    let view = 'numbers';
    let draws = [];
    let activeRequest;

    function numberList(items) {
        const table = document.createElement('table');
        table.className = 'data-table';
        const caption = document.createElement('caption');
        caption.className = 'visually-hidden'; caption.textContent = '회차별 당첨 번호 6개';
        const head = document.createElement('thead');
        const header = document.createElement('tr');
        ['회차', '당첨 번호'].forEach(label => {
            const cell = document.createElement('th'); cell.scope = 'col'; cell.textContent = label;
            header.append(cell);
        });
        head.append(header);
        const body = document.createElement('tbody');
        items.forEach(item => {
            const row = document.createElement('tr');
            const draw = document.createElement('th'); draw.scope = 'row'; draw.className = 'cell-strong'; draw.textContent = `${item.drawNo}회`;
            const numbers = document.createElement('td');
            const balls = document.createElement('div'); balls.className = 'ball-row';
            [...item.numbers].sort((a, b) => a - b).forEach(number => balls.append(ball(number)));
            numbers.append(balls); row.append(draw, numbers); body.append(row);
        });
        table.append(caption, head, body); return table;
    }
    function updateStats() {
        const latest = draws[draws.length - 1];
        stats.total.textContent = draws.length ? `${draws.length.toLocaleString('ko-KR')}회` : '-';
        stats.latest.textContent = latest ? `${latest}회` : '-';
        stats.latestMeta.textContent = latest ? '저장된 마지막 회차' : '-';
        stats.range.textContent = `${histories.length.toLocaleString('ko-KR')}회차`;
        stats.rangeMeta.textContent = histories.length ? `${histories[0].drawNo}회 ~ ${histories[histories.length - 1].drawNo}회` : '조회 결과 없음';
        const counts = new Map();
        histories.forEach(item => item.numbers.forEach(number => counts.set(number, (counts.get(number) || 0) + 1)));
        const [top, count] = [...counts].sort((a, b) => b[1] - a[1] || a[0] - b[0])[0] || [];
        stats.frequent.replaceChildren(top ? ball(top) : '-');
        stats.frequentMeta.textContent = top ? `${count}회 출현` : '조회 결과 없음';
    }
    function render() {
        const totalPages = Math.ceil(histories.length / pageSize);
        page = Math.max(1, Math.min(page, totalPages || 1));
        const offset = (page - 1) * pageSize;
        const items = histories.slice(offset, offset + pageSize);
        results.className = view === 'cards' ? 'sheet-grid' : 'table-wrap';
        results.setAttribute('aria-labelledby', view === 'cards' ? 'cards-tab' : 'numbers-tab');
        results.replaceChildren();
        if (items.length) {
            if (view === 'cards') {
                const fragment = document.createDocumentFragment();
                items.forEach(item => fragment.append(sheet(item))); results.append(fragment);
            } else results.append(numberList(items));
        }
        pagination.replaceChildren(); footer.hidden = !histories.length;
        if (!histories.length) return;
        pageSummary.textContent = `전체 ${histories.length}회차 중 ${offset + 1}–${offset + items.length}`;
        function pageButton(label, target, disabled = false) {
            const control = document.createElement('button');
            control.type = 'button'; control.className = 'page-button'; control.textContent = label;
            control.disabled = disabled;
            if (target === page && /^\d+$/.test(label)) control.setAttribute('aria-current', 'page');
            if (/^\d+$/.test(label)) control.setAttribute('aria-label', `${label}페이지`);
            if (arrowLabels[label]) {
                control.setAttribute('aria-label', arrowLabels[label]);
                control.title = arrowLabels[label];
            }
            control.addEventListener('click', () => {
                page = target; render();
                pagination.querySelector('[aria-current="page"]').focus();
                message(`${page} / ${totalPages}페이지 · ${Math.min(pageSize, histories.length - (page - 1) * pageSize)}개 회차를 표시합니다.`);
            });
            pagination.append(control);
        }
        pageButton('«', 1, page === 1);
        pageButton('‹', page - 1, page === 1);
        const start = Math.max(1, Math.min(page - 1, totalPages - 2));
        const end = Math.min(totalPages, start + 2);
        for (let i = start; i <= end; i++) pageButton(String(i), i);
        pageButton('›', page + 1, page === totalPages);
        pageButton('»', totalPages, page === totalPages);
    }
    setupTabs(tabs, index => {
        view = index === 0 ? 'numbers' : 'cards';
        render();
    });

    function options(select, values, selected) {
        select.replaceChildren(...values.map(value => new Option(`${value}회`, String(value))));
        select.value = String(selected);
    }
    function updateEnd() {
        const allowed = draws.filter(draw => draw >= Number(from.value));
        const current = Number(to.value);
        options(to, allowed, allowed.includes(current) ? current : allowed[0]);
    }
    async function search() {
        const start = Number(from.value), end = Number(to.value);
        if (!draws.includes(start) || !draws.includes(end) || start > end) {
            notify('올바른 추첨 회차 범위를 선택해 주세요.', { type: 'error', title: '검색 범위를 확인해 주세요' });
            return;
        }
        if (activeRequest) activeRequest.abort();
        const request = new AbortController();
        activeRequest = request;
        button.disabled = true;
        results.setAttribute('aria-busy', 'true');
        results.replaceChildren();
        histories = []; page = 1;
        pagination.replaceChildren(); footer.hidden = true;
        message('당첨 이력을 불러오는 중입니다.');
        try {
            const url = new URL(main.dataset.historyUrl, location.href);
            url.searchParams.set('fromDrawNo', start);
            url.searchParams.set('toDrawNo', end);
            const items = await fetchJson(url, { signal: request.signal });
            if (!Array.isArray(items) || !items.every(validHistory)) throw new Error('잘못된 응답');
            items.sort((a, b) => a.drawNo - b.drawNo);
            histories = items; render(); updateStats();
            message(items.length ? '' : '선택한 범위에 저장된 당첨 이력이 없습니다.');
        } catch (error) {
            if (error.name !== 'AbortError') {
                message('');
                notify('다시 검색해 주세요.', { type: 'error', title: '당첨 이력을 불러오지 못했습니다' });
            }
        } finally {
            if (activeRequest === request) {
                button.disabled = false;
                results.setAttribute('aria-busy', 'false');
            }
        }
    }
    from.addEventListener('change', updateEnd);
    document.getElementById('search-form').addEventListener('submit', event => {
        event.preventDefault();
        search();
    });
    async function initialize() {
        try {
            const data = await fetchJson(main.dataset.drawsUrl);
            if (!Array.isArray(data) || !data.every(draw => Number.isInteger(draw) && draw > 0)) throw new Error('잘못된 응답');
            draws = [...new Set(data)].sort((a, b) => a - b);
            updateStats();
            if (!draws.length) {
                from.replaceChildren(new Option('저장된 회차 없음', ''));
                to.replaceChildren(new Option('저장된 회차 없음', ''));
                message('저장된 당첨 이력이 없습니다. 데이터를 먼저 적재해 주세요.');
                return;
            }
            options(from, draws, draws[Math.max(0, draws.length - 10)]);
            options(to, draws.filter(draw => draw >= Number(from.value)), draws[draws.length - 1]);
            from.disabled = false;
            to.disabled = false;
            await search();
        } catch (error) {
            from.replaceChildren(new Option('조회 실패', ''));
            to.replaceChildren(new Option('조회 실패', ''));
            message('');
            notify('페이지를 새로고침해 주세요.', { type: 'error', title: '회차 목록을 불러오지 못했습니다' });
        } finally {
            results.setAttribute('aria-busy', 'false');
        }
    }
    initialize();
})();
