// Playwright script to analyze SpeedoStream and extract m3u8 URL
// Uses Brave browser to bypass ads

const { chromium } = require('playwright');

(async () => {
    // Launch Brave browser
    const browser = await chromium.launch({
        executablePath: 'C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe',
        headless: false, // Set to true for headless mode
        args: ['--disable-blink-features=AutomationControlled']
    });

    const context = await browser.newContext({
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    });

    const page = await context.newPage();

    // Intercept network requests to find m3u8 URLs
    const m3u8Urls = [];
    page.on('request', request => {
        const url = request.url();
        if (url.includes('.m3u8') || url.includes('master') || url.includes('playlist')) {
            console.log('🎬 M3U8 Request:', url);
            m3u8Urls.push(url);
        }
    });

    page.on('response', async response => {
        const url = response.url();
        if (url.includes('.m3u8')) {
            console.log('📼 M3U8 Response:', url);
        }
    });

    try {
        // Navigate to direct SpeedoStream page (not embed)
        // Get a working link from PrMovies first
        const prMoviesUrl = 'https://prmovies.delivery/stranger-things-2025-season-5-part-3-hindi-dubbed-Watch-online-full-movie/';
        console.log('Opening PrMovies:', prMoviesUrl);

        await page.goto(prMoviesUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
        await page.waitForTimeout(2000);

        // Extract SpeedoStream links from PrMovies page
        const speedoLinks = await page.evaluate(() => {
            const links = [];
            // Find iframes in #content-embed
            document.querySelectorAll('#content-embed iframe, iframe[src*="speedostream"]').forEach(iframe => {
                if (iframe.src) links.push(iframe.src);
            });
            // Find anchor links
            document.querySelectorAll('a[href*="speedostream"]').forEach(a => {
                if (a.href) links.push(a.href);
            });
            return links;
        });

        console.log('SpeedoStream links found:', speedoLinks);

        if (speedoLinks.length > 0) {
            // Navigate to the first SpeedoStream link
            const speedoUrl = speedoLinks[0];
            console.log('Opening SpeedoStream:', speedoUrl);

            await page.goto(speedoUrl, { waitUntil: 'networkidle', timeout: 30000 });
        } else {
            console.error('No SpeedoStream links found on PrMovies page.');
            await browser.close();
            return; // Exit if no link is found
        }

        // Wait a bit for player to initialize
        await page.waitForTimeout(3000);

        // Try to find and click the play button
        const playButton = await page.$('button, .play, .vjs-big-play-button, [class*="play"], .jw-icon-display');
        if (playButton) {
            console.log('Found play button, clicking...');
            await playButton.click();
            await page.waitForTimeout(5000);
        }

        // Extract video source from DOM
        const videoInfo = await page.evaluate(() => {
            const results = {
                videoSrc: null,
                sourceSrc: null,
                jwplayerSources: null,
                scriptPatterns: []
            };

            // Check video element
            const video = document.querySelector('video');
            if (video) {
                results.videoSrc = video.src || video.currentSrc;
            }

            // Check source elements
            const sources = document.querySelectorAll('source');
            sources.forEach(s => {
                if (s.src) {
                    results.sourceSrc = s.src;
                }
            });

            // Search script tags for jwplayer/sources config
            const scripts = document.querySelectorAll('script');
            scripts.forEach(script => {
                const text = script.innerText || '';

                // Look for file/sources patterns
                const patterns = [
                    /file\s*:\s*["']([^"']+\.m3u8[^"']*)["']/gi,
                    /sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']/gi,
                    /source\s*:\s*["']([^"']+\.m3u8[^"']*)["']/gi,
                    /"file"\s*:\s*"([^"]+\.m3u8[^"]*)"/gi
                ];

                patterns.forEach(pattern => {
                    const matches = text.matchAll(pattern);
                    for (const match of matches) {
                        if (match[1]) {
                            results.scriptPatterns.push(match[1]);
                        }
                    }
                });

                // Check for eval/packed JS
                if (text.includes('eval(') && text.includes('p,a,c,k,e,d')) {
                    results.hasPacked = true;
                }
            });

            return results;
        });

        console.log('\n📊 Video Info Found:');
        console.log(JSON.stringify(videoInfo, null, 2));

        console.log('\n🎥 M3U8 URLs from Network:');
        m3u8Urls.forEach(url => console.log(url));

        // Get full page HTML for analysis
        const html = await page.content();

        // Search for m3u8 patterns in HTML
        const m3u8Patterns = html.match(/https?:\/\/[^"'\s]+\.m3u8[^"'\s]*/gi);
        if (m3u8Patterns) {
            console.log('\n📝 M3U8 URLs in HTML:');
            m3u8Patterns.forEach(url => console.log(url));
        }

        // Save HTML for manual analysis
        const fs = require('fs');
        fs.writeFileSync('speedostream_source.html', html);
        console.log('\n✅ HTML saved to speedostream_source.html');

    } catch (error) {
        console.error('Error:', error.message);
    }

    // Keep browser open for manual inspection
    console.log('\nBrowser stays open. Press Ctrl+C to close.');
    await page.waitForTimeout(60000);

    await browser.close();
})();
