# BlockSouls server pack (without Docker)

The Docker image (see `docker/HOSTING.md` in the repo) is the easy way. To run a plain Fabric server instead:

1. Install a **Fabric server** for Minecraft **26.3** with the Fabric Loader version from the release notes
   (https://fabricmc.net/use/server/). Java 25 is required.
2. Copy everything in this zip's `mods/` folder into the server's `mods/` folder.
3. Add **Fabric API** for 26.3 from https://modrinth.com/mod/fabric-api to `mods/`.
4. Turn on the whitelist (`white-list=true` in `server.properties`) and start the server.

Updating: replace the `blocksouls-*.jar` files with the ones from the new release (same names) and restart.
New run: `/bs run new` in-game writes a marker; stop the server and delete the `world` folder before starting again.
