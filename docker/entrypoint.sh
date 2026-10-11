#!/bin/sh
# Default VERSION / FABRIC_LOADER_VERSION to the versions the mods were built against,
# then hand off to the itzg start script. Values set in compose still win.
set -e
. /etc/blocksouls.env
export VERSION="${VERSION:-$BLOCKSOULS_MC_VERSION}"
export FABRIC_LOADER_VERSION="${FABRIC_LOADER_VERSION:-$BLOCKSOULS_LOADER_VERSION}"
exec /image/scripts/start "$@"
