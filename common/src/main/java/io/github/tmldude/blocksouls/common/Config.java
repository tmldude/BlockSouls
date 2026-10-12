package io.github.tmldude.blocksouls.common;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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
 *
 * <p>{@link #load} remembers the instance it returns for each file name, and {@link #reload} later copies a
 * fresh read of the file into that <em>same</em> instance (field by field, recursively into nested config
 * objects), so a module's {@code CONFIG} picks up edits without restarting. {@code /bs config reload} does
 * this and fires {@code ConfigEvents.RELOADED} for anything that caches config-derived values.
 */
public final class Config {
	private static final Logger LOGGER = LoggerFactory.getLogger("blocksouls");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** Every file {@link #load} returned an instance for, by name; the latest load of a name wins. */
	private static final Map<String, Loaded<?>> LOADED = new LinkedHashMap<>();

	private record Loaded<T>(Path dir, Class<T> type, Supplier<T> defaults, T instance) {
	}

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
		track(dir, name, type, defaults, config);
		return config;
	}

	private static synchronized <T> void track(Path dir, String name, Class<T> type, Supplier<T> defaults, T instance) {
		LOADED.put(name, new Loaded<>(dir, type, defaults, instance));
	}

	/** The names of every config loaded so far, in load order. */
	public static synchronized List<String> loadedNames() {
		return new ArrayList<>(LOADED.keySet());
	}

	/** The live instance {@link #load} last returned for {@code name}, or null if it was never loaded. */
	public static synchronized Object loaded(String name) {
		Loaded<?> loaded = LOADED.get(name);
		return loaded != null ? loaded.instance() : null;
	}

	/**
	 * Re-reads {@code <name>.json} and copies its values into the live instance {@link #load} returned, then
	 * calls {@code after} with the name and that instance. Keys missing from the file go back to their
	 * defaults. A file that is missing or fails to parse changes nothing and doesn't call {@code after}. The
	 * file is not rewritten. Use {@code ConfigEvents.reload} to also fire {@code ConfigEvents.RELOADED}.
	 *
	 * @return null on success, otherwise why nothing changed
	 */
	public static String reload(String name, BiConsumer<String, Object> after) {
		Loaded<?> loaded;
		synchronized (Config.class) {
			loaded = LOADED.get(name);
		}
		if (loaded == null) {
			return "no config named '" + name + "' is loaded";
		}
		return reload(name, loaded, after);
	}

	private static <T> String reload(String name, Loaded<T> loaded, BiConsumer<String, Object> after) {
		Path file = loaded.dir().resolve(name + ".json");
		if (!Files.exists(file)) {
			return file.getFileName() + " is missing";
		}

		T fresh;
		try (Reader reader = Files.newBufferedReader(file)) {
			fresh = GSON.fromJson(reader, loaded.type());
		} catch (IOException | JsonParseException e) {
			LOGGER.error("Failed to reload config {}, keeping the current values", file, e);
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			return file.getFileName() + ": " + cause.getMessage();
		}
		if (fresh == null) {
			fresh = loaded.defaults().get();
		}

		copyFields(fresh, loaded.instance());
		after.accept(name, loaded.instance());
		return null;
	}

	/**
	 * Copies every instance field of {@code from} into {@code to} (same class). Nested config objects are
	 * copied into recursively, so references to them stay valid; lists, sets and maps are refilled in place
	 * when they're mutable. Strings, numbers, enums, records and arrays are assigned. Static and transient
	 * fields are skipped; a final field is only copied into, never reassigned.
	 */
	public static void copyFields(Object from, Object to) {
		if (from.getClass() != to.getClass()) {
			throw new IllegalArgumentException("Can't copy " + from.getClass().getName() + " into " + to.getClass().getName());
		}
		for (Class<?> type = to.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				int modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
					continue;
				}
				try {
					field.setAccessible(true);
					copyField(field, from, to);
				} catch (ReflectiveOperationException | RuntimeException e) {
					throw new IllegalStateException("Can't copy config field " + type.getName() + "." + field.getName(), e);
				}
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void copyField(Field field, Object from, Object to) throws IllegalAccessException {
		Object value = field.get(from);
		Object current = field.get(to);
		boolean isFinal = Modifier.isFinal(field.getModifiers());

		if (value != null && current != null && value != current) {
			if (value.getClass() == current.getClass() && isNested(value.getClass())) {
				copyFields(value, current);
				return;
			}
			try {
				if (value instanceof Collection newItems && current instanceof Collection items) {
					items.clear();
					items.addAll(newItems);
					return;
				}
				if (value instanceof Map newEntries && current instanceof Map entries) {
					entries.clear();
					entries.putAll(newEntries);
					return;
				}
			} catch (UnsupportedOperationException immutable) {
				// e.g. a List.of default: replace it below.
			}
		}
		if (!isFinal) {
			field.set(to, value);
		}
	}

	/** A config section to copy into: a plain class of ours, not a value type. */
	private static boolean isNested(Class<?> type) {
		return !type.isPrimitive() && !type.isArray() && !Enum.class.isAssignableFrom(type) && !type.isRecord()
				&& !Collection.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type)
				&& !type.getName().startsWith("java.") && !type.getName().startsWith("com.google.");
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
