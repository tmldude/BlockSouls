#!/bin/bash
# Fetches everything typecheck.sh / runtests.sh need. Safe to re-run.
set -euo pipefail
H=${OFFLINE_CHECK_HOME:-$HOME/.cache/blocksouls-offline-check}
MC=$(sed -n 's/^minecraft_version=//p' "$(dirname "$0")/../../gradle.properties")
mkdir -p "$H/libs"
cd "$H"

clone() { # url dir [branch]
	[ -d "$2" ] && return
	git clone -q --depth 1 ${3:+-b "$3"} "$1" "$2"
}
clone https://github.com/FabricMC/fabric.git fabric-src
clone https://github.com/FabricMC/fabric-loader.git fabric-loader
clone https://github.com/Mojang/DataFixerUpper.git DataFixerUpper
clone https://github.com/Mojang/brigadier.git brigadier
if [ ! -d mc ]; then
	git clone -q --depth 1 --single-branch -b "client$MC" --filter=blob:none --no-checkout \
		https://github.com/extremeheat/extracted_minecraft_data.git mc
	git -C mc sparse-checkout set client/net/minecraft client/com/mojang
	git -C mc checkout -q
fi

# repo1.maven.org rate-limits some sandboxes; Google's mirror of Maven Central doesn't.
M=https://maven-central.storage-download.googleapis.com/maven2
jar() { # group artifact version
	local f="libs/$2-$3.jar"
	[ -f "$f" ] || curl -sSf --max-time 120 -o "$f" "$M/${1//.//}/$2/$3/$2-$3.jar"
}
for a in netty-buffer netty-codec netty-codec-base netty-codec-http netty-common netty-handler netty-resolver \
		netty-transport netty-transport-classes-epoll netty-transport-classes-kqueue netty-transport-native-unix-common; do
	jar io.netty "$a" 4.2.19.Final
done
for a in lwjgl lwjgl-sdl lwjgl-opengl lwjgl-vulkan lwjgl-openal lwjgl-stb lwjgl-freetype lwjgl-vma lwjgl-shaderc \
		lwjgl-spvc lwjgl-tinyfd lwjgl-jemalloc; do
	jar org.lwjgl "$a" 3.4.3
done
jar com.google.code.gson gson 2.14.0
jar com.google.guava guava 33.7.2-jre
jar com.google.code.findbugs jsr305 3.0.2
jar it.unimi.dsi fastutil 8.5.19
jar org.slf4j slf4j-api 2.0.17
jar org.joml joml 1.10.9
jar org.jspecify jspecify 1.0.1
jar org.jetbrains annotations 26.1.0
jar org.apache.commons commons-lang3 3.21.0
jar org.apache.commons commons-compress 1.28.0
jar commons-io commons-io 2.22.0
jar org.apache.logging.log4j log4j-api 2.25.3
jar org.apache.logging.log4j log4j-core 2.25.3
jar net.sf.jopt-simple jopt-simple 5.0.4
jar com.github.oshi oshi-core 6.9.1
jar net.java.dev.jna jna 5.19.1
jar net.fabricmc sponge-mixin 0.17.4+mixin.0.8.7
jar io.github.llamalad7 mixinextras-fabric 0.5.5
for a in asm asm-tree asm-commons asm-util; do jar org.ow2.asm "$a" 9.11; done
jar org.junit.jupiter junit-jupiter-api 6.1.3
jar org.junit.platform junit-platform-commons 6.1.3
jar org.junit.platform junit-platform-console-standalone 6.1.3
jar org.opentest4j opentest4j 1.3.0
jar org.apiguardian apiguardian-api 1.1.2

echo "offline-check ready in $H"
