package io.github.tmldude.blocksouls.game.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
 * portal charges, and sieges firing off the synthetic clock in the Nether. Mock players can't change
 * dimension in the gametest harness, so the skeleton aliases its acts to one dimension (the aliased
 * dimension belongs to the highest open act, {@code ActMath}) and the Nether test runs natively in the
 * Nether via the test's {@code dimension}. Classic-shape tests live in {@link RunGameTests} and must keep
 * passing unchanged — the spine is behavior-neutral unless {@code runShape} opts in.
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

	/** Expedition config with a 2-second charge and the given act dimensions ("minecraft:..." ids). */
	private static ServerPlayer setUpExpedition(GameTestHelper helper, List<String> actDimensions) {
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
		config.acts = new ArrayList<>();
		for (int i = 0; i < actDimensions.size(); i++) {
			config.acts.add(new RunConfig.ActSpec("blocksouls_game:test_act" + (i + 1), actDimensions.get(i), "Act " + (i + 1)));
		}
		RunDebug.reloadActs(server);
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
	}

	@GameTest(environment = "blocksouls_game_test:act_skeleton", maxTicks = 600)
	public void expeditionWalksTheActSkeletonThroughPortalCharges(GameTestHelper helper) {
		// All four acts alias the test's own dimension: each completed charge opens the next act, and the
		// aliased dimension immediately belongs to it, so the party "arrives" without changing levels.
		String here = helper.getLevel().dimension().identifier().toString();
		setUpExpedition(helper, List.of(here, here, here, here));
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(Runs.get(server).phase() == RunPhase.DAY, "run auto-starts, was " + Runs.get(server).phase());
		helper.assertTrue(RunDebug.currentAct(server) == 1 && RunDebug.highestOpenAct(server) == 1, "starts in act 1");
		helper.assertTrue(EVENTS.contains("started:1") && EVENTS.contains("entered:1"), "act 1 started+entered, events " + EVENTS);
		helper.assertTrue(Acts.current(server).ordinal() == 1, "Acts contract reports act 1");

		int[] chargingFrom = {0};
		helper.onEachTick(() -> {
			MinecraftServer s = helper.getLevel().getServer();
			RunDebug.tickActs(s);
			int act = RunDebug.currentAct(s);
			if (act < 4 && act > chargingFrom[0]) {
				// Arrived in a new act: charge its exit portal once.
				String error = RunDebug.startCharge(s);
				helper.assertTrue(error == null, "charge from act " + act + " starts: " + error);
				chargingFrom[0] = act;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(RunDebug.currentAct(helper.getLevel().getServer()) == 4,
					"walked to act 4, at " + RunDebug.currentAct(helper.getLevel().getServer()) + ", events " + EVENTS);
			for (int act = 1; act <= 3; act++) {
				helper.assertTrue(EVENTS.contains("chargeStarted:" + act) && EVENTS.contains("chargeCompleted:" + act),
						"act " + act + " charged, events " + EVENTS);
			}
			for (int act = 1; act <= 4; act++) {
				helper.assertTrue(EVENTS.contains("started:" + act) && EVENTS.contains("entered:" + act),
						"act " + act + " started+entered, events " + EVENTS);
			}
			helper.assertTrue(Acts.current(helper.getLevel().getServer()).ordinal() == 4, "Acts contract reports act 4");
			helper.assertTrue(RunDebug.highestOpenAct(helper.getLevel().getServer()) == 4, "all four acts open");
			tearDown(helper);
		});
	}

	@GameTest(environment = "blocksouls_game_test:act_nether_siege", dimension = "minecraft:the_nether", maxTicks = 400)
	public void syntheticClockRunsSiegesInTheNether(GameTestHelper helper) {
		// The test (and its mock player) live in the real Nether; act 1 is the overworld, act 2 the Nether.
		// Charging act 1's portal opens act 2, and the party-follow moves the siege front to the player.
		setUpExpedition(helper, List.of("minecraft:overworld", "minecraft:the_nether"));
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(RunDebug.startCharge(server) == null, "the portal charge starts");
		int[] step = {0};
		helper.onEachTick(() -> {
			MinecraftServer s = helper.getLevel().getServer();
			RunDebug.tickActs(s);
			switch (step[0]) {
				case 0 -> {
					if (RunDebug.currentAct(s) == 2) {
						helper.assertTrue(EVENTS.contains("chargeCompleted:1") && EVENTS.contains("started:2"),
								"charge opened act 2, " + EVENTS);
						helper.assertTrue(EVENTS.contains("entered:2"), "the party in the Nether entered act 2, " + EVENTS);
						helper.assertTrue(RunSystem.activeClock(s).synthetic(), "the Nether runs on the synthetic clock");
						EVENTS.clear();
						RunDebug.skipSyntheticTo(s, 18000);
						step[0] = 1;
					}
				}
				case 1 -> {
					if (Runs.get(s).phase() == RunPhase.WAVE) {
						helper.assertTrue(EVENTS.contains("dusk:1"), "synthetic dusk fired DUSK, " + EVENTS);
						helper.assertTrue(Runs.get(s).threat() >= 1, "the threat counter ticked");
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
