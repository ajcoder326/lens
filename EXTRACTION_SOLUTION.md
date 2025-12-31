# HDHub4u Extraction - Complete Solution

## Problem
HDHub4u uses Cloudflare-protected hosting pages (hubdrive, hubcloud, hubcdn) with:
- Ad popups that intercept clicks
- Overlay elements blocking buttons  
- Multi-step flow: movie page → hubdrive → hubcloud → gamerxyt → final download

## Working Solution (Playwright)

Successfully created `extract_based_on_recording.py` that:

### Flow
1. **Movie Page** → Extract hubdrive.space links
2. **HubDrive Page** → Click [HubCloud Server] button
3. **HubCloud Page** → Click "Generate Direct Download Link"
4. **GamerXYT Page** → Extract final download URLs

### Key Techniques
- **Brave Browser**: Built-in ad blocker
- **Direct Navigation**: Use `page.goto()` instead of clicking to avoid ad interception
- **Overlay Removal**: JavaScript to remove `#dontfoid` ad overlays
- **DOM Content Loaded**: Wait for `domcontentloaded` instead of `networkidle` (faster)
- **Popup Blocking**: Auto-close ad popups (4rabet, betting sites)

### Results
✅ Successfully extracts 4 download URLs per movie:
- FSLv2 Server (direct .mkv)
- FSL Server
- HubCDN PixelServer
- Pixeldrain

## Android App Status

### Current Implementation
- ✅ Extension system working
- ✅ stream.js returns 11 links from hblinks.dad
- ✅ Links display on Info page
- ✅ WebViewExtractor.kt created
- ✅ PlayerViewModel handles "web" type streams

### Issue
The hubcloud/hubcdn links returned by stream.js are **hosting pages**, not direct video URLs.
These require the multi-step flow above to get the actual download link.

### Attempted Solutions
1. **WebView Automation** ❌ - Too complex, needs to handle:
   - Multiple page navigations
   - Ad popup blocking
   - Overlay removal
   - Button clicking
   
2. **Direct URL Construction** ❌ - gamerxyt.com link requires:
   - `host` parameter
   - `id` from hubcloud URL
   - `token` (Base64 encoded, only available after loading hubcloud page)

### Recommended Approaches

#### Option 1: Open in Browser (Simplest)
Update `PlayerViewModel.kt` to open "web" type links in device browser:
```kotlin
if (selectedStream.type == "web") {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedStream.link))
    context.startActivity(intent)
}
```
User manually goes through: hubdrive → hubcloud → download

#### Option 2: Backend API (Best)
Create a Node.js backend using Playwright:
- Android app sends hubdrive URL to backend
- Backend runs Playwright automation
- Returns final download URLs to app
- App plays the video

#### Option 3: Extract Direct Links Earlier
Modify `stream.js` to:
- Visit each hubdrive link
- Extract the gamerxyt.com URL
- Return only final download URLs
**Problem**: Requires HTTP client to support JavaScript execution (not available in Rhino)

## Files Created

### Python Scripts
1. `extract_based_on_recording.py` ✅ - **WORKING** complete extraction
2. `extract_hubcloud.py` - Initial attempt
3. `test_movie_page.py` - Flow analysis
4. `full_extraction.py` - Earlier version

### Android Files
1. `WebViewExtractor.kt` - WebView-based extraction class
2. `PlayerViewModel.kt` - Updated to handle "web" type streams
3. `stream.js` - Updated to return type: "web" for hubcloud links

## Next Steps

### For Testing
Run the working extraction:
```bash
python extract_based_on_recording.py
```

### For Android App
Choose one of the 3 recommended approaches above.

**Recommended**: Option 2 (Backend API) for best user experience with full automation.

## Screenshots
- `step1_movie_page.png` - Movie listing
- `step2_hubdrive_page.png` - HubDrive with [HubCloud Server] button
- `step3_hubcloud_page.png` - HubCloud with "Generate" button
- `step4_final_page.png` - GamerXYT with final download links

## Extracted Links Example
```json
{
  "movie_url": "https://new1.hdhub4u.fo/mandali-2023-webrip-hindi-full-movie/",
  "extracted_urls": [
    "https://fsl.gigabytes.icu/Mandali.2023.480p.Hindi.WEB-DL.ESub.x264-HDHub4u.Ms.mkv?token=...",
    "https://fsl.firecdn.buzz/a92ba2b39cc256ae63537e81fc7dedde?token=...",
    "https://pixel.hubcdn.fans/?id=...",
    "https://pixeldrain.dev/u/BPE8GD9R"
  ]
}
```
