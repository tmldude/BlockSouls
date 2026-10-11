#!/bin/sh
# Adds the third-party mods to the pack. Needs packwiz and network access to api.modrinth.com.
# packwiz refuses a mod with no build for the pack's Minecraft version + loader, so a failure here
# means that mod has no 26.3 Fabric build: pick an equivalent and record it in DECISIONS.md.
# Client/server sides come from each project's Modrinth metadata.
set -e
cd "$(dirname "$0")"

for slug in fabric-api modmenu cloth-config geckolib lithium sodium iris; do
	packwiz modrinth add -y "$slug"
done

# Gun framework (TACZ or Point Blank) is chosen in Phase 2b; add it here once picked.
