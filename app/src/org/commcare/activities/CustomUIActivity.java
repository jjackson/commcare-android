package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.commcare.CommCareApplication;
import org.commcare.customui.CommCareJavaScriptInterface;
import org.commcare.dalvik.R;
import org.commcare.utils.AndroidCommCarePlatform;

/**
 * Activity that loads a custom UI from the CCZ assets folder and provides
 * a JavaScript bridge to interact with CommCare's form and case APIs.
 * 
 * This allows developers to build custom frontends using web technologies
 * while leveraging CommCare's offline storage, sync, and case management.
 */
public class CustomUIActivity extends AppCompatActivity {
    private static final String TAG = CustomUIActivity.class.getSimpleName();
    
    public static final String EXTRA_CUSTOM_UI_PATH = "custom_ui_path";
    public static final String EXTRA_INITIAL_ROUTE = "initial_route";
    
    private WebView webView;
    private CommCareJavaScriptInterface jsInterface;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_ui);
        
        // Initialize WebView
        webView = findViewById(R.id.custom_ui_webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        // Enable debugging for WebView
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        
        // Set up WebView clients for debugging and navigation
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page loaded: " + url);
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView error: " + description);
                Toast.makeText(CustomUIActivity.this, "Error loading page: " + description, Toast.LENGTH_LONG).show();
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "Console: " + consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return true;
            }
        });
        
        // Initialize and attach JavaScript interface
        try {
            AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
            jsInterface = new CommCareJavaScriptInterface(this, platform);
            webView.addJavascriptInterface(jsInterface, "CommCareAPI");
            
            Log.d(TAG, "JavaScript interface attached successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing JavaScript interface", e);
            Toast.makeText(this, "Error initializing CommCare API", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Load the custom UI
        String customUIPath = getIntent().getStringExtra(EXTRA_CUSTOM_UI_PATH);
        String initialRoute = getIntent().getStringExtra(EXTRA_INITIAL_ROUTE);
        
        if (customUIPath == null) {
            customUIPath = "custom_ui/index.html";
        }
        
        String url = "file:///android_asset/" + customUIPath;
        if (initialRoute != null) {
            url += "#" + initialRoute;
        }
        
        Log.d(TAG, "Loading custom UI from: " + url);
        webView.loadUrl(url);
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Handle any activity results (e.g., from barcode scanner, camera, etc.)
        if (jsInterface != null) {
            jsInterface.handleActivityResult(requestCode, resultCode, data);
        }
    }
    
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("CommCareAPI");
            webView.destroy();
        }
        super.onDestroy();
    }
}
