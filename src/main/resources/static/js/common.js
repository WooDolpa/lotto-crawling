(() => {
    'use strict';

    /**
     * JSON 요청. 실패하면 status(HTTP 상태)와 serverMessage(응답 message)를 담은 Error를 던진다.
     */
    async function fetchJson(url, options = {}) {
        const response = await fetch(url, { ...options, headers: { Accept: 'application/json', ...options.headers } });
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const error = new Error(data?.message || '요청에 실패했습니다.');
            error.status = response.status;
            error.serverMessage = data?.message;
            throw error;
        }
        return data;
    }

    function createMessenger(element) {
        return (text, error = false) => {
            element.textContent = text;
            element.classList.toggle('error', error);
        };
    }

    const TOAST_LIMIT = 3;
    const TOAST_TYPES = {
        error: { title: '처리하지 못했습니다', duration: 3000, icon: 'M12 8v5M12 16h.01M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z' },
        success: { title: '완료', duration: 3000, icon: 'M8 12.5l2.5 2.5L16 9.5M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z' },
        info: { title: '안내', duration: 3000, icon: 'M12 11v5M12 8h.01M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z' }
    };
    let toastStack;

    function svgIcon(path) {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('aria-hidden', 'true');
        const shape = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        shape.setAttribute('d', path);
        svg.append(shape);
        return svg;
    }

    /**
     * 화면 위쪽에 토스트 알림 표시 (최대 3개, 마우스를 올리거나 포커스하면 사라지는 시간이 멈춤)
     *
     * @param message          본문
     * @param options.type     'error' | 'success' | 'info' (기본 info)
     * @param options.title    제목 (기본값: 종류별 문구)
     * @param options.duration 표시 시간 ms (기본값: 3초)
     */
    function notify(message, { type = 'info', title, duration } = {}) {
        const config = TOAST_TYPES[type] || TOAST_TYPES.info;
        const heading = title || config.title;
        const lifetime = duration ?? config.duration;
        if (!toastStack) {
            toastStack = document.createElement('div');
            toastStack.className = 'toast-stack';
            toastStack.setAttribute('aria-live', 'polite');
            document.body.append(toastStack);
        }
        // 같은 알림이 연달아 오면 이전 것을 지우고 새로 표시
        [...toastStack.children]
            .filter(item => item.dataset.key === `${type}|${heading}|${message}`)
            .forEach(item => item.dismiss());

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.dataset.key = `${type}|${heading}|${message}`;
        toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
        toast.style.setProperty('--toast-duration', `${lifetime}ms`);

        const body = document.createElement('div');
        body.className = 'toast-body';
        const titleElement = document.createElement('p');
        titleElement.className = 'toast-title';
        titleElement.textContent = heading;
        body.append(titleElement);
        if (message) {
            const text = document.createElement('p');
            text.className = 'toast-message';
            text.textContent = message;
            body.append(text);
        }
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'toast-close';
        close.setAttribute('aria-label', '알림 닫기');
        close.append(svgIcon('M6 6l12 12M18 6L6 18'));
        const progress = document.createElement('span');
        progress.className = 'toast-progress';
        progress.setAttribute('aria-hidden', 'true');
        toast.append(svgIcon(config.icon), body, close, progress);

        let remaining = lifetime;
        let startedAt;
        let timer;
        const pause = () => {
            if (toast.classList.contains('is-paused')) return;
            clearTimeout(timer);
            remaining -= Date.now() - startedAt;
            toast.classList.add('is-paused');
        };
        const resume = () => {
            if (!toast.classList.contains('is-paused') || toast.matches(':hover') || toast.contains(document.activeElement)) return;
            startedAt = Date.now();
            timer = setTimeout(() => toast.dismiss(), Math.max(0, remaining));
            toast.classList.remove('is-paused');
        };
        toast.dismiss = () => {
            clearTimeout(timer);
            toast.remove();
        };
        toast.addEventListener('mouseenter', pause);
        toast.addEventListener('focusin', pause);
        toast.addEventListener('mouseleave', resume);
        toast.addEventListener('focusout', () => setTimeout(resume));
        close.addEventListener('click', () => toast.dismiss());

        toastStack.prepend(toast);
        [...toastStack.children].slice(TOAST_LIMIT).forEach(item => item.dismiss());
        startedAt = Date.now();
        timer = setTimeout(() => toast.dismiss(), lifetime);
    }

    /**
     * role="tab" 버튼 목록에 클릭·방향키(좌우, Home, End) 선택을 연결하고 선택 함수를 반환
     */
    function setupTabs(tabs, onSelect) {
        function select(index) {
            tabs.forEach((tab, i) => {
                tab.setAttribute('aria-selected', String(i === index));
                tab.tabIndex = i === index ? 0 : -1;
            });
            onSelect(index);
        }
        tabs.forEach((tab, index) => {
            tab.addEventListener('click', () => select(index));
            tab.addEventListener('keydown', event => {
                if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
                event.preventDefault();
                const last = tabs.length - 1;
                const next = event.key === 'Home' ? 0
                    : event.key === 'End' ? last
                    : event.key === 'ArrowLeft' ? (index === 0 ? last : index - 1)
                    : (index === last ? 0 : index + 1);
                select(next);
                tabs[next].focus();
            });
        });
        return select;
    }

    window.LottoCommon = Object.freeze({ fetchJson, createMessenger, setupTabs, notify });
})();
