package lol.yukichat.web;

import android.app.ActivityManager;
import android.os.Bundle;

/** A site added to the home screen as an app: its own task in recents, no address bar (see MainActivity.appUrl). */
public class WebAppActivity extends MainActivity {
    static final String EXTRA_NAME = "yw_name";

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name != null) setTaskDescription(new ActivityManager.TaskDescription(name));
    }

    @Override
    String appUrl() { return getIntent().getData() == null ? "https://yukichat.lol" : getIntent().getDataString(); }
}
