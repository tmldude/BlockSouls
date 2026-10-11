#!/bin/sh
# One-command BlockSouls server install for a fresh Ubuntu/Debian VPS (e.g. Hetzner). Run as root:
#
#   curl -fsSL https://raw.githubusercontent.com/tmldude/BlockSouls/main/docker/install.sh | sh -s -- \
#       --whitelist "Alice,Bob,Carol" --ops "Alice"
#
# It installs Docker, clones the repo to /opt/blocksouls, writes docker/.env (generating the RCON password),
# opens only SSH + Minecraft in ufw, schedules the daily 05:00 update check, and starts the server.
# Safe to re-run: an existing .env is kept, the checkout is updated.
set -eu

DIR=/opt/blocksouls
REPO=https://github.com/tmldude/BlockSouls.git
WHITELIST=""
OPS=""
TAG=latest

while [ $# -gt 0 ]; do
	case "$1" in
		--whitelist) WHITELIST="$2"; shift 2 ;;
		--ops) OPS="$2"; shift 2 ;;
		--tag) TAG="$2"; shift 2 ;;
		--dir) DIR="$2"; shift 2 ;;
		*) echo "unknown option: $1" >&2; exit 1 ;;
	esac
done

if [ "$(id -u)" -ne 0 ]; then
	echo "Run as root (or with sudo)." >&2
	exit 1
fi

say() { printf '\n==> %s\n' "$*"; }

say "Installing Docker"
if ! command -v docker >/dev/null 2>&1; then
	curl -fsSL https://get.docker.com | sh
fi
command -v git >/dev/null 2>&1 || { apt-get update -q && apt-get install -yq git; }

say "Fetching BlockSouls into $DIR"
if [ -d "$DIR/.git" ]; then
	git -C "$DIR" pull -q --ff-only
else
	git clone -q --depth 1 "$REPO" "$DIR"
fi
cd "$DIR/docker"

if [ ! -f .env ]; then
	say "Writing docker/.env"
	if [ -z "$WHITELIST" ] && [ -t 0 ]; then
		printf 'Minecraft usernames allowed to join (comma-separated): '
		read -r WHITELIST
	fi
	if [ -z "$OPS" ] && [ -t 0 ]; then
		printf 'Usernames to make operators (comma-separated, can be empty): '
		read -r OPS
	fi
	if [ -z "$WHITELIST" ]; then
		echo "A whitelist is required: pass --whitelist \"Name1,Name2\"." >&2
		exit 1
	fi
	umask 077
	cat > .env <<ENV
WHITELIST=$WHITELIST
OPS=$OPS
RCON_PASSWORD=$(openssl rand -hex 24)
BLOCKSOULS_TAG=$TAG
TZ=$(cat /etc/timezone 2>/dev/null || echo UTC)
ENV
else
	say "Keeping existing docker/.env"
fi

if command -v ufw >/dev/null 2>&1; then
	say "Firewall: allowing SSH and Minecraft only"
	ufw allow 22/tcp >/dev/null
	ufw allow 25565/tcp >/dev/null
	ufw --force enable >/dev/null
fi

say "Scheduling the daily update check (05:00 server time)"
cat > /etc/cron.d/blocksouls <<CRON
# Pull a new BlockSouls image if there is one and restart (players get a 60 s warning).
0 5 * * * root $DIR/docker/update.sh >> /var/log/blocksouls-update.log 2>&1
CRON

say "Starting the server (first start downloads mods and generates the world; give it a few minutes)"
docker compose pull -q
docker compose up -d

IP=$(curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
say "Done. Friends connect to: $IP"
echo "Logs:    cd $DIR/docker && docker compose logs -f mc"
echo "Console: cd $DIR/docker && docker compose exec mc rcon-cli"
