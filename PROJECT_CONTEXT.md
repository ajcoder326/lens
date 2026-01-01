# StreamBox Project Documentation

> **Last Updated:** January 1, 2026  
> **Purpose:** Context document for AI assistants working on this project

---

## Project Overview

**StreamBox** is an Android streaming application that uses a JavaScript-based extension system to aggregate content from various video sources. Extensions are written in JavaScript and executed via the Rhino JS engine within the app.

### Tech Stack
- **Platform:** Android (Kotlin, Jetpack Compose)
- **Media Player:** Media3/ExoPlayer
- **Dependency Injection:** Hilt
- **JS Runtime:** Rhino JavaScript Engine
- **Networking:** OkHttp, Axios (injected to JS)
- **HTML Parsing:** Cheerio (injected to JS)

---

## Repository Structure

```
d:\lens\StreamBox\
├── app/                          # Main Android application
│   └── src/main/java/com/streambox/app/
│       ├── extension/            # Extension management
│       ├── player/               # HiddenBrowserExtractor for WebView automation
│       ├── runtime/              # JSRuntime, ExtensionExecutor
│       ├── ui/                   # Compose screens & viewmodels
│       └── data/                 # Database, repositories
├── extensions/                   # Local extension copies
│   ├── hdhub4u/                  # v1 extension (WebView-based)
│   └── hdhub4u-2.0/              # v2 extension (Direct HTTP + fallback)
└── tools/                        # Helper scripts
```

---

## Extension System Architecture

### Extension Structure
Each extension is a folder containing:
```
extension-name/
├── manifest.json    # Metadata, module definitions
├── catalog.js       # Category definitions (var catalog = [...])
├── posts.js         # getPosts(filter, page) - fetch content listings
├── meta.js          # getMetaData(link) - fetch content details
└── stream.js        # getStreams(link, type) - extract playable URLs
```

### JS APIs Available to Extensions
| API | Usage |
|-----|-------|
| `axios.get(url, {headers})` | HTTP GET requests |
| `axios.post(url, data, {headers})` | HTTP POST requests |
| `cheerio.load(html)` | HTML parsing (jQuery-like) |
| `atob(str)` / `btoa(str)` | Base64 encode/decode |
| `console.log()` | Debug logging |
| `JSON.parse()` / `JSON.stringify()` | JSON handling |

### Stream Source Types
```javascript
// Returned by stream.js getStreams():
{
  server: "Server Name",
  link: "https://...",
  type: "direct" | "m3u8" | "automate" | "iframe",
  quality: "720p",
  headers: { "Referer": "..." },      // For m3u8 streams
  automation: "{ steps: [...] }"      // For WebView automation
}
```

---

## HDHub4u 2.0 Extension

**GitHub:** https://github.com/ajcoder326/hdhub2.0  
**Install URL:** https://ajcoder326.github.io/hdhub2.0/

### Supported Link Types
| Domain | Extraction Method |
|--------|------------------|
| `gadgetsweb.xyz` | Direct HTTP + decoding |
| `hblinks.dad` | Direct HTTP redirect following |
| `hubdrive.space` | Direct HTTP DOM parsing |
| `hubcloud.fyi` | Direct HTTP DOM parsing |
| `hubstream.art` | **WebView automation** (JS-rendered) |

### Decoding Chain (gadgetsweb)
```
HTML → find s('o','BASE64',...) → extract BASE64
     → atob() → atob() → ROT13 → atob() → JSON.parse()
     → { o: "nextUrlBase64" }
```

### Movie vs Series Handling
- **Movies:** Show quality links (480p, 720p, 1080p)
- **Series:** Show episode-by-episode links only (exclude full season ZIPs)

Detection: Title contains "season", "series", or "episode"

### Page Structure Patterns
**Pattern A (Mayor of Kingstown style):**
```html
<h3><a>EPiSODE 1</a> | <a>WATCH</a></h3>
```

**Pattern B (Four More Shots style):**
```html
<h4>EPiSODE 1</h4>
<h4>720p - <a>Drive</a> | <a>Instant</a></h4>
```

---

## HiddenBrowserExtractor Actions

For sites requiring JavaScript rendering (like HubStream), extensions return `type: "automate"` with automation rules:

| Action | Description |
|--------|-------------|
| `extractUrl` | Extract single URL from DOM, navigate to it |
| `extractLinks` | Extract multiple download links from DOM |
| `waitAndClick` | Wait for button, click it |
| `wait` | Poll for element to appear (max 10s) |
| `extractVideoUrl` | Extract m3u8 from video/source elements |

### Example Automation Rules
```javascript
{
  steps: [
    { action: "wait", selector: "video, source", timeout: 10000 },
    { action: "extractVideoUrl", 
      selectors: ["source[src*='.m3u8']"],
      patterns: [".m3u8"] }
  ],
  headers: { "Referer": "https://hubstream.art/" }
}
```

---

## Player Features

### Custom Headers for HLS
PlayerViewModel supports passing headers to ExoPlayer via `DefaultHttpDataSource.Factory`:
```kotlin
val dataSourceFactory = DefaultHttpDataSource.Factory()
    .setDefaultRequestProperties(headers)  // e.g., Referer

val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
    .createMediaSource(mediaItem)
```

### Gesture Controls
| Gesture | Side | Effect |
|---------|------|--------|
| Swipe Up/Down | Left | Brightness adjustment |
| Swipe Up/Down | Right | Volume adjustment |

---

## Key Implementation Files

### App Core
| File | Purpose |
|------|---------|
| `JSRuntime.kt` | Rhino JS engine setup, API injection |
| `ExtensionExecutor.kt` | Calls JS module functions, parses results |
| `HiddenBrowserExtractor.kt` | WebView automation for JS-rendered pages |
| `PlayerViewModel.kt` | Media playback, stream handling |
| `PlayerScreen.kt` | Player UI with gesture controls |

### Extension (HDHub4u 2.0)
| File | Purpose |
|------|---------|
| `posts.js` | Content listings, search via `/search/query/` |
| `meta.js` | Episode grouping, movie vs series detection |
| `stream.js` | Link extraction, WebView automation rules |

---

## Common Issues & Solutions

### 1. Rhino JS Compatibility
- **Problem:** Complex regex patterns crash
- **Solution:** Use `indexOf()` + `substring()` instead of regex

### 2. Search Not Working
- **Problem:** `?s=` URL requires JS
- **Solution:** Use `/search/query/` URL format

### 3. HubStream Not Playing
- **Problem:** m3u8 not in initial HTML (JS-rendered)
- **Solution:** Use WebView automation with `extractVideoUrl` action

### 4. Missing Referer Header
- **Problem:** Stream server rejects requests
- **Solution:** Return headers in stream response, PlayerViewModel applies them

---

## Build Commands

```powershell
# Build debug APK
.\gradlew assembleDebug

# Compile Kotlin only (fast check)
.\gradlew compileDebugKotlin

# APK output location
app\build\outputs\apk\debug\app-debug.apk
```

---

## GitHub Repositories

| Repository | URL |
|------------|-----|
| StreamBox App | `d:\lens\StreamBox` (local) |
| HDHub4u 2.0 Extension | https://github.com/ajcoder326/hdhub2.0 |
| Extension Install URL | https://ajcoder326.github.io/hdhub2.0/ |
