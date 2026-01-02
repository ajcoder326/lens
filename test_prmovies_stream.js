const { chromium } = require('playwright');

async function testPRMoviesExtraction() {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
  });
  const page = await context.newPage();

  try {
    console.log('Step 1: Loading PRMovies movie page...');
    await page.goto('https://prmovies.delivery/mass-jathara-2025-hindi-dubbed-Watch-online-full-movie/', {
      waitUntil: 'domcontentloaded',
      timeout: 30000
    });

    console.log('Step 2: Finding SpeedoStream links...');
    await page.waitForTimeout(2000);
    
    // Find all download/streaming links
    const links = await page.$$eval('#list-dl a.lnk-lnk', anchors => {
      return anchors.map(a => ({
        href: a.href,
        text: a.textContent.trim()
      }));
    });

    console.log(`Found ${links.length} links:`, links);

    // Find SpeedoStream link
    const speedoLink = links.find(l => l.href.includes('speedostream'));
    if (!speedoLink) {
      console.error('No SpeedoStream link found!');
      return;
    }

    console.log('Step 3: Navigating to SpeedoStream:', speedoLink.href);
    await page.goto(speedoLink.href, {
      waitUntil: 'domcontentloaded',
      timeout: 30000
    });

    console.log('Step 4: Waiting for player to load...');
    await page.waitForTimeout(5000);

    // Check for video element
    console.log('Step 5: Looking for video element...');
    const videoSrc = await page.evaluate(() => {
      // Try video source
      const video = document.querySelector('video');
      if (video) {
        console.log('Found video element');
        if (video.src) return { type: 'video.src', url: video.src };
        const source = video.querySelector('source');
        if (source && source.src) return { type: 'source.src', url: source.src };
      }

      // Try to find in scripts
      const scripts = Array.from(document.querySelectorAll('script'));
      for (const script of scripts) {
        const text = script.textContent || script.innerText;
        if (!text) continue;

        // Look for file: or source: patterns
        const fileMatch = text.match(/file\s*:\s*["']([^"']+\.m3u8[^"']*)["']/i);
        if (fileMatch) return { type: 'script.file', url: fileMatch[1] };

        const sourceMatch = text.match(/source\s*:\s*["']([^"']+\.m3u8[^"']*)["']/i);
        if (sourceMatch) return { type: 'script.source', url: sourceMatch[1] };

        const srcMatch = text.match(/src\s*:\s*["']([^"']+\.m3u8[^"']*)["']/i);
        if (srcMatch) return { type: 'script.src', url: srcMatch[1] };
      }

      return { type: 'none', url: null };
    });

    console.log('Video source found:', videoSrc);

    // Take a screenshot
    await page.screenshot({ path: 'd:/lens/StreamBox/speedostream_page.png', fullPage: true });
    console.log('Screenshot saved to speedostream_page.png');

    // Get page HTML for analysis
    const html = await page.content();
    const fs = require('fs');
    fs.writeFileSync('d:/lens/StreamBox/speedostream_page.html', html);
    console.log('HTML saved to speedostream_page.html');

    // Check for any iframes
    const iframes = await page.$$eval('iframe', frames => frames.map(f => f.src));
    console.log('Iframes found:', iframes);

  } catch (error) {
    console.error('Error:', error.message);
  } finally {
    await browser.close();
  }
}

testPRMoviesExtraction();
