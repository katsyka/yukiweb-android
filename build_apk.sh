#!/usr/bin/env bash
# Builds the signed release APK and publishes it next to the PC installer:
#   https://yuki.yukichat.lol/yukiweb/yukiweb.apk + android.json (the app checks this for updates)
# Usage: ./build_apk.sh "What's new"   (bump versionCode/versionName in app/build.gradle first)
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME=/opt/android-sdk
OUT=/root/yuki-hub/yukiweb
# The HTML playground and the line icons come from the PC version; home.html is the phone's own.
for f in theme.js icons.js html.html; do cp /root/yukiweb/src/$f app/src/main/assets/; done
rm -rf app/src/main/assets/walls app/src/main/assets/wall.jpg app/src/main/assets/newtab.html

./gradlew assembleRelease --no-daemon -q
APK=app/build/outputs/apk/release/app-release.apk
cp "$APK" "$OUT/yukiweb.apk.tmp"
python3 - "$OUT/android.json.tmp" "${1:-}" <<'PY'
import json, sys, os, hashlib, datetime
apk = "app/build/outputs/apk/release/app-release.apk"
meta = json.load(open("app/build/outputs/apk/release/output-metadata.json"))["elements"][0]
json.dump({"version_code": meta["versionCode"], "version_name": meta["versionName"], "notes": sys.argv[2],
           "url": "https://yuki.yukichat.lol/yukiweb/yukiweb.apk", "size": os.path.getsize(apk),
           "sha256": hashlib.sha256(open(apk, "rb").read()).hexdigest(),
           "released": datetime.date.today().isoformat(), "min_android": "8.0"}, open(sys.argv[1], "w"), indent=2)
PY
# Swap both in together so the version info always matches the APK.
mv "$OUT/yukiweb.apk.tmp" "$OUT/yukiweb.apk"
mv "$OUT/android.json.tmp" "$OUT/android.json"
echo "Published $(python3 -c 'import json;print(json.load(open("'$OUT'/android.json"))["version_name"])'): https://yuki.yukichat.lol/yukiweb/yukiweb.apk ($(du -h "$OUT/yukiweb.apk" | cut -f1))"
