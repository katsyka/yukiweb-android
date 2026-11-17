package lol.yukichat.web;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Yuki ID sync, the same protocol as the PC (ui.js syncNow): pull what changed on the server
 * since last time, keep whichever side changed each key last, merge history (and bookmarks on
 * the first sync) instead, then push what changed here.
 */
final class Sync {
    interface Listener {
        void applied(Set<String> keys);   // values changed from another device
        void status(String text);
        void signedOut();
        void dash(boolean changed);       // new chats/music/mail/sent tabs for the home page
    }

    private static final MediaType JSON = MediaType.get("application/json");
    private final Store store;
    private final Listener listener;
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable later = this::now;
    private boolean busy;

    Sync(Store store, Listener listener) {
        this.store = store;
        this.listener = listener;
        store.listener = () -> { main.removeCallbacks(later); main.postDelayed(later, 5000); };
    }

    void now() {
        main.removeCallbacks(later);
        if (busy || store.token().isEmpty()) return;
        busy = true;
        listener.status("Syncing…");
        worker.execute(() -> {
            String status;
            Set<String> changed = new HashSet<>();
            try {
                status = run(changed);
            } catch (Exception e) {
                status = "Not synced: " + (e.getMessage() == null ? e.toString() : e.getMessage());
            }
            store.setSyncStatus(status);
            final String s = status;
            main.post(() -> {
                busy = false;
                if (!changed.isEmpty()) listener.applied(changed);
                listener.status(s);
            });
        });
    }

    private String run(Set<String> changed) throws Exception {
        String auth = "Bearer " + store.token();
        JSONObject res;
        try (Response r = http.newCall(new Request.Builder().url(BuildConfig.API + "/api/yukiweb/sync?since=" + store.since())
                .header("Authorization", auth).build()).execute()) {
            if (r.code() == 401) {
                main.post(listener::signedOut);
                return "Signed out";
            }
            if (!r.isSuccessful()) return "Not synced: server " + r.code();
            res = new JSONObject(r.body().string());
        }
        JSONObject items = res.optJSONObject("items");
        if (items == null) items = new JSONObject();
        boolean first = store.first();
        Map<String, Boolean> push = new LinkedHashMap<>();
        for (String k : Store.SYNC_KEYS) if (store.dirty(k)) push.put(k, true);

        for (Iterator<String> it = items.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject item = items.optJSONObject(k);
            if (item == null || !known(k)) continue;
            String theirs = item.optString("v");
            long at = item.optLong("at");
            if (k.equals("hist") || (k.equals("bm") && first)) {
                String merged = k.equals("hist") ? mergeHist(store.raw(k), theirs) : mergeBm(store.raw(k), theirs);
                if (!merged.equals(store.raw(k))) { store.put(k, merged, false); changed.add(k); }
                if (!merged.equals(theirs)) { push.put(k, true); store.setAt(k, System.currentTimeMillis()); }
                else store.setAt(k, at);
            } else if (at > store.at(k)) {
                store.put(k, theirs, false);
                store.setAt(k, at);
                push.remove(k);
                changed.add(k);
            }
        }
        // First sync: whatever this phone has that the account doesn't goes up.
        if (first) for (String k : Store.SYNC_KEYS)
            if (!items.has(k) && !store.raw(k).isEmpty()) {
                push.put(k, true);
                if (store.at(k) == 0) store.setAt(k, System.currentTimeMillis());
            }

        if (!push.isEmpty()) {
            JSONObject body = new JSONObject();
            for (String k : push.keySet())
                body.put(k, new JSONObject().put("v", store.raw(k)).put("at", store.at(k) == 0 ? System.currentTimeMillis() : store.at(k)));
            try (Response r = http.newCall(new Request.Builder().url(BuildConfig.API + "/api/yukiweb/sync").header("Authorization", auth)
                    .post(RequestBody.create(new JSONObject().put("items", body).toString(), JSON)).build()).execute()) {
                if (!r.isSuccessful()) return r.code() == 413 ? "Not synced: something is too big" : "Not synced: server " + r.code();
            }
            for (String k : push.keySet()) store.setDirty(k, false);
        }
        store.synced(res.optLong("now"));
        // Home page data: one call, kept for the next time home opens.
        try (Response r = http.newCall(new Request.Builder().url(BuildConfig.API + "/api/yukiweb/dash").header("Authorization", auth).build()).execute()) {
            if (r.isSuccessful()) {
                String d = r.body().string(), old = store.dash();
                store.setDash(d);
                boolean ch = !d.equals(old);
                main.post(() -> listener.dash(ch));
            }
        } catch (Exception ignored) {}
        return "Synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
    }

    private static boolean known(String k) {
        for (String s : Store.SYNC_KEYS) if (s.equals(k)) return true;
        return false;
    }

    /** Union by address: most visits, latest time, newest title. */
    static String mergeHist(String a, String b) {
        Map<String, JSONObject> m = new HashMap<>();
        for (String src : new String[]{a, b}) {
            JSONArray arr = Store.parse(src);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject h = arr.optJSONObject(i);
                if (h == null) continue;
                String u = h.optString("u");
                JSONObject o = m.get(u);
                if (o == null) { m.put(u, h); continue; }
                try {
                    String t = h.optLong("at") > o.optLong("at") ? h.optString("t") : o.optString("t");
                    o.put("n", Math.max(o.optInt("n"), h.optInt("n"))).put("at", Math.max(o.optLong("at"), h.optLong("at")))
                            .put("t", t.isEmpty() ? (o.optString("t").isEmpty() ? h.optString("t") : o.optString("t")) : t);
                } catch (Exception ignored) {}
            }
        }
        java.util.List<JSONObject> all = new java.util.ArrayList<>(m.values());
        all.sort((x, y) -> Long.compare(y.optLong("at"), x.optLong("at")));
        JSONArray out = new JSONArray();
        for (int i = 0; i < all.size() && i < 3000; i++) out.put(all.get(i));
        return out.toString();
    }

    /** Mine plus every link of theirs I don't have. */
    static String mergeBm(String mine, String theirs) {
        JSONArray out = Store.parse(mine), b = Store.parse(theirs);
        Set<String> have = new HashSet<>();
        for (int i = 0; i < out.length(); i++) collect(out.optJSONObject(i), have);
        try {
            for (int i = 0; i < b.length(); i++) {
                JSONObject x = b.optJSONObject(i);
                if (x == null) continue;
                JSONArray c = x.optJSONArray("c");
                if (c == null) { if (!have.contains(x.optString("u"))) out.put(x); continue; }
                JSONArray kept = new JSONArray();
                for (int j = 0; j < c.length(); j++) if (!have.contains(c.optJSONObject(j).optString("u"))) kept.put(c.opt(j));
                if (kept.length() > 0) out.put(new JSONObject(x.toString()).put("c", kept));
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    private static void collect(JSONObject o, Set<String> into) {
        if (o == null) return;
        JSONArray c = o.optJSONArray("c");
        if (c == null) into.add(o.optString("u"));
        else for (int j = 0; j < c.length(); j++) into.add(c.optJSONObject(j).optString("u"));
    }
}
