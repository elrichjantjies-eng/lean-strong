import re, sys, json, subprocess, html, os
out = sys.argv[1]
UA = "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"
feeds = [l.split('"')[1::2] for l in open(sys.argv[2]) if l.strip().startswith('{"')]
items = []
for name, url in feeds:
    try:
        x = subprocess.run(["curl","-sSL","--max-time","25","-A",UA,url],capture_output=True,timeout=40).stdout.decode("utf-8","replace")
    except Exception as e:
        print(name, "feed error", e); continue
    links = []
    for it in re.findall(r"<(?:item|entry)\b.*?</(?:item|entry)>", x, re.S):
        m = re.search(r"<link>\s*(?:<!\[CDATA\[)?\s*(https?://[^<\]\s]+)", it) or re.search(r'<link[^>]+href="(https?://[^"]+)"', it)
        if m: links.append(html.unescape(m.group(1)))
    print(f"{name:20s} {len(links)} items")
    for i, l in enumerate(links[:2]):
        items.append({"source": name, "url": l, "file": f"{len(items):02d}.html"})
for it in items:
    r = subprocess.run(["curl","-sSL","--max-time","25","-A",UA,"-o",os.path.join(out,it["file"]),"-w","%{http_code} %{size_download}",it["url"]],capture_output=True,timeout=40)
    it["status"] = r.stdout.decode()
json.dump(items, open(os.path.join(out,"index.json"),"w"), indent=1)
for it in items: print(it["file"], it["source"], it["status"], it["url"][:90])
