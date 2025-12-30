# StreamBox Extension Development Guide

## Overview

StreamBox extensions are JavaScript modules that provide content catalogs, search, metadata, and streaming sources. They follow the Vega provider format for compatibility.

---

## Extension Structure

An extension consists of a `manifest.json` and JavaScript modules:

```
my-extension/
├── manifest.json     # Extension metadata
├── catalog.js        # Categories/filters
├── posts.js          # Content listings & search
├── meta.js           # Content details
├── stream.js         # Video sources
└── episodes.js       # (optional) Episode listings
```

---

## manifest.json

```json
{
  "name": "My Extension",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "Description of your extension",
  "icon": "https://example.com/icon.png",
  "modules": {
    "catalog": "catalog.js",
    "posts": "posts.js",
    "meta": "meta.js",
    "stream": "stream.js",
    "episodes": "episodes.js"
  }
}
```

---

## Module APIs

### catalog.js

Exports an array of catalog categories:

```javascript
const catalog = [
  { title: "Latest", filter: "" },
  { title: "Movies", filter: "movies" },
  { title: "TV Shows", filter: "tv" },
  { title: "Trending", filter: "trending" }
];

// Optional: Genre filters
const genres = [
  { title: "Action", filter: "genre/action" },
  { title: "Comedy", filter: "genre/comedy" }
];
```

---

### posts.js

Two functions for fetching content:

```javascript
// Get posts by filter (from catalog.js)
async function getPosts(filter, page, providerContext) {
  const url = `https://example.com/api/${filter}?page=${page}`;
  const response = await axios.get(url);
  const $ = cheerio.load(response.data);
  
  const posts = [];
  $(".item").each((i, el) => {
    posts.push({
      title: $(el).find(".title").text().trim(),
      link: $(el).find("a").attr("href"),
      image: $(el).find("img").attr("src")
    });
  });
  
  return posts;
}

// Search posts by query
async function getSearchPosts(query, page, providerContext) {
  const url = `https://example.com/search?q=${encodeURIComponent(query)}&page=${page}`;
  const response = await axios.get(url);
  const $ = cheerio.load(response.data);
  
  const posts = [];
  $(".result").each((i, el) => {
    posts.push({
      title: $(el).find(".title").text(),
      link: $(el).find("a").attr("href"),
      image: $(el).find("img").attr("src")
    });
  });
  
  return posts;
}
```

**Post Object:**
```javascript
{
  title: "Movie Title",      // Required
  link: "/movie/12345",      // Required - used for meta.js
  image: "https://..."       // Poster image URL
}
```

---

### meta.js

Fetches detailed content information:

```javascript
async function getMetaData(link, providerContext) {
  const response = await axios.get(link);
  const $ = cheerio.load(response.data);
  
  return {
    title: $("h1.title").text(),
    image: $(".poster img").attr("src"),
    poster: $(".poster img").attr("src"),
    background: $(".backdrop img").attr("src"),
    synopsis: $(".description").text(),
    type: "movie",  // or "series"
    rating: $(".rating").text(),
    year: $(".year").text(),
    tags: $(".genre a").map((i, el) => $(el).text()).get(),
    linkList: [
      {
        title: "1080p",
        quality: "1080p",
        link: "/stream/12345/1080",
        directLinks: [
          { title: "Server 1", link: "https://..." }
        ]
      }
    ]
  };
}
```

**ContentInfo Object:**
```javascript
{
  title: "Movie Title",       // Required
  image: "https://...",       // Required - poster
  synopsis: "Description...", // Required
  type: "movie" | "series",   // Content type
  rating: "8.5",              // Optional
  year: "2024",               // Optional
  poster: "https://...",      // Optional - same as image
  background: "https://...",  // Optional - backdrop
  tags: ["Action", "Drama"],  // Optional - genres
  linkList: [...]             // Stream sources
}
```

---

### stream.js

Extracts actual video stream URLs:

```javascript
async function getStream(link, type, providerContext) {
  const response = await axios.get(link);
  const $ = cheerio.load(response.data);
  
  // Extract video sources
  const streams = [];
  
  // Example: Extract m3u8 from page
  const m3u8Match = response.data.match(/source:\s*["']([^"']+\.m3u8[^"']*)/);
  if (m3u8Match) {
    streams.push({
      server: "Direct",
      link: m3u8Match[1],
      type: "m3u8",
      quality: "1080p"
    });
  }
  
  // Example: Handle encrypted sources
  const encoded = $("script").text().match(/encoded\s*=\s*["']([^"']+)/);
  if (encoded) {
    const decoded = atob(encoded[1]);
    streams.push({
      server: "Backup",
      link: decoded,
      type: "mp4",
      quality: "720p"
    });
  }
  
  return streams;
}
```

**StreamSource Object:**
```javascript
{
  server: "Server Name",    // Required
  link: "https://...",      // Required - video URL
  type: "m3u8" | "mp4",     // Stream type
  quality: "1080p",         // Optional
  subtitles: [              // Optional
    { lang: "English", url: "https://..." }
  ]
}
```

---

### episodes.js (Optional)

For TV series episode listings:

```javascript
async function getEpisodes(link, providerContext) {
  const response = await axios.get(link);
  const $ = cheerio.load(response.data);
  
  const episodes = [];
  $(".episode").each((i, el) => {
    episodes.push({
      title: $(el).find(".ep-title").text(),
      link: $(el).find("a").attr("href")
    });
  });
  
  return episodes;
}
```

---

## Available APIs

### HTTP Requests

```javascript
// GET request
const response = await axios.get(url, {
  headers: {
    "User-Agent": "Mozilla/5.0...",
    "Referer": "https://example.com"
  }
});
console.log(response.data);

// POST request
const response = await axios.post(url, "key=value", {
  headers: { "Content-Type": "application/x-www-form-urlencoded" }
});
```

### HTML Parsing (Cheerio)

```javascript
const $ = cheerio.load(html);

// Select elements
$(".class")
$("#id")
$("div.class > a")

// Get text/attributes
$(".title").text()
$("a").attr("href")
$("img").attr("src")

// Iterate
$(".item").each((index, element) => {
  const title = $(element).find(".title").text();
});

// Map to array
const titles = $(".item").map((i, el) => $(el).text()).get();

// Filter
$(".item").filter(".active")

// Navigation
$(el).find(".child")
$(el).parent()
$(el).children()
$(el).next()
$(el).prev()
```

### Crypto Utilities

```javascript
// Base64
const encoded = btoa("hello");      // "aGVsbG8="
const decoded = atob("aGVsbG8=");   // "hello"

// MD5 hash
const hash = crypto.md5("text");

// AES decryption
const decrypted = crypto.aesDecrypt(encryptedData, key, iv);
```

### Console (Debug)

```javascript
console.log("Debug message");
console.error("Error message");
```

---

## Hosting Your Extension

1. **GitHub Pages** (Recommended):
   - Create a GitHub repo with your extension files
   - Enable GitHub Pages
   - Manifest URL: `https://username.github.io/repo/manifest.json`

2. **Any Web Server**:
   - Host files on any HTTPS server
   - Ensure CORS headers allow requests

---

## Adding Extension to StreamBox

1. Open StreamBox app
2. Go to **Extensions** screen
3. Tap **Add Extension**
4. Enter manifest URL: `https://your-host.com/manifest.json`
5. Extension will be downloaded and cached

---

## Example: Minimal Extension

**manifest.json:**
```json
{
  "name": "Demo Extension",
  "version": "1.0.0",
  "modules": {
    "catalog": "catalog.js",
    "posts": "posts.js",
    "meta": "meta.js",
    "stream": "stream.js"
  }
}
```

**catalog.js:**
```javascript
const catalog = [
  { title: "All", filter: "" }
];
```

**posts.js:**
```javascript
async function getPosts(filter, page) {
  return [
    { title: "Big Buck Bunny", link: "bunny", image: "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/220px-Big_buck_bunny_poster_big.jpg" }
  ];
}

async function getSearchPosts(query, page) {
  return getPosts("", page);
}
```

**meta.js:**
```javascript
async function getMetaData(link) {
  return {
    title: "Big Buck Bunny",
    image: "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/220px-Big_buck_bunny_poster_big.jpg",
    synopsis: "A short film by the Blender Foundation",
    type: "movie",
    year: "2008",
    linkList: [
      { title: "Play", link: "bunny-stream", quality: "1080p" }
    ]
  };
}
```

**stream.js:**
```javascript
async function getStream(link, type) {
  return [
    {
      server: "Direct",
      link: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
      type: "m3u8",
      quality: "1080p"
    }
  ];
}
```

---

## Tips

1. **Test with console.log** - Messages appear in Android Logcat
2. **Handle errors** - Wrap in try/catch
3. **Respect rate limits** - Add delays between requests if needed
4. **Use full URLs** - Resolve relative URLs to absolute
5. **Check CORS** - Some sites block requests; may need workarounds
