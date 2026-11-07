package com.skyglass.weather.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.skyglass.weather.MainActivity;
import java.util.List;

/**
 * Picks the place a widget shows. Opens when a widget is added on older Android versions, and
 * from "widget settings" (long-press the widget) on Android 12+.
 */
public class WidgetConfigActivity extends Activity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Bundle extras = getIntent().getExtras();
        if (extras != null) widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        Store store = new Store(this);
        List<Store.Place> places = store.places();
        String chosen = store.widgetPlace(widgetId);
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * dp);
        list.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Show weather for");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, (int) (12 * dp));
        list.addView(title);

        if (places.isEmpty()) {
            TextView msg = new TextView(this);
            msg.setText("Open Skyglass first to add a location. The widget will then show it.");
            msg.setTextSize(15);
            list.addView(msg);
            Button open = new Button(this);
            open.setText("Open Skyglass");
            open.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                done(null);
            });
            list.addView(open);
        } else {
            addOption(list, "Follow my first location", "My Location when it's on, otherwise your first city", chosen == null, () -> done(null));
            for (Store.Place p : places) {
                String sub = p.current ? "My Location" : String.format(java.util.Locale.US, "%.2f, %.2f", p.lat, p.lon);
                addOption(list, p.name, sub, p.id.equals(chosen), () -> done(p.id));
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        setContentView(scroll, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addOption(LinearLayout list, String name, String sub, boolean selected, Runnable onClick) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (10 * dp), 0, (int) (10 * dp));
        row.setClickable(true);
        row.setFocusable(true);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        TextView a = new TextView(this);
        a.setText((selected ? "● " : "○ ") + name);
        a.setTextSize(17);
        TextView b = new TextView(this);
        b.setText(sub);
        b.setTextSize(13);
        b.setAlpha(0.7f);
        b.setPadding((int) (22 * dp), 0, 0, 0);
        row.addView(a);
        row.addView(b);
        row.setOnClickListener(v -> onClick.run());
        list.addView(row);
    }

    private void done(String placeId) {
        Store store = new Store(this);
        store.setWidgetPlace(widgetId, placeId);
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        WidgetRenderer.render(this, mgr, widgetId, WidgetRenderer.kindOfWidget(this, widgetId));
        Store.Place p = store.place(placeId);
        if (p != null && store.data(p.id) == null) RefreshWorker.runNow(this);
        RefreshWorker.schedule(this);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }
}
