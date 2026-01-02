const { chromium } = require('playwright');

async function testPRMoviesFlow() {
    console.log('🚀 Starting PRMovies complete flow analysis...\n');
    
    // Launch Brave browser
    const browser = await chromium.launch({
        headless: false,
        executablePath: 'C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe',
        args: ['--disable-blink-features=AutomationControlled']
    });
    
    const context = await browser.newContext({
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        extraHTTPHeaders: {
            'Accept-Language': 'en-US,en;q=0.9'
        }
    });
    
    const page = await context.newPage();
    
    // Track all download links found
    const downloadLinks = [];
    
    // Track network requests for video files
    page.on('request', request => {
        const url = request.url();
        if (url.includes('m3u8') || url.includes('.mp4') || url.includes('.mkv')) {
            console.log('📤 VIDEO REQUEST:', request.method(), url.substring(0, 120));
        }
    });
    
    page.on('response', async response => {
        const url = response.url();
        if (url.includes('m3u8') || url.includes('.mp4') || url.includes('.mkv')) {
            console.log('📥 VIDEO RESPONSE:', response.status(), url.substring(0, 120));
        }
    });
    
    // Handle popups
    context.on('page', async (popup) => {
        console.log('🚫 Popup detected, closing:', popup.url());
        await popup.close();
    });
    
    try {
        // Step 1: Go to PRMovies homepage
        console.log('📍 Step 1: Loading PRMovies homepage...');
        console.log('─'.repeat(80));
        
        await page.goto('https://prmovies.delivery/', { 
            waitUntil: 'domcontentloaded',
            timeout: 60000 
        });
        
        console.log('✅ Page loaded');
        console.log('⏸️ Waiting 10 seconds for Cloudflare challenge (please solve manually)...\n');
        await page.waitForTimeout(10000);
        
        // Take screenshot for debugging
        await page.screenshot({ path: 'prmovies_homepage.png' });
        console.log('📸 Screenshot saved: prmovies_homepage.png\n');
        
        // Step 2: Find and click on first movie
        console.log('📍 Step 2: Looking for movies...');
        
        // Try multiple selectors
        let movieLinks = await page.$$('div.ml-item a.ml-mask');
        if (movieLinks.length === 0) {
            movieLinks = await page.$$('article.item a.absolute');
        }
        if (movieLinks.length === 0) {
            movieLinks = await page.$$('article a[href*="prmovies"]');
        }
        if (movieLinks.length === 0) {
            movieLinks = await page.$$('a[title]');
        }
        
        console.log(`Found ${movieLinks.length} potential movie links\n`);
        
        if (movieLinks.length === 0) {
            throw new Error('No movies found on homepage');
        }
        
        // Get first movie details
        const firstMovie = movieLinks[0];
        const movieTitle = await firstMovie.evaluate(el => {
            const img = el.querySelector('img');
            return img?.alt || el.getAttribute('title') || 'Unknown';
        });
        const movieUrl = await firstMovie.evaluate(el => el.href);
        
        console.log(`🎬 Selected movie: "${movieTitle}"`);
        console.log(`🔗 URL: ${movieUrl}\n`);
        
        // Step 3: Go to movie page
        console.log('📍 Step 3: Opening movie page...');
        await page.goto(movieUrl, { 
            waitUntil: 'domcontentloaded',
            timeout: 30000 
        });
        console.log('✅ Movie page loaded\n');
        await page.waitForTimeout(2000);
        
        // Step 4: Find download links on movie page
        console.log('📍 Step 4: Looking for download/stream links...');
        
        // PRMovies uses .lnk-lnk class for links
        const streamLinks = await page.$$('a.lnk-lnk, #list-dl a, a[href*="speedostream"], a[href*="streamwish"]');
        console.log(`Found ${streamLinks.length} stream links\n`);
        
        // Get all links info
        for (let i = 0; i < Math.min(streamLinks.length, 10); i++) {
            const linkInfo = await streamLinks[i].evaluate(el => ({
                text: el.textContent.trim(),
                href: el.href,
                classes: el.className
            }));
            console.log(`  ${i + 1}. "${linkInfo.text}" -> ${linkInfo.href.substring(0, 80)}`);
            downloadLinks.push(linkInfo);
        }
        
        if (streamLinks.length === 0) {
            throw new Error('No download links found on movie page');
        }
        
        // Step 5: Click on first SpeedoStream link
        console.log('\n📍 Step 5: Clicking on first download link...');
        const targetLink = streamLinks[0];
        const targetUrl = await targetLink.evaluate(el => el.href);
        console.log(`🎯 Target: ${targetUrl}\n`);
        
        // Click and wait for navigation or popup
        await Promise.race([
            targetLink.click(),
            page.waitForTimeout(1000)
        ]);
        
        await page.waitForTimeout(3000);
        
        // Check if we're on a new page or if a popup opened
        const currentUrl = page.url();
        console.log(`Current URL: ${currentUrl}\n`);
        
        // Step 6: Handle the download/stream page
        console.log('📍 Step 6: Analyzing download page...');
        
        // Look for forms to submit
        const forms = await page.$$('form');
        console.log(`Found ${forms.length} forms`);
        
        if (forms.length > 0) {
            // Submit first form (usually "I'm Human" verification)
            console.log('📝 Submitting form...');
            const submitBtn = await page.$('button[type="submit"], input[type="submit"]');
            if (submitBtn) {
                await submitBtn.click();
                await page.waitForTimeout(2000);
            }
        }
        
        // Look for buttons to click
        const buttons = await page.$$('button, input[type="button"], a.btn, .download-btn');
        console.log(`Found ${buttons.length} buttons\n`);
        
        for (let i = 0; i < Math.min(buttons.length, 5); i++) {
            const btnText = await buttons[i].evaluate(el => el.textContent?.trim() || el.value || '');
            console.log(`  Button ${i + 1}: "${btnText}"`);
        }
        
        // Step 7: Look for final download links
        console.log('\n📍 Step 7: Looking for final download links...');
        
        const finalLinks = await page.$$('a[href*=".mp4"], a[href*=".mkv"], a[href*="download"], a:has-text("Download")');
        console.log(`Found ${finalLinks.length} potential download links\n`);
        
        const foundDownloads = [];
        for (let i = 0; i < finalLinks.length; i++) {
            const linkInfo = await finalLinks[i].evaluate(el => ({
                text: el.textContent.trim(),
                href: el.href
            }));
            
            if (linkInfo.href.includes('.mp4') || linkInfo.href.includes('.mkv') || linkInfo.href.includes('ydc1wes')) {
                console.log(`✅ DOWNLOAD LINK ${i + 1}: "${linkInfo.text}"`);
                console.log(`   ${linkInfo.href}\n`);
                foundDownloads.push(linkInfo);
            }
        }
        
        // Step 8: Check page source for m3u8 or video URLs
        console.log('📍 Step 8: Checking page source for video URLs...');
        const pageContent = await page.content();
        
        const m3u8Matches = pageContent.match(/https?:\/\/[^"'\s]+\.m3u8[^"'\s]*/g);
        if (m3u8Matches && m3u8Matches.length > 0) {
            console.log('\n✅ Found M3U8 URLs in page source:');
            m3u8Matches.forEach((url, idx) => {
                console.log(`  ${idx + 1}. ${url}`);
                foundDownloads.push({ type: 'm3u8', href: url });
            });
        }
        
        const mp4Matches = pageContent.match(/https?:\/\/[^"'\s]+\.mp4[^"'\s]*/g);
        if (mp4Matches && mp4Matches.length > 0) {
            console.log('\n✅ Found MP4 URLs in page source:');
            mp4Matches.forEach((url, idx) => {
                console.log(`  ${idx + 1}. ${url}`);
            });
        }
        
        // Step 9: Summary
        console.log('\n' + '='.repeat(80));
        console.log('📊 SUMMARY');
        console.log('='.repeat(80));
        console.log(`Movie: ${movieTitle}`);
        console.log(`Stream links found on movie page: ${downloadLinks.length}`);
        console.log(`Final download links found: ${foundDownloads.length}`);
        
        if (foundDownloads.length > 0) {
            console.log('\n✅ SUCCESS! Found working download links:');
            foundDownloads.forEach((link, idx) => {
                console.log(`\n${idx + 1}. ${link.text || 'Direct Link'}`);
                console.log(`   ${link.href}`);
            });
        }
        
        console.log('\n⏸️ Keeping browser open for 15 seconds for inspection...');
        await page.waitForTimeout(15000);
        console.log('\n⏸️ Keeping browser open for 15 seconds for inspection...');
        await page.waitForTimeout(15000);
        
    } catch (error) {
        console.error('❌ Error:', error.message);
        console.error(error.stack);
    } finally {
        await browser.close();
        console.log('\n✅ Test complete!');
    }
}

testPRMoviesFlow();
