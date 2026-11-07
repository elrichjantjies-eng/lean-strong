package com.skyglass.weather;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.skyglass.weather.widget.WidgetBridgePlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(WidgetBridgePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
