#!/usr/bin/env bash
# KA-Music歌词 构建脚本（无 Gradle，直接使用 Android SDK 工具链）
set -e

# ===== 依赖路径，按需修改 =====
ANDROID_JAR="${ANDROID_JAR:-/tmp/kl/p30/android-11/android.jar}"
XPOSED_JAR="${XPOSED_JAR:-/tmp/api-82.jar}"
SDK_DIR="${SDK_DIR:-/tmp/kl/sdk}"          # provider-classes.jar / model.jar
LIB_DIR="${LIB_DIR:-/tmp/kl/libs}"          # kotlin / serialization / parcelize
AX_DIR="${AX_DIR:-/tmp/kl/ax}"              # androidx / coroutines
R8_JAR="${R8_JAR:-/tmp/kl/r8.jar}"
KEYSTORE="${KEYSTORE:-kam2.jks}"
KS_PASS="${KS_PASS:-kam12345}"
KEY_PASS="${KEY_PASS:-kam12345}"
OUT="KA_Music_Lyrics.apk"

DEPS="$SDK_DIR/provider-classes.jar $SDK_DIR/model.jar \
$LIB_DIR/kotlin-stdlib-2.3.20.jar \
$LIB_DIR/kotlinx-serialization-json-jvm-1.10.0.jar \
$LIB_DIR/kotlinx-serialization-core-jvm-1.10.0.jar \
$LIB_DIR/kotlin-parcelize-runtime-2.3.20.jar \
$AX_DIR/ax-core.jar $AX_DIR/ax-corektx.jar $AX_DIR/ax-appcompatres.jar \
$AX_DIR/annotation-jvm-1.9.1.jar $AX_DIR/collection-jvm-1.4.5.jar \
$AX_DIR/kotlinx-coroutines-core-jvm-1.10.2.jar $AX_DIR/ca.jar"

echo "[1/6] 清理"
rm -rf gen classes res.zip base.apk dexout unsigned.apk aligned.apk "$OUT"

echo "[2/6] 编译资源"
aapt2 compile --dir res -o res.zip
aapt2 link -o base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    --java gen --min-sdk-version 27 --target-sdk-version 30 -A assets res.zip

echo "[3/6] 编译 Java"
javac -encoding UTF-8 -source 17 -target 17 -nowarn \
    -cp "$ANDROID_JAR:$XPOSED_JAR:$SDK_DIR/provider-classes.jar:$SDK_DIR/model.jar" \
    -d classes $(find src -name '*.java') gen/com/ka/music/lyrics/R.java

echo "[4/6] D8 生成 dex"
mkdir -p dexout
java -Xmx1400m -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 27 --lib "$ANDROID_JAR" \
    --output dexout --release $(find classes -name '*.class') $DEPS

echo "[5/6] 打包"
python3 - <<'PY'
import zipfile
dex = open('dexout/classes.dex', 'rb').read()
zin = zipfile.ZipFile('base.apk')
zo = zipfile.ZipFile('unsigned.apk', 'w')
for i in zin.infolist():
    ni = zipfile.ZipInfo(i.filename, date_time=i.date_time)
    ni.compress_type = i.compress_type
    zo.writestr(ni, zin.read(i.filename))
ni = zipfile.ZipInfo('classes.dex', date_time=(2026, 1, 1, 0, 0, 0))
ni.compress_type = zipfile.ZIP_DEFLATED
zo.writestr(ni, dex)
zo.close()
PY

echo "[6/6] 对齐与签名"
zipalign -p -f 4 unsigned.apk aligned.apk
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
    --out "$OUT" aligned.apk

echo "完成：$OUT"
