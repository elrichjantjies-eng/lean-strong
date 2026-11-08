/*
 * SA Radio article extractor.
 *
 * The page's HTML is parsed into an inert document (no scripts run, nothing is downloaded), Mozilla
 * Readability picks out the story, and the result is flattened into simple blocks that the app
 * draws with its own views: paragraphs, headings, pictures, quotes, lists and embeds. Ads, pop-ups,
 * cookie banners and "related stories" never make it through.
 */
(function (global) {
  "use strict";

  // Paragraphs that are promotion or navigation rather than part of the story.
  var JUNK = new RegExp(
    "^\\s*(also read|read also|read more|read:|related( stories| articles| news)?:?|recommended|more on this|more from|" +
      "you may also like|you might also like|don.t miss|subscribe|sign up|sign-up|join our|follow us|follow @|" +
      "download (the|our)|get the latest|stay up to date|click here|advertisement|advertorial|sponsored|" +
      "share this|support (independent|our|quality)|become a (member|subscriber)|this (article|story) (was|is) (first )?" +
      "(published|republished)|for more news|send us your|whatsapp us|join the conversation|catch up on|" +
      "story audio is generated|listen to (this|the) (article|story)|audio version|ask ai|share your thoughts|" +
      "get breaking news|join (the|our) (whatsapp|telegram)|for the latest|let us know (what|your)|" +
      "\\[?(ad|advert)\\]?$|watch:?$|listen:?$|photo:?$|image:?$|picture:?$|tags?:|topics?:)",
    "i"
  );
  var BAD_IMG = /(logo|avatar|gravatar|icon|sprite|pixel|spacer|blank\.gif|placeholder|badge|emoji|\.svg(\?|$))/i;
  var EMBED_HOSTS = /(youtube\.com|youtu\.be|vimeo\.com|twitter\.com|x\.com|instagram\.com|facebook\.com|tiktok\.com|soundcloud\.com|spotify\.com|iono\.fm|omny\.fm)/i;

  function abs(u, base) {
    try {
      var r = new URL(u, base).href;
      return /^https?:/i.test(r) ? r : "";
    } catch (e) {
      return "";
    }
  }

  function clean(s) {
    return (s || "").replace(/\s+/g, " ").trim();
  }

  function esc(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  /** Best picture URL for a target width, looking through lazy-loading attributes and srcset. */
  function pickSrc(img, base, want) {
    var sets = [img.getAttribute("data-srcset"), img.getAttribute("srcset")];
    var parent = img.parentNode;
    if (parent && parent.tagName && parent.tagName.toLowerCase() === "picture") {
      var srcs = parent.querySelectorAll("source[srcset], source[data-srcset]");
      for (var i = 0; i < srcs.length; i++) {
        var t = (srcs[i].getAttribute("type") || "").toLowerCase();
        if (t && t.indexOf("image/") !== 0) continue;
        sets.push(srcs[i].getAttribute("data-srcset") || srcs[i].getAttribute("srcset"));
      }
    }
    var best = null;
    for (var k = 0; k < sets.length; k++) {
      if (!sets[k]) continue;
      sets[k].split(/,\s+(?=[^,]+\s+\d)/).forEach(function (part) {
        var bits = clean(part).split(/\s+/);
        if (!bits[0] || /^data:/i.test(bits[0])) return;
        var w = 0;
        var d = bits[1] || "";
        if (/w$/i.test(d)) w = parseInt(d, 10);
        else if (/x$/i.test(d)) w = Math.round(parseFloat(d) * 400);
        var u = abs(bits[0], base);
        if (!u) return;
        // Smallest picture at least as wide as wanted, else the widest available.
        if (!best || (w >= want && (best.w < want || w < best.w)) || (best.w < want && w > best.w)) best = { u: u, w: w };
      });
    }
    if (best && best.u) return best.u;
    var attrs = ["data-src", "data-lazy-src", "data-original", "data-lazy", "data-url", "data-full-url", "src"];
    for (var a = 0; a < attrs.length; a++) {
      var v = img.getAttribute(attrs[a]);
      if (v && !/^data:/i.test(v)) {
        var r = abs(v, base);
        if (r) return r;
      }
    }
    return "";
  }

  /** Inline HTML with only links, bold, italics and line breaks kept. */
  function inline(node, base) {
    var out = "";
    for (var c = node.firstChild; c; c = c.nextSibling) {
      if (c.nodeType === 3) {
        out += esc(c.nodeValue.replace(/\s+/g, " "));
        continue;
      }
      if (c.nodeType !== 1) continue;
      var tag = c.tagName.toLowerCase();
      var inner = inline(c, base);
      if (tag === "a") {
        var href = abs(c.getAttribute("href") || "", base);
        out += href && clean(inner) ? '<a href="' + href.replace(/"/g, "&quot;") + '">' + inner + "</a>" : inner;
      } else if (tag === "b" || tag === "strong") {
        out += clean(inner) ? "<b>" + inner + "</b>" : inner;
      } else if (tag === "i" || tag === "em" || tag === "cite") {
        out += clean(inner) ? "<i>" + inner + "</i>" : inner;
      } else if (tag === "br") {
        out += "<br>";
      } else if (tag === "sup" || tag === "sub") {
        out += "<" + tag + ">" + inner + "</" + tag + ">";
      } else if (tag !== "script" && tag !== "style" && tag !== "img" && tag !== "svg" && tag !== "button") {
        out += inner;
      }
    }
    return out;
  }

  function linkShare(node) {
    var all = clean(node.textContent).length;
    if (!all) return 0;
    var links = 0;
    node.querySelectorAll("a").forEach(function (a) {
      links += clean(a.textContent).length;
    });
    return links / all;
  }

  var BLOCK_TAGS = /^(p|div|section|article|main|header|footer|figure|h[1-6]|ul|ol|li|blockquote|table|pre|hr|iframe|video|picture|img|aside|details|dl|center|form)$/;

  function hasBlockChild(el) {
    for (var c = el.firstElementChild; c; c = c.nextElementSibling) {
      if (BLOCK_TAGS.test(c.tagName.toLowerCase())) return true;
    }
    return false;
  }

  function Walker(base, want) {
    this.base = base;
    this.want = want;
    this.blocks = [];
  }

  Walker.prototype.para = function (node) {
    var html = clean(inline(node, this.base)).replace(/^(<br>\s*)+|(\s*<br>)+$/g, "");
    var text = clean(node.textContent);
    if (!text) return;
    this.blocks.push({ t: "p", html: html, text: text, links: linkShare(node) });
  };

  Walker.prototype.image = function (img, caption) {
    var src = pickSrc(img, this.base, this.want);
    if (!src || BAD_IMG.test(src)) return;
    var w = parseInt(img.getAttribute("width") || "0", 10);
    var h = parseInt(img.getAttribute("height") || "0", 10);
    if ((w && w < 160) || (h && h < 100)) return;
    this.blocks.push({ t: "img", src: src, caption: clean(caption || ""), alt: clean(img.getAttribute("alt") || ""), w: w, h: h });
  };

  Walker.prototype.embed = function (url, label) {
    url = abs(url, this.base);
    if (!url || !EMBED_HOSTS.test(url)) return;
    this.blocks.push({ t: "embed", url: url, text: clean(label || "") });
  };

  Walker.prototype.walk = function (el) {
    var run = null; // a run of loose inline content inside a block container
    var self = this;
    function flush() {
      if (run && clean(run.textContent)) self.para(run);
      run = null;
    }
    for (var c = el.firstChild; c; c = c.nextSibling) {
      if (c.nodeType === 3) {
        if (!run) run = el.ownerDocument.createElement("span");
        run.appendChild(c.cloneNode(true));
        continue;
      }
      if (c.nodeType !== 1) continue;
      var tag = c.tagName.toLowerCase();
      if (!BLOCK_TAGS.test(tag)) {
        // Inline element; an inline element that only wraps a picture is a picture.
        var only = c.querySelector("img");
        if (only && !clean(c.textContent)) {
          flush();
          this.image(only, "");
        } else {
          if (!run) run = el.ownerDocument.createElement("span");
          run.appendChild(c.cloneNode(true));
        }
        continue;
      }
      flush();
      this.element(c, tag);
    }
    flush();
  };

  Walker.prototype.element = function (c, tag) {
    var i;
    if (/^h[1-6]$/.test(tag)) {
      var ht = clean(c.textContent);
      if (ht) this.blocks.push({ t: "h", text: ht });
    } else if (tag === "p") {
      var imgs = c.querySelectorAll("img");
      if (imgs.length && clean(c.textContent).length < 30) {
        for (i = 0; i < imgs.length; i++) this.image(imgs[i], "");
      } else {
        this.para(c);
      }
    } else if (tag === "figure" || tag === "picture") {
      var cap = c.querySelector("figcaption");
      var fi = c.querySelector("img");
      var fr = c.querySelector("iframe");
      if (fi) this.image(fi, cap ? cap.textContent : "");
      else if (fr) this.embed(fr.getAttribute("src") || fr.getAttribute("data-src"), cap ? cap.textContent : "");
      else if (clean(c.textContent)) this.walk(c);
    } else if (tag === "img") {
      this.image(c, "");
    } else if (tag === "blockquote") {
      var cls = (c.getAttribute("class") || "").toLowerCase();
      if (/twitter|tiktok|instagram/.test(cls)) {
        var links = c.querySelectorAll("a[href]");
        var last = links.length ? links[links.length - 1].getAttribute("href") : "";
        this.embed(last, c.textContent);
      } else if (clean(c.textContent)) {
        this.blocks.push({ t: "quote", html: clean(inline(c, this.base)), text: clean(c.textContent) });
      }
    } else if (tag === "ul" || tag === "ol") {
      var items = [];
      for (var li = c.firstElementChild; li; li = li.nextElementSibling) {
        if (li.tagName.toLowerCase() !== "li") continue;
        var lt = clean(li.textContent);
        if (lt) items.push({ html: clean(inline(li, this.base)), text: lt, links: linkShare(li) });
      }
      if (items.length) this.blocks.push({ t: "list", ordered: tag === "ol", items: items });
    } else if (tag === "iframe") {
      this.embed(c.getAttribute("src") || c.getAttribute("data-src"), "");
    } else if (tag === "video") {
      var vs = c.getAttribute("src") || (c.querySelector("source") || { getAttribute: function () { return ""; } }).getAttribute("src");
      if (vs) this.blocks.push({ t: "embed", url: abs(vs, this.base), text: "Video" });
    } else if (tag === "table") {
      var rows = c.querySelectorAll("tr");
      var lines = [];
      for (i = 0; i < rows.length && i < 40; i++) {
        var cells = [];
        rows[i].querySelectorAll("th,td").forEach(function (td) {
          var ct = clean(td.textContent);
          if (ct) cells.push(ct);
        });
        if (cells.length) lines.push({ html: esc(cells.join("  ·  ")), text: cells.join(" · "), links: 0 });
      }
      if (lines.length) this.blocks.push({ t: "list", ordered: false, items: lines, table: true });
    } else if (tag === "pre") {
      var pt = clean(c.textContent);
      if (pt) this.blocks.push({ t: "p", html: esc(pt), text: pt, links: 0 });
    } else if (tag === "hr" || tag === "form" || tag === "aside") {
      // nothing worth keeping
    } else if (tag === "li") {
      this.para(c);
    } else if (hasBlockChild(c)) {
      this.walk(c);
    } else if (clean(c.textContent)) {
      var dimg = c.querySelector("img");
      if (dimg && clean(c.textContent).length < 30) this.image(dimg, "");
      else this.para(c);
    } else {
      var only = c.querySelector("img");
      if (only) this.image(only, "");
      var ifr = c.querySelector("iframe");
      if (ifr) this.embed(ifr.getAttribute("src") || ifr.getAttribute("data-src"), "");
    }
  };

  function imgKey(u) {
    // Same picture at different sizes: drop query strings and WordPress-style size suffixes.
    return (u || "")
      .split("?")[0]
      .replace(/-\d{2,4}x\d{2,4}(?=\.\w+$)/, "")
      .replace(/\/(resizer|thumbor)\/.*?\/(?=[^/]+$)/, "/")
      .toLowerCase()
      .split("/")
      .pop();
  }

  function tidy(blocks, title, excerpt) {
    var out = [];
    var seenImg = {};
    var seenText = {};
    var nt = clean(title).toLowerCase();
    blocks.forEach(function (b) {
      if (b.t === "p") {
        var t = b.text;
        if (t.length < 2) return;
        if (JUNK.test(t) && t.length < 200) return;
        if (b.links > 0.85 && t.length < 300 && !/[.!?"”’)]$/.test(t)) return; // a bare "related story" link
        if (t.toLowerCase() === nt) return;
        if (/^(by|words by|photo:|image:|picture:|file photo|supplied)\b/i.test(t) && t.length < 60) return;
        var key = t.toLowerCase().slice(0, 120);
        if (seenText[key]) return;
        seenText[key] = 1;
      } else if (b.t === "h") {
        if (b.text.toLowerCase() === nt || JUNK.test(b.text)) return;
      } else if (b.t === "img") {
        var k = imgKey(b.src);
        if (seenImg[k]) return;
        seenImg[k] = 1;
      } else if (b.t === "list") {
        b.items = b.items.filter(function (it) {
          return !(JUNK.test(it.text) && it.text.length < 200) && !(it.links > 0.85 && it.text.length < 200 && !/[.!?"”’)]$/.test(it.text));
        });
        if (!b.items.length) return;
      }
      out.push(b);
    });
    // Headings left dangling at the end ("Related", "Most read") go too.
    while (out.length && (out[out.length - 1].t === "h" || out[out.length - 1].t === "img")) out.pop();
    return out;
  }

  function meta(doc, names) {
    for (var i = 0; i < names.length; i++) {
      var m = doc.querySelector('meta[property="' + names[i] + '"], meta[name="' + names[i] + '"]');
      if (m && m.getAttribute("content")) return clean(m.getAttribute("content"));
    }
    return "";
  }

  /**
   * @param html the page's HTML
   * @param url the page's address (for relative links and pictures)
   * @param want preferred picture width in pixels
   */
  function extract(html, url, want) {
    var res = { ok: false };
    try {
      var doc = new DOMParser().parseFromString(html, "text/html");
      var base = doc.createElement("base");
      base.setAttribute("href", url);
      if (doc.head) doc.head.insertBefore(base, doc.head.firstChild);

      var leadSrc = abs(meta(doc, ["og:image", "twitter:image", "twitter:image:src"]), url);
      var site = meta(doc, ["og:site_name", "application-name"]);
      var published = meta(doc, ["article:published_time", "og:article:published_time", "date", "pubdate", "sailthru.date"]);
      var paywalled = /"isAccessibleForFree"\s*:\s*"?(false)/i.test(html);

      // Pre-clean things Readability sometimes keeps.
      doc.querySelectorAll(
        "script,style,noscript,iframe[src*='doubleclick'],ins.adsbygoogle,[id^='div-gpt-ad'],[class*='taboola'],[id*='taboola']," +
          "[class*='outbrain'],[class*='OUTBRAIN'],[class*='newsletter'],[class*='related-'],[class*='-related'],[class*='share-'],[class*='social-'],[aria-hidden='true']"
      ).forEach(function (n) {
        if (n.parentNode) n.parentNode.removeChild(n);
      });

      var art = new Readability(doc, { charThreshold: 400, keepClasses: true }).parse();
      if (!art || !art.content) {
        res.reason = "no-article";
        res.paywalled = paywalled;
        return JSON.stringify(res);
      }
      var holder = new DOMParser().parseFromString("<div id='sar'>" + art.content + "</div>", "text/html");
      var hb = holder.createElement("base");
      hb.setAttribute("href", url);
      holder.head.appendChild(hb);
      var w = new Walker(url, want || 900);
      w.walk(holder.getElementById("sar"));
      var blocks = tidy(w.blocks, art.title || "", art.excerpt || "");

      // Use the first picture as the lead when it comes early; otherwise the share picture.
      var lead = null;
      for (var i = 0; i < Math.min(3, blocks.length); i++) {
        if (blocks[i].t === "img") {
          lead = { src: blocks[i].src, caption: blocks[i].caption };
          blocks.splice(i, 1);
          break;
        }
      }
      if (!lead && leadSrc && !BAD_IMG.test(leadSrc)) lead = { src: leadSrc, caption: "" };
      if (lead) {
        var lk = imgKey(lead.src);
        blocks = blocks.filter(function (b) {
          return b.t !== "img" || imgKey(b.src) !== lk;
        });
      }

      var words = 0;
      blocks.forEach(function (b) {
        var t = b.t === "list" ? b.items.map(function (x) { return x.text; }).join(" ") : b.text || "";
        words += t ? t.split(/\s+/).length : 0;
        delete b.links;
        if (b.items) b.items.forEach(function (x) { delete x.links; });
      });

      res.ok = words >= 60;
      if (!res.ok) res.reason = "too-short";
      res.title = clean(art.title);
      res.byline = clean(art.byline)
        .replace(/^by\s+/i, "")
        .replace(/([a-z])(Political|Senior|Chief|Deputy|Assistant|Correspondent|Reporter|Journalist|Editor|Writer|Staff|News|Agency|Contributor|Photographer)\b/g, "$1 $2");
      res.site = clean(art.siteName || site);
      res.excerpt = clean(art.excerpt);
      res.published = clean(art.publishedTime || published);
      res.lead = lead;
      res.blocks = blocks;
      res.words = words;
      res.paywalled = paywalled;
    } catch (e) {
      res.reason = "error: " + (e && e.message);
    }
    return JSON.stringify(res);
  }

  global.SAR = { extract: extract };
})(this);
