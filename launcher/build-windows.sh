#!/usr/bin/env bash
# Windows (Git Bash) version of build-and-install.sh: uses Android Studio's JDK and SDK, Python for zipping,
# and targets older boxes (Android 7+). On Android < 9 there is no set-home-activity: disabling the
# stock launcher leaves this one as the only real HOME.
#   launcher/build-windows.sh            build + install
#   launcher/build-windows.sh --build    build only
set -euo pipefail
cd "$(dirname "$0")"
# Stop Git Bash rewriting TV paths like /data/local/tmp into C:/Program Files/Git/...
export MSYS_NO_PATHCONV=1
JDK="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
export PATH="$(cygpath -u "$JDK")/bin:$PATH"
# Windows form, because MSYS_NO_PATHCONV stops Git Bash converting it for d8.bat / apksigner.bat.
export JAVA_HOME="$(cygpath -w "$JDK")"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/${BUILD_TOOLS:-36.0.0}"
JAR="$SDK/platforms/${PLATFORM:-android-36}/android.jar"
MIN_SDK="${MIN_SDK:-24}"   # 24 = Android 7.0 (the CVTE TV); the M8S box is 7.1
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.leanbacklauncher}"
PY=../tools/venv/Scripts/python
B=build

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex"
"$BT/aapt2.exe" compile --dir res -o "$B/res.zip"
"$BT/aapt2.exe" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  -A assets --min-sdk-version "$MIN_SDK" --target-sdk-version 30 "$B/res.zip"
javac -nowarn -Xlint:-options --release 11 -classpath "$JAR" -d "$B/classes" \
  $(find src "$B/gen" -name '*.java')
"$BT/d8.bat" --min-api "$MIN_SDK" --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
"$PY" -I -c "import sys, zipfile; z = zipfile.ZipFile(sys.argv[1], 'a'); z.write(sys.argv[2], 'classes.dex'); z.close()" \
  "$B/base.apk" "$B/dex/classes.dex"
"$BT/zipalign.exe" -f 4 "$B/base.apk" "$B/aligned.apk"
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvhome" >/dev/null 2>&1
"$BT/apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --out "$B/tvhome.apk" "$B/aligned.apk"
echo "built $B/tvhome.apk ($(wc -c < "$B/tvhome.apk") bytes)"
[ "${1:-}" = "--build" ] && exit 0

# Android 7 toolbox has no md5sum, so --push-b64 can't verify; use ADB sync push and compare sizes.
"$PY" -I ../tools/tv.py --push "$B/tvhome.apk" /data/local/tmp/tvhome.apk
printf '%s\n' \
  "pm install -r /data/local/tmp/tvhome.apk" \
  "rm -f /data/local/tmp/tvhome.apk" \
  "pm grant com.mihir.tvhome android.permission.ACCESS_FINE_LOCATION" \
  "pm disable-user --user 0 $STOCK_LAUNCHER" \
  "am force-stop com.android.tv.settings" \
  "input keyevent KEYCODE_HOME" \
  | "$PY" -I ../tools/tv.py --batch
echo "Installed. Press Home on the remote."
