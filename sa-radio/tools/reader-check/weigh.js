// Full-page weight (every request the page makes in ~20 s) vs reader weight (HTML + story pictures).
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const results = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  const pick = ['00.html','02.html','05.html','06.html','12.html','16.html','20.html','26.html'];
  const browser = await chromium.launch({ proxy: { server: process.env.HTTPS_PROXY } });
  const ua = 'Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36';
  for (const f of pick) {
    const r = results.find(x => x.file === f);
    const ctx = await browser.newContext({ userAgent: ua, viewport: { width: 412, height: 915 } });
    const page = await ctx.newPage();
    const cdp = await ctx.newCDPSession(page);
    await cdp.send('Network.enable');
    let bytes = 0, reqs = 0, adBytes = 0;
    const urls = {};
    cdp.on('Network.requestWillBeSent', e => { urls[e.requestId] = e.request.url; });
    cdp.on('Network.loadingFinished', e => {
      bytes += e.encodedDataLength; reqs++;
      if (/doubleclick|googlesyndication|googletagservices|adservice|adnxs|pubmatic|rubicon|openx|criteo|taboola|outbrain|teads|amazon-adsystem|smartadserver|casalemedia|3lift|sharethrough|indexww|yieldmo|adform|quantserve|scorecardresearch|chartbeat|facebook\.net|hotjar|permutive|moatads|id5-sync/i.test(urls[e.requestId] || '')) adBytes += e.encodedDataLength;
    });
    try { await page.goto(r.url, { waitUntil: 'domcontentloaded', timeout: 30000 }); } catch (e) {}
    await page.waitForTimeout(20000);
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight / 2)).catch(() => {});
    await page.waitForTimeout(5000);
    await ctx.close();
    console.log(JSON.stringify({ file: f, source: r.source, fullKB: Math.round(bytes / 1024), requests: reqs, adTrackerKB: Math.round(adBytes / 1024),
      images: [r.res.lead && r.res.lead.src].concat((r.res.blocks || []).filter(b => b.t === 'img').map(b => b.src)).filter(Boolean) }));
  }
  await browser.close();
})();
