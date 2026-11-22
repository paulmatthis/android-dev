#!/bin/zsh
# Builds and signs build/eist-controls.apk. Usage: ./build.sh
set -euo pipefail
cd "${0:A:h}"
SDK="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
TOOLS="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
export JAVA_HOME="$(brew --prefix openjdk@21)"
export PATH="$JAVA_HOME/bin:$PATH"

# The signing key and its password stay on this Mac (both are git-ignored).
# Back them up: every future update must be signed with the same key.
KS=eist-controls.keystore
[[ -f .signing-pass ]] || { openssl rand -hex 16 > .signing-pass; chmod 600 .signing-pass; }
PASS="$(cat .signing-pass)"
[[ -f $KS ]] || keytool -genkeypair -keystore $KS -storepass "$PASS" -keypass "$PASS" \
  -alias eist-controls -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Eist Controls Widget"

rm -rf build && mkdir -p build/gen build/classes build/dex
"$TOOLS/aapt2" compile --dir res -o build/res.zip
"$TOOLS/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --java build/gen -o build/base.apk build/res.zip
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR:$TOOLS/core-lambda-stubs.jar" -d build/classes \
  build/gen/dev/eistcontrols/widget/R.java src/dev/eistcontrols/widget/*.java
"$TOOLS/d8" --release --lib "$JAR" --min-api 28 --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"$TOOLS/apksigner" sign --ks $KS --ks-pass "pass:$PASS" --out build/eist-controls.apk build/aligned.apk
echo "Built $PWD/build/eist-controls.apk"
