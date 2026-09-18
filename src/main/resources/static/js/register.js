(() => {
    'use strict';
    const { fetchJson, setupTabs, notify } = window.LottoCommon;
    const tabs = [document.getElementById('bulk-tab'), document.getElementById('single-tab')];
    const panels = [document.getElementById('bulk-panel'), document.getElementById('single-panel')];
    const buttons = [document.getElementById('upload-button'), document.getElementById('save-button')];
    const modelStatus = document.getElementById('registration-model-status');
    const MAX_FILE_SIZE = 10 * 1024 * 1024;
    const MAX_INT = 2147483647;
    let busy = false;

    setupTabs(tabs, index => {
        panels.forEach((panel, i) => { panel.hidden = i !== index; });
        if (!busy) { modelStatus.textContent = '등록이 끝나면 모델 반영 상태가 여기에 표시됩니다.'; }
    });

    /**
     * 저장 중에는 버튼·탭을 잠그고 제출 버튼 문구를 진행 중 문구로 바꿈
     */
    function setBusy(value, form) {
        busy = value;
        buttons.forEach(button => button.disabled = value);
        tabs.forEach(tab => tab.disabled = value);
        const button = form?.querySelector('[type="submit"]');
        const label = button && [...button.childNodes].find(node => node.nodeType === Node.TEXT_NODE && node.textContent.trim());
        if (!label) return;
        if (value) {
            button.dataset.label = label.textContent;
            label.textContent = `${label.textContent} 중…`;
            button.setAttribute('aria-busy', 'true');
        } else {
            label.textContent = button.dataset.label ?? label.textContent;
            button.removeAttribute('aria-busy');
        }
    }
    /**
     * 입력 오류를 토스트로 알리고 해당 입력칸을 표시·포커스
     */
    function invalid(input, text) {
        if (input) {
            input.setAttribute('aria-invalid', 'true');
            input.focus();
        }
        notify(text, { type: 'error', title: '입력 값을 확인해 주세요' });
    }
    async function showModelStatus() {
        modelStatus.textContent = '등록이 완료되었습니다. 모델 변경 확인을 요청했으며, 등록 성공과 학습 완료는 별개입니다.';
        try {
            const state = await fetchJson(modelStatus.dataset.url);
            const models = [['점수 모델', state.pattern], ['확률 모델', state.probability]];
            const errors = models.filter(([, model]) => model.lastError).map(([name, model]) => `${name} ${model.lastError}`);
            const base = ([name, model]) => `${name} ${model.trainedBaseDrawNo ? `${model.trainedBaseDrawNo}회` : '없음'}`;
            modelStatus.textContent = errors.length ? `등록 완료 · 모델 오류: ${errors.join(' / ')}`
                : state.training ? '등록 완료 · 모델 학습 또는 변경 확인 중입니다.'
                : `등록 완료 · 현재 학습 기준 ${models.map(base).join(', ')}. 변경 확인 결과는 번호 예측 화면에서 확인해 주세요.`;
        } catch (error) { /* Registration remains successful even when status lookup fails. */ }
    }
    async function submit(form, options, success) {
        if (busy) return;
        setBusy(true, form);
        try {
            const data = await fetchJson(form.dataset.url, { method: 'POST', ...options }).catch(error => {
                throw new Error(error.serverMessage || (error.status === 413 ? '파일 크기는 10MB 이하여야 합니다.' : '저장에 실패했습니다. 파일 형식과 입력 정보를 확인해 주세요.'));
            });
            notify(success(data ?? {}), { type: 'success', title: '등록 완료' });
            form.reset();
            await showModelStatus();
        } catch (error) {
            notify(error.message, { type: 'error', title: '등록하지 못했습니다' });
        } finally { setBusy(false, form); }
    }

    // ===== 대량등록 =====
    const bulk = document.getElementById('bulk-form');
    const fileInput = document.getElementById('excel-file');
    const fileName = document.getElementById('excel-file-name');
    const dropzone = fileInput.closest('.dropzone');
    fileInput.addEventListener('change', () => { fileName.textContent = fileInput.files[0]?.name || ''; });
    ['dragenter', 'dragover'].forEach(type => fileInput.addEventListener(type, () => dropzone.classList.add('is-dragover')));
    ['dragleave', 'drop'].forEach(type => fileInput.addEventListener(type, () => dropzone.classList.remove('is-dragover')));
    bulk.addEventListener('reset', () => { fileName.textContent = ''; });
    bulk.addEventListener('submit', event => {
        event.preventDefault();
        const file = fileInput.files[0];
        if (!file) { invalid(null, '업로드할 엑셀 파일을 선택해 주세요.'); fileInput.focus(); return; }
        if (!/\.(xls|xlsx)$/i.test(file.name)) { invalid(null, '.xls 또는 .xlsx 파일만 업로드할 수 있습니다.'); fileInput.focus(); return; }
        if (file.size > MAX_FILE_SIZE) { invalid(null, '파일 크기는 10MB 이하여야 합니다.'); fileInput.focus(); return; }
        const data = new FormData(); data.append('file', file);
        submit(bulk, { body: data }, result => result.message || '엑셀 업로드를 완료했습니다.');
    });

    // ===== 단일등록 =====
    const single = document.getElementById('single-form');
    const drawNoInput = document.getElementById('draw-no');
    const ballInputs = [...single.querySelectorAll('.ball-input[name="number"]')];
    const bonusInput = document.getElementById('bonus-number');
    const amountFields = [
        { input: document.getElementById('prize-amount'), hint: document.getElementById('prize-amount-hint'), max: Number.MAX_SAFE_INTEGER, label: '1등 당첨금', describe: koreanWon },
        { input: document.getElementById('prize-winners'), max: MAX_INT, label: '1등 당첨 인원' }
    ];

    function paintBall(input) {
        const number = Number(input.value);
        input.className = input.className.replace(/\s*ball-group-\d/g, '');
        if (Number.isInteger(number) && number >= 1 && number <= 45) input.classList.add(`ball-group-${Math.min(5, Math.ceil(number / 10))}`);
    }
    function digitsOf(value) {
        return value.replace(/\D/g, '').replace(/^0+(?=\d)/, '');
    }
    function withCommas(digits) {
        return digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    }
    /**
     * 억·만 단위 읽기 쉬운 금액 (예: 1791817758 → "약 17억 9,181만 원")
     */
    function koreanWon(value) {
        const eok = Math.floor(value / 1e8);
        const man = Math.floor((value % 1e8) / 1e4);
        if (!eok && !man) return `${value.toLocaleString('ko-KR')}원`;
        const parts = [];
        if (eok) parts.push(`${eok.toLocaleString('ko-KR')}억`);
        if (man) parts.push(`${man.toLocaleString('ko-KR')}만`);
        return `${value % 1e4 ? '약 ' : ''}${parts.join(' ')} 원`;
    }
    function updateHint(field) {
        if (!field.hint) return;
        const digits = digitsOf(field.input.value);
        const value = Number(digits);
        field.hint.textContent = digits && value <= field.max ? field.describe(value) : '';
    }
    function formatAmount(field) {
        const { input } = field;
        const caret = input.selectionEnd ?? input.value.length;
        // 커서 오른쪽 숫자 개수를 기준으로 콤마를 넣은 뒤 커서 위치 복원
        const digitsAfterCaret = input.value.slice(caret).replace(/\D/g, '').length;
        const formatted = withCommas(digitsOf(input.value).slice(0, String(field.max).length));
        input.value = formatted;
        let position = formatted.length;
        for (let seen = 0; position > 0 && seen < Math.min(digitsAfterCaret, formatted.replace(/\D/g, '').length); ) {
            position--;
            if (/\d/.test(formatted[position])) seen++;
        }
        while (digitsAfterCaret > 0 && position > 0 && formatted[position - 1] === ',') position--;
        if (document.activeElement === input) input.setSelectionRange(position, position);
        updateHint(field);
    }

    ballInputs.concat(bonusInput).forEach(input => input.addEventListener('input', () => paintBall(input)));
    amountFields.forEach(field => field.input.addEventListener('input', () => formatAmount(field)));
    single.addEventListener('input', event => event.target.removeAttribute('aria-invalid'));
    single.addEventListener('reset', () => setTimeout(() => {
        ballInputs.concat(bonusInput).forEach(paintBall);
        amountFields.forEach(updateHint);
        single.querySelectorAll('[aria-invalid]').forEach(input => input.removeAttribute('aria-invalid'));
    }));

    function isLottoNumber(value) {
        return Number.isInteger(value) && value >= 1 && value <= 45;
    }
    single.addEventListener('submit', event => {
        event.preventDefault();
        single.querySelectorAll('[aria-invalid]').forEach(input => input.removeAttribute('aria-invalid'));

        const drawNo = Number(drawNoInput.value);
        if (drawNoInput.value.trim() === '' || !Number.isInteger(drawNo) || drawNo < 1 || drawNo > MAX_INT) {
            invalid(drawNoInput, '추첨 회차를 1 이상의 숫자로 입력해 주세요.');
            return;
        }
        const numbers = ballInputs.map(input => input.value.trim() === '' ? NaN : Number(input.value));
        const outOfRange = numbers.findIndex(number => !isLottoNumber(number));
        if (outOfRange >= 0) {
            invalid(ballInputs[outOfRange], `${outOfRange + 1}번째 당첨 번호를 1~45 사이 숫자로 입력해 주세요.`);
            return;
        }
        const duplicate = numbers.findIndex((number, index) => numbers.indexOf(number) !== index);
        if (duplicate >= 0) {
            invalid(ballInputs[duplicate], `같은 당첨 번호(${numbers[duplicate]})가 두 번 입력되었습니다.`);
            return;
        }
        const bonus = bonusInput.value.trim() === '' ? NaN : Number(bonusInput.value);
        if (!isLottoNumber(bonus)) {
            invalid(bonusInput, '보너스 번호를 1~45 사이 숫자로 입력해 주세요.');
            return;
        }
        if (numbers.includes(bonus)) {
            invalid(bonusInput, '보너스 번호는 당첨 번호와 중복될 수 없습니다.');
            return;
        }

        const payload = { drawNo, numbers, bonusNumber: bonus };
        for (const field of amountFields) {
            const digits = digitsOf(field.input.value);
            const value = digits === '' ? null : Number(digits);
            if (value !== null && (!Number.isSafeInteger(value) || value > field.max)) {
                invalid(field.input, `${field.label}이 입력 가능한 범위를 넘었습니다.`);
                return;
            }
            payload[field.input.name] = value;
        }
        submit(single, { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }, result => `${result.drawNo}회 당첨 번호를 등록했습니다.`);
    });
})();
