package za.saradio.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A light article-text extractor for the story widget, in plain Java. The app's reader uses
 * Mozilla Readability in a WebView, which a widget can't run; this follows Readability's main idea
 * (the element holding the most real paragraphs is the story) on a simple tag scanner, and returns
 * just the words: no pictures, ads or links.
 */
final class PlainText {
    private PlainText() {}

    static final class Block {
        final String text;
        final boolean heading;

        Block(String text, boolean heading) {
            this.text = text;
            this.heading = heading;
        }
    }

    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9-]*)([^>]*)>");
    private static final Pattern DROP = Pattern.compile(
            "(?is)<(script|style|noscript|svg|template|iframe|form|select|button|figure|figcaption|nav|footer|aside)\\b.*?</\\1\\s*>"
                    + "|<!--.*?-->");
    private static final Pattern CLASS_ID = Pattern.compile("(?i)\\b(?:class|id)\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern UNLIKELY = Pattern.compile(
            "(?i)comment|related|recommend|newsletter|subscribe|share|social|promo|sidebar|footer|header|menu|"
                    + "breadcrumb|cookie|consent|advert|sponsor|outbrain|taboola|popup|modal|signup|login|more-stories|trending");
    private static final Pattern LIKELY = Pattern.compile("(?i)article|story|body|content|entry|post|main|text");
    private static final Pattern BOILER = Pattern.compile(
            "(?i)^(advertisement|read more|read also|also read|related|subscribe|sign up|follow us|share this|"
                    + "click here|download the|join our|get the latest|support independent|we have a favour|"
                    + "you might also|recommended|sponsored|loading|image:|photo:|picture:|file photo|©).*");
    private static final String[] VOID = {"br", "img", "hr", "input", "meta", "link", "source", "wbr", "area", "col", "embed", "param", "track"};

    private static final class El {
        final String name;
        final int parent;
        final String attrs;
        double score;
        int depth;

        El(String name, int parent, String attrs, int depth) {
            this.name = name;
            this.parent = parent;
            this.attrs = attrs;
            this.depth = depth;
        }
    }

    /** A paragraph-like element found in the page. */
    private static final class Para {
        final String tag;
        final int el;
        final String text;
        final int linkChars;

        Para(String tag, int el, String text, int linkChars) {
            this.tag = tag;
            this.el = el;
            this.text = text;
            this.linkChars = linkChars;
        }
    }

    /** The story's paragraphs (and sub-headings), or an empty list if none were found. */
    static List<Block> extract(String html) {
        List<Block> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return out;
        String doc = DROP.matcher(html).replaceAll(" ");
        int bodyAt = indexOfIgnoreCase(doc, "<body");
        if (bodyAt > 0) doc = doc.substring(bodyAt);

        // Scan the tags, building the element tree as we go, and collect paragraph-like elements.
        List<El> els = new ArrayList<>();
        els.add(new El("#root", -1, "", 0));
        List<Integer> stack = new ArrayList<>();
        stack.add(0);
        List<Para> paras = new ArrayList<>();
        Matcher m = TAG.matcher(doc);
        int textStart = -1;
        String textTag = null;
        int textEl = -1;
        int skipDepth = 0;
        while (m.find()) {
            boolean close = !m.group(1).isEmpty();
            String name = m.group(2).toLowerCase(Locale.ROOT);
            String attrs = m.group(3);
            if (isVoid(name)) continue;
            if (!close && closesP(name)) {
                // As in a browser, a new block closes an open paragraph ("<p><p>" is two paragraphs).
                int at = -1;
                for (int i = stack.size() - 1; i > 0; i--) {
                    String n = els.get(stack.get(i)).name;
                    if (n.equals("p")) {
                        at = i;
                        break;
                    }
                    if (!isInline(n)) break;
                }
                if (at > 0) {
                    if (textTag != null && at < skipDepth) {
                        collect(paras, textTag, textEl, doc.substring(textStart, m.start()));
                        textTag = null;
                    }
                    while (stack.size() > at) stack.remove(stack.size() - 1);
                }
            }
            if (!close) {
                int parent = stack.get(stack.size() - 1);
                El e = new El(name, parent, attrs, stack.size());
                els.add(e);
                int id = els.size() - 1;
                if (attrs.endsWith("/")) continue;
                stack.add(id);
                if (textTag == null && isPara(name)) {
                    textTag = name;
                    textStart = m.end();
                    textEl = id;
                    skipDepth = stack.size();
                }
            } else {
                // Pop back to the matching open tag, if there is one.
                int at = -1;
                for (int i = stack.size() - 1; i > 0; i--) {
                    if (els.get(stack.get(i)).name.equals(name)) {
                        at = i;
                        break;
                    }
                }
                if (at < 0) continue;
                if (textTag != null && at < skipDepth) {
                    // A paragraph's closing tag (or an outer one that closes it).
                    collect(paras, textTag, textEl, doc.substring(textStart, m.start()));
                    textTag = null;
                }
                while (stack.size() > at) stack.remove(stack.size() - 1);
            }
        }

        // Score: each real paragraph credits its parent, and half as much its grandparent.
        double best = 0;
        int top = -1;
        for (Para p : paras) {
            if (!"p".equals(p.tag) || p.text.length() < 25) continue;
            El holder = els.get(p.el);
            int parent = holder.parent;
            if (parent < 0) continue;
            double s = 1 + count(p.text, ',') + Math.min(3, p.text.length() / 100.0);
            if (p.linkChars * 2 > p.text.length()) s *= 0.2;
            credit(els, parent, s);
            int grand = els.get(parent).parent;
            if (grand >= 0) credit(els, grand, s / 2);
        }
        for (int i = 1; i < els.size(); i++) {
            El e = els.get(i);
            if (e.score <= 0) continue;
            double s = e.score;
            String ci = classId(e.attrs);
            if (UNLIKELY.matcher(ci).find() && !LIKELY.matcher(ci).find()) s *= 0.3;
            if (s > best) {
                best = s;
                top = i;
            }
        }
        if (top < 0) return out;

        // Keep the paragraphs inside the winner.
        for (Para p : paras) {
            if (!within(els, p.el, top)) continue;
            String t = p.text;
            boolean heading = p.tag.startsWith("h");
            if (t.length() < (heading ? 3 : 20) && !t.endsWith(".")) continue;
            if (BOILER.matcher(t).matches() && t.length() < 160) continue;
            // Mostly a link and not a sentence: a "read more" or a list of stories.
            if (p.linkChars > t.length() * 0.85 && !t.matches(".*[.!?\"”’]$")) continue;
            if ("li".equals(p.tag) && (t.length() < 60 || p.linkChars * 2 > t.length())) continue;
            if (!out.isEmpty() && out.get(out.size() - 1).text.equals(t)) continue;
            out.add(new Block(t, heading));
        }
        // Drop trailing headings with nothing under them ("Related", "More on this").
        while (!out.isEmpty() && out.get(out.size() - 1).heading) out.remove(out.size() - 1);
        return out;
    }

    private static void collect(List<Para> paras, String tag, int el, String inner) {
        int linkChars = 0;
        Matcher a = Pattern.compile("(?is)<a\\b[^>]*>(.*?)</a>").matcher(inner);
        while (a.find()) linkChars += clean(a.group(1)).length();
        String t = clean(inner);
        if (!t.isEmpty()) paras.add(new Para(tag, el, t, linkChars));
    }

    private static void credit(List<El> els, int i, double s) {
        els.get(i).score += s;
    }

    private static boolean within(List<El> els, int el, int top) {
        for (int cur = el; cur >= 0; cur = els.get(cur).parent) if (cur == top) return true;
        return false;
    }

    private static boolean isPara(String name) {
        return name.equals("p") || name.equals("h2") || name.equals("h3") || name.equals("blockquote") || name.equals("li");
    }

    private static final String[] BLOCKS = {"p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote",
        "table", "section", "article", "header", "pre", "dl", "hr"};
    private static final String[] INLINE = {"a", "b", "i", "em", "strong", "span", "u", "small", "sup", "sub", "mark", "cite",
        "abbr", "time", "q", "s", "font"};

    private static boolean closesP(String name) {
        for (String b : BLOCKS) if (b.equals(name)) return true;
        return false;
    }

    private static boolean isInline(String name) {
        for (String b : INLINE) if (b.equals(name)) return true;
        return false;
    }

    private static boolean isVoid(String name) {
        for (String v : VOID) if (v.equals(name)) return true;
        return false;
    }

    private static String classId(String attrs) {
        StringBuilder sb = new StringBuilder();
        Matcher m = CLASS_ID.matcher(attrs);
        while (m.find()) sb.append(m.group(1)).append(' ');
        return sb.toString();
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static int indexOfIgnoreCase(String s, String what) {
        return s.toLowerCase(Locale.ROOT).indexOf(what);
    }

    /** Strips tags, decodes entities and tidies the spaces. */
    static String clean(String html) {
        String t = html.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", " ");
        t = decode(t);
        return t.replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i && semi - i <= 10) {
                    String ent = s.substring(i + 1, semi);
                    String rep = entity(ent);
                    if (rep != null) {
                        sb.append(rep);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String entity(String e) {
        try {
            if (e.startsWith("#x") || e.startsWith("#X")) return new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
            if (e.startsWith("#")) return new String(Character.toChars(Integer.parseInt(e.substring(1))));
        } catch (Exception ex) {
            return null;
        }
        switch (e) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
            case "rsquo": return "’";
            case "lsquo": return "‘";
            case "rdquo": return "”";
            case "ldquo": return "“";
            case "ndash": return "–";
            case "mdash": return "—";
            case "hellip": return "…";
            case "eacute": return "é";
            case "egrave": return "è";
            case "ecirc": return "ê";
            case "euml": return "ë";
            case "ocirc": return "ô";
            case "auml": return "ä";
            case "ouml": return "ö";
            case "uuml": return "ü";
            default: return null;
        }
    }
}
