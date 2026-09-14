(() => {
    'use strict';
    const root = document.documentElement;
    const sidebar = document.getElementById('sidebar');
    const toggle = document.getElementById('sidebar-toggle');
    const opener = document.getElementById('sidebar-open');
    const backdrop = document.getElementById('sidebar-backdrop');
    const content = document.querySelector('.app-content');
    const toolbar = document.querySelector('.mobile-toolbar');
    const mobile = window.matchMedia('(max-width: 768px)');
    const links = [...sidebar.querySelectorAll('a.sidebar-link')];
    const groupToggle = document.getElementById('lotto-menu-toggle');
    const submenu = document.getElementById('lotto-submenu');
    let groupOpen = groupToggle.getAttribute('aria-expanded') === 'true';
    let collapsed = false;
    let open = false;
    try { collapsed = localStorage.getItem('lotto.sidebar.collapsed') === 'true'; } catch (error) { /* Storage may be unavailable. */ }

    function render() {
        root.dataset.sidebarCollapsed = String(collapsed);
        document.body.classList.toggle('sidebar-open', mobile.matches && open);
        sidebar.inert = mobile.matches && !open;
        content.inert = mobile.matches && open;
        toolbar.inert = mobile.matches && open;
        backdrop.hidden = !mobile.matches || !open;
        opener.setAttribute('aria-expanded', String(open && mobile.matches));
        toggle.setAttribute('aria-expanded', String(mobile.matches ? open : !collapsed));
        toggle.setAttribute('aria-label', mobile.matches ? '메뉴 닫기' : collapsed ? '사이드바 펼치기' : '사이드바 접기');
        toggle.firstElementChild.textContent = mobile.matches ? '×' : collapsed ? '»' : '«';
        submenu.hidden = !groupOpen || (!mobile.matches && collapsed);
        groupToggle.setAttribute('aria-expanded', String(!submenu.hidden));
    }
    function close(restoreFocus = true) {
        open = false; render();
        if (restoreFocus && mobile.matches) opener.focus();
    }
    opener.addEventListener('click', () => { open = true; render(); toggle.focus(); });
    toggle.addEventListener('click', () => {
        if (mobile.matches) { close(); return; }
        collapsed = !collapsed;
        try { localStorage.setItem('lotto.sidebar.collapsed', String(collapsed)); } catch (error) { /* Keep working without persistence. */ }
        render();
    });
    backdrop.addEventListener('click', () => close());
    groupToggle.addEventListener('click', () => {
        if (!mobile.matches && collapsed) {
            collapsed = false; groupOpen = true;
            try { localStorage.setItem('lotto.sidebar.collapsed', 'false'); } catch (error) { }
        } else groupOpen = !groupOpen;
        render();
    });
    document.addEventListener('keydown', event => {
        if (!mobile.matches || !open) return;
        if (event.key === 'Escape') { event.preventDefault(); close(); }
        if (event.key === 'Tab') {
            const focusable = [toggle, ...sidebar.querySelectorAll('nav button, nav a')].filter(element => !element.closest('[hidden]'));
            const first = focusable[0], last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    });
    links.forEach(link => link.addEventListener('click', () => {
        if (mobile.matches) close(false);
    }));
    mobile.addEventListener('change', () => { open = false; render(); if (mobile.matches && sidebar.contains(document.activeElement)) opener.focus(); });
    render();
})();
