package io.github.tmldude.blocksouls.game.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.event.DirectorEvents;
import io.github.tmldude.blocksouls.game.run.RunConfig;
import io.github.tmldude.blocksouls.game.run.RunDebug;
import io.github.tmldude.blocksouls.game.run.RunSystem;
import io.github.tmldude.blocksouls.game.run.director.Composition;
import io.github.tmldude.blocksouls.game.run.director.Director;
import io.github.tmldude.blocksouls.game.run.director.DirectorConfig;
import io.github.tmldude.blocksouls.game.run.director.DirectorLog;
import io.github.tmldude.blocksouls.game.run.director.Objectives;
import io.github.tmldude.blocksouls.game.run.director.Pacer;

/**
 * The AI Director (PLAN2 §3b) steering a scripted expedition siege: a camping, wounded party draws flankers
 * from the influence map's cold side; scripted high stress starts a lull that releases no spawns and ends
 * within the lull ceiling; scripted calm spikes; then a supply drop objective is held to completion.
 */
public class DirectorGameTests {
	private static final List<String> OBJECTIVES = new ArrayList<>();

	static {
		DirectorEvents.OBJECTIVE_ENDED.register((server, objective, success) -> OBJECTIVES.add(objective.getPath() + ":" + success));
	}

	private enum Stage {
		FLANK,
		LULL,
		SPIKE,
		OBJECTIVE,
		DONE
	}

	private static int spikes() {
		return (int) Director.log().tail(500, DirectorLog.Kind.MOOD).stream().filter(entry -> entry.text().contains("-> SPIKE")).count();
	}

	@GameTest(environment = "blocksouls_game_test:director_expedition", maxTicks = 2400)
	public void directorPacesAnExpeditionSiege(GameTestHelper helper) {
		DirectorConfig director = DirectorTestSupport.quietConfig();
		director.stress.smoothing = 0; // the scripted stress holds; the raw reading still drives composition
		director.stress.damagePool = 10;
		director.levers.lullSeconds = 60; // asks for more than the ceiling allows
		director.levers.moodCooldownSeconds = 1;
		director.levers.campingSeconds = 2;
		director.levers.flankShare = 1;
		director.pacing.lullCeilingSeconds = 4;
		director.objectives.distance = 4;
		director.objectives.supplyDropSeconds = 3;
		ServerPlayer player = DirectorTestSupport.setUp(helper, director);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		RunConfig run = RunSystem.config();
		run.baseCount = 500; // the wave never runs dry; the test kills each spawn as it appears
		OBJECTIVES.clear();
		helper.assertTrue(Director.active(server), "Director drives the expedition");

		RunDebug.startWave(server, 1);
		Director.setStress(0.5);
		Vec3[] centre = {player.position()};
		Map<UUID, Vec3> seen = new HashMap<>();
		int[] flankers = {0, 0}; // spawns seen while FLANKERS: total, behind the player
		Stage[] stage = {Stage.FLANK};
		int[] tick = {0};
		int[] mark = {0, 0}; // stage start (lull: its first tick; spike: spikes logged so far), spawns at stage start
		double[] stress = {0.5};

		helper.onEachTick(() -> {
			tick[0]++;
			Director.setStress(stress[0]);
			if (tick[0] == 70) {
				// After spawn protection: one hit fills the damage signal, so the camping party reads as pinned (flankers, not brutes).
				player.hurtServer(level, level.damageSources().generic(), 5);
			}
			player.setHealth(player.getMaxHealth());
			player.snapTo(centre[0].x, centre[0].y, centre[0].z, 0, 0);
			for (UUID id : List.copyOf(RunDebug.waveMobs(server))) {
				Entity mob = level.getEntity(id);
				if (mob == null || seen.containsKey(id)) {
					continue;
				}
				seen.put(id, mob.position());
				if (Director.composition() == Composition.FLANKERS) {
					flankers[0]++;
					if (mob.getZ() < centre[0].z) {
						flankers[1]++; // the player faces +Z (yaw 0): the flank is behind, at -Z
					}
				}
				mob.kill(level);
			}

			switch (stage[0]) {
				case FLANK -> {
					if (flankers[0] >= 8) {
						helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.FLANK, "Flanking"), "flank decision logged" + DirectorTestSupport.dump());
						helper.assertTrue(flankers[1] >= flankers[0] * 3 / 4,
								"flankers come from behind: " + flankers[1] + " of " + flankers[0] + DirectorTestSupport.dump());
						stress[0] = 0.95;
						stage[0] = Stage.LULL;
						mark[0] = -1;
					}
				}
				case LULL -> {
					if (mark[0] < 0 && Director.mood() == Pacer.Mood.LULL) {
						mark[0] = tick[0];
						mark[1] = seen.size();
					} else if (mark[0] >= 0 && Director.mood() == Pacer.Mood.LULL) {
						helper.assertTrue(seen.size() <= mark[1] + 1, "a lull releases no spawns: " + (seen.size() - mark[1]) + " spawned");
					} else if (mark[0] >= 0) {
						int lasted = tick[0] - mark[0];
						helper.assertTrue(lasted <= (director.pacing.lullCeilingSeconds + 2) * 20,
								"the lull asked " + director.levers.lullSeconds + "s but the ceiling is " + director.pacing.lullCeilingSeconds + "s; it ran "
										+ lasted + " ticks" + DirectorTestSupport.dump());
						helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.MOOD, "-> LULL for " + director.pacing.lullCeilingSeconds + "s"),
								"lull logged at the ceiling" + DirectorTestSupport.dump());
						stress[0] = 0.05;
						stage[0] = Stage.SPIKE;
						mark[0] = spikes();
						mark[1] = seen.size();
					}
				}
				case SPIKE -> {
					if (spikes() > mark[0] && seen.size() > mark[1]) {
						stress[0] = 0.5;
						stage[0] = Stage.OBJECTIVE;
						String error = Objectives.start(server, Objectives.SUPPLY_DROP);
						helper.assertTrue(error == null, "supply drop starts: " + error);
					}
				}
				case OBJECTIVE -> {
					BlockPos drop = Objectives.position();
					if (drop != null) {
						// Hold the zone: stand next to the drop.
						centre[0] = Vec3.atBottomCenterOf(drop.offset(1, 0, 0));
					}
					if (OBJECTIVES.contains(Objectives.SUPPLY_DROP + ":true")) {
						stage[0] = Stage.DONE;
					}
				}
				case DONE -> {
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stage[0] == Stage.DONE, "at stage " + stage[0] + ", " + seen.size() + " spawns, objectives " + OBJECTIVES
					+ DirectorTestSupport.dump());
			helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.OBJECTIVE, "supply_drop completed"), "objective logged");
			helper.assertTrue(Director.log().violations() == 0, "no pacing violations" + DirectorTestSupport.dump());
			for (DirectorLog.Entry entry : Director.log().tail(500, null)) {
				helper.assertTrue(entry.line().matches("\\[ *\\d+:\\d\\d] [A-Z]+ +\\S.*"), "log line reads as /bs director log prints it: " + entry.line());
			}
			DirectorTestSupport.tearDown(helper);
		});
	}
}
