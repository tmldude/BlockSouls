# BlockSouls modpack

## Playing

1. Install the [Modrinth App](https://modrinth.com/app).
2. Download `BlockSouls-<version>.mrpack` from the latest GitHub release.
3. In the Modrinth App: **Add pack from file** (the `+` button → **From file**) and pick the `.mrpack`.
4. Launch it and join `<server-ip>`.

To update, download the new `.mrpack` and import it the same way.

**How do I craft X?** The pack ships a recipe viewer (EMI, or REI if EMI has no build for this Minecraft version):
hover an item and press `R` for its recipes, `U` for its uses. Without it, the vanilla recipe book (the green book in
the crafting table) shows every BlockSouls recipe once you've held one of its ingredients, and the **BlockSouls**
advancement tab (`L`) walks you through guns, the kart and the run step by step.

## Maintaining (packwiz)

This folder is a [packwiz](https://packwiz.infra.link/) pack pinned to Minecraft 26.3 / Fabric Loader 0.19.5.

```sh
./add-mods.sh                    # add the third-party mod list (needs Modrinth access)
packwiz modrinth add <slug>      # add one more mod
packwiz refresh                  # after editing files by hand
packwiz modrinth export          # build BlockSouls-<version>.mrpack
```

Our own four mods aren't committed here: `release.yml` copies the freshly built jars into `mods/` and they
ship inside the `.mrpack` as overrides.
