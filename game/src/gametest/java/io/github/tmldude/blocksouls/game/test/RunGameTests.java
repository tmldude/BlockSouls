package io.github.tmldude.blocksouls.game.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.game.run.RunConfig;
import io.github.tmldude.blocksouls.game.run.RunDebug;
import io.github.tmldude.blocksouls.game.run.RunIds;
import io.github.tmldude.blocksouls.game.run.RunSystem;
import io.github.tmldude.blocksouls.game.run.StarterKit;

/**
 * The run loop in a headless server. The run is global state, so every test has its own test environment (each
 * environment is its own batch, and batches run one after another) and resets the run when it starts and ends.
 */
public class RunGameTests {
	/** Events seen so far: "started:3", "ended:3:true", "over", ... */
	private static final List<String> EVENTS = new ArrayList<>();

	static {
		RunEvents.WAVE_STARTED.register((server, wave) -> EVENTS.add("started:" + wave));
		RunEvents.WAVE_ENDED.register((server, wave, cleared) -> EVENTS.add("ended:" + wave + ":" + cleared));
		RunEvents.RUN_OVER.register((server, state) -> EVENTS.add("over:" + state.phase()));
	}

	/** Resets the run and makes waves spawn close by, inside the test's (loaded) area. */
	private static ServerPlayer setUp(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RunDebug.reset(server);
		EVENTS.clear();
		RunConfig config = RunSystem.config();
		config.spawnMinDistance = 2;
		config.spawnMaxDistance = 4;
		config.minPlayerDistance = 1;
		config.waveBreakSeconds = 1;
		config.wavesPerNight = new ArrayList<>(List.of(2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(4, 1, 4)));
		return player;
	}

	private static void tearDown(GameTestHelper helper) {
		RunDebug.reset(helper.getLevel().getServer());
		RunConfig defaults = new RunConfig();
		RunConfig config = RunSystem.config();
		config.spawnMinDistance = defaults.spawnMinDistance;
		config.spawnMaxDistance = defaults.spawnMaxDistance;
		config.minPlayerDistance = defaults.minPlayerDistance;
		config.waveBreakSeconds = defaults.waveBreakSeconds;
		config.wavesPerNight = defaults.wavesPerNight;
	}

	private static boolean holds(ServerPlayer player, Item item) {
		return player.getInventory().contains((ItemStack stack) -> stack.is(item));
	}

	@GameTest(environment = "blocksouls_game_test:run_autostart", maxTicks = 40)
	public void runAutoStartsOnJoinAndGrantsTheKit(GameTestHelper helper) {
		ServerPlayer player = setUp(helper);
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(Runs.get(server).phase() == RunPhase.DAY, "run should auto-start on join, was " + Runs.get(server).phase());
		helper.assertTrue(holds(player, Items.STONE_PICKAXE), "start kit has a stone pickaxe");
		Item pistol = StarterKit.pistol().orElseThrow();
		helper.assertTrue(holds(player, pistol), "start kit has the Starter Pistol");
		helper.assertTrue(RunDebug.gotKit(server).contains(player.getUUID()), "kit is recorded");
		tearDown(helper);
		helper.succeed();
	}

	@GameTest(environment = "blocksouls_game_test:run_spawn", maxTicks = 200, skyAccess = true)
	public void startingAWaveSpawnsMobs(GameTestHelper helper) {
		setUp(helper);
		MinecraftServer server = helper.getLevel().getServer();
		RunDebug.skipTo(server, 18000);
		RunDebug.startWave(server, 1);
		helper.assertTrue(Runs.get(server).phase() == RunPhase.WAVE, "wave phase");
		helper.assertTrue(EVENTS.contains("started:1"), "WAVE_STARTED fired");
		helper.succeedWhen(() -> {
			Set<UUID> mobs = RunDebug.waveMobs(server);
			helper.assertTrue(mobs.size() >= 3, "wave mobs spawned: " + mobs.size());
			Entity mob = helper.getLevel().getEntity(mobs.iterator().next());
			helper.assertTrue(mob instanceof Mob m && m.isPersistenceRequired() && m.entityTags().contains(RunIds.WAVE_MOB_TAG),
					"wave mobs are tagged and persistent");
			tearDown(helper);
		});
	}

	@GameTest(environment = "blocksouls_game_test:run_chain", maxTicks = 300, skyAccess = true)
	public void clearingAWaveChainsTheNextAfterTheBreak(GameTestHelper helper) {
		setUp(helper);
		MinecraftServer server = helper.getLevel().getServer();
		RunDebug.skipTo(server, 14000);
		boolean[] cleared = {false};
		helper.onEachTick(() -> {
			if (!cleared[0] && Runs.get(server).phase() == RunPhase.WAVE && Runs.get(server).wave() == 1 && !RunDebug.waveMobs(server).isEmpty()) {
				cleared[0] = true;
				RunDebug.clearWave(server);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(EVENTS.contains("ended:1:true"), "wave 1 cleared, events " + EVENTS);
			helper.assertTrue(Runs.get(server).phase() == RunPhase.WAVE && Runs.get(server).wave() == 2,
					"wave 2 follows the break, state " + Runs.get(server));
			tearDown(helper);
		});
	}

	@GameTest(environment = "blocksouls_game_test:run_dawn", maxTicks = 400, skyAccess = true)
	public void dawnEndsTheWaveButElitesKeepHunting(GameTestHelper helper) {
		setUp(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		RunDebug.skipTo(server, 14000);
		UUID[] picked = new UUID[2];
		helper.onEachTick(() -> {
			if (picked[0] != null || Runs.get(server).phase() != RunPhase.WAVE) {
				return;
			}
			List<UUID> mobs = new ArrayList<>(RunDebug.waveMobs(server));
			if (mobs.size() < 2) {
				return;
			}
			picked[0] = mobs.get(0);
			picked[1] = mobs.get(1);
			level.getEntity(picked[0]).addTag(RunIds.ELITE_TAG);
			RunDebug.skipTo(server, RunSystem.config().dawnTime);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(picked[0] != null, "wave spawned");
			helper.assertTrue(EVENTS.contains("ended:1:false"), "dawn ended the wave, events " + EVENTS);
			helper.assertTrue(Runs.get(server).phase() == RunPhase.DAY, "day after dawn");
			Entity elite = level.getEntity(picked[0]);
			helper.assertTrue(elite != null && elite.isAlive(), "the elite survives dawn");
			helper.assertTrue(RunDebug.huntingElites(server).contains(picked[0]), "the elite keeps hunting");
			helper.assertTrue(level.getEntity(picked[1]) == null, "ordinary wave mobs burn away");
			tearDown(helper);
		});
	}

	@GameTest(environment = "blocksouls_game_test:run_pistol", maxTicks = 40)
	public void endOfTheLastPistolWaveBreaksTheStarterPistol(GameTestHelper helper) {
		ServerPlayer player = setUp(helper);
		MinecraftServer server = helper.getLevel().getServer();
		Item pistol = StarterKit.pistol().orElseThrow();
		player.getEnderChestInventory().setItem(0, new ItemStack(pistol));
		helper.assertTrue(holds(player, pistol), "player starts with the pistol");
		int lastWave = RunSystem.config().starterPistolLastWave;
		RunDebug.startWave(server, lastWave);
		RunDebug.endWave(server);
		helper.assertTrue(EVENTS.contains("ended:" + lastWave + ":false"), "wave " + lastWave + " ended");
		helper.assertTrue(RunDebug.pistolsBroken(server), "pistols are marked broken");
		helper.assertFalse(holds(player, pistol), "pistol removed from the inventory");
		helper.assertTrue(player.getEnderChestInventory().getItem(0).isEmpty(), "pistol removed from the ender chest");
		tearDown(helper);
		helper.succeed();
	}

	@GameTest(environment = "blocksouls_game_test:run_over", maxTicks = 40)
	public void endRunSetsOverAndFiresRunOver(GameTestHelper helper) {
		setUp(helper);
		MinecraftServer server = helper.getLevel().getServer();
		RunSystem.endRun(server);
		helper.assertTrue(Runs.get(server).phase() == RunPhase.OVER, "phase OVER");
		helper.assertTrue(EVENTS.contains("over:OVER"), "RUN_OVER fired, events " + EVENTS);
		// A finished run never restarts waves on its own.
		RunDebug.skipTo(server, 14000);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(Runs.get(server).phase() == RunPhase.OVER, "still OVER at night");
			tearDown(helper);
			helper.succeed();
		});
	}

	@GameTest(environment = "blocksouls_game_test:run_loot", maxTicks = 20)
	public void waveLootTablesLoad(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		for (var key : List.of(CommonIds.WAVE_DROPS_COMMON, CommonIds.WAVE_DROPS_RARE, CommonIds.WAVE_DROPS_LEGENDARY, CommonIds.WAVE_CLEAR_REWARD)) {
			helper.assertTrue(server.reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "loot table " + key.identifier() + " loads");
		}
		helper.succeed();
	}
}
