import json, subprocess, sys
UA = "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"
res = {r["file"]: r for r in json.load(open(sys.argv[1]))}
def size(url, compressed=False):
    args = ["curl","-sSL","--max-time","30","-A",UA,"-o","/dev/null","-w","%{size_download}"] + (["--compressed"] if compressed else []) + [url]
    try: return int(subprocess.run(args,capture_output=True,timeout=40).stdout.decode() or 0)
    except Exception: return 0
tot_full = tot_reader = tot_text = 0
print(f"{'publisher':18s} {'full page':>10s} {'requests':>8s} {'ads/trk':>8s} | {'reader':>8s} {'text only':>9s}")
for line in open(sys.argv[2]):
    w = json.loads(line); r = res[w["file"]]
    html = size(r["url"], compressed=True)
    imgs = sum(size(u) for u in w["images"])
    reader = html + imgs
    tot_full += w["fullKB"]; tot_reader += reader/1024; tot_text += html/1024
    print(f"{w['source']:18s} {w['fullKB']:>8d}KB {w['requests']:>8d} {w['adTrackerKB']:>6d}KB | {reader/1024:>6.0f}KB {html/1024:>7.0f}KB")
print(f"{'TOTAL':18s} {tot_full:>8.0f}KB {'':>8s} {'':>8s} | {tot_reader:>6.0f}KB {tot_text:>7.0f}KB")
print(f"reader uses {100*tot_reader/tot_full:.0f}% of the full page's data; text-only (Saver) {100*tot_text/tot_full:.0f}%")
