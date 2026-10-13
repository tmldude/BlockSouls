package io.github.tmldude.blocksouls.game.player.rune;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rune profile files: plain JSON through Gson, no Minecraft classes (unit-tested). */
public final class RuneProfileIO {
	private static final Logger LOGGER = LoggerFactory.getLogger("blocksouls_game");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private RuneProfileIO() {
	}

	/** A fresh profile when nothing is there or it doesn't parse (a broken file is never a crash). */
	public static RuneProfile load(Path file) {
		if (!Files.exists(file)) {
			return new RuneProfile();
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			RuneProfile profile = GSON.fromJson(reader, RuneProfile.class);
			if (profile == null) {
				return new RuneProfile();
			}
			if (profile.unlocked == null) {
				profile.unlocked = new java.util.ArrayList<>();
			}
			return profile;
		} catch (IOException | JsonParseException e) {
			LOGGER.error("Failed to read rune profile {}, starting fresh", file, e);
			return new RuneProfile();
		}
	}

	/** Writes atomically (tmp file, then move). */
	public static void save(Path file, RuneProfile profile) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(profile, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOGGER.error("Failed to write rune profile {}", file, e);
		}
	}
}
