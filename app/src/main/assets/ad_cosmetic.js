(function () {
    console.log("StreamBox AdBlocker Active - Enhanced");

    // Enhanced CSS for aggressive ad blocking - including SpeedoStream specific
    var css = `
    /* General Ad Elements */
    iframe[src*="ads"], iframe[src*="doubleclick"], iframe[src*="googlesyndication"],
    div[id^="taboola-"], div[id^="google_ads"], div[class^="adsbygoogle"],
    a[href*="popads"], a[href*="bet365"], a[href*="1xbet"], a[href*="4rabet"],
    .ad-banner, .popup-overlay, #popup, #overlay, .jw-ad,
    div[style*="z-index: 2147483647"], div[style*="z-index:9999999"],
    a[target="_blank"][href*="bitcoin"], a[target="_blank"][href*="casino"],
    .adsbox, .ad-box, .ad, .advertisement, [id^="ad-"], [class*=" ad-"],
    
    /* Streaming site specific */
    #ai-aiinhbfoopmessage-container,
    div[class*="banner"], div[id*="banner"],
    div[class*="popup"], div[id*="popup"],
    div[class*="overlay"]:not(#container),
    .sponsor, .sponsored, [class*="sponsor"],
    
    /* SpeedoStream specific */
    .adtop, .adbottom, .adside, .adcenter,
    div[style*="position: fixed"][style*="z-index"],
    div[style*="position:fixed"][style*="z-index"],
    
    /* Hide anything with common ad class patterns */
    [class*="advertising"], [id*="advertising"],
    [class*="promo-"], [id*="promo"],
    
    /* Block fake download buttons */
    a[href*="tracking"], a[href*="redirect"][href*="click"]
    { display: none !important; visibility: hidden !important; height: 0 !important; width: 0 !important; pointer-events: none !important; }
    
    /* Ensure main content is visible */
    #container, #player, .video-player, video { display: block !important; visibility: visible !important; }
    `;

    function injectStyle() {
        var head = document.head || document.getElementsByTagName('head')[0];
        if (head) {
            var existing = document.getElementById('streambox-adblocker-css');
            if (existing) existing.remove();

            var style = document.createElement('style');
            style.id = 'streambox-adblocker-css';
            style.type = 'text/css';
            style.appendChild(document.createTextNode(css));
            head.appendChild(style);
            console.log("StreamBox CSS injected");
        }
    }

    // Inject immediately
    injectStyle();

    // And on load
    window.addEventListener('load', injectStyle);

    // Re-inject after DOM changes (for dynamic content)
    var observer = new MutationObserver(function (mutations) {
        injectStyle();
    });
    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
    }

    // Block window.open (popups)
    var originalOpen = window.open;
    window.open = function (url, target, features) {
        console.log('StreamBox: Popup blocked: ' + url);
        return null;
    };

    // Block onclick handlers that open popups
    document.addEventListener('click', function (e) {
        var target = e.target.closest('a');
        if (target) {
            var href = target.href || '';
            var isAd = /popads|bet365|1xbet|4rabet|casino|bitcoin|tracking|click.*redirect/i.test(href);
            if (isAd || (target.target === '_blank' && !href.includes(window.location.hostname))) {
                console.log('StreamBox: Blocked ad click: ' + href);
                e.preventDefault();
                e.stopPropagation();
                return false;
            }
        }
    }, true);

    // Aggressive: Remove suspicious elements every second
    setInterval(function () {
        // Remove suspicious iframes
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
            var src = (iframes[i].src || '').toLowerCase();
            if (src.indexOf('ads') !== -1 || src.indexOf('bet') !== -1 || src.indexOf('pop') !== -1 ||
                src.indexOf('track') !== -1 || src.indexOf('click') !== -1) {
                console.log('StreamBox: Removed iframe: ' + src);
                iframes[i].remove();
            }
        }

        // Remove fixed position overlays (common ad pattern)
        var fixedDivs = document.querySelectorAll('div[style*="position: fixed"], div[style*="position:fixed"]');
        for (var j = 0; j < fixedDivs.length; j++) {
            var el = fixedDivs[j];
            var style = el.getAttribute('style') || '';
            if (style.indexOf('z-index') !== -1 && parseInt(el.style.zIndex) > 9000) {
                // Skip if it's likely a video player or controls
                if (!el.id || (el.id.indexOf('player') === -1 && el.id.indexOf('video') === -1)) {
                    console.log('StreamBox: Removed fixed overlay');
                    el.remove();
                }
            }
        }
    }, 1000);

    console.log("StreamBox AdBlocker: All protections active");
})();
