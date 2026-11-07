package com.skyglass.weather.widget;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * The web app's link to the home-screen widgets and background notifications
 * (src/platform/native.ts → WidgetBridge).
 */
@CapacitorPlugin(
    name = "WidgetBridge",
    permissions = { @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS }) }
)
public class WidgetBridgePlugin extends Plugin {

    @PluginMethod
    public void sync(PluginCall call) {
        Context ctx = getContext();
        Store store = new Store(ctx);
        try {
            JSArray places = call.getArray("places", new JSArray());
            JSObject settings = call.getObject("settings", new JSObject());
            String before = store.places().isEmpty() ? "" : store.places().get(0).id;
            store.savePlaces(new JSONArray(places.toString()));
            store.saveSettings(settings);
            // Redraw at once with the new style, then fetch fresh data in the background.
            WidgetRenderer.updateAll(ctx);
            RefreshWorker.schedule(ctx);
            RefreshWorker.scheduleDaily(ctx, store.settings());
            String after = store.places().isEmpty() ? "" : store.places().get(0).id;
            Store.Place first = store.place(null);
            if (!before.equals(after) || (first != null && store.data(first.id) == null) || WidgetRenderer.widgetCount(ctx) > 0) {
                RefreshWorker.runNow(ctx);
            }
            call.resolve();
        } catch (JSONException e) {
            call.reject("Bad places", e);
        }
    }

    @PluginMethod
    public void refresh(PluginCall call) {
        RefreshWorker.runNow(getContext());
        call.resolve();
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED) {
            resolveNotifications(call);
        } else {
            requestPermissionForAlias("notifications", call, "notificationsResult");
        }
    }

    @PermissionCallback
    private void notificationsResult(PluginCall call) {
        resolveNotifications(call);
    }

    private void resolveNotifications(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", NotificationManagerCompat.from(getContext()).areNotificationsEnabled());
        call.resolve(r);
    }

    @PluginMethod
    public void info(PluginCall call) {
        Context ctx = getContext();
        JSObject r = new JSObject();
        r.put("widgets", WidgetRenderer.widgetCount(ctx));
        r.put("notifications", NotificationManagerCompat.from(ctx).areNotificationsEnabled());
        r.put("canPin", Build.VERSION.SDK_INT >= 26 && AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported());
        call.resolve(r);
    }

    @PluginMethod
    public void pin(PluginCall call) {
        Context ctx = getContext();
        JSObject r = new JSObject();
        if (Build.VERSION.SDK_INT < 26) {
            r.put("requested", false);
            call.resolve(r);
            return;
        }
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName provider = new ComponentName(ctx, WidgetRenderer.providerFor(call.getString("kind", "medium")));
        r.put("requested", mgr.isRequestPinAppWidgetSupported() && mgr.requestPinAppWidget(provider, null, null));
        call.resolve(r);
    }
}
