package lol.yukichat.web;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.StorageController;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.function.Supplier;

import static lol.yukichat.web.Ui.*;

/** Full-screen pages over the browser: Settings, History, Bookmarks, Downloads. */
final class Screens {
    private final MainActivity a;
    private final FrameLayout host;
    private final List<Supplier<View>> builders = new ArrayList<>();
    private final List<View> views = new ArrayList<>();

    Screens(MainActivity a, FrameLayout host) {
        this.a = a;
        this.host = host;
    }

    boolean open() { return !views.isEmpty(); }

    static final Object SETTINGS = new Object(), DL_TAG = new Object();

    private void push(Object tag, Supplier<View> b) { push(tag, b, true); }

    private void push(Object tag, Supplier<View> b, boolean animate) {
        Supplier<View> tagged = () -> { View v = b.get(); v.setTag(tag); return v; };
        View v = tagged.get();
        builders.add(tagged);
        views.add(v);
        host.addView(v);
        if (!animate) return;
        v.setTranslationX(host.getWidth());
        v.animate().translationX(0).setDuration(200).start();
    }

    void pop() {
        if (views.isEmpty()) return;
        View v = views.remove(views.size() - 1);
        builders.remove(builders.size() - 1);
        v.animate().translationX(host.getWidth()).setDuration(180).withEndAction(() -> host.removeView(v)).start();
    }

    void closeAll() {
        for (View v : views) host.removeView(v);
        views.clear();
        builders.clear();
    }

    /** Rebuilds the top page after something on it changed. */
    void refresh() {
        if (views.isEmpty()) return;
        int i = views.size() - 1;
        View old = views.get(i), v = builders.get(i).get();
        views.set(i, v);
        host.addView(v, host.indexOfChild(old));
        host.removeView(old);
    }

    private int dp(float v) { return Ui.dp(a, v); }

    /** Header with a back arrow and a title, then the content. */
    private LinearLayout page(String title, View content, ImageView... actions) {
        LinearLayout p = new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(BG);
        p.setClickable(true);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(6), dp(6), dp(6), dp(6));
        head.addView(iconButton(a, R.drawable.i_back, 44, v -> pop()));
        TextView t = text(a, title, 20, TEXT, true);
        t.setPadding(dp(8), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (ImageView x : actions) head.addView(x);
        p.addView(head);
        p.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return p;
    }

    private TextView empty(String s) {
        TextView t = text(a, s, 15, DIM, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(32), dp(80), dp(32), 0);
        return t;
    }

    private void open(String u) {
        closeAll();
        a.load(u);
    }

    // ---------------------------------------------------------------- settings

    void settings() { settings(true); }
    void settings(boolean animate) { push(SETTINGS, this::buildSettings, animate); }

    private View buildSettings() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(0, 0, 0, dp(32));

        // Yuki ID card
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(a, PAGE, 20));
        card.setPadding(dp(18), dp(18), dp(18), dp(16));
        LinearLayout who = new LinearLayout(a);
        who.setGravity(Gravity.CENTER_VERTICAL);
        boolean in = !a.store.token().isEmpty();
        JSONObject p = a.store.profile();
        String name = p.optString("display_name", p.optString("username"));
        TextView av = badge(a, in ? name : "?", 48);
        av.setBackground(round(a, in ? ACC : SOFT2, 24));
        av.setTextColor(ON_ACC);
        if (!in) av.setText("");
        who.addView(av);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(a, in ? name : "Yuki ID", 18, TEXT, true));
        String user = p.optString("username");
        col.addView(text(a, in ? (user.isEmpty() ? "" : "@" + user + " · ") + (a.store.syncStatus().isEmpty() ? "Signed in" : a.store.syncStatus())
                : "Sign in to sync with YukiWeb on your PC", 13, DIM, false));
        who.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(who);
        TextView note = text(a, in ? "History, suggestions, bookmarks, search engine and look sync with every YukiWeb signed in to this Yuki ID."
                : "Your history and address suggestions, bookmarks and search engine come along to the phone.", 13, DIM, false);
        note.setPadding(0, dp(12), 0, dp(12));
        card.addView(note);
        LinearLayout btns = new LinearLayout(a);
        if (in) {
            btns.addView(button("Sync now", true, v -> a.sync.now()));
            btns.addView(button("Sign out", false, v -> Ui.dialog(a).setMessage("Sign out of Yuki ID on this phone?")
                    .setPositiveButton("Sign out", (d, w) -> { a.signOut(); refresh(); }).setNegativeButton("Cancel", null).show()));
        } else btns.addView(button("Sign in with Yuki ID", true, v -> { closeAll(); a.account(); }));
        card.addView(btns);
        FrameLayout cardWrap = new FrameLayout(a);
        cardWrap.setPadding(dp(16), dp(4), dp(16), dp(4));
        cardWrap.addView(card);
        c.addView(cardWrap);

        c.addView(section(a, L.t("Your stuff")));
        c.addView(row(a, R.drawable.i_bookmark, L.t("Bookmarks"), null, null, v -> bookmarks()));
        c.addView(row(a, R.drawable.i_history, L.t("History"), null, null, v -> history()));
        c.addView(row(a, R.drawable.i_download, L.t("Downloads"), null, null, v -> downloads()));
        c.addView(row(a, R.drawable.i_key, L.t("Passwords"), L.t("Saved on this phone, encrypted"), String.valueOf(MainActivity.logins.list().length()), v -> passwords()));
        c.addView(row(a, R.drawable.i_shield, L.t("Site settings"), L.t("Permissions, apps, site data"), null, v -> sites()));

        c.addView(section(a, "General"));
        String lang = a.store.raw("lang").replace("\"", "");
        c.addView(row(a, R.drawable.i_translate, L.t("Language"), null,
                lang.equals("ru") ? "Русский" : lang.equals("de") ? "Deutsch" : lang.equals("en") ? "English" : L.t("Phone language"), v -> language()));
        String[] th = Ui.theme(a.store.themeObj().optString("name", "light"));
        c.addView(row(a, R.drawable.i_palette, "Appearance", "Theme and accent color", a.store.themeSystem() ? "Match phone" : th[1], v -> appearance(true)));
        c.addView(row(a, R.drawable.i_search, "Search engine", null, a.engineName(), v -> a.engines(this::refresh)));
        boolean def = isDefault();
        c.addView(row(a, R.drawable.i_globe, "Default browser", def ? "Links from other apps open in YukiWeb" : "Open links from other apps here",
                def ? "On" : "Off", v -> a.makeDefault()));

        c.addView(section(a, "Privacy"));
        c.addView(row(a, R.drawable.i_history, "Clear history", "Also clears address suggestions on synced devices", null,
                v -> confirm("Clear all history?", "Clear", () -> { a.store.clearHist(); a.toast("History cleared"); })));
        c.addView(row(a, R.drawable.i_shield, "Clear cookies and site data", "Signs you out of websites", null,
                v -> confirm("Clear cookies and site data?", "Clear", () -> a.clearSiteData(StorageController.ClearFlags.COOKIES | StorageController.ClearFlags.SITE_DATA
                        | StorageController.ClearFlags.DOM_STORAGES | StorageController.ClearFlags.PERMISSIONS))));
        c.addView(row(a, R.drawable.i_trash, "Clear cache", null, null,
                v -> a.clearSiteData(StorageController.ClearFlags.ALL_CACHES)));

        c.addView(section(a, "About"));
        JSONObject up = Updater.available;
        if (up != null) c.addView(row(a, R.drawable.i_update, "Update to " + up.optString("version_name"), "Installs over this version, nothing is lost", null,
                v -> Updater.offer(a, up)));
        else c.addView(row(a, R.drawable.i_update, "Check for updates", null, null, v -> Updater.check(a, true)));
        c.addView(row(a, R.drawable.i_info, "YukiWeb", "Android " + Build.VERSION.RELEASE, BuildConfig.VERSION_NAME, null));

        ScrollView s = new ScrollView(a);
        s.addView(c);
        return page("Settings", s);
    }

    private void language() {
        String[] names = {L.t("Phone language"), "English", "Русский", "Deutsch"}, codes = {"", "en", "ru", "de"};
        Ui.dialog(a).setTitle(L.t("Language")).setItems(names, (d, i) -> {
            // Synced like on the PC: the key holds a JSON string.
            a.store.put("lang", codes[i].isEmpty() ? "" : "\"" + codes[i] + "\"", true);
            L.lang = a.store.lang();
            a.languageChanged();
            refresh();
        }).show();
    }

    // ---------------------------------------------------------------- passwords

    /** Shown only after the phone's own screen lock, when it has one. */
    void passwords() {
        android.app.KeyguardManager km = a.getSystemService(android.app.KeyguardManager.class);
        Intent i = km != null && km.isDeviceSecure() ? km.createConfirmDeviceCredentialIntent(L.t("Passwords"), L.t("Unlock to see saved passwords")) : null;
        if (i == null) passwordsUnlocked(); else a.startActivityForResult(i, 10);
    }

    void passwordsUnlocked() { push(null, this::buildPasswords); }

    private View buildPasswords() {
        JSONArray l = MainActivity.logins.list();
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < l.length(); i++) items.add(l.optJSONObject(i));
        items.sort((x, y) -> Logins.host((JSONObject) x).compareTo(Logins.host((JSONObject) y)));
        if (items.isEmpty()) return page(L.t("Passwords"), empty(L.t("When you sign in on a site, YukiWeb offers to save the password here.")));
        ListView lv = list(new Rows(items) {
            @Override View entry(JSONObject o) {
                String h = Logins.host(o);
                LinearLayout r = row(a, 0, h, o.optString("u").isEmpty() ? "(" + L.t("no username") + ")" : o.optString("u"), null, null);
                r.addView(badge(a, h, 36), 0);
                return r;
            }
        });
        lv.setOnItemClickListener((p, v, i, id) -> {
            JSONObject o = (JSONObject) items.get(i);
            Ui.dialog(a).setTitle(Logins.host(o)).setMessage(o.optString("u") + "\n" + o.optString("p"))
                    .setPositiveButton(L.t("Copy password"), (d, w) -> a.copy(o.optString("p")))
                    .setNeutralButton(L.t("Delete"), (d, w) -> confirm(L.t("Delete this password?"), L.t("Delete"),
                            () -> { MainActivity.logins.remove(o.optString("g")); refresh(); }))
                    .setNegativeButton(L.t("Copy username"), (d, w) -> a.copy(o.optString("u"))).show();
        });
        return page(L.t("Passwords"), lv);
    }

    // ---------------------------------------------------------------- site settings

    void sites() { push(null, this::buildSites); }

    private static String permName(int p) {
        switch (p) {
            case GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION: return L.t("Location");
            case GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION: return L.t("Notifications");
            case GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS: return L.t("Cookies across sites");
            case GeckoSession.PermissionDelegate.PERMISSION_XR: return "VR";
            default: return null;
        }
    }

    /** Every site with a remembered permission or an "always open in app" choice. */
    private View buildSites() {
        FrameLayout box = new FrameLayout(a);
        box.addView(empty(L.t("Loading…")));
        a.runtimeStorage().getAllPermissions().accept(perms -> a.runOnUiThread(() -> {
            java.util.TreeMap<String, List<GeckoSession.PermissionDelegate.ContentPermission>> by = new java.util.TreeMap<>();
            if (perms != null) for (GeckoSession.PermissionDelegate.ContentPermission p : perms) {
                if (p.privateMode || permName(p.permission) == null) continue;
                String h = MainActivity.host(p.uri);
                if (!h.isEmpty()) by.computeIfAbsent(h, k -> new ArrayList<>()).add(p);
            }
            for (String h : a.store.appAlways()) by.computeIfAbsent(h, k -> new ArrayList<>());
            box.removeAllViews();
            if (by.isEmpty()) { box.addView(empty(L.t("Sites you give permissions to, or set to open in their app, show up here."))); return; }
            List<Object> items = new ArrayList<>();
            for (String h : by.keySet()) {
                try { items.add(new JSONObject().put("h", h)); } catch (Exception ignored) {}
            }
            ListView lv = list(new Rows(items) {
                @Override View entry(JSONObject o) {
                    String h = o.optString("h");
                    List<String> what = new ArrayList<>();
                    for (GeckoSession.PermissionDelegate.ContentPermission p : by.get(h))
                        what.add(permName(p.permission) + (p.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW ? " ✓" : " ✕"));
                    if (a.store.appAlways().contains(h)) what.add(L.t("Opens in app"));
                    LinearLayout r = row(a, 0, h, joined(what), null, null);
                    r.addView(badge(a, h, 36), 0);
                    return r;
                }
            });
            lv.setOnItemClickListener((p, v, i, id) -> site(((JSONObject) items.get(i)).optString("h"), by.get(((JSONObject) items.get(i)).optString("h"))));
            box.addView(lv);
        }), e -> {});
        return page(L.t("Site settings"), box);
    }

    private static String joined(List<String> l) { return android.text.TextUtils.join(" · ", l); }

    private void site(String h, List<GeckoSession.PermissionDelegate.ContentPermission> perms) {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        for (GeckoSession.PermissionDelegate.ContentPermission p : perms) {
            boolean on = p.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW;
            labels.add(permName(p.permission) + ": " + L.t(on ? "Allowed" : "Blocked") + " → " + L.t(on ? "Block" : "Allow"));
            acts.add(() -> a.runtimeStorage().setPermission(p, on ? GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                    : GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW));
            labels.add(permName(p.permission) + ": " + L.t("Ask every time"));
            acts.add(() -> a.runtimeStorage().setPermission(p, GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT));
        }
        if (a.store.appAlways().contains(h)) {
            labels.add(L.t("Stop opening links in the app"));
            acts.add(() -> a.store.setAppAlways(h, false));
        }
        labels.add(L.t("Clear cookies and data for this site"));
        acts.add(() -> confirm(L.t("Clear cookies and data for") + " " + h + "?", L.t("Clear"), () -> {
            a.runtimeStorage().clearDataFromHost(h, StorageController.ClearFlags.ALL);
            a.toast(L.t("Cleared"));
        }));
        Ui.dialog(a).setTitle(h).setItems(labels.toArray(new String[0]), (d, i) -> { acts.get(i).run(); refresh(); }).show();
    }

    // ---------------------------------------------------------------- appearance

    void appearance(boolean animate) { push(null, this::buildAppearance, animate); }

    private View buildAppearance() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(0, 0, 0, dp(32));
        JSONObject cur = a.store.themeObj();
        boolean sys = a.store.themeSystem();
        String name = cur.optString("name", "light"), acc = cur.optString("acc", "");

        c.addView(section(a, "Theme"));
        List<View> cards = new ArrayList<>();
        cards.add(themeCard(null, "Match phone", sys, v -> { a.store.setThemeSystem(true); a.restyle(true); }));
        for (String[] t : Ui.THEMES)
            cards.add(themeCard(t, t[1], !sys && t[0].equals(name), v -> {
                a.store.setThemeSystem(false);
                setTheme("name", t[0]);
            }));
        for (int i = 0; i < cards.size(); i += 3) {
            LinearLayout line = new LinearLayout(a);
            line.setPadding(dp(12), 0, dp(12), 0);
            for (int j = i; j < i + 3; j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                line.addView(j < cards.size() ? cards.get(j) : new View(a), lp);
            }
            c.addView(line);
        }

        c.addView(section(a, "Accent color"));
        LinearLayout dots = new LinearLayout(a);
        dots.setPadding(dp(14), dp(4), dp(14), 0);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        TextView def = text(a, "Auto", 13, acc.isEmpty() ? ON_ACC : TEXT, true);
        def.setGravity(Gravity.CENTER);
        def.setPadding(dp(12), 0, dp(12), 0);
        def.setBackground(press(a, acc.isEmpty() ? ACC : SOFT, 18));
        def.setOnClickListener(v -> setTheme("acc", null));
        dots.addView(def, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        for (String hx : Ui.ACCENTS) {
            View d = new View(a);
            android.graphics.drawable.GradientDrawable g = round(a, Ui.color(hx), 18);
            if (hx.equalsIgnoreCase(acc)) g.setStroke(dp(3), TEXT);
            d.setBackground(g);
            d.setOnClickListener(v -> setTheme("acc", hx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            lp.setMargins(dp(6), 0, 0, 0);
            FrameLayout f = new FrameLayout(a);
            f.addView(d, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
            dots.addView(f, lp);
        }
        c.addView(dots);

        TextView note = text(a, sys ? "Match phone follows your phone's light or dark mode. Pick a theme to use it on your PC too."
                : "Theme and accent sync with YukiWeb on your PC through Yuki ID.", 13, DIM, false);
        note.setPadding(dp(18), dp(22), dp(18), 0);
        c.addView(note);
        ScrollView s = new ScrollView(a);
        s.addView(c);
        return page("Appearance", s);
    }

    private void setTheme(String k, String v) {
        JSONObject t = a.store.themeObj();
        try { if (v == null) t.remove(k); else t.put(k, v); } catch (Exception ignored) {}
        if (!t.has("name")) try { t.put("name", "light"); } catch (Exception ignored) {}
        a.store.put("theme", t.toString(), true);
        a.restyle(true);
    }

    /** A little picture of the theme: page color, a bar in the surface color, an accent dot. */
    private View themeCard(String[] t, String label, boolean on, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(6), dp(6), dp(6), dp(10));
        col.setOnClickListener(l);
        FrameLayout pic = new FrameLayout(a);
        android.graphics.drawable.GradientDrawable frame = round(a, 0, 16);
        frame.setStroke(dp(on ? 3 : 1), on ? ACC : LINE);
        pic.setForeground(frame);
        pic.setClipToOutline(true);
        pic.setBackground(round(a, 0xffffffff, 16));
        if (t == null) {
            LinearLayout halves = new LinearLayout(a);
            halves.addView(mini(Ui.theme("light")), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            halves.addView(mini(Ui.theme("dark")), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            pic.addView(halves);
        } else pic.addView(mini(t));
        col.addView(pic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));
        TextView n = text(a, label, 13, on ? TEXT : DIM, on);
        n.setPadding(0, dp(8), 0, 0);
        col.addView(n);
        return col;
    }

    private View mini(String[] t) {
        LinearLayout m = new LinearLayout(a);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setBackgroundColor(Ui.color(t[9]));
        m.setPadding(dp(8), dp(10), dp(8), dp(8));
        View bar = new View(a);
        bar.setBackground(round(a, Ui.color(t[6]), 6));
        m.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        View dot = new View(a);
        dot.setBackground(round(a, Ui.color(t[2]), 9));
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(18), dp(18));
        dp2.setMargins(0, dp(14), 0, 0);
        m.addView(dot, dp2);
        return m;
    }

    private TextView button(String label, boolean primary, View.OnClickListener l) {
        TextView t = text(a, label, 14, primary ? ON_ACC : TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setBackground(press(a, primary ? ACC : SOFT2, 20));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void confirm(String msg, String yes, Runnable r) {
        Ui.dialog(a).setMessage(msg).setPositiveButton(yes, (d, w) -> { r.run(); refresh(); }).setNegativeButton("Cancel", null).show();
    }

    private boolean isDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = a.getSystemService(RoleManager.class);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_BROWSER);
        }
        android.content.pm.ResolveInfo r = a.getPackageManager().resolveActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), 0);
        return r != null && a.getPackageName().equals(r.activityInfo.packageName);
    }

    // ---------------------------------------------------------------- lists

    /** A list of rows where some items are day headers (String) and the rest are entries. */
    private abstract class Rows extends BaseAdapter {
        final List<Object> items;
        Rows(List<Object> items) { this.items = items; }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean isEnabled(int i) { return !(items.get(i) instanceof String); }
        @Override public View getView(int i, View old, ViewGroup parent) {
            Object o = items.get(i);
            return o instanceof String ? section(a, (String) o) : entry((JSONObject) o);
        }
        abstract View entry(JSONObject o);
    }

    private ListView list(BaseAdapter ad) {
        ListView l = new ListView(a);
        l.setDivider(null);
        l.setAdapter(ad);
        return l;
    }

    private static String day(long at) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long today = c.getTimeInMillis();
        if (at >= today) return "Today";
        if (at >= today - 86_400_000L) return "Yesterday";
        return DateUtils.formatDateTime(null, at, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY);
    }

    private LinearLayout siteRow(String title, String u, String trail) {
        String bare = u.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        LinearLayout r = row(a, 0, title.isEmpty() ? bare : title, bare, trail, null);
        r.addView(badge(a, MainActivity.host(u), 36), 0);
        return r;
    }

    void history() { push(null, this::buildHistory); }

    private View buildHistory() {
        JSONArray h = a.store.hist();
        List<Object> items = new ArrayList<>();
        String last = null;
        for (int i = 0; i < h.length(); i++) {
            JSONObject o = h.optJSONObject(i);
            if (o == null || !o.optString("u").startsWith("http") || o.optString("u").contains("tauri.localhost")) continue;
            String d = day(o.optLong("at"));
            if (!d.equals(last)) { items.add(d); last = d; }
            items.add(o);
        }
        ImageView clear = iconButton(a, R.drawable.i_trash, 44, v -> confirm("Clear all history?", "Clear", a.store::clearHist));
        if (items.isEmpty()) return page("History", empty("Pages you visit here and on your PC show up here."));
        ListView l = list(new Rows(items) {
            @Override View entry(JSONObject o) {
                return siteRow(o.optString("t"), o.optString("u"),
                        DateUtils.formatDateTime(a, o.optLong("at"), DateUtils.FORMAT_SHOW_TIME));
            }
        });
        l.setOnItemClickListener((p, v, i, id) -> open(((JSONObject) items.get(i)).optString("u")));
        l.setOnItemLongClickListener((p, v, i, id) -> {
            String u = ((JSONObject) items.get(i)).optString("u");
            entryMenu(u, "Delete from history", () -> { a.store.removeHist(u); refresh(); });
            return true;
        });
        return page("History", l, clear);
    }

    void bookmarks() { push(null, this::buildBookmarks); }

    private View buildBookmarks() {
        JSONArray b = a.store.bmFlat();
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < b.length(); i++) items.add(b.optJSONObject(i));
        if (items.isEmpty()) return page("Bookmarks", empty("Tap the star in the menu to bookmark a page. Bookmarks from your PC show up here too."));
        ListView l = list(new Rows(items) {
            @Override View entry(JSONObject o) { return siteRow(o.optString("t"), o.optString("u"), null); }
        });
        l.setOnItemClickListener((p, v, i, id) -> open(((JSONObject) items.get(i)).optString("u")));
        l.setOnItemLongClickListener((p, v, i, id) -> {
            String u = ((JSONObject) items.get(i)).optString("u");
            entryMenu(u, "Delete bookmark", () -> { a.store.removeBookmark(u); refresh(); });
            return true;
        });
        return page("Bookmarks", l);
    }

    private void entryMenu(String u, String del, Runnable delete) {
        String[] labels = {"Open in new tab", "Copy link", del};
        Ui.dialog(a).setTitle(u.replaceFirst("^https?://", "")).setItems(labels, (d, w) -> {
            if (w == 0) { closeAll(); a.newTab(u, false, true); }
            else if (w == 1) a.copy(u);
            else delete.run();
        }).show();
    }

    void downloads() { push(DL_TAG, this::buildDownloads); }

    private View buildDownloads() {
        JSONArray d = a.store.downloads();
        List<Object> items = new ArrayList<>();
        String last = null;
        for (int i = d.length() - 1; i >= 0; i--) {
            JSONObject o = d.optJSONObject(i);
            String day = day(o.optLong("at"));
            if (!day.equals(last)) { items.add(day); last = day; }
            items.add(o);
        }
        ImageView folder = iconButton(a, R.drawable.i_file, 44, v -> {
            try { a.startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)); } catch (Exception e) { a.toast("No file manager found"); }
        });
        if (items.isEmpty()) return page("Downloads", empty("Files you download are saved to the Downloads folder and listed here."), folder);
        ListView l = list(new Rows(items) {
            @Override View entry(JSONObject o) {
                String st = o.optString("st"), sub;
                if (st.equals("run")) sub = "Downloading…" + (o.optLong("s") > 0 ? " " + Formatter.formatShortFileSize(a, o.optLong("s")) : "");
                else if (st.equals("fail")) sub = "Failed";
                else sub = Formatter.formatShortFileSize(a, o.optLong("s")) + " · " + DateUtils.formatDateTime(a, o.optLong("at"), DateUtils.FORMAT_SHOW_TIME);
                return row(a, R.drawable.i_file, o.optString("n"), sub, null, null);
            }
        });
        l.setOnItemClickListener((p, v, i, id) -> {
            JSONObject o = (JSONObject) items.get(i);
            if (!o.optString("st").equals("ok")) return;
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(o.optString("u")), o.optString("m"))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (Exception e) { a.toast("No app can open this file"); }
        });
        l.setOnItemLongClickListener((p, v, i, id) -> {
            JSONObject o = (JSONObject) items.get(i);
            Ui.dialog(a).setTitle(o.optString("n")).setItems(new String[]{"Remove from list", "Delete file"}, (dd, w) -> {
                if (w == 1) try { a.getContentResolver().delete(Uri.parse(o.optString("u")), null, null); } catch (Exception ignored) {}
                JSONArray all = a.store.downloads(), keep = new JSONArray();
                for (int k = 0; k < all.length(); k++) if (all.optJSONObject(k).optLong("at") != o.optLong("at")) keep.put(all.opt(k));
                a.store.setDownloads(keep);
                refresh();
            }).show();
            return true;
        });
        return page("Downloads", l, folder);
    }

    /** Refreshes the top page if it's that one (a finished download, a sync). */
    void refreshIf(Object tag) { if (!views.isEmpty() && views.get(views.size() - 1).getTag() == tag) refresh(); }
}
