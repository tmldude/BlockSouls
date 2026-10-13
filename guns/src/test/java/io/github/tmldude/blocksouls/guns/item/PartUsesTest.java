package io.github.tmldude.blocksouls.guns.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** The part tooltips must match what the recipes actually craft. */
class PartUsesTest {
	private static final Pattern RESULT = Pattern.compile("\"result\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"blocksouls_guns:(\\w+)\"");

	private static Path recipes() {
		for (String base : List.of("src/main/resources", "guns/src/main/resources")) {
			Path dir = Path.of(base, "data", "blocksouls_guns", "recipe");
			if (Files.isDirectory(dir)) {
				return dir;
			}
		}
		throw new IllegalStateException("recipe folder not found from " + Path.of("").toAbsolutePath());
	}

	@Test
	void tooltipsMatchTheRecipes() throws IOException {
		Map<String, Set<String>> actual = new HashMap<>();
		try (Stream<Path> files = Files.list(recipes())) {
			for (Path file : files.toList()) {
				String json = Files.readString(file);
				Matcher result = RESULT.matcher(json);
				if (!result.find() || PartUses.USES.containsKey(result.group(1))) {
					continue;
				}
				for (String part : PartUses.USES.keySet()) {
					if (json.contains("\"blocksouls_guns:" + part + "\"")) {
						actual.computeIfAbsent(part, p -> new TreeSet<>()).add(result.group(1));
					}
				}
			}
		}
		for (Map.Entry<String, List<String>> entry : PartUses.USES.entrySet()) {
			assertEquals(new TreeSet<>(entry.getValue()), actual.getOrDefault(entry.getKey(), Set.of()), entry.getKey());
		}
		assertTrue(PartUses.of("primary_ammo").isEmpty());
	}
}
