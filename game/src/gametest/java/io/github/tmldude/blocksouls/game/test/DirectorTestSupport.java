package io.github.tmldude.blocksouls.game.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import io.github.tmldude.blocksouls.game.run.RunConfig;
import io.github.tmldude.blocksouls.game.run.RunDebug;
import io.github.tmldude.blocksouls.game.run.RunSystem;
import io.github.tmldude.blocksouls.game.run.director.Director;
import io.github.tmldude.blocksouls.game.run.director.DirectorConfig;
import io.github.tmldude.blocksouls.game.run.director.DirectorLog;

/**
 * An expedition run with one hurtable player standing on a stone floor, the Director on with a test config.
 * The Director is global state, so each Director test has its own environment (its own batch).
 */
final class DirectorTestSupport {
	/** The floor is {@code FLOOR}×{@code FLOOR}; the player stands at its centre. */
	static final int FLOOR = 24;
	static final int CENTRE = FLOOR / 2;

	private DirectorTestSupport() {
	}

	/** A Director config that only does what a test asks: no loot barrels, day packs or random objectives. */
	static DirectorConfig quietConfig() {
		DirectorConfig config = new DirectorConfig();
		config.loot.enabled = false;
		config.dayMobs.intervalSeconds = 100_000;
		config.objectives.chance = 0;
		config.pacing.ceilingSeconds = 100_000;
		return config;
	}

	static ServerPlayer setUp(GameTestHelper helper, DirectorConfig director) {
		MinecraftServer server = helper.getLevel().getServer();
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			server.getPlayerList().remove(player);
		}
		Director.setConfig(director);
		RunDebug.reset(server);
		RunConfig config = RunSystem.config();
		config.runShape = "expedition";
		config.portalChargeSeconds = 2;
		config.spawnMinDistance = 4;
		config.spawnMaxDistance = 8;
		config.minPlayerDistance = 2;
		config.wavesPerNight = new ArrayList<>(List.of(1));
		String here = helper.getLevel().dimension().identifier().toString();
		config.acts = new ArrayList<>();
		for (int i = 1; i <= 4; i++) {
			config.acts.add(new RunConfig.ActSpec("blocksouls_game:test_act" + i, here, "Act " + i));
		}
		RunDebug.reloadActs(server);
		for (int x = 0; x < FLOOR; x++) {
			for (int z = 0; z < FLOOR; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		ServerPlayer player = DownedTestPlayers.make(helper, CENTRE, CENTRE);
		player.setYRot(0);
		player.setYHeadRot(0);
		Director.resetLive(server);
		return player;
	}

	static void tearDown(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			server.getPlayerList().remove(player);
		}
		RunDebug.reset(server);
		RunConfig defaults = new RunConfig();
		RunConfig config = RunSystem.config();
		config.runShape = defaults.runShape;
		config.portalChargeSeconds = defaults.portalChargeSeconds;
		config.spawnMinDistance = defaults.spawnMinDistance;
		config.spawnMaxDistance = defaults.spawnMaxDistance;
		config.minPlayerDistance = defaults.minPlayerDistance;
		config.baseCount = defaults.baseCount;
		config.wavesPerNight = defaults.wavesPerNight;
		config.acts = defaults.acts;
		RunDebug.reloadActs(server);
		Director.setConfig(new DirectorConfig());
		Director.resetLive(server);
	}

	/** Whether the decision log has a {@code kind} line containing {@code text}. */
	static boolean logged(DirectorLog.Kind kind, String text) {
		return Director.log().tail(500, kind).stream().anyMatch(entry -> entry.text().contains(text));
	}

	/** The whole log as {@code /bs director log} prints it, for failure messages. */
	static String dump() {
		StringBuilder out = new StringBuilder();
		for (DirectorLog.Entry entry : Director.log().tail(500, null)) {
			out.append('\n').append(entry.line());
		}
		return out.toString();
	}
}
