package dev.pirateflix.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.EditText;
import android.text.InputType;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Collections;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Native Android-TV remote layer around the first-party PirateFlix website. */
public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://pirate-flix.pages.dev/";
    private static final String PREFS = "browser_state";
    private static final String LAST_URL = "last_site_url";
    private static final String PREF_ZOOM = "page_zoom";
    private static final long URL_CHECK_MS = 1250L;
    private static final int PICK_FILE = 501;
    private static final int SEEK_TICK_MS = 400;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private WebView webView;
    private ProgressBar loading;
    private LinearLayout errorPanel;
    private TextView statusToast;
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customVideoCallback;
    private android.webkit.ValueCallback<Uri[]> fileCallback;
    private String navigationScript;
    private String mediaFrameScript;
    private boolean clearHistoryOnHome;
    private boolean backHeld;
    private boolean backMenuShown;
    private boolean keyboardRequested;
    private boolean okHeld;
    private boolean okMenuShown;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean passthroughPlayerKeys;
    private final Runnable longOkAction = () -> {
        if (okHeld) { okMenuShown = true; showNavigationMenu(); }
    };
    private int seekDirection;
    private long seekStartMs;
    private Runnable hideStatus;
    private final Runnable longBackAction = () -> {
        if (backHeld) {
            backMenuShown = true;
            showNavigationMenu();
        }
    };
    private final Runnable seekTask = new Runnable() {
        @Override public void run() {
            if (seekDirection == 0 || webView == null) return;
            long hold = android.os.SystemClock.uptimeMillis() - seekStartMs;
            // Rate while holding: 15, 30, 60, 120, eventually 240 seconds per second.
            double rate = hold > 4800 ? 240 : hold > 3300 ? 120 : hold > 2100 ? 60
                    : hold > 1000 ? 30 : 15;
            seekVideo(seekDirection * rate * (SEEK_TICK_MS / 1000.0), false);
            handler.postDelayed(this, SEEK_TICK_MS);
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
        navigationScript = loadAsset("tv-nav.js");
        mediaFrameScript = loadAsset("iframe-media.js");
        applyImmersiveMode();
        buildLayout();
        createWebView();
        boolean restored = false;
        if (state != null) {
            try { restored = webView.restoreState(state) != null; }
            catch (RuntimeException ignored) { }
        }
        if (!restored) webView.loadUrl(getSavedSiteUrl());
    }

    private String loadAsset(String name) {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
            return bytes.toString("UTF-8");
        } catch (Exception ignored) { return ""; }
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(9, 14, 25));
        setContentView(root);

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);
        loading.setProgressTintList(ColorStateList.valueOf(Color.rgb(32, 229, 204)));
        loading.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        root.addView(loading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));

        errorPanel = new LinearLayout(this);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorPanel.setBackgroundColor(Color.rgb(9, 14, 25));
        TextView headline = new TextView(this);
        headline.setText("Can't open PirateFlix");
        headline.setTextSize(24);
        headline.setTextColor(Color.WHITE);
        headline.setGravity(Gravity.CENTER);
        errorPanel.addView(headline);
        TextView caption = new TextView(this);
        caption.setText("Check your internet connection, then retry.");
        caption.setTextColor(Color.rgb(177, 189, 204));
        caption.setTextSize(15);
        caption.setGravity(Gravity.CENTER);
        errorPanel.addView(caption);
        Button retry = new Button(this);
        retry.setText("Retry");
        retry.setOnClickListener(v -> { errorPanel.setVisibility(View.GONE); webView.loadUrl(getSavedSiteUrl()); });
        errorPanel.addView(retry);
        Button home = new Button(this);
        home.setText("Go Home");
        home.setOnClickListener(v -> goHome());
        errorPanel.addView(home);
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusToast = new TextView(this);
        statusToast.setTextColor(Color.WHITE);
        statusToast.setTextSize(18);
        statusToast.setGravity(Gravity.CENTER);
        statusToast.setPadding(dp(20), dp(12), dp(20), dp(12));
        statusToast.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(20, 35, 52)));
        statusToast.setBackgroundResource(android.R.drawable.dialog_holo_dark_frame);
        FrameLayout.LayoutParams toastParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        toastParams.bottomMargin = dp(42);
        root.addView(statusToast, toastParams);
        statusToast.setVisibility(View.GONE);
    }

    private void showStatus(String text) {
        statusToast.setText(text);
        statusToast.setVisibility(View.VISIBLE);
        statusToast.bringToFront();
        if (hideStatus != null) handler.removeCallbacks(hideStatus);
        hideStatus = () -> statusToast.setVisibility(View.GONE);
        handler.postDelayed(hideStatus, 2000L);
    }

    private void createWebView() {
        final WebView browser = new WebView(this);
        webView = browser;
        browser.setBackgroundColor(Color.rgb(9, 14, 25));
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
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);

        // Inject lightweight media controls into iframe documents as they load.
        // This allows seeking within many cross-origin HTML5 players while
        // respecting each iframe's own source/DRM constraints.
        if (!mediaFrameScript.isEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(browser, mediaFrameScript, Collections.singleton("*"));
            } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
                // Older Android System WebView: main-page video controls still work.
            }
        }

        browser.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Restrict blocking to known ad/malvertising hosts. Do not intercept video or source URLs.
                Uri uri = request.getUrl();
                if (isBlockedAdHost(uri.getHost())) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (isBlockedAdHost(uri.getHost())) return true;
                if (isWebUrl(uri)) {
                    // Never permit an ad popup to replace the saved first-party page.
                    if (!isSiteUrl(uri.toString()) && !request.hasGesture()) return true;
                    return false;
                }
                openExternal(uri);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                errorPanel.setVisibility(View.GONE);
                loading.setVisibility(View.VISIBLE);
                keyboardRequested = false;
                rememberSiteUrl(url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                rememberSiteUrl(url);
                loading.setVisibility(View.GONE);
                installTvNavigation();
                if (clearHistoryOnHome && isHomeUrl(url)) {
                    view.clearHistory();
                    clearHistoryOnHome = false;
                }
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                rememberSiteUrl(url);
                handler.postDelayed(() -> installTvNavigation(), 320L);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    loading.setVisibility(View.GONE);
                    errorPanel.setVisibility(View.VISIBLE);
                    errorPanel.bringToFront();
                }
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
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
                if (progress >= 75 && progress <= 85) installTvNavigation();
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) { callback.onCustomViewHidden(); return; }
                customVideoView = view;
                customVideoCallback = callback;
                browser.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                view.bringToFront();
                statusToast.bringToFront();
                applyImmersiveMode();
            }
            @Override public void onHideCustomView() { exitFullScreenVideo(); }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                if (!isUserGesture) return false;
                final WebView popup = new WebView(MainActivity.this);
                popup.getSettings().setJavaScriptEnabled(false);
                popup.setWebViewClient(new WebViewClient() {
                    private boolean forwarded;
                    private void forward(Uri uri) {
                        if (forwarded) return;
                        forwarded = true;
                        handler.post(() -> {
                            if (isSiteUrl(uri.toString())) browser.loadUrl(uri.toString());
                            // Reject advertising/new-tab redirects. Source picking remains in the website.
                            popup.stopLoading();
                            popup.destroy();
                        });
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                        forward(r.getUrl()); return true;
                    }
                    @Override public void onPageStarted(WebView w, String url, android.graphics.Bitmap favicon) {
                        forward(Uri.parse(url));
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
            @Override public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try { startActivityForResult(params.createIntent(), PICK_FILE); return true; }
                catch (ActivityNotFoundException | SecurityException ex) {
                    fileCallback = null; return false;
                }
            }
        });
        root.addView(browser, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        browser.requestFocus();
        statusToast.bringToFront();
    }

    private static boolean isBlockedAdHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        // Exact/suffix host matches rather than loose substring matches to avoid breaking media hosts.
        String[] blocked = {
                "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
                "popads.net", "popcash.net", "propellerads.com", "adsterra.com", "monetag.com",
                "exoclick.com", "trafficjunky.com", "adnxs.com", "taboola.com", "outbrain.com",
                "mgid.com", "adskeeper.co.uk", "hilltopads.net", "ad-maven.com", "clickadu.com",
                "juicyads.com", "onclickperformance.com", "rtmark.net", "phoangaisool.com",
                "vouphoanooque.net", "ptichoolsougn.net", "madurird.com", "epoackouxaik.com"
        };
        for (String name : blocked) if (h.equals(name) || h.endsWith("." + name)) return true;
        return false;
    }

    private void installTvNavigation() {
        if (webView == null || !isSiteUrl(webView.getUrl()) || navigationScript.isEmpty()) return;
        int zoom = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(PREF_ZOOM, 85);
        webView.evaluateJavascript("window.__PFTV_ZOOM=" + zoom + ";" + navigationScript, null);
    }

    private void evaluateTv(String script) {
        if (webView != null && isSiteUrl(webView.getUrl())) webView.evaluateJavascript(script, null);
    }

    private void sendNav(String direction) {
        if (webView == null) return;
        if (!isSiteUrl(webView.getUrl())) {
            if (direction.equals("DOWN")) webView.scrollBy(0, dp(280));
            if (direction.equals("UP")) webView.scrollBy(0, -dp(280));
            return;
        }
        webView.evaluateJavascript("window.PFTV&&window.PFTV.key('"+direction+"')", response -> {
            if ("null".equals(response) || "false".equals(response)) installTvNavigation();
        });
    }

    private void activateSelected() {
        if (webView == null) return;
        webView.evaluateJavascript("window.PFTV&&window.PFTV.key('OK')", result -> {
            if ("\"keyboard\"".equals(result)) {
                keyboardRequested = true;
                handler.postDelayed(() -> {
                    if (webView != null) {
                        webView.requestFocus();
                        InputMethodManager ime = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        ime.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 130L);
            }
        });
    }

    private void hideKeyboard() {
        keyboardRequested = false;
        InputMethodManager ime = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (webView != null) ime.hideSoftInputFromWindow(webView.getWindowToken(), 0);
        evaluateTv("window.PFTV&&window.PFTV.back()");
    }

    private void seekVideo(double seconds, boolean first) {
        if (webView == null) return;
        String n = String.format(Locale.US, "%.2f", seconds);
        webView.evaluateJavascript("window.PFTV&&window.PFTV.seek("+n+")", value -> {
            if ("\"ok\"".equals(value)) {
                if (first) showStatus((seconds >= 0 ? "+" : "") + Math.round(seconds) + "s • hold to accelerate");
            } else if ("\"pending\"".equals(value)) {
                if (first) showStatus("Checking embedded player seek support...");
            } else if (first) {
                showStatus("This player does not support TV remote seeking. Try a different source.");
            } else stopSeek();
        });
    }

    private void seekPercent(int percent) {
        if (webView == null) return;
        webView.evaluateJavascript("window.PFTV&&window.PFTV.seekPercent("+percent+")", result -> {
            if ("\"unsupported\"".equals(result) || "\"no-player\"".equals(result))
                showStatus("Seek unsupported on this source");
        });
    }

    private void jumpToTimeDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("01:00:00");
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("hh:mm:ss or mm:ss");
        int pad=dp(24);
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setPadding(pad,dp(8),pad,0);
        wrapper.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle("Jump to video time")
                .setMessage("Example: 01:15:30 goes to 1 hour, 15 minutes and 30 seconds.")
                .setView(wrapper)
                .setPositiveButton("Jump", (d,w)->{
                    try {
                        String[] fields=input.getText().toString().trim().split(":");
                        if(fields.length<1||fields.length>3)throw new NumberFormatException();
                        int seconds=0;
                        for(String f:fields) {
                            int value=Integer.parseInt(f.trim());
                            if(value<0)throw new NumberFormatException();
                            seconds=Math.addExact(Math.multiplyExact(seconds,60),value);
                        }
                        if(seconds>86400)throw new NumberFormatException();
                        if (webView != null) webView.evaluateJavascript("window.PFTV&&window.PFTV.seekTo("+seconds+")", result -> {
                            if ("\"unsupported\"".equals(result) || "\"no-player\"".equals(result))
                                showStatus("This source does not allow seeking to a time");
                        });
                    } catch (NumberFormatException | ArithmeticException ex) {
                        showStatus("Invalid time • use hh:mm:ss");
                    }
                }).setNegativeButton("Cancel",null).show();
    }

    private void showSeekMenu() {
        final int[] percents={10,25,50,75,90};
        final String[] choices={"Jump to 10%", "Jump to 25%", "Jump to 50% (halfway)",
            "Jump to 75%", "Jump to 90%", "Enter exact time..."};
        new AlertDialog.Builder(this).setTitle("Seek video • choose position")
                .setItems(choices,(d,i)->{
                    if(i==5)jumpToTimeDialog(); else seekPercent(percents[i]);
                }).setNegativeButton("Cancel",null).show();
    }

    // A click seeks once; holding accelerates smoothly without using remote repeat-count quirks.
    private void beginSeek(int dir) {
        stopSeek();
        seekDirection = dir;
        seekStartMs = android.os.SystemClock.uptimeMillis();
        seekVideo(dir * 15.0, true);
        handler.postDelayed(seekTask, 850L);
    }
    private void stopSeek() {
        seekDirection = 0;
        handler.removeCallbacks(seekTask);
    }

    private void handleArrow(int key) {
        if (keyboardRequested && (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_UP)) {
            hideKeyboard();
            sendNav(key == KeyEvent.KEYCODE_DPAD_DOWN ? "DOWN" : "UP");
            return;
        }
        if (keyboardRequested || webView == null) return;
        final String arrow = key == KeyEvent.KEYCODE_DPAD_UP ? "UP" :
                key == KeyEvent.KEYCODE_DPAD_DOWN ? "DOWN" :
                key == KeyEvent.KEYCODE_DPAD_LEFT ? "LEFT" : "RIGHT";
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (customVideoView != null) {
                if (key == KeyEvent.KEYCODE_DPAD_UP) showSeekMenu();
                return;
            }
            sendNav(arrow); return;
        }
        final int dir = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
        if (customVideoView != null) { beginSeek(dir); return; }
        webView.evaluateJavascript("window.PFTV&&window.PFTV.hasPlayer()", value -> {
            if ((dir > 0 ? rightHeld : leftHeld) && "true".equals(value)) {
                beginSeek(dir);
            } else if ((dir > 0 ? rightHeld : leftHeld)) {
                sendNav(arrow);
            }
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        final int code = event.getKeyCode();
        // Optional passthrough: let inaccessible iframe/native player controls receive remote keys.
        // Back/Menu/Info remain handled by this activity so the user can always exit this mode.
        if (passthroughPlayerKeys && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
                code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || code == KeyEvent.KEYCODE_MEDIA_REWIND ||
                code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            return super.dispatchKeyEvent(event);
        }
        boolean down = event.getAction()==KeyEvent.ACTION_DOWN;
        boolean up = event.getAction()==KeyEvent.ACTION_UP;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (down && !backHeld) {
                backHeld = true;
                backMenuShown = false;
                handler.postDelayed(longBackAction, 650L);
            } else if (up) {
                handler.removeCallbacks(longBackAction);
                if (backHeld && !backMenuShown && !event.isCanceled()) handleShortBack();
                backHeld = false;
                backMenuShown = false;
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO) {
            if (up) showNavigationMenu(); return true;
        }
        if (code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT ||
                code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down && event.getRepeatCount()==0) {
                if (code==KeyEvent.KEYCODE_DPAD_LEFT) leftHeld=true;
                if (code==KeyEvent.KEYCODE_DPAD_RIGHT) rightHeld=true;
                handleArrow(code);
            } else if (down && event.getRepeatCount() >= 4 && event.getRepeatCount()%4 == 0 &&
                    (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
                handleArrow(code);
            } else if (up && (code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (code==KeyEvent.KEYCODE_DPAD_LEFT) leftHeld=false;
                if (code==KeyEvent.KEYCODE_DPAD_RIGHT) rightHeld=false;
                stopSeek();
            }
            // Only intercept D-pad when not typing; let keyboard own its directional movement.
            return !keyboardRequested || code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN;
        }
        if (code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (keyboardRequested) return super.dispatchKeyEvent(event);
            if (down && !okHeld) {
                okHeld=true;
                okMenuShown=false;
                handler.postDelayed(longOkAction, 650L);
            } else if (up) {
                handler.removeCallbacks(longOkAction);
                if (okHeld && !okMenuShown && !event.isCanceled()) {
                    if (customVideoView!=null) evaluateTv("window.PFTV&&window.PFTV.togglePlayback()");
                    else activateSelected();
                }
                okHeld=false;
                okMenuShown=false;
            }
            return true;
        }
        if (code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || code==KeyEvent.KEYCODE_MEDIA_REWIND) {
            if (down && event.getRepeatCount()==0) beginSeek(code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?1:-1);
            if (up) stopSeek();
            return true;
        }
        if (code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code==KeyEvent.KEYCODE_MEDIA_PLAY || code==KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (down) evaluateTv("window.PFTV&&window.PFTV.togglePlayback()"); return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void exitFullScreenVideo() {
        if (customVideoView == null) return;
        root.removeView(customVideoView);
        customVideoView = null;
        WebChromeClient.CustomViewCallback callback = customVideoCallback;
        customVideoCallback = null;
        if (webView != null) { webView.setVisibility(View.VISIBLE); webView.requestFocus(); }
        if (callback != null) callback.onCustomViewHidden();
        applyImmersiveMode();
    }

    private void goHome() {
        passthroughPlayerKeys = false;
        stopSeek();
        if (customVideoView != null) exitFullScreenVideo();
        hideKeyboard();
        errorPanel.setVisibility(View.GONE);
        clearHistoryOnHome = true;
        rememberSiteUrl(HOME_URL);
        if (webView != null) webView.loadUrl(HOME_URL);
    }

    private void handleShortBack() {
        stopSeek();
        if (keyboardRequested) { hideKeyboard(); return; }
        if (customVideoView != null) { exitFullScreenVideo(); return; }
        if (webView != null && isSiteUrl(webView.getUrl())) {
            webView.evaluateJavascript("window.PFTV&&window.PFTV.back()", response -> {
                if ("true".equals(response)) return;
                if (webView.canGoBack()) webView.goBack();
                else showNavigationMenu();
            });
            return;
        }
        if (webView != null && webView.canGoBack()) { webView.goBack(); return; }
        showNavigationMenu();
    }

    private void showNavigationMenu() {
        stopSeek();
        final String[] options = {
                "TV grid / Browse", "Website view (sources, episodes & details)",
                "Go to PirateFlix Home", "Previous web page", "Play / Pause",
                "Seek / Jump to position", "Change video source", "Fullscreen current player",
                "Page zoom 40%–160%", "Player remote passthrough ON/OFF", "Reload website", "Exit app"
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PirateFlix TV • Controls")
                .setItems(options, (d, choice) -> {
                    if (choice == 0) {
                        passthroughPlayerKeys = false;
                        if (customVideoView != null) exitFullScreenVideo();
                        evaluateTv("window.PFTV&&window.PFTV.enterTV()");
                    }
                    if (choice == 1) evaluateTv("window.PFTV&&window.PFTV.enterWebsite()");
                    if (choice == 2) goHome();
                    if (choice == 3 && webView != null && webView.canGoBack()) webView.goBack();
                    if (choice == 4) evaluateTv("window.PFTV&&window.PFTV.togglePlayback()");
                    if (choice == 5) showSeekMenu();
                    if (choice == 6) {
                        if (customVideoView != null) exitFullScreenVideo();
                        evaluateTv("window.PFTV&&window.PFTV.chooseSources()");
                    }
                    if (choice == 7) evaluateTv("window.PFTV&&window.PFTV.cinema(true)");
                    if (choice == 8) showZoomMenu();
                    if (choice == 9) {
                        passthroughPlayerKeys = !passthroughPlayerKeys;
                        if (passthroughPlayerKeys) {
                            if (webView != null) webView.requestFocus();
                            evaluateTv("window.PFTV&&window.PFTV.focusPlayer()");
                            showStatus("Direct player keys ON · Menu to turn off");
                        } else showStatus("TV navigation keys restored");
                    }
                    if (choice == 10 && webView != null) { errorPanel.setVisibility(View.GONE); webView.reload(); }
                    if (choice == 11) finish();
                })
                .setNegativeButton("Cancel", null).create();
        dialog.setOnDismissListener(d -> applyImmersiveMode());
        dialog.show();
    }

    private void showZoomMenu() {
        final int[] values = {40, 50, 60, 70, 80, 85, 90, 100, 115, 125, 140, 160};
        final String[] labels = {"40% (very small)", "50%", "60%", "70%", "80%", "85% (default)",
                "90%", "100% (original)", "115%", "125%", "140%", "160% (large)"};
        int saved = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(PREF_ZOOM, 85);
        int checked = 5;
        for (int i=0;i<values.length;i++) if (saved==values[i]) checked=i;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Website zoom (TV grid retains readable cards)")
                .setSingleChoiceItems(labels, checked, (d, i) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREF_ZOOM,values[i]).apply();
                    evaluateTv("window.PFTV&&window.PFTV.zoom("+values[i]+")");
                    showStatus("Website zoom: " + values[i] + "%");
                    d.dismiss();
                }).setNegativeButton("Cancel", null).create();
        dialog.setOnDismissListener(d -> applyImmersiveMode());
        dialog.show();
    }

    @Override @Deprecated public void onBackPressed() { handleShortBack(); }
    private void openExternal(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("mailto") || scheme.equalsIgnoreCase("tel") || scheme.equalsIgnoreCase("market"))) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try { startActivity(intent); } catch (ActivityNotFoundException | SecurityException ignored) { }
    }
    private static boolean isWebUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }
    private static boolean isSiteUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        return "https".equalsIgnoreCase(uri.getScheme()) && "pirate-flix.pages.dev".equalsIgnoreCase(uri.getHost());
    }
    private static boolean isHomeUrl(String url) {
        return HOME_URL.equals(url) || "https://pirate-flix.pages.dev".equals(url);
    }
    private void rememberSiteUrl(String url) {
        if (!isSiteUrl(url)) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!url.equals(prefs.getString(LAST_URL, ""))) prefs.edit().putString(LAST_URL, url).apply();
    }
    private String getSavedSiteUrl() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(LAST_URL, HOME_URL);
        return isSiteUrl(saved) ? saved : HOME_URL;
    }
    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        handler.removeCallbacks(saveUrlTask);
        handler.postDelayed(saveUrlTask, URL_CHECK_MS);
        applyImmersiveMode();
    }
    @Override protected void onPause() {
        stopSeek();
        handler.removeCallbacks(saveUrlTask);
        if (webView != null) { rememberSiteUrl(webView.getUrl()); webView.onPause(); }
        CookieManager.getInstance().flush();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (webView != null) webView.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }
    @Override protected void onDestroy() {
        stopSeek();
        handler.removeCallbacks(saveUrlTask);
        handler.removeCallbacks(longBackAction);
        handler.removeCallbacks(longOkAction);
        if (hideStatus != null) handler.removeCallbacks(hideStatus);
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (customVideoView != null) exitFullScreenVideo();
        if (webView != null) { root.removeView(webView); webView.stopLoading(); webView.destroy(); webView=null; }
        super.onDestroy();
    }
}
