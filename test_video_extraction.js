const https = require('https');
const { chromium } = require('playwright');

async function extractVideoUrl(url) {
    return new Promise((resolve, reject) => {
        const options = {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                'Referer': 'https://prmovies.delivery/'
            }
        };

        https.get(url, options, (res) => {
            let data = '';
            
            res.on('data', (chunk) => {
                data += chunk;
            });
            
            res.on('end', () => {
                // Try multiple patterns to extract m3u8 URL
                const patterns = [
                    /sources:\s*\[\s*\{\s*file\s*:\s*["']([^"']+\.m3u8[^"']*)["']/i,
                    /file\s*:\s*["']([^"']+\.m3u8[^"']*)["']/i,
                    /["']([^"']*\.m3u8[^"']*)["']/i
                ];

                for (const pattern of patterns) {
                    const match = data.match(pattern);
                    if (match) {
                        console.log('\n✅ Found video URL with pattern:', pattern.source);
                        console.log('Extracted URL:', match[1]);
                        resolve(match[1]);
                        return;
                    }
                }
                
                reject('No video URL found in response');
            });
        }).on('error', reject);
    });
}

async function testVideoUrl(videoUrl) {
    console.log('\n🧪 Testing if video URL is accessible...');
    
    return new Promise((resolve, reject) => {
        https.get(videoUrl, (res) => {
            console.log('Status Code:', res.statusCode);
            console.log('Content-Type:', res.headers['content-type']);
            
            if (res.statusCode === 200 || res.statusCode === 302) {
                console.log('✅ Video URL is accessible!');
                resolve(true);
            } else {
                console.log('❌ Video URL returned status:', res.statusCode);
                resolve(false);
            }
        }).on('error', (err) => {
            console.log('❌ Error accessing video URL:', err.message);
            reject(err);
        });
    });
}

async function testVideoPlayback(videoUrl) {
    console.log('\n🎬 Testing video playback in browser...');
    
    const browser = await chromium.launch({ headless: false });
    const context = await browser.newContext({
        extraHTTPHeaders: {
            'Referer': 'https://speedostream1.com/'
        }
    });
    const page = await context.newPage();
    
    try {
        // Create a simple HTML page to test video playback
        await page.setContent(`
            <!DOCTYPE html>
            <html>
            <head>
                <title>Video Test</title>
                <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
            </head>
            <body style="margin: 0; background: #000;">
                <video id="video" controls style="width: 100vw; height: 100vh;"></video>
                <div id="status" style="position: absolute; top: 10px; left: 10px; color: white; background: rgba(0,0,0,0.7); padding: 10px;"></div>
                <script>
                    const video = document.getElementById('video');
                    const status = document.getElementById('status');
                    const videoSrc = '${videoUrl}';
                    
                    status.textContent = 'Loading video...';
                    
                    if (Hls.isSupported()) {
                        const hls = new Hls({
                            xhrSetup: function(xhr, url) {
                                xhr.setRequestHeader('Referer', 'https://speedostream1.com/');
                            }
                        });
                        hls.loadSource(videoSrc);
                        hls.attachMedia(video);
                        
                        hls.on(Hls.Events.MANIFEST_PARSED, function() {
                            status.textContent = '✅ Video loaded successfully! Ready to play.';
                            status.style.color = '#0f0';
                            window.videoLoaded = true;
                        });
                        
                        hls.on(Hls.Events.ERROR, function(event, data) {
                            status.textContent = '❌ Error: ' + data.type + ' - ' + data.details;
                            status.style.color = '#f00';
                            window.videoError = data;
                        });
                    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                        video.src = videoSrc;
                        video.addEventListener('loadedmetadata', function() {
                            status.textContent = '✅ Video loaded successfully! Ready to play.';
                            status.style.color = '#0f0';
                            window.videoLoaded = true;
                        });
                        
                        video.addEventListener('error', function() {
                            status.textContent = '❌ Error loading video';
                            status.style.color = '#f00';
                            window.videoError = true;
                        });
                    }
                </script>
            </body>
            </html>
        `);
        
        // Wait for video to load or error (max 10 seconds)
        await page.waitForFunction(
            () => window.videoLoaded === true || window.videoError !== undefined,
            { timeout: 10000 }
        ).catch(() => {
            console.log('⚠️ Timeout waiting for video status');
        });
        
        const videoLoaded = await page.evaluate(() => window.videoLoaded);
        const videoError = await page.evaluate(() => window.videoError);
        
        if (videoLoaded) {
            console.log('✅ Video successfully loaded in browser!');
            console.log('⏸️ Keeping browser open for 5 seconds so you can see it...');
            await page.waitForTimeout(5000);
            return true;
        } else {
            console.log('❌ Video failed to load:', videoError);
            await page.waitForTimeout(3000);
            return false;
        }
        
    } catch (error) {
        console.log('❌ Error during playback test:', error.message);
        return false;
    } finally {
        await browser.close();
    }
}

async function main() {
    const speedoStreamUrl = 'https://speedostream1.com/embed-mq8ucd0yg4nj.html';
    
    console.log('🔍 Testing SpeedoStream extraction and playback');
    console.log('Target URL:', speedoStreamUrl);
    console.log('─'.repeat(60));
    
    try {
        // Step 1: Extract video URL
        console.log('\n📥 Step 1: Extracting video URL from page...');
        const videoUrl = await extractVideoUrl(speedoStreamUrl);
        
        // Step 2: Test if URL is accessible
        console.log('\n📡 Step 2: Testing URL accessibility...');
        await testVideoUrl(videoUrl);
        
        // Step 3: Test video playback
        console.log('\n🎥 Step 3: Testing actual video playback...');
        const playbackSuccess = await testVideoPlayback(videoUrl);
        
        // Final result
        console.log('\n' + '='.repeat(60));
        if (playbackSuccess) {
            console.log('✅ SUCCESS! Video extraction and playback working!');
            console.log('\n📋 Extraction regex pattern that works:');
            console.log('   sources:\\s*\\[\\s*\\{\\s*file\\s*:\\s*["\']([^"\']+\\.m3u8[^"\']*)');
            console.log('\n🎯 Ready to implement in stream.js!');
        } else {
            console.log('❌ FAILED! Video extraction or playback not working.');
            console.log('⚠️ Need to investigate further before implementing.');
        }
        console.log('='.repeat(60));
        
    } catch (error) {
        console.log('\n❌ Error:', error);
        console.log('\n⚠️ Extraction failed - need to debug before implementing');
    }
}

main();
