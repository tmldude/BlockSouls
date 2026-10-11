# Offline check

For sandboxes that can't reach `maven.fabricmc.net` / Mojang, so `./gradlew build` can't run.
It type-checks our sources against **decompiled Minecraft 26.3** and **Fabric API / Loader sources**,
and runs the unit tests for pure-logic code. It is not a substitute for `./gradlew build`: it doesn't
apply mixins, check mixin target strings, or package jars. Use the real build whenever it's available.

```sh
tools/offline-check/setup.sh            # once: clones sources, fetches jars (needs github.com git + Maven Central mirror)
tools/offline-check/typecheck.sh        # all modules, or: typecheck.sh movement common
tools/offline-check/runtests.sh         # unit tests (pure logic only: tests can't reach Minecraft classes)
```

Everything is cached in `$OFFLINE_CHECK_HOME` (default `~/.cache/blocksouls-offline-check`).
Needs JDK 25 at `$JAVA25_HOME` (default `/usr/lib/jvm/java-25-openjdk-amd64`; `apt install openjdk-25-jdk-headless`).

Vanilla sources come from `extremeheat/extracted_minecraft_data` (branch `client26.3`). When checking a
mixin target, grep those sources for the exact method name and signature.
