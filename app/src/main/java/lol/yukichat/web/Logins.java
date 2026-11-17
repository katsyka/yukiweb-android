package lol.yukichat.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.GeckoResult;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Saved passwords: one JSON list, encrypted with a key that never leaves the phone's keystore. Gecko fills forms from it. */
final class Logins implements Autocomplete.StorageDelegate {
    private static final String ALIAS = "yw_logins";
    private final SharedPreferences p;
    private JSONArray all;

    Logins(Context c) { p = c.getSharedPreferences("logins", Context.MODE_PRIVATE); }

    // --- the list ---

    synchronized JSONArray list() {
        if (all == null) {
            try { all = new JSONArray(decrypt(p.getString("data", ""))); } catch (Exception e) { all = new JSONArray(); }
        }
        return all;
    }

    private synchronized void write() {
        try { p.edit().putString("data", encrypt(all.toString())).apply(); } catch (Exception ignored) {}
    }

    synchronized void remove(String guid) {
        JSONArray out = new JSONArray(), l = list();
        for (int i = 0; i < l.length(); i++) if (!guid.equals(l.optJSONObject(i).optString("g"))) out.put(l.opt(i));
        all = out;
        write();
    }

    /** Sites where the user said "Never" to saving. */
    boolean never(String host) { return p.getStringSet("never", java.util.Collections.emptySet()).contains(host); }
    void setNever(String host) {
        java.util.Set<String> s = new java.util.HashSet<>(p.getStringSet("never", java.util.Collections.emptySet()));
        s.add(host);
        p.edit().putStringSet("never", s).apply();
    }

    static String host(JSONObject o) { String h = Uri.parse(o.optString("o")).getHost(); return h == null ? o.optString("o") : h; }

    private static Autocomplete.LoginEntry entry(JSONObject o) {
        return new Autocomplete.LoginEntry.Builder().guid(o.optString("g")).origin(o.optString("o"))
                .formActionOrigin(o.optString("f", null)).httpRealm(o.optString("r", null))
                .username(o.optString("u")).password(o.optString("p")).build();
    }

    // --- Gecko's side ---

    @Override
    public GeckoResult<Autocomplete.LoginEntry[]> onLoginFetch(String domain) {
        List<Autocomplete.LoginEntry> out = new ArrayList<>();
        JSONArray l = list();
        for (int i = 0; i < l.length(); i++) {
            JSONObject o = l.optJSONObject(i);
            String h = host(o);
            if (h.equals(domain) || h.endsWith("." + domain) || domain.endsWith("." + h)) out.add(entry(o));
        }
        return GeckoResult.fromValue(out.toArray(new Autocomplete.LoginEntry[0]));
    }

    @Override
    public GeckoResult<Autocomplete.LoginEntry[]> onLoginFetch() {
        List<Autocomplete.LoginEntry> out = new ArrayList<>();
        JSONArray l = list();
        for (int i = 0; i < l.length(); i++) out.add(entry(l.optJSONObject(i)));
        return GeckoResult.fromValue(out.toArray(new Autocomplete.LoginEntry[0]));
    }

    @Override
    public synchronized void onLoginSave(Autocomplete.LoginEntry e) {
        JSONArray l = list();
        JSONObject hit = null;
        for (int i = 0; i < l.length(); i++) {
            JSONObject o = l.optJSONObject(i);
            boolean same = e.guid != null ? e.guid.equals(o.optString("g"))
                    : o.optString("o").equals(e.origin) && o.optString("u").equals(e.username == null ? "" : e.username);
            if (same) { hit = o; break; }
        }
        try {
            if (hit == null) { hit = new JSONObject().put("g", UUID.randomUUID().toString()); l.put(hit); }
            hit.put("o", e.origin).put("u", e.username == null ? "" : e.username).put("p", e.password == null ? "" : e.password)
                    .put("at", System.currentTimeMillis());
            if (e.formActionOrigin != null) hit.put("f", e.formActionOrigin);
            if (e.httpRealm != null) hit.put("r", e.httpRealm);
        } catch (Exception ignored) {}
        write();
    }

    // --- keystore AES-GCM; stored as base64(iv | ciphertext) ---

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return g.generateKey();
    }

    private static String encrypt(String s) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV(), body = c.doFinal(s.getBytes(StandardCharsets.UTF_8)), out = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(body, 0, out, iv.length, body.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    private static String decrypt(String s) throws Exception {
        if (s.isEmpty()) return "[]";
        byte[] in = Base64.decode(s, Base64.NO_WRAP);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, in, 0, 12));
        return new String(c.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
    }
}
