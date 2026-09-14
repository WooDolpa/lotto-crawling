(() => {
    'use strict';
    const { getJson, validHistory, sheet } = window.LottoSheet;
    const experimentForm = document.getElementById('experiment-form');
    experimentForm.addEventListener('submit', async event => {
        event.preventDefault();
        const runButton = document.getElementById('experiment-button');
        const runStatus = document.getElementById('experiment-status');
        const summary = document.getElementById('experiment-summary');
        const output = document.getElementById('experiment-results');
        if (runButton.disabled) return;
        runButton.disabled = true;
        output.setAttribute('aria-busy', 'true');
        output.replaceChildren(); summary.replaceChildren();
        runStatus.textContent = '시간순 학습과 검증을 진행 중입니다. 수십 초 이상 걸릴 수 있습니다.';
        try {
            const url = new URL(experimentForm.dataset.url, location.href);
            url.searchParams.set('testDraws', document.getElementById('test-draws').value);
            url.searchParams.set('seed', '42');
            const response = await fetch(url, { method: 'POST', headers: { Accept: 'application/json' } });
            if (!response.ok) {
                if (response.status === 400) throw new Error('검증 회차 수 + 50건 이상의 연속된 유효한 이력이 필요합니다.');
                throw new Error('모델 실험에 실패했습니다. 서버 로그를 확인해 주세요.');
            }
            const report = await response.json();
            if (!validHistory({ drawNo: report.nextDrawNo, numbers: report.numbers }) || !Array.isArray(report.trials)) throw new Error('실험 응답 형식이 올바르지 않습니다.');
            const table = document.createElement('table'); table.className = 'metrics';
            function row(values, header = false) {
                const tr = document.createElement('tr');
                values.forEach(value => { const cell = document.createElement(header ? 'th' : 'td'); cell.textContent = value; if (header) cell.scope = 'col'; tr.append(cell); });
                return tr;
            }
            const thead = document.createElement('thead');
            thead.append(row(['비교 모델', '평균 일치 번호 (0~6)', '평균 패턴 유사도 (0~100%)'], true));
            const tbody = document.createElement('tbody');
            for (const [name, metric] of [['번호 + 패턴', report.pattern], ['번호만', report.numberOnly], ['무작위 (회차당 1,000개)', report.random]]) {
                tbody.append(row([name, metric.matches.toFixed(3), `${(metric.similarity * 100).toFixed(1)}%`]));
            }
            table.append(thead, tbody); summary.append(table);
            const note = document.createElement('p');
            note.textContent = `기준 ${report.baseDrawNo}회 · 학습 이력 ${report.trainingDraws}건 · 검증 ${report.testDraws}회차 · seed ${report.seed}. 유사도는 오름차순으로 짝지은 6개 점의 평균 거리를 용지 대각선으로 정규화한 값입니다. 번호만 모델은 기존 6개 특징을 사용하며 두 모델의 학습 설정은 동일합니다.`;
            summary.append(note);
            const next = sheet({ drawNo: report.nextDrawNo, numbers: report.numbers });
            next.querySelector('h3').textContent = `${report.nextDrawNo}회 후보`;
            next.querySelector('time').textContent = '패턴 모델';
            output.append(next);
            for (const trial of report.trials) {
                for (const [label, numbers] of [['실제', trial.actual], [`패턴 · ${trial.pattern.matches}개 일치`, trial.pattern.numbers], [`번호만 · ${trial.numberOnly.matches}개 일치`, trial.numberOnly.numbers]]) {
                    const card = sheet({ drawNo: trial.drawNo, numbers });
                    card.querySelector('time').textContent = label;
                    output.append(card);
                }
            }
            runStatus.textContent = '실험 완료. 적은 검증 회차의 차이는 우연일 수 있으며 예측력 향상을 의미하지 않습니다.';
        } catch (error) {
            summary.replaceChildren(); output.replaceChildren();
            runStatus.textContent = error.message;
        } finally {
            runButton.disabled = false;
            output.setAttribute('aria-busy', 'false');
        }
    });

})();
