package lol.yukichat.web;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Checks android.json next to the PC installer and installs the newer APK over this one through a
 * PackageInstaller session, so a failure comes back with its real reason instead of "App not installed".
 */
final class Updater {
    static final String ACTION_STATUS = "lol.yukichat.web.INSTALL_STATUS";
    /** android.json of a newer version, once a check found one (the menu offers it). */
    static JSONObject available;
    private static boolean busy;

    static void check(Activity a, boolean manual) {
        new Thread(() -> {
            try (Response r = new OkHttpClient().newCall(new Request.Builder().url(BuildConfig.UPDATE_JSON).build()).execute()) {
                JSONObject j = new JSONObject(r.body().string());
                boolean newer = j.optInt("version_code") > BuildConfig.VERSION_CODE;
                if (newer) available = j;
                a.runOnUiThread(() -> {
                    if (newer) offer(a, j);
                    else if (manual) Ui.dialog(a).setTitle("YukiWeb " + BuildConfig.VERSION_NAME)
                            .setMessage("You have the latest version.").setPositiveButton("OK", null).show();
                });
            } catch (Exception e) {
                if (manual) a.runOnUiThread(() -> Toast.makeText(a, "Couldn't check for updates", Toast.LENGTH_SHORT).show());
            }
        }).start();
        // A finished update leaves its APK behind; it's 100 MB, so don't keep it.
        if (!busy) new File(new File(a.getCacheDir(), "updates"), "yukiweb.apk").delete();
    }

    static void offer(Activity a, JSONObject j) {
        Ui.dialog(a).setTitle("YukiWeb " + j.optString("version_name") + " is out")
                .setMessage(j.optString("notes", "A new version is ready.") + "\n\nIt installs over this one: your tabs, bookmarks and sign-in stay.")
                .setPositiveButton("Update", (d, w) -> download(a, j))
                .setNegativeButton("Later", null).show();
    }

    private static void download(Activity a, JSONObject j) {
        if (busy) { Toast.makeText(a, "The update is already downloading", Toast.LENGTH_SHORT).show(); return; }
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "Allow YukiWeb to install updates, then tap Update again", Toast.LENGTH_LONG).show();
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            return;
        }
        busy = true;
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        int p = Ui.dp(a, 24);
        bar.setPadding(p, p / 2, p, 0);
        AlertDialog dlg = Ui.dialog(a).setTitle("Downloading YukiWeb " + j.optString("version_name")).setView(bar).setCancelable(false).show();
        new Thread(() -> {
            File dir = new File(a.getCacheDir(), "updates"), apk = new File(dir, "yukiweb.apk"), part = new File(dir, "yukiweb.part");
            dir.mkdirs();
            try (Response r = new OkHttpClient().newCall(new Request.Builder().url(j.optString("url")).build()).execute()) {
                if (!r.isSuccessful()) throw new Exception("server " + r.code());
                long total = r.body().contentLength(), got = 0;
                try (InputStream in = r.body().byteStream(); OutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[64 * 1024];
                    int last = -1;
                    for (int n; (n = in.read(buf)) > 0; ) {
                        out.write(buf, 0, n);
                        got += n;
                        int pct = total > 0 ? (int) (got * 100 / total) : 0;
                        if (pct != last) { last = pct; a.runOnUiThread(() -> bar.setProgress(pct)); }
                    }
                }
                if (total > 0 && got != total) throw new Exception("download cut off");
                apk.delete();
                if (!part.renameTo(apk)) throw new Exception("couldn't save the file");
                a.runOnUiThread(() -> dlg.setTitle("Installing…"));
                install(a, apk);
                a.runOnUiThread(dlg::dismiss);
            } catch (Exception e) {
                busy = false;
                part.delete();
                report("download " + e);
                a.runOnUiThread(() -> { dlg.dismiss(); fail(a, "Download failed: " + e.getMessage()); });
            }
        }).start();
    }

    private static void install(Activity a, File apk) throws Exception {
        PackageInstaller pi = a.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(a.getPackageName());
        sp.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(sp);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("yukiweb.apk", 0, apk.length())) {
                byte[] buf = new byte[256 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent back = new Intent(a, MainActivity.class).setAction(ACTION_STATUS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getActivity(a, id, back, flags).getIntentSender());
        }
    }

    /** The installer's answer, delivered to MainActivity.onNewIntent. */
    static void status(Activity a, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) a.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        busy = false;
        if (st == PackageInstaller.STATUS_SUCCESS) return;
        String why;
        switch (st) {
            case PackageInstaller.STATUS_FAILURE_ABORTED: why = "The update was cancelled."; break;
            case PackageInstaller.STATUS_FAILURE_STORAGE: why = "Not enough free space on the phone. YukiWeb needs about 300 MB free to update."; break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT: why = "This YukiWeb was installed from a different build. Uninstall it and install the new one from yuki.yukichat.lol/yukiweb."; break;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: why = "This version doesn't support your phone."; break;
            case PackageInstaller.STATUS_FAILURE_BLOCKED: why = "The phone blocked the install (Play Protect or a security setting)."; break;
            case PackageInstaller.STATUS_FAILURE_INVALID: why = "The downloaded file is damaged. Try again."; break;
            default: why = "The update didn't install.";
        }
        String detail = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        report(st + " " + detail);
        fail(a, why + (detail == null ? "" : "\n\n" + detail));
    }

    private static void fail(Activity a, String msg) {
        Ui.dialog(a).setTitle("Update failed").setMessage(msg).setPositiveButton("OK", null).show();
    }

    /** Sends why an update failed to the same debug log the PC app uses: version, phone model, installer message. */
    private static void report(String msg) {
        new Thread(() -> {
            String m = "android " + BuildConfig.VERSION_NAME + " sdk" + Build.VERSION.SDK_INT + " " + Build.MANUFACTURER + " " + Build.MODEL + ": " + msg;
            try (Response r = new OkHttpClient().newCall(new Request.Builder()
                    .url("https://yuki.yukichat.lol/yukiweb/log?s=android&m=" + Uri.encode(m)).build()).execute()) {
            } catch (Exception ignored) {}
        }).start();
    }
}
