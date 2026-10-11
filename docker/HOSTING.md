# Hosting the BlockSouls server

Target: a Hetzner CPX31 (4 vCPU, 8 GB RAM) or any Linux box with 8 GB RAM running Docker.
The server uses 6 GB (`MEMORY` in `compose.yml`); the rest is for the OS and the backup container.

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
   If your provider has a cloud firewall (Hetzner does), set the same two rules there too.

## 2. First start

```sh
git clone https://github.com/tmldude/BlockSouls.git
cd BlockSouls/docker
cp .env.example .env        # fill in WHITELIST, OPS, RCON_PASSWORD
docker compose pull         # or `docker compose build` to build from source
docker compose up -d
docker compose logs -f mc   # wait for "Done (...)! For help, type "help""
```

If the GitHub repo is private, its GHCR image is private too: run
`docker login ghcr.io -u <github-user>` with a personal access token that has `read:packages` first.

World, config and mods live in `docker/data/`; backups land in `docker/backups/` every 2 hours and are kept 7 days.

## 3. Updating

After a release tag's CI finishes:

```sh
cd BlockSouls/docker
docker compose pull && docker compose up -d
```

The new image replaces our mod jars in `data/mods/` on start; third-party mods are managed by
`MODRINTH_PROJECTS`. Then tell friends to update the pack in the Modrinth App.

To pin a release instead of `latest`, set `BLOCKSOULS_TAG=v0.1.0` in `.env`.

## 4. Admin

```sh
docker compose exec mc rcon-cli                # server console
docker compose exec mc rcon-cli whitelist add <name>
```

Restore a backup: stop the stack, then follow
https://github.com/itzg/docker-mc-backup#restoring-backups (the `restore-tar-backup` entrypoint).
