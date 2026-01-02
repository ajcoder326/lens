const { chromium } = require('playwright');

async function demonstrateDownloadFlow() {
    console.log('🎬 Demonstrating PRMovies → SpeedoStream Download Flow\n');
    console.log('Following the exact steps from RPA recording...\n');
    console.log('─'.repeat(80));
    
    const browser = await chromium.launch({
        headless: false,
        executablePath: 'C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe',
        args: ['--disable-blink-features=AutomationControlled']
    });
    
    const context = await browser.newContext({
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
    });
    
    const page = await context.newPage();
    
    // Track all URLs
    const capturedUrls = [];
    
    page.on('response', async (response) => {
        const url = response.url();
        if (url.includes('ydc1wes.me') || url.includes('.mp4') || url.includes('.mkv')) {
            console.log('✅ CAPTURED:', url);
            capturedUrls.push(url);
        }
    });
    
    try {
        // Step 1: Go to movie page
        console.log('\n📍 Step 1: Loading movie page...');
        const movieUrl = 'https://prmovies.delivery/mass-jathara-2025-hindi-dubbed-Watch-online-full-movie/';
        await page.goto(movieUrl, { waitUntil: 'domcontentloaded', timeout: 60000 });
        console.log('✅ Movie page loaded');
        console.log('⏸️ Wait for page to fully load (5 seconds)...');
        await page.waitForTimeout(5000);
        
        // Step 2: Find and click SpeedoStream download link
        console.log('\n📍 Step 2: Looking for download links...');
        await page.screenshot({ path: 'step1_movie_page.png' });
        
        const downloadLinks = await page.$$('a.lnk-lnk, #list-dl a');
        console.log(`Found ${downloadLinks.length} download links`);
        
        if (downloadLinks.length === 0) {
            console.log('❌ No download links found. You may need to solve Cloudflare manually.');
            console.log('⏸️ Browser will stay open. Click on a download link manually and press Enter here...');
            await new Promise(resolve => {
                process.stdin.once('data', () => resolve());
            });
        } else {
            // Click first link
            const firstLink = downloadLinks[0];
            const linkText = await firstLink.textContent();
            const linkHref = await firstLink.getAttribute('href');
            console.log(`\n🎯 Clicking: "${linkText}"`);
            console.log(`   URL: ${linkHref}`);
            
            await firstLink.click();
            await page.waitForTimeout(3000);
        }
        
        // Step 3: On SpeedoStream page, submit form
        console.log('\n📍 Step 3: On SpeedoStream page, looking for submit button...');
        await page.screenshot({ path: 'step2_speedostream_page.png' });
        
        const submitBtn = await page.$('#btn_download, input[type="submit"], button[type="submit"]');
        if (submitBtn) {
            const btnText = await submitBtn.evaluate(el => el.value || el.textContent || 'Submit');
            console.log(`🖱️ Clicking: "${btnText}"`);
            await submitBtn.click();
            await page.waitForTimeout(3000);
        } else {
            console.log('⚠️ No submit button found. Continuing...');
        }
        
        // Step 4: Quality selection page
        console.log('\n📍 Step 4: Quality selection page...');
        await page.screenshot({ path: 'step3_quality_page.png' });
        
        const qualityLinks = await page.$$('a[href*="/d/"]');
        console.log(`Found ${qualityLinks.length} quality links`);
        
        if (qualityLinks.length > 0) {
            const qualityLink = qualityLinks[0];
            const qualityText = await qualityLink.textContent();
            const qualityHref = await qualityLink.getAttribute('href');
            console.log(`\n🎯 Clicking quality: "${qualityText}"`);
            console.log(`   URL: ${qualityHref}`);
            
            await qualityLink.click();
            await page.waitForTimeout(3000);
        }
        
        // Step 5: Download page - submit form
        console.log('\n📍 Step 5: Download page, submitting form...');
        await page.screenshot({ path: 'step4_download_page.png' });
        
        const downloadBtn = await page.$('button[type="submit"], input[type="submit"]');
        if (downloadBtn) {
            const btnText = await downloadBtn.evaluate(el => el.textContent || el.value || 'Download');
            console.log(`🖱️ Clicking: "${btnText}"`);
            await downloadBtn.click();
            await page.waitForTimeout(3000);
        }
        
        // Step 6: Final page with direct download link
        console.log('\n📍 Step 6: Looking for final download link...');
        await page.screenshot({ path: 'step5_final_page.png' });
        
        const finalLinks = await page.$$('a[href*="ydc1wes.me"], a:has-text("Direct Download")');
        if (finalLinks.length > 0) {
            console.log(`\n✅ Found ${finalLinks.length} direct download links!`);
            
            for (let i = 0; i < finalLinks.length; i++) {
                const link = finalLinks[i];
                const text = await link.textContent();
                const href = await link.getAttribute('href');
                console.log(`\n${i + 1}. "${text}"`);
                console.log(`   ${href}`);
                capturedUrls.push(href);
            }
        }
        
        // Also check page source
        const content = await page.content();
        const sourceUrls = content.match(/https:\/\/[^"'\s]*ydc1wes\.me[^"'\s]*/g);
        if (sourceUrls) {
            console.log(`\n✅ Found ${sourceUrls.length} URLs in page source!`);
            sourceUrls.forEach((url, idx) => {
                if (!capturedUrls.includes(url)) {
                    console.log(`${idx + 1}. ${url}`);
                    capturedUrls.push(url);
                }
            });
        }
        
        console.log('\n' + '='.repeat(80));
        console.log('📊 SUMMARY - All Captured URLs');
        console.log('='.repeat(80));
        
        if (capturedUrls.length > 0) {
            capturedUrls.forEach((url, idx) => {
                console.log(`\n${idx + 1}. ${url}`);
            });
        } else {
            console.log('\n⚠️ No download URLs captured.');
            console.log('You may need to manually navigate through the pages.');
        }
        
        console.log('\n⏸️ Browser will stay open for 20 seconds. Check the final page.');
        await page.waitForTimeout(20000);
        
    } catch (error) {
        console.error('\n❌ Error:', error.message);
        await page.screenshot({ path: 'error_page.png' });
        console.log('📸 Error screenshot saved: error_page.png');
    } finally {
        await browser.close();
        console.log('\n✅ Done!');
    }
}

demonstrateDownloadFlow();
