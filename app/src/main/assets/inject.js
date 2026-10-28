(function() {
    'use strict';

    // 1. 强制注入移动端 Viewport 标签 (禁用缩放以获得原生 App 触感)
    function ensureViewport() {
        let meta = document.querySelector('meta[name="viewport"]');
        if (!meta) {
            meta = document.createElement('meta');
            meta.name = 'viewport';
            document.head.appendChild(meta);
        }
        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover';
    }
    ensureViewport();

    // 2. 注入全局播放控制函数（供 Android 原生 Notification / MediaSession 调用）
    window.__spotifyTogglePlay = function() {
        const btn = document.querySelector('[data-testid="control-button-playpause"]');
        if (btn) btn.click();
    };

    window.__spotifyNext = function() {
        const btn = document.querySelector('[data-testid="control-button-skip-forward"]');
        if (btn) btn.click();
    };

    window.__spotifyPrev = function() {
        const btn = document.querySelector('[data-testid="control-button-skip-back"]');
        if (btn) btn.click();
    };

    // 3. 构建高度仿真 Spotify 官方 App 的原生底部导航栏 (Bottom Navigation Bar)
    const NAV_ITEMS = [
        {
            id: 'nav-home',
            title: '首页',
            path: '/',
            svg: '<svg viewBox="0 0 24 24"><path d="M12.5 3.247a1 1 0 0 0-1 0L4 7.577V20h4.5v-6a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1v6H20V7.577l-7.5-4.33zm-2-1.732a3 3 0 0 1 3 0l7.5 4.33A2 2 0 0 1 22 7.577V21a1 1 0 0 1-1 1h-6.5a1 1 0 0 1-1-1v-5h-3v5a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7.577a2 2 0 0 1 .99-1.732l7.5-4.33z"/></svg>'
        },
        {
            id: 'nav-search',
            title: '搜索',
            path: '/search',
            svg: '<svg viewBox="0 0 24 24"><path d="M10.533 1.278a9.255 9.255 0 1 0 5.82 16.474l5.009 5.009a1 1 0 0 0 1.414-1.414l-5.01-5.01a9.255 9.255 0 0 0-7.233-15.06zm-7.255 9.255a7.255 7.255 0 1 1 14.51 0 7.255 7.255 0 0 1-14.51 0z"/></svg>'
        },
        {
            id: 'nav-library',
            title: '音乐库',
            path: '/collection/playlists',
            svg: '<svg viewBox="0 0 24 24"><path d="M14.5 2.134a1 1 0 0 1 1 0l6 3.464a1 1 0 0 1 .5.866V21a1 1 0 0 1-1 1h-6a1 1 0 0 1-1-1V3a1 1 0 0 1 .5-.866zM16 4.732V20h4V7.041l-4-2.309zM3 22a1 1 0 0 1-1-1V3a1 1 0 0 1 2 0v18a1 1 0 0 1-1 1zm6 0a1 1 0 0 1-1-1V3a1 1 0 0 1 2 0v18a1 1 0 0 1-1 1z"/></svg>'
        },
        {
            id: 'nav-liked',
            title: '喜欢的歌',
            path: '/collection/tracks',
            svg: '<svg viewBox="0 0 24 24"><path d="M8.667 1.912a6.257 6.257 0 0 0-5.462 3.726 6.157 6.157 0 0 0 .733 6.328l7.556 8.79a.75.75 0 0 0 1.132 0l7.556-8.79a6.157 6.157 0 0 0 .733-6.328 6.257 6.257 0 0 0-5.462-3.726 6.002 6.002 0 0 0-4.39 2.045 6.002 6.002 0 0 0-4.39-2.045z"/></svg>'
        }
    ];

    function navigateTo(targetPath) {
        if (window.location.pathname === targetPath) return;

        // 优先在 DOM 中寻找现成链接点击以触发 React 内部客户端路由
        const existingLink = document.querySelector(`a[href="${targetPath}"]`) ||
                             document.querySelector(`a[href="${targetPath}/"]`);
        if (existingLink) {
            existingLink.click();
            updateActiveTab();
            return;
        }

        // 回退到 HTML5 pushState
        try {
            window.history.pushState(null, '', targetPath);
            window.dispatchEvent(new PopStateEvent('popstate'));
        } catch (e) {
            window.location.href = targetPath;
        }

        setTimeout(() => {
            if (window.location.pathname !== targetPath) {
                window.location.href = targetPath;
            }
            updateActiveTab();
        }, 150);
    }

    function updateActiveTab() {
        const currentPath = window.location.pathname;
        NAV_ITEMS.forEach(item => {
            const el = document.getElementById(item.id);
            if (!el) return;
            const isMatch = (item.path === '/' && currentPath === '/') ||
                            (item.path !== '/' && currentPath.startsWith(item.path));
            if (isMatch) {
                el.classList.add('active');
            } else {
                el.classList.remove('active');
            }
        });
    }

    function ensureBottomNav() {
        if (document.getElementById('myspotify-bottom-nav')) {
            updateActiveTab();
            return;
        }

        const nav = document.createElement('div');
        nav.id = 'myspotify-bottom-nav';

        NAV_ITEMS.forEach(item => {
            const btn = document.createElement('div');
            btn.id = item.id;
            btn.className = 'myspotify-nav-item';
            btn.innerHTML = item.svg + `<span>${item.title}</span>`;
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                navigateTo(item.path);
            });
            nav.appendChild(btn);
        });

        document.body.appendChild(nav);
        updateActiveTab();
    }

    // 4. 监听播放状态、歌曲名称、艺人、封面变化
    let lastState = { isPlaying: false, title: '', artist: '', coverUrl: '' };

    function checkPlayback() {
        ensureViewport();
        ensureBottomNav();

        if (!window.AndroidBridge) return;

        // 获取播放/暂停按钮状态
        const playBtn = document.querySelector('[data-testid="control-button-playpause"]');
        let isPlaying = false;
        if (playBtn) {
            const label = (playBtn.getAttribute('aria-label') || '').toLowerCase();
            isPlaying = label.includes('pause') || label.includes('暂停');
        }

        // 获取曲名
        let title = '';
        const titleEl = document.querySelector('[data-testid="context-item-info-title"]') ||
                        document.querySelector('[data-testid="now-playing-widget"] a[data-testid="context-item-link"]');
        if (titleEl) {
            title = titleEl.innerText.trim();
        }

        // 获取歌手名
        let artist = '';
        const artistEl = document.querySelector('[data-testid="context-item-info-subtitles"]') ||
                         document.querySelector('[data-testid="context-item-info-artist"]');
        if (artistEl) {
            artist = artistEl.innerText.trim();
        }

        // 获取专辑封面
        let coverUrl = '';
        const imgEl = document.querySelector('[data-testid="cover-art-image"]');
        if (imgEl) {
            coverUrl = imgEl.src || '';
        }

        if (title && (isPlaying !== lastState.isPlaying || title !== lastState.title || artist !== lastState.artist || coverUrl !== lastState.coverUrl)) {
            lastState = { isPlaying, title, artist, coverUrl };
            try {
                window.AndroidBridge.onPlaybackStateChanged(isPlaying, title, artist, coverUrl);
            } catch (e) {
                console.error("Bridge notification error:", e);
            }
        }
    }

    // 5. 定时轮询 + DOM Mutation 观察 + 路由事件监听
    setInterval(checkPlayback, 800);
    window.addEventListener('popstate', updateActiveTab);

    const observer = new MutationObserver(function() {
        checkPlayback();
    });

    function attachObserver() {
        const target = document.body;
        if (target) {
            observer.observe(target, { childList: true, subtree: true, attributes: true });
        } else {
            setTimeout(attachObserver, 500);
        }
    }
    attachObserver();

    console.log("MySpotify Mobile UI Injector active.");
})();
