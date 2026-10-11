package io.github.tmldude.blocksouls.game.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** The end-of-run summary (title + chat) and the run log line. Mechanics and stats only. */
public final class RunSummary {
	public static final String LOG_FILE = "blocksouls-runs.log";

	private RunSummary() {
	}

	/** Waves fully behind the team: a wave in progress when the run ended doesn't count. */
	static int wavesSurvived(RunData data, boolean diedMidWave) {
		return Math.max(0, diedMidWave ? data.wave - 1 : data.wave);
	}

	static long runSeconds(RunData data) {
		return Math.max(0, (data.endGameTime - data.startGameTime) / 20);
	}

	static String formatDuration(long seconds) {
		return String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
	}

	static void show(MinecraftServer server, RunData data, int wavesSurvived) {
		List<Component> lines = List.of(
				line("waves", "Waves survived: %s", wavesSurvived),
				line("nights", "Nights: %s", data.night),
				line("bosses", "Bosses felled: %s", bossNames(data)),
				line("time", "Run time: %s", formatDuration(runSeconds(data))),
				line("difficulty", "Difficulty: %s", data.difficulty.id()));
		Component title = Component.translatableWithFallback("title.blocksouls_game.run_over", "Run Over").withStyle(ChatFormatting.DARK_RED);
		Component subtitle = line("waves", "Waves survived: %s", wavesSurvived);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 30));
			player.connection.send(new ClientboundSetTitleTextPacket(title));
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			player.sendSystemMessage(title.copy().withStyle(ChatFormatting.BOLD));
			for (Component line : lines) {
				player.sendSystemMessage(line);
			}
		}
	}

	private static Component line(String key, String fallback, Object value) {
		return Component.translatableWithFallback("summary.blocksouls_game." + key, fallback, value).withStyle(ChatFormatting.GRAY);
	}

	private static Component bossNames(RunData data) {
		if (data.bossesKilled.isEmpty()) {
			return Component.literal("-");
		}
		Component names = Component.empty();
		for (int i = 0; i < data.bossesKilled.size(); i++) {
			if (i > 0) {
				names = names.copy().append(", ");
			}
			names = names.copy().append(bossName(data.bossesKilled.get(i)));
		}
		return names;
	}

	/** The boss's entity name if an entity type has its id, else a prettified id path. */
	static Component bossName(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return Component.literal(id);
		}
		return BuiltInRegistries.ENTITY_TYPE.getOptional(identifier)
				.<Component>map(type -> type.getDescription())
				.orElseGet(() -> Component.literal(prettify(identifier.getPath())));
	}

	static String prettify(String path) {
		StringBuilder out = new StringBuilder();
		for (String word : path.split("_")) {
			if (!word.isEmpty()) {
				if (!out.isEmpty()) {
					out.append(' ');
				}
				out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return out.toString();
	}

	/** Appends one JSON line per finished run to {@code blocksouls-runs.log} in the server directory. */
	static void appendLog(MinecraftServer server, RunData data, int wavesSurvived) {
		JsonObject json = new JsonObject();
		json.addProperty("ended", Instant.now().toString());
		json.addProperty("difficulty", data.difficulty.id());
		json.addProperty("wavesSurvived", wavesSurvived);
		json.addProperty("lastWave", data.wave);
		json.addProperty("nights", data.night);
		json.addProperty("endless", data.endless);
		json.addProperty("runSeconds", runSeconds(data));
		JsonArray bosses = new JsonArray();
		data.bossesKilled.forEach(bosses::add);
		json.add("bossesKilled", bosses);
		Path file = server.getServerDirectory().resolve(LOG_FILE);
		try {
			Files.writeString(file, json + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			BlockSoulsGame.LOGGER.error("Failed to append to {}", file, e);
		}
	}
}
