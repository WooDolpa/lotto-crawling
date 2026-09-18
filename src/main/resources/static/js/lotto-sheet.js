(() => {
    'use strict';
    const ns = 'http://www.w3.org/2000/svg';
    function svgElement(name, attributes, text) {
        const element = document.createElementNS(ns, name);
        Object.entries(attributes).forEach(([key, value]) => element.setAttribute(key, value));
        if (text !== undefined) element.textContent = text;
        return element;
    }
    // 용지 7열 배치 (가로 28, 세로 33 간격)
    function point(number) {
        return { x: 25 + ((number - 1) % 7) * 28, y: 30 + Math.floor((number - 1) / 7) * 33 };
    }
    function validHistory(item) {
        return item && Number.isInteger(item.drawNo) && item.drawNo > 0 &&
            Array.isArray(item.numbers) && item.numbers.length === 6 &&
            new Set(item.numbers).size === 6 &&
            item.numbers.every(number => Number.isInteger(number) && number >= 1 && number <= 45);
    }
    function ball(number) {
        const element = document.createElement('span');
        element.className = `ball ball-group-${Math.min(5, Math.ceil(number / 10))}`;
        element.textContent = String(number);
        return element;
    }
    /**
     * @param item { drawNo, numbers }
     * @param options.title    카드 제목 (기본값: "N회")
     * @param options.subtitle 제목 옆 보조 문구 (없으면 표시하지 않음)
     */
    function sheet(item, { title, subtitle } = {}) {
        const numbers = [...item.numbers].sort((a, b) => a - b);
        const card = document.createElement('article');
        card.className = 'sheet-card';
        const heading = document.createElement('div');
        heading.className = 'sheet-heading';
        const titleElement = document.createElement('h3');
        titleElement.textContent = title ?? `${item.drawNo}회`;
        heading.append(titleElement);
        if (subtitle !== undefined) {
            const subtitleElement = document.createElement('span');
            subtitleElement.textContent = subtitle;
            heading.append(subtitleElement);
        }
        const svg = svgElement('svg', { viewBox: '0 0 218 250', class: 'lotto-sheet', role: 'img', 'aria-label': `${item.drawNo}회 당첨 번호 ${numbers.join(', ')}, 오름차순 연결` });
        svg.append(svgElement('title', {}, `${item.drawNo}회: ${numbers.join(', ')}`));
        svg.append(svgElement('rect', { x: .5, y: .5, width: 217, height: 249, rx: 6, class: 'sheet-frame' }));
        svg.append(svgElement('polyline', { points: numbers.map(number => { const p = point(number); return `${p.x},${p.y}`; }).join(' '), class: 'sheet-path' }));
        for (let number = 1; number <= 45; number++) {
            const { x, y } = point(number);
            const selected = numbers.includes(number);
            if (selected) svg.append(svgElement('rect', { x: x - 11, y: y - 11, width: 22, height: 22, rx: 4, class: 'sheet-mark' }));
            svg.append(svgElement('path', { d: `M ${x - 11} ${y - 5} v -6 h 7 M ${x + 4} ${y - 11} h 7 v 6 M ${x - 11} ${y + 5} v 6 h 7 M ${x + 4} ${y + 11} h 7 v -6`, class: 'sheet-corner' }));
            svg.append(svgElement('text', { x, y: y + 4, 'text-anchor': 'middle', class: `sheet-number${selected ? ' is-selected' : ''}` }, String(number)));
        }
        card.append(heading, svg);
        return card;
    }
    window.LottoSheet = Object.freeze({ validHistory, ball, sheet });
})();
