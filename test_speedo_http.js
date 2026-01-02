const axios = require('axios');
const cheerio = require('cheerio');
const qs = require('querystring');

async function testSpeedoStream() {
    const link = "https://speedostream1.com/ryvx95mwydz9.html";
    console.log("Testing:", link);

    const headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer": "https://prmovies.delivery/",
        "Content-Type": "application/x-www-form-urlencoded"
    };

    try {
        // STEP 0: GET main page first
        console.log("0. GET main page...");
        const resp0 = await axios.get(link, { headers });

        // Capture cookies
        let cookies = resp0.headers['set-cookie'];
        if (cookies) {
            console.log("Cookies found:", cookies);
            headers['Cookie'] = Array.isArray(cookies) ? cookies.join('; ') : cookies;
        }

        // STEP 1: POST to main page
        console.log("1. POST to main page...");
        const resp1 = await axios.post(link, "imhuman=", { headers });
        const $1 = cheerio.load(resp1.data);

        const qualityLinks = [];
        $1("a[href*='/d/']").each((i, el) => {
            let href = $1(el).attr('href');
            if (!href.startsWith('http')) href = "https://speedostream1.com" + href;
            qualityLinks.push({ href, text: $1(el).text().trim() });
        });

        console.log("Quality Links found:", qualityLinks.length);
        if (qualityLinks.length === 0) {
            console.log("Failed Step 1. Dumping headers used:", headers);
            // console.log(resp1.data.substring(0, 500));
            return;
        }

        // STEP 2: Process First Link
        const qLink = qualityLinks[0];
        console.log("2. Processing:", qLink.text, qLink.href);

        // STEP 3: GET the quality page
        headers["Referer"] = link;
        console.log("3. GET quality page...");
        const resp2 = await axios.get(qLink.href, { headers });

        // STEP 4: POST to quality page
        headers["Referer"] = qLink.href;
        console.log("4. POST to quality page...");
        const resp3 = await axios.post(qLink.href, "imhuman=", { headers });

        const $3 = cheerio.load(resp3.data);
        const directLink = $3("a[href*='ydc1wes.me']").attr('href');

        if (directLink) {
            console.log("✅ SUCCESS! Found direct link:", directLink);
        } else {
            console.log("❌ Failed to find direct link in Step 4 response.");
            // console.log(resp3.data.substring(0, 500));
        }

    } catch (e) {
        console.error("Error:", e.message);
        if (e.response) console.log("Status:", e.response.status);
    }
}

testSpeedoStream();
