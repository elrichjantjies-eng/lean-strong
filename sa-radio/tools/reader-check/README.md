# Reader check

Checks the article reader (`app/src/main/assets/reader/extract.js`) against live stories from every
feed, in Chromium, exactly as the app runs it. Re-run it when a publisher changes their site.

Needs Python 3, Node and Playwright with Chromium.

```sh
OUT=$(mktemp -d)                      # downloaded pages are untrusted; keep them out of the repo
grep '^        {"' ../../app/src/main/java/za/saradio/app/NewsRepo.java > "$OUT/feeds.txt"
python3 -I collect.py "$OUT" "$OUT/feeds.txt"            # two stories per feed
node run_extract.js "$OUT" ../../app/src/main/assets/reader/readability.js ../../app/src/main/assets/reader/extract.js "$OUT/results.json"
```

`run_extract.js` prints, per story: whether it extracted, word/picture/quote counts, the first and
last paragraph, and any short paragraph that looks like promotion (`??`). Optional:
`node weigh.js results.json > weights.jsonl` then `python3 -I reader_weight.py results.json weights.jsonl`
compares the full page's data use with the reader's.

