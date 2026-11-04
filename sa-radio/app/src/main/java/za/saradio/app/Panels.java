package za.saradio.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** The bottom sheets: settings, sound, sleep timer and adding a station. */
final class Panels {
    private Panels() {}

    // ---- Settings ---------------------------------------------------------------------------

    static void settings(final MainActivity a) {
        final Sheet s = new Sheet(a, "Settings");
        Context c = a;

        s.section("Data & quality");
        Segmented seg = new Segmented(c, DataMode.SHORT);
        seg.select(Prefs.dataMode(a), false);
        s.body.addView(seg, Ui.lp(-1, -2));
        final TextView desc = s.note(DataMode.describe(Prefs.dataMode(a)));
        seg.setListener(i -> {
            Prefs.setDataMode(a, i);
            desc.setText(DataMode.describe(i));
            a.onDataModeChanged();
            a.refreshLists();
        });

        LinearLayout usage = Ui.row(c);
        usage.setBackground(Ui.roundStroke(Ui.SURFACE2, Ui.dp(c, 18), Ui.STROKE, Ui.dp(c, 1)));
        usage.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        usage.addView(stat(c, Prefs.formatBytes(Prefs.monthBytes(a)), "Radio this month"), Ui.weight(1));
        View div = new View(c);
        div.setBackgroundColor(Ui.STROKE2);
        usage.addView(div, Ui.lp(Ui.dp(c, 1), Ui.dp(c, 36)));
        usage.addView(stat(c, Prefs.formatBytes(RadioService.sessionBytes), "This session"), Ui.weight(1));
        s.body.addView(usage, Ui.lp(-1, -2, 0, Ui.dp(c, 14), 0, 0));
        TextView fine = s.note("Counted from the bytes the app actually downloads for audio. News and pictures are not included.");
        fine.setTextSize(12);

        s.section("Sound");
        EqPresets.Preset p = EqPresets.find(Prefs.eqPreset(a));
        String soundSub = !Prefs.eqEnabled(a) ? "Effects off" : p != null ? p.name + " · " + p.blurb : "Custom curve";
        s.row(R.drawable.ic_eq, "Equalizer & effects", soundSub, v -> {
            s.dismiss();
            sound(a);
        });

        s.section("Stations");
        long at = Prefs.get(a).getLong("stationsAt", 0);
        String upd = a.stationCount() + " stations" + (at > 0 ? " · updated " + NewsRepo.ago(at).toLowerCase(java.util.Locale.ROOT) : "");
        s.row(R.drawable.ic_refresh, "Update station list", upd, v -> {
            s.dismiss();
            a.refreshStations(true);
        });
        s.row(R.drawable.ic_link, "Add your own station", "Play any stream link and keep it in Favourites", v -> {
            s.dismiss();
            addStation(a);
        });

        s.section("About");
        s.note("SA Radio 3.0. Stations come from radio-browser.info, a free community directory, so a few may be "
                + "off air at times. Headlines come from " + NewsRepo.FEEDS.length + " South African publishers' public "
                + "feeds, and stories open on the publisher's own site.");
        s.show();
    }

    private static View stat(Context c, String big, String small) {
        LinearLayout l = Ui.column(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView b = Ui.text(c, big, 20, Ui.TEXT, Ui.W_HEAVY);
        b.setGravity(Gravity.CENTER);
        l.addView(b);
        TextView t = Ui.text(c, small, 12, Ui.MUTED, Ui.W_MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        l.addView(t);
        return l;
    }

    // ---- Sound ------------------------------------------------------------------------------

    static void sound(final MainActivity a) {
        final Sheet s = new Sheet(a, "Sound");
        final Context c = a;
        AudioFx.ensure(a);
        final boolean eqOk = AudioFx.hasEqualizer();

        final Switch power = new Switch(c);
        power.setChecked(Prefs.eqEnabled(a));
        power.setThumbTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}}, new int[] {Ui.GOLD, 0xFFB8BEC9}));
        power.setTrackTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}}, new int[] {Ui.alpha(Ui.GOLD, 0.45f), 0x40FFFFFF}));
        power.setContentDescription("Sound effects on or off");
        s.addAction(power);

        final int[] bands = AudioFx.bands();
        final EqCurveView curve = new EqCurveView(c);
        curve.setBands(bands, AudioFx.curve(a), AudioFx.rangeDb());
        LinearLayout curveCard = Ui.column(c);
        curveCard.setBackground(Ui.roundStroke(Ui.SURFACE2, Ui.dp(c, 22), Ui.STROKE, Ui.dp(c, 1)));
        curveCard.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        curveCard.addView(curve, Ui.lp(-1, Ui.dp(c, 200)));
        Ui.roundClip(curveCard, Ui.dp(c, 22));
        s.body.addView(curveCard, Ui.lp(-1, -2, 0, Ui.dp(c, 4), 0, 0));
        final TextView hint = s.note(eqOk ? "Pick a preset or drag the points to make your own."
                : "This phone doesn't let apps change the sound, so the equalizer can't be used here. "
                        + "(Some phones reserve it for their own sound settings.)");
        hint.setTextSize(12.5f);

        s.section("Presets");
        final LinearLayout grid = Ui.column(c);
        s.body.addView(grid, Ui.lp(-1, -2));

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            grid.removeAllViews();
            String cur = Prefs.eqPreset(a);
            LinearLayout row = null;
            int i = 0;
            int total = EqPresets.ALL.length + (Prefs.eqCustom(a, bands.length) != null ? 1 : 0);
            for (int k = 0; k < total; k++) {
                final String id;
                String nm;
                String bl;
                if (k < EqPresets.ALL.length) {
                    EqPresets.Preset p = EqPresets.ALL[k];
                    id = p.id;
                    nm = p.name;
                    bl = p.blurb;
                } else {
                    id = "custom";
                    nm = "Custom";
                    bl = "Your own curve";
                }
                if (i % 2 == 0) {
                    row = Ui.row(c);
                    row.setGravity(Gravity.TOP);
                    grid.addView(row, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 10)));
                }
                View card = presetCard(c, nm, bl, id.equals(cur));
                card.setOnClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    Prefs.get(a).edit().putString("eqPreset", id).putBoolean("eqOn", true).apply();
                    power.setChecked(true);
                    AudioFx.apply(a);
                    curve.setLevels(AudioFx.curve(a));
                    rebuild[0].run();
                    a.refreshPlayer();
                });
                card.setEnabled(eqOk);
                LinearLayout.LayoutParams lp = Ui.weight(1);
                lp.setMargins(i % 2 == 0 ? 0 : Ui.dp(c, 5), 0, i % 2 == 0 ? Ui.dp(c, 5) : 0, 0);
                row.addView(card, lp);
                i++;
            }
            if (i % 2 == 1) row.addView(new View(c), Ui.weight(1));
        };
        rebuild[0].run();

        curve.setListener(new EqCurveView.Listener() {
            @Override
            public void onBand(int band, float db) {
                AudioFx.setBand(band, db);
            }

            @Override
            public void onCommit(float[] db) {
                Prefs.setEqCustom(a, db);
                Prefs.get(a).edit().putBoolean("eqOn", true).apply();
                power.setChecked(true);
                AudioFx.apply(a);
                rebuild[0].run();
                a.refreshPlayer();
            }
        });

        s.section("Effects");
        LinearLayout fx = Ui.column(c);
        s.body.addView(fx, Ui.lp(-1, -2));
        if (AudioFx.hasBass()) fx.addView(slider(a, "bass", "Bass boost", "Extra weight in the low end"));
        if (AudioFx.hasSurround()) fx.addView(slider(a, "surround", "Surround", "Wider sound; best with headphones"));
        if (AudioFx.hasLoudness()) fx.addView(slider(a, "loudness", "Loudness", "Lifts quiet talk shows and old recordings"));
        if (fx.getChildCount() == 0) s.note("This phone doesn't offer bass boost, surround or loudness to apps.");

        TextView reset = Ui.secondaryButton(c, "Reset to Normal");
        reset.setOnClickListener(v -> {
            Prefs.get(a).edit().putString("eqPreset", "normal").putInt("fx_bass", 0).putInt("fx_surround", 0)
                    .putInt("fx_loudness", 0).putBoolean("eqOn", true).apply();
            s.dismiss();
            AudioFx.apply(a);
            a.refreshPlayer();
            sound(a);
        });
        s.body.addView(reset, Ui.lp(-1, -2, 0, Ui.dp(c, 18), 0, 0));

        final boolean anyFx = eqOk || AudioFx.hasBass() || AudioFx.hasSurround() || AudioFx.hasLoudness();
        if (!anyFx) power.setVisibility(View.GONE);
        final View[] dim = {curveCard, grid, fx};
        Runnable applyPower = () -> {
            boolean on = power.isChecked() && anyFx;
            curve.setActive(on && eqOk);
            for (View d : dim) d.setAlpha(on ? 1f : 0.45f);
            // Presets only do something when the phone's equalizer works.
            setEnabledDeep(grid, on && eqOk);
            curveCard.setAlpha(on && eqOk ? 1f : 0.45f);
            grid.setAlpha(on && eqOk ? 1f : 0.45f);
        };
        power.setOnCheckedChangeListener((b, on) -> {
            Prefs.get(a).edit().putBoolean("eqOn", on).apply();
            AudioFx.apply(a);
            applyPower.run();
            a.refreshPlayer();
        });
        applyPower.run();
        s.show();
    }

    private static void setEnabledDeep(View v, boolean on) {
        v.setEnabled(on);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), on);
        }
    }

    private static View presetCard(Context c, String name, String blurb, boolean on) {
        LinearLayout k = Ui.column(c);
        k.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        float r = Ui.dp(c, 18);
        k.setBackground(Ui.ripple(on ? Ui.roundStroke(Ui.alpha(Ui.GOLD, 0.14f), r, Ui.GOLD, Ui.dp(c, 2))
                : Ui.roundStroke(Ui.SURFACE2, r, Ui.STROKE, Ui.dp(c, 1)), r));
        LinearLayout top = Ui.row(c);
        TextView t = Ui.text(c, name, 15, on ? Ui.GOLD : Ui.TEXT, Ui.W_HEAVY);
        top.addView(t, Ui.weight(1));
        if (on) {
            ImageView tick = new ImageView(c);
            tick.setImageResource(R.drawable.ic_check);
            tick.setColorFilter(Ui.GOLD);
            top.addView(tick, Ui.lp(Ui.dp(c, 18), Ui.dp(c, 18)));
        }
        k.addView(top);
        TextView b = Ui.text(c, blurb, 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        b.setPadding(0, Ui.dp(c, 4), 0, 0);
        b.setMinLines(2);
        k.addView(b);
        k.setContentDescription(name + ". " + blurb + (on ? ". Selected" : ""));
        Ui.press(k, 0.97f);
        return k;
    }

    private static View slider(final MainActivity a, final String key, String title, String sub) {
        Context c = a;
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 6));
        box.setBackground(Ui.roundStroke(Ui.SURFACE2, Ui.dp(c, 18), Ui.STROKE, Ui.dp(c, 1)));
        LinearLayout top = Ui.row(c);
        LinearLayout txt = Ui.column(c);
        txt.addView(Ui.text(c, title, 15, Ui.TEXT, Ui.W_BOLD));
        TextView s = Ui.text(c, sub, 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        s.setPadding(0, Ui.dp(c, 3), 0, 0);
        txt.addView(s);
        top.addView(txt, Ui.weight(1));
        final TextView val = Ui.text(c, "", 14, Ui.GOLD, Ui.W_HEAVY);
        top.addView(val);
        box.addView(top);
        SeekBar bar = new SeekBar(c);
        bar.setMax(100);
        int cur = Prefs.effect(a, key);
        bar.setProgress(cur);
        val.setText(cur == 0 ? "Off" : cur + "%");
        bar.setProgressTintList(ColorStateList.valueOf(Ui.GOLD));
        bar.setThumbTintList(ColorStateList.valueOf(Ui.GOLD));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x40FFFFFF));
        bar.setContentDescription(title);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                val.setText(p == 0 ? "Off" : p + "%");
                if (user) {
                    Prefs.get(a).edit().putInt("fx_" + key, p).apply();
                    AudioFx.apply(a);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {
                b.getParent().requestDisallowInterceptTouchEvent(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar b) {}
        });
        box.addView(bar, Ui.lp(-1, Ui.dp(c, 40)));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 10));
        box.setLayoutParams(lp);
        return box;
    }

    // ---- Sleep timer ------------------------------------------------------------------------

    static void sleep(final MainActivity a) {
        final Sheet s = new Sheet(a, "Sleep timer");
        Context c = a;
        final int[] mins = {0, 15, 30, 45, 60, 90, 120};
        final String[] labels = {"Off", "15 minutes", "30 minutes", "45 minutes", "1 hour", "1½ hours", "2 hours"};
        long at = RadioService.sleepAt;
        if (at > 0) s.note("The radio fades out and switches off at " + NowPlaying.clock(at) + ".");
        else s.note("The radio fades out gently over the last 30 seconds.");
        int curIdx = -1;
        if (at > 0) {
            long left = (at - System.currentTimeMillis()) / 60_000;
            for (int i = 1; i < mins.length; i++) if (Math.abs(mins[i] - left) <= 1) curIdx = i;
        }
        LinearLayout list = Ui.column(c);
        list.setPadding(0, Ui.dp(c, 12), 0, 0);
        for (int i = 0; i < mins.length; i++) {
            final int m = mins[i];
            boolean on = (at == 0 && i == 0) || i == curIdx;
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 14), 0);
            float r = Ui.dp(c, 16);
            row.setBackground(Ui.ripple(on ? Ui.roundStroke(Ui.alpha(Ui.GOLD, 0.12f), r, Ui.alpha(Ui.GOLD, 0.6f), Ui.dp(c, 1))
                    : Ui.round(Ui.SURFACE2, r), r));
            TextView t = Ui.text(c, labels[i], 15.5f, on ? Ui.GOLD : Ui.TEXT, Ui.W_BOLD);
            row.addView(t, Ui.weight(1));
            if (on) {
                ImageView tick = new ImageView(c);
                tick.setImageResource(R.drawable.ic_check);
                tick.setColorFilter(Ui.GOLD);
                row.addView(tick, Ui.lp(Ui.dp(c, 20), Ui.dp(c, 20)));
            }
            row.setOnClickListener(v -> {
                RadioService.sleepAt = m > 0 ? System.currentTimeMillis() + m * 60_000L : 0;
                RadioService.send(a, RadioService.ACTION_SLEEP, null, m);
                a.snack(m > 0 ? "Radio will switch off at " + NowPlaying.clock(RadioService.sleepAt) : "Sleep timer off");
                s.dismiss();
                a.refreshPlayer();
            });
            list.addView(row, Ui.lp(-1, Ui.dp(c, 54), 0, 0, 0, Ui.dp(c, 8)));
        }
        s.body.addView(list);
        s.show();
    }

    // ---- Add a station ----------------------------------------------------------------------

    static void addStation(final MainActivity a) {
        final Sheet s = new Sheet(a, "Add your own station");
        Context c = a;
        s.note("Paste the direct stream link from a station's website. It usually ends in .mp3, .aac, .m3u or .pls.");
        final EditText name = field(c, "Station name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        s.body.addView(name, Ui.lp(-1, Ui.dp(c, 54), 0, Ui.dp(c, 16), 0, 0));
        final EditText url = field(c, "Stream link (https://…)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        s.body.addView(url, Ui.lp(-1, Ui.dp(c, 54), 0, Ui.dp(c, 10), 0, 0));
        final TextView err = Ui.text(c, "", 13, Ui.RED, Ui.W_BOLD);
        err.setVisibility(View.GONE);
        s.body.addView(err, Ui.lp(-1, -2, Ui.dp(c, 4), Ui.dp(c, 8), 0, 0));
        TextView go = Ui.primaryButton(c, "Add and play");
        go.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String u = url.getText().toString().trim();
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                err.setText("The link must start with http:// or https://");
                err.setVisibility(View.VISIBLE);
                return;
            }
            s.dismiss();
            a.addCustomStation(n, u);
        });
        s.body.addView(go, Ui.lp(-1, -2, 0, Ui.dp(c, 16), 0, 0));
        s.show();
        name.requestFocus();
    }

    private static EditText field(Context c, String hint, int type) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(Ui.MUTED);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(15.5f);
        e.setTypeface(Ui.medium);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        e.setBackground(Ui.roundStroke(Ui.SURFACE2, Ui.dp(c, 16), Ui.STROKE2, Ui.dp(c, 1)));
        return e;
    }
}
