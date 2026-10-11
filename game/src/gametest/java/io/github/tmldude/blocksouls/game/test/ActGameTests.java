package io.github.tmldude.blocksouls.game.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.act.Acts;
import io.github.tmldude.blocksouls.common.event.ActEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.game.run.RunConfig;
import io.github.tmldude.blocksouls.game.run.RunDebug;
import io.github.tmldude.blocksouls.game.run.RunSystem;

/**
 * The v2 expedition spine in a headless server: the act machine walking a scripted act skeleton through
 * portal charges (acts aliased to the overworld/Nether until Stage 6 registers the real dimensions), and
 * sieges firing off the synthetic clock in the Nether. Classic-shape tests live in {@link RunGameTests}
 * and must keep passing unchanged — the spine is behavior-neutral unless {@code runShape} opts in.
 */
public class ActGameTests {
	/** Act events seen so far: "started:2", "entered:2", "chargeStarted:1", "chargeCompleted:1", "dusk:1", ... */
	private static final List<String> EVENTS = new ArrayList<>();

	static {
		ActEvents.ACT_STARTED.register((server, act) -> EVENTS.add("started:" + act.ordinal()));
		ActEvents.ACT_ENTERED.register((server, act) -> EVENTS.add("entered:" + act.ordinal()));
		ActEvents.PORTAL_CHARGE_STARTED.register((server, act) -> EVENTS.add("chargeStarted:" + act.ordinal()));
		ActEvents.PORTAL_CHARGE_COMPLETED.register((server, act) -> EVENTS.add("chargeCompleted:" + act.ordinal()));
		RunEvents.DUSK.register((server, night) -> EVENTS.add("dusk:" + night));
		RunEvents.DAWN.register((server, night) -> EVENTS.add("dawn:" + night));
		RunEvents.WAVE_STARTED.register((server, wave) -> EVENTS.add("wave:" + wave));
	}

	/** Expedition config with a 2-second charge and acts aliased to the two vanilla dimensions that exist. */
	private static ServerPlayer setUpExpedition(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RunDebug.reset(server);
		EVENTS.clear();
		RunConfig config = RunSystem.config();
		config.runShape = "expedition";
		config.portalChargeSeconds = 2;
		config.spawnMinDistance = 2;
		config.spawnMaxDistance = 4;
		config.minPlayerDistance = 1;
		config.wavesPerNight = new ArrayList<>(List.of(1));
		config.acts = new ArrayList<>(List.of(
				new RunConfig.ActSpec("blocksouls_game:act1", "minecraft:overworld", "Act One"),
				new RunConfig.ActSpec("blocksouls_game:act2", "minecraft:the_nether", "Act Two"),
				new RunConfig.ActSpec("blocksouls_game:act3", "minecraft:overworld", "Act Three"),
				new RunConfig.ActSpec("blocksouls_game:act4", "minecraft:the_nether", "Act Four")));
		RunDebug.reloadActs(server);
		// Keep a spot on the Nether roof loaded so the party can stand there without a real connection.
		server.getLevel(Level.NETHER).setChunkForced(0, 0, true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(4, 1, 4)));
		return player;
	}

	private static void tearDown(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RunDebug.reset(server);
		RunConfig defaults = new RunConfig();
		RunConfig config = RunSystem.config();
		config.runShape = defaults.runShape;
		config.portalChargeSeconds = defaults.portalChargeSeconds;
		config.spawnMinDistance = defaults.spawnMinDistance;
		config.spawnMaxDistance = defaults.spawnMaxDistance;
		config.minPlayerDistance = defaults.minPlayerDistance;
		config.wavesPerNight = defaults.wavesPerNight;
		config.acts = defaults.acts;
		RunDebug.reloadActs(server);
		server.getLevel(Level.NETHER).setChunkForced(0, 0, false);
	}

	private static void toNether(GameTestHelper helper, ServerPlayer player) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		player.teleportTo(nether, 0.5, 129, 0.5, Set.of(), 0, 0, false);
	}

	private static void toOverworld(GameTestHelper helper, ServerPlayer player) {
		player.teleportTo(helper.getLevel(), helper.absoluteVec(new Vec3(4, 1, 4)).x, helper.absoluteVec(new Vec3(4, 1, 4)).y,
				helper.absoluteVec(new Vec3(4, 1, 4)).z, Set.of(), 0, 0, false);
	}

	@GameTest(environment = "blocksouls_game_test:act_skeleton", maxTicks = 600)
	public void expeditionWalksTheActSkeletonThroughPortalCharges(GameTestHelper helper) {
		ServerPlayer player = setUpExpedition(helper);
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(Runs.get(server).phase() == RunPhase.DAY, "run auto-starts, was " + Runs.get(server).phase());
		helper.assertTrue(RunDebug.currentAct(server) == 1 && RunDebug.highestOpenAct(server) == 1, "starts in act 1");
		helper.assertTrue(EVENTS.contains("started:1") && EVENTS.contains("entered:1"), "act 1 started+entered, events " + EVENTS);
		helper.assertTrue(Acts.current(server).ordinal() == 1, "Acts contract reports act 1");

		// Scripted skeleton: charge → nether → charge → back (aliased act 3) → charge → nether (act 4).
		int[] step = {0};
		helper.onEachTick(() -> {
			MinecraftServer s = helper.getLevel().getServer();
			switch (step[0]) {
				case 0 -> {
					String error = RunDebug.startCharge(s);
					helper.assertTrue(error == null, "charge 1 starts: " + error);
					helper.assertTrue(RunDebug.charging(s), "charging");
					step[0] = 1;
				}
				case 1 -> {
					if (RunDebug.highestOpenAct(s) == 2) {
						helper.assertTrue(EVENTS.contains("chargeStarted:1") && EVENTS.contains("chargeCompleted:1"),
								"charge events, " + EVENTS);
						helper.assertTrue(EVENTS.contains("started:2"), "act 2 started, " + EVENTS);
						toNether(helper, player);
						step[0] = 2;
					}
				}
				case 2 -> {
					RunDebug.tickActs(s);
					if (RunDebug.currentAct(s) == 2) {
						helper.assertTrue(EVENTS.contains("entered:2"), "act 2 entered, " + EVENTS);
						helper.assertTrue(RunDebug.startCharge(s) == null, "charge 2 starts");
						step[0] = 3;
					}
				}
				case 3 -> {
					if (RunDebug.highestOpenAct(s) == 3) {
						toOverworld(helper, player);
						step[0] = 4;
					}
				}
				case 4 -> {
					RunDebug.tickActs(s);
					// The overworld is aliased by acts 1 and 3; with act 3 open it reads as act 3.
					if (RunDebug.currentAct(s) == 3) {
						helper.assertTrue(RunDebug.startCharge(s) == null, "charge 3 starts");
						step[0] = 5;
					}
				}
				case 5 -> {
					if (RunDebug.highestOpenAct(s) == 4) {
						toNether(helper, player);
						step[0] = 6;
					}
				}
				case 6 -> {
					RunDebug.tickActs(s);
					if (RunDebug.currentAct(s) == 4) {
						step[0] = 7;
					}
				}
				default -> {
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] == 7, "walked the skeleton, step " + step[0] + ", events " + EVENTS);
			helper.assertTrue(EVENTS.contains("entered:4"), "act 4 entered, " + EVENTS);
			helper.assertTrue(Acts.current(helper.getLevel().getServer()).ordinal() == 4, "Acts contract reports act 4");
			tearDown(helper);
		});
	}

	@GameTest(environment = "blocksouls_game_test:act_nether_siege", maxTicks = 400)
	public void syntheticClockRunsSiegesInTheNether(GameTestHelper helper) {
		ServerPlayer player = setUpExpedition(helper);
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(RunDebug.advanceAct(server) == null, "debug advance to act 2");
		toNether(helper, player);
		int[] step = {0};
		helper.onEachTick(() -> {
			MinecraftServer s = helper.getLevel().getServer();
			switch (step[0]) {
				case 0 -> {
					RunDebug.tickActs(s);
					if (RunDebug.currentAct(s) == 2) {
						helper.assertTrue(RunSystem.activeClock(s).synthetic(), "the Nether runs on the synthetic clock");
						RunDebug.skipSyntheticTo(s, 18000);
						step[0] = 1;
					}
				}
				case 1 -> {
					if (Runs.get(s).phase() == RunPhase.WAVE) {
						helper.assertTrue(EVENTS.contains("dusk:1"), "synthetic dusk fired DUSK, " + EVENTS);
						helper.assertTrue(EVENTS.contains("wave:1"), "wave 1 started off the synthetic clock, " + EVENTS);
						helper.assertTrue(Runs.get(s).threat() == 1, "the threat counter ticked");
						RunDebug.skipSyntheticTo(s, RunSystem.config().dawnTime);
						step[0] = 2;
					}
				}
				case 2 -> {
					if (Runs.get(s).phase() == RunPhase.DAY) {
						step[0] = 3;
					}
				}
				default -> {
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] == 3, "siege ran dusk→dawn in the Nether, step " + step[0] + ", events " + EVENTS);
			helper.assertTrue(EVENTS.contains("dawn:1"), "synthetic dawn fired DAWN, " + EVENTS);
			tearDown(helper);
		});
	}

	@GameTest(environment = "blocksouls_game_test:act_classic", maxTicks = 40)
	public void classicShapeIgnoresTheActMachine(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RunDebug.reset(server);
		EVENTS.clear();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(4, 1, 4)));
		helper.assertTrue(Runs.get(server).phase() == RunPhase.DAY, "run auto-starts");
		helper.assertTrue(RunDebug.startCharge(server) != null, "no portal charge in classic");
		helper.assertTrue(Acts.current(server).equals(Acts.CLASSIC), "classic reports the single overworld act");
		helper.assertTrue(Acts.highestOpen(server) == 1 && Acts.all(server).size() == 1, "one act, open");
		helper.assertTrue(!RunSystem.activeClock(server).synthetic(), "classic runs on the sun");
		helper.assertTrue(EVENTS.stream().noneMatch(e -> e.startsWith("started:") || e.startsWith("entered:")),
				"no act events in classic, " + EVENTS);
		RunDebug.reset(server);
		helper.succeed();
	}
}
