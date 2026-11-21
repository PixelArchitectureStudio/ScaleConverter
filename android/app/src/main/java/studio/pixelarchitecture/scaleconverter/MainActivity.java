package studio.pixelarchitecture.scaleconverter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Collections;
import org.json.JSONTokener;

public class MainActivity extends Activity {
    private static final String BASE_URL = "https://pixelarchitecturestudio.github.io/ScaleConverter/";
    private static final String PATH = "/ScaleConverter/";
    private static final String SNAPSHOT =
        "JSON.stringify({fields:Array.from(document.querySelectorAll('input[id],select[id],textarea[id]'))"
        + ".map(e=>({id:e.id,value:e.value,checked:e.checked})),y:scrollY})";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private FrameLayout root;
    private ScrollView splash;
    private ImageView splashIcon;
    private TextView splashText;
    private TextView banner;
    private volatile String currentUrl;
    private String savedState = "null";
    private int loadId;
    private boolean pageReady;
    private boolean saving;
    private boolean destroyed;
    private Boolean lastConnection;

    private final Runnable timeout = () -> {
        if (!pageReady && currentUrl != null && !isOfflineDocument(Uri.parse(currentUrl))) {
            loadPage(true);
        }
    };
    private final Runnable checkConnection = () -> onConnectionChanged(false);
    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(checkConnection);
            handler.postDelayed(checkConnection, 450);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        setContentView(root);
        Typeface typeface;
        try {
            typeface = Typeface.createFromAsset(getAssets(), "Vazirmatn-Regular.ttf");
        } catch (RuntimeException e) {
            typeface = Typeface.DEFAULT;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        banner = persianText("ارتباط با سایت برقرار نیست. نسخهٔ آفلاین فعال است.", 12, typeface);
        banner.setTextColor(Color.WHITE);
        banner.setBackgroundColor(Color.rgb(65, 65, 65));
        banner.setPadding(dp(16), dp(8), dp(16), dp(8));
        banner.setVisibility(View.GONE);
        content.addView(banner, new LinearLayout.LayoutParams(-1, -2));

        web = new WebView(this);
        content.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDefaultTextEncodingName("UTF-8");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new ConverterClient());
        web.setDownloadListener((url, agent, disposition, mime, length) -> openExternal(Uri.parse(url)));

        createSplash(typeface);
        applyAppearance(getResources().getConfiguration());
        lastConnection = isConnected();
        loadPage(!lastConnection);
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(networkReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(networkReceiver, filter);
        }
    }

    private void createSplash(Typeface typeface) {
        splash = new ScrollView(this);
        splash.setFillViewport(true);
        splash.setVerticalScrollBarEnabled(false);
        splash.setClickable(true);
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER);
        group.setPadding(dp(24), dp(16), dp(24), dp(16));
        splash.addView(group, new ScrollView.LayoutParams(-1, -2));

        splashIcon = new ImageView(this);
        splashIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        group.addView(splashIcon, new LinearLayout.LayoutParams(dp(160), dp(160)));
        ProgressBar spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinnerSize = new LinearLayout.LayoutParams(dp(24), dp(24));
        spinnerSize.setMargins(0, dp(12), 0, dp(10));
        group.addView(spinner, spinnerSize);
        splashText = persianText("در حال بارگذاری…", 14, typeface);
        group.addView(splashText, new LinearLayout.LayoutParams(-1, -2));
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        splash.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop,
                oldRight, oldBottom) -> {
            int size = Math.min(dp(160), Math.min((bottom - top) / 3, right - left - dp(48)));
            if (size > 0 && size != splashIcon.getLayoutParams().width) {
                splashIcon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            }
        });
    }

    private TextView persianText(String value, int size, Typeface typeface) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setGravity(Gravity.CENTER);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        label.setTextSize(size);
        label.setTypeface(typeface);
        return label;
    }

    private void applyAppearance(Configuration config) {
        boolean dark = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int background = dark ? 0xFF0E0D09 : 0xFFFEFEFE;
        root.setBackgroundColor(background);
        splash.setBackgroundColor(background);
        splashText.setTextColor(dark ? Color.LTGRAY : Color.DKGRAY);
        try (InputStream image = getAssets().open(dark ? "splash/dark.png" : "splash/light.png")) {
            splashIcon.setImageDrawable(Drawable.createFromStream(image, null));
        } catch (IOException ignored) {
            splashIcon.setImageResource(R.drawable.icon);
        }
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(dark || Build.VERSION.SDK_INT < 26 ? Color.BLACK : background);
        int flags = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyAppearance(configuration);
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    @Override protected void onResume() {
        super.onResume();
        onConnectionChanged(true);
    }

    private boolean isConnected() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void onConnectionChanged(boolean resume) {
        if (destroyed || currentUrl == null) return;
        boolean connected = isConnected();
        boolean changed = lastConnection == null || lastConnection != connected;
        lastConnection = connected;
        boolean offline = isOfflineDocument(Uri.parse(currentUrl));
        if (!connected && !offline || connected && offline && (changed || resume)) {
            switchPage(!connected);
        }
    }

    private void switchPage(boolean offline) {
        if (!pageReady) {
            loadPage(offline);
        } else if (!saving) {
            saving = true;
            web.evaluateJavascript(SNAPSHOT, result -> {
                if (destroyed) return;
                try {
                    Object value = new JSONTokener(result).nextValue();
                    if (value instanceof String) savedState = (String) value;
                } catch (Exception ignored) {
                    savedState = "null";
                }
                saving = false;
                loadPage(offline);
                if (isConnected() == offline) handler.post(checkConnection);
            });
        }
    }

    private void loadPage(boolean offline) {
        handler.removeCallbacks(timeout);
        pageReady = false;
        currentUrl = BASE_URL + (offline ? "index.html?app_offline=" : "?app_session=") + (++loadId);
        web.stopLoading();
        web.loadUrl(currentUrl, Collections.singletonMap("Cache-Control", "no-cache"));
        if (!offline) handler.postDelayed(timeout, 9000);
    }

    private boolean isLocalProject(Uri uri) {
        String path = uri.getPath();
        return "https".equals(uri.getScheme())
            && "pixelarchitecturestudio.github.io".equals(uri.getHost())
            && uri.getPort() == -1 && path != null && path.startsWith(PATH);
    }

    private boolean isOfflineDocument(Uri uri) {
        return isLocalProject(uri) && uri.getQueryParameter("app_offline") != null;
    }

    private void openExternal(Uri uri) {
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "مرورگری برای باز کردن لینک پیدا نشد.", Toast.LENGTH_SHORT).show();
        }
    }

    private class ConverterClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (TextUtils.equals(currentUrl, url)) return false;
            openExternal(Uri.parse(url));
            return true;
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!isLocalProject(uri)) return null;
            String activeUrl = currentUrl;
            if (!isOfflineDocument(uri) && (request.isForMainFrame() || activeUrl == null
                    || !isOfflineDocument(Uri.parse(activeUrl)))) return null;
            String name = uri.getPath().substring(PATH.length());
            if (!name.matches("[A-Za-z0-9._-]+") || name.startsWith(".")) return missingAsset();
            try {
                String mime = name.endsWith(".ttf") ? "font/ttf" : URLConnection.guessContentTypeFromName(name);
                if (name.endsWith(".css")) mime = "text/css";
                if (name.endsWith(".svg")) mime = "image/svg+xml";
                InputStream stream = getAssets().open("offline/" + name);
                return new WebResourceResponse(mime == null ? "application/octet-stream" : mime, "UTF-8", stream);
            } catch (IOException e) {
                return missingAsset();
            }
        }

        private WebResourceResponse missingAsset() {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            fallback(request);
        }

        @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse error) {
            fallback(request);
        }

        private void fallback(WebResourceRequest request) {
            if (request.isForMainFrame() && TextUtils.equals(currentUrl, request.getUrl().toString())
                && !isOfflineDocument(request.getUrl())) loadPage(true);
        }

        @Override public void onPageFinished(WebView view, String url) {
            if (destroyed || !TextUtils.equals(currentUrl, url)) return;
            handler.removeCallbacks(timeout);
            banner.setVisibility(isOfflineDocument(Uri.parse(url)) ? View.VISIBLE : View.GONE);
            String restore = "(function(){"
                + "var m=document.querySelector('meta[name=viewport]');"
                + "if(m)m.content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no';"
                + "var s=" + savedState + ";if(!s)return;"
                + "for(var f of s.fields){var e=document.getElementById(f.id);if(!e)continue;"
                + "e.value=f.value;if(typeof f.checked==='boolean')e.checked=f.checked;"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "requestAnimationFrame(function(){scrollTo(0,s.y||0)});})();";
            view.evaluateJavascript(restore, result -> {
                if (destroyed || !TextUtils.equals(currentUrl, url)) return;
                savedState = "null";
                pageReady = true;
                view.clearHistory();
                splash.setVisibility(View.GONE);
                onConnectionChanged(false);
            });
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(networkReceiver);
        ((LinearLayout) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
