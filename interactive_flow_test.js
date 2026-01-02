const { chromium } = require('playwright');

async function interactiveTest() {
    console.log('🚀 Starting Interactive PRMovies Flow Test...\n');
    console.log('This script will open the browser and let YOU control it.');
    console.log('The script will just observe and log important information.\n');
    console.log('─'.repeat(80));
    
    // Launch Brave with stealth settings
    const browser = await chromium.launch({
        headless: false,
        executablePath: 'C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe',
        args: [
            '--disable-blink-features=AutomationControlled',
            '--disable-dev-shm-usage',
            '--no-sandbox',
            '--disable-web-security',
            '--disable-features=IsolateOrigins,site-per-process'
        ]
    });
    
    const context = await browser.newContext({
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        viewport: { width: 1920, height: 1080 },
        locale: 'en-US',
        timezoneId: 'America/New_York',
        permissions: ['geolocation']
    });
    
    // Add cookies to bypass Cloudflare
    await context.addCookies([
        {
            name: 'domain-alert',
            value: '1',
            domain: 'prmovies.delivery',
            path: '/',
            expires: 1767345594,
            httpOnly: false,
            secure: false,
            sameSite: 'Lax'
        }
    ]);
    
    // Remove automation indicators
    await context.addInitScript(() => {
        Object.defineProperty(navigator, 'webdriver', {
            get: () => false,
        });
        
        // Mock chrome object
        window.chrome = {
            runtime: {},
        };
        
        // Mock permissions
        const originalQuery = window.navigator.permissions.query;
        window.navigator.permissions.query = (parameters) => (
            parameters.name === 'notifications' ?
                Promise.resolve({ state: Notification.permission }) :
                originalQuery(parameters)
        );
    });
    
    const page = await context.newPage();
    
    // Track video/download URLs
    const foundUrls = {
        m3u8: new Set(),
        mp4: new Set(),
        download: new Set()
    };
    
    // Monitor network for video files
    page.on('request', request => {
        const url = request.url();
        
        if (url.includes('.m3u8')) {
            foundUrls.m3u8.add(url);
            console.log('\n🎯 M3U8 REQUEST:', url);
        } else if (url.includes('.mp4') || url.includes('.mkv')) {
            foundUrls.mp4.add(url);
            console.log('\n🎯 VIDEO REQUEST:', url);
        } else if (url.includes('ydc1wes') || url.includes('download')) {
            foundUrls.download.add(url);
            console.log('\n🎯 DOWNLOAD REQUEST:', url);
        }
    });
    
    page.on('response', async response => {
        const url = response.url();
        
        if (url.includes('.m3u8') && response.status() === 200) {
            console.log('✅ M3U8 RESPONSE:', response.status(), url.substring(0, 100));
        } else if ((url.includes('.mp4') || url.includes('.mkv')) && response.status() === 200) {
            console.log('✅ VIDEO RESPONSE:', response.status(), url.substring(0, 100));
        }
    });
    
    // Handle popups
    context.on('page', async (popup) => {
        const popupUrl = popup.url();
        console.log('\n🚫 POPUP DETECTED:', popupUrl);
        
        // Check if popup has download links
        try {
            await popup.waitForLoadState('domcontentloaded', { timeout: 3000 });
            const content = await popup.content();
            
            if (content.includes('.m3u8') || content.includes('.mp4')) {
                console.log('⚠️ Popup may contain video links, keeping it open...');
            } else {
                console.log('❌ Closing popup...');
                await popup.close();
            }
        } catch (e) {
            await popup.close();
        }
    });
    
    console.log('\n📍 Opening PRMovies homepage...');
    await page.goto('https://prmovies.delivery/', { 
        waitUntil: 'domcontentloaded',
        timeout: 60000 
    });
    
    console.log('\n✅ Browser is open!');
    console.log('\n' + '='.repeat(80));
    console.log('INSTRUCTIONS:');
    console.log('='.repeat(80));
    console.log('1. Solve any Cloudflare challenge if present');
    console.log('2. Click on any movie');
    console.log('3. Click on a download/stream link (SpeedoStream, etc.)');
    console.log('4. Follow through the pages until you see video or download links');
    console.log('5. The script will automatically detect and log any video URLs');
    console.log('\n🖥️ The browser will stay open. Check the terminal for logged URLs.');
    console.log('📝 Press Ctrl+C in the terminal when done to see the summary.');
    console.log('='.repeat(80) + '\n');
    
    // Keep monitoring
    let checkCount = 0;
    const monitorInterval = setInterval(async () => {
        checkCount++;
        
        if (checkCount % 5 === 0) {
            // Check page for video URLs every 5 seconds
            try {
                const pageContent = await page.content();
                
                // Look for m3u8 in page source
                const m3u8Matches = pageContent.match(/https?:\/\/[^"'\s]+\.m3u8[^"'\s]*/g);
                if (m3u8Matches) {
                    m3u8Matches.forEach(url => {
                        if (!foundUrls.m3u8.has(url)) {
                            foundUrls.m3u8.add(url);
                            console.log('\n✅ FOUND M3U8 IN PAGE SOURCE:', url);
                        }
                    });
                }
                
                // Look for jwplayer setup
                if (pageContent.includes('jwplayer') && pageContent.includes('sources')) {
                    const jwMatch = pageContent.match(/sources:\s*\[\s*\{\s*file:\s*["']([^"']+)["']/);
                    if (jwMatch && jwMatch[1]) {
                        const videoUrl = jwMatch[1];
                        if (!foundUrls.m3u8.has(videoUrl) && !foundUrls.mp4.has(videoUrl)) {
                            if (videoUrl.includes('.m3u8')) {
                                foundUrls.m3u8.add(videoUrl);
                                console.log('\n✅ FOUND VIDEO IN JWPLAYER:', videoUrl);
                            } else if (videoUrl.includes('.mp4')) {
                                foundUrls.mp4.add(videoUrl);
                                console.log('\n✅ FOUND VIDEO IN JWPLAYER:', videoUrl);
                            }
                        }
                    }
                }
            } catch (e) {
                // Ignore errors during monitoring
            }
        }
    }, 1000);
    
    // Wait for user to close browser or press Ctrl+C
    await new Promise((resolve) => {
        process.on('SIGINT', () => {
            clearInterval(monitorInterval);
            resolve();
        });
        
        // Also check if browser is closed
        const checkBrowser = setInterval(async () => {
            try {
                await page.title();
            } catch (e) {
                clearInterval(checkBrowser);
                clearInterval(monitorInterval);
                resolve();
            }
        }, 1000);
    });
    
    console.log('\n\n' + '='.repeat(80));
    console.log('📊 SUMMARY - URLs FOUND');
    console.log('='.repeat(80));
    
    if (foundUrls.m3u8.size > 0) {
        console.log('\n✅ M3U8 URLs (' + foundUrls.m3u8.size + '):');
        foundUrls.m3u8.forEach((url, idx) => {
            console.log(`\n${idx + 1}. ${url}`);
        });
    }
    
    if (foundUrls.mp4.size > 0) {
        console.log('\n✅ MP4/MKV URLs (' + foundUrls.mp4.size + '):');
        foundUrls.mp4.forEach((url, idx) => {
            console.log(`\n${idx + 1}. ${url}`);
        });
    }
    
    if (foundUrls.download.size > 0) {
        console.log('\n✅ Download URLs (' + foundUrls.download.size + '):');
        foundUrls.download.forEach((url, idx) => {
            console.log(`\n${idx + 1}. ${url}`);
        });
    }
    
    if (foundUrls.m3u8.size === 0 && foundUrls.mp4.size === 0 && foundUrls.download.size === 0) {
        console.log('\n⚠️ No video URLs detected.');
        console.log('Make sure you clicked through to a video/download page.');
    }
    
    console.log('\n' + '='.repeat(80));
    
    await browser.close();
    console.log('\n✅ Test complete!');
}

interactiveTest().catch(console.error);
