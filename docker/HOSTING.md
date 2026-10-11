# Hosting the BlockSouls server

Target: a Hetzner CPX31 (4 vCPU, 8 GB RAM) or any Linux box with 8 GB RAM running Docker.
The server uses 6 GB (`MEMORY` in `compose.yml`); the rest is for the OS and the backup container.

## Quick start (one command)

On a fresh Ubuntu 24.04 VPS, as root:

```sh
curl -fsSL https://raw.githubusercontent.com/tmldude/BlockSouls/main/docker/install.sh | sh -s -- \
    --whitelist "Alice,Bob,Carol" --ops "Alice"
```

It installs Docker, puts BlockSouls in `/opt/blocksouls`, writes `docker/.env` (with a generated RCON password),
allows only SSH + Minecraft through `ufw`, schedules the daily update check, starts the server and prints the IP
friends connect to. Re-running it is safe. Also set the same two firewall rules (22/tcp, 25565/tcp) in your
provider's cloud firewall (Hetzner has one).

The rest of this page is the manual version and day-to-day admin.

## 1. Provision

1. Create the server with Ubuntu 24.04 and your SSH key.
2. Install Docker Engine and the compose plugin (https://docs.docker.com/engine/install/ubuntu/).
3. Firewall: allow only **22/tcp** (SSH) and **25565/tcp** (Minecraft). With `ufw`:

   ```sh
   ufw default deny incoming
   ufw allow 22/tcp
   ufw allow 25565/tcp
   ufw enable
   ```

   Docker-published ports bypass `ufw`; only 25565 is published in `compose.yml`, so nothing else is exposed.

## 2. First start

```sh
git clone https://github.com/tmldude/BlockSouls.git
cd BlockSouls/docker
cp .env.example .env        # fill in WHITELIST, OPS, RCON_PASSWORD
docker compose pull         # or `docker compose build` to build from source
docker compose up -d
docker compose logs -f mc   # wait for "Done (...)! For help, type "help""
```

If the image pull is denied, the GHCR package is still private: make it public once in GitHub (Packages →
blocksouls → Package settings → Change visibility), or `docker login ghcr.io` with a token that has `read:packages`.

World, config and mods live in `docker/data/`; backups land in `docker/backups/` every 2 hours and are kept 7 days.

## 3. Updating

**Automatic:** `install.sh` adds `/etc/cron.d/blocksouls`, which runs `docker/update.sh` at **05:00 server time**
every day. If a new image exists, players get a 60-second warning and the server restarts on it; otherwise nothing
happens. Log: `/var/log/blocksouls-update.log`. Run `docker/update.sh` by hand to update right away.

**Manual:** after a release tag's CI finishes:

```sh
cd /opt/blocksouls/docker      # or wherever you cloned it
docker compose pull && docker compose up -d
```

The new image replaces our mod jars in `data/mods/` on start; third-party mods are managed by
`MODRINTH_PROJECTS`. Then tell friends to update the pack in the Modrinth App.

To pin a release instead of `latest`, set `BLOCKSOULS_TAG=v0.1.0` in `.env`.

## 4. New run

`/bs run new [difficulty]` in-game (as an op) ends the world: the server stops, Docker restarts it, and it starts on
a fresh world. Backups of the old world stay in `docker/backups/`.

## 5. Admin

```sh
docker compose exec mc rcon-cli                # server console
docker compose exec mc rcon-cli whitelist add <name>
```

Restore a backup: stop the stack, then follow
https://github.com/itzg/docker-mc-backup#restoring-backups (the `restore-tar-backup` entrypoint).
