(function () {
    console.log("StreamBox AdBlocker Active");
    var css = `
    iframe[src*="ads"], iframe[src*="doubleclick"], iframe[src*="googlesyndication"],
    div[id^="taboola-"], div[id^="google_ads"], div[class^="adsbygoogle"],
    a[href*="popads"], a[href*="bet365"], a[href*="1xbet"], a[href*="4rabet"],
    .ad-banner, .popup-overlay, #popup, #overlay, .jw-ad,
    div[style*="z-index: 2147483647"], div[style*="z-index:9999999"],
    a[target="_blank"][href*="bitcoin"], a[target="_blank"][href*="casino"],
    .adsbox, .ad-box, .ad, .advertisement, [id^="ad-"], [class*=" ad-"]
    { display: none !important; visibility: hidden !important; height: 0 !important; width: 0 !important; }
    `;

    function injectStyle() {
        var head = document.head || document.getElementsByTagName('head')[0];
        if (head) {
            var style = document.createElement('style');
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

    // Block window.open
    var originalOpen = window.open;
    window.open = function (url, target, features) {
        console.log('Popup blocked: ' + url);
        return null;
    };

    // Nuke existing iframes that look suspicious
    setInterval(function () {
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
            var src = (iframes[i].src || '').toLowerCase();
            if (src.indexOf('ads') !== -1 || src.indexOf('bet') !== -1 || src.indexOf('pop') !== -1) {
                iframes[i].remove();
            }
        }
    }, 2000);
})();
