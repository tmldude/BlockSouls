#!/bin/sh
# Adds the third-party mods to the pack. Needs packwiz and network access to api.modrinth.com.
# Fabric API is required. The rest are nice-to-haves (performance, mod list): one without a 26.3 Fabric build
# is skipped with a warning instead of failing the release. Client/server sides come from Modrinth metadata.
set -e
cd "$(dirname "$0")"

packwiz modrinth add -y fabric-api

for slug in modmenu sodium lithium; do
	if ! packwiz modrinth add -y "$slug"; then
		echo "warning: $slug has no Minecraft 26.3 Fabric build yet; leaving it out of the pack" >&2
	fi
done

# Recipe viewer (client-side; the server doesn't need it): EMI, else REI, else none. Pick the first with a 26.3 build.
viewer=""
for slug in emi rei; do
	if packwiz modrinth add -y "$slug"; then
		viewer=$slug
		break
	fi
done
if [ -n "$viewer" ]; then
	echo "recipe viewer: $viewer"
else
	echo "warning: neither EMI nor REI has a Minecraft 26.3 Fabric build yet; the pack ships without a recipe viewer (the vanilla recipe book still unlocks every BlockSouls recipe)" >&2
fi
