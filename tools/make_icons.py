#!/usr/bin/env python3
"""Turns the PC's line icons (icons.js, Lucide-style SVG) plus a few phone-only ones into
Android vector drawables: res/drawable/i_<name>.xml, black strokes tinted at runtime.
Run after adding an icon: python3 tools/make_icons.py"""
import os, re

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
SRC = "/root/yukiweb/src/icons.js"
WANT = {"chat", "music", "mail", "wallet", "pages", "write", "board", "chain", "hub", "globe", "shield", "search",
        "back", "forward", "reload", "plus", "x", "user", "logout", "dots", "download", "settings", "star", "palette",
        "html", "lock", "sparkles", "moon", "meet", "clipboard"}
EXTRA = {
    "home": '<path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8"/><path d="M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
    "history": '<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l4 2"/>',
    "bookmark": '<path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"/>',
    "monitor": '<rect width="20" height="14" x="2" y="3" rx="2"/><line x1="8" x2="16" y1="21" y2="21"/><line x1="12" x2="12" y1="17" y2="21"/>',
    "share": '<path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><polyline points="16 6 12 2 8 6"/><line x1="12" x2="12" y1="2" y2="15"/>',
    "private": '<path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49"/><path d="M14.084 14.158a3 3 0 0 1-4.242-4.242"/><path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143"/><path d="m2 2 20 20"/>',
    "trash": '<path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>',
    "info": '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>',
    "update": '<circle cx="12" cy="12" r="10"/><path d="m16 12-4-4-4 4"/><path d="M12 16V8"/>',
    "chevron": '<path d="m9 18 6-6-6-6"/>',
    "find": '<path d="M21 21l-4.34-4.34"/><circle cx="11" cy="11" r="8"/><path d="M8 11h6"/>',
    "copy": '<rect width="14" height="14" x="8" y="8" rx="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>',
    "file": '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/>',
    "up": '<path d="m18 15-6-6-6 6"/>',
    "down": '<path d="m6 9 6 6 6-6"/>',
}


def num(a, k, d=0.0):
    m = re.search(r'\b' + k + r'="([-\d.]+)"', a)
    return float(m.group(1)) if m else d


def f(v):
    return ("%.3f" % v).rstrip("0").rstrip(".")


def to_path(tag, a):
    if tag == "path":
        return re.search(r'\bd="([^"]+)"', a).group(1)
    if tag == "circle":
        cx, cy, r = num(a, "cx"), num(a, "cy"), num(a, "r")
        return f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0"
    if tag == "line":
        return f"M{f(num(a, 'x1'))},{f(num(a, 'y1'))}L{f(num(a, 'x2'))},{f(num(a, 'y2'))}"
    if tag == "polyline":
        p = re.search(r'points="([^"]+)"', a).group(1).split()
        pts = [p[i] + "," + p[i + 1] for i in range(0, len(p), 2)] if "," not in p[0] else p
        return "M" + "L".join(pts)
    if tag == "rect":
        x, y, w, h, r = num(a, "x"), num(a, "y"), num(a, "width"), num(a, "height"), num(a, "rx")
        if not r:
            return f"M{f(x)},{f(y)}h{f(w)}v{f(h)}h{f(-w)}z"
        return (f"M{f(x + r)},{f(y)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0 0,1 {f(r)},{f(r)}v{f(h - 2 * r)}"
                f"a{f(r)},{f(r)} 0 0,1 {f(-r)},{f(r)}h{f(-(w - 2 * r))}a{f(r)},{f(r)} 0 0,1 {f(-r)},{f(-r)}"
                f"v{f(-(h - 2 * r))}a{f(r)},{f(r)} 0 0,1 {f(r)},{f(-r)}z")
    raise ValueError(tag)


def vector(svg, fill=False):
    paths = [to_path(t, a) for t, a in re.findall(r"<(path|circle|line|polyline|rect)\b([^>]*)/?>", svg)]
    body = "".join(
        f'\n  <path android:pathData="{p}" android:strokeColor="#FF000000" android:strokeWidth="2" '
        f'android:strokeLineCap="round" android:strokeLineJoin="round"'
        + (' android:fillColor="#FF000000"' if fill else ' android:fillColor="#00000000"') + "/>" for p in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">'
            + body + "\n</vector>\n")


icons = dict(re.findall(r"^\s*(\w+): '([^']*)'", open(SRC).read(), re.M))
icons = {k: v for k, v in icons.items() if k in WANT}
icons.update(EXTRA)
os.makedirs(OUT, exist_ok=True)
for name, svg in icons.items():
    open(os.path.join(OUT, f"i_{name}.xml"), "w").write(vector(svg))
open(os.path.join(OUT, "i_star_on.xml"), "w").write(vector(icons["star"], fill=True))
print(len(icons) + 1, "icons")
