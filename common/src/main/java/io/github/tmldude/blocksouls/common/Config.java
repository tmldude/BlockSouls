package io.github.tmldude.blocksouls.common;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/**
 * JSON config files in {@code config/blocksouls/<name>.json}.
 *
 * <p>Config classes are plain objects with a no-arg constructor and default field values. Keys missing
 * from the file keep their defaults, and the file is rewritten so newly added keys show up. A file that
 * fails to parse is left untouched and the defaults are used.
 */
public final class Config {
	private static final Logger LOGGER = LoggerFactory.getLogger("blocksouls");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private Config() {
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve("blocksouls");
	}

	public static <T> T load(String name, Class<T> type, Supplier<T> defaults) {
		return load(directory(), name, type, defaults);
	}

	public static <T> T load(Path dir, String name, Class<T> type, Supplier<T> defaults) {
		Path file = dir.resolve(name + ".json");
		T config = null;

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				config = GSON.fromJson(reader, type);
			} catch (IOException | JsonParseException e) {
				LOGGER.error("Failed to read config {}, using defaults", file, e);
				return defaults.get();
			}
		}

		// An empty file parses to null.
		if (config == null) {
			config = defaults.get();
		}

		save(dir, name, config);
		return config;
	}

	public static void save(String name, Object config) {
		save(directory(), name, config);
	}

	public static void save(Path dir, String name, Object config) {
		Path file = dir.resolve(name + ".json");

		try {
			Files.createDirectories(dir);

			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to write config {}", file, e);
		}
	}
}
