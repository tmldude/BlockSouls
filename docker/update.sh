#!/bin/sh
# Pulls the latest BlockSouls image and restarts the server only if it changed. install.sh runs this daily at 05:00
# (server time) from /etc/cron.d/blocksouls, so updates never kick anyone mid-evening.
set -e
cd "$(dirname "$0")"

image="ghcr.io/tmldude/blocksouls:$(sed -n 's/^BLOCKSOULS_TAG=//p' .env)"
[ "$image" = "ghcr.io/tmldude/blocksouls:" ] && image="ghcr.io/tmldude/blocksouls:latest"
before=$(docker image inspect --format '{{.Id}}' "$image" 2>/dev/null || true)
docker compose pull -q mc
after=$(docker image inspect --format '{{.Id}}' "$image")

if [ "$before" = "$after" ]; then
	echo "$(date -Is) BlockSouls is up to date"
	exit 0
fi

echo "$(date -Is) new BlockSouls image; restarting in 60 s"
docker compose exec -T mc rcon-cli "say Server updating in 60 seconds" >/dev/null 2>&1 || true
sleep 60
docker compose up -d
