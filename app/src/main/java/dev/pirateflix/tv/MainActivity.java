package dev.pirateflix.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Full-screen, TV-remote-friendly wrapper for the PirateFlix website. */
public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://pirate-flix.pages.dev/";
    private static final String PREFS = "browser_state";
    private static final String LAST_URL = "last_site_url";
    private static final int PICK_FILE = 501;
    private static final long URL_CHECK_MS = 1200L;

    private FrameLayout root;
    private WebView webView;
    private ProgressBar loading;
    private LinearLayout errorPanel;
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customVideoCallback;
    private WebChromeClient.FileChooserParams pendingChooser;
    private android.webkit.ValueCallback<Uri[]> fileCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean clearHistoryOnHome;
    private boolean backHeld;
    private boolean backMenuShown;
    private final Runnable longBackAction = () -> {
        if (backHeld) {
            backMenuShown = true;
            showNavigationMenu();
        }
    };

    private final Runnable saveUrlTask = new Runnable() {
        @Override public void run() {
            if (webView != null) rememberSiteUrl(webView.getUrl());
            handler.postDelayed(this, URL_CHECK_MS);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        applyImmersiveMode();
        buildLayout();
        createWebView();
        boolean restored = false;
        if (state != null) {
            try { restored = webView.restoreState(state) != null; }
            catch (RuntimeException ignored) { restored = false; }
        }
        if (!restored) webView.loadUrl(getSavedSiteUrl());
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(10, 15, 28));
        setContentView(root);

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);
        loading.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(39, 213, 186)));
        loading.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        root.addView(loading, progressParams);

        errorPanel = new LinearLayout(this);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorPanel.setBackgroundColor(Color.rgb(10, 15, 28));
        TextView headline = new TextView(this);
        headline.setText("Can't open PirateFlix");
        headline.setTextSize(24);
        headline.setTextColor(Color.WHITE);
        headline.setGravity(Gravity.CENTER);
        errorPanel.addView(headline);
        TextView caption = new TextView(this);
        caption.setText("Check your connection, then retry.");
        caption.setTextColor(Color.rgb(177, 189, 204));
        caption.setTextSize(15);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.setMargins(0, dp(10), 0, dp(20));
        errorPanel.addView(caption, captionParams);

        Button retry = new Button(this);
        retry.setText("Retry");
        retry.setOnClickListener(v -> {
            errorPanel.setVisibility(View.GONE);
            if (webView != null) webView.loadUrl(getSavedSiteUrl());
        });
        errorPanel.addView(retry);

        Button home = new Button(this);
        home.setText("Go to Home");
        home.setOnClickListener(v -> goHome());
        errorPanel.addView(home);

        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loading.bringToFront();
    }

    private void createWebView() {
        WebView browser = new WebView(this);
        webView = browser;
        browser.setBackgroundColor(Color.rgb(10, 15, 28));
        browser.setFocusable(true);
        browser.setFocusableInTouchMode(true);
        browser.setOverScrollMode(View.OVER_SCROLL_NEVER);
        browser.setVerticalScrollBarEnabled(false);
        browser.setHorizontalScrollBarEnabled(false);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);

        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (isWebUrl(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                errorPanel.setVisibility(View.GONE);
                loading.setVisibility(View.VISIBLE);
                rememberSiteUrl(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                rememberSiteUrl(url);
                loading.setVisibility(View.GONE);
                if (clearHistoryOnHome && isHomeUrl(url)) {
                    view.clearHistory();
                    clearHistoryOnHome = false;
                }
            }

            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                rememberSiteUrl(url);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  WebResourceError error) {
                if (request.isForMainFrame()) {
                    loading.setVisibility(View.GONE);
                    errorPanel.setVisibility(View.VISIBLE);
                    errorPanel.bringToFront();
                }
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // WebView's separate renderer can be killed by Android on low-memory TV boxes.
                if (customVideoView != null) exitFullScreenVideo();
                rememberSiteUrl(view.getUrl());
                root.removeView(view);
                view.destroy();
                webView = null;
                createWebView();
                webView.loadUrl(getSavedSiteUrl());
                return true;
            }
        });

        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                loading.setProgress(progress);
                if (progress == 100) loading.setVisibility(View.GONE);
            }

            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customVideoView = view;
                customVideoCallback = callback;
                browser.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                view.bringToFront();
                applyImmersiveMode();
            }

            @Override public void onHideCustomView() { exitFullScreenVideo(); }

            @Override public boolean onCreateWindow(WebView view, boolean isDialog,
                                                    boolean isUserGesture, Message resultMsg) {
                if (!isUserGesture) return false;
                // Open user-initiated target=_blank links in the same full-screen tab.
                final WebView popup = new WebView(MainActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);
                popup.setWebViewClient(new WebViewClient() {
                    private boolean forwarded;
                    private void forward(Uri uri) {
                        if (forwarded) return;
                        forwarded = true;
                        handler.post(() -> {
                            if (isWebUrl(uri)) browser.loadUrl(uri.toString());
                            else openExternal(uri);
                            popup.stopLoading();
                            popup.destroy();
                        });
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                        forward(r.getUrl());
                        return true;
                    }
                    @Override public void onPageStarted(WebView w, String url,
                                                        android.graphics.Bitmap favicon) {
                        if (isWebUrl(Uri.parse(url))) forward(Uri.parse(url));
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }

            @Override public boolean onShowFileChooser(WebView view,
                     android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                pendingChooser = params;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                    return true;
                } catch (ActivityNotFoundException | SecurityException ex) {
                    fileCallback = null;
                    pendingChooser = null;
                    return false;
                }
            }
        });

        root.addView(browser, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        browser.requestFocus();
    }

    private void exitFullScreenVideo() {
        if (customVideoView == null) return;
        root.removeView(customVideoView);
        customVideoView = null;
        WebChromeClient.CustomViewCallback callback = customVideoCallback;
        customVideoCallback = null;
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
            webView.requestFocus();
        }
        if (callback != null) callback.onCustomViewHidden();
        applyImmersiveMode();
    }

    private void goHome() {
        if (customVideoView != null) exitFullScreenVideo();
        errorPanel.setVisibility(View.GONE);
        clearHistoryOnHome = true;
        rememberSiteUrl(HOME_URL);
        if (webView != null) webView.loadUrl(HOME_URL);
    }

    private void handleShortBack() {
        if (customVideoView != null) { exitFullScreenVideo(); return; }
        if (webView != null && webView.canGoBack()) { webView.goBack(); return; }
        showNavigationMenu();
    }

    private void showNavigationMenu() {
        final String[] options = {"Go to PirateFlix Home", "Back one page", "Reload page", "Exit app"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PirateFlix TV")
                .setItems(options, (d, selection) -> {
                    if (selection == 0) goHome();
                    if (selection == 1 && webView != null && webView.canGoBack()) webView.goBack();
                    if (selection == 2 && webView != null) {
                        errorPanel.setVisibility(View.GONE);
                        webView.reload();
                    }
                    if (selection == 3) finish();
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnDismissListener(d -> applyImmersiveMode());
        dialog.show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        // Intercept before the WebView: some TV firmware swallows ordinary key handlers.
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (!backHeld) {
                    backHeld = true;
                    backMenuShown = false;
                    handler.postDelayed(longBackAction, 650L);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longBackAction);
                if (backHeld && !backMenuShown && !event.isCanceled()) handleShortBack();
                backHeld = false;
                backMenuShown = false;
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) showNavigationMenu();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override @Deprecated public void onBackPressed() { handleShortBack(); }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("mailto") ||
                scheme.equalsIgnoreCase("tel") || scheme.equalsIgnoreCase("market"))) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException ignored) { }
    }

    private static boolean isWebUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
    }

    private static boolean isHomeUrl(String url) {
        return url != null && (url.equals(HOME_URL) ||
                url.equals("https://pirate-flix.pages.dev"));
    }

    private void rememberSiteUrl(String url) {
        if (url == null) return;
        Uri uri = Uri.parse(url);
        // Only reopen the named website, never a stray advertisement or external player.
        if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                !"pirate-flix.pages.dev".equalsIgnoreCase(uri.getHost())) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!url.equals(prefs.getString(LAST_URL, ""))) {
            prefs.edit().putString(LAST_URL, url).apply();
        }
    }

    private String getSavedSiteUrl() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(LAST_URL, HOME_URL);
        if (saved == null) return HOME_URL;
        Uri uri = Uri.parse(saved);
        return "https".equalsIgnoreCase(uri.getScheme()) &&
                "pirate-flix.pages.dev".equalsIgnoreCase(uri.getHost()) ? saved : HOME_URL;
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        handler.removeCallbacks(saveUrlTask);
        handler.postDelayed(saveUrlTask, URL_CHECK_MS);
        applyImmersiveMode();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(saveUrlTask);
        if (webView != null) {
            rememberSiteUrl(webView.getUrl());
            webView.onPause();
        }
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            pendingChooser = null;
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(saveUrlTask);
        handler.removeCallbacks(longBackAction);
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (customVideoView != null) exitFullScreenVideo();
        if (webView != null) {
            root.removeView(webView);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
