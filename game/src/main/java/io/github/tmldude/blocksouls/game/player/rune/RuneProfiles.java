package io.github.tmldude.blocksouls.game.player.rune;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

/**
 * Rune profiles on disk: {@code <server root>/blocksouls/runes/<uuid>.json}, a sibling of
 * {@code blocksouls-runs.log}. Never under the world folder, so {@code /bs run new}'s world wipe keeps them.
 * Loaded on first use and cached; every change writes the file ({@link RuneProfileIO}).
 */
public final class RuneProfiles {
	public static final String DIRECTORY = "blocksouls/runes";
	private static final Map<UUID, RuneProfile> CACHE = new HashMap<>();

	private RuneProfiles() {
	}

	public static Path directory(MinecraftServer server) {
		return server.getServerDirectory().resolve(DIRECTORY);
	}

	public static Path file(MinecraftServer server, UUID player) {
		return directory(server).resolve(player + ".json");
	}

	/** The player's profile, loaded from disk the first time (a missing or broken file is a fresh profile). */
	public static RuneProfile get(MinecraftServer server, UUID player) {
		return CACHE.computeIfAbsent(player, id -> RuneProfileIO.load(file(server, id)));
	}

	/** Writes the player's profile. */
	public static void save(MinecraftServer server, UUID player) {
		RuneProfile profile = CACHE.get(player);
		if (profile != null) {
			RuneProfileIO.save(file(server, player), profile);
		}
	}

	/** Drops the cached copy (a player leaving, or tests); the file stays. */
	public static void evict(UUID player) {
		CACHE.remove(player);
	}
}
