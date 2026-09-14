(() => {
    'use strict';
    const { getJson, validHistory, sheet } = window.LottoSheet;
    const modelControls = document.getElementById('model-controls');
    const modelStatus = document.getElementById('model-status');
    const predictButton = document.getElementById('predict-button');
    const trainButton = document.getElementById('train-button');
    async function showModelStatus() {
        try {
            const data = await getJson(`${modelControls.dataset.url}/status`);
            modelStatus.textContent = `${data.training ? '학습 중 · ' : ''}${data.modelAvailable ? `학습 기준 ${data.trainedBaseDrawNo}회 · 전체 ${data.historyCount}건` : '사용 가능한 모델 없음'}${data.lastError ? ` · 최근 오류: ${data.lastError}` : ''}`;
        } catch (error) { modelStatus.textContent = '모델 상태를 불러오지 못했습니다.'; }
    }
    trainButton.addEventListener('click', async () => {
        trainButton.disabled = true;
        try {
            const response = await fetch(`${modelControls.dataset.url}/train`, { method: 'POST' });
            if (!response.ok) throw new Error('학습 요청 실패');
            await showModelStatus();
        } catch (error) { modelStatus.textContent = '학습 갱신을 요청하지 못했습니다.'; }
        finally { trainButton.disabled = false; }
    });
    predictButton.addEventListener('click', async () => {
        predictButton.disabled = true;
        const output = document.getElementById('prediction-results');
        output.replaceChildren();
        try {
            const response = await fetch(`${modelControls.dataset.url}/predict`, { headers: { Accept: 'application/json' } });
            if (!response.ok) throw new Error(response.status === 503 ? '모델이 아직 없습니다. 학습 상태를 확인해 주세요.' : '예측에 실패했습니다. 모델 상태와 서버 로그를 확인해 주세요.');
            const data = await response.json();
            if (!validHistory({ drawNo: data.nextDrawNo, numbers: data.numbers })) throw new Error('예측 응답 형식이 올바르지 않습니다.');
            const card = sheet({ drawNo: data.nextDrawNo, numbers: data.numbers });
            card.querySelector('h3').textContent = `${data.nextDrawNo}회 후보`;
            card.querySelector('time').textContent = '저장 모델';
            output.append(card);
            modelStatus.textContent = `입력 기준 ${data.baseDrawNo}회 · 학습 기준 ${data.trainedBaseDrawNo}회 · 학습 ${data.historyCount}건 / ${data.trainingSamples}개 샘플${data.staleModel ? ' · 데이터 변경 후 이전 모델을 사용 중입니다.' : ''}`;
        } catch (error) { modelStatus.textContent = error.message; }
        finally { predictButton.disabled = false; }
    });
    showModelStatus();

})();
