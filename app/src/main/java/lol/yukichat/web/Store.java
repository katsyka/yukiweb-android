package lol.yukichat.web;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Everything YukiWeb remembers, in the same shapes as the PC version's localStorage so sync can
 * move it between them untouched: synced keys hold the exact JSON text the PC stores.
 *   bm    [{u, t, i}] or folders {t, c: [...]}
 *   hist  [{u, t, n (visits), at (ms)}], newest first
 *   search "yuki" | "google" | "yandex" | "bing" | "ddg"
 */
final class Store {
    static final String[] SYNC_KEYS = {"bm", "bmbar", "theme", "wall", "search", "sleep", "hist", "lang"};

    /** Language for the app and home page: the synced choice (Settings on PC), else the phone's. */
    String lang() {
        String v = raw("lang").replace("\"", "");
        if (v.equals("en") || v.equals("ru") || v.equals("de")) return v;
        String l = java.util.Locale.getDefault().getLanguage();
        return l.equals("ru") || l.equals("de") ? l : "en";
    }

    /** This phone's id for "send tab" (the PC skips what it sent itself, and so do we). */
    String dev() {
        String d = p.getString("dev", "");
        if (d.isEmpty()) {
            d = "a" + Long.toString(new java.util.Random().nextLong() & Long.MAX_VALUE, 36).substring(0, 7);
            p.edit().putString("dev", d).apply();
        }
        return d;
    }

    /** Last /api/yukiweb/dash answer: chats, mail, music, tabs sent from other devices. */
    String dash() { return p.getString("dash", ""); }
    void setDash(String v) { p.edit().putString("dash", v).apply(); }

    // Sites whose links open straight in their app ("Always" on the open-in-app bar).
    java.util.Set<String> appAlways() { return new java.util.TreeSet<>(p.getStringSet("app_always", java.util.Collections.emptySet())); }
    void setAppAlways(String host, boolean on) {
        java.util.Set<String> s = appAlways();
        if (on) s.add(host); else s.remove(host);
        p.edit().putStringSet("app_always", s).apply();
    }
    boolean inboxSeen(long id) { return p.getBoolean("inbox_" + id, false); }
    void setInboxSeen(long id) { p.edit().putBoolean("inbox_" + id, true).apply(); }
    private static final int HIST_MAX = 3000;

    private final SharedPreferences p;
    private JSONArray hist, bm;

    Store(Context c) { p = c.getSharedPreferences("yw", Context.MODE_PRIVATE); }

    // --- synced values (raw JSON text) ---
    String raw(String k) { return p.getString("k_" + k, ""); }

    /** local = the user changed it here (sync must push it); false = it came from sync. */
    void put(String k, String v, boolean local) {
        SharedPreferences.Editor e = p.edit();
        if (v == null || v.isEmpty()) e.remove("k_" + k); else e.putString("k_" + k, v);
        if (local) {
            e.putLong("at_" + k, System.currentTimeMillis());
            e.putBoolean("dirty_" + k, true);
        }
        e.apply();
        if (k.equals("hist")) hist = null;
        if (k.equals("bm")) bm = null;
        if (local && listener != null) listener.run();
    }

    long at(String k) { return p.getLong("at_" + k, 0); }
    void setAt(String k, long at) { p.edit().putLong("at_" + k, at).apply(); }
    boolean dirty(String k) { return p.getBoolean("dirty_" + k, false); }
    void setDirty(String k, boolean d) { p.edit().putBoolean("dirty_" + k, d).apply(); }

    /** Called after any local change to a synced key (the sync schedules a push). */
    Runnable listener;

    // --- look: "Match phone" (light/dark with the system) stays on this phone; a picked theme syncs ---
    boolean themeSystem() { return p.getBoolean("theme_system", true); }
    void setThemeSystem(boolean on) { p.edit().putBoolean("theme_system", on).apply(); }
    JSONObject themeObj() {
        try { return new JSONObject(raw("theme").isEmpty() ? "{}" : raw("theme")); } catch (JSONException e) { return new JSONObject(); }
    }

    // --- sync bookkeeping ---
    long since() { return p.getLong("since", 0); }
    boolean first() { return p.getBoolean("first", true); }
    void synced(long now) { p.edit().putLong("since", now).putBoolean("first", false).apply(); }
    void resetSync() {
        SharedPreferences.Editor e = p.edit().putLong("since", 0).putBoolean("first", true);
        for (String k : SYNC_KEYS) e.remove("at_" + k).remove("dirty_" + k);
        e.apply();
    }
    String syncStatus() { return p.getString("sync_status", ""); }
    void setSyncStatus(String s) { p.edit().putString("sync_status", s).apply(); }

    // --- account (Yuki ID) ---
    String token() { return p.getString("token", ""); }
    JSONObject profile() {
        try { return new JSONObject(p.getString("profile", "{}")); } catch (JSONException e) { return new JSONObject(); }
    }
    void setAccount(String token, JSONObject profile) {
        p.edit().putString("token", token == null ? "" : token).putString("profile", profile == null ? "{}" : profile.toString()).apply();
    }

    // --- open tabs, restored on the next start ---
    String session() { return p.getString("session", "[]"); }
    void setSession(String s) { p.edit().putString("session", s).apply(); }

    // --- search ---
    String engine() {
        String s = raw("search").replace("\"", "");
        return s.isEmpty() ? "yuki" : s;
    }
    String searchUrl(String q) {
        String base;
        switch (engine()) {
            case "google": base = "https://www.google.com/search?q="; break;
            case "yandex": base = "https://yandex.ru/search/?text="; break;
            case "bing": base = "https://www.bing.com/search?q="; break;
            case "ddg": base = "https://duckduckgo.com/?q="; break;
            default: base = "https://search.yukichat.lol/search?q=";
        }
        return base + android.net.Uri.encode(q);
    }

    // --- history ---
    JSONArray hist() {
        if (hist == null) hist = parse(raw("hist"));
        return hist;
    }
    void visit(String url, String title) {
        if (url.contains("?sso=") || url.contains("&sso=")) return;
        JSONArray h = hist(), out = new JSONArray();
        JSONObject cur = null;
        for (int i = 0; i < h.length(); i++) {
            JSONObject o = h.optJSONObject(i);
            if (o == null) continue;
            if (cur == null && url.equals(o.optString("u"))) cur = o; else out.put(o);
        }
        try {
            if (cur == null) cur = new JSONObject().put("u", url).put("t", "").put("n", 0);
            long now = System.currentTimeMillis();
            if (now - cur.optLong("at") > 30000) cur.put("n", cur.optInt("n") + 1);
            cur.put("at", now);
            if (title != null && !title.isEmpty()) cur.put("t", title);
        } catch (JSONException ignored) {}
        JSONArray next = new JSONArray().put(cur);
        for (int i = 0; i < out.length() && next.length() < HIST_MAX; i++) next.put(out.opt(i));
        hist = next;
        put("hist", next.toString(), true);
    }
    void retitle(String url, String title) {
        JSONArray h = hist();
        for (int i = 0; i < h.length(); i++) {
            JSONObject o = h.optJSONObject(i);
            if (o != null && url.equals(o.optString("u"))) {
                try { o.put("t", title); } catch (JSONException ignored) {}
                put("hist", h.toString(), true);
                return;
            }
        }
    }

    void removeHist(String url) {
        JSONArray h = hist(), out = new JSONArray();
        for (int i = 0; i < h.length(); i++) {
            JSONObject o = h.optJSONObject(i);
            if (o != null && !url.equals(o.optString("u"))) out.put(o);
        }
        put("hist", out.toString(), true);
    }
    void clearHist() { put("hist", "[]", true); }

    /** Most visited pages for the home page: [[title, url], ...]. */
    JSONArray topSites(int n) {
        JSONArray h = hist();
        java.util.List<JSONObject> all = new java.util.ArrayList<>();
        java.util.Set<String> hosts = new java.util.HashSet<>();
        for (int i = 0; i < h.length() && i < 1000; i++) {
            JSONObject o = h.optJSONObject(i);
            if (o != null && o.optString("u").startsWith("http") && !o.optString("u").contains("tauri.localhost")) all.add(o);
        }
        all.sort((x, y) -> Integer.compare(y.optInt("n"), x.optInt("n")));
        JSONArray out = new JSONArray();
        for (JSONObject o : all) {
            if (out.length() >= n) break;
            String host = MainActivity.host(o.optString("u")).replaceFirst("^www\\.", "");
            if (!hosts.add(host)) continue;
            out.put(new JSONArray().put(o.optString("t")).put(o.optString("u")));
        }
        return out;
    }

    // --- downloads (this phone only): [{n name, u content uri, m mime, s size, at, st: "ok"|"fail"|"run"}] ---
    JSONArray downloads() { return parse(p.getString("dl", "")); }
    void setDownloads(JSONArray a) { p.edit().putString("dl", a.toString()).apply(); }

    // --- bookmarks ---
    JSONArray bm() {
        if (bm == null) bm = parse(raw("bm"));
        return bm;
    }
    /** Every link, folders opened up. */
    JSONArray bmFlat() {
        JSONArray out = new JSONArray(), b = bm();
        for (int i = 0; i < b.length(); i++) {
            JSONObject o = b.optJSONObject(i);
            if (o == null) continue;
            JSONArray c = o.optJSONArray("c");
            if (c == null) out.put(o); else for (int j = 0; j < c.length(); j++) out.put(c.opt(j));
        }
        return out;
    }
    boolean isBookmarked(String url) {
        JSONArray f = bmFlat();
        for (int i = 0; i < f.length(); i++) if (url.equals(f.optJSONObject(i).optString("u"))) return true;
        return false;
    }
    void addBookmark(String url, String title) {
        try { bm().put(new JSONObject().put("u", url).put("t", title).put("i", "")); } catch (JSONException ignored) {}
        put("bm", bm().toString(), true);
    }
    void removeBookmark(String url) {
        JSONArray b = bm(), out = new JSONArray();
        for (int i = 0; i < b.length(); i++) {
            JSONObject o = b.optJSONObject(i);
            if (o == null) continue;
            JSONArray c = o.optJSONArray("c");
            if (c == null) { if (!url.equals(o.optString("u"))) out.put(o); continue; }
            JSONArray kept = new JSONArray();
            for (int j = 0; j < c.length(); j++) if (!url.equals(c.optJSONObject(j).optString("u"))) kept.put(c.opt(j));
            if (kept.length() > 0) try { out.put(new JSONObject(o.toString()).put("c", kept)); } catch (JSONException ignored) {}
        }
        put("bm", out.toString(), true);
    }

    static JSONArray parse(String s) {
        try { return s == null || s.isEmpty() ? new JSONArray() : new JSONArray(s); } catch (JSONException e) { return new JSONArray(); }
    }
}
