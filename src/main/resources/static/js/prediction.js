(() => {
    'use strict';
    const { fetchJson, notify } = window.LottoCommon;
    const { validHistory, ball, sheet } = window.LottoSheet;
    const modelControls = document.getElementById('model-controls');
    const modelBadge = document.getElementById('model-badge');
    const predictButton = document.getElementById('predict-button');
    const trainButton = document.getElementById('train-button');
    const drawTitle = document.getElementById('prediction-draw');
    const inputBase = document.getElementById('input-base');
    const output = document.getElementById('prediction-results');
    const emptyState = output.firstElementChild;
    const dateFormat = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' });
    const percent = value => `${Math.round(Math.abs(value) * 100)}%`;
    // 학습한 모델(A·B·E)의 공통 안내. 학습 결과가 최신인지부터 알린다.
    const trainedMeta = result => result.staleModel ? '당첨 이력이 바뀐 뒤 재학습 전인 이전 학습 결과로 계산했습니다.'
        : result.model.trainingSamples === 0 ? '이력이 부족해 모든 번호를 같은 확률로 뽑았습니다.'
        : `${result.model.trainedBaseDrawNo}회까지 학습한 결과로 계산했습니다.`;
    /**
     * 응답 키, 게임 라벨, 화면 이름, 게임 아래 안내 문구
     * group: 모델 상태 영역 (학습하지 않는 C·D는 없음), samples: 상태 영역의 학습 샘플 단위
     */
    const models = [
        { key: 'pattern', tag: 'A', name: '점수 모델', samples: '개', meta: trainedMeta },
        { key: 'probability', tag: 'B', name: '확률 모델', samples: '개', meta: trainedMeta },
        {
            key: 'coOccurrence3', tag: 'C', name: '동반출현 3개',
            meta: () => '당첨 번호에 가장 자주 함께 나온 3개에 무작위 3개를 더했습니다. 많이 나온 조합은 우연이라 당첨 확률은 오르지 않고, 사람들이 많이 고르는 쪽이라 당첨금을 나눌 사람은 오히려 늘 수 있습니다.'
        },
        {
            key: 'coOccurrence4', tag: 'D', name: '동반출현 4개',
            meta: () => '가장 자주 함께 나온 4개에 무작위 2개를 더했습니다. 4개짜리는 후보가 적어, 새 회차에서 순위가 바뀌기 전까지 같은 4개가 계속 나옵니다.'
        },
        {
            key: 'popularity', tag: 'E', name: '인기도 모델', samples: '회',
            // 예상 인기도는 평균 대비 비율이다 (0.9 = 평균보다 10% 덜 붐빔)
            meta: result => result.popularity == null || result.staleModel ? trainedMeta(result)
                : result.popularity < 1 ? `평균보다 ${percent(1 - result.popularity)} 덜 붐비는 조합입니다. ${trainedMeta(result)}`
                : `평균만큼 붐비는 조합입니다. 후보 중 더 나은 조합을 찾지 못했습니다. ${trainedMeta(result)}`
        }
    ].map(model => ({ ...model, game: document.getElementById(`game-${model.key}`), group: document.getElementById(`status-${model.key}`) }));
    const trainedModels = models.filter(model => model.group);
    const field = (root, name) => root.querySelector(`[data-field="${name}"]`);
    const count = (value, unit) => `${value.toLocaleString('ko-KR')}${unit}`;

    function setBadge(text, tone) {
        modelBadge.textContent = text;
        modelBadge.className = `badge${tone ? ` badge-${tone}` : ''}`;
    }
    function placeholders() {
        return Array.from({ length: 6 }, () => {
            const element = document.createElement('span');
            element.className = 'ball ball-lg ball-placeholder';
            element.setAttribute('aria-hidden', 'true');
            element.textContent = '?';
            return element;
        });
    }
    function renderModelStatus({ group, samples }, status) {
        field(group, 'state').textContent = status.available ? '사용 가능' : '없음';
        field(group, 'trained-base').textContent = status.trainedBaseDrawNo ? `${status.trainedBaseDrawNo}회` : '-';
        field(group, 'history-count').textContent = status.available ? count(status.historyCount, '건') : '-';
        field(group, 'trained-at').textContent = status.trainedAt ? dateFormat.format(new Date(status.trainedAt)) : '-';
        field(group, 'samples').textContent = status.available ? count(status.trainingSamples, samples) : '-';
        field(group, 'error').textContent = status.lastError ? `최근 오류: ${status.lastError}` : '';
    }
    function renderStatus(data) {
        const statuses = trainedModels.map(model => data[model.key]);
        trainedModels.forEach((model, index) => renderModelStatus(model, statuses[index]));
        const ready = statuses.filter(status => status.available).length;
        if (data.training) setBadge('학습 중', 'warning');
        else if (statuses.some(status => status.lastError)) setBadge('최근 학습 오류', 'danger');
        else if (ready === trainedModels.length) setBadge('모델 준비됨', 'success');
        else if (ready) setBadge('일부 모델 준비됨', 'warning');
        else setBadge('모델 없음');
    }
    async function showModelStatus() {
        try {
            renderStatus(await fetchJson(`${modelControls.dataset.url}/status`));
        } catch (error) {
            setBadge('상태 확인 실패', 'danger');
            notify('잠시 후 다시 시도하거나 서버 상태를 확인해 주세요.', { type: 'error', title: '모델 상태를 불러오지 못했습니다' });
        }
    }
    /**
     * 게임 1개를 그리고, 예측에 성공했으면 용지 카드를 반환
     */
    function renderGame(model, result, data, order) {
        const { nextDrawNo } = data;
        const balls = field(model.game, 'balls');
        const meta = field(model.game, 'meta');
        if (model.group) renderModelStatus(model, result.model);
        meta.classList.remove('is-warning', 'is-error');
        if (!result.available) {
            balls.replaceChildren(...placeholders());
            meta.textContent = result.message || '이 게임을 만들지 못했습니다.';
            meta.classList.add('is-error');
            notify(meta.textContent, { type: 'error', title: `${model.tag} · ${model.name} 게임을 만들지 못했습니다` });
            return null;
        }
        const numbers = [...result.numbers].sort((a, b) => a - b);
        balls.replaceChildren(...numbers.map((number, index) => {
            const element = ball(number);
            element.classList.add('ball-lg', 'ball-pop');
            element.style.setProperty('--delay', `${(order * 6 + index) * 60}ms`);
            return element;
        }));
        meta.textContent = model.meta(result, data);
        meta.classList.toggle('is-warning', result.staleModel);
        return sheet({ drawNo: nextDrawNo, numbers }, { title: `${model.tag} · ${model.name}`, subtitle: `${nextDrawNo}회 후보` });
    }
    function validResponse(data) {
        return data && Number.isInteger(data.nextDrawNo) && models.every(({ key, group }) => {
            const result = data[key];
            return result && (!group || result.model) && (!result.available || validHistory({ drawNo: data.nextDrawNo, numbers: result.numbers }));
        });
    }

    models.forEach(({ game }) => field(game, 'balls').replaceChildren(...placeholders()));
    trainButton.addEventListener('click', async () => {
        trainButton.disabled = true;
        try {
            renderStatus(await fetchJson(`${modelControls.dataset.url}/train`, { method: 'POST' }));
            notify('당첨 이력이 바뀐 모델만 백그라운드에서 다시 학습합니다.', { title: '변경 확인을 요청했습니다' });
        } catch (error) { notify('잠시 후 다시 시도해 주세요.', { type: 'error', title: '학습 갱신을 요청하지 못했습니다' }); }
        finally { trainButton.disabled = false; }
    });
    predictButton.addEventListener('click', async () => {
        predictButton.disabled = true;
        try {
            const data = await fetchJson(modelControls.dataset.url).catch(error => {
                throw new Error(error.serverMessage || '예측에 실패했습니다. 모델 상태와 서버 로그를 확인해 주세요.');
            });
            if (!validResponse(data)) throw new Error('예측 응답 형식이 올바르지 않습니다.');
            drawTitle.textContent = `${data.nextDrawNo}회 후보`;
            inputBase.textContent = `입력 기준 회차 ${data.baseDrawNo}회`;
            const cards = models.map((model, order) => renderGame(model, data[model.key], data, order)).filter(Boolean);
            output.replaceChildren(...(cards.length ? cards : [emptyState]));
        } catch (error) {
            notify(error.message, { type: 'error', title: '예측하지 못했습니다' });
            if (!output.querySelector('.sheet-card')) output.replaceChildren(emptyState);
        } finally { predictButton.disabled = false; }
    });
    showModelStatus();
})();
