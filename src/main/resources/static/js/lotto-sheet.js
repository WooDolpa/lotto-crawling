(() => {
    'use strict';
    async function getJson(url, signal) {
        const response = await fetch(url, { signal, headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error('조회 실패');
        return response.json();
    }
    const ns = 'http://www.w3.org/2000/svg';
    function svgElement(name, attributes, text) {
        const element = document.createElementNS(ns, name);
        Object.entries(attributes).forEach(([key, value]) => element.setAttribute(key, value));
        if (text !== undefined) element.textContent = text;
        return element;
    }
    function point(number) {
        return { x: 25 + ((number - 1) % 7) * 28, y: 30 + Math.floor((number - 1) / 7) * 33 };
    }
    function validHistory(item) {
        return item && Number.isInteger(item.drawNo) && item.drawNo > 0 &&
            Array.isArray(item.numbers) && item.numbers.length === 6 &&
            new Set(item.numbers).size === 6 &&
            item.numbers.every(number => Number.isInteger(number) && number >= 1 && number <= 45);
    }
    function sheet(item) {
        const numbers = [...item.numbers].sort((a, b) => a - b);
        const card = document.createElement('article');
        card.className = 'sheet-card';
        const heading = document.createElement('div');
        heading.className = 'sheet-heading';
        const title = document.createElement('h3');
        title.textContent = `${item.drawNo}회`;
        const date = document.createElement('time');
        date.textContent = item.drawDate || '추첨일 없음';
        if (item.drawDate) date.dateTime = item.drawDate;
        heading.append(title, date);
        const svg = svgElement('svg', { viewBox: '0 0 218 250', class: 'lotto-sheet', role: 'img', 'aria-label': `${item.drawNo}회 당첨 번호 ${numbers.join(', ')}, 오름차순 연결` });
        svg.append(svgElement('title', {}, `${item.drawNo}회: ${numbers.join(', ')}`));
        svg.append(svgElement('rect', { x: 1, y: 1, width: 216, height: 248, fill: '#fff', stroke: '#ff9096', 'stroke-width': 1 }));
        svg.append(svgElement('polyline', { points: numbers.map(number => { const p = point(number); return `${p.x},${p.y}`; }).join(' '), fill: 'none', stroke: '#64748b', 'stroke-opacity': .65, 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }));
        for (let number = 1; number <= 45; number++) {
            const { x, y } = point(number);
            const selected = numbers.includes(number);
            if (selected) svg.append(svgElement('rect', { x: x - 11, y: y - 11, width: 22, height: 22, rx: 2, fill: '#ef7179', 'fill-opacity': .3 }));
            svg.append(svgElement('path', { d: `M ${x - 11} ${y - 5} v -6 h 7 M ${x + 4} ${y - 11} h 7 v 6 M ${x - 11} ${y + 5} v 6 h 7 M ${x + 4} ${y + 11} h 7 v -6`, fill: 'none', stroke: '#ff9096', 'stroke-width': .8 }));
            svg.append(svgElement('text', { x, y: y + 4, 'text-anchor': 'middle', class: `number-text${selected ? ' selected' : ''}` }, String(number)));
        }
        card.append(heading, svg);
        return card;
    }
    window.LottoSheet = Object.freeze({ getJson, validHistory, sheet });
})();
