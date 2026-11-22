#!/bin/zsh
# Builds and signs build/dinolite.apk. Usage: ./build.sh
set -euo pipefail
cd "${0:A:h}"
SDK="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
TOOLS="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
export JAVA_HOME="$(brew --prefix openjdk@21)"
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf build && mkdir -p build/gen build/classes build/dex
"$TOOLS/aapt2" compile --dir res -o build/res.zip
"$TOOLS/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --java build/gen -o build/base.apk build/res.zip
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR:$TOOLS/core-lambda-stubs.jar" -d build/classes \
  build/gen/dev/dinolite/widget/R.java src/dev/dinolite/widget/*.java
"$TOOLS/d8" --lib "$JAR" --min-api 33 --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
[[ -f dinolite.keystore ]] || keytool -genkeypair -keystore dinolite.keystore -storepass dinolite -keypass dinolite \
  -alias dinolite -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Dinolite Widget"
"$TOOLS/apksigner" sign --ks dinolite.keystore --ks-pass pass:dinolite --out build/dinolite.apk build/aligned.apk
echo "Built $PWD/build/dinolite.apk"
