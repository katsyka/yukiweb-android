package lol.yukichat.web;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.format.DateFormat;
import android.widget.EditText;

import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.List;

/** GeckoView draws no dialogs of its own: alerts, confirms, <select> menus, file and date pickers, password prompts. */
final class Prompts implements GeckoSession.PromptDelegate {
    private final MainActivity a;

    Prompts(MainActivity a) { this.a = a; }

    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession s, AlertPrompt p) {
        GeckoResult<PromptResponse> r = new GeckoResult<>();
        Ui.dialog(a).setTitle(p.title).setMessage(p.message)
                .setPositiveButton("OK", (d, w) -> r.complete(p.dismiss()))
                .setOnCancelListener(d -> r.complete(p.dismiss())).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession s, ButtonPrompt p) {
        GeckoResult<PromptResponse> r = new GeckoResult<>();
        Ui.dialog(a).setTitle(p.title).setMessage(p.message)
                .setPositiveButton("OK", (d, w) -> r.complete(p.confirm(ButtonPrompt.Type.POSITIVE)))
                .setNegativeButton("Cancel", (d, w) -> r.complete(p.confirm(ButtonPrompt.Type.NEGATIVE)))
                .setOnCancelListener(d -> r.complete(p.dismiss())).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onTextPrompt(GeckoSession s, TextPrompt p) {
        GeckoResult<PromptResponse> r = new GeckoResult<>();
        EditText in = new EditText(a);
        in.setText(p.defaultValue);
        Ui.dialog(a).setTitle(p.title).setMessage(p.message).setView(in)
                .setPositiveButton("OK", (d, w) -> r.complete(p.confirm(in.getText().toString())))
                .setNegativeButton("Cancel", (d, w) -> r.complete(p.dismiss()))
                .setOnCancelListener(d -> r.complete(p.dismiss())).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession s, ChoicePrompt p) {
        GeckoResult<PromptResponse> r = new GeckoResult<>();
        // Option groups come nested; show them as one list.
        List<ChoicePrompt.Choice> all = new ArrayList<>();
        flatten(p.choices, all);
        String[] labels = new String[all.size()];
        boolean[] on = new boolean[all.size()];
        int sel = -1;
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i).label;
            on[i] = all.get(i).selected;
            if (on[i] && sel < 0) sel = i;
        }
        AlertDialog.Builder b = Ui.dialog(a).setTitle(p.title).setOnCancelListener(d -> r.complete(p.dismiss()));
        if (p.type == ChoicePrompt.Type.MULTIPLE) {
            b.setMultiChoiceItems(labels, on, (d, i, checked) -> on[i] = checked)
                    .setPositiveButton("OK", (d, w) -> {
                        List<ChoicePrompt.Choice> picked = new ArrayList<>();
                        for (int i = 0; i < on.length; i++) if (on[i]) picked.add(all.get(i));
                        r.complete(p.confirm(picked.toArray(new ChoicePrompt.Choice[0])));
                    });
        } else {
            b.setSingleChoiceItems(labels, sel, (d, i) -> { r.complete(p.confirm(all.get(i))); d.dismiss(); });
        }
        b.show();
        return r;
    }

    private static void flatten(ChoicePrompt.Choice[] in, List<ChoicePrompt.Choice> out) {
        if (in == null) return;
        for (ChoicePrompt.Choice c : in) {
            if (c.items != null) flatten(c.items, out);
            else if (!c.separator && !c.disabled) out.add(c);
        }
    }

    @Override
    public GeckoResult<PromptResponse> onFilePrompt(GeckoSession s, FilePrompt p) {
        GeckoResult<PromptResponse> r = new GeckoResult<>();
        a.pickFile(p, r);
        return r;
    }

    // ---------------------------------------------------------------- date & time inputs

    @Override
    public GeckoResult<PromptResponse> onDateTimePrompt(GeckoSession s, DateTimePrompt p) {
        GeckoResult<PromptResponse> r = new GeckoResult<>();
        Calendar c = parse(p.type, p.defaultValue);
        if (p.type == DateTimePrompt.Type.TIME) { time(p, r, c); return r; }
        DatePickerDialog d = new DatePickerDialog(a, (v, y, m, day) -> {
            c.set(y, m, day);
            if (p.type == DateTimePrompt.Type.DATETIME_LOCAL) time(p, r, c);
            else r.complete(p.confirm(format(p.type, c)));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        Calendar min = parse(p.type, p.minValue), max = parse(p.type, p.maxValue);
        if (p.minValue != null && !p.minValue.isEmpty()) d.getDatePicker().setMinDate(min.getTimeInMillis());
        if (p.maxValue != null && !p.maxValue.isEmpty()) d.getDatePicker().setMaxDate(max.getTimeInMillis());
        d.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Clear", (x, w) -> r.complete(p.confirm("")));
        d.setOnCancelListener(x -> r.complete(p.dismiss()));
        d.show();
        return r;
    }

    private void time(DateTimePrompt p, GeckoResult<PromptResponse> r, Calendar c) {
        TimePickerDialog t = new TimePickerDialog(a, (v, h, m) -> {
            c.set(Calendar.HOUR_OF_DAY, h);
            c.set(Calendar.MINUTE, m);
            r.complete(p.confirm(format(p.type, c)));
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(a));
        t.setOnCancelListener(x -> r.complete(p.dismiss()));
        t.show();
    }

    /** HTML input values: 2026-11-02, 2026-11, 2026-W45, 14:30, 2026-11-02T14:30. */
    private static Calendar parse(int type, String v) {
        Calendar c = Calendar.getInstance();
        if (v == null || v.isEmpty()) return c;
        try {
            if (type == DateTimePrompt.Type.TIME) {
                String[] t = v.split(":");
                c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0]));
                c.set(Calendar.MINUTE, Integer.parseInt(t[1]));
                return c;
            }
            if (type == DateTimePrompt.Type.WEEK) {
                String[] w = v.split("-W");
                c.clear();
                c.setFirstDayOfWeek(Calendar.MONDAY);
                c.setMinimalDaysInFirstWeek(4);
                c.set(Calendar.YEAR, Integer.parseInt(w[0]));
                c.set(Calendar.WEEK_OF_YEAR, Integer.parseInt(w[1]));
                c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                return c;
            }
            String[] dt = v.split("T"), d = dt[0].split("-");
            c.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, d.length > 2 ? Integer.parseInt(d[2]) : 1);
            if (dt.length > 1) {
                String[] t = dt[1].split(":");
                c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0]));
                c.set(Calendar.MINUTE, Integer.parseInt(t[1]));
            }
        } catch (Exception ignored) {}
        return c;
    }

    private static String format(int type, Calendar c) {
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        int h = c.get(Calendar.HOUR_OF_DAY), min = c.get(Calendar.MINUTE);
        if (type == DateTimePrompt.Type.MONTH) return String.format(Locale.ROOT, "%04d-%02d", y, m);
        if (type == DateTimePrompt.Type.TIME) return String.format(Locale.ROOT, "%02d:%02d", h, min);
        if (type == DateTimePrompt.Type.DATETIME_LOCAL) return String.format(Locale.ROOT, "%04d-%02d-%02dT%02d:%02d", y, m, d, h, min);
        if (type == DateTimePrompt.Type.WEEK) {
            c.setFirstDayOfWeek(Calendar.MONDAY);
            c.setMinimalDaysInFirstWeek(4);
            return String.format(Locale.ROOT, "%04d-W%02d", c.getWeekYear(), c.get(Calendar.WEEK_OF_YEAR));
        }
        return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d);
    }

    // ---------------------------------------------------------------- passwords

    @Override
    public GeckoResult<PromptResponse> onLoginSave(GeckoSession s, AutocompleteRequest<Autocomplete.LoginSaveOption> req) {
        if (req.options.length == 0) return GeckoResult.fromValue(req.dismiss());
        Autocomplete.LoginEntry e = req.options[0].value;
        String host = MainActivity.host(e.origin);
        if (a.logins.never(host)) return GeckoResult.fromValue(req.dismiss());
        boolean update = e.guid != null && !e.guid.isEmpty();
        GeckoResult<PromptResponse> r = new GeckoResult<>();
        Ui.dialog(a).setTitle(L.t(update ? "Update saved password?" : "Save password?"))
                .setMessage(host + (e.username == null || e.username.isEmpty() ? "" : "\n" + e.username))
                .setPositiveButton(L.t(update ? "Update" : "Save"), (d, w) -> r.complete(req.confirm(req.options[0])))
                .setNeutralButton(L.t("Never for this site"), (d, w) -> { a.logins.setNever(host); r.complete(req.dismiss()); })
                .setNegativeButton(L.t("Not now"), (d, w) -> r.complete(req.dismiss()))
                .setOnCancelListener(d -> r.complete(req.dismiss())).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onLoginSelect(GeckoSession s, AutocompleteRequest<Autocomplete.LoginSelectOption> req) {
        if (req.options.length == 0) return GeckoResult.fromValue(req.dismiss());
        GeckoResult<PromptResponse> r = new GeckoResult<>();
        String[] names = new String[req.options.length];
        for (int i = 0; i < names.length; i++) {
            String u = req.options[i].value.username;
            names[i] = u == null || u.isEmpty() ? "(" + L.t("no username") + ")" : u;
        }
        Ui.dialog(a).setTitle(L.t("Sign in as"))
                .setItems(names, (d, i) -> r.complete(req.confirm(req.options[i])))
                .setOnCancelListener(d -> r.complete(req.dismiss())).show();
        return r;
    }
}
