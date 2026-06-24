package com.myservyfast.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Logger;

import java.util.Map;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "MyServyFast";
    private static final String WEBSITE_URL = "https://myservyfast.com";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up splash theme before super
        getPreloadPlugin();
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge display
        setupEdgeToEdge();

        // Set up permission launchers
        setupPermissionLaunchers();

        // Override WebView configuration from Capacitor
        configureWebView();

        // Handle incoming intents (notifications, share, deep links)
        handleIntent(getIntent());

        // File chooser for file uploads
        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePathCallback != null) {
                    handleFileChooserResult(result.getResultCode(), result.getData());
                }
            }
        );

        // Back button handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupPermissionLaunchers() {
        // Multiple permissions launcher
        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                        Logger.warn(TAG, "Permission denied: " + entry.getKey());
                    }
                }
                if (allGranted) {
                    Logger.info(TAG, "All permissions granted");
                }
            }
        );

        // Notification permission launcher for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        Logger.info(TAG, "Notification permission granted");
                        // Initialize push notifications after permission granted
                        initializePushNotifications();
                    } else {
                        Logger.warn(TAG, "Notification permission denied");
                        Toast.makeText(this,
                            "Notifications are disabled. Enable in Settings for order updates.",
                            Toast.LENGTH_LONG).show();
                    }
                }
            );
        }
    }

    private void setupEdgeToEdge() {
        // Make window draw edge-to-edge
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_TOUCH);
            }
        }

        // Set status bar and navigation bar to transparent
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Request notification permission on first launch for Android 13+
        requestNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Uri data = intent.getData();

        Log.d(TAG, "Handling intent: action=" + action + ", data=" + data);

        // Handle notification click with deep link
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String url = data.toString();
            Logger.info(TAG, "Deep link received: " + url);
            loadUrlInWebView(url);
        }

        // Handle notification action click
        String notificationAction = intent.getStringExtra("notification_action");
        if (notificationAction != null) {
            handleNotificationAction(notificationAction, intent);
        }

        // Handle share intent
        if (Intent.ACTION_SEND.equals(action)) {
            handleShareIntent(intent);
        }

        // Handle notification data payload
        handleNotificationData(intent);
    }

    private void handleNotificationAction(String action, Intent intent) {
        Logger.info(TAG, "Notification action: " + action);

        String url = intent.getStringExtra("url");
        String productId = intent.getStringExtra("productId");
        String orderId = intent.getStringExtra("orderId");
        String chatId = intent.getStringExtra("chatId");

        switch (action) {
            case "OPEN_URL":
                if (url != null) {
                    loadUrlInWebView(url);
                }
                break;

            case "OPEN_PRODUCT":
                if (productId != null) {
                    loadUrlInWebView(WEBSITE_URL + "/product/" + productId);
                }
                break;

            case "OPEN_ORDER":
                if (orderId != null) {
                    loadUrlInWebView(WEBSITE_URL + "/my-account/orders/" + orderId);
                }
                break;

            case "OPEN_CHAT":
                if (chatId != null) {
                    loadUrlInWebView(WEBSITE_URL + "/my-account/messages/" + chatId);
                } else {
                    loadUrlInWebView(WEBSITE_URL + "/my-account/messages");
                }
                break;

            case "OPEN_CART":
                loadUrlInWebView(WEBSITE_URL + "/cart");
                break;

            case "OPEN_CHECKOUT":
                loadUrlInWebView(WEBSITE_URL + "/checkout");
                break;

            case "OPEN_ACCOUNT":
                loadUrlInWebView(WEBSITE_URL + "/my-account");
                break;

            default:
                if (url != null) {
                    loadUrlInWebView(url);
                } else {
                    Logger.warn(TAG, "Unknown notification action: " + action);
                }
                break;
        }
    }

    private void handleShareIntent(Intent intent) {
        String type = intent.getType();
        if ("text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                Logger.info(TAG, "Shared text: " + sharedText);
                // Could pass to website via URL parameter
            }
        } else if (type != null && type.startsWith("image/")) {
            Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                Logger.info(TAG, "Shared image: " + imageUri);
            }
        }
    }

    private void handleNotificationData(Intent intent) {
        // Extract all extras and handle as notification data
        Bundle extras = intent.getExtras();
        if (extras != null && webView != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                if (value != null) {
                    Logger.debug(TAG, "Notification extra: " + key + " = " + value);
                }
            }

            // Pass notification data to WebView
            String url = extras.getString("url");
            if (url != null) {
                loadUrlInWebView(url);
            }
        }
    }

    private void loadUrlInWebView(String url) {
        if (webView != null) {
            webView.loadUrl(url);
        } else {
            // WebView not ready, store URL to load later
            Logger.warn(TAG, "WebView not ready for URL: " + url);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                // Check if we should show rationale
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    Toast.makeText(this,
                        "Notifications help you track orders and receive important updates",
                        Toast.LENGTH_LONG).show();
                }

                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Permission already granted, initialize push notifications
                initializePushNotifications();
            }
        } else {
            // Pre-Android 13, no permission needed
            initializePushNotifications();
        }
    }

    private void initializePushNotifications() {
        // Push notifications are handled by Capacitor plugin
        // This is called after permission is granted
        Logger.info(TAG, "Push notifications initialized");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        // Get WebView from Capacitor bridge
        webView = getBridge().getWebView();

        if (webView == null) return;

        WebSettings settings = webView.getSettings();

        // Enable JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Enable DOM storage for session persistence
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Enable file access
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Enable geolocation
        settings.setGeolocationEnabled(true);
        settings.setGeolocationDatabasePath(getFilesDir().getPath());

        // Enable media playback without user gesture
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Enable zoom and wide viewport
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Cache settings for offline support
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Mixed content mode for API 21+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        // Persist cookies and login sessions
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.flush();

        // Custom WebViewClient for link handling
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (isExternalUrl(url)) {
                    openExternalBrowser(url);
                    return true;
                }

                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
            }
        });

        // Handle file uploads and permissions
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }
        });
    }

    private boolean isExternalUrl(String url) {
        String[] externalPatterns = {
            "tel:", "mailto:", "sms:", "whatsapp:",
            "facebook.com", "instagram.com", "twitter.com",
            "play.google.com", "apps.apple.com"
        };

        for (String pattern : externalPatterns) {
            if (url.contains(pattern)) {
                return true;
            }
        }

        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null && !host.contains("myservyfast.com")) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    private void openExternalBrowser(String url) {
        try {
            Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleFileChooserResult(int resultCode, Intent data) {
        Uri[] results = null;

        if (resultCode == RESULT_OK && data != null) {
            if (data.getData() != null) {
                results = new Uri[]{ data.getData() };
            } else if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
        }

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CookieManager.getInstance().flush();
        requestRequiredPermissions();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private void requestRequiredPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            permissions = new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        permissionLauncher.launch(permissions);
    }
}
