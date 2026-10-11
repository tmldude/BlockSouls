#!/bin/bash
# Compiles and runs JUnit tests. Tests can only reach our main sources and a stub FabricLoader, not
# Minecraft, which keeps unit tests on pure logic.
# Usage: runtests.sh [module ...]   (default: all modules)
REPO=$(cd "$(dirname "$0")/../.." && pwd)
H=${OFFLINE_CHECK_HOME:-$HOME/.cache/blocksouls-offline-check}
JDK=${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}/bin
ALL=$(sed -n "s/^include //p" "$REPO/settings.gradle" | tr -d "',")
MODS=${*:-$ALL}
OUT=$H/test-out

CP=$(ls "$H"/libs/*.jar | tr '\n' ':')
SP="$REPO/tools/offline-check/test-stubs"
for m in $ALL; do SP="$SP:$REPO/$m/src/main/java"; done
FILES=$(for m in $MODS; do find "$REPO/$m/src/test/java" -name '*.java' 2>/dev/null; done)
[ -z "$FILES" ] && { echo "no tests"; exit 0; }

rm -rf "$OUT"; mkdir -p "$OUT"
if ! "$JDK/javac" --release 25 -proc:none -nowarn -encoding UTF-8 -sourcepath "$SP" -cp "$CP" -d "$OUT" $FILES > "$H/test.log" 2>&1; then
	grep -v '^Picked up' "$H/test.log" | head -80
	exit 1
fi
"$JDK/java" -cp "$OUT:$CP" org.junit.platform.console.ConsoleLauncher execute --scan-classpath "$OUT" \
	--disable-banner --disable-ansi-colors --details=tree 2>&1 | grep -vE '^Picked up|^SLF4J|^$'
exit "${PIPESTATUS[0]}"
