package io.github.tmldude.blocksouls.game.player.summary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.act.Acts;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.run.RunState;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.boon.Boon;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;
import io.github.tmldude.blocksouls.game.player.rune.PlayerRunes;
import io.github.tmldude.blocksouls.game.run.RunSummary;

/**
 * Run summary v2 (PLAN2 §3g): per-player cards at run end — class, boss kills, revives given, time downed (with a
 * "most time downed" badge), the best boon, the peak build tags and the rune souls paid — on top of the run line
 * (wave, threat, nights, acts, bosses). Tallies come from events during the run; the cards go to every client and
 * one JSON line goes to {@code blocksouls-runs.log} next to the run's own line. The v1 title + chat summary stays.
 *
 * <p>Register after {@link PlayerRunes} so the rune payout is known when the run-over event reaches this.
 */
public final class PlayerRunSummary {
	private static final SummaryStats STATS = new SummaryStats();
	private static SummaryConfig config = new SummaryConfig();
	private static int sampleTimer;

	private PlayerRunSummary() {
	}

	public static void register(SummaryConfig summaryConfig) {
		config = summaryConfig;
		Networking.registerClientbound(SummaryPayloads.Cards.TYPE, SummaryPayloads.Cards.CODEC);

		RunEvents.RUN_STARTED.register((server, state) -> STATS.clear());
		PlayerRunEvents.REVIVED.register((player, reviver) -> {
			if (reviver != null && reviver != player) {
				tally(reviver).revivesGiven++;
			}
		});
		BossEvents.KILLED.register((level, bossId, boss, players) -> {
			STATS.bossesFelled++;
			players.forEach(player -> tally(player).bossKills++);
		});
		PlayerRunEvents.BOON_GRANTED.register((player, boon) -> {
			offerBoon(player, boon);
			sampleBuild(player);
		});
		ServerTickEvents.END_SERVER_TICK.register(PlayerRunSummary::tick);
		RunEvents.RUN_OVER.register(PlayerRunSummary::onRunOver);

		DebugCommands.register((root, context) -> root.then(Commands.literal("summary")
				.then(Commands.literal("show").executes(PlayerRunSummary::show))));
	}

	public static SummaryStats stats() {
		return STATS;
	}

	private static SummaryStats.Tally tally(ServerPlayer player) {
		return STATS.tally(player.getUUID(), player.getPlainTextName());
	}

	private static void tick(MinecraftServer server) {
		if (!Runs.get(server).isActive()) {
			return;
		}
		boolean sample = ++sampleTimer >= Math.max(1, config.buildSampleIntervalTicks);
		if (sample) {
			sampleTimer = 0;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (PlayerStatus.isDowned(player)) {
				tally(player).downedTicks++;
			}
			if (sample) {
				sampleBuild(player);
			}
		}
	}

	private static void offerBoon(ServerPlayer player, Identifier id) {
		Boon boon = BoonCatalog.get(id);
		if (boon == null || boon.kind() != Boon.Kind.BOON) {
			return;
		}
		SummaryStats.offerBoon(tally(player), boon.id(), boon.tier().ordinal(), boon.keystone(), PlayerBoons.level(player, id));
	}

	private static void sampleBuild(ServerPlayer player) {
		List<BoonPayloads.TagView> line = PlayerBoons.buildLine(player, Math.max(0, config.buildTags));
		int stacks = line.stream().mapToInt(BoonPayloads.TagView::stacks).sum();
		SummaryStats.offerBuild(tally(player), line.stream().map(tag -> tag.tag().toString()).toList(), stacks);
	}

	private static void onRunOver(MinecraftServer server, RunState state) {
		if (!config.enabled) {
			return;
		}
		SummaryPayloads.Cards cards = build(server, state);
		if (config.showScreen) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Networking.sendToPlayer(player, cards);
			}
		}
		if (config.logCards) {
			appendLog(server, cards);
		}
	}

	/** The cards for every online player, from this run's tallies. */
	public static SummaryPayloads.Cards build(MinecraftServer server, RunState state) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer player : players) {
			sampleBuild(player);
		}
		List<Long> downed = players.stream().map(player -> tally(player).downedTicks).toList();
		int mostDowned = SummaryStats.mostDowned(downed);
		List<SummaryPayloads.Card> cards = new ArrayList<>();
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer player = players.get(i);
			SummaryStats.Tally tally = tally(player);
			cards.add(new SummaryPayloads.Card(
					tally.name,
					PlayerClasses.id(player),
					tally.bossKills,
					tally.revivesGiven,
					(int) (tally.downedTicks / 20),
					tally.bestBoon.isEmpty() ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(tally.bestBoon)),
					tally.bestBoonTier,
					tally.peakBuild.stream().map(Identifier::tryParse).filter(java.util.Objects::nonNull).toList(),
					PlayerRunes.lastPayout(player.getUUID()),
					i == mostDowned));
		}
		SummaryPayloads.RunLine run = new SummaryPayloads.RunLine(state.wave(), state.threat(), state.night(), Acts.highestOpen(server), STATS.bossesFelled);
		return new SummaryPayloads.Cards(run, cards, Math.max(1, config.columns));
	}

	/** One JSON line ({@code "kind": "players"}) per run, next to the run's own line in the same log. */
	static void appendLog(MinecraftServer server, SummaryPayloads.Cards payload) {
		JsonObject json = new JsonObject();
		json.addProperty("kind", "players");
		json.addProperty("ended", Instant.now().toString());
		json.addProperty("wave", payload.run().wave());
		json.addProperty("threat", payload.run().threat());
		json.addProperty("nights", payload.run().nights());
		json.addProperty("acts", payload.run().acts());
		JsonArray cards = new JsonArray();
		for (SummaryPayloads.Card card : payload.cards()) {
			JsonObject line = new JsonObject();
			line.addProperty("player", card.name());
			card.playerClass().ifPresent(id -> line.addProperty("class", id.toString()));
			line.addProperty("bossKills", card.bossKills());
			line.addProperty("revivesGiven", card.revivesGiven());
			line.addProperty("downedSeconds", card.downedSeconds());
			line.addProperty("mostDowned", card.mostDowned());
			card.bestBoon().ifPresent(id -> line.addProperty("bestBoon", id.toString()));
			JsonArray build = new JsonArray();
			card.build().forEach(tag -> build.add(tag.toString()));
			line.add("build", build);
			line.addProperty("runeSouls", card.runeSouls());
			cards.add(line);
		}
		json.add("cards", cards);
		Path file = server.getServerDirectory().resolve(RunSummary.LOG_FILE);
		try {
			Files.writeString(file, json + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			BlockSoulsGame.LOGGER.error("Failed to append the player cards to {}", file, e);
		}
	}

	/** {@code /bs summary show}: the cards so far, on the caller's screen (testing, screenshots). */
	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		Networking.sendToPlayer(player, build(server, Runs.get(server)));
		ctx.getSource().sendSuccess(() -> Component.literal("Run summary cards sent"), false);
		return 1;
	}
}
