package za.saradio.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** A story drawn with the app's own views: no ads, pop-ups or cookie banners, and readable type. */
final class ArticleView extends FrameLayout {

    interface Host {
        void openOriginal();

        void openExternal(String url);

        void shareArticle();
    }

    static final float[] SIZES = {15.5f, 17f, 19f, 21.5f};
    static final String[] SIZE_NAMES = {"Small", "Medium", "Large", "Extra large"};
    private static final int BODY = 0xFF1E1E1E;

    private final MainActivity a;
    private final Host host;
    private final ScrollView scroll;
    private final LinearLayout col;
    private ObjectAnimator pulse;
    private NewsRepo.Item item;
    private Article article;

    ArticleView(MainActivity act, Host host) {
        super(act);
        a = act;
        this.host = host;
        scroll = new ScrollView(act);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        col = Ui.column(act);
        col.setPadding(dp(22), dp(8), dp(22), dp(24));
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    void setBottomPadding(int px) {
        scroll.setPadding(0, 0, 0, px);
    }

    static int textSize(Context c) {
        return Math.max(0, Math.min(SIZES.length - 1, Prefs.get(c).getInt("readerSize", 1)));
    }

    // ---- States -----------------------------------------------------------------------------

    void loading(NewsRepo.Item it) {
        item = it;
        article = null;
        col.removeAllViews();
        scroll.scrollTo(0, 0);
        header(it, null);
        LinearLayout sk = Ui.column(a);
        View pic = new View(a);
        pic.setBackground(Ui.round(Ui.SURFACE3, dp(10)));
        sk.addView(pic, Ui.lp(-1, dp(190), 0, dp(18), 0, dp(18)));
        int[] widths = {100, 96, 92, 98, 60, 0, 100, 94, 97, 40};
        for (int w : widths) {
            if (w == 0) {
                sk.addView(new View(a), Ui.lp(-1, dp(14)));
                continue;
            }
            View bar = new View(a);
            bar.setBackground(Ui.round(Ui.SURFACE3, dp(4)));
            int full = getResources().getDisplayMetrics().widthPixels - dp(44);
            sk.addView(bar, Ui.lp(full * w / 100, dp(13), 0, 0, 0, dp(11)));
        }
        col.addView(sk);
        if (pulse != null) pulse.cancel();
        pulse = ObjectAnimator.ofFloat(sk, View.ALPHA, 1f, 0.45f);
        pulse.setDuration(700);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
        setContentDescription("Loading the story");
    }

    void show(NewsRepo.Item it, Article art) {
        stopPulse();
        item = it;
        article = art;
        render(0);
    }

    /** Re-draws at a new text size, keeping roughly the same place in the story. */
    void restyle() {
        if (article == null || !article.ok) return;
        float at = col.getHeight() > 0 ? scroll.getScrollY() / (float) col.getHeight() : 0;
        render(at);
    }

    private void render(final float scrollFraction) {
        Article art = article;
        col.removeAllViews();
        header(item, art);
        if (art.excerpt.length() > 40 && !art.excerpt.endsWith("…") && !art.excerpt.endsWith("...") && !startsBody(art, art.excerpt)) {
            TextView st = Ui.text(a, art.excerpt, SIZES[textSize(a)] + 1, Ui.TEXT2, Ui.W_MEDIUM);
            st.setLineSpacing(0, 1.35f);
            col.addView(st, Ui.lp(-1, -2, 0, dp(12), 0, 0));
        }
        if (!art.leadSrc.isEmpty()) picture(art.leadSrc, art.leadCaption, 0, 0, true);
        for (Article.Block b : art.blocks) block(b);
        if (art.teaserOnly()) paywall(art);
        footer(art);
        if (scrollFraction > 0) scroll.post(() -> scroll.scrollTo(0, (int) (col.getHeight() * scrollFraction)));
        else scroll.scrollTo(0, 0);
        setContentDescription(null);
    }

    void failed(NewsRepo.Item it, Article art) {
        stopPulse();
        item = it;
        article = art;
        col.removeAllViews();
        header(it, null);
        boolean net = art.reason.startsWith("network");
        boolean pay = art.paywalled;
        LinearLayout card = card();
        ImageView ic = new ImageView(a);
        ic.setImageResource(pay ? R.drawable.ic_lock : net ? R.drawable.ic_wifi : R.drawable.ic_nav_news);
        ic.setColorFilter(Ui.ACCENT);
        card.addView(ic, Ui.lp(dp(26), dp(26)));
        String title = pay ? "For subscribers" : net ? "Couldn't reach " + it.source : "No clean version of this one";
        String body = pay
                ? it.source + " keeps this story for subscribers. Open it on their site to sign in or subscribe."
                : net
                        ? "Check your connection and try again, or open the story in your browser."
                        : "This page isn't laid out like a normal article; it may be a video, live blog or photo gallery. "
                                + "You can still open the original page, with ads blocked.";
        TextView t = Ui.text(a, title, 18, Ui.TEXT, Ui.W_HEAVY);
        card.addView(t, Ui.lp(-1, -2, 0, dp(12), 0, 0));
        TextView bt = Ui.text(a, body, 14.5f, Ui.TEXT2, Ui.W_MEDIUM);
        bt.setLineSpacing(0, 1.35f);
        card.addView(bt, Ui.lp(-1, -2, 0, dp(6), 0, dp(14)));
        if (!pay && !net) {
            TextView orig = Ui.primaryButton(a, "Show original page");
            orig.setOnClickListener(v -> host.openOriginal());
            card.addView(orig, Ui.lp(-1, -2, 0, 0, 0, dp(8)));
        }
        TextView ext = pay ? Ui.primaryButton(a, "Continue on " + it.source) : Ui.secondaryButton(a, "Open in browser");
        ext.setOnClickListener(v -> host.openExternal(it.link));
        card.addView(ext, Ui.lp(-1, -2));
        col.addView(card, Ui.lp(-1, -2, 0, dp(22), 0, 0));
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
    }

    // ---- Pieces -----------------------------------------------------------------------------

    private void header(NewsRepo.Item it, Article art) {
        LinearLayout meta = Ui.row(a);
        TextView src = Ui.text(a, it.source.toUpperCase(Locale.ROOT), 12, Ui.ACCENT, Ui.W_HEAVY);
        src.setLetterSpacing(0.1f);
        meta.addView(src);
        String when = NewsRepo.ago(it.time);
        StringBuilder rest = new StringBuilder();
        if (!when.isEmpty()) rest.append("  ·  ").append(when);
        if (art != null && art.ok) rest.append("  ·  ").append(art.minutes()).append(" min read");
        TextView r = Ui.text(a, rest, 12.5f, Ui.MUTED, Ui.W_BOLD);
        meta.addView(r);
        col.addView(meta, Ui.lp(-1, -2, 0, dp(6), 0, 0));

        String title = it.title.isEmpty() && art != null ? art.title : it.title;
        TextView t = Ui.text(a, title, SIZES[textSize(a)] + 8, Ui.TEXT, Ui.W_HEAVY);
        t.setLineSpacing(0, 1.12f);
        Ui.heading(t);
        col.addView(t, Ui.lp(-1, -2, 0, dp(10), 0, 0));

        if (art != null && !art.byline.isEmpty() && !art.byline.equalsIgnoreCase(it.source)) {
            TextView by = Ui.text(a, "By " + art.byline, 13.5f, Ui.MUTED, Ui.W_BOLD);
            col.addView(by, Ui.lp(-1, -2, 0, dp(12), 0, 0));
        }
    }

    /** True when the intro just repeats the first paragraph (often word for word, sometimes reworded). */
    static boolean startsBody(Article art, String s) {
        for (Article.Block b : art.blocks) {
            if (!"p".equals(b.type)) continue;
            java.util.Set<String> a = words(s), p = words(b.text);
            if (a.isEmpty()) return false;
            int same = 0;
            for (String w : a) if (p.contains(w)) same++;
            return same >= a.size() * 0.6f;
        }
        return false;
    }

    private static java.util.Set<String> words(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        String[] all = s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        for (int i = 0; i < all.length && out.size() < 25; i++) if (all[i].length() > 2) out.add(all[i]);
        return out;
    }

    private void block(Article.Block b) {
        float size = SIZES[textSize(a)];
        switch (b.type) {
            case "h": {
                TextView h = Ui.text(a, b.text, size + 2.5f, Ui.TEXT, Ui.W_HEAVY);
                h.setLineSpacing(0, 1.15f);
                Ui.heading(h);
                col.addView(h, Ui.lp(-1, -2, 0, dp(20), 0, dp(2)));
                break;
            }
            case "quote": {
                LinearLayout row = Ui.row(a);
                row.setGravity(Gravity.TOP);
                View bar = new View(a);
                bar.setBackground(Ui.round(Ui.ACCENT, dp(2)));
                row.addView(bar, new LinearLayout.LayoutParams(dp(3), -1));
                TextView q = body(b.html, size + 1);
                q.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
                q.setTextColor(Ui.TEXT2);
                q.setPadding(dp(14), 0, 0, 0);
                row.addView(q, Ui.weight(1));
                col.addView(row, Ui.lp(-1, -2, 0, dp(16), 0, dp(2)));
                break;
            }
            case "list": {
                if (b.table) {
                    table(b);
                    break;
                }
                LinearLayout list = Ui.column(a);
                int n = 1;
                for (String it : b.items) {
                    LinearLayout row = Ui.row(a);
                    row.setGravity(Gravity.TOP);
                    TextView mark = Ui.text(a, b.ordered ? (n++) + "." : "•", size, Ui.ACCENT, Ui.W_HEAVY);
                    mark.setGravity(Gravity.END);
                    row.addView(mark, Ui.lp(dp(22), -2));
                    TextView t = body(it, size);
                    t.setPadding(dp(10), 0, 0, 0);
                    row.addView(t, Ui.weight(1));
                    list.addView(row, Ui.lp(-1, -2, 0, dp(6), 0, 0));
                }
                col.addView(list, Ui.lp(-1, -2, 0, dp(8), 0, 0));
                break;
            }
            case "img":
                picture(b.src, b.caption, b.width, b.height, false);
                break;
            case "embed":
                embed(b);
                break;
            default: {
                TextView p = body(b.html, size);
                col.addView(p, Ui.lp(-1, -2, 0, dp(16), 0, 0));
            }
        }
    }

    /** Table rows, header row first, in a card. */
    private void table(Article.Block b) {
        LinearLayout k = Ui.column(a);
        k.setBackground(Ui.roundStroke(Ui.SURFACE, dp(16), Ui.STROKE2, dp(1)));
        k.setPadding(dp(14), dp(6), dp(14), dp(6));
        for (int i = 0; i < b.items.size(); i++) {
            if (i > 0) {
                View rule = new View(a);
                rule.setBackgroundColor(Ui.STROKE);
                k.addView(rule, Ui.lp(-1, dp(1)));
            }
            String row = Html.fromHtml(b.items.get(i), Html.FROM_HTML_MODE_LEGACY).toString().trim();
            TextView t = Ui.text(a, row, i == 0 ? 13 : 14.5f, i == 0 ? Ui.MUTED : Ui.TEXT2, i == 0 ? Ui.W_HEAVY : Ui.W_MEDIUM);
            t.setPadding(0, dp(8), 0, dp(8));
            k.addView(t);
        }
        col.addView(k, Ui.lp(-1, -2, 0, dp(16), 0, 0));
    }

    private TextView body(String html, float size) {
        TextView t = new TextView(a);
        t.setText(trim(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)));
        t.setTextSize(size);
        t.setTypeface(Typeface.SERIF);
        t.setTextColor(BODY);
        t.setLineSpacing(0, 1.5f);
        t.setLinkTextColor(Ui.ACCENT);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        return t;
    }

    private static CharSequence trim(Spanned s) {
        SpannableStringBuilder b = new SpannableStringBuilder(s);
        while (b.length() > 0 && Character.isWhitespace(b.charAt(b.length() - 1))) b.delete(b.length() - 1, b.length());
        while (b.length() > 0 && Character.isWhitespace(b.charAt(0))) b.delete(0, 1);
        return b;
    }

    private void picture(final String src, String caption, int w, int h, boolean lead) {
        final int eff = DataMode.effective(a);
        final int width = getResources().getDisplayMetrics().widthPixels - dp(44);
        float aspect = w > 0 && h > 0 ? h / (float) w : 0.5625f;
        aspect = Math.max(0.45f, Math.min(1.25f, aspect));
        final FrameLayout box = new FrameLayout(a);
        box.setBackground(Ui.round(Ui.SURFACE3, dp(10)));
        box.setForeground(Ui.roundStroke(0, dp(10), Ui.INK, Ui.line()));
        Ui.roundClip(box, dp(10));
        final ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setContentDescription(caption.isEmpty() ? "Picture" : caption);
        box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        final int px = eff == DataMode.BEST ? 1080 : eff == DataMode.SAVER ? 540 : 800;
        final Runnable load = () -> ImageLoader.get(src, px, new ImageLoader.Target() {
            @Override
            public boolean wants(String url) {
                return true;
            }

            @Override
            public void onBitmap(String url, Bitmap bm) {
                iv.setImageBitmap(bm);
                // Use the picture's real shape once it's known.
                float r = Math.max(0.45f, Math.min(1.25f, bm.getHeight() / (float) Math.max(1, bm.getWidth())));
                box.getLayoutParams().height = Math.round(width * r);
                box.requestLayout();
                iv.setAlpha(0f);
                iv.animate().alpha(1f).setDuration(200).start();
            }
        });
        if (eff == DataMode.SAVER) {
            // Data saver: pictures load only when asked for.
            LinearLayout tap = Ui.column(a);
            tap.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(a);
            ic.setImageResource(R.drawable.ic_image);
            ic.setColorFilter(Ui.MUTED);
            tap.addView(ic, Ui.lp(dp(28), dp(28)));
            TextView tt = Ui.text(a, "Tap to load picture", 13, Ui.MUTED, Ui.W_BOLD);
            tt.setGravity(Gravity.CENTER);
            tap.addView(tt, Ui.lp(-2, -2, 0, dp(6), 0, 0));
            box.addView(tap, new FrameLayout.LayoutParams(-1, -1));
            box.setOnClickListener(v -> {
                box.removeView(tap);
                box.setOnClickListener(null);
                load.run();
            });
            box.setContentDescription("Picture, tap to load");
        } else {
            load.run();
        }
        col.addView(box, Ui.lp(-1, Math.round(width * aspect), 0, lead ? dp(18) : dp(20), 0, 0));
        if (!caption.isEmpty()) {
            TextView c = Ui.text(a, caption, 12.5f, Ui.MUTED, Ui.W_MEDIUM);
            c.setLineSpacing(0, 1.3f);
            col.addView(c, Ui.lp(-1, -2, dp(2), dp(8), dp(2), 0));
        }
    }

    private void embed(final Article.Block b) {
        String host = "";
        try {
            host = Uri.parse(b.url).getHost();
        } catch (Exception ignored) {
        }
        host = host == null ? "" : host.replaceFirst("^www\\.", "");
        boolean video = host.contains("youtu") || host.contains("vimeo") || host.contains("tiktok") || "Video".equals(b.text);
        String label = host.contains("youtu") ? "Watch on YouTube" : host.equals("x.com") || host.contains("twitter")
                ? "View post on X" : host.contains("instagram") ? "View on Instagram" : host.contains("facebook")
                ? "View on Facebook" : video ? "Watch the video" : "Open " + host;
        LinearLayout row = Ui.row(a);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        float r = dp(18);
        row.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE2, r, Ui.STROKE2, dp(1)), r));
        ImageView ic = new ImageView(a);
        ic.setImageResource(video ? R.drawable.ic_play : R.drawable.ic_open);
        ic.setColorFilter(Ui.ON_ACCENT);
        int p = dp(8);
        ic.setPadding(p, p, p, p);
        ic.setBackground(Ui.round(Ui.ACCENT, dp(20)));
        row.addView(ic, Ui.lp(dp(40), dp(40)));
        LinearLayout txt = Ui.column(a);
        txt.setPadding(dp(12), 0, 0, 0);
        txt.addView(Ui.text(a, label, 15, Ui.TEXT, Ui.W_BOLD));
        String snippet = b.text.length() > 4 && !"Video".equals(b.text) ? b.text : host;
        TextView s = Ui.text(a, snippet, 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        s.setMaxLines(2);
        s.setEllipsize(TextUtils.TruncateAt.END);
        s.setPadding(0, dp(3), 0, 0);
        txt.addView(s);
        row.addView(txt, Ui.weight(1));
        row.setOnClickListener(v -> host(b.url));
        row.setContentDescription(label);
        Ui.press(row, 0.98f);
        col.addView(row, Ui.lp(-1, -2, 0, dp(18), 0, 0));
    }

    private void host(String url) {
        host.openExternal(url);
    }

    private LinearLayout card() {
        LinearLayout k = Ui.column(a);
        k.setPadding(dp(18), dp(18), dp(18), dp(18));
        k.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(22), Ui.STROKE2, dp(1)));
        return k;
    }

    private void paywall(Article art) {
        LinearLayout k = card();
        k.setBackground(Ui.roundStroke(Ui.alpha(Ui.ACCENT, 0.1f), dp(22), Ui.alpha(Ui.ACCENT, 0.5f), dp(1)));
        LinearLayout top = Ui.row(a);
        ImageView ic = new ImageView(a);
        ic.setImageResource(R.drawable.ic_lock);
        ic.setColorFilter(Ui.ACCENT);
        top.addView(ic, Ui.lp(dp(20), dp(20), 0, 0, dp(10), 0));
        top.addView(Ui.text(a, "The rest is for subscribers", 16, Ui.TEXT, Ui.W_HEAVY));
        k.addView(top);
        TextView t = Ui.text(a, item.source + " only shows the start of this story to non-subscribers. "
                + "Sign in or subscribe on their site to read it all.", 14, Ui.TEXT2, Ui.W_MEDIUM);
        t.setLineSpacing(0, 1.35f);
        k.addView(t, Ui.lp(-1, -2, 0, dp(8), 0, dp(14)));
        TextView go = Ui.primaryButton(a, "Continue on " + item.source);
        go.setOnClickListener(v -> host.openExternal(item.link));
        k.addView(go, Ui.lp(-1, -2));
        col.addView(k, Ui.lp(-1, -2, 0, dp(24), 0, 0));
    }

    private void footer(Article art) {
        View rule = new View(a);
        rule.setBackgroundColor(Ui.STROKE2);
        col.addView(rule, Ui.lp(-1, dp(1), 0, dp(28), 0, dp(18)));
        TextView note = Ui.text(a, "Ad-free reader view. This story belongs to " + item.source
                + "; open the original to support their journalism.", 13, Ui.MUTED, Ui.W_MEDIUM);
        note.setLineSpacing(0, 1.35f);
        col.addView(note);
        LinearLayout row = Ui.row(a);
        TextView orig = Ui.secondaryButton(a, "Original page");
        orig.setCompoundDrawablePadding(dp(8));
        orig.setOnClickListener(v -> host.openOriginal());
        LinearLayout.LayoutParams l1 = Ui.weight(1);
        l1.setMargins(0, 0, dp(5), 0);
        row.addView(orig, l1);
        TextView share = Ui.secondaryButton(a, "Share");
        share.setOnClickListener(v -> host.shareArticle());
        LinearLayout.LayoutParams l2 = Ui.weight(1);
        l2.setMargins(dp(5), 0, 0, 0);
        row.addView(share, l2);
        col.addView(row, Ui.lp(-1, -2, 0, dp(14), 0, 0));
    }

    Article article() {
        return article;
    }

    /** Tests: jump to a point in the story. */
    void scrollToFraction(float f) {
        scroll.scrollTo(0, (int) (col.getHeight() * f));
    }
}
