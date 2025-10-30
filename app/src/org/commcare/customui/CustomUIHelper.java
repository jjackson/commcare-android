package org.commcare.customui;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.activities.CustomUIActivity;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Helper class to detect and launch custom UI apps.
 * 
 * Checks if the current app has custom UI assets in the assets/custom_ui folder,
 * and provides methods to launch the CustomUIActivity.
 */
public class CustomUIHelper {
    private static final String TAG = CustomUIHelper.class.getSimpleName();
    
    private static final String CUSTOM_UI_FOLDER = "custom_ui";
    private static final String CUSTOM_UI_CONFIG = "custom_ui/config.json";
    private static final String CUSTOM_UI_INDEX = "custom_ui/index.html";
    
    /**
     * Check if the current app has custom UI enabled
     * @param context Android context
     * @return true if custom UI is available and enabled
     */
    public static boolean isCustomUIEnabled(Context context) {
        try {
            // Check if config file exists in assets
            InputStream configStream = context.getAssets().open(CUSTOM_UI_CONFIG);
            
            // Read and parse config
            byte[] buffer = new byte[configStream.available()];
            configStream.read(buffer);
            configStream.close();
            
            String configJson = new String(buffer, "UTF-8");
            JSONObject config = new JSONObject(configJson);
            
            // Check if custom UI is enabled
            if (config.has("customUI")) {
                JSONObject customUIConfig = config.getJSONObject("customUI");
                boolean enabled = customUIConfig.optBoolean("enabled", false);
                
                Log.d(TAG, "Custom UI enabled: " + enabled);
                return enabled;
            }
            
            return false;
        } catch (Exception e) {
            Log.d(TAG, "Custom UI not found or not enabled: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Launch the custom UI activity
     * @param context Android context
     */
    public static void launchCustomUI(Context context) {
        launchCustomUI(context, null);
    }
    
    /**
     * Launch the custom UI activity with an initial route
     * @param context Android context
     * @param initialRoute Initial route/hash for the web app (e.g., "form", "cases")
     */
    public static void launchCustomUI(Context context, String initialRoute) {
        Intent intent = new Intent(context, CustomUIActivity.class);
        intent.putExtra(CustomUIActivity.EXTRA_CUSTOM_UI_PATH, CUSTOM_UI_INDEX);
        
        if (initialRoute != null) {
            intent.putExtra(CustomUIActivity.EXTRA_INITIAL_ROUTE, initialRoute);
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        
        Log.d(TAG, "Launching custom UI");
    }
    
    /**
     * Get the custom UI entrypoint path from config
     * @param context Android context
     * @return Path to custom UI entrypoint, or null if not configured
     */
    public static String getCustomUIEntrypoint(Context context) {
        try {
            InputStream configStream = context.getAssets().open(CUSTOM_UI_CONFIG);
            byte[] buffer = new byte[configStream.available()];
            configStream.read(buffer);
            configStream.close();
            
            String configJson = new String(buffer, "UTF-8");
            JSONObject config = new JSONObject(configJson);
            
            if (config.has("customUI")) {
                JSONObject customUIConfig = config.getJSONObject("customUI");
                return customUIConfig.optString("entrypoint", CUSTOM_UI_INDEX);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading custom UI config", e);
        }
        
        return null;
    }
}
