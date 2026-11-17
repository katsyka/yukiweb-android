package lol.yukichat.web;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.TranslationsController;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static lol.yukichat.web.Ui.*;

public class MainActivity extends Activity {
    static final String ASSETS = "resource://android/assets/";
    static final String NEWTAB = ASSETS + "home.html";
    static final String SIGNIN = "https://account.yukichat.lol/yukiweb";
    // Same @ shortcuts as the PC address bar.
    static final String[][] ROUTES = {
            {"chat", "Yuki Chat", "https://yukichat.lol"}, {"music", "YukiMusic", "https://music.yukichat.lol"},
            {"mail", "YukiMail", "https://mail.yukichat.lol"}, {"meet", "Yuki Meet", "https://meet.yukichat.lol"},
            {"wallet", "Yuki Wallet", "https://wallet.yukichat.lol"}, {"pages", "Yuki Pages", "https://pages.yukichat.lol"},
            {"write", "Yuki Write", "https://write.yukichat.lol"}, {"board", "Yuki Board", "https://board.yukichat.lol"},
            {"chain", "YukiChain", "https://chain.yukichat.lol"}, {"hub", "Yuki Hub", "https://yuki.yukichat.lol"},
            {"search", "Yuki Search", "https://search.yukichat.lol"}, {"maps", "Yuki Maps", "https://maps.yukichat.lol"},
            {"docs", "Yuki Docs", "https://docs.yukichat.lol"}, {"id", "Yuki ID", "https://account.yukichat.lol"},
            {"vinyl", "YukiVinyl", "https://vinyl.yukichat.lol"}, {"loader", "YukiLoader", "https://loader.yukichat.lol"},
            {"html", "HTML playground", ASSETS + "html.html"}, {"f1", "#22 pixel race", ASSETS + "f1.html"},
    };
    static final int[] ROUTE_ICONS = {R.drawable.i_chat, R.drawable.i_music, R.drawable.i_mail, R.drawable.i_meet, R.drawable.i_wallet,
            R.drawable.i_pages, R.drawable.i_write, R.drawable.i_board, R.drawable.i_chain, R.drawable.i_hub, R.drawable.i_search,
            R.drawable.i_globe, R.drawable.i_file, R.drawable.i_user, R.drawable.i_music, R.drawable.i_download, R.drawable.i_html,
            R.drawable.i_sparkles};
    static final String[] PAGES = {"settings", "history", "bookmarks", "downloads"};
    private static final int REQ_FILE = 7, REQ_PERM = 8, REQ_ROLE = 9, REQ_UNLOCK = 10;

    private static GeckoRuntime runtime;
    static Logins logins;
    private final Handler ui = new Handler(Looper.getMainLooper());
    Store store;
    Sync sync;
    private Screens screens;
    private PullView view;
    private EditText url, findText;
    private TextView tabsBtn, findCount;
    private ProgressBar progress;
    private LinearLayout bar, findBar, appBar;
    private FrameLayout pullDot;
    private ImageView pullIcon;
    private ListView sugg;
    private LinearLayout suggWrap, pageCard;
    private TextView ghost;
    private ImageView homeBtn, clearBtn;
    private View[] barTools;
    private android.widget.AbsListView tabList;
    private View tabsPanel;
    private final List<Tab> tabs = new ArrayList<>();
    private Tab cur;
    private boolean editing, completing, fullscreen;
    private String ssoState = "";
    private GeckoSession.PromptDelegate.FilePrompt filePrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> fileResult;
    private GeckoSession.PermissionDelegate.Callback permCallback;
    private final Runnable tick = new Runnable() {
        @Override public void run() { sync.now(); ui.postDelayed(this, 3 * 60_000); }
    };

    /** A tab; its GeckoSession is created when first shown, so restored tabs cost nothing. */
    final class Tab {
        String url, title = "", group = "";
        GeckoSession s;
        boolean priv, back, fwd, desktop, translated, app;
        android.graphics.Bitmap thumb;
        JSONObject manifest;
        String docLang, state;
        Tab opener;
        int progress = 100;
        Tab(String url, boolean priv) { this.url = url; this.priv = priv; }
        String label() {
            if (isNew(url)) return "Home";
            return title.isEmpty() ? host(url) : title;
        }
    }

    // ---------------------------------------------------------------- setup

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new Store(this);
        L.lang = store.lang();
        sync = new Sync(store, new Sync.Listener() {
            @Override public void applied(java.util.Set<String> keys) {
                if (keys.contains("theme")) restyle(false);
                if (keys.contains("lang")) { L.lang = store.lang(); refreshHome(); }
            }
            @Override public void dash(boolean changed) { onDash(changed); }
            @Override public void status(String t) { screens.refreshIf(Screens.SETTINGS); }
            @Override public void signedOut() { store.setAccount("", null); screens.refreshIf(Screens.SETTINGS); }
        });
        if (runtime == null) {
            runtime = GeckoRuntime.create(getApplicationContext(), new GeckoRuntimeSettings.Builder()
                    .aboutConfigEnabled(true)
                    .loginAutofillEnabled(true)
                    .build());
        }
        if (logins == null) {
            logins = new Logins(getApplicationContext());
            runtime.setAutocompleteStorageDelegate(logins);
        }
        theme();
        buildUi();
        if (appUrl() != null) {
            // Home-screen web app: one tab, no address bar (buildUi hides it), nothing saved to the browser's session.
            Tab t = new Tab(appUrl(), false);
            t.app = true;
            tabs.add(t);
            select(t);
            return;
        }
        restore();
        handle(getIntent());
        Updater.check(this, false);
    }

    private int dp(float v) { return Ui.dp(this, v); }

    /** The web app's address when running as a home-screen app (WebAppActivity), else null. */
    String appUrl() { return null; }

    /** Picks the palette from the settings; true if it changed. */
    private boolean theme() {
        JSONObject t = store.themeObj();
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String name = store.themeSystem() ? (night ? "dark" : "light") : t.optString("name", "light");
        return Ui.apply(name, t.optString("acc", null));
    }

    /** Re-applies the look: rebuilds the browser chrome around the same tabs and repaints home pages. */
    void restyle(boolean reopenAppearance) {
        if (!theme()) { if (reopenAppearance) screens.refresh(); return; }
        view.releaseSession();
        buildUi();
        if (cur != null && cur.s != null) view.setSession(cur.s);
        showUrl();
        updateTabsBtn();
        for (Tab t : tabs) if (t.s != null && isNew(t.url)) { t.url = homeUrl(); t.s.loadUri(t.url); }
        if (reopenAppearance) { screens.settings(false); screens.appearance(false); }
    }

    private void bars() {
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        w.getDecorView().setSystemUiVisibility(DARK ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (store.themeSystem()) restyle(screens.open());
    }

    private void buildUi() {
        FrameLayout shell = new FrameLayout(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(6), dp(4), dp(6));
        homeBtn = iconButton(this, R.drawable.i_home, 44, v -> { if (editing) stopEditing(); else home(); });
        bar.addView(homeBtn);
        url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("Search or type @");
        url.setTextSize(15);
        url.setTextColor(TEXT);
        url.setHintTextColor(DIM);
        url.setSelectAllOnFocus(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_GO);
        url.setBackground(round(this, SOFT, 22));
        url.setPadding(dp(14), 0, dp(14), 0);
        url.setCompoundDrawablePadding(dp(10));
        url.setCompoundDrawableTintList(ColorStateList.valueOf(DIM));
        // The field, the rest of the history match drawn after the typed text (never inserted into it, so the
        // keyboard's composing word can't double up), and a clear button that has its own space at the end.
        FrameLayout field = new FrameLayout(this);
        field.addView(url, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        ghost = new TextView(this);
        ghost.setSingleLine(true);
        ghost.setTextSize(15);
        ghost.setTextColor(DIM);
        ghost.setGravity(Gravity.CENTER_VERTICAL);
        ghost.setVisibility(View.GONE);
        ghost.setOnClickListener(v -> acceptGhost());
        field.addView(ghost, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        clearBtn = iconButton(this, R.drawable.i_x, 40, v -> { url.setText(""); url.requestFocus(); });
        clearBtn.setVisibility(View.GONE);
        field.addView(clearBtn, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END | Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0, dp(44), 1);
        up.setMargins(dp(2), 0, dp(4), 0);
        bar.addView(field, up);
        url.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeGhost());

        View plusBtn = iconButton(this, R.drawable.i_plus, 44, v -> newTab(null, false, true));
        bar.addView(plusBtn);
        FrameLayout tabsWrap = new FrameLayout(this);
        tabsWrap.setBackground(press(this, 0, 22));
        tabsBtn = text(this, "1", 12, TEXT, true);
        tabsBtn.setGravity(Gravity.CENTER);
        GradientDrawable box = round(this, Color.TRANSPARENT, 6);
        box.setStroke(dp(2), TEXT);
        tabsBtn.setBackground(box);
        tabsWrap.addView(tabsBtn, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        tabsWrap.setOnClickListener(v -> showTabs(tabsPanel.getVisibility() != View.VISIBLE));
        bar.addView(tabsWrap, new LinearLayout.LayoutParams(dp(44), dp(44)));
        View dotsBtn = iconButton(this, R.drawable.i_dots, 44, v -> showMenu());
        bar.addView(dotsBtn);
        barTools = new View[]{plusBtn, tabsWrap, dotsBtn};
        root.addView(bar);
        if (appUrl() != null) bar.setVisibility(View.GONE);

        findBar = buildFindBar();
        findBar.setVisibility(View.GONE);
        root.addView(findBar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(ACC));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(BG));
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout content = new FrameLayout(this);
        view = new PullView(this);
        content.addView(view);
        content.addView(buildPullDot());
        suggWrap = new LinearLayout(this);
        suggWrap.setOrientation(LinearLayout.VERTICAL);
        suggWrap.setBackgroundColor(BG);
        suggWrap.setClickable(true);
        suggWrap.setVisibility(View.GONE);
        pageCard = new LinearLayout(this);
        pageCard.setOrientation(LinearLayout.VERTICAL);
        suggWrap.addView(pageCard);
        sugg = new ListView(this);
        sugg.setDivider(null);
        suggWrap.addView(sugg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(suggWrap);
        appBar = new LinearLayout(this);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(14), dp(8), dp(4), dp(8));
        appBar.setBackground(round(this, PAGE, 22));
        appBar.setElevation(dp(6));
        appBar.setClickable(true);
        appBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams abp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        abp.setMargins(dp(10), 0, dp(10), dp(12));
        content.addView(appBar, abp);
        tabsPanel = buildTabsPanel();
        tabsPanel.setVisibility(View.GONE);
        content.addView(tabsPanel);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(root);
        screens = new Screens(this, shell);
        setContentView(shell);
        bars();

        url.setOnFocusChangeListener((v, has) -> {
            editing = has;
            if (has) {
                // Full address while editing, just the site otherwise.
                completing = true;
                url.setText(cur == null || isNew(cur.url) ? "" : shown(cur.url, true));
                completing = false;
                url.post(url::selectAll);
                barIcon();
                editMode(true);
                // The bar re-lays out as it widens, which swallows the tap's own keyboard request: ask again.
                keyboard(url, true);
                suggest();
            } else { ghost(null); editMode(false); showUrl(); }
        });
        url.setOnEditorActionListener((v, id, e) -> { go(url.getText().toString() + (ghostRest == null ? "" : ghostRest)); return true; });
        // Long-press the bar while browsing: copy the link, like Chrome.
        url.setOnLongClickListener(v -> {
            if (editing || cur == null || isNew(cur.url)) return false;
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            copy(cur.url);
            return true;
        });
        url.addTextChangedListener(new TextWatcher() {
            int added;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { added = count - before; }
            @Override public void afterTextChanged(Editable s) {
                if (completing || !editing) return;
                if (added > 0) complete(); else ghost(null);
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                suggest();
            }
        });
        url.setOnTouchListener(new SwipeTabs());
    }

    /** Swiping the address bar sideways switches to the next / previous tab, like Chrome. */
    private final class SwipeTabs implements View.OnTouchListener {
        private final int slop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
        private float x0, y0;
        private boolean swiping;

        @Override public boolean onTouch(View v, MotionEvent e) {
            if (editing) return false;
            float dx = e.getRawX() - x0;
            int i = tabs.indexOf(cur);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = e.getRawX();
                    y0 = e.getRawY();
                    swiping = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!swiping && tabs.size() > 1 && Math.abs(dx) > 2 * slop && Math.abs(dx) > 2 * Math.abs(e.getRawY() - y0)) {
                        swiping = true;
                        MotionEvent c = MotionEvent.obtain(e);
                        c.setAction(MotionEvent.ACTION_CANCEL);
                        url.onTouchEvent(c);
                        c.recycle();
                    }
                    if (!swiping) return false;
                    boolean edge = (dx > 0 && i == 0) || (dx < 0 && i == tabs.size() - 1);
                    url.setTranslationX(edge ? dx / 4 : dx);
                    url.setAlpha(1 - Math.min(0.6f, Math.abs(dx) / url.getWidth()));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!swiping) return false;
                    swiping = false;
                    int to = dx < 0 ? i + 1 : i - 1;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(dx) > url.getWidth() / 4f && to >= 0 && to < tabs.size()) {
                        float w = url.getWidth(), dir = Math.signum(dx);
                        url.animate().translationX(dir * w).alpha(0).setDuration(110).withEndAction(() -> {
                            select(tabs.get(to));
                            url.setTranslationX(-dir * w / 2);
                            url.animate().translationX(0).alpha(1).setDuration(170).start();
                        }).start();
                    } else url.animate().translationX(0).alpha(1).setDuration(150).start();
                    return true;
            }
            return false;
        }
    }

    /** Pull to refresh: a round badge follows the finger; past the line it turns yellow and reloads on release. */
    private View buildPullDot() {
        pullDot = new FrameLayout(this);
        pullDot.setBackground(round(this, BG, 22));
        pullDot.setElevation(dp(6));
        pullIcon = icon(this, R.drawable.i_reload, TEXT, 22);
        pullDot.addView(pullIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pullDot.setLayoutParams(lp);
        float hidden = -dp(56), arm = dp(76);
        pullDot.setTranslationY(hidden);
        pullDot.setAlpha(0);
        view.listener = new PullView.Listener() {
            boolean armed;
            @Override public void pull(float dy) {
                float d = Math.min(dy * 0.5f, dp(110));
                pullDot.animate().cancel();
                pullDot.setTranslationY(hidden + d);
                pullDot.setAlpha(Math.min(1, d / dp(40)));
                pullIcon.setRotation(dy * 0.7f);
                boolean a = d >= arm;
                if (a != armed) {
                    armed = a;
                    pullDot.setBackground(round(MainActivity.this, a ? ACC : BG, 22));
                    pullIcon.setImageTintList(ColorStateList.valueOf(a ? ON_ACC : TEXT));
                    if (a) pullDot.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                }
            }
            @Override public void release(boolean commit) {
                boolean go = commit && armed && cur != null && cur.s != null;
                armed = false;
                if (go) {
                    cur.s.reload();
                    pullDot.animate().translationY(dp(20)).setDuration(120).start();
                    pullIcon.animate().rotationBy(360).setDuration(450).withEndAction(this::hide).start();
                } else hide();
            }
            void hide() {
                pullDot.animate().translationY(hidden).alpha(0).setDuration(180)
                        .withEndAction(() -> { pullDot.setBackground(round(MainActivity.this, BG, 22)); pullIcon.setImageTintList(ColorStateList.valueOf(TEXT)); }).start();
            }
        };
        return pullDot;
    }

    private LinearLayout buildFindBar() {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(8), dp(6), dp(4), dp(6));
        findText = new EditText(this);
        findText.setSingleLine(true);
        findText.setHint("Find in page");
        findText.setTextSize(15);
        findText.setTextColor(TEXT);
        findText.setHintTextColor(DIM);
        findText.setBackground(round(this, SOFT, 22));
        findText.setPadding(dp(16), 0, dp(16), 0);
        findText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        f.addView(findText, new LinearLayout.LayoutParams(0, dp(44), 1));
        findCount = text(this, "", 13, DIM, false);
        findCount.setPadding(dp(10), 0, dp(4), 0);
        f.addView(findCount);
        f.addView(iconButton(this, R.drawable.i_up, 44, v -> findNext(true)));
        f.addView(iconButton(this, R.drawable.i_down, 44, v -> findNext(false)));
        f.addView(iconButton(this, R.drawable.i_x, 44, v -> find(false)));
        findText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (cur == null || cur.s == null) return;
                if (s.length() == 0) { cur.s.getFinder().clear(); findCount.setText(""); return; }
                cur.s.getFinder().find(s.toString(), 0).accept(MainActivity.this::found);
            }
        });
        findText.setOnEditorActionListener((v, id, e) -> { findNext(false); return true; });
        return f;
    }

    private void found(GeckoSession.FinderResult r) {
        if (r != null) findCount.setText(r.total == 0 ? "0/0" : r.current + "/" + r.total);
    }

    private void findNext(boolean back) {
        if (cur == null || cur.s == null || findText.length() == 0) return;
        cur.s.getFinder().find(null, back ? GeckoSession.FINDER_FIND_BACKWARDS : 0).accept(this::found);
    }

    private void find(boolean on) {
        bar.setVisibility(on ? View.GONE : View.VISIBLE);
        findBar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            findText.setText("");
            findCount.setText("");
            findText.requestFocus();
            keyboard(findText, true);
        } else {
            if (cur != null && cur.s != null) cur.s.getFinder().clear();
            keyboard(findText, false);
        }
    }

    // ---------------------------------------------------------------- tabs

    private static boolean isNew(String u) { return u == null || u.isEmpty() || u.startsWith(NEWTAB) || u.startsWith(ASSETS + "newtab.html"); }

    static String host(String u) {
        try { String h = Uri.parse(u).getHost(); return h == null ? "" : h; } catch (Exception e) { return ""; }
    }

    private static String bare(String u) {
        return u.replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceFirst("/$", "");
    }

    /** The home page with the most visited sites handed over in the hash. */
    private String homeUrl() {
        JSONObject h = new JSONObject();
        try {
            JSONObject p = store.profile();
            String name = p.optString("display_name", p.optString("username")).trim();
            JSONObject dash = dashForHome();
            if (dash != null) h.put("dash", dash);
            h.put("lang", store.lang());
            h.put("top", store.topSites(8)).put("name", store.token().isEmpty() ? "" : name.split("\\s+")[0]).put("dark", DARK)
                    .put("c", new JSONObject().put("bg", hex(BG)).put("text", hex(TEXT)).put("dim", hex(DIM)).put("soft", hex(SOFT))
                            .put("soft2", hex(SOFT2)).put("acc", hex(ACC)).put("onacc", hex(ON_ACC)).put("accsoft", hex(ACC_SOFT)).put("acctext", hex(ACC_TEXT)));
        } catch (Exception ignored) {}
        return NEWTAB + "#" + Uri.encode(h.toString());
    }

    /** The dashboard as the home page needs it: tabs sent by other devices, a few chats and songs. */
    private JSONObject dashForHome() {
        if (store.token().isEmpty() || store.dash().isEmpty()) return null;
        try {
            JSONObject d = new JSONObject(store.dash()), out = new JSONObject();
            JSONArray inbox = new JSONArray(), in = d.optJSONArray("inbox"), chats = d.optJSONArray("chats"), ch = new JSONArray();
            if (in != null) for (int i = 0; i < in.length(); i++) {
                JSONObject x = in.getJSONObject(i);
                if (!x.optString("from_dev").equals(store.dev())) inbox.put(new JSONObject().put("id", x.optLong("id")).put("title", x.optString("title")).put("url", x.optString("url")));
            }
            if (chats != null) for (int i = 0; i < Math.min(3, chats.length()); i++) ch.put(chats.get(i));
            out.put("inbox", inbox).put("chats", ch);
            if (d.optJSONObject("mail") != null) out.put("mail", d.getJSONObject("mail"));
            JSONObject m = d.optJSONObject("music");
            if (m != null) {
                JSONArray r = m.optJSONArray("recent"), rr = new JSONArray();
                if (r != null) for (int i = 0; i < Math.min(7, r.length()); i++) rr.put(r.get(i));
                out.put("music", new JSONObject().put("playing", m.opt("playing")).put("recent", rr));
            }
            return out;
        } catch (Exception e) { return null; }
    }

    /** Fresh dashboard: redraw home if it's showing, and offer tabs just sent from the PC. */
    private void onDash(boolean changed) {
        if (changed) refreshHome();
        try {
            JSONArray in = new JSONObject(store.dash()).optJSONArray("inbox");
            if (in == null) return;
            for (int i = 0; i < in.length(); i++) {
                JSONObject x = in.getJSONObject(i);
                long id = x.optLong("id");
                if (x.optString("from_dev").equals(store.dev()) || store.inboxSeen(id)) continue;
                store.setInboxSeen(id);
                Ui.dialog(this).setTitle(L.t("Tab from your PC")).setMessage(x.optString("title", x.optString("url")))
                        .setPositiveButton(L.t("Open"), (dl, w) -> openInbox(id)).setNegativeButton(L.t("Later"), null).show();
                break;
            }
        } catch (Exception ignored) {}
    }

    private void refreshHome() {
        if (cur != null && cur.s != null && isNew(cur.url)) cur.s.loadUri(homeUrl());
    }

    private void openInbox(long id) {
        try {
            JSONObject d = new JSONObject(store.dash());
            JSONArray in = d.optJSONArray("inbox"), keep = new JSONArray();
            String u = null;
            if (in != null) for (int i = 0; i < in.length(); i++) {
                JSONObject x = in.getJSONObject(i);
                if (x.optLong("id") == id) u = x.optString("url"); else keep.put(x);
            }
            if (u == null || !u.matches("(?i)^https?://.*")) return;
            store.setDash(d.put("inbox", keep).toString());
            if (cur != null && isNew(cur.url)) load(u); else newTab(u, false, true);
            String token = store.token();
            new Thread(() -> {
                try (Response r = new OkHttpClient().newCall(new Request.Builder().url(BuildConfig.API + "/api/yukiweb/inbox/done")
                        .header("Authorization", "Bearer " + token)
                        .post(RequestBody.create(new JSONObject().put("ids", new JSONArray().put(id)).toString(), okhttp3.MediaType.get("application/json"))).build()).execute()) {
                    r.code();
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception ignored) {}
    }

    /** "Send to PC": the PC shows it on its avatar badge (and so would other phones). */
    private void sendTab() {
        if (cur == null || cur.url == null || !cur.url.matches("(?i)^https?://.*")) { toast(L.t("Open a page first")); return; }
        if (store.token().isEmpty()) { toast(L.t("Sign in first")); return; }
        String token = store.token(), u = cur.url, title = cur.title == null ? "" : cur.title, dev = store.dev();
        new Thread(() -> {
            boolean ok = false;
            try (Response r = new OkHttpClient().newCall(new Request.Builder().url(BuildConfig.API + "/api/yukiweb/send")
                    .header("Authorization", "Bearer " + token)
                    .post(RequestBody.create(new JSONObject().put("url", u).put("title", title).put("dev", dev).toString(), okhttp3.MediaType.get("application/json"))).build()).execute()) {
                ok = r.isSuccessful();
            } catch (Exception ignored) {}
            boolean done = ok;
            ui.post(() -> toast(L.t(done ? "Sent to your PC" : "Couldn't send")));
        }).start();
    }

    private String ua(boolean desktop) {
        if (desktop) return null;
        String s = GeckoSession.getDefaultUserAgent() + " YukiWeb/" + BuildConfig.VERSION_NAME;
        return store.token().isEmpty() ? s : s + " YukiID";
    }

    private void open(Tab t) {
        GeckoSessionSettings.Builder b = new GeckoSessionSettings.Builder().usePrivateMode(t.priv);
        if (t.app) b.displayMode(GeckoSessionSettings.DISPLAY_MODE_STANDALONE);
        if (t.desktop) b.userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP).viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP);
        else b.userAgentOverride(ua(false));
        t.s = new GeckoSession(b.build());
        hook(t);
        t.s.open(runtime);
    }

    Tab newTab(String u, boolean priv, boolean select) {
        Tab t = new Tab(u == null || u.isEmpty() ? homeUrl() : u, priv);
        if (tabsPanel != null && tabsPanel.getVisibility() == View.VISIBLE) t.group = groupFilter;
        else if (cur != null) t.group = cur.group;
        int i = cur == null ? tabs.size() : tabs.indexOf(cur) + 1;
        tabs.add(i, t);
        if (select) {
            select(t);
            if (isNew(t.url)) focusBar();
        } else updateTabsBtn();
        return t;
    }

    void select(Tab t) {
        if (cur != null && cur != t) snapshot(cur);
        if (cur != null && cur.s != null && cur != t) cur.s.setActive(false);
        cur = t;
        appBar.setVisibility(View.GONE);
        if (t.s == null) {
            open(t);
            GeckoSession.SessionState st = null;
            if (t.state != null) try { st = GeckoSession.SessionState.fromString(t.state); } catch (Exception ignored) {}
            if (st != null) t.s.restoreState(st); else t.s.loadUri(isNew(t.url) ? homeUrl() : t.url);
        }
        view.releaseSession();
        view.setSession(t.s);
        t.s.setActive(true);
        progress.setProgress(t.progress >= 100 ? 0 : t.progress);
        if (findBar.getVisibility() == View.VISIBLE) find(false);
        showTabs(false);
        showUrl();
        updateTabsBtn();
        saveSession();
    }

    void close(Tab t) {
        int i = tabs.indexOf(t);
        tabs.remove(t);
        if (t.s != null) {
            if (cur == t) view.releaseSession();
            t.s.close();
        }
        boolean wasCur = cur == t;
        if (wasCur) cur = null;
        if (tabs.isEmpty()) newTab(null, false, true);
        else if (wasCur) select(tabs.get(Math.min(i, tabs.size() - 1)));
        updateTabsBtn();
        if (tabList != null && tabsPanel.getVisibility() == View.VISIBLE) refreshTabs();
        saveSession();
    }

    private void home() {
        if (cur == null) { newTab(null, false, true); return; }
        if (editing) { url.clearFocus(); keyboard(url, false); }
        load(homeUrl());
    }

    /** Opens an address in the current tab. */
    void load(String u) {
        if (cur == null) { newTab(u, false, true); return; }
        cur.url = u;
        if (cur.s == null) select(cur); else cur.s.loadUri(u);
        showUrl();
    }

    private void updateTabsBtn() { tabsBtn.setText(tabs.size() > 99 ? ":D" : String.valueOf(tabs.size())); }

    private String groupFilter = "";
    private LinearLayout groupChips;

    private List<Tab> shownTabs() {
        if (groupFilter.isEmpty()) return tabs;
        List<Tab> out = new ArrayList<>();
        for (Tab t : tabs) if (t.group.equals(groupFilter)) out.add(t);
        return out;
    }

    private List<String> groups() {
        List<String> g = new ArrayList<>();
        for (Tab t : tabs) if (!t.group.isEmpty() && !g.contains(t.group)) g.add(t.group);
        return g;
    }

    /** Tabs as a two-column grid of page previews, with group chips on top. */
    private View buildTabsPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(PAGE);
        p.setClickable(true);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(18), dp(10), dp(12), dp(4));
        head.addView(text(this, L.t("Tabs"), 20, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(pill(R.drawable.i_private, L.t("Private"), SOFT2, v -> newTab(null, true, true)));
        head.addView(pill(R.drawable.i_plus, L.t("New tab"), ACC, v -> newTab(null, false, true)));
        p.addView(head);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        groupChips = new LinearLayout(this);
        groupChips.setPadding(dp(10), dp(6), dp(10), dp(6));
        hs.addView(groupChips);
        p.addView(hs);
        android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(2);
        grid.setVerticalSpacing(dp(10));
        grid.setHorizontalSpacing(dp(10));
        grid.setPadding(dp(12), dp(4), dp(12), dp(16));
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return shownTabs().size(); }
            @Override public Object getItem(int i) { return shownTabs().get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View old, ViewGroup parent) { return tabCard(shownTabs().get(i), getResources().getDisplayMetrics().widthPixels); }
        });
        tabList = grid;
        p.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return p;
    }

    private View tabCard(Tab t, int gridW) {
        int fg = t.priv ? Color.WHITE : TEXT;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = round(this, t.priv ? 0xff2b2d33 : BG, 16);
        bg.setStroke(dp(2), t == cur ? ACC : (t.priv ? 0xff2b2d33 : BG));
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setPadding(dp(2), dp(2), dp(2), dp(2));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(2), 0, dp(2));
        if (isNew(t.url)) top.addView(icon(this, t.priv ? R.drawable.i_private : R.drawable.i_home, fg, 16));
        else top.addView(badge(this, host(t.url), 18));
        TextView a = text(this, t.label(), 13, fg, false);
        a.setSingleLine(true);
        a.setEllipsize(TextUtils.TruncateAt.END);
        a.setPadding(dp(8), 0, 0, 0);
        top.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView x = iconButton(this, R.drawable.i_x, 36, v -> close(t));
        x.setImageTintList(ColorStateList.valueOf(t.priv ? Color.WHITE : DIM));
        top.addView(x);
        card.addView(top);
        int w = Math.max(dp(120), (gridW - dp(34)) / 2);
        ImageView shot = new ImageView(this);
        shot.setScaleType(ImageView.ScaleType.FIT_XY);
        GradientDrawable sb = round(this, t.priv ? 0xff3a3c44 : SOFT, 12);
        shot.setBackground(sb);
        shot.setClipToOutline(true);
        if (t.thumb != null) shot.setImageBitmap(t.thumb);
        else {
            shot.setScaleType(ImageView.ScaleType.CENTER);
            shot.setImageResource(isNew(t.url) ? R.drawable.i_home : R.drawable.i_globe);
            shot.setImageTintList(ColorStateList.valueOf(DIM));
        }
        card.addView(shot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (w * 1.25f)));
        if (!t.group.isEmpty() && groupFilter.isEmpty()) {
            TextView g = text(this, t.group, 11, DIM, true);
            g.setPadding(dp(8), dp(4), dp(8), dp(2));
            g.setSingleLine(true);
            card.addView(g);
        }
        card.setOnClickListener(v -> select(t));
        card.setOnLongClickListener(v -> { tabMenu(t); return true; });
        return card;
    }

    private void tabMenu(Tab t) {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add(L.t("New group…")); acts.add(() -> ask(L.t("Group name"), "", n -> { t.group = n; groupFilter = n; refreshTabs(); }));
        for (String g : groups()) if (!g.equals(t.group)) { labels.add(L.t("Move to") + " " + g); acts.add(() -> { t.group = g; refreshTabs(); }); }
        if (!t.group.isEmpty()) { labels.add(L.t("Remove from group")); acts.add(() -> { t.group = ""; refreshTabs(); }); }
        labels.add(L.t("Close other tabs")); acts.add(() -> { for (Tab o : new ArrayList<>(shownTabs())) if (o != t) close(o); });
        labels.add(L.t("Close tab")); acts.add(() -> close(t));
        Ui.dialog(this).setTitle(t.label()).setItems(labels.toArray(new String[0]), (d, i) -> acts.get(i).run()).show();
    }

    void ask(String title, String value, java.util.function.Consumer<String> done) {
        EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setText(value);
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(in);
        Ui.dialog(this).setTitle(title).setView(f)
                .setPositiveButton("OK", (d, w) -> { String v = in.getText().toString().trim(); if (!v.isEmpty()) done.accept(v); })
                .setNegativeButton(L.t("Cancel"), null).show();
    }

    private void refreshTabs() {
        if (!groupFilter.isEmpty() && !groups().contains(groupFilter)) groupFilter = "";
        groupChips.removeAllViews();
        List<String> g = groups();
        if (!g.isEmpty()) {
            groupChips.addView(chip(L.t("All") + " · " + tabs.size(), groupFilter.isEmpty(), () -> { groupFilter = ""; refreshTabs(); }));
            for (String n : g) groupChips.addView(chip(n, n.equals(groupFilter), () -> { groupFilter = n; refreshTabs(); }));
        }
        ((View) groupChips.getParent()).setVisibility(g.isEmpty() ? View.GONE : View.VISIBLE);
        ((BaseAdapter) tabList.getAdapter()).notifyDataSetChanged();
        saveSession();
    }

    private TextView chip(String label, boolean on, Runnable r) {
        TextView c = text(this, label, 13, on ? ON_ACC : TEXT, true);
        c.setPadding(dp(14), dp(7), dp(14), dp(7));
        c.setBackground(press(this, on ? ACC : SOFT, 16));
        c.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        c.setLayoutParams(lp);
        return c;
    }

    /** Keeps a small top-of-page picture of the tab for the grid. */
    private void snapshot(Tab t) {
        if (t == null || t.s == null || view.getSession() != t.s || tabsPanel.getVisibility() == View.VISIBLE) return;
        try {
            view.capturePixels().accept(b -> snapshotFrom(t, b), e -> {});
        } catch (Exception ignored) {}
    }

    private LinearLayout pill(int iconRes, String text, int bg, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(12), dp(8), dp(16), dp(8));
        t.setBackground(press(this, bg, 20));
        t.setOnClickListener(l);
        int fg = bg == ACC ? ON_ACC : TEXT;
        ImageView i = icon(this, iconRes, fg, 18);
        ((LinearLayout.LayoutParams) i.getLayoutParams()).setMargins(0, 0, dp(6), 0);
        t.addView(i);
        t.addView(text(this, text, 14, fg, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), 0, 0, 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void showTabs(boolean on) {
        if (on) {
            // Picture first, then the grid shows it.
            if (cur != null && cur.s != null) {
                view.capturePixels().accept(b -> {
                    snapshotFrom(cur, b);
                    showTabsNow();
                }, e -> showTabsNow());
            } else showTabsNow();
            return;
        }
        tabsPanel.setVisibility(View.GONE);
    }

    private void showTabsNow() {
        tabsPanel.setVisibility(View.VISIBLE);
        keyboard(url, false);
        url.clearFocus();
        if (cur != null && !cur.group.equals(groupFilter)) groupFilter = cur.group;
        refreshTabs();
        tabList.setSelection(Math.max(0, shownTabs().indexOf(cur)));
    }

    private void snapshotFrom(Tab t, android.graphics.Bitmap b) {
        if (t == null || b == null || b.getWidth() == 0) return;
        int w = b.getWidth(), h = Math.min(b.getHeight(), (int) (w * 1.25f));
        android.graphics.Bitmap crop = android.graphics.Bitmap.createBitmap(b, 0, 0, w, h);
        t.thumb = android.graphics.Bitmap.createScaledBitmap(crop, 300, (int) (300f * h / w), true);
        if (crop != b) crop.recycle();
        b.recycle();
    }

    // Open tabs survive restarts; only the one you were on loads at start. Private tabs don't.
    private void saveSession() {
        if (appUrl() != null) return;
        JSONArray a = new JSONArray();
        int active = 0;
        for (Tab t : tabs) {
            if (t.priv || isNew(t.url)) continue;
            if (t == cur) active = a.length();
            try { a.put(new JSONObject().put("u", t.url).put("t", t.title).put("g", t.group).putOpt("s", t.state)); } catch (Exception ignored) {}
        }
        try { store.setSession(new JSONObject().put("tabs", a).put("a", active).toString()); } catch (Exception ignored) {}
    }

    private void restore() {
        JSONObject s;
        try { s = new JSONObject(store.session()); } catch (Exception e) { s = new JSONObject(); }
        JSONArray a = s.optJSONArray("tabs");
        if (a == null || a.length() == 0) { tabs.add(new Tab(homeUrl(), false)); select(tabs.get(0)); return; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            Tab t = new Tab(o.optString("u"), false);
            t.title = o.optString("t");
            t.group = o.optString("g");
            t.state = o.optString("s", null);
            tabs.add(t);
        }
        select(tabs.get(Math.min(s.optInt("a"), tabs.size() - 1)));
    }

    // ---------------------------------------------------------------- address bar

    /** What the bar shows: the site alone while browsing, the whole address while editing. */
    private static String shown(String u, boolean full) {
        if (u.startsWith(ASSETS)) return "yukiweb://" + u.replaceAll("[#?].*", "").replaceAll(".*/|\\.html$", "");
        if (full) return u;
        String h = host(u).replaceFirst("^www\\.", "");
        return h.isEmpty() ? u : h;
    }

    private void showUrl() {
        if (editing || cur == null) return;
        completing = true;
        url.setText(isNew(cur.url) ? "" : shown(cur.url, false));
        completing = false;
        barIcon();
    }

    private void barIcon() {
        int res = editing || cur == null || isNew(cur.url) ? R.drawable.i_search
                : cur.url.startsWith("https:") ? R.drawable.i_lock : cur.priv ? R.drawable.i_private : R.drawable.i_globe;
        Drawable d = getDrawable(res).mutate();
        d.setBounds(0, 0, dp(18), dp(18));
        url.setCompoundDrawablesRelative(d, null, null, null);
    }

    /** Typing: the field takes the whole bar (back arrow instead of home, tab/menu buttons hidden). */
    private void editMode(boolean on) {
        android.transition.TransitionManager.beginDelayedTransition(bar, new android.transition.AutoTransition().setDuration(160));
        for (View v : barTools) v.setVisibility(on ? View.GONE : View.VISIBLE);
        homeBtn.setImageResource(on ? R.drawable.i_back : R.drawable.i_home);
        clearBtn.setVisibility(on && url.length() > 0 ? View.VISIBLE : View.GONE);
        url.setPaddingRelative(dp(14), 0, on ? dp(42) : dp(14), 0);
        if (!on) suggWrap.setVisibility(View.GONE);
    }

    private void stopEditing() { url.clearFocus(); keyboard(url, false); }

    private String ghostRest;

    /** Shows (or hides with null) the inline completion after the typed text. */
    private void ghost(String rest) {
        ghostRest = rest == null || rest.isEmpty() ? null : rest;
        ghost.setText(ghostRest == null ? "" : ghostRest);
        ghost.setVisibility(ghostRest == null ? View.GONE : View.VISIBLE);
        if (ghostRest != null) url.post(this::placeGhost);
    }

    private void placeGhost() {
        if (ghostRest == null || url.getLayout() == null) return;
        float x = url.getTotalPaddingLeft() + url.getLayout().getPrimaryHorizontal(url.length()) - url.getScrollX();
        int room = url.getWidth() - url.getTotalPaddingRight() - (int) x;
        if (room < dp(12)) { ghost.setVisibility(View.GONE); return; }
        ghost.setVisibility(View.VISIBLE);
        ghost.setTranslationX(x);
        ghost.setMaxWidth(room);
        ghost.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    /** Tapping the grey completion takes it into the field. */
    private void acceptGhost() {
        if (ghostRest == null) return;
        String full = url.getText().toString() + ghostRest;
        ghost(null);
        completing = true;
        url.setText(full);
        url.setSelection(full.length());
        completing = false;
        suggest();
    }

    /** While editing a page: its title and link with copy / share / edit, plus the QR scanner. */
    private void fillPageCard(boolean show) {
        pageCard.removeAllViews();
        if (!show) return;
        boolean page = cur != null && !isNew(cur.url);
        if (page) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(16), dp(10), dp(6), dp(10));
            r.addView(icon(this, cur.url.startsWith("https:") ? R.drawable.i_lock : R.drawable.i_globe, DIM, 20));
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(dp(14), 0, dp(8), 0);
            TextView t1 = text(this, cur.title.isEmpty() ? host(cur.url) : cur.title, 15, TEXT, true);
            t1.setSingleLine(true);
            t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView t2 = text(this, shown(cur.url, true), 13, DIM, false);
            t2.setSingleLine(true);
            t2.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            tx.addView(t1);
            tx.addView(t2);
            r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            r.addView(iconButton(this, R.drawable.i_copy, 44, v -> { copy(cur.url); stopEditing(); }));
            r.addView(iconButton(this, R.drawable.i_share, 44, v -> {
                stopEditing();
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, cur.url), null));
            }));
            r.addView(iconButton(this, R.drawable.i_write, 44, v -> { url.setText(cur.url); url.setSelection(url.length()); }));
            pageCard.addView(r);
        }
        pageCard.addView(row(this, R.drawable.i_qr, L.t("Scan QR code"), null, null, v -> { stopEditing(); scanQr(); }));
        View line = new View(this);
        line.setBackgroundColor(SOFT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(16), dp(2), dp(16), dp(4));
        pageCard.addView(line, lp);
    }

    void focusBar() {
        url.requestFocus();
        keyboard(url, true);
    }

    String resolve(String v) {
        v = v.trim();
        if (v.startsWith("@")) {
            for (String[] r : ROUTES) if (r[0].startsWith(v.substring(1).toLowerCase(Locale.ROOT))) return r[2];
        }
        if (v.matches("(?i)^yukiweb://(newtab|home)/?$")) return homeUrl();
        if (v.matches("(?i)^yukiweb://html/?$")) return ASSETS + "html.html";
        if (v.matches("(?i)^[a-z][a-z0-9+.-]*://.*") || v.startsWith("resource:")) return v;
        // Bare IP addresses (routers, servers) almost never have TLS, so they go over http.
        if (v.matches("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?([/?#]\\S*)?$") || v.matches("(?i)^\\[[0-9a-f:.]+\\](:\\d+)?([/?#]\\S*)?$")) return "http://" + v;
        if (v.matches("(?i)^localhost(:\\d+)?(/.*)?$") || v.matches("(?i)^[^\\s/]+\\.[a-z]{2,}(:\\d+)?(/\\S*)?$")) return "https://" + v;
        return store.searchUrl(v);
    }

    /** yukiweb://settings and friends open the app's own pages. */
    private boolean page(String v) {
        String p = v.trim().toLowerCase(Locale.ROOT).replaceFirst("^yukiweb://", "").replaceFirst("/$", "");
        if (!v.trim().toLowerCase(Locale.ROOT).startsWith("yukiweb://")) return false;
        if (p.startsWith("inbox/")) {
            try { openInbox(Long.parseLong(p.substring(6))); } catch (NumberFormatException ignored) {}
            return true;
        }
        switch (p) {
            case "settings": screens.settings(); return true;
            case "history": screens.history(); return true;
            case "bookmarks": screens.bookmarks(); return true;
            case "downloads": screens.downloads(); return true;
            case "focus": focusBar(); return true;
            case "send": sendTab(); return true;
            case "qr": scanQr(); return true;
            case "private": newTab(null, true, true); return true;
            case "account": account(); return true;
            case "at":
                focusBar();
                url.setText("@");
                url.setSelection(1);
                return true;
        }
        return false;
    }

    void go(String v) {
        if (v.trim().isEmpty()) return;
        url.clearFocus();
        keyboard(url, false);
        if (page(v)) return;
        load(resolve(v));
    }

    /** Chrome-style: fill in the rest of the best history match, selected so typing replaces it. */
    private void complete() {
        String v = url.getText().toString();
        if (v.isEmpty() || v.startsWith("@") || v.contains(" ") || url.getSelectionStart() != v.length()) { ghost(null); return; }
        String low = v.toLowerCase(Locale.ROOT), best = null;
        JSONArray h = store.hist();
        for (int i = 0; i < h.length() && i < 500; i++) {
            JSONObject o = h.optJSONObject(i);
            String u = o == null ? "" : o.optString("u");
            if (!u.startsWith("http") || u.contains("tauri.localhost")) continue;
            String b = bare(u), hostPart = b.split("/")[0];
            if (hostPart.startsWith(low)) { best = hostPart; break; }
            if (best == null && b.startsWith(low)) best = b;
        }
        ghost(best == null || best.length() <= v.length() ? null : best.substring(v.length()));
    }

    private final java.util.Map<String, List<String>> webSug = new java.util.HashMap<>();
    private final java.util.Set<String> webSugPending = new java.util.HashSet<>();

    private void fetchWebSug(String q) {
        if (!webSugPending.add(q)) return;
        new Thread(() -> {
            List<String> out = new ArrayList<>();
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                        "https://search.yukichat.lol/api/suggest?q=" + android.net.Uri.encode(q)).openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(3000);
                try (java.util.Scanner sc = new java.util.Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    JSONArray a = new JSONArray(sc.hasNext() ? sc.next() : "[]");
                    for (int i = 0; i < a.length(); i++) if (!a.optString(i).isEmpty()) out.add(a.optString(i));
                }
            } catch (Exception ignored) {
                // offline: history suggestions are enough
            }
            runOnUiThread(() -> {
                webSugPending.remove(q);
                if (webSug.size() > 300) webSug.clear();
                webSug.put(q, out);
                String now = url.getText().toString();
                if (url.getSelectionStart() != url.getSelectionEnd()) now = now.substring(0, Math.min(url.getSelectionStart(), url.getSelectionEnd()));
                if (!out.isEmpty() && url.hasFocus() && now.trim().toLowerCase(Locale.ROOT).equals(q)) suggest();
            });
        }).start();
    }

    private void suggest() {
        String typed = url.getText().toString(), v = typed;
        if (url.getSelectionStart() != url.getSelectionEnd()) v = typed.substring(0, Math.min(url.getSelectionStart(), url.getSelectionEnd()));
        v = v.trim().toLowerCase(Locale.ROOT);
        List<Object[]> items = new ArrayList<>(); // label, hint, url, icon
        boolean untouched = v.isEmpty() || (cur != null && typed.equals(shown(cur.url, true)));
        fillPageCard(untouched);
        if (ghostRest != null) {
            String full = typed.trim() + ghostRest;
            items.add(new Object[]{full, L.t("Open"), resolve(full), R.drawable.i_globe});
        }
        if (untouched) v = "";
        if (v.startsWith("@")) {
            for (int k = 0; k < ROUTES.length; k++) {
                String[] r = ROUTES[k];
                if (r[0].startsWith(v.substring(1)) || r[1].toLowerCase(Locale.ROOT).contains(v.substring(1)))
                    items.add(new Object[]{r[1], "@" + r[0] + "  ·  " + (r[2].startsWith("resource:") ? L.t("built in") : host(r[2])), r[2], ROUTE_ICONS[k]});
            }
        } else if (!v.isEmpty()) {
            items.add(new Object[]{typed.trim(), L.t("Search with") + " " + engineName(), resolve(typed), R.drawable.i_search});
            // Search suggestions from Yuki Search (skipped for things that look like addresses).
            if (v.length() <= 120 && !v.matches("^([a-z][a-z0-9+.-]*:|\\S+\\.[a-z]{2,}(/.*)?$).*")) {
                List<String> ws = webSug.get(v);
                if (ws == null) fetchWebSug(v);
                else for (String s : ws) {
                    if (items.size() >= 5) break;
                    if (!s.equalsIgnoreCase(v)) items.add(new Object[]{s, "", store.searchUrl(s), R.drawable.i_search});
                }
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            JSONArray b = store.bmFlat();
            for (int i = 0; i < b.length() && items.size() < 4; i++) {
                JSONObject o = b.optJSONObject(i);
                String u = o.optString("u");
                if ((bare(u).contains(v) || o.optString("t").toLowerCase(Locale.ROOT).contains(v)) && seen.add(bare(u)))
                    items.add(new Object[]{o.optString("t").isEmpty() ? bare(u) : o.optString("t"), bare(u), u, R.drawable.i_star});
            }
            JSONArray h = store.hist();
            for (int i = 0; i < h.length() && items.size() < 8; i++) {
                JSONObject o = h.optJSONObject(i);
                String u = o.optString("u");
                if (!u.startsWith("http") || u.contains("tauri.localhost")) continue;
                if ((bare(u).contains(v) || o.optString("t").toLowerCase(Locale.ROOT).contains(v)) && seen.add(bare(u)))
                    items.add(new Object[]{o.optString("t").isEmpty() ? bare(u) : o.optString("t"), bare(u), u, R.drawable.i_history});
            }
        }
        if (!editing) return;
        suggWrap.setVisibility(View.VISIBLE);
        sugg.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View old, ViewGroup parent) {
                Object[] it = items.get(i);
                return row(MainActivity.this, (Integer) it[3], (String) it[0], (String) it[1], null, x -> go((String) it[2]));
            }
        });
    }

    private void keyboard(View target, boolean show) {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) target.postDelayed(() -> { if (target.hasFocus()) im.showSoftInput(target, 0); }, 150);
        else im.hideSoftInputFromWindow(target.getWindowToken(), 0);
    }

    // ---------------------------------------------------------------- menu

    /** Bottom sheet: page actions on top, Yuki ID, then a grid of everything else. */
    private void showMenu() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(12), dp(8), dp(12), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);

        View handle = new View(this);
        handle.setBackground(round(this, SOFT2, 2));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.setMargins(0, 0, 0, dp(10));
        sheet.addView(handle, hp);

        boolean page = cur != null && cur.url.startsWith("http");
        boolean marked = page && store.isBookmarked(cur.url);
        LinearLayout acts = new LinearLayout(this);
        acts.addView(action(R.drawable.i_back, cur != null && cur.back, () -> cur.s.goBack(), d));
        acts.addView(action(R.drawable.i_forward, cur != null && cur.fwd, () -> cur.s.goForward(), d));
        acts.addView(action(R.drawable.i_reload, cur != null && cur.s != null, () -> cur.s.reload(), d));
        acts.addView(action(marked ? R.drawable.i_star_on : R.drawable.i_star, page, () -> {
            if (store.isBookmarked(cur.url)) store.removeBookmark(cur.url); else store.addBookmark(cur.url, cur.label());
            toast(L.t(store.isBookmarked(cur.url) ? "Bookmarked" : "Bookmark removed"));
        }, d));
        acts.addView(action(R.drawable.i_share, page, () -> startActivity(Intent.createChooser(
                new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, cur.url), null)), d));
        if (marked) ((ImageView) acts.getChildAt(3)).setImageTintList(ColorStateList.valueOf(0xffe0a800));
        sheet.addView(acts);

        // Yuki ID
        boolean in = !store.token().isEmpty();
        JSONObject p = store.profile();
        String name = p.optString("display_name", p.optString("username"));
        LinearLayout acct = new LinearLayout(this);
        acct.setGravity(Gravity.CENTER_VERTICAL);
        acct.setPadding(dp(14), dp(12), dp(10), dp(12));
        acct.setBackground(press(this, PAGE, 18));
        TextView av = badge(this, in ? name : "?", 40);
        av.setBackground(round(this, in ? ACC : SOFT2, 20));
        av.setTextColor(ON_ACC);
        if (!in) av.setText("");
        ((LinearLayout.LayoutParams) av.getLayoutParams()).setMargins(0, 0, dp(14), 0);
        acct.addView(av);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(this, in ? name : "Sign in with Yuki ID", 15, TEXT, true));
        col.addView(text(this, in ? (store.syncStatus().isEmpty() ? "Sync on" : store.syncStatus()) : "Sync history and bookmarks with your PC", 12, DIM, false));
        acct.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        acct.addView(icon(this, R.drawable.i_chevron, DIM, 18));
        acct.setOnClickListener(v -> { d.dismiss(); if (in) screens.settings(); else account(); });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(0, dp(10), 0, dp(14));
        sheet.addView(acct, ap);

        boolean desk = cur != null && cur.desktop;
        Object[][] grid = {
                {R.drawable.i_plus, "New tab", (Runnable) () -> newTab(null, false, true), false},
                {R.drawable.i_private, "Private tab", (Runnable) () -> newTab(null, true, true), false},
                {R.drawable.i_bookmark, "Bookmarks", (Runnable) screens::bookmarks, false},
                {R.drawable.i_history, "History", (Runnable) screens::history, false},
                {R.drawable.i_download, "Downloads", (Runnable) screens::downloads, false},
                {R.drawable.i_find, "Find in page", (Runnable) () -> { if (page) find(true); else toast(L.t("Open a page first")); }, false},
                {R.drawable.i_monitor, "Desktop site", (Runnable) this::toggleDesktop, desk},
                {R.drawable.i_forward, "Send to PC", (Runnable) this::sendTab, false},
                {R.drawable.i_translate, cur != null && cur.translated ? "Show original" : "Translate", (Runnable) () -> { if (page) translate(cur); else toast(L.t("Open a page first")); }, cur != null && cur.translated},
                {R.drawable.i_addhome, cur != null && cur.manifest != null ? "Install app" : "Add to Home screen", (Runnable) () -> { if (page) addToHome(cur); else toast(L.t("Open a page first")); }, false},
                {R.drawable.i_qr, "Scan QR", (Runnable) this::scanQr, false},
                {R.drawable.i_settings, "Settings", (Runnable) screens::settings, false},
        };
        for (int row = 0; row < grid.length; row += 4) {
            LinearLayout line = new LinearLayout(this);
            for (int i = row; i < row + 4; i++) {
                if (i >= grid.length) { line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1)); continue; }
                Object[] g = grid[i];
                line.addView(tile((Integer) g[0], L.t((String) g[1]), (Boolean) g[3], () -> { d.dismiss(); ((Runnable) g[2]).run(); }),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            sheet.addView(line);
        }

        JSONObject up = Updater.available;
        if (up != null) {
            LinearLayout u = new LinearLayout(this);
            u.setGravity(Gravity.CENTER_VERTICAL);
            u.setPadding(dp(14), dp(12), dp(14), dp(12));
            u.setBackground(press(this, ACC, 18));
            ImageView ui = icon(this, R.drawable.i_update, ON_ACC, 20);
            ((LinearLayout.LayoutParams) ui.getLayoutParams()).setMargins(0, 0, dp(12), 0);
            u.addView(ui);
            u.addView(text(this, "Update to YukiWeb " + up.optString("version_name"), 15, ON_ACC, true));
            u.setOnClickListener(v -> { d.dismiss(); Updater.offer(this, up); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(14), 0, 0);
            sheet.addView(u, lp);
        }

        d.setContentView(sheet);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        w.setWindowAnimations(android.R.style.Animation_InputMethod);
        w.setDimAmount(0.3f);
        d.show();
    }

    private View action(int res, boolean on, Runnable r, Dialog d) {
        ImageView b = iconButton(this, res, 52, v -> { d.dismiss(); r.run(); });
        b.setEnabled(on);
        b.setAlpha(on ? 1f : 0.3f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        b.setLayoutParams(lp);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return b;
    }

    private View tile(int res, String label, boolean active, Runnable r) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, dp(6), 0, dp(10));
        t.setBackground(press(this, 0, 16));
        ImageView i = icon(this, res, active ? ON_ACC : TEXT, 52);
        i.setPadding(dp(15), dp(15), dp(15), dp(15));
        i.setBackground(round(this, active ? ACC : SOFT, 18));
        t.addView(i);
        TextView l = text(this, label, 12, TEXT, false);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(2);
        l.setPadding(dp(2), dp(6), dp(2), 0);
        t.addView(l);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void toggleDesktop() {
        if (cur == null) return;
        cur.desktop = !cur.desktop;
        view.releaseSession();
        if (cur.s != null) cur.s.close();
        open(cur);
        view.setSession(cur.s);
        cur.s.loadUri(cur.url);
    }

    String engineName() {
        switch (store.engine()) {
            case "yandex": return "Yandex";
            case "bing": return "Bing";
            case "ddg": return "DuckDuckGo";
            case "google": return "Google";
            default: return "Yuki Search";
        }
    }

    void engines(Runnable after) {
        String[] ids = {"yuki", "google", "yandex", "bing", "ddg"}, names = {"Yuki Search", "Google", "Yandex", "Bing", "DuckDuckGo"};
        int sel = java.util.Arrays.asList(ids).indexOf(store.engine());
        Ui.dialog(this).setTitle("Search engine").setSingleChoiceItems(names, sel, (d, i) -> {
            store.put("search", "\"" + ids[i] + "\"", true);
            d.dismiss();
            after.run();
        }).show();
    }

    void makeDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), REQ_ROLE);
                return;
            }
        }
        try { startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    StorageController runtimeStorage() { return runtime.getStorageController(); }

    void languageChanged() { refreshHome(); }

    void clearSiteData(long flags) {
        runtime.getStorageController().clearData(flags).accept(v -> toast("Cleared"), e -> toast("Couldn't clear: " + e.getMessage()));
    }

    // ---------------------------------------------------------------- Yuki ID

    void account() {
        if (store.token().isEmpty()) {
            // Same flow as the PC: YukiChat signs in, then redirects to SIGNIN with a one-time code.
            ssoState = UUID.randomUUID().toString();
            newTab("https://yukichat.lol/?sso=web&state=" + ssoState, false, true);
            return;
        }
        screens.settings();
    }

    private void signIn(Tab t, Uri u) {
        String code = u.getQueryParameter("yc_code");
        if (code == null || !ssoState.equals(u.getQueryParameter("state"))) return;
        ssoState = "";
        t.s.loadUri("https://yukichat.lol");
        new Thread(() -> {
            try (Response r = new OkHttpClient().newCall(new Request.Builder().url(BuildConfig.API + "/api/yukiweb/login")
                    .post(RequestBody.create(new JSONObject().put("code", code).put("platform", "android").toString(), MediaType.get("application/json")))
                    .build()).execute()) {
                if (!r.isSuccessful()) throw new Exception("login " + r.code());
                JSONObject j = new JSONObject(r.body().string());
                ui.post(() -> {
                    store.setAccount(j.optString("token"), j.optJSONObject("profile"));
                    store.resetSync();
                    sync.now();
                    JSONObject p = j.optJSONObject("profile");
                    toast("Signed in as @" + (p == null ? "" : p.optString("username")) + ". Syncing with your PC…");
                });
            } catch (Exception e) {
                ui.post(() -> toast("Sign-in failed: " + e.getMessage()));
            }
        }).start();
    }

    void signOut() {
        String token = store.token();
        store.setAccount("", null);
        new Thread(() -> {
            try (Response r = new OkHttpClient().newCall(new Request.Builder().url(BuildConfig.API + "/api/yukiweb/logout")
                    .header("Authorization", "Bearer " + token).post(RequestBody.create(new byte[0])).build()).execute()) {
            } catch (Exception ignored) {}
        }).start();
        toast(L.t("Signed out"));
    }

    // ---------------------------------------------------------------- GeckoView delegates

    // Hands a navigation to another app when it is meant for one; true = the browser must not load it.
    private boolean external(Tab t, GeckoSession.NavigationDelegate.LoadRequest req, boolean top) {
        String u = req.uri;
        if (top && yukiSso(t, u)) return true;
        // A home-screen web app keeps to its own site; links elsewhere open in the browser.
        if (top && t.app && req.hasUserGesture && !req.isRedirect && u.matches("(?i)^https?:.*")
                && !base(host(u)).equals(base(host(appUrl())))) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).setClass(this, MainActivity.class));
            return true;
        }
        // tel:, mailto:, intent:, bank…:// (SBP), market:… always leave the browser.
        if (!u.matches("(?i)^(https?|about|data|blob|resource|file|moz-extension|javascript|view-source):.*")) {
            Intent i;
            try {
                i = u.regionMatches(true, 0, "intent:", 0, 7) ? Intent.parseUri(u, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, Uri.parse(u));
            } catch (Exception e) { toast(L.t("No app can open this link")); return true; }
            i.addCategory(Intent.CATEGORY_BROWSABLE).setComponent(null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setSelector(null);
            try { startActivity(i); return true; }
            catch (Exception e) {
                String fb = i.getStringExtra("browser_fallback_url");
                if (fb != null && fb.matches("(?i)^https?:.*")) { ui.post(() -> load(fb)); return true; }
                if (i.getPackage() != null) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + i.getPackage())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
                    catch (Exception ignored) {}
                }
                toast(L.t("No app can open this link"));
                return true;
            }
        }
        // https links an app claims (SBP qr.nspk.ru → bank, t.me…): offer the app in a bar, or open it right away
        // when the user chose "Always" for that site. Only for clicks/redirects into another site, never typed addresses.
        if (!top || req.isDirectNavigation || !(req.hasUserGesture || req.isRedirect) || !u.matches("(?i)^https?:.*")) return false;
        String from = Uri.parse(req.triggerUri != null ? req.triggerUri : t.url).getHost(), to = Uri.parse(u).getHost();
        if (to == null || to.equalsIgnoreCase(from)) return false;
        List<android.content.pm.ResolveInfo> apps = appsFor(u);
        if (apps.isEmpty()) return false;
        if (store.appAlways().contains(to.toLowerCase(Locale.ROOT)) && openInApp(u, apps)) return true;
        ui.post(() -> appBar(t, u, apps));
        return false;
    }

    private static String base(String h) {
        String[] p = h.split("\\.");
        return p.length <= 2 ? h : p[p.length - 2] + "." + p[p.length - 1];
    }

    // Sibling Yuki sites sign in through yukichat.lol/?sso=<app>; signed into Yuki ID here, that's one tap instead.
    private static final java.util.Map<String, String> SSO = new java.util.HashMap<>();
    static {
        SSO.put("music", "yukimusic"); SSO.put("mail", "yukimail"); SSO.put("pages", "yukipages");
        SSO.put("account", "yukiid"); SSO.put("vinyl", "yukivinyl");
    }
    private String ssoSkip = "";

    private boolean yukiSso(Tab t, String u) {
        if (t.priv || store.token().isEmpty() || !u.startsWith("https://yukichat.lol/") || u.equals(ssoSkip)) return false;
        Uri q = Uri.parse(u);
        String client = SSO.get(String.valueOf(q.getQueryParameter("sso")));
        if (client == null || "app".equals(q.getQueryParameter("ret"))) return false;
        String state = q.getQueryParameter("state") == null ? "" : q.getQueryParameter("state");
        JSONObject p = store.profile();
        String name = p.optString("display_name", p.optString("username"));
        ui.post(() -> Ui.dialog(this).setTitle(L.t("Sign in with Yuki ID"))
                .setMessage(L.t("Continue as") + " " + name + (p.optString("username").isEmpty() ? "" : " (@" + p.optString("username") + ")"))
                .setPositiveButton(L.t("Continue"), (d, w) -> new Thread(() -> {
                    try (Response r = new OkHttpClient().newCall(new Request.Builder().url(BuildConfig.API + "/api/oauth/authorize")
                            .header("Authorization", "Bearer " + store.token())
                            .post(RequestBody.create(new JSONObject().put("client", client).toString(), MediaType.get("application/json")))
                            .build()).execute()) {
                        if (!r.isSuccessful()) throw new Exception("" + r.code());
                        JSONObject j = new JSONObject(r.body().string());
                        String next = j.optString("redirect") + "?yc_code=" + Uri.encode(j.optString("code")) + "&state=" + Uri.encode(state);
                        ui.post(() -> { if (t.s != null) t.s.loadUri(next); });
                    } catch (Exception e) {
                        ui.post(() -> { toast(L.t("Sign-in failed")); ssoSkip = u; if (t.s != null) t.s.loadUri(u); });
                    }
                }).start())
                .setNegativeButton(L.t("Use another account"), (d, w) -> { ssoSkip = u; if (t.s != null) t.s.loadUri(u); })
                .show());
        return true;
    }

    /** Apps (not browsers, not us) that open this link. */
    private List<android.content.pm.ResolveInfo> appsFor(String u) {
        PackageManager pm = getPackageManager();
        java.util.Set<String> browsers = new java.util.HashSet<>();
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/"))
                .addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_ALL)) browsers.add(r.activityInfo.packageName);
        List<android.content.pm.ResolveInfo> out = new ArrayList<>();
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse(u))
                .addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_ALL)) {
            String pkg = r.activityInfo.packageName;
            if (!browsers.contains(pkg) && !pkg.equals(getPackageName())) out.add(r);
        }
        return out;
    }

    private boolean openInApp(String u, List<android.content.pm.ResolveInfo> apps) {
        if (apps.size() == 1) return launch(u, apps.get(0));
        // Several (SBP: every bank): our own picker. Android's would drop apps that aren't verified for the domain.
        PackageManager pm = getPackageManager();
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = String.valueOf(apps.get(i).loadLabel(pm));
        Ui.dialog(this).setTitle(L.t("Open with")).setItems(names, (d, i) -> launch(u, apps.get(i))).show();
        return true;
    }

    private boolean launch(String u, android.content.pm.ResolveInfo app) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setClassName(app.activityInfo.packageName, app.activityInfo.name));
            return true;
        } catch (Exception e) { toast(L.t("No app can open this link")); return false; }
    }

    /** "Open in <app>" bar over the bottom of the page, like Firefox. */
    private void appBar(Tab t, String u, List<android.content.pm.ResolveInfo> apps) {
        if (t != cur) return;
        PackageManager pm = getPackageManager();
        String label = apps.size() == 1 ? String.valueOf(apps.get(0).loadLabel(pm)) : L.t("app");
        String host = Uri.parse(u).getHost().toLowerCase(Locale.ROOT);
        appBar.removeAllViews();
        if (apps.size() == 1) {
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(apps.get(0).loadIcon(pm));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
            lp.setMargins(0, 0, dp(12), 0);
            appBar.addView(ic, lp);
        } else appBar.addView(icon(this, R.drawable.i_app, TEXT, 24));
        TextView tx = text(this, L.t("Open in") + " " + label, 15, TEXT, true);
        tx.setSingleLine(true);
        tx.setEllipsize(TextUtils.TruncateAt.END);
        tx.setPadding(apps.size() == 1 ? 0 : dp(12), 0, dp(8), 0);
        appBar.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView always = text(this, L.t("Always"), 14, TEXT, true);
        always.setPadding(dp(14), dp(10), dp(14), dp(10));
        always.setBackground(press(this, SOFT, 18));
        always.setOnClickListener(v -> {
            store.setAppAlways(host, true);
            appBar.setVisibility(View.GONE);
            if (openInApp(u, apps)) toast(L.t("Links to") + " " + host + " " + L.t("will open in the app"));
        });
        appBar.addView(always);
        TextView open = text(this, L.t("Open"), 14, ON_ACC, true);
        open.setPadding(dp(16), dp(10), dp(16), dp(10));
        open.setBackground(press(this, ACC, 18));
        open.setOnClickListener(v -> { appBar.setVisibility(View.GONE); openInApp(u, apps); });
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.setMargins(dp(8), 0, 0, 0);
        appBar.addView(open, op);
        appBar.addView(iconButton(this, R.drawable.i_x, 40, v -> appBar.setVisibility(View.GONE)));
        appBar.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideAppBar);
        ui.postDelayed(hideAppBar, 12_000);
    }

    private final Runnable hideAppBar = () -> { if (appBar != null) appBar.setVisibility(View.GONE); };

    private void hook(Tab t) {
        GeckoSession s = t.s;
        s.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession session, String u) { t.progress = 5; bar(t); }
            @Override public void onProgressChange(GeckoSession session, int p) { t.progress = p; bar(t); }
            @Override public void onPageStop(GeckoSession session, boolean ok) {
                t.progress = 100;
                bar(t);
                if (ok && !t.priv && t.url.startsWith("http")) store.visit(t.url, t.title);
            }
            @Override public void onSessionStateChange(GeckoSession session, GeckoSession.SessionState st) {
                if (t.priv) return;
                t.state = st.toString();
                saveSession();
            }
        });
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onLocationChange(GeckoSession session, String u, List<GeckoSession.PermissionDelegate.ContentPermission> perms, Boolean gesture) {
                if (u == null || u.startsWith("about:blank")) return;
                if (!u.equals(t.url)) { t.title = ""; t.translated = false; }
                if (!host(u).equals(host(t.url))) t.manifest = null;
                t.url = u;
                if (t == cur) showUrl();
                saveSession();
            }
            @Override public void onCanGoBack(GeckoSession session, boolean can) { t.back = can; }
            @Override public void onCanGoForward(GeckoSession session, boolean can) { t.fwd = can; }
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, LoadRequest req) {
                String u = req.uri;
                if (u.startsWith(SIGNIN)) { signIn(t, Uri.parse(u)); return GeckoResult.deny(); }
                // The home page talks to the app through yukiweb:// links.
                if (u.toLowerCase(Locale.ROOT).startsWith("yukiweb://")) { ui.post(() -> { if (!page(u)) load(resolve(u)); }); return GeckoResult.deny(); }
                return external(t, req, true) ? GeckoResult.deny() : GeckoResult.allow();
            }
            @Override public GeckoResult<AllowOrDeny> onSubframeLoadRequest(GeckoSession session, LoadRequest req) {
                return external(t, req, false) ? GeckoResult.deny() : GeckoResult.allow();
            }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String u) {
                Tab n = new Tab(u, t.priv);
                n.opener = t;
                tabs.add(tabs.indexOf(t) + 1, n);
                GeckoSessionSettings.Builder b = new GeckoSessionSettings.Builder().usePrivateMode(t.priv).userAgentOverride(ua(false));
                n.s = new GeckoSession(b.build());
                hook(n);
                ui.post(() -> select(n));
                return GeckoResult.fromValue(n.s);
            }
        });
        s.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession session, String title) {
                t.title = title == null ? "" : title;
                if (!t.priv && t.url.startsWith("http") && !t.title.isEmpty()) store.retitle(t.url, t.title);
                saveSession();
            }
            @Override public void onFullScreen(GeckoSession session, boolean on) { fullscreen(on); }
            @Override public void onCloseRequest(GeckoSession session) { close(t); }
            @Override public void onExternalResponse(GeckoSession session, WebResponse r) { download(r); }
            @Override public void onWebAppManifest(GeckoSession session, JSONObject m) { t.manifest = m; }
            @Override public void onContextMenu(GeckoSession session, int x, int y, ContextElement e) { contextMenu(t, e); }
            @Override public void onCrash(GeckoSession session) { reopen(t); }
            @Override public void onKill(GeckoSession session) { reopen(t); }
        });
        s.setPermissionDelegate(new Permissions(this));
        s.setPromptDelegate(new Prompts(this));
        s.setTranslationsSessionDelegate(new TranslationsController.SessionTranslation.Delegate() {
            @Override public void onTranslationStateChange(GeckoSession session, TranslationsController.SessionTranslation.TranslationState st) {
                if (st.detectedLanguages != null && st.detectedLanguages.docLangTag != null) t.docLang = st.detectedLanguages.docLangTag;
            }
        });
    }

    private void bar(Tab t) {
        if (t != cur) return;
        progress.setProgress(t.progress >= 100 ? 0 : t.progress);
    }

    private void reopen(Tab t) {
        if (t.s != null) { if (t == cur) view.releaseSession(); t.s.close(); }
        t.s = null;
        if (t == cur) select(t);
    }

    private void contextMenu(Tab t, GeckoSession.ContentDelegate.ContextElement e) {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (e.linkUri != null) {
            String l = e.linkUri;
            labels.add("Open in new tab"); acts.add(() -> { newTab(l, t.priv, false); toast("Opened in a new tab"); });
            labels.add(L.t("Copy link")); acts.add(() -> copy(l));
            labels.add(L.t("Share link")); acts.add(() -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, l), null)));
        }
        if (e.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE && e.srcUri != null) {
            String src = e.srcUri;
            labels.add("Open image in new tab"); acts.add(() -> newTab(src, t.priv, true));
            labels.add(L.t("Save image")); acts.add(() -> saveImage(t, src));
            labels.add("Copy image address"); acts.add(() -> copy(src));
        }
        if (labels.isEmpty()) return;
        Ui.dialog(this).setTitle(e.linkUri != null ? bare(e.linkUri) : null)
                .setItems(labels.toArray(new String[0]), (d, i) -> acts.get(i).run()).show();
    }

    void copy(String s) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("link", s));
        toast(L.t("Link copied"));
    }

    private void fullscreen(boolean on) {
        fullscreen = on;
        bar.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) findBar.setVisibility(View.GONE);
        progress.setVisibility(on ? View.GONE : View.VISIBLE);
        WindowInsetsController c = Build.VERSION.SDK_INT >= 30 ? getWindow().getInsetsController() : null;
        if (c != null) {
            if (on) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else c.show(WindowInsets.Type.systemBars());
        } else {
            if (on) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            else bars();
        }
    }

    // ---------------------------------------------------------------- translate, home screen, QR

    /** Firefox's on-device translation into the app language (models download on first use). */
    private void translate(Tab t) {
        TranslationsController.SessionTranslation tr = t.s == null ? null : t.s.getSessionTranslation();
        if (tr == null) { toast(L.t("Translation isn't available")); return; }
        if (t.translated) { tr.restoreOriginalPage(); t.translated = false; return; }
        String to = store.lang();
        String from = t.docLang == null ? null : t.docLang.split("-")[0];
        if (to.equals(from)) to = to.equals("en") ? "ru" : "en";
        final String target = to;
        toast(L.t("Translating…"));
        tr.translate(from, target, null).accept(v -> t.translated = true,
                e -> ui.post(() -> toast(L.t("Couldn't translate this page"))));
    }

    /** Pins the page to the home screen. Sites with a web app manifest open as an app of their own, without the address bar. */
    private void addToHome(Tab t) {
        JSONObject m = t.manifest;
        String name = m != null ? m.optString("short_name", m.optString("name", "")) : "";
        if (name.isEmpty()) name = t.title.isEmpty() ? host(t.url).replaceFirst("^www\\.", "") : t.title;
        String base = t.url;
        String start = m != null && !m.optString("start_url").isEmpty() ? abs(base, m.optString("start_url")) : t.url;
        if (start == null || !start.startsWith("http")) start = t.url;
        boolean app = m != null && !"browser".equals(m.optString("display", "browser"));
        // Icons to try, best first: the manifest's biggest (maskable ones fill the whole adaptive icon), then the usual files.
        List<String[]> cands = new ArrayList<>(); // url, "1" if maskable
        JSONArray icons = m == null ? null : m.optJSONArray("icons");
        for (int i = 0; icons != null && i < icons.length(); i++) {
            JSONObject ic = icons.optJSONObject(i);
            String src = ic == null ? null : abs(base, ic.optString("src"));
            if (src == null || src.endsWith(".svg")) continue;
            int size = 0;
            java.util.regex.Matcher sm = java.util.regex.Pattern.compile("(\\d+)x").matcher(ic.optString("sizes"));
            while (sm.find()) size = Math.max(size, Integer.parseInt(sm.group(1)));
            boolean mask = ic.optString("purpose").contains("maskable");
            cands.add(new String[]{src, mask ? "1" : "", String.valueOf(size + (mask ? 10000 : 0))});
        }
        cands.sort((x, y) -> Integer.parseInt(y[2]) - Integer.parseInt(x[2]));
        String origin = Uri.parse(t.url).getScheme() + "://" + host(t.url);
        cands.add(new String[]{origin + "/apple-touch-icon.png", "", "0"});
        cands.add(new String[]{origin + "/favicon.ico", "", "0"});
        final String startUrl = start;
        ask(L.t(app ? "Install app" : "Add to Home screen"), name, label -> new Thread(() -> {
            android.graphics.Bitmap img = null;
            boolean mask = false;
            for (String[] c : cands) {
                img = fetchBitmap(c[0]);
                if (img != null && img.getWidth() >= 32) { mask = !c[1].isEmpty(); break; }
                img = null;
            }
            final android.graphics.Bitmap fimg = img;
            final boolean fmask = mask;
            ui.post(() -> pin(label, startUrl, app, fimg, fmask));
        }).start());
    }

    private static String abs(String base, String rel) {
        try { return rel == null || rel.isEmpty() ? null : new java.net.URL(new java.net.URL(base), rel).toString(); } catch (Exception e) { return null; }
    }

    private static android.graphics.Bitmap fetchBitmap(String u) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) YukiWeb");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) { return android.graphics.BitmapFactory.decodeStream(in); }
        } catch (Exception e) { return null; }
    }

    private boolean pinned, pinLostFocus;

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (!has) pinLostFocus = true;
    }

    private void pin(String label, String u, boolean app, android.graphics.Bitmap img, boolean maskable) {
        android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) { toast(L.t("Your launcher can't add shortcuts")); return; }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u)).setClass(this, app ? WebAppActivity.class : MainActivity.class)
                .putExtra(WebAppActivity.EXTRA_NAME, label);
        // Adaptive icon: a maskable icon fills it; any other icon (or the site's letter) sits centered on a plain disc.
        int px = 216;
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        if (img != null && maskable) {
            c.drawBitmap(img, null, new android.graphics.Rect(0, 0, px, px), paint);
        } else if (img != null) {
            c.drawColor(Color.WHITE);
            int box = (int) (px * 0.5f), o = (px - box) / 2;
            c.drawBitmap(img, null, new android.graphics.Rect(o, o, o + box, o + box), paint);
        } else {
            c.drawColor(ACC);
            paint.setColor(ON_ACC);
            paint.setTextSize(px * 0.34f);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            String letter = label.substring(0, 1).toUpperCase(Locale.ROOT);
            c.drawText(letter, px / 2f, px / 2f - (paint.descent() + paint.ascent()) / 2, paint);
        }
        // A fresh id each time: re-adding a page whose old shortcut was removed or disabled would otherwise be refused.
        android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(this, "web:" + System.currentTimeMillis())
                .setShortLabel(label.length() > 25 ? label.substring(0, 25) : label).setLongLabel(label)
                .setIcon(android.graphics.drawable.Icon.createWithAdaptiveBitmap(out)).setIntent(i).build();
        String action = getPackageName() + ".PINNED";
        pinned = false;
        pinLostFocus = false;
        android.content.BroadcastReceiver done = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context cx, Intent in) {
                pinned = true;
                toast(L.t("Added to Home screen"));
                try { unregisterReceiver(this); } catch (Exception ignored) {}
            }
        };
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, new android.content.IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(done, new android.content.IntentFilter(action));
        android.app.PendingIntent cb = android.app.PendingIntent.getBroadcast(this, 0, new Intent(action).setPackage(getPackageName()),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        boolean asked;
        try { asked = sm.requestPinShortcut(info, cb.getIntentSender()); } catch (Exception e) { asked = false; }
        // Launchers like MIUI's drop the request silently unless the app may create shortcuts: no dialog took
        // focus and nothing came back, so point at the setting.
        boolean ok = asked;
        ui.postDelayed(() -> {
            if (pinned || isFinishing() || (ok && pinLostFocus)) return;
            Ui.dialog(this).setMessage(L.t("Nothing appeared? Allow YukiWeb to create home screen shortcuts in its app settings."))
                    .setPositiveButton(L.t("App settings"), (d, w) -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("OK", null).show();
        }, 2500);
    }

    static final int REQ_SCAN = com.google.zxing.integration.android.IntentIntegrator.REQUEST_CODE;

    void scanQr() {
        if (editing) { url.clearFocus(); keyboard(url, false); }
        new com.google.zxing.integration.android.IntentIntegrator(this)
                .setDesiredBarcodeFormats(com.google.zxing.integration.android.IntentIntegrator.QR_CODE)
                .setPrompt(L.t("Point at a QR code")).setBeepEnabled(false).setOrientationLocked(true).initiateScan();
    }

    /** A scanned code: links open (SBP codes go straight to the bank app), anything else is offered to copy or search. */
    private void scanned(String v) {
        if (v == null || v.isEmpty()) return;
        if (v.matches("(?i)^https?://\\S+$")) {
            List<android.content.pm.ResolveInfo> apps = appsFor(v);
            if (!apps.isEmpty() && openInApp(v, apps)) return;
            newTab(v, false, true);
            return;
        }
        if (v.matches("(?i)^[a-z][a-z0-9+.-]*:.*") && !v.regionMatches(true, 0, "javascript:", 0, 11)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(v)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception ignored) {}
        }
        Ui.dialog(this).setMessage(v)
                .setPositiveButton(L.t("Search"), (d, w) -> newTab(store.searchUrl(v), false, true))
                .setNegativeButton(L.t("Copy"), (d, w) -> copy(v)).show();
    }

    // ---------------------------------------------------------------- downloads

    /** Saves straight into Downloads from Gecko's own stream (so cookies and logins apply), listed on the Downloads page. */
    private void download(WebResponse r) {
        if (r.body == null) return;
        String mime = r.headers.get("Content-Type"), len = r.headers.get("Content-Length");
        long total = -1;
        try { if (len != null) total = Long.parseLong(len.trim()); } catch (Exception ignored) {}
        save(fileName(r), mime == null ? "application/octet-stream" : mime.split(";")[0].trim(), r.body, total);
    }

    /** Long-press "Save image": data: images (Google thumbnails) are decoded here, the rest fetched through Gecko so cookies apply. */
    private void saveImage(Tab t, String src) {
        if (src.regionMatches(true, 0, "data:", 0, 5)) {
            int comma = src.indexOf(',');
            if (comma < 0) { toast("Download failed"); return; }
            String head = src.substring(5, comma);
            String mime = head.split(";")[0].trim();
            byte[] data = head.endsWith(";base64") ? android.util.Base64.decode(src.substring(comma + 1), android.util.Base64.DEFAULT)
                    : Uri.decode(src.substring(comma + 1)).getBytes();
            save("image", mime.isEmpty() ? "image/png" : mime, new java.io.ByteArrayInputStream(data), data.length);
            return;
        }
        if (src.startsWith("blob:")) { toast("Download failed"); return; }
        WebRequest req = new WebRequest.Builder(src).referrer(t.url).build();
        new GeckoWebExecutor(runtime).fetch(req, t.priv ? GeckoWebExecutor.FETCH_FLAGS_PRIVATE : GeckoWebExecutor.FETCH_FLAGS_NONE)
                .accept(this::download, e -> ui.post(() -> toast("Download failed: " + e.getMessage())));
    }

    private void save(String name0, String mime, InputStream body, long total) {
        // Image CDNs often serve paths like /images?q=… with no extension; add one so the gallery picks it up.
        String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        String name = !name0.contains(".") && ext != null ? name0 + "." + ext : name0;
        toast("Downloading " + name);
        final String type = mime;
        final long at = System.currentTimeMillis();
        dlUpdate(at, name, "", type, 0, "run");
        android.app.NotificationManager nm = dlChannel();
        int id = (int) (at & 0x7fffffff);
        Notification.Builder nb = new Notification.Builder(this, "downloads").setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(name).setOnlyAlertOnce(true).setOngoing(true).setProgress(100, 0, total <= 0);
        nm.notify(id, nb.build());
        new Thread(() -> {
            Uri dest = null;
            long size = 0;
            try (InputStream in = body) {
                OutputStream out;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, type);
                    dest = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    out = getContentResolver().openOutputStream(dest);
                } else {
                    java.io.File f = new java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                    dest = FileProvider.getUriForFile(this, "lol.yukichat.web.files", f);
                    out = new java.io.FileOutputStream(f);
                }
                try (OutputStream o = out) {
                    byte[] buf = new byte[64 * 1024];
                    long shown = 0;
                    for (int n; (n = in.read(buf)) > 0; ) {
                        o.write(buf, 0, n);
                        size += n;
                        long now = System.currentTimeMillis();
                        if (now - shown > 700) {
                            shown = now;
                            nb.setContentText(android.text.format.Formatter.formatShortFileSize(this, size)
                                    + (total > 0 ? " / " + android.text.format.Formatter.formatShortFileSize(this, total) : ""));
                            if (total > 0) nb.setProgress(100, (int) (size * 100 / total), false);
                            nm.notify(id, nb.build());
                        }
                    }
                }
                final Uri u = dest;
                final long s = size;
                // Done: tapping the notification opens the file.
                Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(u, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                nm.notify(id, new Notification.Builder(this, "downloads").setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(name).setContentText(L.t("Download complete") + " · " + android.text.format.Formatter.formatShortFileSize(this, s))
                        .setAutoCancel(true).setContentIntent(android.app.PendingIntent.getActivity(this, id, view,
                                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT)).build());
                ui.post(() -> { dlUpdate(at, name, u.toString(), type, s, "ok"); toast("Saved to Downloads: " + name); });
            } catch (Exception e) {
                final Uri u = dest;
                nm.notify(id, new Notification.Builder(this, "downloads").setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentTitle(name).setContentText(L.t("Download failed")).setAutoCancel(true).build());
                ui.post(() -> { dlUpdate(at, name, u == null ? "" : u.toString(), type, 0, "fail"); toast("Download failed: " + e.getMessage()); });
            }
        }).start();
    }

    private android.app.NotificationManager dlChannel() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        nm.createNotificationChannel(new android.app.NotificationChannel("downloads", L.t("Downloads"), android.app.NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERM + 100);
        return nm;
    }

    private void dlUpdate(long at, String name, String uri, String mime, long size, String st) {
        JSONArray all = store.downloads(), out = new JSONArray();
        for (int i = 0; i < all.length(); i++) if (all.optJSONObject(i).optLong("at") != at) out.put(all.opt(i));
        try { out.put(new JSONObject().put("at", at).put("n", name).put("u", uri).put("m", mime).put("s", size).put("st", st)); } catch (Exception ignored) {}
        while (out.length() > 200) out.remove(0);
        store.setDownloads(out);
        screens.refreshIf(Screens.DL_TAG);
    }

    private static String fileName(WebResponse r) {
        String cd = r.headers.get("Content-Disposition");
        if (cd != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)").matcher(cd);
            if (m.find()) return Uri.decode(m.group(1));
        }
        String last = Uri.parse(r.uri).getLastPathSegment();
        return last == null || last.isEmpty() ? "download" : last;
    }

    // ---------------------------------------------------------------- prompts & permissions plumbing

    void pickFile(GeckoSession.PromptDelegate.FilePrompt p, GeckoResult<GeckoSession.PromptDelegate.PromptResponse> res) {
        filePrompt = p;
        fileResult = res;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        if (p.mimeTypes != null && p.mimeTypes.length > 0) i.putExtra(Intent.EXTRA_MIME_TYPES, p.mimeTypes);
        if (p.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, REQ_FILE); }
        catch (Exception e) { res.complete(p.dismiss()); filePrompt = null; }
    }

    void androidPermissions(String[] perms, GeckoSession.PermissionDelegate.Callback cb) {
        List<String> need = new ArrayList<>();
        for (String p : perms) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (need.isEmpty()) { cb.grant(); return; }
        permCallback = cb;
        requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        if (req == REQ_SCAN) {
            com.google.zxing.integration.android.IntentResult r = com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(req, code, data);
            if (r != null) scanned(r.getContents());
            return;
        }
        if (req == REQ_UNLOCK) { if (code == RESULT_OK) screens.passwordsUnlocked(); return; }
        super.onActivityResult(req, code, data);
        if (req == REQ_ROLE) { screens.refreshIf(Screens.SETTINGS); return; }
        if (req != REQ_FILE || filePrompt == null) return;
        GeckoSession.PromptDelegate.FilePrompt p = filePrompt;
        filePrompt = null;
        if (code != RESULT_OK || data == null) { fileResult.complete(p.dismiss()); return; }
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        fileResult.complete(uris.isEmpty() ? p.dismiss() : p.confirm(this, uris.toArray(new Uri[0])));
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req != REQ_PERM || permCallback == null) return;
        boolean ok = true;
        for (int r : results) ok &= r == PackageManager.PERMISSION_GRANTED;
        if (ok) permCallback.grant(); else permCallback.reject();
        permCallback = null;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (appUrl() != null) return;
        handle(i);
    }

    /** Links from other apps, shared text, web searches. */
    private void handle(Intent i) {
        if (i == null) return;
        String a = i.getAction();
        if (Updater.ACTION_STATUS.equals(a)) { Updater.status(this, i); return; }
        if (Intent.ACTION_VIEW.equals(a) && i.getData() != null) newTab(i.getDataString(), false, true);
        else if (Intent.ACTION_WEB_SEARCH.equals(a)) {
            String q = i.getStringExtra("query");
            if (q != null) newTab(resolve(q), false, true);
        } else if (Intent.ACTION_SEND.equals(a)) {
            String t = i.getStringExtra(Intent.EXTRA_TEXT);
            if (t != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(t);
                newTab(m.find() ? m.group() : resolve(t), false, true);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreen && cur != null) { cur.s.exitFullScreen(); return; }
        if (screens.open()) { screens.pop(); return; }
        if (findBar.getVisibility() == View.VISIBLE) { find(false); return; }
        if (tabsPanel.getVisibility() == View.VISIBLE) { showTabs(false); return; }
        if (editing) { url.clearFocus(); keyboard(url, false); return; }
        if (cur != null && cur.back) { cur.s.goBack(); return; }
        // A tab a link opened has no history of its own: back closes it and returns to the opener.
        if (cur != null && cur.opener != null && tabs.contains(cur.opener)) { Tab o = cur.opener; close(cur); select(o); return; }
        moveTaskToBack(true);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        saveSession();
        sync.now();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
