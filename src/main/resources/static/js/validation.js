(() => {
    'use strict';
    const { fetchJson, notify } = window.LottoCommon;
    const { ball } = window.LottoSheet;
    const form = document.getElementById('validation-form');
    const runButton = document.getElementById('validation-button');
    const testDraws = document.getElementById('test-draws');
    const progressState = document.getElementById('progress-state');
    const progressTrack = document.getElementById('progress-track');
    const progressMeta = document.getElementById('progress-meta');
    const panels = ['summary-panel', 'popularity-panel', 'distribution-panel', 'draws-panel'].map(id => document.getElementById(id));
    const $ = id => document.getElementById(id);
    const POLL_INTERVAL = 2000;
    const STANDARD_TEST_DRAWS = [100, 300, 500];
    const DEFAULT_TEST_DRAWS = 300;
    let pollTimer;
    // 이 화면에서 실행 중인 상태를 본 뒤 끝났을 때만 완료·실패 토스트를 띄움
    let watching = false;

    const percent = (value, digits = 1) => `${(value * 100).toFixed(digits)}%`;
    const signed = value => `${value >= 0 ? '+' : ''}${value.toFixed(3)}`;
    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }
    function rowHeader(text) {
        const cell = element('th', 'cell-strong', text);
        cell.scope = 'row';
        return cell;
    }
    function valueCell(value, sub) {
        const cell = element('td', 'cell-strong', value);
        if (sub) cell.append(element('span', 'cell-sub', sub));
        return cell;
    }
    function duration(status) {
        const end = status.finishedAt ? new Date(status.finishedAt) : new Date();
        const seconds = Math.max(0, Math.round((end - new Date(status.startedAt)) / 1000));
        return seconds >= 60 ? `${Math.floor(seconds / 60)}분 ${seconds % 60}초` : `${seconds}초`;
    }
    /**
     * p값을 "우연일 확률"로 읽기 쉽게 표시 (아주 작으면 0.01% 미만)
     */
    function chance(pValue) {
        return pValue < 0.0001 ? '0.01% 미만' : percent(pValue, 2);
    }

    function renderProgress(status) {
        const ratio = status.totalSteps ? status.completedSteps / status.totalSteps : 0;
        progressTrack.firstElementChild.style.width = `${ratio * 100}%`;
        progressTrack.setAttribute('aria-valuenow', String(Math.round(ratio * 100)));
        progressTrack.classList.toggle('is-failed', status.state === 'FAILED');
        progressMeta.classList.toggle('error', status.state === 'FAILED');
        if (status.state === 'IDLE') {
            progressState.textContent = '실행한 검증이 없습니다';
            progressMeta.textContent = '검증 회차 수를 고르고 실행해 주세요.';
        } else if (status.state === 'RUNNING') {
            progressState.textContent = `${status.testDraws}회차 검증 중 · ${status.completedSteps}/${status.totalSteps}단계`;
            progressMeta.textContent = `특징 계산 1단계와 20회차마다 학습 ${status.totalSteps - 1}단계로 진행합니다. 경과 ${duration(status)}`;
        } else if (status.state === 'DONE') {
            progressState.textContent = `${status.testDraws}회차 검증 완료`;
            progressMeta.textContent = `걸린 시간 ${duration(status)}`;
        } else {
            progressState.textContent = `${status.testDraws}회차 검증 실패`;
            progressMeta.textContent = status.error || '서버 로그를 확인해 주세요.';
        }
    }

    function renderSummary(report) {
        const winners = report.games.filter(game => game.betterThanRandom).map(game => game.name);
        $('summary-meta').textContent = `${report.firstDrawNo}회 ~ ${report.lastDrawNo}회 · ${report.testDraws}회차`;
        $('verdict').replaceChildren(winners.length
            ? element('span', '', `무작위보다 확실히 나은 게임: ${winners.join(', ')}`)
            : element('span', '', '무작위보다 확실히 나은 게임이 없습니다. 모든 게임의 차이가 우연으로 설명될 수 있는 수준입니다.'));
        $('verdict').classList.toggle('is-positive', winners.length > 0);

        const rows = report.games.map(game => {
            const row = document.createElement('tr');
            const verdict = element('td');
            verdict.append(element('span', `badge${game.betterThanRandom ? ' badge-success' : ''}`, game.betterThanRandom ? '무작위보다 나음' : '차이 없음'));
            row.append(rowHeader(game.name),
                valueCell(game.averageMatches.toFixed(3), `무작위 대비 ${signed(game.averageMatches - report.random.averageMatches)}`),
                valueCell(percent(game.prizeRate, 2), `우연일 확률 ${chance(game.prizePValue)}`),
                valueCell(chance(game.pValue)),
                verdict);
            return row;
        });
        const baseline = document.createElement('tr');
        baseline.className = 'is-baseline';
        baseline.append(rowHeader('무작위 (이론값)'), valueCell(report.random.averageMatches.toFixed(3)),
            valueCell(percent(report.random.prizeRate, 2)), valueCell('-'), valueCell('기준'));
        $('summary-body').replaceChildren(...rows, baseline);
        $('summary-note').textContent = `우연일 확률은 "사실은 무작위와 같은데 운으로 이만큼 맞혔을 확률"입니다. ${report.games.length}개 게임을 함께 비교하므로 이 값이 ${percent(report.significanceLevel, 2)}보다 작을 때만 "무작위보다 나음"으로 판정합니다. 학습과 번호 뽑기에 시드 ${report.seed}를 써서, 같은 이력으로 다시 실행하면 같은 결과가 나옵니다.`;
    }

    /**
     * 인기도 지수 1.0(평균만큼 붐빔) 기준으로 얼마나 덜·더 붐볐는지
     */
    function crowd(index) {
        return `평균보다 ${percent(Math.abs(1 - index))} ${index < 1 ? '덜' : '더'} 붐볐습니다`;
    }
    function metricRow(label, value, note) {
        const row = document.createElement('tr');
        row.append(rowHeader(label), valueCell(value), element('td', 'cell-muted', note));
        return row;
    }
    /**
     * D 인기도 모델은 맞힌 개수가 아니라 "예측 인기도와 실제 인기도가 같이 움직였는지"로 잼
     */
    function renderPopularity(report) {
        const popularity = report.popularity;
        const verdict = $('popularity-verdict');
        verdict.classList.toggle('is-positive', popularity.available && popularity.betterThanChance);
        if (!popularity.available) {
            $('popularity-meta').textContent = '잴 수 없음';
            verdict.replaceChildren(element('span', '', popularity.message));
            $('popularity-body').replaceChildren();
            $('popularity-note').textContent = '인기도는 회차별 판매금액과 5등 당첨자 수로 계산합니다. 두 값이 채워진 회차만 검증에 들어갑니다.';
            return;
        }
        // 인기도는 검정할 가설이 하나라 게임 수로 나누지 않은 0.05를 기준으로 쓴다
        const level = report.significanceLevel * report.games.length;
        $('popularity-meta').textContent = `${popularity.firstDrawNo}회 ~ ${popularity.lastDrawNo}회 · ${popularity.draws}회차`;
        verdict.replaceChildren(element('span', '', popularity.betterThanChance
            ? `인기도 예측이 실제와 같이 움직였습니다. 덜 붐빌 것으로 본 회차는 실제로 ${percent(Math.abs(1 - popularity.lowQuartileIndex))} 덜 붐볐습니다.`
            : '인기도 예측과 실제 사이에 뚜렷한 관계가 없습니다. 차이가 우연으로 설명될 수 있는 수준입니다.'));
        $('popularity-body').replaceChildren(
            metricRow('예측-실제 상관계수', popularity.correlation.toFixed(3), '1에 가까울수록 잘 맞힌 것, 0이면 맞히지 못한 것'),
            metricRow('보정 기울기', popularity.slope.toFixed(2),
                `예측한 차이의 ${percent(popularity.slope, 0)}만 실제로 나타납니다. 1보다 작으면 예측이 낙관적이라는 뜻입니다.`),
            metricRow('덜 붐빌 것으로 본 25% 회차', popularity.lowQuartileIndex.toFixed(3), crowd(popularity.lowQuartileIndex)),
            metricRow('더 붐빌 것으로 본 25% 회차', popularity.highQuartileIndex.toFixed(3), crowd(popularity.highQuartileIndex)),
            metricRow('우연일 확률', chance(popularity.pValue), `${percent(level, 0)}보다 작을 때만 관계가 있다고 봅니다`));
        $('popularity-note').textContent = '생성한 조합의 실제 인기도는 잴 방법이 없어(당첨되지 않았으니 5등 당첨자 수도 없습니다), 대신 그 회차 실제 당첨 조합의 인기도를 직전 이력만으로 학습한 모델이 얼마나 맞히는지 쟀습니다. 당첨 조합은 무작위로 정해지므로 모델이 처음 보는, 고르게 뽑힌 표본입니다. 인기도 지수는 실제 5등 당첨자 수 ÷ 기대 5등 당첨자 수이며, 1.0이면 평균만큼 붐빈 조합입니다. 이 값은 당첨 확률과 무관하고, 당첨됐을 때 당첨금을 몇 명과 나누는지만 말해 줍니다.';
    }

    function renderDistribution(report) {
        const rows = report.games.map(game => {
            const row = document.createElement('tr');
            row.append(rowHeader(game.name), ...game.matchCounts.map(count => element('td', 'cell-muted', count.toLocaleString('ko-KR'))));
            return row;
        });
        const baseline = document.createElement('tr');
        baseline.className = 'is-baseline';
        baseline.append(rowHeader('무작위 기대값'), ...report.random.matchProbabilities.map(probability =>
            element('td', 'cell-muted', (probability * report.testDraws).toFixed(1))));
        $('distribution-body').replaceChildren(...rows, baseline);
    }

    function renderDraws(report) {
        const head = document.createElement('tr');
        ['회차', '실제 번호', ...report.games.map(game => game.name)].forEach(label => {
            const cell = element('th', '', label);
            cell.scope = 'col';
            head.append(cell);
        });
        $('draws-head').replaceChildren(head);
        const fragment = document.createDocumentFragment();
        [...report.draws].reverse().forEach(draw => {
            const row = document.createElement('tr');
            const balls = element('div', 'ball-row');
            draw.actual.forEach(number => balls.append(ball(number)));
            const actual = element('td');
            actual.append(balls);
            row.append(rowHeader(`${draw.drawNo}회`), actual, ...draw.picks.map((pick, index) => {
                const cell = element('td', `match-count${pick.matches >= 3 ? ' is-hit' : ''}`, `${pick.matches}개`);
                cell.title = `${report.games[index].name}: ${pick.numbers.join(', ')}`;
                return cell;
            }));
            fragment.append(row);
        });
        $('draws-body').replaceChildren(fragment);
    }

    /**
     * 기본 회차 수 중 실행 가능한 것과 "가능한 최대" 옵션으로 선택지를 다시 만듦 (선택값은 가능하면 유지)
     */
    function renderOptions(max) {
        if (testDraws.dataset.max === String(max)) return;
        testDraws.dataset.max = String(max);
        const previous = Number(testDraws.value);
        if (!max) {
            testDraws.replaceChildren(new Option('데이터 부족', ''));
            return;
        }
        const values = STANDARD_TEST_DRAWS.filter(value => value < max);
        const options = values.map(value => new Option(`${value}회차`, String(value)));
        options.push(new Option(`가능한 최대 (${max}회차)`, String(max)));
        testDraws.replaceChildren(...options);
        testDraws.value = String([...values, max].includes(previous) ? previous : Math.min(DEFAULT_TEST_DRAWS, max));
        if (!testDraws.value) testDraws.value = String(max);
    }

    function apply(status) {
        clearTimeout(pollTimer);
        renderProgress(status);
        renderOptions(status.maxTestDraws);
        const running = status.state === 'RUNNING';
        runButton.disabled = running || !status.maxTestDraws;
        testDraws.disabled = running || !status.maxTestDraws;
        if (!running && !status.maxTestDraws) {
            progressMeta.textContent = '검증하려면 연속된 당첨 이력이 70회차 이상 필요합니다.';
        }
        if (running) {
            watching = true;
            pollTimer = setTimeout(refresh, POLL_INTERVAL);
            return;
        }
        const report = status.state === 'DONE' ? status.report : null;
        panels.forEach(panel => { panel.hidden = !report; });
        if (report) {
            renderSummary(report);
            renderPopularity(report);
            renderDistribution(report);
            renderDraws(report);
        }
        if (watching && report) notify(`${report.testDraws}회차 검증을 마쳤습니다. 결과 표에서 무작위와의 차이를 확인해 주세요.`, { type: 'success', title: '검증 완료' });
        if (watching && status.state === 'FAILED') notify(status.error || '서버 로그를 확인해 주세요.', { type: 'error', title: '검증하지 못했습니다' });
        watching = false;
    }
    async function refresh() {
        try {
            apply(await fetchJson(form.dataset.url));
        } catch (error) {
            progressState.textContent = '상태를 불러오지 못했습니다';
            progressMeta.textContent = '';
            notify('페이지를 새로고침해 주세요.', { type: 'error', title: '검증 상태를 불러오지 못했습니다' });
        }
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (runButton.disabled) return;
        runButton.disabled = true;
        try {
            const url = new URL(form.dataset.url, location.href);
            url.searchParams.set('testDraws', testDraws.value);
            apply(await fetchJson(url, { method: 'POST' }));
        } catch (error) {
            runButton.disabled = false;
            notify(error.serverMessage || '잠시 후 다시 시도해 주세요.', { type: 'error', title: '검증을 시작하지 못했습니다' });
            if (error.status === 409) refresh();
        }
    });
    refresh();
})();
