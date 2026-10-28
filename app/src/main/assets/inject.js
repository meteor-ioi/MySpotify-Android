(function() {
    'use strict';

    // 1. 确保注入移动端视口 Viewport 标签
    let meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
        meta = document.createElement('meta');
        meta.name = 'viewport';
        document.head.appendChild(meta);
    }
    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

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

    // 3. 监听播放状态、歌曲名称、艺人、封面变化
    let lastState = { isPlaying: false, title: '', artist: '', coverUrl: '' };

    function checkPlayback() {
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

        // 仅在有实际曲目信息且状态变更时通知 Android 端
        if (title && (isPlaying !== lastState.isPlaying || title !== lastState.title || artist !== lastState.artist || coverUrl !== lastState.coverUrl)) {
            lastState = { isPlaying, title, artist, coverUrl };
            try {
                window.AndroidBridge.onPlaybackStateChanged(isPlaying, title, artist, coverUrl);
            } catch (e) {
                console.error("Bridge notification error:", e);
            }
        }
    }

    // 4. 定时轮询 + DOM Mutation 观察
    setInterval(checkPlayback, 800);

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

    console.log("MySpotify Web Injector initialized.");
})();
