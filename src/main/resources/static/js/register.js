(() => {
    'use strict';
    const tabs = [document.getElementById('bulk-tab'), document.getElementById('single-tab')];
    const panels = [document.getElementById('bulk-panel'), document.getElementById('single-panel')];
    const status = document.getElementById('registration-status');
    const modelStatus = document.getElementById('registration-model-status');
    let busy = false;
    function message(text, error = false) { status.textContent = text; status.classList.toggle('error', error); }
    function tab(index) {
        tabs.forEach((element, i) => { element.setAttribute('aria-selected', String(i === index)); element.tabIndex = i === index ? 0 : -1; panels[i].hidden = i !== index; });
        if (!busy) { message(''); modelStatus.textContent = ''; }
    }
    tabs.forEach((element, index) => {
        element.addEventListener('click', () => tab(index));
        element.addEventListener('keydown', event => {
            if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
            event.preventDefault(); const next = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : 1 - index;
            tab(next); tabs[next].focus();
        });
    });
    async function submit(form, options, success) {
        if (busy) return;
        busy = true;
        document.getElementById('upload-button').disabled = true;
        document.getElementById('save-button').disabled = true;
        tabs.forEach(element => element.disabled = true);
        message('데이터를 저장하는 중입니다.'); modelStatus.textContent = '';
        try {
            const response = await fetch(form.dataset.url, { method: 'POST', ...options });
            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(data.message || (response.status === 413 ? '파일 크기는 10MB 이하여야 합니다.' : '저장에 실패했습니다. 파일 형식과 입력 정보를 확인해 주세요.'));
            message(success(data)); form.reset();
            modelStatus.textContent = '등록이 완료되었습니다. 모델 변경 확인을 요청했으며, 등록 성공과 학습 완료는 별개입니다.';
            try {
                const stateResponse = await fetch(modelStatus.dataset.url);
                if (!stateResponse.ok) return;
                const state = await stateResponse.json();
                modelStatus.textContent = state.lastError ? `등록 완료 · 모델 상태: ${state.lastError}` : state.training ? '등록 완료 · 모델 학습 또는 변경 확인 중입니다.' : `등록 완료 · 현재 모델 학습 기준 ${state.trainedBaseDrawNo || '없음'}회. 변경 확인 결과는 번호 예측 화면에서 확인해 주세요.`;
            } catch (error) { /* Registration remains successful even when status lookup fails. */ }
        } catch (error) { message(error.message, true); }
        finally { busy = false; tabs.forEach(element => element.disabled = false); document.getElementById('upload-button').disabled = false; document.getElementById('save-button').disabled = false; }
    }
    const bulk = document.getElementById('bulk-form');
    bulk.addEventListener('submit', event => {
        event.preventDefault();
        const file = document.getElementById('excel-file').files[0];
        if (!file || !/\.(xls|xlsx)$/i.test(file.name) || file.size > 10 * 1024 * 1024) { message('.xls 또는 .xlsx 파일을 10MB 이하로 선택해 주세요.', true); return; }
        const data = new FormData(); data.append('file', file);
        submit(bulk, { body: data }, data => data.message || '엑셀 업로드를 완료했습니다.');
    });
    const single = document.getElementById('single-form');
    single.addEventListener('submit', event => {
        event.preventDefault();
        const data = new FormData(single);
        const numbers = data.getAll('number').map(Number), bonus = Number(data.get('bonusNumber'));
        if (numbers.length !== 6 || numbers.some(n => !Number.isInteger(n) || n < 1 || n > 45) || new Set(numbers).size !== 6 || !Number.isInteger(bonus) || bonus < 1 || bonus > 45 || numbers.includes(bonus)) { message('당첨 번호는 서로 다른 1~45의 6개이며, 보너스 번호와 중복될 수 없습니다.', true); return; }
        const payload = { drawNo: Number(data.get('drawNo')), drawDate: data.get('drawDate'), numbers, bonusNumber: bonus };
        for (const key of ['totalSales', 'firstPrizeAmount', 'firstPrizeWinners']) payload[key] = data.get(key) === '' ? null : Number(data.get(key));
        if (payload.drawNo <= 0 || !Number.isInteger(payload.drawNo) || !payload.drawDate || ['totalSales', 'firstPrizeAmount', 'firstPrizeWinners'].some(key => payload[key] !== null && (!Number.isSafeInteger(payload[key]) || payload[key] < 0))) { message('회차·추첨일과 선택 입력 값을 확인해 주세요.', true); return; }
        submit(single, { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }, result => `${result.drawNo}회 당첨 번호 등록을 완료했습니다.`);
    });
})();
