(() => {
    'use strict';
    const { getJson, validHistory, sheet } = window.LottoSheet;
    const main = document.querySelector('main');
    const from = document.getElementById('from-draw');
    const to = document.getElementById('to-draw');
    const button = document.getElementById('search-button');
    const results = document.getElementById('results');
    const status = document.getElementById('status');
    const tabs = [document.getElementById('numbers-tab'), document.getElementById('cards-tab')];
    const pagination = document.getElementById('history-pagination');
    const pageSize = 20;
    let histories = [];
    let page = 1;
    let view = 'numbers';
    let draws = [];
    let activeRequest;

    function numberList(items) {
        const table = document.createElement('table');
        table.className = 'history-table';
        const caption = document.createElement('caption');
        caption.className = 'visually-hidden'; caption.textContent = '회차별 당첨 번호 6개';
        const head = document.createElement('thead');
        const header = document.createElement('tr');
        ['회차', '추첨일', '당첨 번호'].forEach(label => {
            const cell = document.createElement('th'); cell.scope = 'col'; cell.textContent = label; header.append(cell);
        });
        head.append(header);
        const body = document.createElement('tbody');
        items.forEach(item => {
            const row = document.createElement('tr');
            const draw = document.createElement('th'); draw.scope = 'row'; draw.textContent = `${item.drawNo}회`;
            const date = document.createElement('td'); date.textContent = item.drawDate || '추첨일 없음';
            const numbers = document.createElement('td');
            const balls = document.createElement('div'); balls.className = 'history-balls';
            [...item.numbers].sort((a, b) => a - b).forEach(number => {
                const ball = document.createElement('span');
                ball.className = `history-ball ball-group-${Math.min(5, Math.ceil(number / 10))}`;
                ball.textContent = String(number); balls.append(ball);
            });
            numbers.append(balls); row.append(draw, date, numbers); body.append(row);
        });
        table.append(caption, head, body); return table;
    }
    function render() {
        const totalPages = Math.ceil(histories.length / pageSize);
        page = Math.max(1, Math.min(page, totalPages || 1));
        const offset = (page - 1) * pageSize;
        const items = histories.slice(offset, offset + pageSize);
        results.className = view === 'cards' ? 'sheets' : 'history-numbers';
        results.setAttribute('aria-labelledby', view === 'cards' ? 'cards-tab' : 'numbers-tab');
        results.replaceChildren();
        if (items.length) {
            if (view === 'cards') {
                const fragment = document.createDocumentFragment();
                items.forEach(item => fragment.append(sheet(item))); results.append(fragment);
            } else results.append(numberList(items));
        }
        pagination.replaceChildren(); pagination.hidden = !histories.length;
        if (!histories.length) return;
        function pageButton(label, target, disabled = false) {
            const control = document.createElement('button');
            control.type = 'button'; control.className = 'page-button'; control.textContent = label;
            control.disabled = disabled;
            if (target === page && /^\d+$/.test(label)) control.setAttribute('aria-current', 'page');
            if (/^\d+$/.test(label)) control.setAttribute('aria-label', `${label}페이지`);
            const arrowLabels = { '<<': '첫 페이지', '<': '이전 페이지', '>': '다음 페이지', '>>': '마지막 페이지' };
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
        pageButton('<<', 1, page === 1);
        pageButton('<', page - 1, page === 1);
        const start = Math.max(1, Math.min(page - 1, totalPages - 2));
        const end = Math.min(totalPages, start + 2);
        for (let i = start; i <= end; i++) pageButton(String(i), i);
        pageButton('>', page + 1, page === totalPages);
        pageButton('>>', totalPages, page === totalPages);
    }
    function selectTab(index) {
        view = index === 0 ? 'numbers' : 'cards';
        tabs.forEach((tab, i) => { tab.setAttribute('aria-selected', String(i === index)); tab.tabIndex = i === index ? 0 : -1; });
        render();
    }
    tabs.forEach((tab, index) => {
        tab.addEventListener('click', () => selectTab(index));
        tab.addEventListener('keydown', event => {
            if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
            event.preventDefault();
            const next = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : 1 - index;
            selectTab(next); tabs[next].focus();
        });
    });

    function message(text, error = false) {
        status.textContent = text;
        status.classList.toggle('error', error);
    }
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
            message('올바른 추첨 회차 범위를 선택해 주세요.', true);
            return;
        }
        if (activeRequest) activeRequest.abort();
        const request = new AbortController();
        activeRequest = request;
        button.disabled = true;
        results.setAttribute('aria-busy', 'true');
        results.replaceChildren();
        histories = []; page = 1;
        pagination.replaceChildren(); pagination.hidden = true;
        message('당첨 이력을 불러오는 중입니다.');
        try {
            const url = new URL(main.dataset.historyUrl, location.href);
            url.searchParams.set('fromDrawNo', start);
            url.searchParams.set('toDrawNo', end);
            const items = await getJson(url, request.signal);
            if (!Array.isArray(items) || !items.every(validHistory)) throw new Error('잘못된 응답');
            items.sort((a, b) => a.drawNo - b.drawNo);
            histories = items; render();
            message(items.length ? '' : '선택한 범위에 저장된 당첨 이력이 없습니다.');
        } catch (error) {
            if (error.name !== 'AbortError') message('당첨 이력을 불러오지 못했습니다. 다시 검색해 주세요.', true);
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
            const data = await getJson(main.dataset.drawsUrl);
            if (!Array.isArray(data) || !data.every(draw => Number.isInteger(draw) && draw > 0)) throw new Error('잘못된 응답');
            draws = [...new Set(data)].sort((a, b) => a - b);
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
            message('회차 목록을 불러오지 못했습니다. 페이지를 새로고침해 주세요.', true);
        } finally {
            results.setAttribute('aria-busy', 'false');
        }
    }
    initialize();
})();
