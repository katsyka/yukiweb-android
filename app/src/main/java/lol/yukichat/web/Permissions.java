package lol.yukichat.web;

import android.Manifest;
import android.app.AlertDialog;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/** Asks before a site gets location, notifications, camera or microphone. */
final class Permissions implements GeckoSession.PermissionDelegate {
    private final MainActivity a;

    Permissions(MainActivity a) { this.a = a; }

    @Override
    public void onAndroidPermissionsRequest(GeckoSession s, String[] perms, Callback cb) {
        if (perms == null) cb.grant(); else a.androidPermissions(perms, cb);
    }

    @Override
    public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s, ContentPermission p) {
        String what;
        switch (p.permission) {
            case PERMISSION_GEOLOCATION: what = "know your location"; break;
            case PERMISSION_DESKTOP_NOTIFICATION: what = "show notifications"; break;
            case PERMISSION_XR: what = "use virtual reality"; break;
            case PERMISSION_STORAGE_ACCESS: what = "use its cookies here"; break;
            default:
                // Autoplay, storage, DRM video: just allow, like other mobile browsers.
                return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
        }
        GeckoResult<Integer> r = new GeckoResult<>();
        ask(MainActivity.host(p.uri) + " wants to " + what,
                () -> r.complete(ContentPermission.VALUE_ALLOW), () -> r.complete(ContentPermission.VALUE_DENY));
        return r;
    }

    @Override
    public void onMediaPermissionRequest(GeckoSession s, String uri, MediaSource[] video, MediaSource[] audio, MediaCallback cb) {
        String what = video != null && audio != null ? "use your camera and microphone" : video != null ? "use your camera" : "use your microphone";
        ask(MainActivity.host(uri) + " wants to " + what, () -> {
            String[] need = video != null && audio != null ? new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}
                    : video != null ? new String[]{Manifest.permission.CAMERA} : new String[]{Manifest.permission.RECORD_AUDIO};
            a.androidPermissions(need, new Callback() {
                @Override public void grant() { cb.grant(video == null ? null : video[0], audio == null ? null : audio[0]); }
                @Override public void reject() { cb.reject(); }
            });
        }, cb::reject);
    }

    private void ask(String msg, Runnable yes, Runnable no) {
        Ui.dialog(a).setMessage(msg)
                .setPositiveButton("Allow", (d, w) -> yes.run())
                .setNegativeButton("Block", (d, w) -> no.run())
                .setOnCancelListener(d -> no.run())
                .show();
    }
}
