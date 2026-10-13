#!/data/data/com.termux/files/usr/bin/bash
# Builds SunsetHook.apk on-device in Termux.
# Needs: javac, d8, aapt2, zipalign, apksigner, and apktool's framework at
# ~/.local/share/apktool/framework/1.apk (run apktool once to create it).
set -e
cd "$(dirname "$0")"
AJ=$PREFIX/share/java/android.jar
FW=$HOME/.local/share/apktool/framework/1.apk

rm -rf build && mkdir -p build/stubs build/classes build/dex
javac -source 8 -target 8 -bootclasspath "$AJ" -d build/stubs $(find stubs -name '*.java')
javac -source 8 -target 8 -bootclasspath "$AJ" -cp build/stubs -d build/classes $(find src -name '*.java')
d8 --min-api 29 --lib "$AJ" --classpath build/stubs --output build/dex $(find build/classes -name '*.class')
aapt2 link -I "$FW" --manifest AndroidManifest.xml -A assets -o build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
zipalign -f 4 build/unsigned.apk build/aligned.apk
: "${KEYSTORE_PASSWORD:?set KEYSTORE_PASSWORD (keystore password) before building}"
KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
apksigner sign --ks key.jks --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:$KEY_PASSWORD" --out SunsetHook.apk build/aligned.apk
echo "Built $(pwd)/SunsetHook.apk"
