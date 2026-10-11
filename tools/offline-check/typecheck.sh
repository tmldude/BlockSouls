#!/bin/bash
# Type-checks our sources against decompiled Minecraft + Fabric API/Loader sources.
# Usage: typecheck.sh [module ...]   (default: all modules)
REPO=$(cd "$(dirname "$0")/../.." && pwd)
H=${OFFLINE_CHECK_HOME:-$HOME/.cache/blocksouls-offline-check}
JAVAC=${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}/bin/javac
ALL=$(sed -n "s/^include //p" "$REPO/settings.gradle" | tr -d "',")
MODS=${*:-$ALL}
OUT=$H/typecheck-out

SP="$H/mc/client:$H/DataFixerUpper/src/main/java:$H/brigadier/src/main/java:$H/fabric-loader/src/main/java"
for d in "$H"/fabric-src/*/src/main/java "$H"/fabric-src/*/src/client/java; do SP="$SP:$d"; done
CP=$(ls "$H"/libs/*.jar | grep -v console-standalone | tr '\n' ':')

FILES=()
for m in $MODS; do
	while IFS= read -r f; do FILES+=("$f"); done < <(find "$REPO/$m/src/main/java" "$REPO/$m/src/client/java" "$REPO/$m/src/gametest/java" -name '*.java' 2>/dev/null)
done
rm -rf "$OUT"; mkdir -p "$OUT"
# -implicit:none: vanilla/Fabric sources are only used to resolve names, never compiled.
"$JAVAC" --release 25 -proc:none -implicit:none -Xmaxerrs 100000 -nowarn -encoding UTF-8 \
	-sourcepath "$SP" -cp "$CP" -d "$OUT" "${FILES[@]}" > "$H/typecheck.log" 2>&1
# Errors inside vanilla sources come from closed Mojang libraries (authlib, logging) we don't have.
# They don't affect checking our code, so only our errors are reported.
OURS=$(grep -c "^$REPO/.*: error:" "$H/typecheck.log")
grep -A3 "^$REPO/" "$H/typecheck.log"
echo "typecheck: ${#FILES[@]} files, $OURS error(s) in our sources"
[ "$OURS" -eq 0 ]
