package lol.yukichat.web;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small view builders shared by the bar, the menu and the full-screen pages. */
final class Ui {
    // The current palette, set by apply(). Same themes as the PC (theme.js), so a synced theme looks the same.
    static int ACC = 0xffffd23f, ON_ACC = 0xff16181e, ACC_SOFT, ACC_TEXT, TEXT = 0xff16181e, DIM = 0xff6b7080, SOFT = 0xfff1f2f5,
            SOFT2 = 0xffe6e8ec, LINE = 0xffd9dce2, PAGE = 0xfff6f7f9, BG = 0xffffffff, RIPPLE = 0x22000000;
    static boolean DARK;
    static String key = "";

    //               name        label       acc        surface    text       dim        soft       hover      line       page       dark
    static final String[][] THEMES = {
            {"light", "Light", "#ffd23f", "#ffffff", "#16181e", "#6b7080", "#f1f2f5", "#e6e8ec", "#d9dce2", "#f6f7f9", "0"},
            {"dark", "Dark", "#ffd23f", "#232429", "#ececf0", "#9a9cab", "#2e2f35", "#383940", "#383940", "#1c1d21", "1"},
            {"midnight", "Midnight", "#7aa2ff", "#171e33", "#e6eaf5", "#8e98b8", "#202a45", "#283454", "#2a3554", "#121a2e", "1"},
            {"sakura", "Sakura", "#ff8fb3", "#fffafb", "#2a1a20", "#8b6f7a", "#f8edf1", "#f1dde4", "#ead3db", "#fbf3f6", "0"},
            {"forest", "Forest", "#7bd389", "#fbfdfb", "#15211a", "#62786a", "#edf3ee", "#dfe9e1", "#cfdcd2", "#f3f7f4", "0"},
    };
    static final String[] ACCENTS = {"#ffd23f", "#ff8a3d", "#ff5c7a", "#ff8fb3", "#b18cff", "#7aa2ff", "#3ec7e0", "#7bd389"};

    static String[] theme(String name) {
        for (String[] t : THEMES) if (t[0].equals(name)) return t;
        return THEMES[0];
    }

    /** Mix a color toward black (k<0) or white (k>0), like shade() in theme.js. */
    static int shade(int c, float k) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        if (k < 0) { r = Math.round(r * (1 + k)); g = Math.round(g * (1 + k)); b = Math.round(b * (1 + k)); }
        else { r = Math.round(r + (255 - r) * k); g = Math.round(g + (255 - g) * k); b = Math.round(b + (255 - b) * k); }
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    static int color(String hex) { return 0xff000000 | Integer.parseInt(hex.substring(1), 16); }
    static String hex(int c) { return String.format("#%06x", c & 0xffffff); }

    /** Sets the palette; false if it's the one already in use. */
    static boolean apply(String name, String acc) {
        String[] t = theme(name);
        if (acc == null || !acc.matches("#[0-9a-fA-F]{6}")) acc = t[2];
        String k = t[0] + acc;
        if (k.equals(key)) return false;
        key = k;
        DARK = t[10].equals("1");
        ACC = color(acc);
        BG = color(t[3]); TEXT = color(t[4]); DIM = color(t[5]); SOFT = color(t[6]); SOFT2 = color(t[7]); LINE = color(t[8]); PAGE = color(t[9]);
        double lum = (0.299 * (ACC >> 16 & 255) + 0.587 * (ACC >> 8 & 255) + 0.114 * (ACC & 255)) / 255;
        ON_ACC = lum > 0.6 ? 0xff16181e : 0xffffffff;
        ACC_SOFT = DARK ? shade(ACC, -0.72f) : shade(ACC, 0.82f);
        ACC_TEXT = DARK ? ACC : shade(ACC, -0.42f);
        RIPPLE = DARK ? 0x33ffffff : 0x22000000;
        return true;
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, DARK ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(Context c, int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    /** A background that shows a ripple on touch, clipped to the rounded shape. */
    static RippleDrawable press(Context c, int color, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), round(c, color, radius), round(c, 0xff000000, radius));
    }

    static ImageView icon(Context c, int res, int tint, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(tint));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** A round icon button with a ripple. */
    static ImageView iconButton(Context c, int res, int sizeDp, View.OnClickListener l) {
        ImageView v = icon(c, res, TEXT, sizeDp);
        int p = (sizeDp - 22) / 2;
        v.setPadding(dp(c, p), dp(c, p), dp(c, p), dp(c, p));
        v.setBackground(press(c, 0x00000000, sizeDp / 2f));
        v.setOnClickListener(l);
        return v;
    }

    static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** A list row: icon, title, optional subtitle, optional trailing text. */
    static LinearLayout row(Context c, int iconRes, String title, String sub, String trail, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        r.setMinimumHeight(dp(c, 56));
        if (l != null) { r.setBackground(press(c, 0x00000000, 0)); r.setOnClickListener(l); }
        if (iconRes != 0) {
            ImageView i = icon(c, iconRes, TEXT, 22);
            ((LinearLayout.LayoutParams) i.getLayoutParams()).setMargins(0, 0, dp(c, 18), 0);
            r.addView(i);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView a = text(c, title, 16, TEXT, false);
        a.setSingleLine(true);
        a.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(a);
        if (sub != null && !sub.isEmpty()) {
            TextView b = text(c, sub, 13, DIM, false);
            b.setSingleLine(true);
            b.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(b);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (trail != null) {
            TextView t = text(c, trail, 14, DIM, false);
            t.setPadding(dp(c, 12), 0, 0, 0);
            r.addView(t);
        }
        return r;
    }

    /** A round letter badge standing in for a site icon. */
    static TextView badge(Context c, String host, int sizeDp) {
        String h = host.replaceFirst("^www\\.", "");
        TextView t = text(c, h.isEmpty() ? "?" : h.substring(0, 1).toUpperCase(), sizeDp * 0.42f, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(c, SOFT, sizeDp / 2f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.setMargins(0, 0, dp(c, 16), 0);
        t.setLayoutParams(lp);
        return t;
    }

    static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, DIM, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(c, 18), dp(c, 22), dp(c, 18), dp(c, 6));
        return t;
    }
}
