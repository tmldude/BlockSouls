package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.raid.FinalDragon;
import io.github.tmldude.blocksouls.bosses.raid.FinalRaids;
import io.github.tmldude.blocksouls.bosses.raid.RaidArena;
import io.github.tmldude.blocksouls.bosses.raid.RaidCrystal;
import io.github.tmldude.blocksouls.bosses.raid.logic.CrystalOrder;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.event.BossEvents;

/**
 * The final raid's phases 2 and 3 without the End (phase 1 is vanilla's fight): a scripted arena in the test area
 * (three crystal spots, two plate spots, a perch at the center) and scripted health.
 *
 * <p>Determinism: the tests run in their own environment (their own batch), each force-loads the chunks around its
 * arena (the runner only forces the test structure's own chunks, and the dragon's flight or the chunk grid can put it
 * a few blocks over a border, where it would stop ticking; the runner un-forces everything after the batch), and the
 * arena is tiny (flight scale 0.08, fight radius ~9) so neighbouring tests' mock players never count as being in
 * this fight. Timings are in game ticks with wide margins; nothing depends on wall-clock time.
 */
public class FinalRaidGameTests {
	/** Normal difficulty, in its own batch: the raid tests never share a batch with the other bosses' tests. */
	private static final String ENV = "blocksouls_bosses_test:final_raid";
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

	private static final List<Kill> KILLS = new ArrayList<>();

	private record Kill(UUID boss, Identifier id, List<UUID> players) {
	}

	static {
		BossEvents.KILLED.register((level, bossId, boss, players) ->
				KILLS.add(new Kill(boss.getUUID(), bossId, players.stream().map(Entity::getUUID).toList())));
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A final dragon on foot in a scripted arena: crystals at three corners, plates two blocks east and west. */
	private static FinalDragon dragon(GameTestHelper helper) {
		BlockPos center = helper.absolutePos(CENTER);
		Vec3 c = Vec3.atBottomCenterOf(center);
		List<Vec3> crystals = List.of(c.add(-3, 2, -3), c.add(3, 2, -3), c.add(-3, 2, 3));
		List<BlockPos> plates = List.of(center.offset(-2, -1, 0), center.offset(2, -1, 0));
		return dragon(helper, crystals, plates);
	}

	/** A final dragon on foot in a scripted arena with the given crystal and plate spots. */
	private static FinalDragon dragon(GameTestHelper helper, List<Vec3> crystals, List<BlockPos> plates) {
		floor(helper);
		BlockPos center = helper.absolutePos(CENTER);
		// Every chunk the dragon can reach (leash ~4.4 blocks, plus its body) ticks, wherever the chunk borders fall.
		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.getLevel().setChunkForced(cx + dx, cz + dz, true);
			}
		}
		Vec3 c = Vec3.atBottomCenterOf(center);
		FinalDragon dragon = helper.spawn(FinalRaids.TYPE, CENTER);
		dragon.setArena(RaidArena.of(center, crystals, c.add(0, 3, 0), c, plates, 0.08), false);
		return dragon;
	}

	/** A (creative, so never targeted) mock player at the arena center: one player in the fight. */
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(CENTER));
		// teleportTo, not snapTo: the mock connection never confirms its login teleport, and vanilla re-sends a pending
		// teleport every second, snapping a plain snapTo back.
		player.teleportTo(at.x, at.y, at.z);
		return player;
	}

	/** Something to fight that can't die or move, so the fight never resets while a test waits. */
	private static Pig dummy(GameTestHelper helper) {
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(6, 1, 6));
		pig.setNoAi(true);
		pig.setPermanentlyInvulnerable(true);
		return pig;
	}

	private static RaidCrystal crystalAt(FinalDragon dragon, int slot) {
		return dragon.standingCrystals().stream().filter(c -> c.slot() == slot).findFirst().orElse(null);
	}

	private static void hitCrystal(GameTestHelper helper, FinalDragon dragon, int slot, ServerPlayer player) {
		RaidCrystal crystal = crystalAt(dragon, slot);
		helper.assertTrue(crystal != null, "no crystal standing in slot " + slot + ": " + dragon.debugInfo());
		ServerLevel level = helper.getLevel();
		crystal.hurtServer(level, level.damageSources().playerAttack(player), 1);
	}

	private static float lostTo(GameTestHelper helper, FinalDragon dragon, float damage) {
		float before = dragon.getHealth();
		dragon.setInvulnerableTime(0);
		// Magic ignores armor, so the numbers are exact.
		dragon.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), damage);
		return before - dragon.getHealth();
	}

	@GameTest(environment = ENV)
	public void finalDragonIsTaggedAndScaled(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		helper.assertTrue(dragon.is(CommonTags.BOSSES), "final dragon missing from #blocksouls:bosses");
		helper.assertTrue(dragon.definition().id().equals(CommonIds.FINAL_BOSS), "boss id " + dragon.definition().id());
		helper.assertTrue(dragon.getMaxHealth() >= FinalRaids.CONFIG.health - 0.01, "base health " + dragon.getMaxHealth());
		helper.assertTrue(dragon.definition().phases().size() == 2, "Grounded and Final Stand");
		helper.assertTrue(dragon.mode() == FinalDragon.Mode.GROUNDED, "spawns on foot outside the End");
		helper.succeed();
	}

	/** On foot, its ground attacks keep finishing (no softlock) and it stays on the ground. */
	@GameTest(environment = ENV, maxTicks = 500)
	public void groundedAttacksFinish(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		dragon.engage(player(helper));
		dragon.forceTarget(dummy(helper));
		helper.succeedWhen(() -> {
			helper.assertTrue(dragon.attacksFinished() >= 3, "only " + dragon.attacksFinished() + " attacks: " + dragon.debugInfo());
			helper.assertTrue(dragon.mode() == FinalDragon.Mode.GROUNDED, "left the ground: " + dragon.debugInfo());
			helper.assertTrue(dragon.isEngaged(), "fight reset");
		});
	}

	/** Damage can't skip the interludes: a huge hit at full health stops at the wave's threshold. */
	@GameTest(environment = ENV)
	public void interludesCantBeSkipped(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		dragon.engage(player(helper));
		lostTo(helper, dragon, 100000);
		float fraction = dragon.getHealth() / dragon.getMaxHealth();
		helper.assertTrue(Math.abs(fraction - FinalRaids.CONFIG.waveThreshold) < 0.002, "stopped at " + fraction);
		helper.assertTrue(dragon.phase() == 0, "skipped to the final stand");
		helper.succeed();
	}

	/** 85%: it takes off untouchable and calls a wave; clearing the wave brings it back down. */
	@GameTest(environment = ENV, maxTicks = 400)
	public void waveInterlude(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		dragon.engage(player(helper));
		dragon.forceTarget(dummy(helper));
		dragon.setHealth(dragon.getMaxHealth() * (float) FinalRaids.CONFIG.waveThreshold);
		boolean[] cleared = {false};
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(dragon.waveAlive() > 0, "no wave: " + dragon.debugInfo());
			helper.assertTrue(dragon.mode() == FinalDragon.Mode.TAKEOFF || dragon.mode() == FinalDragon.Mode.AIRBORNE, "not in the air: "
					+ dragon.debugInfo());
			helper.assertTrue(lostTo(helper, dragon, 10) == 0, "took damage during the wave");
			// Clear the wave.
			for (Mob mob : helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(helper.absolutePos(CENTER)).inflate(16),
					m -> !(m instanceof BossEntity) && !(m instanceof Pig))) {
				mob.kill(helper.getLevel());
			}
			cleared[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cleared[0], "wave not cleared yet");
			helper.assertTrue(dragon.waveDone(), "wave not done: " + dragon.debugInfo());
			helper.assertTrue(dragon.mode() == FinalDragon.Mode.GROUNDED, "not back on the ground: " + dragon.debugInfo());
		});
	}

	/** 60%: a herald answers (weaker than in the field); killing it crashes the dragon down stunned (×2 damage). */
	@GameTest(environment = ENV, maxTicks = 300)
	public void heraldInterludeEndsInAStun(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		dragon.engage(player(helper));
		dragon.forceTarget(dummy(helper));
		dragon.setHealth(dragon.getMaxHealth() * (float) FinalRaids.CONFIG.heraldThreshold);
		dragon.beginHerald(helper.getLevel());
		BossEntity herald = dragon.herald();
		helper.assertTrue(herald != null, "no herald: " + dragon.debugInfo());
		helper.assertTrue(!herald.fogWall().isUp(), "the herald raised a fog wall on the island");
		helper.assertTrue(lostTo(helper, dragon, 10) == 0, "took damage while its herald fights");
		herald.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.succeedWhen(() -> {
			helper.assertTrue(dragon.heraldDone(), "herald not done: " + dragon.debugInfo());
			helper.assertTrue(dragon.mode() == FinalDragon.Mode.STUNNED, "not stunned: " + dragon.debugInfo());
			float lost = lostTo(helper, dragon, 10);
			helper.assertTrue(Math.abs(lost - 10 * FinalRaids.CONFIG.stunDamageMult) < 0.01, "stunned hit took " + lost);
		});
	}

	/** Final stand: crystals are sealed until the plate is held; then the order breaks the cast and stuns the dragon. */
	@GameTest(environment = ENV, maxTicks = 400)
	public void finalStandCrystalsNeedThePlates(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerPlayer player = player(helper);
		dragon.engage(player);
		dragon.forceTarget(dummy(helper));
		dragon.setHealth(dragon.getMaxHealth() * (float) (FinalRaids.CONFIG.finalStandThreshold - 0.05));
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(dragon.phase() == 1, "not in the final stand: " + dragon.debugInfo());
			CrystalOrder order = dragon.crystalOrder();
			helper.assertTrue(order != null && order.remaining().size() == 3, "expected 3 crystals: " + dragon.debugInfo());
			helper.assertTrue(dragon.plateCenters().size() == 2, "solo gets two plates: " + dragon.debugInfo());
			helper.assertTrue(dragon.castLeft() > 0, "no cast: " + dragon.debugInfo());
			// Sealed: a hit clinks off.
			hitCrystal(helper, dragon, order.next(), player);
			helper.assertTrue(order.destroyed() == 0, "broke a sealed crystal");
			BlockPos plate = dragon.plateCenters().getFirst();
			Vec3 on = Vec3.atBottomCenterOf(plate.above());
			player.teleportTo(on.x, on.y, on.z);
		});
		helper.runAfterDelay(80, () -> {
			CrystalOrder order = dragon.crystalOrder();
			// A wrong one: heals and pushes the cast forward.
			int castBefore = dragon.castLeft();
			dragon.setHealth(dragon.getMaxHealth() * 0.2f);
			int wrong = order.remaining().get(1);
			hitCrystal(helper, dragon, wrong, player);
			helper.assertTrue(Math.abs(dragon.getHealth() / dragon.getMaxHealth() - 0.25f) < 0.002, "wrong crystal should heal 5%: "
					+ dragon.debugInfo());
			helper.assertTrue(dragon.castLeft() < castBefore - 10, "the cast didn't surge: " + dragon.debugInfo());
			helper.assertTrue(order.contains(wrong), "the wrong crystal stays in the order");
		});
		helper.runAfterDelay(80 + FinalRaids.CONFIG.crystalReformTicks + 5, () -> {
			CrystalOrder order = dragon.crystalOrder();
			while (!order.isDone()) {
				hitCrystal(helper, dragon, order.next(), player);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dragon.mode() == FinalDragon.Mode.STUNNED, "not stunned: " + dragon.debugInfo());
		});
	}

	/**
	 * A full party of 8 in the final stand (party.json's curves): 8 crystals, 5 plates placed, and the crystals stay sealed
	 * until 4 of them are held at once.
	 */
	@GameTest(environment = ENV, maxTicks = 300)
	public void aFullPartyOfEightScalesTheFinalStand(GameTestHelper helper) {
		BlockPos center = helper.absolutePos(CENTER);
		Vec3 c = Vec3.atBottomCenterOf(center);
		List<Vec3> crystals = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx += 3) {
			for (int dz = -3; dz <= 3; dz += 3) {
				if (dx != 0 || dz != 0) {
					crystals.add(c.add(dx, 2, dz));
				}
			}
		}
		// Five non-overlapping 3x3 plates, none at the center where the party starts.
		List<BlockPos> plates = List.of(center.offset(-3, -1, 0), center.offset(3, -1, 0), center.offset(0, -1, -3),
				center.offset(0, -1, 3), center.offset(-3, -1, -3));
		FinalDragon dragon = dragon(helper, crystals, plates);
		List<ServerPlayer> party = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			party.add(player(helper));
		}
		dragon.engage(party.getFirst());
		dragon.forceTarget(dummy(helper));
		dragon.setHealth(dragon.getMaxHealth() * (float) (FinalRaids.CONFIG.finalStandThreshold - 0.05));
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(dragon.phase() == 1, "not in the final stand: " + dragon.debugInfo());
			CrystalOrder order = dragon.crystalOrder();
			helper.assertTrue(order != null && order.remaining().size() == 8, "expected 8 crystals: " + dragon.debugInfo());
			helper.assertTrue(dragon.plateCenters().size() == 5, "8 players get 5 plates: " + dragon.debugInfo());
			for (int i = 0; i < 3; i++) {
				Vec3 on = Vec3.atBottomCenterOf(dragon.plateCenters().get(i).above());
				party.get(i).teleportTo(on.x, on.y, on.z);
			}
		});
		helper.runAfterDelay(80, () -> {
			CrystalOrder order = dragon.crystalOrder();
			hitCrystal(helper, dragon, order.next(), party.getLast());
			helper.assertTrue(order.destroyed() == 0, "3 of 4 plates held, yet a crystal broke: " + dragon.debugInfo());
			Vec3 on = Vec3.atBottomCenterOf(dragon.plateCenters().get(3).above());
			party.get(3).teleportTo(on.x, on.y, on.z);
		});
		helper.runAfterDelay(100, () -> {
			CrystalOrder order = dragon.crystalOrder();
			hitCrystal(helper, dragon, order.next(), party.getLast());
			helper.assertTrue(order.destroyed() == 1, "4 plates held, but the crystal stayed sealed: " + dragon.debugInfo());
			helper.succeed();
		});
	}

	/** Final stand: when the cast completes, Sovereign's End goes off and the next cast begins. */
	@GameTest(environment = ENV, maxTicks = 200)
	public void castCompletes(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		dragon.engage(player(helper));
		dragon.forceTarget(dummy(helper));
		dragon.setHealth(dragon.getMaxHealth() * 0.3f);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(dragon.phase() == 1, "not in the final stand: " + dragon.debugInfo());
			dragon.finishCastIn(5);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dragon.castsCompleted() == 1, "cast didn't go off: " + dragon.debugInfo());
			helper.assertTrue(dragon.castLeft() > dragon.castTotal() / 2, "next cast didn't start: " + dragon.debugInfo());
		});
	}

	/** A wipe keeps the checkpoints: after the herald, phase 2 restarts at 60%; in phase 3 at 35%. */
	@GameTest(environment = ENV, maxTicks = 200)
	public void wipeHealsToTheLastCheckpoint(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerPlayer player = player(helper);
		dragon.engage(player);
		dragon.beginHerald(helper.getLevel());
		dragon.herald().hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(dragon.heraldDone(), "herald not done: " + dragon.debugInfo());
			dragon.setHealth(dragon.getMaxHealth() * 0.45f);
			dragon.resetFight();
			float fraction = dragon.getHealth() / dragon.getMaxHealth();
			helper.assertTrue(Math.abs(fraction - FinalRaids.CONFIG.heraldThreshold) < 0.002, "phase 2 wipe healed to " + fraction);
			helper.assertTrue(dragon.waveDone() && dragon.heraldDone(), "cleared interludes came back");

			dragon.engage(player);
			dragon.setHealth(dragon.getMaxHealth() * 0.3f);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(dragon.phase() == 1, "not in the final stand: " + dragon.debugInfo());
			dragon.setHealth(dragon.getMaxHealth() * 0.1f);
			dragon.resetFight();
			float fraction = dragon.getHealth() / dragon.getMaxHealth();
			helper.assertTrue(dragon.phase() == 1, "a wipe left the final stand");
			helper.assertTrue(Math.abs(fraction - FinalRaids.CONFIG.finalStandThreshold) < 0.002, "phase 3 wipe healed to " + fraction);
			helper.assertTrue(dragon.standingCrystals().isEmpty() && dragon.plateCenters().isEmpty(), "mechanics left after the wipe");
			helper.succeed();
		});
	}

	/** Final stand: its airborne attacks keep finishing. */
	@GameTest(environment = ENV, maxTicks = 600)
	public void finalStandAttacksFinish(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		dragon.engage(player(helper));
		dragon.forceTarget(dummy(helper));
		dragon.setHealth(dragon.getMaxHealth() * 0.3f);
		int[] mark = {-1};
		helper.runAfterDelay(60, () -> mark[0] = dragon.attacksFinished());
		helper.succeedWhen(() -> {
			helper.assertTrue(mark[0] >= 0, "not started yet");
			helper.assertTrue(dragon.phase() == 1, "not in the final stand: " + dragon.debugInfo());
			helper.assertTrue(dragon.attacksFinished() >= mark[0] + 2, "attacks stalled: " + dragon.debugInfo());
		});
	}

	/** Death fires BossEvents.KILLED with the final boss id and the participant (the game module's endless switch). */
	@GameTest(environment = ENV, maxTicks = 200)
	public void deathFiresKilledWithFinalBoss(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerPlayer player = player(helper);
		dragon.engage(player);
		UUID id = dragon.getUUID();
		dragon.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.succeedWhen(() -> {
			helper.assertTrue(dragon.isDeadOrDying(), "not dead");
			Kill kill = KILLS.stream().filter(k -> k.boss().equals(id)).findFirst().orElse(null);
			helper.assertTrue(kill != null, "no BossEvents.KILLED");
			helper.assertTrue(kill.id().equals(CommonIds.FINAL_BOSS), "KILLED with id " + kill.id());
			helper.assertTrue(kill.players().contains(player.getUUID()), "KILLED without the participant");
		});
	}

	/** The main table drops the trophy; the guns table only loads with the guns module and never breaks loading. */
	@GameTest(environment = ENV)
	public void dropsTheTrophy(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerLevel level = helper.getLevel();
		List<ItemStack> drops = new ArrayList<>();
		var source = level.damageSources().playerAttack(helper.makeMockServerPlayerInLevel());
		dragon.dropFromLootTable(level, source, true, dragon.getLootTable().orElseThrow(), drops::add);
		for (var table : dragon.definition().extraLoot()) {
			dragon.dropFromLootTable(level, source, true, table, drops::add);
		}
		helper.assertTrue(drops.stream().anyMatch(s -> s.is(FinalRaids.TROPHY)), "no trophy in " + drops);
		helper.succeed();
	}
}
