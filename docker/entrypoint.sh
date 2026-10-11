#!/bin/sh
# Default VERSION / FABRIC_LOADER_VERSION to the versions the mods were built against,
# then hand off to the itzg start script. Values set in compose still win.
set -e
. /etc/blocksouls.env

# `/bs run new` drops this marker and stops the server; the next start begins on a fresh world.
if [ -f /data/blocksouls-new-run ]; then
	rm -rf /data/"${LEVEL:-world}" /data/"${LEVEL:-world}"_nether /data/"${LEVEL:-world}"_the_end
	rm -f /data/blocksouls-new-run
fi
export VERSION="${VERSION:-$BLOCKSOULS_MC_VERSION}"
export FABRIC_LOADER_VERSION="${FABRIC_LOADER_VERSION:-$BLOCKSOULS_LOADER_VERSION}"
exec /image/scripts/start "$@"
