// Runs the app's extractor in Chromium on saved article HTML: node run_extract.js <articles dir> <readability.js> <extract.js> <out.json>
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const [dir, rjs, ejs, outFile] = process.argv.slice(2);
  const index = JSON.parse(fs.readFileSync(path.join(dir, 'index.json'), 'utf8'));
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent('<html><body></body></html>');
  await page.addScriptTag({ content: fs.readFileSync(rjs, 'utf8') });
  await page.addScriptTag({ content: fs.readFileSync(ejs, 'utf8') });
  const results = [];
  for (const it of index) {
    if (!it.status.startsWith('200')) { results.push({ ...it, res: { ok: false, reason: 'http ' + it.status } }); continue; }
    const html = fs.readFileSync(path.join(dir, it.file), 'utf8');
    const t0 = Date.now();
    const json = await page.evaluate(([h, u]) => SAR.extract(h, u, 900), [html, it.url]);
    results.push({ ...it, ms: Date.now() - t0, res: JSON.parse(json) });
  }
  await browser.close();
  fs.writeFileSync(outFile, JSON.stringify(results, null, 1));
  const sus = /(subscribe|newsletter|advert|read more|also read|whatsapp|follow us|sign up|related|download the|recommended|cookie|comment)/i;
  for (const r of results) {
    const x = r.res, b = x.blocks || [];
    const c = t => b.filter(y => y.t === t).length;
    const paras = b.filter(y => y.t === 'p');
    console.log(`\n${r.file} ${r.source.padEnd(17)} ok=${x.ok} ${x.reason || ''} words=${x.words || 0} p=${c('p')} h=${c('h')} img=${c('img')} q=${c('quote')} list=${c('list')} embed=${c('embed')} lead=${x.lead ? 'y' : 'n'} pay=${x.paywalled} ${r.ms || 0}ms`);
    if (!b.length) continue;
    console.log('   T:', (x.title || '').slice(0, 90), '| by:', (x.byline || '').slice(0, 40));
    console.log('   first:', (paras[0] || {}).text?.slice(0, 110));
    console.log('   last :', (paras[paras.length - 1] || {}).text?.slice(0, 140));
    paras.filter(p => sus.test(p.text) && p.text.length < 220).forEach(p => console.log('   ?? ', p.text.slice(0, 140)));
  }
})();
